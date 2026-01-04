/*
 * Folia Phantom - Block Transformer
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

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class BlockTransformer implements ClassTransformer {

    private static final String BLOCK_INTERFACE = "org/bukkit/block/Block";
    private static final Map<String, String> METHOD_MAP = new HashMap<>();
    private final Logger logger;
    private final String relocatedPatcherPath;

    static {
        // Methods that modify the block state (void return type)
        METHOD_MAP.put("setType", "(Lorg/bukkit/Material;)V");
        METHOD_MAP.put("setType", "(Lorg/bukkit/Material;Z)V");
        METHOD_MAP.put("setBlockData", "(Lorg/bukkit/block/data/BlockData;)V");
        METHOD_MAP.put("setBlockData", "(Lorg/bukkit/block/data/BlockData;Z)V");

        // Methods that retrieve block state (object return type)
        METHOD_MAP.put("getState", "()Lorg/bukkit/block/BlockState;");
        METHOD_MAP.put("getChunk", "()Lorg/bukkit/Chunk;");

        // Methods with primitive return types
        METHOD_MAP.put("getX", "()I");
        METHOD_MAP.put("getY", "()I");
        METHOD_MAP.put("getZ", "()I");
        METHOD_MAP.put("getLightLevel", "()B");
        METHOD_MAP.put("getLightFromSky", "()B");
        METHOD_MAP.put("getLightFromBlocks", "()B");
    }

    public BlockTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor parent) {
        return new BlockClassVisitor(parent, this.logger, this.relocatedPatcherPath);
    }

    private static class BlockClassVisitor extends ClassVisitor {
        private final Logger logger;
        private final String relocatedPatcherPath;

        public BlockClassVisitor(ClassVisitor cv, Logger logger, String relocatedPatcherPath) {
            super(Opcodes.ASM9, cv);
            this.logger = logger;
            this.relocatedPatcherPath = relocatedPatcherPath;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new BlockMethodVisitor(mv, access, name, descriptor, logger, relocatedPatcherPath);
        }
    }

    private static class BlockMethodVisitor extends AdviceAdapter {
        private final Logger logger;
        private final String relocatedPatcherPath;

        protected BlockMethodVisitor(MethodVisitor mv, int access, String name, String desc, Logger logger, String relocatedPatcherPath) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.logger = logger;
            this.relocatedPatcherPath = relocatedPatcherPath;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if (opcode == Opcodes.INVOKEINTERFACE && BLOCK_INTERFACE.equals(owner) && METHOD_MAP.containsKey(name) && METHOD_MAP.get(name).equals(desc)) {

                String newDesc;
                if (desc.startsWith("()")) { // No args
                    newDesc = "(Lorg/bukkit/block/Block;)" + desc.substring(2);
                } else {
                    newDesc = "(Lorg/bukkit/block/Block;" + desc.substring(1);
                }

                String patcherMethodName = "safe" + Character.toUpperCase(name.charAt(0)) + name.substring(1);

                super.visitMethodInsn(Opcodes.INVOKESTATIC, relocatedPatcherPath + "/FoliaPatcher", patcherMethodName, newDesc, false);
                logger.fine("[FoliaPhantom] Transformed Block." + name + " call");
                return;
            }
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }
    }
}
