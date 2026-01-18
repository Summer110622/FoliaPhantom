/*
 * Folia Phantom - Player#getTargetBlock() Transformer
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
 * Transforms {@code Player#getTargetBlock()} calls to a thread-safe equivalent
 * in {@code FoliaPatcher}. This is critical for Folia compatibility as ray-tracing
 * from an async thread can lead to server crashes or data corruption.
 */
public class PlayerGetTargetBlockTransformer implements ClassTransformer {

    private final Logger logger;
    private final String relocatedPatcherPath;
    private boolean hasTransformed = false;

    public PlayerGetTargetBlockTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor classVisitor) {
        return new PlayerGetTargetBlockClassVisitor(classVisitor);
    }

    /**
     * Checks if the transformer has made any changes to the class.
     *
     * @return {@code true} if a transformation was applied, {@code false} otherwise.
     */
    public boolean hasTransformed() {
        return hasTransformed;
    }

    private class PlayerGetTargetBlockClassVisitor extends ClassVisitor {
        private String className;
        private boolean isJavaPlugin;
        private String pluginField;
        private String pluginFieldType;

        public PlayerGetTargetBlockClassVisitor(ClassVisitor cv) {
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
                return new GetTargetBlockMethodVisitor(mv, access, name, descriptor);
            }
            return mv;
        }

        private class GetTargetBlockMethodVisitor extends AdviceAdapter {
            GetTargetBlockMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
                super(Opcodes.ASM9, mv, access, name, desc);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                if (opcode == Opcodes.INVOKEINTERFACE &&
                    "org/bukkit/entity/Player".equals(owner) &&
                    "getTargetBlock".equals(name) &&
                    "(Ljava/util/Set;I)Lorg/bukkit/block/Block;".equals(desc)) {

                    logger.fine("[PlayerGetTargetBlockTransformer] Transforming " + name + " in " + className);

                    // Stack before: [..., Player, Set<Material>, int]
                    // We need to add the plugin instance to the stack for the new method call.
                    loadPluginInstance();
                    // Stack after: [..., Player, Set<Material>, int, Plugin]

                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        relocatedPatcherPath + "/FoliaPatcher",
                        "safeGetTargetBlock",
                        "(Lorg/bukkit/entity/Player;Ljava/util/Set;I;Lorg/bukkit/plugin/Plugin;)Lorg/bukkit/block/Block;",
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
                    // This should not happen due to the check in visitMethod, but as a safeguard:
                    throw new IllegalStateException("Cannot transform getTargetBlock in " + className + ": No plugin instance found.");
                }
            }
        }
    }
}
