/*
 * Folia Phantom - Inventory Transformer
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
 * Transforms inventory-related method calls to be Folia-compatible.
 *
 * <p>This transformer targets methods on {@code org.bukkit.inventory.Inventory}
 * and {@code org.bukkit.entity.HumanEntity} to ensure they are executed on the
 * appropriate region thread.</p>
 */
public class InventoryTransformer implements ClassTransformer {
    private final Logger logger;
    private final String relocatedPatcherPath;
    private static final String PATCHER_CLASS = "FoliaPatcher";
    private static final String INVENTORY_OWNER = "org/bukkit/inventory/Inventory";
    private static final String HUMAN_ENTITY_OWNER = "org/bukkit/entity/HumanEntity";

    public InventoryTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new InventoryClassVisitor(next, relocatedPatcherPath + "/" + PATCHER_CLASS);
    }

    private static class InventoryClassVisitor extends ClassVisitor {
        private final String patcherPath;
        private String pluginFieldOwner;
        private String pluginFieldName;
        private boolean isPluginClass = false;

        public InventoryClassVisitor(ClassVisitor cv, String patcherPath) {
            super(Opcodes.ASM9, cv);
            this.patcherPath = patcherPath;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.pluginFieldOwner = name;
            if ("org/bukkit/plugin/java/JavaPlugin".equals(superName)) {
                isPluginClass = true;
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if ("Lorg/bukkit/plugin/Plugin;".equals(descriptor) || "Lorg/bukkit/plugin/java/JavaPlugin;".equals(descriptor)) {
                if (pluginFieldName == null) {
                    pluginFieldName = name;
                }
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            if (pluginFieldName == null && isPluginClass) {
                // Heuristic: If this is the main plugin class, the plugin instance is just 'this'.
                return new InventoryMethodVisitor(mv, access, name, desc, patcherPath, pluginFieldOwner, "this");
            }
            return new InventoryMethodVisitor(mv, access, name, desc, patcherPath, pluginFieldOwner, pluginFieldName);
        }
    }

    private static class InventoryMethodVisitor extends AdviceAdapter {
        private final String patcherPath;
        private final String pluginFieldOwner;
        private final String pluginFieldName;

        protected InventoryMethodVisitor(MethodVisitor mv, int access, String name, String desc, String patcherPath, String pluginFieldOwner, String pluginFieldName) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherPath = patcherPath;
            this.pluginFieldOwner = pluginFieldOwner;
            this.pluginFieldName = pluginFieldName;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if (pluginFieldName == null) {
                // Cannot proceed if we don't know how to get the plugin instance.
                super.visitMethodInsn(opcode, owner, name, desc, isInterface);
                return;
            }

            if (opcode == INVOKEINTERFACE) {
                boolean isInventoryMethod = INVENTORY_OWNER.equals(owner) && isTargetInventoryMethod(name, desc);
                boolean isHumanEntityMethod = HUMAN_ENTITY_OWNER.equals(owner) && isTargetHumanEntityMethod(name, desc);

                if (isInventoryMethod || isHumanEntityMethod) {
                    // Original stack: [instance, arg1, arg2, ...]
                    // We need to inject the Plugin instance at the beginning of the args.
                    // New stack: [plugin, instance, arg1, arg2, ...]

                    Type[] argTypes = Type.getArgumentTypes(desc);
                    int[] locals = new int[argTypes.length];
                     for (int i = argTypes.length - 1; i >= 0; i--) {
                        locals[i] = newLocal(argTypes[i]);
                        storeLocal(locals[i]);
                    }

                    // Pop the instance
                    int instanceLocal = newLocal(Type.getType(Object.class));
                    storeLocal(instanceLocal);

                    // Load plugin instance.
                    loadThis(); // ALOAD 0
                    if (!"this".equals(pluginFieldName)) {
                         super.visitFieldInsn(GETFIELD, pluginFieldOwner, pluginFieldName, "Lorg/bukkit/plugin/Plugin;");
                    }

                    // Load instance and args back.
                    loadLocal(instanceLocal);
                    for (int i = 0; i < argTypes.length; i++) {
                        loadLocal(locals[i]);
                    }

                    String newDesc = "(Lorg/bukkit/plugin/Plugin;L" + owner + ";" + desc.substring(1);
                    super.visitMethodInsn(INVOKESTATIC, patcherPath, name, newDesc, false);
                    return;
                }
            }
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }

        private boolean isTargetInventoryMethod(String name, String desc) {
            switch (name) {
                case "addItem":
                case "removeItem":
                case "removeItemAnySlot":
                    return "([Lorg/bukkit/inventory/ItemStack;)Ljava/util/HashMap;".equals(desc);
                case "setItem":
                    return "(ILorg/bukkit/inventory/ItemStack;)V".equals(desc);
                case "clear":
                    return "()V".equals(desc) || "(I)V".equals(desc);
                case "remove":
                    return "(Lorg/bukkit/inventory/ItemStack;)V".equals(desc);
                default:
                    return false;
            }
        }

        private boolean isTargetHumanEntityMethod(String name, String desc) {
            switch (name) {
                case "openInventory":
                    return "(Lorg/bukkit/inventory/Inventory;)Lorg/bukkit/inventory/InventoryView;".equals(desc);
                case "closeInventory":
                    return "()V".equals(desc);
                case "setItemOnCursor":
                    return "(Lorg/bukkit/inventory/ItemStack;)V".equals(desc);
                default:
                    return false;
            }
        }
    }
}
