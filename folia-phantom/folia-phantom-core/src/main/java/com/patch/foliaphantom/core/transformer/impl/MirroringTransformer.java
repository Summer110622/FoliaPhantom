/*
 * Folia Phantom - Mirroring Transformer
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
 * Consolidates all mirroring-related transformations for high performance.
 * This includes online players, worlds, getPlayer, and getWorld calls.
 * Uses HP-PWM (High-Performance Player & World Mirroring) for O(1) thread-safe access.
 */
public class MirroringTransformer implements ClassTransformer {

    private final Logger logger;
    private final String relocatedPatcherPath;
    private boolean hasTransformed = false;

    public MirroringTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor classVisitor) {
        return new MirroringClassVisitor(classVisitor);
    }

    private class MirroringClassVisitor extends ClassVisitor {
        private String className;

        public MirroringClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name;
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new MirroringMethodVisitor(mv, access, name, descriptor);
        }

        private class MirroringMethodVisitor extends AdviceAdapter {
            MirroringMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
                super(Opcodes.ASM9, mv, access, name, desc);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                boolean isBukkit = "org/bukkit/Bukkit".equals(owner);
                boolean isServer = "org/bukkit/Server".equals(owner);
                boolean isWorld = "org/bukkit/World".equals(owner);

                if (isBukkit || isServer) {
                    if ("getOnlinePlayers".equals(name) && "()Ljava/util/Collection;".equals(desc)) {
                        if (isServer) pop();
                        super.visitMethodInsn(INVOKESTATIC, relocatedPatcherPath + "/FoliaPatcher", "_o", "()Ljava/util/Collection;", false);
                        hasTransformed = true;
                        return;
                    }
                    if ("getWorlds".equals(name) && "()Ljava/util/List;".equals(desc)) {
                        if (isServer) pop();
                        super.visitMethodInsn(INVOKESTATIC, relocatedPatcherPath + "/FoliaPatcher", "_w", "()Ljava/util/List;", false);
                        hasTransformed = true;
                        return;
                    }
                    if ("getPlayer".equals(name)) {
                        if ("(Ljava/lang/String;)Lorg/bukkit/entity/Player;".equals(desc)) {
                            if (isServer) pop();
                            super.visitMethodInsn(INVOKESTATIC, relocatedPatcherPath + "/FoliaPatcher", "_ps", "(Ljava/lang/String;)Lorg/bukkit/entity/Player;", false);
                            hasTransformed = true;
                            return;
                        }
                        if ("(Ljava/util/UUID;)Lorg/bukkit/entity/Player;".equals(desc)) {
                            if (isServer) pop();
                            super.visitMethodInsn(INVOKESTATIC, relocatedPatcherPath + "/FoliaPatcher", "_pu", "(Ljava/util/UUID;)Lorg/bukkit/entity/Player;", false);
                            hasTransformed = true;
                            return;
                        }
                    }
                    if ("getWorld".equals(name)) {
                        if ("(Ljava/lang/String;)Lorg/bukkit/World;".equals(desc)) {
                            if (isServer) pop();
                            super.visitMethodInsn(INVOKESTATIC, relocatedPatcherPath + "/FoliaPatcher", "_ws", "(Ljava/lang/String;)Lorg/bukkit/World;", false);
                            hasTransformed = true;
                            return;
                        }
                        if ("(Ljava/util/UUID;)Lorg/bukkit/World;".equals(desc)) {
                            if (isServer) pop();
                            super.visitMethodInsn(INVOKESTATIC, relocatedPatcherPath + "/FoliaPatcher", "_wu", "(Ljava/util/UUID;)Lorg/bukkit/World;", false);
                            hasTransformed = true;
                            return;
                        }
                    }
                }

                if (isWorld && "getPlayers".equals(name) && "()Ljava/util/List;".equals(desc)) {
                    super.visitMethodInsn(INVOKESTATIC, relocatedPatcherPath + "/FoliaPatcher", "_wp", "(Lorg/bukkit/World;)Ljava/util/List;", false);
                    hasTransformed = true;
                    return;
                }

                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }
        }
    }

    public boolean hasTransformed() {
        return hasTransformed;
    }
}
