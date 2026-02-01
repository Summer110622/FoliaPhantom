/*
 * Folia Phantom - Server#broadcastMessage() Transformer
 *
 * Copyright (c) 2026 Marv
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
 * Transforms {@code Server#broadcastMessage(String)} calls to a thread-safe equivalent
 * in {@code FoliaPatcher}. This ensures that plugins broadcasting messages
 * from asynchronous threads do not cause concurrency issues.
 */
public class ServerBroadcastMessageTransformer implements ClassTransformer {

    private final Logger logger;
    private final String relocatedPatcherPath;
    private boolean hasTransformed = false;

    public ServerBroadcastMessageTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor classVisitor) {
        return new ServerBroadcastMessageClassVisitor(classVisitor);
    }

    /**
     * Checks if the transformer has made any changes to the class.
     *
     * @return {@code true} if a transformation was applied, {@code false} otherwise.
     */
    public boolean hasTransformed() {
        return hasTransformed;
    }

    private class ServerBroadcastMessageClassVisitor extends ClassVisitor {
        private String className;
        private boolean isJavaPlugin;
        private String pluginField;
        private String pluginFieldType; // To store descriptor like "Lorg/bukkit/plugin/Plugin;"

        public ServerBroadcastMessageClassVisitor(ClassVisitor cv) {
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
            if (isJavaPlugin || pluginField != null) {
                return new BroadcastMessageMethodVisitor(mv, access, name, descriptor);
            }
            return mv;
        }

        private class BroadcastMessageMethodVisitor extends AdviceAdapter {

            BroadcastMessageMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
                super(Opcodes.ASM9, mv, access, name, desc);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                if (opcode == Opcodes.INVOKEINTERFACE &&
                    "org/bukkit/Server".equals(owner) &&
                    "broadcastMessage".equals(name) &&
                    "(Ljava/lang/String;)V".equals(desc)) {

                    logger.fine("[ServerBroadcastMessageTransformer] Transforming broadcastMessage call in " + className);

                    // Original stack: [..., server, message]
                    // We need to replace the 'server' instance with our 'plugin' instance.
                    // The stack needs to become [..., plugin, message] for the static call.

                    // Stack: [..., server, message] -> SWAP -> [..., message, server]
                    super.visitInsn(Opcodes.SWAP);
                    // Stack: [..., message, server] -> POP -> [..., message]
                    super.visitInsn(Opcodes.POP);
                    // Stack: [..., message] -> loadPluginInstance -> [..., message, plugin]
                    loadPluginInstance();
                    // Stack: [..., message, plugin] -> SWAP -> [..., plugin, message]
                    super.visitInsn(Opcodes.SWAP);

                    // Now the stack is ready for the static call.
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        relocatedPatcherPath + "/FoliaPatcher",
                        "_bm",
                        // The descriptor needs the plugin instance as the first argument.
                        "(Lorg/bukkit/plugin/Plugin;Ljava/lang/String;)V",
                        false
                    );
                    hasTransformed = true;

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
                    throw new IllegalStateException("Cannot transform broadcastMessage call in " + className + ": No plugin instance found.");
                }
            }
        }
    }
}
