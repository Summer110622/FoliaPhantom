/*
 * Folia Phantom - Mirroring Transformer
 *
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import java.util.logging.Logger;

/**
 * Hyper-performance mirroring transformer for players and worlds.
 * Redirects Bukkit/Server calls to cached mirrors in FoliaPatcher.
 */
public class MirroringTransformer implements ClassTransformer {
    private final Logger logger;
    private final String relocatedPatcherPath;
    private static final String PATCHER_CLASS = "FoliaPatcher";

    public MirroringTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new MirroringVisitor(next, relocatedPatcherPath + "/" + PATCHER_CLASS);
    }

    private class MirroringVisitor extends ClassVisitor {
        private final String patcherPath;

        public MirroringVisitor(ClassVisitor cv, String patcherPath) {
            super(Opcodes.ASM9, cv);
            this.patcherPath = patcherPath;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            return new MirroringMethodVisitor(mv, access, name, desc, patcherPath);
        }
    }

    private class MirroringMethodVisitor extends AdviceAdapter {
        private final String patcherPath;

        protected MirroringMethodVisitor(MethodVisitor mv, int access, String name, String desc, String patcherPath) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherPath = patcherPath;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            boolean isBukkit = "org/bukkit/Bukkit".equals(owner);
            boolean isServer = "org/bukkit/Server".equals(owner);

            if (isBukkit || isServer) {
                if (name.equals("getOnlinePlayers") && desc.equals("()Ljava/util/Collection;")) {
                    if (opcode == INVOKEINTERFACE) pop();
                    super.visitMethodInsn(INVOKESTATIC, patcherPath, "_o", "()Ljava/util/Collection;", false);
                    return;
                }
                if (name.equals("getWorlds") && desc.equals("()Ljava/util/List;")) {
                    if (opcode == INVOKEINTERFACE) pop();
                    super.visitMethodInsn(INVOKESTATIC, patcherPath, "_w", "()Ljava/util/List;", false);
                    return;
                }
                if (name.equals("getPlayer")) {
                    if (desc.equals("(Ljava/lang/String;)Lorg/bukkit/entity/Player;")) {
                        if (opcode == INVOKEINTERFACE) {
                            int loc = newLocal(Type.getType(String.class));
                            storeLocal(loc);
                            pop();
                            loadLocal(loc);
                        }
                        super.visitMethodInsn(INVOKESTATIC, patcherPath, "_ps", "(Ljava/lang/String;)Lorg/bukkit/entity/Player;", false);
                        return;
                    }
                    if (desc.equals("(Ljava/util/UUID;)Lorg/bukkit/entity/Player;")) {
                        if (opcode == INVOKEINTERFACE) {
                            int loc = newLocal(Type.getType(java.util.UUID.class));
                            storeLocal(loc);
                            pop();
                            loadLocal(loc);
                        }
                        super.visitMethodInsn(INVOKESTATIC, patcherPath, "_pu", "(Ljava/util/UUID;)Lorg/bukkit/entity/Player;", false);
                        return;
                    }
                }
                if (name.equals("getWorld")) {
                    if (desc.equals("(Ljava/lang/String;)Lorg/bukkit/World;")) {
                        if (opcode == INVOKEINTERFACE) {
                            int loc = newLocal(Type.getType(String.class));
                            storeLocal(loc);
                            pop();
                            loadLocal(loc);
                        }
                        super.visitMethodInsn(INVOKESTATIC, patcherPath, "_ws", "(Ljava/lang/String;)Lorg/bukkit/World;", false);
                        return;
                    }
                    if (desc.equals("(Ljava/util/UUID;)Lorg/bukkit/World;")) {
                        if (opcode == INVOKEINTERFACE) {
                            int loc = newLocal(Type.getType(java.util.UUID.class));
                            storeLocal(loc);
                            pop();
                            loadLocal(loc);
                        }
                        super.visitMethodInsn(INVOKESTATIC, patcherPath, "_wu", "(Ljava/util/UUID;)Lorg/bukkit/World;", false);
                        return;
                    }
                }
            }

            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }
    }
}
