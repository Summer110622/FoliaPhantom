/*
 * Folia Phantom - Event Fire-and-Forget Transformer
 *
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.logging.Logger;

public class EventFireAndForgetTransformer implements ClassTransformer {

    private final Logger logger;
    private final String relocatedPatcherPath;

    public EventFireAndForgetTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new EventFireAndForgetVisitor(next);
    }

    private class EventFireAndForgetVisitor extends ClassVisitor {
        private String className;
        private String superName;
        private String pluginFieldName = null;
        private String pluginFieldDesc = null;

        public EventFireAndForgetVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM9, classVisitor);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name;
            this.superName = superName;
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (descriptor.equals("Lorg/bukkit/plugin/Plugin;") || descriptor.equals("Lorg/bukkit/plugin/java/JavaPlugin;")) {
                pluginFieldName = name;
                pluginFieldDesc = descriptor;
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            boolean isPlugin = "org/bukkit/plugin/java/JavaPlugin".equals(superName) || "org/bukkit/plugin/Plugin".equals(superName);
            return new EventFireAndForgetAdapter(mv, isPlugin);
        }

        private class EventFireAndForgetAdapter extends MethodVisitor {
            private final boolean isPlugin;

            public EventFireAndForgetAdapter(MethodVisitor methodVisitor, boolean isPlugin) {
                super(Opcodes.ASM9, methodVisitor);
                this.isPlugin = isPlugin;
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                if (opcode == Opcodes.INVOKEINTERFACE &&
                    owner.equals("org/bukkit/plugin/PluginManager") &&
                    name.equals("callEvent") &&
                    descriptor.equals("(Lorg/bukkit/event/Event;)V")) {

                    logger.fine("[FoliaPhantom] Rewriting PluginManager.callEvent call in " + className);

                    // The stack is [pluginManager, event]
                    // We need to add the plugin instance to call our helper.
                    // The final stack should be [plugin, pluginManager, event]
                    boolean pluginFound = false;
                    if (isPlugin) {
                        super.visitVarInsn(Opcodes.ALOAD, 0); // this
                        pluginFound = true;
                    } else if (pluginFieldName != null) {
                        super.visitVarInsn(Opcodes.ALOAD, 0); // this
                        super.visitFieldInsn(Opcodes.GETFIELD, className, pluginFieldName, pluginFieldDesc);
                        pluginFound = true;
                    }

                    if (pluginFound) {
                        // Stack is now [pluginManager, event, plugin]
                        // We want [plugin, pluginManager, event] for the static call.
                        // DUP_X2 duplicates the top value and inserts it two values down.
                        // Stack: [plugin, pluginManager, event, plugin]
                        super.visitInsn(Opcodes.DUP_X2);
                        // POP the duplicated value from the top.
                        // Stack: [plugin, pluginManager, event]
                        super.visitInsn(Opcodes.POP);

                        super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                              relocatedPatcherPath + "/FoliaPatcher",
                                              "safeCallEvent",
                                              "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/plugin/PluginManager;Lorg/bukkit/event/Event;)V",
                                              false);
                    } else {
                        // If we can't find the plugin instance, we can't call our safe method.
                        // Log a warning and leave the original instruction unmodified.
                        logger.warning("[FoliaPhantom] Could not find plugin instance in " + className + ". Skipping transformation for this call.");
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }
                } else {
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }
            }
        }
    }
}
