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
import org.objectweb.asm.commons.AdviceAdapter;

import java.util.logging.Logger;

/**
 * Transforms {@code Server#getOnlinePlayers()} calls to a thread-safe equivalent
 * in {@code FoliaPatcher}. This ensures that plugins accessing the player list
 * from asynchronous threads do not cause concurrency issues.
 */
public class ServerGetOnlinePlayersTransformer implements ClassTransformer {

    private final Logger logger;
    private final String relocatedPatcherPath;
    private boolean hasTransformed = false;

    public ServerGetOnlinePlayersTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor classVisitor) {
        return new ServerGetOnlinePlayersClassVisitor(classVisitor);
    }

    /**
     * Checks if the transformer has made any changes to the class.
     *
     * @return {@code true} if a transformation was applied, {@code false} otherwise.
     */
    public boolean hasTransformed() {
        return hasTransformed;
    }

    private class ServerGetOnlinePlayersClassVisitor extends ClassVisitor {
        private String className;
        private boolean isJavaPlugin;
        private String pluginField;
        private String pluginFieldType; // To store descriptor like "Lorg/bukkit/plugin/Plugin;"

        public ServerGetOnlinePlayersClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name;
            this.isJavaPlugin = "org/bukkit/plugin/java/JavaPlugin".equals(superName);
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public org.objectweb.asm.FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (descriptor.equals("Lorg/bukkit/plugin/Plugin;") || descriptor.equals("Lorg/bukkit/plugin/java/JavaPlugin;")) {
                this.pluginField = name;
                this.pluginFieldType = descriptor;
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            // We need a plugin instance to make the safe call. If this class is not a plugin
            // and does not contain a plugin field, we cannot transform it.
            if (isJavaPlugin || pluginField != null) {
                return new GetOnlinePlayersMethodVisitor(mv, access, name, descriptor);
            }
            return mv;
        }

        private class GetOnlinePlayersMethodVisitor extends AdviceAdapter {
            private boolean seenGetOnlinePlayers = false;

            GetOnlinePlayersMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
                super(Opcodes.ASM9, mv, access, name, desc);
            }

            private void flushPendingGetOnlinePlayers() {
                if (seenGetOnlinePlayers) {
                    logger.fine("[ServerGetOnlinePlayersTransformer] Flushing pending getOnlinePlayers call in " + className);
                    seenGetOnlinePlayers = false; // Prevent recursion
                    pop(); // Pop the Server instance that was the target for the call
                    loadPluginInstance();
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        relocatedPatcherPath + "/FoliaPatcher",
                        "safeGetOnlinePlayers",
                        "(Lorg/bukkit/plugin/Plugin;)Ljava/util/Collection;",
                        false
                    );
                    hasTransformed = true;
                }
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                if (seenGetOnlinePlayers &&
                    opcode == Opcodes.INVOKEINTERFACE &&
                    "java/util/Collection".equals(owner) &&
                    "size".equals(name) &&
                    "()I".equals(desc)) {

                    logger.fine("[ServerGetOnlinePlayersTransformer] Optimizing getOnlinePlayers().size() in " + className);

                    pop(); // Pop the server instance from the original call
                    loadPluginInstance();
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        relocatedPatcherPath + "/FoliaPatcher",
                        "safeGetOnlinePlayersSize",
                        "(Lorg/bukkit/plugin/Plugin;)I",
                        false
                    );
                    seenGetOnlinePlayers = false;
                    hasTransformed = true;

                } else {
                    flushPendingGetOnlinePlayers();

                    if (opcode == Opcodes.INVOKEINTERFACE &&
                        "org/bukkit/Server".equals(owner) &&
                        "getOnlinePlayers".equals(name) &&
                        "()Ljava/util/Collection;".equals(desc)) {

                        seenGetOnlinePlayers = true;
                    } else {
                        super.visitMethodInsn(opcode, owner, name, desc, itf);
                    }
                }
            }

            private void loadPluginInstance() {
                if (isJavaPlugin) {
                    visitVarInsn(ALOAD, 0); // this
                } else if (pluginField != null) {
                    visitVarInsn(ALOAD, 0); // this
                    visitFieldInsn(GETFIELD, className, pluginField, pluginFieldType);
                } else {
                    throw new IllegalStateException("Cannot transform getOnlinePlayers call in " + className + ": No plugin instance found.");
                }
            }

            // The following overrides ensure that if any other instruction occurs after
            // getOnlinePlayers(), the pending transformation is flushed correctly.

            @Override public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) { flushPendingGetOnlinePlayers(); super.visitFrame(type, numLocal, local, numStack, stack); }
            @Override public void visitInsn(int opcode) { flushPendingGetOnlinePlayers(); super.visitInsn(opcode); }
            @Override public void visitIntInsn(int opcode, int operand) { flushPendingGetOnlinePlayers(); super.visitIntInsn(opcode, operand); }
            @Override public void visitVarInsn(int opcode, int var) { flushPendingGetOnlinePlayers(); super.visitVarInsn(opcode, var); }
            @Override public void visitTypeInsn(int opcode, String type) { flushPendingGetOnlinePlayers(); super.visitTypeInsn(opcode, type); }
            @Override public void visitFieldInsn(int opcode, String owner, String name, String desc) { flushPendingGetOnlinePlayers(); super.visitFieldInsn(opcode, owner, name, desc); }
            @Override public void visitInvokeDynamicInsn(String name, String desc, org.objectweb.asm.Handle bsm, Object... bsmArgs) { flushPendingGetOnlinePlayers(); super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs); }
            @Override public void visitJumpInsn(int opcode, org.objectweb.asm.Label label) { flushPendingGetOnlinePlayers(); super.visitJumpInsn(opcode, label); }
            @Override public void visitLabel(org.objectweb.asm.Label label) { flushPendingGetOnlinePlayers(); super.visitLabel(label); }
            @Override public void visitLdcInsn(Object cst) { flushPendingGetOnlinePlayers(); super.visitLdcInsn(cst); }
            @Override public void visitIincInsn(int var, int increment) { flushPendingGetOnlinePlayers(); super.visitIincInsn(var, increment); }
            @Override public void visitTableSwitchInsn(int min, int max, org.objectweb.asm.Label dflt, org.objectweb.asm.Label... labels) { flushPendingGetOnlinePlayers(); super.visitTableSwitchInsn(min, max, dflt, labels); }
            @Override public void visitLookupSwitchInsn(org.objectweb.asm.Label dflt, int[] keys, org.objectweb.asm.Label[] labels) { flushPendingGetOnlinePlayers(); super.visitLookupSwitchInsn(dflt, keys, labels); }
            @Override public void visitMultiANewArrayInsn(String desc, int dims) { flushPendingGetOnlinePlayers(); super.visitMultiANewArrayInsn(desc, dims); }
            @Override public void visitMaxs(int maxStack, int maxLocals) { flushPendingGetOnlinePlayers(); super.visitMaxs(maxStack, maxLocals); }
        }
    }
}
