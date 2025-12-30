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
import com.patch.foliaphantom.core.transformer.impl.EntitySchedulerTransformer;
import com.patch.foliaphantom.core.transformer.impl.SchedulerClassTransformer;
import com.patch.foliaphantom.core.transformer.impl.ThreadSafetyTransformer;
import com.patch.foliaphantom.core.transformer.impl.WorldGenClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

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
     * Creates a new PluginPatcher instance with the specified logger and progress listener.
     *
     * @param logger           Logger for diagnostics
     * @param progressListener Listener for real-time progress updates
     */
    public PluginPatcher(Logger logger, PatchProgressListener progressListener) {
        this.logger = logger;
        this.progressListener = progressListener != null ? progressListener : NULL_LISTENER;
    }

    /**
     * Creates a new PluginPatcher instance with the specified logger.
     *
     * @param logger Logger for outputting patching progress and diagnostics
     */
    public PluginPatcher(Logger logger) {
        this(logger, null);
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
            this.transformers = new ArrayList<>();
            transformers.add(new ThreadSafetyTransformer(logger, relocatedPatcherPath));
            transformers.add(new WorldGenClassTransformer(logger, relocatedPatcherPath));
            transformers.add(new EntitySchedulerTransformer(logger, relocatedPatcherPath));
            transformers.add(new SchedulerClassTransformer(logger, relocatedPatcherPath));

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
     * Creates the patched JAR file with parallel class transformation.
     * 
     * @param source      Path to source JAR
     * @param destination Path to output JAR
     * @throws IOException If an I/O error occurs
     */
    private void createPatchedJar(Path source, Path destination) throws IOException {
        ForkJoinPool executor = ForkJoinPool.commonPool();
        List<ClassPatchFuture> classFutures = new ArrayList<>(256);

        progressListener.onProgressUpdate(0, "Scanning JAR entries...");
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(source))) {
            zis.mark(Integer.MAX_VALUE);
            int classCount = 0;
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                if (ze.getName().endsWith(".class")) {
                    classCount++;
                }
            }
            zis.reset();

            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(destination))) {
                zos.setMethod(ZipOutputStream.DEFLATED);
                zos.setLevel(COMPRESSION_LEVEL);

                ZipEntry entry;
                int processedCount = 0;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (isSignatureFile(name)) continue;

                    if (!entry.isDirectory() && name.endsWith(".class")) {
                        byte[] classBytes = zis.readAllBytes();
                        classesScanned.incrementAndGet();
                        final int currentClassIndex = processedCount++;
                        progressListener.onClassTransform(name, currentClassIndex, classCount);
                        classFutures.add(new ClassPatchFuture(name,
                                executor.submit(() -> patchClass(classBytes, name))));
                    } else if (!entry.isDirectory() && name.equals("plugin.yml")) {
                        String originalYml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                        writeEntry(zos, name, addFoliaSupportedFlag(originalYml).getBytes(StandardCharsets.UTF_8));
                    } else {
                        zos.putNextEntry(new ZipEntry(name));
                        if (!entry.isDirectory()) copyStream(zis, zos);
                        zos.closeEntry();
                    }
                }

                progressListener.onProgressUpdate(50, "Writing transformed classes...");
                for (int i = 0; i < classFutures.size(); i++) {
                    ClassPatchFuture cpf = classFutures.get(i);
                    try {
                        ClassPatchResult result = cpf.future.get();
                        writeEntry(zos, cpf.name, result.bytes);
                        if (result.wasTransformed) classesTransformed.incrementAndGet();
                        else classesSkipped.incrementAndGet();
                        progressListener.onProgressUpdate(50 + (i * 40 / classFutures.size()),
                                "Writing: " + cpf.name);
                    } catch (Exception e) {
                        throw new IOException("Failed to patch class: " + cpf.name, e);
                    }
                }

                progressListener.onProgressUpdate(90, "Bundling runtime classes...");
                bundleFoliaPatcherClasses(zos);
                progressListener.onProgressUpdate(100, "Finalizing JAR...");
            }
        }
    }

    /**
     * Checks if a file is a JAR signature file.
     */
    private boolean isSignatureFile(String name) {
        return name.startsWith("META-INF/") &&
                (name.endsWith(".SF") || name.endsWith(".DSA") || name.endsWith(".RSA"));
    }

    /**
     * Writes an entry to the ZIP output stream.
     */
    private void writeEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(data);
        zos.closeEntry();
    }

    /**
     * Bundles and relocates the FoliaPatcher runtime classes into the output JAR using ASM.
     *
     * <p>These classes are required at runtime by the patched plugin to handle Folia's
     * scheduler API calls. They are relocated to a unique package path to avoid conflicts.</p>
     *
     * @param zos The output ZIP stream
     * @throws IOException If bundling fails
     */
    private void bundleFoliaPatcherClasses(ZipOutputStream zos) throws IOException {
        String originalPatcherPath = "com/patch/foliaphantom/core/patcher";
        SimpleRemapper remapper = new SimpleRemapper(originalPatcherPath, this.relocatedPatcherPath);

        String[] classesToBundle = {
            "FoliaPatcher.class",
            "FoliaPatcher$FoliaBukkitTask.class",
            "FoliaPatcher$FoliaChunkGenerator.class"
        };

        for (String className : classesToBundle) {
            String originalClassPath = originalPatcherPath + "/" + className;
            String relocatedClassPath = this.relocatedPatcherPath + "/" + className;

            try (InputStream is = getClass().getClassLoader().getResourceAsStream(originalClassPath)) {
                if (is == null) {
                    logger.warning("[FoliaPhantom] Runtime class not found: " + originalClassPath);
                    continue;
                }

                ClassReader cr = new ClassReader(is);
                ClassWriter cw = new ClassWriter(0);
                ClassVisitor cv = new ClassRemapper(cw, remapper);
                cr.accept(cv, ClassReader.EXPAND_FRAMES);

                zos.putNextEntry(new ZipEntry(relocatedClassPath));
                zos.write(cw.toByteArray());
                zos.closeEntry();

                logger.fine("Successfully bundled and relocated " + relocatedClassPath);
            }
        }
    }

    /**
     * Copies data from an input stream to an output stream.
     * 
     * @param in  Source stream
     * @param out Destination stream
     * @throws IOException If an I/O error occurs
     */
    private void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int len;
        while ((len = in.read(buffer)) > 0) {
            out.write(buffer, 0, len);
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
     * Extracts the plugin name from a JAR file's plugin.yml.
     * 
     * @param jarFile The plugin JAR file
     * @return The plugin name, or null if not found
     * @throws IOException If an I/O error occurs
     */
    public static String getPluginNameFromJar(File jarFile) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jarFile.toPath()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("plugin.yml")) {
                    String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    for (String line : content.lines().toList()) {
                        if (line.trim().startsWith("name:")) {
                            return line.substring(line.indexOf(":") + 1).trim();
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Checks if a plugin JAR already has folia-supported: true.
     * 
     * @param jarFile The plugin JAR file
     * @return true if the plugin already supports Folia
     * @throws IOException If an I/O error occurs
     */
    public static boolean isFoliaSupported(File jarFile) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jarFile.toPath()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("plugin.yml")) {
                    String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    return content.lines()
                            .anyMatch(line -> line.trim().equalsIgnoreCase("folia-supported: true"));
                }
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
