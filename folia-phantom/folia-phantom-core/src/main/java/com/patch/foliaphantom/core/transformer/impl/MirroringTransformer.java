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
 * Consistently mirrors common Bukkit/Server lookups to FoliaPatcher's high-performance cache.
 */
public class MirroringTransformer implements ClassTransformer {
    private final String relocatedPatcherPath;

    public MirroringTransformer(Logger logger, String relocatedPatcherPath) {
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new MirroringClassVisitor(next, relocatedPatcherPath + "/FoliaPatcher");
    }

    private static class MirroringClassVisitor extends ClassVisitor {
        private final String patcherPath;

        public MirroringClassVisitor(ClassVisitor cv, String patcherPath) {
            super(Opcodes.ASM9, cv);
            this.patcherPath = patcherPath;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            return new MirroringMethodVisitor(mv, access, name, desc, patcherPath);
        }
    }

    private static class MirroringMethodVisitor extends AdviceAdapter {
        private final String patcherPath;

        protected MirroringMethodVisitor(MethodVisitor mv, int access, String name, String desc, String patcherPath) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherPath = patcherPath;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if (("org/bukkit/Bukkit".equals(owner) && opcode == INVOKESTATIC) || ("org/bukkit/Server".equals(owner) && opcode == INVOKEINTERFACE)) {
                if ("getOnlinePlayers".equals(name) && "()Ljava/util/Collection;".equals(desc)) {
                    if (opcode == INVOKEINTERFACE) pop();
                    super.visitMethodInsn(INVOKESTATIC, patcherPath, "_o", desc, false);
                    return;
                }
                if ("getWorlds".equals(name) && "()Ljava/util/List;".equals(desc)) {
                    if (opcode == INVOKEINTERFACE) pop();
                    super.visitMethodInsn(INVOKESTATIC, patcherPath, "_w", desc, false);
                    return;
                }
                if ("getPlayer".equals(name)) {
                    if ("(Ljava/lang/String;)Lorg/bukkit/entity/Player;".equals(desc)) {
                        if (opcode == INVOKEINTERFACE) {
                            int local = newLocal(Type.getType(String.class));
                            storeLocal(local);
                            pop();
                            loadLocal(local);
                        }
                        super.visitMethodInsn(INVOKESTATIC, patcherPath, "_ps", desc, false);
                        return;
                    }
                    if ("(Ljava/util/UUID;)Lorg/bukkit/entity/Player;".equals(desc)) {
                        if (opcode == INVOKEINTERFACE) {
                            int local = newLocal(Type.getType(java.util.UUID.class));
                            storeLocal(local);
                            pop();
                            loadLocal(local);
                        }
                        super.visitMethodInsn(INVOKESTATIC, patcherPath, "_pu", "(Ljava/util/UUID;)Lorg/bukkit/entity/Player;", false);
                        return;
                    }
                }
                if ("getWorld".equals(name)) {
                    if ("(Ljava/lang/String;)Lorg/bukkit/World;".equals(desc)) {
                        if (opcode == INVOKEINTERFACE) {
                            int local = newLocal(Type.getType(String.class));
                            storeLocal(local);
                            pop();
                            loadLocal(local);
                        }
                        super.visitMethodInsn(INVOKESTATIC, patcherPath, "_ws", desc, false);
                        return;
                    }
                    if ("(Ljava/util/UUID;)Lorg/bukkit/World;".equals(desc)) {
                        if (opcode == INVOKEINTERFACE) {
                            int local = newLocal(Type.getType(java.util.UUID.class));
                            storeLocal(local);
                            pop();
                            loadLocal(local);
                        }
                        super.visitMethodInsn(INVOKESTATIC, patcherPath, "_wu", "(Ljava/util/UUID;)Lorg/bukkit/World;", false);
                        return;
                    }
                }
            }
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }
    }
}
