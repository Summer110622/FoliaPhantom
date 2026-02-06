/*
 * Folia Phantom - Plugin Patcher Core
 *
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core;

import com.patch.foliaphantom.core.progress.PatchProgressListener;
import com.patch.foliaphantom.core.transformer.ClassTransformer;
import com.patch.foliaphantom.core.transformer.ScanningClassVisitor;
import com.patch.foliaphantom.core.transformer.impl.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class PluginPatcher {
  private final Logger logger;
  private final boolean failFastOnTimeout;
  private final boolean aggressiveEventOptimization;
  private final boolean fireAndForget;
  private final long apiTimeoutMs;
  private final Set<String> fireAndForgetEvents;
  private final Set<String> asyncEventHandlers;
  private final PatchProgressListener progressListener;
  private String relocatedPatcherPath;
  private List<ClassTransformer> visitorTransformers;
  private List<AsyncEventHandlerTransformer> nodeTransformers;
  private final AtomicInteger classesScanned = new AtomicInteger(0);
  private final AtomicInteger classesTransformed = new AtomicInteger(0);
  private final AtomicInteger classesSkipped = new AtomicInteger(0);

  private static final PatchProgressListener NULL_LISTENER = new PatchProgressListener() {
    @Override public void onPatchStart(File originalJar, File outputJar) {}
    @Override public void onClassTransform(String className, int classIndex, int totalClasses) {}
    @Override public void onProgressUpdate(int percentage, String message) {}
    @Override public void onComplete(long durationMillis, int[] statistics, Throwable error) {}
  };

  public PluginPatcher(Logger logger, PatchProgressListener progressListener, boolean failFastOnTimeout, boolean aggressiveEventOptimization, boolean fireAndForget, long apiTimeoutMs, Set<String> fireAndForgetEvents, Set<String> asyncEventHandlers) {
    this.logger = logger;
    this.progressListener = progressListener != null ? progressListener : NULL_LISTENER;
    this.failFastOnTimeout = failFastOnTimeout;
    this.aggressiveEventOptimization = aggressiveEventOptimization;
    this.fireAndForget = fireAndForget;
    this.apiTimeoutMs = apiTimeoutMs;
    this.fireAndForgetEvents = fireAndForgetEvents != null ? fireAndForgetEvents : Collections.emptySet();
    this.asyncEventHandlers = asyncEventHandlers != null ? asyncEventHandlers : Collections.emptySet();
  }

  public PluginPatcher(Logger logger, PatchProgressListener progressListener, boolean failFastOnTimeout, boolean aggressiveEventOptimization, boolean fireAndForget, long apiTimeoutMs, Set<String> fireAndForgetEvents) {
    this(logger, progressListener, failFastOnTimeout, aggressiveEventOptimization, fireAndForget, apiTimeoutMs, fireAndForgetEvents, null);
  }

  public PluginPatcher(Logger logger, PatchProgressListener progressListener, boolean failFastOnTimeout, boolean aggressiveEventOptimization, boolean fireAndForget, long apiTimeoutMs) {
    this(logger, progressListener, failFastOnTimeout, aggressiveEventOptimization, fireAndForget, apiTimeoutMs, null, null);
  }

  public PluginPatcher(Logger logger, PatchProgressListener progressListener, boolean failFastOnTimeout, boolean aggressiveEventOptimization, boolean fireAndForget) {
    this(logger, progressListener, failFastOnTimeout, aggressiveEventOptimization, fireAndForget, 100L, null);
  }

  public PluginPatcher(Logger logger, PatchProgressListener progressListener, boolean failFastOnTimeout, boolean aggressiveEventOptimization) {
    this(logger, progressListener, failFastOnTimeout, aggressiveEventOptimization, false, 100L, null);
  }

  public PluginPatcher(Logger logger, PatchProgressListener progressListener, boolean failFastOnTimeout) {
    this(logger, progressListener, failFastOnTimeout, false, false, 100L, null);
  }

  public PluginPatcher(Logger logger, PatchProgressListener progressListener) {
    this(logger, progressListener, false, false, false, 100L, null);
  }

  public PluginPatcher(Logger logger) {
    this(logger, null, false, false, false, 100L, null);
  }

  public void patchPlugin(File originalJar, File outputJar) throws IOException {
    classesScanned.set(0);
    classesTransformed.set(0);
    classesSkipped.set(0);
    progressListener.onPatchStart(originalJar, outputJar);
    long startTime = System.currentTimeMillis();
    try {
      String pluginName = getPluginNameFromJar(originalJar);
      if (pluginName == null) throw new IOException("Could not find plugin name from plugin.yml");
      String safePluginName = pluginName.toLowerCase().replaceAll("[^a-z0-9]", "");
      this.relocatedPatcherPath = safePluginName + "/folia/runtime";
      this.visitorTransformers = new ArrayList<>();
      visitorTransformers.add(new ServerBroadcastMessageTransformer(logger, relocatedPatcherPath));
      visitorTransformers.add(new MirroringTransformer(logger, relocatedPatcherPath));
      visitorTransformers.add(new WorldGetPlayersTransformer(logger, relocatedPatcherPath));
      visitorTransformers.add(new WorldGetHighestBlockAtTransformer(logger, relocatedPatcherPath));
      visitorTransformers.add(new WorldSpawnEntityTransformer(logger, relocatedPatcherPath));
      visitorTransformers.add(new EventHandlerTransformer(logger, relocatedPatcherPath));
      visitorTransformers.add(new TeleportTransformer(logger, relocatedPatcherPath));
      visitorTransformers.add(new PlayerHealthTransformer(logger, relocatedPatcherPath));
      visitorTransformers.add(new ThreadSafetyTransformer(logger, relocatedPatcherPath));
      visitorTransformers.add(new PlayerTransformer(logger, relocatedPatcherPath));
      visitorTransformers.add(new InventoryTransformer(logger, relocatedPatcherPath));
      visitorTransformers.add(new WorldGenClassTransformer(logger, relocatedPatcherPath));
      visitorTransformers.add(new EntitySchedulerTransformer(logger, relocatedPatcherPath));
      visitorTransformers.add(new ScoreboardTransformer(logger, relocatedPatcherPath));
      visitorTransformers.add(new SchedulerClassTransformer(logger, relocatedPatcherPath));
      visitorTransformers.add(new EventCallTransformer(logger, relocatedPatcherPath));
      visitorTransformers.add(new EventFireAndForgetTransformer(logger, relocatedPatcherPath));
      visitorTransformers.add(new ServerVersionTransformer(logger, relocatedPatcherPath));
      visitorTransformers.add(new PluginEnableTransformer(logger, relocatedPatcherPath));
      visitorTransformers.add(new CommandDispatchTransformer(logger, relocatedPatcherPath));
      visitorTransformers.add(new OfflinePlayerTransformer(logger, relocatedPatcherPath));
      this.nodeTransformers = new ArrayList<>();
      nodeTransformers.add(new AsyncEventHandlerTransformer(logger, relocatedPatcherPath, asyncEventHandlers));
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

  private void createPatchedJar(Path source, Path destination) throws IOException {
    Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
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
      Map<Path, Future<ClassPatchResult>> futures = new HashMap<>();
      for (Path path : classFiles) {
        byte[] originalBytes = Files.readAllBytes(path);
        classesScanned.incrementAndGet();
        futures.put(path, executor.submit(() -> patchClass(originalBytes, path.toString())));
      }
      int processedCount = 0;
      for (Map.Entry<Path, Future<ClassPatchResult>> entry : futures.entrySet()) {
        try {
          ClassPatchResult result = entry.getValue().get();
          if (result.wasTransformed) {
            Files.write(entry.getKey(), result.bytes);
            classesTransformed.incrementAndGet();
          } else classesSkipped.incrementAndGet();
          progressListener.onProgressUpdate((int) (50.0 * (++processedCount) / classFiles.size()), "Processed: " + entry.getKey());
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
          try { Files.delete(p); } catch (IOException e) { logger.log(Level.WARNING, "Failed to delete signature file: " + p, e); }
        });
      }
    }
  }

  private void bundleFoliaPatcherClasses(FileSystem zipfs) throws IOException {
    String originalPatcherPath = "com/patch/foliaphantom/core/patcher";
    SimpleRemapper remapper = new SimpleRemapper(originalPatcherPath, this.relocatedPatcherPath);
    String[] classesToBundle = {"FoliaPatcher.class", "FoliaPatcher$FoliaBukkitTask.class", "FoliaPatcher$FoliaChunkGenerator.class"};
    Path targetDir = zipfs.getPath(this.relocatedPatcherPath);
    Files.createDirectories(targetDir);
    for (String className : classesToBundle) {
      String originalClassPath = originalPatcherPath + "/" + className;
      Path targetPath = targetDir.resolve(className);
      try (InputStream is = getClass().getClassLoader().getResourceAsStream(originalClassPath)) {
        if (is == null) { logger.warning("[FoliaPhantom] Runtime class not found: " + originalClassPath); continue; }
        ClassReader cr = new ClassReader(is);
        ClassWriter cw = new ClassWriter(0);
        ClassVisitor cv = new ClassRemapper(cw, remapper);
        if (originalClassPath.equals("com/patch/foliaphantom/core/patcher/FoliaPatcher.class")) {
          final ClassVisitor nextVisitor = cv;
          cv = new ClassVisitor(Opcodes.ASM9, nextVisitor) {
            @Override public void visitEnd() {
              FieldVisitor fv = super.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "FAIL_FAST", "Z", null, failFastOnTimeout ? 1 : 0);
              if (fv != null) fv.visitEnd();
              fv = super.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "AGGRESSIVE_EVENT_OPTIMIZATION", "Z", null, aggressiveEventOptimization ? 1 : 0);
              if (fv != null) fv.visitEnd();
              fv = super.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "FIRE_AND_FORGET", "Z", null, fireAndForget ? 1 : 0);
              if (fv != null) fv.visitEnd();
              fv = super.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "API_TIMEOUT_MS", "J", null, apiTimeoutMs);
              if (fv != null) fv.visitEnd();
              super.visitEnd();
            }
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
              MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
              if (name.equals("<clinit>")) {
                return new MethodVisitor(Opcodes.ASM9, mv) {
                  @Override public void visitCode() {
                    super.visitCode();
                    super.visitTypeInsn(Opcodes.NEW, "java/util/HashSet");
                    super.visitInsn(Opcodes.DUP);
                    super.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashSet", "<init>", "()V", false);
                    for (String eventName : fireAndForgetEvents) {
                      super.visitInsn(Opcodes.DUP);
                      super.visitLdcInsn(eventName);
                      super.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Set", "add", "(Ljava/lang/Object;)Z", true);
                      super.visitInsn(Opcodes.POP);
                    }
                    super.visitFieldInsn(Opcodes.PUTSTATIC, relocatedPatcherPath + "/FoliaPatcher", "FIRE_AND_FORGET_EVENTS", "Ljava/util/Set;");
                  }
                };
              }
              return mv;
            }
          };
        }
        cr.accept(cv, ClassReader.EXPAND_FRAMES);
        Files.write(targetPath, cw.toByteArray());
        logger.fine("Successfully bundled and relocated " + targetPath);
      }
    }
  }

  private ClassPatchResult patchClass(byte[] originalBytes, String className) {
    try {
      ClassReader cr = new ClassReader(originalBytes);
      ScanningClassVisitor scanner = new ScanningClassVisitor(relocatedPatcherPath);
      cr.accept(scanner, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
      if (!scanner.needsPatching()) return new ClassPatchResult(originalBytes, false);
      ClassWriter cw = new ClassWriter(cr, 0);
      ClassVisitor cv = cw;
      for (int i = visitorTransformers.size() - 1; i >= 0; i--) cv = visitorTransformers.get(i).createVisitor(cv);
      cr.accept(cv, ClassReader.EXPAND_FRAMES);
      byte[] intermediateBytes = cw.toByteArray();
      boolean transformed = !java.util.Arrays.equals(originalBytes, intermediateBytes);
      byte[] finalBytes = intermediateBytes;
      for (AsyncEventHandlerTransformer transformer : nodeTransformers) finalBytes = transformer.transform(finalBytes);
      if (!java.util.Arrays.equals(intermediateBytes, finalBytes)) transformed = true;
      if (transformed) logger.fine("[FoliaPhantom] Transformed: " + className);
      return new ClassPatchResult(finalBytes, transformed);
    } catch (Exception e) {
      logger.log(Level.WARNING, "[FoliaPhantom] Failed to transform " + className + ", using original", e);
      return new ClassPatchResult(originalBytes, false);
    }
  }

  private String addFoliaSupportedFlag(String pluginYml) {
    if (pluginYml.lines().anyMatch(line -> line.trim().startsWith("folia-supported:"))) return pluginYml.replaceAll("(?m)^\s*folia-supported:.*$", "folia-supported: true");
    else return pluginYml.trim() + "\nfolia-supported: true\n";
  }

  public static String getPluginNameFromJar(File jarFile) throws IOException {
    try (FileSystem zipfs = FileSystems.newFileSystem(jarFile.toPath(), Collections.emptyMap())) {
      Path pluginYmlPath = zipfs.getPath("plugin.yml");
      if (Files.exists(pluginYmlPath)) return Files.readAllLines(pluginYmlPath).stream().map(String::trim).filter(line -> line.startsWith("name:")).findFirst().map(line -> line.substring(line.indexOf(":") + 1).trim()).orElse(null);
    }
    return null;
  }

  public static boolean isFoliaSupported(File jarFile) throws IOException {
    try (FileSystem zipfs = FileSystems.newFileSystem(jarFile.toPath(), Collections.emptyMap())) {
      Path pluginYmlPath = zipfs.getPath("plugin.yml");
      if (Files.exists(pluginYmlPath)) return Files.readAllLines(pluginYmlPath).stream().anyMatch(line -> line.trim().equalsIgnoreCase("folia-supported: true"));
    }
    return false;
  }

  public int[] getStatistics() { return new int[] {classesScanned.get(), classesTransformed.get(), classesSkipped.get()}; }

  private static class ClassPatchResult {
    final byte[] bytes;
    final boolean wasTransformed;
    ClassPatchResult(byte[] bytes, boolean wasTransformed) { this.bytes = bytes; this.wasTransformed = wasTransformed; }
  }
}
