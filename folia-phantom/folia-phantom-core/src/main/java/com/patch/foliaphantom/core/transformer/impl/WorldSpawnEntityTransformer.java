/*
 * Folia Phantom - World Spawn Entity Transformer
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

public class WorldSpawnEntityTransformer implements ClassTransformer {

    private final Logger logger;
    private final String relocatedPatcherPath;

    public WorldSpawnEntityTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor parent) {
        return new WorldSpawnEntityClassVisitor(parent, relocatedPatcherPath, logger);
    }

    private static class WorldSpawnEntityClassVisitor extends ClassVisitor {
        private final String relocatedPatcherPath;
        private final Logger logger;
        private String className;
        private String pluginField;
        private String pluginDesc;

        public WorldSpawnEntityClassVisitor(ClassVisitor cv, String relocatedPatcherPath, Logger logger) {
            super(Opcodes.ASM9, cv);
            this.relocatedPatcherPath = relocatedPatcherPath;
            this.logger = logger;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name;
            if (superName.equals("org/bukkit/plugin/java/JavaPlugin")) {
                pluginField = "this";
                pluginDesc = "L" + className + ";";
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public org.objectweb.asm.FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (descriptor.equals("Lorg/bukkit/plugin/Plugin;") || descriptor.equals("Lorg/bukkit/plugin/java/JavaPlugin;")) {
                pluginField = name;
                pluginDesc = descriptor;
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (pluginField == null) {
                return mv;
            }
            return new WorldSpawnEntityAdviceAdapter(Opcodes.ASM9, mv, access, name, descriptor, relocatedPatcherPath, className, pluginField, pluginDesc, logger);
        }
    }

    private static class WorldSpawnEntityAdviceAdapter extends AdviceAdapter {
        private final String relocatedPatcherPath;
        private final String className;
        private final String pluginField;
        private final String pluginDesc;
        private final Logger logger;
        private final boolean isStatic;

        protected WorldSpawnEntityAdviceAdapter(int api, MethodVisitor mv, int access, String name, String descriptor, String relocatedPatcherPath, String className, String pluginField, String pluginDesc, Logger logger) {
            super(api, mv, access, name, descriptor);
            this.relocatedPatcherPath = relocatedPatcherPath;
            this.className = className;
            this.pluginField = pluginField;
            this.pluginDesc = pluginDesc;
            this.logger = logger;
            this.isStatic = (access & Opcodes.ACC_STATIC) != 0;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (isStatic) {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                return;
            }

            if (opcode == INVOKEINTERFACE && owner.equals("org/bukkit/World")) {
                boolean matched = false;
                String newDescriptor = null;

                if (name.equals("spawn") && descriptor.equals("(Lorg/bukkit/Location;Ljava/lang/Class;)Lorg/bukkit/entity/Entity;")) {
                    matched = true;
                    name = "safeSpawnEntity";
                    newDescriptor = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;Ljava/lang/Class;)Lorg/bukkit/entity/Entity;";
                } else if (name.equals("spawnEntity") && descriptor.equals("(Lorg/bukkit/Location;Lorg/bukkit/entity/EntityType;)Lorg/bukkit/entity/Entity;")) {
                    // This specific overload is deprecated but plugins might still use it.
                    // FoliaPatcher's safeSpawnEntity handles the Class version, so we adapt.
                    logger.fine("Found spawnEntity call in " + className + ", redirecting to FoliaPatcher and adapting EntityType to Class.");

                    // Stack: [world, location, entityType]
                    // 1. Get entity class from entity type
                    visitMethodInsn(INVOKEINTERFACE, "org/bukkit/entity/EntityType", "getEntityClass", "()Ljava/lang/Class;", true);
                    // Stack: [world, location, class]

                    // Now we can use the same logic as the spawn(Location, Class) method.
                    matched = true;
                    name = "safeSpawnEntity"; // The target method name in FoliaPatcher
                    newDescriptor = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;Ljava/lang/Class;)Lorg/bukkit/entity/Entity;";
                } else if (name.equals("dropItem") && descriptor.equals("(Lorg/bukkit/Location;Lorg/bukkit/inventory/ItemStack;)Lorg/bukkit/entity/Item;")) {
                    matched = true;
                    name = "safeDropItem";
                    newDescriptor = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;Lorg/bukkit/inventory/ItemStack;)Lorg/bukkit/entity/Item;";
                } else if (name.equals("dropItemNaturally") && descriptor.equals("(Lorg/bukkit/Location;Lorg/bukkit/inventory/ItemStack;)Lorg/bukkit/entity/Item;")) {
                    matched = true;
                    name = "safeDropItemNaturally";
                    newDescriptor = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;Lorg/bukkit/inventory/ItemStack;)Lorg/bukkit/entity/Item;";
                }


                if (matched) {
                    logger.fine("Found " + name + " call in " + className + ", redirecting to FoliaPatcher.");

                    // Store arguments in local variables for readability
                    Type[] argTypes = Type.getArgumentTypes(descriptor);
                    int[] locals = new int[argTypes.length];
                    for (int i = argTypes.length - 1; i >= 0; i--) {
                        locals[i] = newLocal(argTypes[i]);
                        storeLocal(locals[i]);
                    }

                    // Store the 'world' instance
                    int worldLocal = newLocal(Type.getType("Lorg/bukkit/World;"));
                    storeLocal(worldLocal);

                    // Load plugin instance (this)
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, className, pluginField, pluginDesc);

                    // Load 'world' instance
                    loadLocal(worldLocal);

                    // Load the original arguments back
                    for (int local : locals) {
                        loadLocal(local);
                    }

                    super.visitMethodInsn(INVOKESTATIC, relocatedPatcherPath + "/FoliaPatcher", name, newDescriptor, false);
                    return;
                }
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }
    }
}
