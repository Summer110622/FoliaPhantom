/*
 * Folia Phantom - Player#isOnline() Transformer
 *
 * Copyright (c) 2024 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.AdviceAdapter;

import java.util.logging.Logger;

public class PlayerIsOnlineTransformer implements ClassTransformer {

    private final Logger logger;
    private final String relocatedPatcherPath;

    public PlayerIsOnlineTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor classVisitor) {
        return new PlayerIsOnlineClassVisitor(classVisitor);
    }

    private class PlayerIsOnlineClassVisitor extends ClassVisitor {
        private String className;

        public PlayerIsOnlineClassVisitor(ClassVisitor cv) {
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
            return new IsOnlineMethodVisitor(mv, access, name, descriptor);
        }

        private class IsOnlineMethodVisitor extends AdviceAdapter {
            IsOnlineMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
                super(Opcodes.ASM9, mv, access, name, desc);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                if (opcode == Opcodes.INVOKEINTERFACE &&
                    "org/bukkit/entity/Player".equals(owner) &&
                    "isOnline".equals(name) &&
                    "()Z".equals(desc)) {

                    logger.fine("[FoliaPhantom] Transforming Player#isOnline call in " + className);

                    // The stack before this instruction is: [playerInstance]
                    // We need to transform it to: [playerInstance] -> safeIsOnline(playerInstance)

                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        relocatedPatcherPath + "/FoliaPatcher",
                        "safeIsOnline",
                        "(Lorg/bukkit/entity/Player;)Z",
                        false
                    );
                } else {
                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                }
            }
        }
    }
}
