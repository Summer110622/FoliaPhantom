/*
 * Folia Phantom - Event Transformer
 *
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.*;
import java.util.logging.Logger;

/**
 * Transforms PluginManager.callEvent() calls to be thread-safe.
 *
 * <p>Redirects event calls to FoliaPatcher utility methods that ensure
 * events are called on the main server thread.</p>
 */
public class EventTransformer implements ClassTransformer {
    private final Logger logger;
    private final String relocatedPatcherPath;
    private String ownerClass;

    public EventTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new EventClassVisitor(next, relocatedPatcherPath);
    }

    private class EventClassVisitor extends ClassVisitor {
        private final String patcherPath;
        private String pluginField = null;

        public EventClassVisitor(ClassVisitor cv, String patcherPath) {
            super(Opcodes.ASM9, cv);
            this.patcherPath = patcherPath;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            ownerClass = name;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
            // Heuristically find a field of type Plugin.
            if ("Lorg/bukkit/plugin/Plugin;".equals(desc)) {
                pluginField = name;
            }
            return super.visitField(access, name, desc, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            return new EventMethodVisitor(super.visitMethod(access, name, desc, signature, exceptions), patcherPath, pluginField);
        }
    }

    private class EventMethodVisitor extends MethodVisitor {
        private final String patcherPath;
        private final String pluginField;

        public EventMethodVisitor(MethodVisitor mv, String patcherPath, String pluginField) {
            super(Opcodes.ASM9, mv);
            this.patcherPath = patcherPath;
            this.pluginField = pluginField;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            // Intercept Bukkit.getPluginManager().callEvent(event)
            if (opcode == Opcodes.INVOKEINTERFACE &&
                "org/bukkit/plugin/PluginManager".equals(owner) &&
                "callEvent".equals(name) &&
                "(Lorg/bukkit/event/Event;)V".equals(desc)) {

                // The stack before this instruction is: [pluginManager, event]
                // We need to transform it to: [plugin, event] for our static method.

                // Pop pluginManager, we don't need it.
                super.visitInsn(Opcodes.POP);

                // Load the plugin instance.
                if (pluginField != null) {
                    // Found a plugin field, use it.
                    super.visitVarInsn(Opcodes.ALOAD, 0); // this
                    super.visitFieldInsn(Opcodes.GETFIELD, ownerClass, pluginField, "Lorg/bukkit/plugin/Plugin;");
                } else {
                    // Fallback to assuming 'this' is the plugin.
                    super.visitVarInsn(Opcodes.ALOAD, 0);
                }

                // The stack is now [event, plugin]. Swap them to [plugin, event].
                super.visitInsn(Opcodes.SWAP);

                // Call the static patcher method.
                String newDesc = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/event/Event;)V";
                super.visitMethodInsn(Opcodes.INVOKESTATIC, patcherPath, "safeCallEvent", newDesc, false);

                logger.fine("Redirected callEvent in " + ownerClass + " using " + (pluginField != null ? "field " + pluginField : "this"));
                return;
            }

            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }
    }
}
