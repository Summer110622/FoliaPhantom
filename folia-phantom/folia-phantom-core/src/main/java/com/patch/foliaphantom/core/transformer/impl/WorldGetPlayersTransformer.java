/*
 * Folia Phantom - World#getPlayers() Transformer
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
 * Transforms {@code World#getPlayers()} calls to a thread-safe equivalent
 * in {@code FoliaPatcher}. This ensures that plugins accessing the player list
 * of a world from asynchronous threads do not cause concurrency issues.
 */
public class WorldGetPlayersTransformer implements ClassTransformer {

    private final Logger logger;
    private final String relocatedPatcherPath;

    public WorldGetPlayersTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor classVisitor) {
        return new WorldGetPlayersClassVisitor(classVisitor);
    }

    private class WorldGetPlayersClassVisitor extends ClassVisitor {
        private String className;
        private boolean isJavaPlugin;
        private String pluginField;
        private String pluginFieldType;

        public WorldGetPlayersClassVisitor(ClassVisitor cv) {
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
                return new GetPlayersMethodVisitor(mv, access, name, descriptor);
            }
            return mv;
        }

        private class GetPlayersMethodVisitor extends AdviceAdapter {
            GetPlayersMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
                super(Opcodes.ASM9, mv, access, name, desc);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                if (opcode == Opcodes.INVOKEINTERFACE &&
                    "org/bukkit/World".equals(owner) &&
                    "getPlayers".equals(name) &&
                    "()Ljava/util/List;".equals(desc)) {

                    logger.fine("[WorldGetPlayersTransformer] Transforming " + owner + "#" + name + " in " + className);

                    // The original call leaves a World instance on the stack.
                    // The safeGetPlayers method needs this, but it also needs a plugin instance.
                    // Stack before: [..., world]
                    // We need to transform it to: [..., plugin, world]

                    // Load the plugin instance onto the stack.
                    loadPluginInstance();
                    // Now stack is: [..., world, plugin]

                    // Swap the top two elements to get the correct order for the static call
                    swap();
                    // Now stack is: [..., plugin, world]

                    // Call our static helper method.
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        relocatedPatcherPath + "/FoliaPatcher",
                        "_gp",
                        "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;)Ljava/util/List;",
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
                    throw new IllegalStateException("Cannot transform getPlayers call in " + className + ": No plugin instance found.");
                }
            }
        }
    }
}
