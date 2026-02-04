/*
 * Folia Phantom - Player#getHealth() Transformer
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

/**
 * Transforms {@code Player#getHealth()} calls to a thread-safe equivalent
 * in {@code FoliaPatcher}. This ensures that plugins accessing player health
 * from asynchronous threads do not cause concurrency issues.
 */
public class PlayerHealthTransformer implements ClassTransformer {

    private final Logger logger;
    private final String relocatedPatcherPath;

    public PlayerHealthTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor classVisitor) {
        return new PlayerHealthClassVisitor(classVisitor);
    }

    private class PlayerHealthClassVisitor extends ClassVisitor {
        private String className;
        private boolean isJavaPlugin;
        private String pluginField;
        private String pluginFieldType;

        public PlayerHealthClassVisitor(ClassVisitor cv) {
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
                return new GetHealthMethodVisitor(mv, access, name, descriptor);
            }
            return mv;
        }

        private class GetHealthMethodVisitor extends AdviceAdapter {
            GetHealthMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
                super(Opcodes.ASM9, mv, access, name, desc);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                if (opcode == Opcodes.INVOKEINTERFACE &&
                    "org/bukkit/entity/Player".equals(owner) &&
                    "getHealth".equals(name) &&
                    "()D".equals(desc)) {

                    logger.fine("[PlayerHealthTransformer] Transforming getHealth() call in " + className);

                    // The stack before is: [..., playerInstance]
                    // We need to arrange it to be [..., pluginInstance, playerInstance]

                    // Load the plugin instance. Stack is now [..., playerInstance, pluginInstance]
                    loadPluginInstance();

                    // Swap the top two elements. Stack is now [..., pluginInstance, playerInstance]
                    super.visitInsn(Opcodes.SWAP);

                    // Call the static helper method
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        relocatedPatcherPath + "/FoliaPatcher",
                        "_gh",
                        "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Player;)D",
                        false
                    );
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
                    // This should not be reached due to the check in visitMethod, but as a safeguard:
                    throw new IllegalStateException("Cannot transform getHealth call in " + className + ": No plugin instance found.");
                }
            }
        }
    }
}
