/*
 * Folia Phantom - Server#getOnlinePlayers() Transformer
 *
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */

package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.logging.Logger;

/**
 * Optimizes {@code .getOnlinePlayers().size()} calls to {@code .getPlayerCount()}.
 * This is a performance optimization to avoid creating an unnecessary list copy.
 */
public class ServerGetOnlinePlayersTransformer implements ClassTransformer {

    private final Logger logger;
    private final String relocatedPatcherPath; // Not used but part of the interface

    public ServerGetOnlinePlayersTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor classVisitor) {
        return new ServerClassVisitor(classVisitor);
    }

    private class ServerClassVisitor extends ClassVisitor {
        public ServerClassVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM9, classVisitor);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new GetOnlinePlayersMethodVisitor(mv);
        }
    }

    private class GetOnlinePlayersMethodVisitor extends MethodVisitor {
        private boolean getOnlinePlayersCallPending = false;

        public GetOnlinePlayersMethodVisitor(MethodVisitor methodVisitor) {
            super(Opcodes.ASM9, methodVisitor);
        }

        private void flushPending() {
            if (getOnlinePlayersCallPending) {
                // Write the original getOnlinePlayers instruction that we skipped
                super.visitMethodInsn(Opcodes.INVOKEINTERFACE, "org/bukkit/Server", "getOnlinePlayers", "()Ljava/util/Collection;", true);
                getOnlinePlayersCallPending = false;
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (getOnlinePlayersCallPending &&
                    opcode == Opcodes.INVOKEINTERFACE &&
                    "java/util/Collection".equals(owner) &&
                    "size".equals(name) &&
                    "()I".equals(descriptor)) {

                // Pattern matched: getOnlinePlayers().size()
                // Replace with getPlayerCount()
                super.visitMethodInsn(Opcodes.INVOKEINTERFACE, "org/bukkit/Server", "getPlayerCount", "()I", true);
                getOnlinePlayersCallPending = false;
            } else {
                // Not a match or no pending call. Flush any pending call first.
                flushPending();

                // Then check if the current instruction is the start of our pattern.
                if (opcode == Opcodes.INVOKEINTERFACE &&
                        "org/bukkit/Server".equals(owner) &&
                        "getOnlinePlayers".equals(name) &&
                        "()Ljava/util/Collection;".equals(descriptor)) {

                    // This is the start of our pattern. Set the flag and DON'T visit yet.
                    getOnlinePlayersCallPending = true;
                } else {
                    // Not the start of the pattern, just a regular instruction.
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }
            }
        }

        // We must override all other instruction visiting methods to flush any pending call.
        // This ensures that if another instruction comes between getOnlinePlayers() and size(),
        // the original getOnlinePlayers() call is correctly written.

        @Override public void visitInsn(int opcode) { flushPending(); super.visitInsn(opcode); }
        @Override public void visitIntInsn(int opcode, int operand) { flushPending(); super.visitIntInsn(opcode, operand); }
        @Override public void visitVarInsn(int opcode, int var) { flushPending(); super.visitVarInsn(opcode, var); }
        @Override public void visitTypeInsn(int opcode, String type) { flushPending(); super.visitTypeInsn(opcode, type); }
        @Override public void visitFieldInsn(int opcode, String owner, String name, String descriptor) { flushPending(); super.visitFieldInsn(opcode, owner, name, descriptor); }
        @Override public void visitInvokeDynamicInsn(String name, String descriptor, org.objectweb.asm.Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) { flushPending(); super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments); }
        @Override public void visitJumpInsn(int opcode, org.objectweb.asm.Label label) { flushPending(); super.visitJumpInsn(opcode, label); }
        @Override public void visitLabel(org.objectweb.asm.Label label) { flushPending(); super.visitLabel(label); }
        @Override public void visitLdcInsn(Object value) { flushPending(); super.visitLdcInsn(value); }
        @Override public void visitIincInsn(int var, int increment) { flushPending(); super.visitIincInsn(var, increment); }
        @Override public void visitTableSwitchInsn(int min, int max, org.objectweb.asm.Label dflt, org.objectweb.asm.Label... labels) { flushPending(); super.visitTableSwitchInsn(min, max, dflt, labels); }
        @Override public void visitLookupSwitchInsn(org.objectweb.asm.Label dflt, int[] keys, org.objectweb.asm.Label[] labels) { flushPending(); super.visitLookupSwitchInsn(dflt, keys, labels); }
        @Override public void visitMultiANewArrayInsn(String descriptor, int numDimensions) { flushPending(); super.visitMultiANewArrayInsn(descriptor, numDimensions); }
        @Override public void visitEnd() { flushPending(); super.visitEnd(); }
        @Override public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) { flushPending(); super.visitFrame(type, numLocal, local, numStack, stack); }
        @Override public void visitLineNumber(int line, org.objectweb.asm.Label start) { flushPending(); super.visitLineNumber(line, start); }
        @Override public void visitMaxs(int maxStack, int maxLocals) { flushPending(); super.visitMaxs(maxStack, maxLocals); }
    }
}
