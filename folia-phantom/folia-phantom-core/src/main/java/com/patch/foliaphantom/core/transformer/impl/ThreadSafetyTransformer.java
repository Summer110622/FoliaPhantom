/*
 * Folia Phantom - Thread Safety Class Transformer
 * 
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.*;
import java.util.logging.Logger;

/**
 * Transforms thread-unsafe method calls into safe versions.
 * 
 * <p>
 * Specifically targets Block.setType and similar calls that must be
 * executed on the correct region thread in Folia.
 * </p>
 */
public class ThreadSafetyTransformer implements ClassTransformer {
    private final Logger logger;
    private final String relocatedPatcherPath;

    public ThreadSafetyTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new ThreadSafetyVisitor(next, relocatedPatcherPath);
    }

    private static class ThreadSafetyVisitor extends ClassVisitor {
        private final String patcherPath;
        private String ownerName;
        private String pluginField = null;
        private boolean isPluginClass = false;

        public ThreadSafetyVisitor(ClassVisitor cv, String patcherPath) {
            super(Opcodes.ASM9, cv);
            this.patcherPath = patcherPath;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.ownerName = name;
            if (superName != null && superName.equals("org/bukkit/plugin/java/JavaPlugin")) {
                isPluginClass = true;
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (descriptor != null && descriptor.equals("Lorg/bukkit/plugin/Plugin;")) {
                pluginField = name;
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            return new ThreadSafetyMethodVisitor(super.visitMethod(access, name, desc, sig, ex), patcherPath, ownerName, pluginField, isPluginClass);
        }
    }

    private static class ThreadSafetyMethodVisitor extends MethodVisitor {
        private final String patcherPath;
        private final String ownerName;
        private final String pluginField;
        private final boolean isPluginClass;

        public ThreadSafetyMethodVisitor(MethodVisitor mv, String patcherPath, String ownerName, String pluginField, boolean isPluginClass) {
            super(Opcodes.ASM9, mv);
            this.patcherPath = patcherPath;
            this.ownerName = ownerName;
            this.pluginField = pluginField;
            this.isPluginClass = isPluginClass;
        }

        private void loadPluginInstance() {
            if (isPluginClass) {
                super.visitVarInsn(Opcodes.ALOAD, 0); // this
            } else if (pluginField != null) {
                super.visitVarInsn(Opcodes.ALOAD, 0);
                super.visitFieldInsn(Opcodes.GETFIELD, ownerName, pluginField, "Lorg/bukkit/plugin/Plugin;");
            } else {
                 // Fallback to ALOAD 0 is too risky. If we can't find the plugin, we shouldn't patch.
                 // This logic is handled by ScanningClassVisitor now.
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            // Redirect Block.setType
            if ("org/bukkit/block/Block".equals(owner) && name.equals("setType")) {
                if ("(Lorg/bukkit/Material;)V".equals(desc)) {
                    loadPluginInstance();
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, patcherPath, "safeSetType",
                            "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/block/Block;Lorg/bukkit/Material;)V", false);
                    return;
                } else if ("(Lorg/bukkit/Material;Z)V".equals(desc)) {
                    loadPluginInstance();
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, patcherPath, "safeSetTypeWithPhysics",
                            "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/block/Block;Lorg/bukkit/Material;Z)V", false);
                    return;
                }
            }

            // Redirect Block.setBlockData
            if ("org/bukkit/block/Block".equals(owner) && name.equals("setBlockData") && "(Lorg/bukkit/block/data/BlockData;Z)V".equals(desc)) {
                loadPluginInstance();
                super.visitMethodInsn(Opcodes.INVOKESTATIC, patcherPath, "safeSetBlockData",
                        "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/block/Block;Lorg/bukkit/block/data/BlockData;Z)V", false);
                return;
            }

            // Redirect World.spawn
            if ("org/bukkit/World".equals(owner) && name.equals("spawn") && "(Lorg/bukkit/Location;Ljava/lang/Class;)Lorg/bukkit/entity/Entity;".equals(desc)) {
                loadPluginInstance();
                super.visitMethodInsn(Opcodes.INVOKESTATIC, patcherPath, "safeSpawnEntity",
                        "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;Ljava/lang/Class;)Lorg/bukkit/entity/Entity;", false);
                return;
            }

            // Redirect World.loadChunk
            if ("org/bukkit/World".equals(owner) && name.equals("loadChunk") && "(IIZ)V".equals(desc)) {
                loadPluginInstance();
                super.visitMethodInsn(Opcodes.INVOKESTATIC, patcherPath, "safeLoadChunk",
                        "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;IIZ)V", false);
                return;
            }

            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }
    }
}
