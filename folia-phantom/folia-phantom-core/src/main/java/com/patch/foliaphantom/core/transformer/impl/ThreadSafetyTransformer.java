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

        public ThreadSafetyVisitor(ClassVisitor cv, String patcherPath) {
            super(Opcodes.ASM9, cv);
            this.patcherPath = patcherPath;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            return new ThreadSafetyMethodVisitor(super.visitMethod(access, name, desc, sig, ex), patcherPath);
        }
    }

    private static class ThreadSafetyMethodVisitor extends MethodVisitor {
        private final String patcherPath;

        public ThreadSafetyMethodVisitor(MethodVisitor mv, String patcherPath) {
            super(Opcodes.ASM9, mv);
            this.patcherPath = patcherPath;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            // Redirect Block.setType
            if ("org/bukkit/block/Block".equals(owner) && name.equals("setType")) {
                if ("(Lorg/bukkit/Material;)V".equals(desc)) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, patcherPath, "safeSetType",
                            "(Lorg/bukkit/block/Block;Lorg/bukkit/Material;)V", false);
                    return;
                } else if ("(Lorg/bukkit/Material;Z)V".equals(desc)) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, patcherPath, "safeSetTypeWithPhysics",
                            "(Lorg/bukkit/block/Block;Lorg/bukkit/Material;Z)V", false);
                    return;
                }
            }

            // Redirect Block.setBlockData
            if ("org/bukkit/block/Block".equals(owner) && name.equals("setBlockData") && "(Lorg/bukkit/block/data/BlockData;Z)V".equals(desc)) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, patcherPath, "safeSetBlockData",
                        "(Lorg/bukkit/block/Block;Lorg/bukkit/block/data/BlockData;Z)V", false);
                return;
            }

            // Redirect World.spawn
            if ("org/bukkit/World".equals(owner) && name.equals("spawn") && "(Lorg/bukkit/Location;Ljava/lang/Class;)Lorg/bukkit/entity/Entity;".equals(desc)) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, patcherPath, "safeSpawnEntity",
                        "(Lorg/bukkit/World;Lorg/bukkit/Location;Ljava/lang/Class;)Lorg/bukkit/entity/Entity;", false);
                return;
            }

            // Redirect World.loadChunk
            if ("org/bukkit/World".equals(owner) && name.equals("loadChunk") && "(IIZ)V".equals(desc)) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, patcherPath, "safeLoadChunk",
                        "(Lorg/bukkit/World;IIZ)V", false);
                return;
            }


            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }
    }
}
