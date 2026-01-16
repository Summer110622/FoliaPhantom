/*
 * Folia Phantom - World Access Transformer
 *
 * Copyright (c) 2026 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;

import java.util.logging.Logger;

public class WorldGetHighestBlockAtTransformer implements ClassTransformer {

    private final Logger logger;
    private final String relocatedPatcherPath;
    private boolean transformed = false;

    public WorldGetHighestBlockAtTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new WorldMethodVisitor(Opcodes.ASM9, next);
    }

    public boolean hasTransformed() {
        return transformed;
    }

    private class WorldMethodVisitor extends ClassVisitor {
        private String className;
        private String pluginFieldOwner;
        private boolean isPluginClass;

        public WorldMethodVisitor(int api, ClassVisitor classVisitor) {
            super(api, classVisitor);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            this.className = name;
            this.isPluginClass = "org/bukkit/plugin/java/JavaPlugin".equals(superName) || "org/bukkit/plugin/Plugin".equals(superName);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (descriptor.equals("Lorg/bukkit/plugin/Plugin;") || descriptor.equals("Lorg/bukkit/plugin/java/JavaPlugin;")) {
                this.pluginFieldOwner = name;
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new MethodAdapter(api, mv, access, name, descriptor);
        }

        private class MethodAdapter extends AdviceAdapter {
            protected MethodAdapter(int api, MethodVisitor methodVisitor, int access, String name, String descriptor) {
                super(api, methodVisitor, access, name, descriptor);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                if (isInterface && owner.equals("org/bukkit/World") && name.equals("getHighestBlockAt") && descriptor.equals("(II)Lorg/bukkit/block/Block;")) {
                    logger.fine("[FoliaPhantom] Found call to World.getHighestBlockAt in " + className);

                    // The call to `World.getHighestBlockAt` has the stack: [..., world, x, z]
                    // We need to transform this to call our static helper, which requires the stack:
                    // [..., plugin, world, x, z]

                    // 1. Store x and z into local variables. The stack becomes [..., world].
                    int zVar = newLocal(Type.INT_TYPE);
                    mv.visitVarInsn(ISTORE, zVar);
                    int xVar = newLocal(Type.INT_TYPE);
                    mv.visitVarInsn(ISTORE, xVar);

                    // 2. Load the plugin instance. The transformer locates 'this' or a field of type Plugin.
                    // The stack is now [..., world, plugin].
                    if (isPluginClass) {
                        mv.visitVarInsn(ALOAD, 0); // this
                    } else if (pluginFieldOwner != null) {
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitFieldInsn(GETFIELD, className, pluginFieldOwner, "Lorg/bukkit/plugin/Plugin;");
                    } else {
                        // If we can't find a plugin instance, we can't patch this call.
                        logger.warning("[FoliaPhantom] Could not find plugin instance for World.getHighestBlockAt call in " + className + ". Skipping transformation.");
                        // We must restore the stack to its original state before calling the original method.
                        mv.visitVarInsn(ILOAD, xVar);
                        mv.visitVarInsn(ILOAD, zVar);
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                        return;
                    }

                    // 3. Swap world and plugin references. The stack is now [..., plugin, world].
                    mv.visitInsn(SWAP);

                    // 4. Load x and z back from local variables.
                    // The stack is now [..., plugin, world, x, z], matching our helper method's signature.
                    mv.visitVarInsn(ILOAD, xVar);
                    mv.visitVarInsn(ILOAD, zVar);

                    // The stack is now [plugin, world, x, z] which is what our static helper needs.

                    String patcherOwner = relocatedPatcherPath + "/FoliaPatcher";
                    String patcherDescriptor = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;II)Lorg/bukkit/block/Block;";
                    mv.visitMethodInsn(INVOKESTATIC, patcherOwner, "safeGetHighestBlockAt", patcherDescriptor, false);
                    transformed = true;
                    logger.info("[FoliaPhantom] Transformed World.getHighestBlockAt call in " + className);
                } else {
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }
            }
        }
    }
}
