/*
 * Folia Phantom - GetOfflinePlayer Transformer
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

public class GetOfflinePlayerTransformer implements ClassTransformer {

    private final Logger logger;

    public GetOfflinePlayerTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor cv) {
        return new ClassVisitor(Opcodes.ASM9, cv) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        boolean isServerInterfaceCall = opcode == Opcodes.INVOKEINTERFACE
                                && owner.equals("org/bukkit/Server")
                                && name.equals("getOfflinePlayer")
                                && descriptor.equals("(Ljava/lang/String;)Lorg/bukkit/OfflinePlayer;");

                        boolean isBukkitStaticCall = opcode == Opcodes.INVOKESTATIC
                                && owner.equals("org/bukkit/Bukkit")
                                && name.equals("getOfflinePlayer")
                                && descriptor.equals("(Ljava/lang/String;)Lorg/bukkit/OfflinePlayer;");

                        if (isServerInterfaceCall) {
                            // The Server instance is already on the stack, proceed with async call
                            transformToAsync(mv);
                        } else if (isBukkitStaticCall) {
                            // Get the Server instance first, then proceed
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bukkit/Bukkit", "getServer", "()Lorg/bukkit/Server;", false);
                            mv.visitInsn(Opcodes.SWAP); // Swap the server instance and the string argument
                            transformToAsync(mv);
                        } else {
                            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                        }
                    }

                    private void transformToAsync(MethodVisitor mv) {
                         mv.visitMethodInsn(
                            Opcodes.INVOKEINTERFACE,
                            "org/bukkit/Server",
                            "getOfflinePlayerAsync",
                            "(Ljava/lang/String;)Ljava/util/concurrent/CompletableFuture;",
                            true
                        );
                        mv.visitMethodInsn(
                            Opcodes.INVOKEINTERFACE,
                            "java/util/concurrent/CompletableFuture",
                            "join",
                            "()Ljava/lang/Object;",
                            true
                        );
                        mv.visitTypeInsn(Opcodes.CHECKCAST, "org/bukkit/OfflinePlayer");
                    }
                };
            }
        };
    }
}
