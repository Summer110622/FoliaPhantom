package com.patch.foliaphantom.core;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import com.patch.foliaphantom.core.transformer.impl.SchedulerClassTransformer;
import com.patch.foliaphantom.core.transformer.impl.WorldGenClassTransformer;
import com.patch.foliaphantom.core.transformer.impl.EntitySchedulerTransformer;
import com.patch.foliaphantom.core.transformer.impl.ThreadSafetyTransformer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class PluginPatcher {

    private final Logger logger;
    private final List<ClassTransformer> transformers = new ArrayList<>();

    public PluginPatcher(Logger logger) {
        this.logger = logger;
        // Register all transformers here. Order is critical.
        this.transformers.add(new ThreadSafetyTransformer(logger));
        this.transformers.add(new WorldGenClassTransformer(logger));
        this.transformers.add(new EntitySchedulerTransformer(logger));
        this.transformers.add(new SchedulerClassTransformer(logger));
    }

    public void patchPlugin(File originalJar, File tempJar) throws IOException {
        logger.info(String.format("[FoliaPhantom] Creating patched JAR at: %s", tempJar.getPath()));
        createPatchedJar(originalJar.toPath(), tempJar.toPath());
    }

    private void createPatchedJar(Path source, Path destination) throws IOException {
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors
                .newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        // Store entries with their processed data
        class EntryData {
            final String name;
            final byte[] data;
            final boolean isDirectory;

            EntryData(String name, byte[] data, boolean isDirectory) {
                this.name = name;
                this.data = data;
                this.isDirectory = isDirectory;
            }
        }

        List<EntryData> entries = new ArrayList<>();
        List<java.util.concurrent.Future<byte[]>> classFutures = new ArrayList<>();
        List<Integer> classIndices = new ArrayList<>();

        // First pass: read all entries and submit class transformations
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(source))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                byte[] bytes = zis.readAllBytes();
                String name = entry.getName();
                boolean isDirectory = entry.isDirectory();

                // Skip signature files (MythicMobs compatibility)
                if (name.startsWith("META-INF/") &&
                        (name.endsWith(".SF") || name.endsWith(".DSA") || name.endsWith(".RSA"))) {
                    logger.fine("Skipping signature file: " + name);
                    continue;
                }

                if (!isDirectory && name.endsWith(".class")) {
                    // Submit for parallel processing
                    final byte[] finalBytes = bytes;
                    classFutures.add(executor.submit(() -> patchClass(finalBytes)));
                    classIndices.add(entries.size());
                    entries.add(new EntryData(name, null, false)); // placeholder
                } else if (!isDirectory && name.equals("plugin.yml")) {
                    String originalYml = new String(bytes, StandardCharsets.UTF_8);
                    String modifiedYml = addFoliaSupportedFlag(originalYml);
                    entries.add(new EntryData(name, modifiedYml.getBytes(StandardCharsets.UTF_8), false));
                } else {
                    entries.add(new EntryData(name, bytes, isDirectory));
                }
            }
        }

        // Wait for all class transformations to complete
        try {
            for (int i = 0; i < classFutures.size(); i++) {
                byte[] patchedClass = classFutures.get(i).get();
                int entryIndex = classIndices.get(i);
                EntryData original = entries.get(entryIndex);
                entries.set(entryIndex, new EntryData(original.name, patchedClass, false));
            }
        } catch (Exception e) {
            throw new IOException("Error during parallel class patching", e);
        } finally {
            executor.shutdown();
        }

        // Bundle FoliaPatcher runtime classes
        bundleFoliaPatcherClasses(entries);

        // Second pass: write all entries sequentially
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(destination))) {
            for (EntryData entryData : entries) {
                zos.putNextEntry(new ZipEntry(entryData.name));
                if (!entryData.isDirectory && entryData.data != null) {
                    zos.write(entryData.data);
                }
                zos.closeEntry();
            }
        }
    }

    private void bundleFoliaPatcherClasses(List<?> entries) throws IOException {
        // Get the class that contains the method to find its location
        Class<?> patcherClass = com.patch.foliaphantom.core.patcher.FoliaPatcher.class;
        String classPath = patcherClass.getName().replace('.', '/');

        // Bundle main FoliaPatcher class
        bundleClass(entries, classPath + ".class");

        // Bundle inner classes
        bundleClass(entries, classPath + "$FoliaBukkitTask.class");
        bundleClass(entries, classPath + "$FoliaChunkGenerator.class");

        logger.info("Bundled FoliaPatcher runtime classes into patched JAR");
    }

    @SuppressWarnings("unchecked")
    private void bundleClass(List<?> entries, String classPath) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(classPath)) {
            if (is == null) {
                logger.warning("Could not find runtime class: " + classPath);
                return;
            }

            byte[] classBytes = is.readAllBytes();

            // Create EntryData using reflection to avoid type issues
            try {
                Class<?> entryDataClass = Class.forName(
                        "com.patch.foliaphantom.core.PluginPatcher$1EntryData");
                java.lang.reflect.Constructor<?> constructor = entryDataClass.getDeclaredConstructors()[0];
                constructor.setAccessible(true);
                Object entryData = constructor.newInstance(this, classPath, classBytes, false);
                ((List<Object>) entries).add(entryData);
            } catch (Exception e) {
                logger.log(java.util.logging.Level.WARNING,
                        "Failed to bundle class: " + classPath, e);
            }
        }
    }

    private byte[] patchClass(byte[] originalBytes) {
        try {
            org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(originalBytes);

            // Fast-fail heuristic: scan first to see if this class needs patching
            com.patch.foliaphantom.core.transformer.ScanningClassVisitor scanner = new com.patch.foliaphantom.core.transformer.ScanningClassVisitor();
            cr.accept(scanner, org.objectweb.asm.ClassReader.SKIP_DEBUG | org.objectweb.asm.ClassReader.SKIP_FRAMES);

            // If no patching needed, return original bytes immediately
            if (!scanner.needsPatching()) {
                return originalBytes;
            }

            // Pass ClassReader to ClassWriter with flags=0 to copy all frame information
            // This avoids ClassNotFoundException by not trying to compute frames or maxs
            org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(cr, 0);
            org.objectweb.asm.ClassVisitor cv = cw;

            for (int i = transformers.size() - 1; i >= 0; i--) {
                cv = transformers.get(i).createVisitor(cv);
            }

            cr.accept(cv, org.objectweb.asm.ClassReader.EXPAND_FRAMES);
            return cw.toByteArray();
        } catch (Exception e) {
            logger.log(java.util.logging.Level.WARNING, "Failed to patch class, returning original bytes", e);
            return originalBytes;
        }
    }

    private String addFoliaSupportedFlag(String pluginYml) {
        if (pluginYml.lines().anyMatch(line -> line.trim().startsWith("folia-supported:"))) {
            return pluginYml.replaceAll("(?m)^\\s*folia-supported:.*$", "folia-supported: true");
        } else {
            return pluginYml.trim() + "\nfolia-supported: true\n";
        }
    }

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

    public static boolean isFoliaSupported(File jarFile) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jarFile.toPath()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("plugin.yml")) {
                    String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    return content.lines().anyMatch(line -> line.trim().equalsIgnoreCase("folia-supported: true"));
                }
            }
        }
        return false;
    }
}
