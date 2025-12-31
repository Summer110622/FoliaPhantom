/*
 * Folia Phantom - Thread Safety Class Transformer
 * 
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import java.util.logging.Logger;

/**
 * Transforms thread-unsafe method calls into safe, plugin-context-aware static
 * calls.
 *
 * <p>
 * This transformer injects the owning {@code Plugin} instance into calls for
 * thread-safe operations, resolving a critical bug with the previous static
 * plugin reference. It uses an {@link AdviceAdapter} to robustly manipulate
 * the bytecode stack, ensuring the correct plugin context is always provided to
 * the FoliaPatcher runtime.
 * </p>
 */
public class ThreadSafetyTransformer implements ClassTransformer {
    private final Logger logger;
    private final String relocatedPatcherPath;
    private static final String PATCHER_CLASS = "FoliaPatcher";
    private static final String PLUGIN_DESC = "Lorg/bukkit/plugin/Plugin;";
    private static final String JAVA_PLUGIN_DESC = "Lorg/bukkit/plugin/java/JavaPlugin;";
    private static final Type PLUGIN_TYPE = Type.getType(PLUGIN_DESC);

    public ThreadSafetyTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new ThreadSafetyVisitor(next, relocatedPatcherPath, logger);
    }

    private static class ThreadSafetyVisitor extends ClassVisitor {
        private String className;
        private String pluginFieldName;
        private String pluginFieldDesc;
        private final String patcherPath;
        private final Logger logger;

        public ThreadSafetyVisitor(ClassVisitor cv, String patcherPath, Logger logger) {
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
                logger.fine("Found plugin field '" + name + "' in class " + className);
            }
            return super.visitField(access, name, desc, sig, val);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            if (pluginFieldName == null) {
                // This class does not appear to hold a plugin instance, so we can't patch it.
                return mv;
            }
            return new ThreadSafetyMethodVisitor(mv, access, name, desc, patcherPath, className, pluginFieldName,
                    pluginFieldDesc);
        }
    }

    private static class ThreadSafetyMethodVisitor extends AdviceAdapter {
        private final String patcherPath;
        private final String pluginFieldOwner;
        private final String pluginFieldName;
        private final String pluginFieldDesc;

        protected ThreadSafetyMethodVisitor(MethodVisitor mv, int access, String name, String desc,
                String patcherPath, String owner, String pfn, String pfd) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherPath = patcherPath;
            this.pluginFieldOwner = owner;
            this.pluginFieldName = pfn;
            this.pluginFieldDesc = pfd;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            String patcherOwner = patcherPath + "/" + PATCHER_CLASS;

            // --- Block.setType(Material) ---
            if (handleBlockSetType(owner, name, desc, patcherOwner)) {
                return;
            }
            // --- Block.setBlockData(BlockData, boolean) ---
            if (handleBlockSetData(owner, name, desc, patcherOwner)) {
                return;
            }
            // --- World.spawn(Location, Class) ---
            if (handleWorldSpawn(owner, name, desc, patcherOwner)) {
                return;
            }
            // --- World.loadChunk(int, int, boolean) ---
            if (handleWorldLoadChunk(owner, name, desc, patcherOwner)) {
                return;
            }

            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }

        private void injectPluginInstance() {
            loadThis();
            getField(Type.getObjectType(pluginFieldOwner), pluginFieldName, Type.getType(pluginFieldDesc));
        }

        private boolean handleBlockSetType(String owner, String name, String desc, String patcherOwner) {
            if (!"org/bukkit/block/Block".equals(owner) || !"setType".equals(name)) return false;

            if ("(Lorg/bukkit/Material;)V".equals(desc)) {
                // Stack: [block, material]
                int matLocal = newLocal(Type.getType("Lorg/bukkit/Material;"));
                storeLocal(matLocal);
                int blockLocal = newLocal(Type.getType("Lorg/bukkit/block/Block;"));
                storeLocal(blockLocal);

                // Stack: []
                injectPluginInstance();
                loadLocal(blockLocal);
                loadLocal(matLocal);

                // Stack: [plugin, block, material]
                String newDesc = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/block/Block;Lorg/bukkit/Material;)V";
                super.visitMethodInsn(INVOKESTATIC, patcherOwner, "safeSetType", newDesc, false);
                return true;

            } else if ("(Lorg/bukkit/Material;Z)V".equals(desc)) {
                // Stack: [block, material, boolean]
                int physicsLocal = newLocal(Type.BOOLEAN_TYPE);
                storeLocal(physicsLocal);
                int matLocal = newLocal(Type.getType("Lorg/bukkit/Material;"));
                storeLocal(matLocal);
                int blockLocal = newLocal(Type.getType("Lorg/bukkit/block/Block;"));
                storeLocal(blockLocal);

                // Stack: []
                injectPluginInstance();
                loadLocal(blockLocal);
                loadLocal(matLocal);
                loadLocal(physicsLocal);

                // Stack: [plugin, block, material, boolean]
                String newDesc = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/block/Block;Lorg/bukkit/Material;Z)V";
                super.visitMethodInsn(INVOKESTATIC, patcherOwner, "safeSetTypeWithPhysics", newDesc, false);
                return true;
            }
            return false;
        }

        private boolean handleBlockSetData(String owner, String name, String desc, String patcherOwner) {
            if ("org/bukkit/block/Block".equals(owner) && "setBlockData".equals(name) && "(Lorg/bukkit/block/data/BlockData;Z)V".equals(desc)) {
                // Stack: [block, blockdata, boolean]
                int physicsLocal = newLocal(Type.BOOLEAN_TYPE);
                storeLocal(physicsLocal);
                int dataLocal = newLocal(Type.getType("Lorg/bukkit/block/data/BlockData;"));
                storeLocal(dataLocal);
                int blockLocal = newLocal(Type.getType("Lorg/bukkit/block/Block;"));
                storeLocal(blockLocal);

                // Stack: []
                injectPluginInstance();
                loadLocal(blockLocal);
                loadLocal(dataLocal);
                loadLocal(physicsLocal);

                // Stack: [plugin, block, blockdata, boolean]
                String newDesc = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/block/Block;Lorg/bukkit/block/data/BlockData;Z)V";
                super.visitMethodInsn(INVOKESTATIC, patcherOwner, "safeSetBlockData", newDesc, false);
                return true;
            }
            return false;
        }

        private boolean handleWorldSpawn(String owner, String name, String desc, String patcherOwner) {
            if ("org/bukkit/World".equals(owner) && "spawn".equals(name) && "(Lorg/bukkit/Location;Ljava/lang/Class;)Lorg/bukkit/entity/Entity;".equals(desc)) {
                // Stack: [world, location, class]
                int classLocal = newLocal(Type.getType(Class.class));
                storeLocal(classLocal);
                int locLocal = newLocal(Type.getType("Lorg/bukkit/Location;"));
                storeLocal(locLocal);
                int worldLocal = newLocal(Type.getType("Lorg/bukkit/World;"));
                storeLocal(worldLocal);

                // Stack: []
                injectPluginInstance();
                loadLocal(worldLocal);
                loadLocal(locLocal);
                loadLocal(classLocal);

                // Stack: [plugin, world, location, class]
                String newDesc = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;Ljava/lang/Class;)Lorg/bukkit/entity/Entity;";
                super.visitMethodInsn(INVOKESTATIC, patcherOwner, "safeSpawnEntity", newDesc, false);
                return true;
            }
            return false;
        }

        private boolean handleWorldLoadChunk(String owner, String name, String desc, String patcherOwner) {
            if ("org/bukkit/World".equals(owner) && "loadChunk".equals(name) && "(IIZ)V".equals(desc)) {
                // Stack: [world, int, int, boolean]
                int genLocal = newLocal(Type.BOOLEAN_TYPE);
                storeLocal(genLocal);
                int zLocal = newLocal(Type.INT_TYPE);
                storeLocal(zLocal);
                int xLocal = newLocal(Type.INT_TYPE);
                storeLocal(xLocal);
                int worldLocal = newLocal(Type.getType("Lorg/bukkit/World;"));
                storeLocal(worldLocal);

                // Stack: []
                injectPluginInstance();
                loadLocal(worldLocal);
                loadLocal(xLocal);
                loadLocal(zLocal);
                loadLocal(genLocal);

                // Stack: [plugin, world, int, int, boolean]
                String newDesc = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;IIZ)V";
                super.visitMethodInsn(INVOKESTATIC, patcherOwner, "safeLoadChunk", newDesc, false);
                return true;
            }
            return false;
        }
    }
}
