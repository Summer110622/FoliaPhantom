/*
 * Folia Phantom - OfflinePlayer Transformer
 *
 * Copyright (c) 2025 Marv
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
 * Transforms {@code Bukkit#getOfflinePlayer} and {@code Server#getOfflinePlayer}
 * calls to thread-safe equivalents in {@code FoliaPatcher}.
 */
public class OfflinePlayerTransformer implements ClassTransformer {

    private final Logger logger;
    private final String relocatedPatcherPath;
    private boolean hasTransformed = false;

    public OfflinePlayerTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor classVisitor) {
        return new OfflinePlayerClassVisitor(classVisitor);
    }

    public boolean hasTransformed() {
        return hasTransformed;
    }

    private class OfflinePlayerClassVisitor extends ClassVisitor {
        private String className;
        private boolean isJavaPlugin;
        private String pluginField;
        private String pluginFieldType;

        public OfflinePlayerClassVisitor(ClassVisitor cv) {
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
                return new OfflinePlayerMethodVisitor(mv, access, name, descriptor);
            }
            return mv;
        }

        private class OfflinePlayerMethodVisitor extends AdviceAdapter {
            OfflinePlayerMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
                super(Opcodes.ASM9, mv, access, name, desc);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                boolean isServerCall = opcode == Opcodes.INVOKEINTERFACE && "org/bukkit/Server".equals(owner);
                boolean isBukkitCall = opcode == Opcodes.INVOKESTATIC && "org/bukkit/Bukkit".equals(owner);

                if ((isServerCall || isBukkitCall) && "getOfflinePlayer".equals(name)) {
                    String patcherDesc = null;
                    if ("(Ljava/lang/String;)Lorg/bukkit/OfflinePlayer;".equals(desc)) {
                        patcherDesc = "(Lorg/bukkit/plugin/Plugin;Ljava/lang/String;)Lorg/bukkit/OfflinePlayer;";
                    } else if ("(Ljava/util/UUID;)Lorg/bukkit/OfflinePlayer;".equals(desc)) {
                        patcherDesc = "(Lorg/bukkit/plugin/Plugin;Ljava/util/UUID;)Lorg/bukkit/OfflinePlayer;";
                    }

                    if (patcherDesc != null) {
                        logger.fine("[FoliaPhantom] Transforming getOfflinePlayer call in " + className);
                        if (isServerCall) {
                            swap();
                            pop();
                        }
                        loadPluginInstance();
                        swap();
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, relocatedPatcherPath + "/FoliaPatcher", "safeGetOfflinePlayer", patcherDesc, false);
                        hasTransformed = true;
                        return;
                    }
                }
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }

            private void loadPluginInstance() {
                if (isJavaPlugin) {
                    visitVarInsn(ALOAD, 0);
                } else if (pluginField != null) {
                    visitVarInsn(ALOAD, 0);
                    visitFieldInsn(GETFIELD, className, pluginField, pluginFieldType);
                }
            }
        }
    }
}
