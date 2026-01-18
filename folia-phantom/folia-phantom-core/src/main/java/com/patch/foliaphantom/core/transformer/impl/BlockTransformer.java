/*
 * Folia Phantom - Block Transformer
 *
 * Copyright (c) 2026 Marv
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
 * Transforms Block.setType() and Block.setBlockData() calls to use the
 * thread-safe FoliaPatcher implementation.
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
        private String classSuperName;
        private String pluginFieldName;
        private String pluginFieldDesc;
        private boolean isPluginClass;
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
            this.classSuperName = superName;
            this.isPluginClass = "org/bukkit/plugin/java/JavaPlugin".equals(superName);
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
            if (pluginFieldName != null || isPluginClass) {
                 return new BlockMethodVisitor(mv, access, name, desc, patcherPath, className, isPluginClass, pluginFieldName, pluginFieldDesc);
            }
            return mv;
        }
    }

    private static class BlockMethodVisitor extends AdviceAdapter {
        private final String patcherPath;
        private final String pluginFieldOwner;
        private final String pluginFieldName;
        private final String pluginFieldDesc;
        private final boolean isPluginClass;


        protected BlockMethodVisitor(MethodVisitor mv, int access, String name, String desc,
                String patcherPath, String owner, boolean isPlugin, String pfn, String pfd) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherPath = patcherPath;
            this.pluginFieldOwner = owner;
            this.isPluginClass = isPlugin;
            this.pluginFieldName = pfn;
            this.pluginFieldDesc = pfd;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if ("org/bukkit/block/Block".equals(owner) && isInterface) {
                 if ("setType".equals(name) && "(Lorg/bukkit/Material;)V".equals(desc)) {
                    transformSetType();
                    return;
                }
                if ("setBlockData".equals(name) && "(Lorg/bukkit/block/data/BlockData;)V".equals(desc)) {
                    transformSetBlockData();
                    return;
                }
            }
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }

        private void transformSetType() {
            String patcherOwner = patcherPath + "/" + PATCHER_CLASS;
            // Stack: [block, material]
            int materialLocal = newLocal(Type.getType("Lorg/bukkit/Material;"));
            storeLocal(materialLocal);
            int blockLocal = newLocal(Type.getType("Lorg/bukkit/block/Block;"));
            storeLocal(blockLocal);

            // Stack: []
            loadPlugin();
            loadLocal(blockLocal);
            loadLocal(materialLocal);

            // Stack: [plugin, block, material]
            String newDesc = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/block/Block;Lorg/bukkit/Material;)V";
            super.visitMethodInsn(INVOKESTATIC, patcherOwner, "safeSetBlockType", newDesc, false);
        }

        private void transformSetBlockData() {
            String patcherOwner = patcherPath + "/" + PATCHER_CLASS;
            // Stack: [block, blockData]
            int dataLocal = newLocal(Type.getType("Lorg/bukkit/block/data/BlockData;"));
            storeLocal(dataLocal);
            int blockLocal = newLocal(Type.getType("Lorg/bukkit/block/Block;"));
            storeLocal(blockLocal);

            // Stack: []
            loadPlugin();
            loadLocal(blockLocal);
            loadLocal(dataLocal);

            // Stack: [plugin, block, blockData]
            String newDesc = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/block/Block;Lorg/bukkit/block/data/BlockData;)V";
            super.visitMethodInsn(INVOKESTATIC, patcherOwner, "safeSetBlockData", newDesc, false);
        }

        private void loadPlugin() {
            if (isPluginClass) {
                loadThis();
            } else {
                loadThis();
                getField(Type.getObjectType(pluginFieldOwner), pluginFieldName, Type.getType(pluginFieldDesc));
            }
        }
    }
}
