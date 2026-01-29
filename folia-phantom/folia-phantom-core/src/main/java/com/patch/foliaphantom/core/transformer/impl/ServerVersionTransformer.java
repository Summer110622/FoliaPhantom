/*
 * Folia Phantom - Server Version Transformer
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

public class ServerVersionTransformer implements ClassTransformer {
    private final Logger logger;
    private final String relocatedPatcherPath;

    public ServerVersionTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor cv) {
        return new ServerVersionClassVisitor(cv, relocatedPatcherPath);
    }

    private static class ServerVersionClassVisitor extends ClassVisitor {
        private final String relocatedPatcherPath;

        public ServerVersionClassVisitor(ClassVisitor cv, String relocatedPatcherPath) {
            super(Opcodes.ASM9, cv);
            this.relocatedPatcherPath = relocatedPatcherPath;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new ServerVersionMethodVisitor(mv, relocatedPatcherPath);
        }
    }

    private static class ServerVersionMethodVisitor extends MethodVisitor {
        private final String relocatedPatcherPath;

        public ServerVersionMethodVisitor(MethodVisitor mv, String relocatedPatcherPath) {
            super(Opcodes.ASM9, mv);
            this.relocatedPatcherPath = relocatedPatcherPath;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKESTATIC && owner.equals("org/bukkit/Bukkit")) {
                if (name.equals("getVersion") && descriptor.equals("()Ljava/lang/String;")) {
                    super.visitFieldInsn(Opcodes.GETSTATIC, relocatedPatcherPath + "/FoliaPatcher", "CACHED_SERVER_VERSION", "Ljava/lang/String;");
                    return;
                }
                if (name.equals("getBukkitVersion") && descriptor.equals("()Ljava/lang/String;")) {
                    super.visitFieldInsn(Opcodes.GETSTATIC, relocatedPatcherPath + "/FoliaPatcher", "CACHED_BUKKIT_VERSION", "Ljava/lang/String;");
                    return;
                }
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }
    }
}
