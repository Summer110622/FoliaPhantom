/*
 * Folia Phantom - Offline Player Transformer
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

public class OfflinePlayerTransformer implements ClassTransformer {

    private final Logger logger;
    private final String relocatedPatcherPath;

    public OfflinePlayerTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
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
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new OfflinePlayerMethodVisitor(mv);
        }
    }

    private class OfflinePlayerMethodVisitor extends MethodVisitor {
        public OfflinePlayerMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKESTATIC &&
                "org/bukkit/Bukkit".equals(owner) &&
                "getOfflinePlayer".equals(name) &&
                ("(Ljava/lang/String;)Lorg/bukkit/OfflinePlayer;".equals(descriptor) || "(Ljava/util/UUID;)Lorg/bukkit/OfflinePlayer;".equals(descriptor))) {

                logger.fine("Redirecting Bukkit.getOfflinePlayer call to FoliaPatcher.safeGetOfflinePlayer");
                super.visitMethodInsn(Opcodes.INVOKESTATIC, relocatedPatcherPath + "/FoliaPatcher", "safeGetOfflinePlayer", descriptor, false);
            } else {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        }
    }
}
