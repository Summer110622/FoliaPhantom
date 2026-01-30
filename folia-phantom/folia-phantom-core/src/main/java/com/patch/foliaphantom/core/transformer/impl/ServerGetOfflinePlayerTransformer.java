/*
 * Folia Phantom - Server#getOfflinePlayer() Transformer
 *
 * Copyright (c) 2024 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.AdviceAdapter;

import java.util.logging.Logger;

/**
 * Transforms {@code Bukkit#getOfflinePlayer(...)} calls to a thread-safe equivalent
 * in {@code FoliaPatcher}. This prevents plugins from using the blocking, deprecated
 * API from asynchronous threads, which can cause server hangs.
 */
public class ServerGetOfflinePlayerTransformer implements ClassTransformer {

    private final Logger logger;
    private final String relocatedPatcherPath;
    private boolean hasTransformed = false;

    public ServerGetOfflinePlayerTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor classVisitor) {
        return new ServerGetOfflinePlayerClassVisitor(classVisitor);
    }

    public boolean hasTransformed() {
        return hasTransformed;
    }

    private class ServerGetOfflinePlayerClassVisitor extends ClassVisitor {
        private String className;
        private boolean isJavaPlugin;
        private String pluginField;
        private String pluginFieldType;

        public ServerGetOfflinePlayerClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name;
            if ("org/bukkit/plugin/java/JavaPlugin".equals(superName)) {
                this.isJavaPlugin = true;
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public org.objectweb.asm.FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (descriptor.equals("Lorg/bukkit/plugin/Plugin;") || descriptor.equals("Lorg/bukkit/plugin/java/JavaPlugin;")) {
                if (this.pluginField == null) {
                    this.pluginField = name;
                    this.pluginFieldType = descriptor;
                }
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (isJavaPlugin || pluginField != null) {
                return new GetOfflinePlayerMethodVisitor(mv, access, name, descriptor);
            }
            return mv;
        }

        private class GetOfflinePlayerMethodVisitor extends AdviceAdapter {
            GetOfflinePlayerMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
                super(Opcodes.ASM9, mv, access, name, desc);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                boolean isBukkitGetOfflinePlayerByName = opcode == Opcodes.INVOKESTATIC
                    && "org/bukkit/Bukkit".equals(owner)
                    && "getOfflinePlayer".equals(name)
                    && "(Ljava/lang/String;)Lorg/bukkit/OfflinePlayer;".equals(desc);

                boolean isBukkitGetOfflinePlayerByUUID = opcode == Opcodes.INVOKESTATIC
                    && "org/bukkit/Bukkit".equals(owner)
                    && "getOfflinePlayer".equals(name)
                    && "(Ljava/util/UUID;)Lorg/bukkit/OfflinePlayer;".equals(desc);

                if (isBukkitGetOfflinePlayerByName) {
                    logger.fine("[FoliaPhantom] Transforming Bukkit#getOfflinePlayer(String) call in " + className);
                    // Stack before: [String]
                    loadPluginInstance(); // Stack: [String, Plugin]
                    swap(); // Stack: [Plugin, String]
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        relocatedPatcherPath + "/FoliaPatcher",
                        "safeGetOfflinePlayer",
                        "(Lorg/bukkit/plugin/Plugin;Ljava/lang/String;)Lorg/bukkit/OfflinePlayer;",
                        false
                    );
                    hasTransformed = true;
                } else if (isBukkitGetOfflinePlayerByUUID) {
                    logger.fine("[FoliaPhantom] Transforming Bukkit#getOfflinePlayer(UUID) call in " + className);
                    // Stack before: [UUID]
                    loadPluginInstance(); // Stack: [UUID, Plugin]
                    swap(); // Stack: [Plugin, UUID]
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        relocatedPatcherPath + "/FoliaPatcher",
                        "safeGetOfflinePlayer",
                        "(Lorg/bukkit/plugin/Plugin;Ljava/util/UUID;)Lorg/bukkit/OfflinePlayer;",
                        false
                    );
                    hasTransformed = true;
                } else {
                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                }
            }

            private void loadPluginInstance() {
                if (isJavaPlugin) {
                    visitVarInsn(ALOAD, 0); // this
                } else if (pluginField != null) {
                    visitVarInsn(ALOAD, 0); // this
                    visitFieldInsn(GETFIELD, className, pluginField, pluginFieldType);
                } else {
                    throw new IllegalStateException("Cannot transform getOfflinePlayer call in " + className + ": No plugin instance found.");
                }
            }
        }
    }
}
