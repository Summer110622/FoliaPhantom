/*
 * Folia Phantom - World Generation Class Transformer
 * 
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.*;
import java.util.logging.Logger;

/**
 * Transforms world creation and generator related calls.
 * 
 * <p>
 * Redirects Bukkit.createWorld and related calls to async/safe alternatives
 * provided by FoliaPatcher.
 * </p>
 */
public class WorldGenClassTransformer implements ClassTransformer {
    private final Logger logger;
    private final String relocatedPatcherPath;

    public WorldGenClassTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new WorldGenVisitor(next, relocatedPatcherPath);
    }

    private static class WorldGenVisitor extends ClassVisitor {
        private final String patcherPath;

        public WorldGenVisitor(ClassVisitor cv, String patcherPath) {
            super(Opcodes.ASM9, cv);
            this.patcherPath = patcherPath;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            return new WorldGenMethodVisitor(super.visitMethod(access, name, desc, sig, ex), patcherPath);
        }
    }

    private static class WorldGenMethodVisitor extends MethodVisitor {
        private final String patcherPath;

        public WorldGenMethodVisitor(MethodVisitor mv, String patcherPath) {
            super(Opcodes.ASM9, mv);
            this.patcherPath = patcherPath;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            // Redirect Plugin.getDefaultWorldGenerator
            if ("org/bukkit/plugin/Plugin".equals(owner) && name.equals("getDefaultWorldGenerator")) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, patcherPath, name,
                        "(Lorg/bukkit/plugin/Plugin;" + desc.substring(1), false);
                return;
            }

            // Redirect Bukkit.createWorld or WorldCreator.createWorld
            if (name.equals("createWorld") && desc.equals("(Lorg/bukkit/WorldCreator;)Lorg/bukkit/World;")) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, patcherPath, "createWorld", desc, false);
                return;
            }

            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }
    }
}
