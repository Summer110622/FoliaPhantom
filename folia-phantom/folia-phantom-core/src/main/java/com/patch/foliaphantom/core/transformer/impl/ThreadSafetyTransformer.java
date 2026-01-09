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
 * Transforms thread-unsafe Bukkit API calls into their thread-safe FoliaPatcher equivalents.
 *
 * <p>This transformer robustly finds a reference to the plugin instance within a class
 * and injects it into the method call, redirecting it to a static method in the
 * {@code FoliaPatcher} runtime. It uses {@link AdviceAdapter} for safe and reliable
 * bytecode stack manipulation.</p>
 */
public class ThreadSafetyTransformer implements ClassTransformer {
    private final String relocatedPatcherPath;
    private static final String PATCHER_CLASS = "FoliaPatcher";
    private static final String PLUGIN_DESC = "Lorg/bukkit/plugin/Plugin;";
    private static final String JAVA_PLUGIN_DESC = "Lorg/bukkit/plugin/java/JavaPlugin;";

    public ThreadSafetyTransformer(Logger logger, String relocatedPatcherPath) {
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new ThreadSafetyVisitor(next, relocatedPatcherPath + "/" + PATCHER_CLASS);
    }

    private static class ThreadSafetyVisitor extends ClassVisitor {
        private String className;
        private String pluginFieldName;
        private String pluginFieldDesc;
        private final String patcherPath;

        public ThreadSafetyVisitor(ClassVisitor cv, String patcherPath) {
            super(Opcodes.ASM9, cv);
            this.patcherPath = patcherPath;
        }

        @Override
        public void visit(int version, int access, String name, String sig, String superName, String[] interfaces) {
            this.className = name;
            super.visit(version, access, name, sig, superName, interfaces);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String sig, Object val) {
            // Find the first field that is a Plugin or a subclass. This is our best-effort
            // guess for the plugin instance.
            if (pluginFieldName == null && (desc.equals(PLUGIN_DESC) || desc.equals(JAVA_PLUGIN_DESC))) {
                this.pluginFieldName = name;
                this.pluginFieldDesc = desc;
            }
            return super.visitField(access, name, desc, sig, val);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            // Only transform methods if we have found a plugin field to reference.
            // Without it, we cannot inject the required plugin instance.
            if (pluginFieldName != null) {
                return new ThreadSafetyMethodVisitor(mv, access, name, desc, patcherPath, className, pluginFieldName, pluginFieldDesc);
            }
            return mv;
        }
    }

    private static class ThreadSafetyMethodVisitor extends AdviceAdapter {
        private final String patcherOwner;
        private final String pluginFieldOwner;
        private final String pluginFieldName;
        private final Type pluginFieldType;

        protected ThreadSafetyMethodVisitor(MethodVisitor mv, int access, String name, String desc,
                String patcherPath, String owner, String pfn, String pfd) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherOwner = patcherPath;
            this.pluginFieldOwner = owner;
            this.pluginFieldName = pfn;
            this.pluginFieldType = Type.getType(pfd);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if (tryHandle(owner, name, desc)) {
                return;
            }
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }

        private boolean tryHandle(String owner, String name, String desc) {
            switch (owner) {
                case "org/bukkit/block/Block":
                    if ("setType".equals(name)) {
                        if ("(Lorg/bukkit/Material;)V".equals(desc)) return transform(1, "safeSetType", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/block/Block;Lorg/bukkit/Material;)V");
                        if ("(Lorg/bukkit/Material;Z)V".equals(desc)) return transform(2, "safeSetTypeWithPhysics", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/block/Block;Lorg/bukkit/Material;Z)V");
                    }
                    if ("setBlockData".equals(name) && "(Lorg/bukkit/block/data/BlockData;Z)V".equals(desc)) {
                        return transform(2, "safeSetBlockData", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/block/Block;Lorg/bukkit/block/data/BlockData;Z)V");
                    }
                    break;
                case "org/bukkit/World":
                    if ("spawn".equals(name) && "(Lorg/bukkit/Location;Ljava/lang/Class;)Lorg/bukkit/entity/Entity;".equals(desc)) {
                        return transform(2, "safeSpawnEntity", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;Ljava/lang/Class;)Lorg/bukkit/entity/Entity;");
                    }
                    if ("loadChunk".equals(name) && "(IIZ)V".equals(desc)) {
                        return transform(3, "safeLoadChunk", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;IIZ)V");
                    }
                    if ("dropItem".equals(name) && "(Lorg/bukkit/Location;Lorg/bukkit/inventory/ItemStack;)Lorg/bukkit/entity/Item;".equals(desc)) {
                        return transform(2, "safeDropItem", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;Lorg/bukkit/inventory/ItemStack;)Lorg/bukkit/entity/Item;");
                    }
                    if ("dropItemNaturally".equals(name) && "(Lorg/bukkit/Location;Lorg/bukkit/inventory/ItemStack;)Lorg/bukkit/entity/Item;".equals(desc)) {
                        return transform(2, "safeDropItemNaturally", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;Lorg/bukkit/inventory/ItemStack;)Lorg/bukkit/entity/Item;");
                    }
                    if ("createExplosion".equals(name) && "(Lorg/bukkit/Location;FZZ)Z".equals(desc)) {
                        return transform(4, "safeCreateExplosion", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;FZZ)Z");
                    }
                    if ("playEffect".equals(name) && "(Lorg/bukkit/Location;Lorg/bukkit/Effect;Ljava/lang/Object;)V".equals(desc)) {
                        return transform(3, "safePlayEffect", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;Lorg/bukkit/Effect;Ljava/lang/Object;)V");
                    }
                    if ("playSound".equals(name) && "(Lorg/bukkit/Location;Lorg/bukkit/Sound;FF)V".equals(desc)) {
                        return transform(4, "safePlaySound", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;Lorg/bukkit/Sound;FF)V");
                    }
                    if ("strikeLightning".equals(name) && "(Lorg/bukkit/Location;)Lorg/bukkit/entity/LightningStrike;".equals(desc)) {
                        return transform(1, "safeStrikeLightning", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;)Lorg/bukkit/entity/LightningStrike;");
                    }
                    if ("generateTree".equals(name) && "(Lorg/bukkit/Location;Lorg/bukkit/TreeType;)Z".equals(desc)) {
                        return transform(2, "safeGenerateTree", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;Lorg/bukkit/TreeType;)Z");
                    }
                    if ("setGameRule".equals(name) && "(Lorg/bukkit/GameRule;Ljava/lang/Object;)Z".equals(desc)) {
                        return transform(2, "safeSetGameRule", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/GameRule;Ljava/lang/Object;)Z");
                    }
                    break;
                case "org/bukkit/entity/Entity":
                    if ("teleport".equals(name) && "(Lorg/bukkit/Location;)Z".equals(desc)) {
                        return transform(1, "safeTeleport", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Entity;Lorg/bukkit/Location;)Z");
                    }
                    if ("remove".equals(name) && "()V".equals(desc)) {
                        return transform(0, "safeRemove", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Entity;)V");
                    }
                    break;
            }
            return false;
        }

        /**
         * Generic transformation logic.
         * @param argCount Number of arguments on the stack (excluding the instance).
         * @param newName The name of the static method in FoliaPatcher.
         * @param newDesc The descriptor of the static method.
         */
        private boolean transform(int argCount, String newName, String newDesc) {
            Type[] argTypes = Type.getArgumentTypes(newDesc);

            // Store all original arguments (including the instance) into local variables.
            // The instance ('world' or 'block') is at argTypes[1]. The actual args start at [2].
            int[] locals = new int[argCount + 1];
            for (int i = argCount; i >= 0; i--) {
                // The new descriptor has Plugin as the first arg, so original args are offset by 1.
                locals[i] = newLocal(argTypes[i + 1]);
                storeLocal(locals[i]);
            }

            // Stack is now empty.
            // Push the plugin instance onto the stack.
            injectPluginInstance();

            // Push the original arguments back onto the stack from locals.
            for (int i = 0; i <= argCount; i++) {
                loadLocal(locals[i]);
            }

            super.visitMethodInsn(INVOKESTATIC, patcherOwner, newName, newDesc, false);
            return true;
        }

        private void injectPluginInstance() {
            // Load `this` onto the stack, then get the plugin field from it.
            loadThis();
            getField(Type.getObjectType(pluginFieldOwner), pluginFieldName, pluginFieldType);
        }
    }
}
