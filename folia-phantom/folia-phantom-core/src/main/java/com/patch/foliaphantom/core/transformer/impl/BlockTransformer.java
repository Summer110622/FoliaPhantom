/*
 * Folia Phantom - Block Transformer
 *
 * Copyright (c) 2024 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import java.util.logging.Logger;

/**
 * Transforms Block.setType() and Block.setBlockData() calls to use the asynchronous, thread-safe
 * FoliaPatcher implementation.
 */
public class BlockTransformer implements ClassTransformer {
    private final Logger logger;
    private final String relocatedPatcherPath;
    private static final String PATCHER_CLASS = "FoliaPatcher";
    private static final String PLUGIN_DESC = "Lorg/bukkit/plugin/Plugin;";
    private static final String JAVA_PLUGIN_DESC = "Lorg/bukkit/plugin/java/JavaPlugin;";

    public BlockTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new BlockClassVisitor(next, relocatedPatcherPath, logger);
    }

    private static class BlockClassVisitor extends ClassVisitor {
        private String className;
        private String pluginFieldName;
        private String pluginFieldDesc;
        private final String patcherPath;
        private final Logger logger;

        public BlockClassVisitor(ClassVisitor cv, String patcherPath, Logger logger) {
            super(Opcodes.ASM9, cv);
            this.patcherPath = patcherPath;
            this.logger = logger;
        }

        @Override
        public void visit(int version, int access, String name, String sig, String superName, String[] interfaces) {
            this.className = name;
            super.visit(version, access, name, sig, superName, interfaces);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String sig, Object val) {
            if (pluginFieldName == null && (desc.equals(PLUGIN_DESC) || desc.equals(JAVA_PLUGIN_DESC))) {
                this.pluginFieldName = name;
                this.pluginFieldDesc = desc;
            }
            return super.visitField(access, name, desc, sig, val);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            if (pluginFieldName == null) {
                return mv;
            }
            return new BlockMethodVisitor(mv, access, name, desc, patcherPath, className, pluginFieldName, pluginFieldDesc);
        }
    }

    private static class BlockMethodVisitor extends AdviceAdapter {
        private final String patcherPath;
        private final String pluginFieldOwner;
        private final String pluginFieldName;
        private final String pluginFieldDesc;

        protected BlockMethodVisitor(MethodVisitor mv, int access, String name, String desc,
                String patcherPath, String owner, String pfn, String pfd) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherPath = patcherPath;
            this.pluginFieldOwner = owner;
            this.pluginFieldName = pfn;
            this.pluginFieldDesc = pfd;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if ("org/bukkit/block/Block".equals(owner) && "setType".equals(name) && "(Lorg/bukkit/Material;)V".equals(desc)) {
                // Method: void setType(Material type)
                // Stack before: [block, material]
                String patcherOwner = patcherPath + "/" + PATCHER_CLASS;

                // Store arguments in local variables
                int materialLocal = newLocal(Type.getType("Lorg/bukkit/Material;"));
                storeLocal(materialLocal);
                int blockLocal = newLocal(Type.getType("Lorg/bukkit/block/Block;"));
                storeLocal(blockLocal);

                // Load plugin instance, then the stored arguments
                loadThis();
                getField(Type.getObjectType(pluginFieldOwner), pluginFieldName, Type.getType(pluginFieldDesc));
                loadLocal(blockLocal);
                loadLocal(materialLocal);

                // Stack after: [plugin, block, material]
                String newDesc = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/block/Block;Lorg/bukkit/Material;)V";
                super.visitMethodInsn(INVOKESTATIC, patcherOwner, "safeSetBlockType", newDesc, false);
                return;
            } else if ("org/bukkit/block/Block".equals(owner) && "setBlockData".equals(name) && "(Lorg/bukkit/block/data/BlockData;)V".equals(desc)) {
                // Method: void setBlockData(BlockData data)
                // Stack before: [block, blockData]
                String patcherOwner = patcherPath + "/" + PATCHER_CLASS;

                // Store arguments in local variables
                int blockDataLocal = newLocal(Type.getType("Lorg/bukkit/block/data/BlockData;"));
                storeLocal(blockDataLocal);
                int blockLocal = newLocal(Type.getType("Lorg/bukkit/block/Block;"));
                storeLocal(blockLocal);

                // Load plugin instance, then the stored arguments
                loadThis();
                getField(Type.getObjectType(pluginFieldOwner), pluginFieldName, Type.getType(pluginFieldDesc));
                loadLocal(blockLocal);
                loadLocal(blockDataLocal);

                // Stack after: [plugin, block, blockData]
                String newDesc = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/block/Block;Lorg/bukkit/block/data/BlockData;)V";
                super.visitMethodInsn(INVOKESTATIC, patcherOwner, "safeSetBlockData", newDesc, false);
                return;
            }
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }
    }
}
