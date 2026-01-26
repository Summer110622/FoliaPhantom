/*
 * Folia Phantom - Server#getOnlinePlayers() Transformer
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
        private String pluginFieldType;

        public ServerGetOnlinePlayersClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name;
            // Simplified check for any class that extends JavaPlugin, directly or indirectly.
            // A more robust solution might involve parsing class hierarchy, but this is sufficient for most cases.
            if ("org/bukkit/plugin/java/JavaPlugin".equals(superName)) {
                this.isJavaPlugin = true;
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public org.objectweb.asm.FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            // Find a field of type Plugin or a subclass, to be used as a context for the static call.
            if (descriptor.equals("Lorg/bukkit/plugin/Plugin;") || descriptor.equals("Lorg/bukkit/plugin/java/JavaPlugin;")) {
                if (this.pluginField == null) { // Prefer the first declared plugin field
                    this.pluginField = name;
                    this.pluginFieldType = descriptor;
                }
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
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
                    logger.fine("[FoliaPhantom] Flushing pending getOnlinePlayers call in " + className);
                    seenGetOnlinePlayers = false; // Reset state
                    pop(); // Pop the player collection
                    loadPluginInstance(); // Push the Plugin instance
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
                if (seenGetOnlinePlayers) {
                    // Case 1: getOnlinePlayers().size()
                    if (opcode == Opcodes.INVOKEINTERFACE && "java/util/Collection".equals(owner) && "size".equals(name) && "()I".equals(desc)) {
                        logger.fine("[FoliaPhantom] Optimizing getOnlinePlayers().size() in " + className);
                        pop(); // Pop player collection
                        loadPluginInstance();
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, relocatedPatcherPath + "/FoliaPatcher", "safeGetOnlinePlayersSize", "(Lorg/bukkit/plugin/Plugin;)I", false);
                        seenGetOnlinePlayers = false;
                        hasTransformed = true;
                        return;
                    }
                    // Case 2: getOnlinePlayers().forEach(...)
                    else if (opcode == Opcodes.INVOKEINTERFACE && ("java/util/Collection".equals(owner) || "java/lang/Iterable".equals(owner)) && "forEach".equals(name) && "(Ljava/util/function/Consumer;)V".equals(desc)) {
                        logger.fine("[FoliaPhantom] Optimizing getOnlinePlayers().forEach() in " + className);
                        // Stack: [Server, Consumer]. We want to call a static method that takes [Plugin, Consumer].
                        pop(); // Pop the Server instance. Stack: [Consumer]
                        loadPluginInstance(); // Push the Plugin instance. Stack: [Consumer, Plugin]
                        swap(); // Swap them. Stack: [Plugin, Consumer]
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, relocatedPatcherPath + "/FoliaPatcher", "forEachPlayer", "(Lorg/bukkit/plugin/Plugin;Ljava/util/function/Consumer;)V", false);
                        seenGetOnlinePlayers = false;
                        hasTransformed = true;
                        return;
                    }
                    // Case 3: getOnlinePlayers().isEmpty() -> safeGetOnlinePlayersSize() == 0
                    else if (opcode == Opcodes.INVOKEINTERFACE && "java/util/Collection".equals(owner) && "isEmpty".equals(name) && "()Z".equals(desc)) {
                        logger.fine("[FoliaPhantom] Optimizing getOnlinePlayers().isEmpty() in " + className);
                        pop(); // Pop player collection
                        loadPluginInstance();
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, relocatedPatcherPath + "/FoliaPatcher", "safeGetOnlinePlayersSize", "(Lorg/bukkit/plugin/Plugin;)I", false);

                        // Compare the result with 0
                        Label isNotEmptyLabel = new Label();
                        super.visitJumpInsn(Opcodes.IFNE, isNotEmptyLabel);
                        super.visitInsn(Opcodes.ICONST_1); // a == 0, so it is empty, push true
                        Label endLabel = new Label();
                        super.visitJumpInsn(Opcodes.GOTO, endLabel);
                        super.visitLabel(isNotEmptyLabel);
                        super.visitInsn(Opcodes.ICONST_0); // a != 0, so it is not empty, push false
                        super.visitLabel(endLabel);

                        seenGetOnlinePlayers = false;
                        hasTransformed = true;
                        return;
                    }
                }

                // If we have a pending getOnlinePlayers, flush it before handling the current instruction.
                flushPendingGetOnlinePlayers();

                // Now, check if the current instruction is getOnlinePlayers to start the pattern matching.
                if (opcode == Opcodes.INVOKEINTERFACE && "org/bukkit/Server".equals(owner) && "getOnlinePlayers".equals(name) && "()Ljava/util/Collection;".equals(desc)) {
                    seenGetOnlinePlayers = true;
                    // Don't call super, we will transform this on the next instruction (or in flush).
                } else {
                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                }
            }

            private void loadPluginInstance() {
                if (isJavaPlugin) {
                    visitVarInsn(ALOAD, 0); // this
                } else if (pluginField != null) {
                    visitVarInsn(ALOAD, 0); // this
                    visitFieldInsn(GETFIELD, className, pluginField, pluginFieldType);
                } else {
                    // This should not be reached due to the check in visitMethod.
                    throw new IllegalStateException("Cannot transform getOnlinePlayers call in " + className + ": No plugin instance found.");
                }
            }

            // Flush any pending transformation before the method ends or other instructions are visited.
            @Override protected void onMethodExit(int opcode) { flushPendingGetOnlinePlayers(); }
            @Override public void visitLabel(org.objectweb.asm.Label label) { flushPendingGetOnlinePlayers(); super.visitLabel(label); }
            @Override public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) { flushPendingGetOnlinePlayers(); super.visitFrame(type, numLocal, local, numStack, stack); }
            @Override public void visitInsn(int opcode) { flushPendingGetOnlinePlayers(); super.visitInsn(opcode); }
            @Override public void visitIntInsn(int opcode, int operand) { flushPendingGetOnlinePlayers(); super.visitIntInsn(opcode, operand); }
            @Override public void visitVarInsn(int opcode, int var) { flushPendingGetOnlinePlayers(); super.visitVarInsn(opcode, var); }
            @Override public void visitTypeInsn(int opcode, String type) { flushPendingGetOnlinePlayers(); super.visitTypeInsn(opcode, type); }
            @Override public void visitFieldInsn(int opcode, String owner, String name, String desc) { flushPendingGetOnlinePlayers(); super.visitFieldInsn(opcode, owner, name, desc); }
            @Override public void visitInvokeDynamicInsn(String name, String desc, org.objectweb.asm.Handle bsm, Object... bsmArgs) { flushPendingGetOnlinePlayers(); super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs); }
            @Override public void visitJumpInsn(int opcode, org.objectweb.asm.Label label) { flushPendingGetOnlinePlayers(); super.visitJumpInsn(opcode, label); }
            @Override public void visitLdcInsn(Object cst) { flushPendingGetOnlinePlayers(); super.visitLdcInsn(cst); }
            @Override public void visitIincInsn(int var, int increment) { flushPendingGetOnlinePlayers(); super.visitIincInsn(var, increment); }
            @Override public void visitTableSwitchInsn(int min, int max, org.objectweb.asm.Label dflt, org.objectweb.asm.Label... labels) { flushPendingGetOnlinePlayers(); super.visitTableSwitchInsn(min, max, dflt, labels); }
            @Override public void visitLookupSwitchInsn(org.objectweb.asm.Label dflt, int[] keys, org.objectweb.asm.Label[] labels) { flushPendingGetOnlinePlayers(); super.visitLookupSwitchInsn(dflt, keys, labels); }
            @Override public void visitMultiANewArrayInsn(String desc, int dims) { flushPendingGetOnlinePlayers(); super.visitMultiANewArrayInsn(desc, dims); }
            @Override public void visitMaxs(int maxStack, int maxLocals) { flushPendingGetOnlinePlayers(); super.visitMaxs(maxStack, maxLocals); }
        }
    }
}
