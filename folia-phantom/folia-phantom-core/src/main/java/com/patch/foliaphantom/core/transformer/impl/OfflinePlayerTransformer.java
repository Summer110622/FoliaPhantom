/*
 * Folia Phantom - OfflinePlayerTransformer
 *
 * Copyright (c) 2024 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.logging.Logger;

/**
 * Transforms calls to {@code Bukkit.getOfflinePlayer()} to use the thread-safe
 * FoliaPatcher helper methods.
 */
public class OfflinePlayerTransformer implements ClassTransformer {

    private final Logger logger;
    private final String relocatedPatcherPath;

    public OfflinePlayerTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath + "/FoliaPatcher";
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor cv) {
        return new OfflinePlayerClassVisitor(cv);
    }

    private class OfflinePlayerClassVisitor extends ClassVisitor {
        public OfflinePlayerClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            return new OfflinePlayerMethodVisitor(mv);
        }
    }

    private class OfflinePlayerMethodVisitor extends MethodVisitor {
        public OfflinePlayerMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if (opcode == Opcodes.INVOKESTATIC &&
                "org/bukkit/Bukkit".equals(owner) &&
                "getOfflinePlayer".equals(name)) {

                if ("(Ljava/lang/String;)Lorg/bukkit/OfflinePlayer;".equals(desc)) {
                    logger.fine("Redirecting Bukkit.getOfflinePlayer(String) to FoliaPatcher");
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        relocatedPatcherPath,
                        "getOfflinePlayerByName",
                        "(Ljava/lang/String;)Lorg/bukkit/OfflinePlayer;",
                        false
                    );
                    return;
                } else if ("(Ljava/util/UUID;)Lorg/bukkit/OfflinePlayer;".equals(desc)) {
                    logger.fine("Redirecting Bukkit.getOfflinePlayer(UUID) to FoliaPatcher");
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        relocatedPatcherPath,
                        "getOfflinePlayerByUUID",
                        "(Ljava/util/UUID;)Lorg/bukkit/OfflinePlayer;",
                        false
                    );
                    return;
                }
            }
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }
    }
}
