/*
 * Folia Phantom - Future Transformer
 *
 * This transformer converts blocking CompletableFuture.get() calls into non-blocking
 * "fire-and-forget" calls when the corresponding optimization flag is enabled.
 *
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import java.util.logging.Logger;

/**
 * Transforms {@code CompletableFuture.get()} calls to be non-blocking.
 *
 * <p>This transformer identifies calls to {@code java.util.concurrent.CompletableFuture.get()}
 * and replaces them with a conditional block. At runtime, this block checks the
 * dynamically-injected {@code FoliaPatcher.FIRE_AND_FORGET} flag. If true, it
 * calls a non-blocking {@code FoliaPatcher.fireAndForget()} method and pushes
 * {@code null} onto the stack. Otherwise, it executes the original blocking
 * {@code get()} call.
 * </p>
 */
public class FutureTransformer implements ClassTransformer {
    private final Logger logger;
    private final String relocatedPatcherPath;
    private static final String FUTURE_CLASS_NAME = "java/util/concurrent/CompletableFuture";

    public FutureTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor classVisitor) {
        return new FutureClassVisitor(classVisitor, relocatedPatcherPath, logger);
    }

    private static class FutureClassVisitor extends ClassVisitor {
        private final String relocatedPatcherPath;
        private final Logger logger;
        private String className;

        public FutureClassVisitor(ClassVisitor classVisitor, String relocatedPatcherPath, Logger logger) {
            super(Opcodes.ASM9, classVisitor);
            this.relocatedPatcherPath = relocatedPatcherPath;
            this.logger = logger;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name;
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            if (mv == null) {
                return null;
            }
            return new FutureMethodVisitor(mv, relocatedPatcherPath, className, name, logger);
        }
    }

    private static class FutureMethodVisitor extends MethodVisitor {
        private final String relocatedPatcherPath;
        private final String className;
        private final String methodName;
        private final Logger logger;

        public FutureMethodVisitor(MethodVisitor mv, String relocatedPatcherPath, String className, String methodName, Logger logger) {
            super(Opcodes.ASM9, mv);
            this.relocatedPatcherPath = relocatedPatcherPath;
            this.className = className;
            this.methodName = methodName;
            this.logger = logger;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if (opcode == Opcodes.INVOKEVIRTUAL &&
                FUTURE_CLASS_NAME.equals(owner) &&
                "get".equals(name) &&
                "()Ljava/lang/Object;".equals(desc)) {

                logger.fine("[FoliaPhantom] Found CompletableFuture.get() call in " + className + "#" + methodName);

                Label elseLabel = new Label();
                Label endLabel = new Label();

                String patcherInternalName = relocatedPatcherPath + "/FoliaPatcher";
                super.visitFieldInsn(Opcodes.GETSTATIC, patcherInternalName, "FIRE_AND_FORGET", "Z");
                super.visitJumpInsn(Opcodes.IFEQ, elseLabel);

                // --- TRUE (if) branch: fireAndForget is true ---
                // The future instance is already on the stack.
                String fireAndForgetDesc = "(Ljava/util/concurrent/CompletableFuture;)V";
                super.visitMethodInsn(Opcodes.INVOKESTATIC, patcherInternalName, "fireAndForget", fireAndForgetDesc, false);
                super.visitInsn(Opcodes.ACONST_NULL); // Replace return value
                super.visitJumpInsn(Opcodes.GOTO, endLabel);

                // --- FALSE (else) branch: fireAndForget is false ---
                super.visitLabel(elseLabel);
                // The future instance is still on the stack.
                super.visitMethodInsn(opcode, owner, name, desc, isInterface);

                // --- END label ---
                super.visitLabel(endLabel);
                return;
            }

            // Pass through all other instructions
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }
    }
}
