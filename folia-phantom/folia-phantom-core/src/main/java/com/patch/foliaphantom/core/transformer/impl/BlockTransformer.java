/*
 * Folia Phantom - Block Transformer
 *
 * Copyright (c) 2024 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

import java.util.logging.Logger;

/**
 * Transforms {@code Block#setType} and {@code Block#setBlockData} calls to
 * thread-safe equivalents
 * in {@code FoliaPatcher}. This prevents unsafe block modifications from
 * asynchronous threads.
 */
public class BlockTransformer implements ClassTransformer {

    private final Logger logger;
    private final String relocatedPatcherPath;
    private boolean hasTransformed = false;

    public BlockTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor classVisitor) {
        return new BlockClassVisitor(classVisitor);
    }

    public boolean hasTransformed() {
        return hasTransformed;
    }

    private class BlockClassVisitor extends ClassVisitor {
        private String className;
        private boolean isJavaPlugin;
        private String pluginField;
        private String pluginFieldType;

        public BlockClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            this.className = name;
            this.isJavaPlugin = "org/bukkit/plugin/java/JavaPlugin".equals(superName);
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public org.objectweb.asm.FieldVisitor visitField(int access, String name, String descriptor, String signature,
                Object value) {
            if (pluginField == null
                    && (descriptor.equals("Lorg/bukkit/plugin/Plugin;")
                            || descriptor.equals("Lorg/bukkit/plugin/java/JavaPlugin;"))) {
                this.pluginField = name;
                this.pluginFieldType = descriptor;
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (isJavaPlugin || pluginField != null) {
                return new BlockMethodVisitor(mv, access, name, descriptor);
            }
            return mv;
        }

        private class BlockMethodVisitor extends AdviceAdapter {

            BlockMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
                super(Opcodes.ASM9, mv, access, name, desc);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                if (opcode == Opcodes.INVOKEINTERFACE && "org/bukkit/block/Block".equals(owner)) {
                    String newName = null;
                    String newDesc = null;

                    if ("setType".equals(name) && "(Lorg/bukkit/Material;)V".equals(desc)) {
                        newName = "safeSetBlockType";
                        newDesc = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/block/Block;Lorg/bukkit/Material;)V";
                    } else if ("setType".equals(name) && "(Lorg/bukkit/Material;Z)V".equals(desc)) {
                        newName = "safeSetBlockTypeWithPhysics";
                        newDesc = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/block/Block;Lorg/bukkit/Material;Z)V";
                    } else if ("setBlockData".equals(name) && "(Lorg/bukkit/block/data/BlockData;)V".equals(desc)) {
                        newName = "safeSetBlockData";
                        newDesc = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/block/Block;Lorg/bukkit/block/data/BlockData;)V";
                    } else if ("setBlockData".equals(name) && "(Lorg/bukkit/block/data/BlockData;Z)V".equals(desc)) {
                        newName = "safeSetBlockDataWithPhysics";
                        newDesc = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/block/Block;Lorg/bukkit/block/data/BlockData;Z)V";
                    }

                    if (newName != null) {
                        logger.fine("[BlockTransformer] Transforming " + name + " call in " + className);

                        Type[] args = Type.getArgumentTypes(desc);
                        int[] locals = new int[args.length];
                        for (int i = args.length - 1; i >= 0; i--) {
                            locals[i] = newLocal(args[i]);
                            storeLocal(locals[i]);
                        }

                        int blockLocal = newLocal(Type.getObjectType("org/bukkit/block/Block"));
                        storeLocal(blockLocal);

                        loadPluginInstance();
                        loadLocal(blockLocal);
                        for (int i = 0; i < args.length; i++) {
                            loadLocal(locals[i]);
                        }

                        super.visitMethodInsn(
                                Opcodes.INVOKESTATIC,
                                relocatedPatcherPath + "/FoliaPatcher",
                                newName,
                                newDesc,
                                false);
                        hasTransformed = true;
                        return;
                    }
                }
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }

            private void loadPluginInstance() {
                if (isJavaPlugin) {
                    visitVarInsn(ALOAD, 0); // this
                } else if (pluginField != null) {
                    visitVarInsn(ALOAD, 0); // this
                    visitFieldInsn(GETFIELD, className, pluginField, pluginFieldType);
                } else {
                    throw new IllegalStateException(
                            "Cannot transform block modification call in " + className + ": No plugin instance found.");
                }
            }
        }
    }
}
