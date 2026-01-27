/*
 * Folia Phantom - ServerGetOfflinePlayer Transformer
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

public class ServerGetOfflinePlayerTransformer implements ClassTransformer {
    private final Logger logger;
    private final String relocatedPatcherPath;

    public ServerGetOfflinePlayerTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor parent) {
        return new ClassVisitor(Opcodes.ASM9, parent) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        if ("org/bukkit/Server".equals(owner) &&
                            "getOfflinePlayer".equals(name) &&
                            (descriptor.equals("(Ljava/lang/String;)Lorg/bukkit/OfflinePlayer;") || descriptor.equals("(Ljava/util/UUID;)Lorg/bukkit/OfflinePlayer;"))) {

                            logger.info("Redirecting " + owner + "#" + name + " to FoliaPatcher");

                            String newDescriptor = "(L" + owner + ";" + descriptor.substring(1);

                            super.visitMethodInsn(Opcodes.INVOKESTATIC, relocatedPatcherPath + "/FoliaPatcher", name, newDescriptor, false);
                        } else {
                            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                        }
                    }
                };
            }
        };
    }
}
