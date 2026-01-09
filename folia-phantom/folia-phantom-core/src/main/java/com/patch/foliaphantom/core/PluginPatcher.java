/*
 * Folia Phantom - Plugin Patcher Core
 * 
 * This module provides the core bytecode transformation logic for converting
 * Bukkit plugins to be compatible with Folia's region-based threading model.
 * 
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core;

import com.patch.foliaphantom.core.progress.PatchProgressListener;
import com.patch.foliaphantom.core.transformer.ClassTransformer;
import com.patch.foliaphantom.core.transformer.ScanningClassVisitor;
import com.patch.foliaphantom.core.transformer.TransformerType;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Main plugin patching utility for Folia Phantom.
 * 
 * <p>
 * This class orchestrates the transformation of Bukkit plugin JAR files,
 * replacing thread-unsafe API calls with Folia-compatible alternatives using
 * ASM bytecode manipulation.
 * </p>
 * 
 * <h2>Features</h2>
 * <ul>
 * <li>Parallel class transformation using ForkJoinPool</li>
 * <li>Automatic plugin.yml modification to add folia-supported flag</li>
 * <li>Signature file removal for compatibility with signed JARs</li>
 * <li>Bundle FoliaPatcher runtime classes into output JAR</li>
 * <li>Fast-fail scanning to skip classes that don't need patching</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * 
 * <pre>{@code
 * Logger logger = Logger.getLogger("FoliaPhantom");
 * PluginPatcher patcher = new PluginPatcher(logger);
 * patcher.patchPlugin(inputJar, outputJar);
 * }</pre>
 * 
 * @author Marv
 * @version 1.0.0
 */
public class PluginPatcher {

    /** Buffer size for stream copying (64KB for optimal I/O performance) */
    private static final int BUFFER_SIZE = 65536;

    /** Compression level for output JAR (1 = fastest) */
    private static final int COMPRESSION_LEVEL = 1;

    /** Logger instance for this patcher */
    private final Logger logger;

    /** Progress listener for real-time feedback */
    private final PatchProgressListener progressListener;

    /** Set of enabled transformers for this patching session */
    private final Set<TransformerType> enabledTransformers;

    /** Patched FoliaPatcher internal path */
    private String relocatedPatcherPath;

    /** Ordered list of class transformers to apply */
    private List<ClassTransformer> transformers;

    /** Statistics: number of classes scanned */
    private final AtomicInteger classesScanned = new AtomicInteger(0);

    /** Statistics: number of classes transformed */
    private final AtomicInteger classesTransformed = new AtomicInteger(0);

    /** Statistics: number of classes skipped (no changes needed) */
    private final AtomicInteger classesSkipped = new AtomicInteger(0);

    /** A progress listener that does nothing */
    private static final PatchProgressListener NULL_LISTENER = new PatchProgressListener() {
        @Override public void onPatchStart(File originalJar, File outputJar) {}
        @Override public void onClassTransform(String className, int classIndex, int totalClasses) {}
        @Override public void onProgressUpdate(int percentage, String message) {}
        @Override public void onComplete(long durationMillis, int[] statistics, Throwable error) {}
    };

    /**
     * Creates a new PluginPatcher instance with specified logger, listener, and enabled transformers.
     *
     * @param logger              Logger for diagnostics
     * @param progressListener    Listener for real-time progress updates
     * @param enabledTransformers Set of transformers to enable for this session
     */
    public PluginPatcher(Logger logger, PatchProgressListener progressListener, Set<TransformerType> enabledTransformers) {
        this.logger = logger;
        this.progressListener = progressListener != null ? progressListener : NULL_LISTENER;
        this.enabledTransformers = enabledTransformers != null ? enabledTransformers : EnumSet.allOf(TransformerType.class);
    }

    /**
     * Creates a new PluginPatcher instance with the specified logger and progress listener.
     * All transformers are enabled by default.
     *
     * @param logger           Logger for diagnostics
     * @param progressListener Listener for real-time progress updates
     */
    public PluginPatcher(Logger logger, PatchProgressListener progressListener) {
        this(logger, progressListener, EnumSet.allOf(TransformerType.class));
    }

    /**
     * Creates a new PluginPatcher instance with the specified logger.
     * All transformers are enabled by default.
     *
     * @param logger Logger for outputting patching progress and diagnostics
     */
    public PluginPatcher(Logger logger) {
        this(logger, null, EnumSet.allOf(TransformerType.class));
    }

    /**
     * Patches a plugin JAR file for Folia compatibility.
     * 
     * <p>
     * This method:
     * </p>
     * <ol>
     * <li>Reads the original JAR file</li>
     * <li>Transforms all .class files using registered transformers</li>
     * <li>Modifies plugin.yml to add folia-supported: true</li>
     * <li>Removes JAR signature files for compatibility</li>
     * <li>Bundles FoliaPatcher runtime classes</li>
     * <li>Writes the patched JAR to the output location</li>
     * </ol>
     * 
     * @param originalJar Source JAR file to patch
     * @param outputJar   Destination for the patched JAR
     * @throws IOException If an I/O error occurs during patching
     */
    public void patchPlugin(File originalJar, File outputJar) throws IOException {
        classesScanned.set(0);
        classesTransformed.set(0);
        classesSkipped.set(0);

        progressListener.onPatchStart(originalJar, outputJar);
        long startTime = System.currentTimeMillis();

        try {
            // Determine the relocation path
            String pluginName = getPluginNameFromJar(originalJar);
            if (pluginName == null) {
                throw new IOException("Could not find plugin name from plugin.yml");
            }

            // Sanitize plugin name for package relocation
            String safePluginName = pluginName.toLowerCase().replaceAll("[^a-z0-9]", "");
            this.relocatedPatcherPath = safePluginName + "/folia/runtime";

            // Initialize transformers with the relocated path
            initializeTransformers();

            logger.info("Relocating FoliaPhantom runtime to: " + relocatedPatcherPath);

            createPatchedJar(originalJar.toPath(), outputJar.toPath());
            long duration = System.currentTimeMillis() - startTime;
            progressListener.onComplete(duration, getStatistics(), null);
        } catch (IOException e) {
            long duration = System.currentTimeMillis() - startTime;
            progressListener.onComplete(duration, getStatistics(), e);
            throw e;
        }
    }

    /**
     * Initializes the transformer instances based on the enabled transformer types.
     * Uses reflection to create new instances of the transformer classes.
     */
    private void initializeTransformers() {
        this.transformers = new ArrayList<>();
        if (enabledTransformers == null || enabledTransformers.isEmpty()) {
            logger.warning("No transformers enabled for this patching session.");
            return;
        }

        logger.info("Initializing " + enabledTransformers.size() + " transformers...");
        for (TransformerType type : TransformerType.values()) {
            if (enabledTransformers.contains(type)) {
                try {
                    Class<? extends ClassTransformer> clazz = type.getTransformerClass();
                    transformers.add(clazz.getConstructor(Logger.class, String.class).newInstance(logger, relocatedPatcherPath));
                    logger.fine("Enabled transformer: " + type.name());
                } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                    logger.log(Level.SEVERE, "Failed to initialize transformer: " + type.name(), e);
                }
            }
        }
    }

    /**
     * Creates the patched JAR using an in-memory ZIP filesystem for high performance.
     *
     * @param source      Path to the source JAR.
     * @param destination Path for the output JAR.
     * @throws IOException If an I/O error occurs during file operations.
     */
    private void createPatchedJar(Path source, Path destination) throws IOException {
        // First, copy the original JAR to the destination. We will modify it in-place.
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);

        // Define ZIP filesystem properties. 'create: false' means we open an existing file.
        Map<String, String> env = new HashMap<>();
        env.put("create", "false");

        URI uri = URI.create("jar:" + destination.toUri());

        try (FileSystem zipfs = FileSystems.newFileSystem(uri, env)) {
            ForkJoinPool executor = ForkJoinPool.commonPool();
            List<Path> classFiles;
            try (Stream<Path> stream = Files.walk(zipfs.getPath("/"))) {
                classFiles = stream.filter(p -> p.toString().endsWith(".class")).toList();
            }

            progressListener.onProgressUpdate(0, "Transforming " + classFiles.size() + " classes...");

            // Submit all class patching tasks for parallel execution.
            Map<Path, Future<ClassPatchResult>> futures = new HashMap<>();
            for (Path path : classFiles) {
                byte[] originalBytes = Files.readAllBytes(path);
                classesScanned.incrementAndGet();
                futures.put(path, executor.submit(() -> patchClass(originalBytes, path.toString())));
            }

            // Process results and write back to the in-memory filesystem.
            int processedCount = 0;
            for (Map.Entry<Path, Future<ClassPatchResult>> entry : futures.entrySet()) {
                try {
                    ClassPatchResult result = entry.getValue().get();
                    if (result.wasTransformed) {
                        Files.write(entry.getKey(), result.bytes);
                        classesTransformed.incrementAndGet();
                    } else {
                        classesSkipped.incrementAndGet();
                    }
                    progressListener.onProgressUpdate(
                        (int) (50.0 * (++processedCount) / classFiles.size()),
                        "Processed: " + entry.getKey()
                    );
                } catch (InterruptedException | ExecutionException e) {
                    throw new IOException("Failed to patch class: " + entry.getKey(), e);
                }
            }

            progressListener.onProgressUpdate(50, "Updating JAR metadata...");
            updatePluginYml(zipfs);
            removeSignatureFiles(zipfs);

            progressListener.onProgressUpdate(90, "Bundling runtime classes...");
            bundleFoliaPatcherClasses(zipfs);

            progressListener.onProgressUpdate(100, "Finalizing JAR...");
        }
    }

    private void updatePluginYml(FileSystem zipfs) throws IOException {
        Path pluginYmlPath = zipfs.getPath("plugin.yml");
        if (Files.exists(pluginYmlPath)) {
            String originalYml = Files.readString(pluginYmlPath, StandardCharsets.UTF_8);
            String modifiedYml = addFoliaSupportedFlag(originalYml);
            Files.writeString(pluginYmlPath, modifiedYml, StandardCharsets.UTF_8);
        }
    }

    private void removeSignatureFiles(FileSystem zipfs) throws IOException {
        Path metaInfDir = zipfs.getPath("META-INF");
        if (Files.isDirectory(metaInfDir)) {
            try (Stream<Path> stream = Files.list(metaInfDir)) {
                stream.filter(p -> {
                    String fn = p.getFileName().toString();
                    return fn.endsWith(".SF") || fn.endsWith(".DSA") || fn.endsWith(".RSA");
                }).forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        logger.log(Level.WARNING, "Failed to delete signature file: " + p, e);
                    }
                });
            }
        }
    }

    private void bundleFoliaPatcherClasses(FileSystem zipfs) throws IOException {
        String originalPatcherPath = "com/patch/foliaphantom/core/patcher";
        SimpleRemapper remapper = new SimpleRemapper(originalPatcherPath, this.relocatedPatcherPath);

        String[] classesToBundle = {
            "FoliaPatcher.class",
            "FoliaPatcher$FoliaBukkitTask.class",
            "FoliaPatcher$FoliaChunkGenerator.class"
        };

        Path targetDir = zipfs.getPath(this.relocatedPatcherPath);
        Files.createDirectories(targetDir);

        for (String className : classesToBundle) {
            String originalClassPath = originalPatcherPath + "/" + className;
            Path targetPath = targetDir.resolve(className);

            try (InputStream is = getClass().getClassLoader().getResourceAsStream(originalClassPath)) {
                if (is == null) {
                    logger.warning("[FoliaPhantom] Runtime class not found: " + originalClassPath);
                    continue;
                }

                ClassReader cr = new ClassReader(is);
                ClassWriter cw = new ClassWriter(0);
                ClassVisitor cv = new ClassRemapper(cw, remapper);
                cr.accept(cv, ClassReader.EXPAND_FRAMES);

                Files.write(targetPath, cw.toByteArray());
                logger.fine("Successfully bundled and relocated " + targetPath);
            }
        }
    }

    /**
     * Patches a single class using the registered transformers.
     * 
     * <p>
     * Uses a fast-fail heuristic: first scans the class to determine
     * if any patching is needed. If not, returns the original bytes
     * immediately without full transformation.
     * </p>
     * 
     * @param originalBytes The original class bytecode
     * @param className     The class name (for logging)
     * @return The patching result containing transformed bytes
     */
    private ClassPatchResult patchClass(byte[] originalBytes, String className) {
        try {
            ClassReader cr = new ClassReader(originalBytes);

            // Fast-fail scan: check if this class needs patching
            ScanningClassVisitor scanner = new ScanningClassVisitor(relocatedPatcherPath);
            cr.accept(scanner, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            if (!scanner.needsPatching()) {
                return new ClassPatchResult(originalBytes, false);
            }

            // Full transformation needed
            ClassWriter cw = new ClassWriter(cr, 0);
            ClassVisitor cv = cw;

            // Apply transformers in reverse order (so first registered runs first)
            for (int i = transformers.size() - 1; i >= 0; i--) {
                cv = transformers.get(i).createVisitor(cv);
            }

            cr.accept(cv, ClassReader.EXPAND_FRAMES);

            logger.fine("[FoliaPhantom] Transformed: " + className);
            return new ClassPatchResult(cw.toByteArray(), true);

        } catch (Exception e) {
            logger.log(Level.WARNING,
                    "[FoliaPhantom] Failed to transform " + className + ", using original", e);
            return new ClassPatchResult(originalBytes, false);
        }
    }

    /**
     * Adds or updates the folia-supported flag in plugin.yml.
     * 
     * @param pluginYml Original plugin.yml content
     * @return Modified plugin.yml content
     */
    private String addFoliaSupportedFlag(String pluginYml) {
        if (pluginYml.lines().anyMatch(line -> line.trim().startsWith("folia-supported:"))) {
            // Update existing flag
            return pluginYml.replaceAll("(?m)^\\s*folia-supported:.*$", "folia-supported: true");
        } else {
            // Add new flag
            return pluginYml.trim() + "\nfolia-supported: true\n";
        }
    }

    // =========================================================================
    // Static Utility Methods
    // =========================================================================

    /**
     * Extracts the plugin name from a JAR file's plugin.yml using NIO FileSystems.
     *
     * @param jarFile The plugin JAR file
     * @return The plugin name, or null if not found
     * @throws IOException If an I/O error occurs
     */
    public static String getPluginNameFromJar(File jarFile) throws IOException {
        try (FileSystem zipfs = FileSystems.newFileSystem(jarFile.toPath(), Collections.emptyMap())) {
            Path pluginYmlPath = zipfs.getPath("plugin.yml");
            if (Files.exists(pluginYmlPath)) {
                return Files.readAllLines(pluginYmlPath).stream()
                        .map(String::trim)
                        .filter(line -> line.startsWith("name:"))
                        .findFirst()
                        .map(line -> line.substring(line.indexOf(":") + 1).trim())
                        .orElse(null);
            }
        }
        return null;
    }

    /**
     * Checks if a plugin JAR already has folia-supported: true using NIO FileSystems.
     *
     * @param jarFile The plugin JAR file
     * @return true if the plugin already supports Folia
     * @throws IOException If an I/O error occurs
     */
    public static boolean isFoliaSupported(File jarFile) throws IOException {
        try (FileSystem zipfs = FileSystems.newFileSystem(jarFile.toPath(), Collections.emptyMap())) {
            Path pluginYmlPath = zipfs.getPath("plugin.yml");
            if (Files.exists(pluginYmlPath)) {
                return Files.readAllLines(pluginYmlPath).stream()
                        .anyMatch(line -> line.trim().equalsIgnoreCase("folia-supported: true"));
            }
        }
        return false;
    }

    /**
     * Returns the current patching statistics.
     * 
     * @return Array of [scanned, transformed, skipped]
     */
    public int[] getStatistics() {
        return new int[] {
                classesScanned.get(),
                classesTransformed.get(),
                classesSkipped.get()
        };
    }

    // =========================================================================
    // Inner Classes
    // =========================================================================

    /**
     * Container for a class patching future.
     */
    private static class ClassPatchFuture {
        final String name;
        final Future<ClassPatchResult> future;

        ClassPatchFuture(String name, Future<ClassPatchResult> future) {
            this.name = name;
            this.future = future;
        }
    }

    /**
     * Result of a class patching operation.
     */
    private static class ClassPatchResult {
        final byte[] bytes;
        final boolean wasTransformed;

        ClassPatchResult(byte[] bytes, boolean wasTransformed) {
            this.bytes = bytes;
            this.wasTransformed = wasTransformed;
        }
    }
}
