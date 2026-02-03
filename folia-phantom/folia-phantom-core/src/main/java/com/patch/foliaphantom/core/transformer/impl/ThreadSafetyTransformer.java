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
        private boolean isSubclassOfJavaPlugin;
        private final String patcherPath;

        public ThreadSafetyVisitor(ClassVisitor cv, String patcherPath) {
            super(Opcodes.ASM9, cv);
            this.patcherPath = patcherPath;
        }

        @Override
        public void visit(int version, int access, String name, String sig, String superName, String[] interfaces) {
            this.className = name;
            this.isSubclassOfJavaPlugin = "org/bukkit/plugin/java/JavaPlugin".equals(superName);
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
            if (pluginFieldName != null || isSubclassOfJavaPlugin) {
                return new ThreadSafetyMethodVisitor(mv, access, name, desc, patcherPath, className,
                        pluginFieldName, pluginFieldDesc, isSubclassOfJavaPlugin);
            }
            return mv;
        }
    }

    private static class ThreadSafetyMethodVisitor extends AdviceAdapter {
        private final String patcherOwner;
        private final String pluginFieldOwner;
        private final String pluginFieldName;
        private final Type pluginFieldType;
        private final boolean isPluginClass;

        protected ThreadSafetyMethodVisitor(MethodVisitor mv, int access, String name, String desc,
                String patcherPath, String owner, String pfn, String pfd, boolean isPlugin) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherOwner = patcherPath;
            this.pluginFieldOwner = owner;
            this.pluginFieldName = pfn;
            this.pluginFieldType = pfd != null ? Type.getType(pfd) : null;
            this.isPluginClass = isPlugin;
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
                        if ("(Lorg/bukkit/Material;)V".equals(desc)) return transform(1, "_st", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/block/Block;Lorg/bukkit/Material;)V");
                        if ("(Lorg/bukkit/Material;Z)V".equals(desc)) return transform(2, "_stwp", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/block/Block;Lorg/bukkit/Material;Z)V");
                    }
                    if ("setBlockData".equals(name) && "(Lorg/bukkit/block/data/BlockData;Z)V".equals(desc)) {
                        return transform(2, "_bd", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/block/Block;Lorg/bukkit/block/data/BlockData;Z)V");
                    }
                    break;
                case "org/bukkit/World":
                    if ("spawn".equals(name) && "(Lorg/bukkit/Location;Ljava/lang/Class;)Lorg/bukkit/entity/Entity;".equals(desc)) {
                        return transform(2, "_ss", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;Ljava/lang/Class;)Lorg/bukkit/entity/Entity;");
                    }
                    if ("loadChunk".equals(name) && "(IIZ)V".equals(desc)) {
                        return transform(3, "_cl", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;IIZ)V");
                    }
                    if ("dropItem".equals(name) && "(Lorg/bukkit/Location;Lorg/bukkit/inventory/ItemStack;)Lorg/bukkit/entity/Item;".equals(desc)) {
                        return transform(2, "_di", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;Lorg/bukkit/inventory/ItemStack;)Lorg/bukkit/entity/Item;");
                    }
                    if ("dropItemNaturally".equals(name) && "(Lorg/bukkit/Location;Lorg/bukkit/inventory/ItemStack;)Lorg/bukkit/entity/Item;".equals(desc)) {
                        return transform(2, "_dn", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;Lorg/bukkit/inventory/ItemStack;)Lorg/bukkit/entity/Item;");
                    }
                    if ("createExplosion".equals(name) && "(Lorg/bukkit/Location;FZZ)Z".equals(desc)) {
                        return transform(4, "_ex", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;FZZ)Z");
                    }
                    if ("playEffect".equals(name) && "(Lorg/bukkit/Location;Lorg/bukkit/Effect;Ljava/lang/Object;)V".equals(desc)) {
                        return transform(3, "_pe", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;Lorg/bukkit/Effect;Ljava/lang/Object;)V");
                    }
                    if ("playSound".equals(name) && "(Lorg/bukkit/Location;Lorg/bukkit/Sound;FF)V".equals(desc)) {
                        return transform(4, "_sd", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;Lorg/bukkit/Sound;FF)V");
                    }
                    if ("strikeLightning".equals(name) && "(Lorg/bukkit/Location;)Lorg/bukkit/entity/LightningStrike;".equals(desc)) {
                        return transform(1, "_sl", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;)Lorg/bukkit/entity/LightningStrike;");
                    }
                    if ("generateTree".equals(name) && "(Lorg/bukkit/Location;Lorg/bukkit/TreeType;)Z".equals(desc)) {
                        return transform(2, "_gt", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;Lorg/bukkit/TreeType;)Z");
                    }
                    if ("setGameRule".equals(name) && "(Lorg/bukkit/GameRule;Ljava/lang/Object;)Z".equals(desc)) {
                        return transform(2, "_sr", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/GameRule;Ljava/lang/Object;)Z");
                    }
                    if ("getEntities".equals(name) && "()Ljava/util/List;".equals(desc)) {
                        return transform(0, "_ce", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;)Ljava/util/List;");
                    }
                    if ("getLivingEntities".equals(name) && "()Ljava/util/List;".equals(desc)) {
                        return transform(0, "_cle", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;)Ljava/util/List;");
                    }
                    if ("getNearbyEntities".equals(name) && "(Lorg/bukkit/Location;DDD)Ljava/util/Collection;".equals(desc)) {
                        return transform(4, "_gn", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;DDD)Ljava/util/Collection;");
                    }
                    if ("getPlayers".equals(name) && "()Ljava/util/List;".equals(desc)) {
                        return transform(0, "_gp", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;)Ljava/util/List;");
                    }
                    if ("rayTraceBlocks".equals(name) && "(Lorg/bukkit/Location;Lorg/bukkit/util/Vector;DLorg/bukkit/FluidCollisionMode;Z)Lorg/bukkit/util/RayTraceResult;".equals(desc)) {
                        return transform(5, "_rtb", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;Lorg/bukkit/util/Vector;DLorg/bukkit/FluidCollisionMode;Z)Lorg/bukkit/util/RayTraceResult;");
                    }
                    if ("rayTraceEntities".equals(name) && "(Lorg/bukkit/Location;Lorg/bukkit/util/Vector;DDLjava/util/function/Predicate;)Lorg/bukkit/util/RayTraceResult;".equals(desc)) {
                        return transform(5, "_rte", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;Lorg/bukkit/util/Vector;DDLjava/util/function/Predicate;)Lorg/bukkit/util/RayTraceResult;");
                    }
                    break;
                case "org/bukkit/Chunk":
                    if ("getEntities".equals(name) && "()[Lorg/bukkit/entity/Entity;".equals(desc)) {
                        return transform(0, "_ce", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/Chunk;)[Lorg/bukkit/entity/Entity;");
                    }
                    if ("load".equals(name)) {
                        if ("(Z)Z".equals(desc)) return transform(1, "_cl", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/Chunk;Z)Z");
                        if ("()Z".equals(desc)) return transform(0, "_cl", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/Chunk;)Z");
                    }
                    if ("unload".equals(name)) {
                        if ("(Z)Z".equals(desc)) return transform(1, "_cu", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/Chunk;Z)Z");
                        if ("()Z".equals(desc)) return transform(0, "_cu", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/Chunk;)Z");
                    }
                    break;
                case "org/bukkit/entity/Entity":
                case "org/bukkit/entity/LivingEntity":
                case "org/bukkit/entity/Player":
                case "org/bukkit/entity/Damageable":
                    switch (name) {
                        case "remove":
                            if ("()V".equals(desc)) return transform(0, "_rm", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Entity;)V");
                            break;
                        case "setVelocity":
                            if ("(Lorg/bukkit/util/Vector;)V".equals(desc)) return transform(1, "_sv", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Entity;Lorg/bukkit/util/Vector;)V");
                            break;
                        case "teleport":
                            if ("(Lorg/bukkit/Location;)Z".equals(desc)) return transform(1, "_te", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Entity;Lorg/bukkit/Location;)Z");
                            break;
                        case "setFireTicks":
                            if ("(I)V".equals(desc)) return transform(1, "_sf", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Entity;I)V");
                            break;
                        case "setCustomName":
                            if ("(Ljava/lang/String;)V".equals(desc)) return transform(1, "_sn", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Entity;Ljava/lang/String;)V");
                            break;
                        case "setGravity":
                            if ("(Z)V".equals(desc)) return transform(1, "_sg", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Entity;Z)V");
                            break;
                        case "damage":
                            if ("(D)V".equals(desc)) return transform(1, "_da", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Damageable;D)V");
                            if ("(DLorg/bukkit/entity/Entity;)V".equals(desc)) return transform(2, "_da", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Damageable;DLorg/bukkit/entity/Entity;)V");
                            break;
                        case "setAI":
                            if ("(Z)V".equals(desc)) return transform(1, "_ai", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/LivingEntity;Z)V");
                            break;
                        case "setGameMode":
                            if ("(Lorg/bukkit/GameMode;)V".equals(desc)) return transform(1, "_gm", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Player;Lorg/bukkit/GameMode;)V");
                            break;
                        case "getHealth":
                            if ("()D".equals(desc)) return transform(0, "_gh", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Player;)D");
                            break;
                        case "addScoreboardTag":
                            if ("(Ljava/lang/String;)Z".equals(desc)) return transform(1, "_at", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Entity;Ljava/lang/String;)Z");
                            break;
                        case "removeScoreboardTag":
                            if ("(Ljava/lang/String;)Z".equals(desc)) return transform(1, "_rt", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Entity;Ljava/lang/String;)Z");
                            break;
                        case "getNearbyEntities":
                            if ("(DDD)Ljava/util/List;".equals(desc)) return transform(3, "_gn", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Entity;DDD)Ljava/util/List;");
                            break;
                        case "addPotionEffect":
                            if ("(Lorg/bukkit/potion/PotionEffect;)Z".equals(desc)) return transform(1, "_ape", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/LivingEntity;Lorg/bukkit/potion/PotionEffect;)Z");
                            break;
                        case "removePotionEffect":
                            if ("(Lorg/bukkit/potion/PotionEffectType;)V".equals(desc)) return transform(1, "_rpe", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/LivingEntity;Lorg/bukkit/potion/PotionEffectType;)V");
                            break;
                        case "addPassenger":
                            if ("(Lorg/bukkit/entity/Entity;)Z".equals(desc)) return transform(1, "_ap", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Entity;Lorg/bukkit/entity/Entity;)Z");
                            break;
                        case "removePassenger":
                            if ("(Lorg/bukkit/entity/Entity;)Z".equals(desc)) return transform(1, "_rp", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Entity;Lorg/bukkit/entity/Entity;)Z");
                            break;
                        case "eject":
                            if ("()Z".equals(desc)) return transform(0, "_ej", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Entity;)Z");
                            break;
                    }
                    break;
                case "org/bukkit/block/BlockState":
                    if ("update".equals(name)) {
                        if ("()Z".equals(desc)) return transform(0, "_up", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/block/BlockState;)Z");
                        if ("(Z)Z".equals(desc)) return transform(1, "_up", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/block/BlockState;Z)Z");
                        if ("(ZZ)Z".equals(desc)) return transform(2, "_up", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/block/BlockState;ZZ)Z");
                    }
                    break;
            }
            return false;
        }

        private boolean transform(int argCount, String newName, String newDesc) {
            Type[] argTypes = Type.getArgumentTypes(newDesc);
            int[] locals = new int[argCount + 1];
            for (int i = argCount; i >= 0; i--) {
                locals[i] = newLocal(argTypes[i + 1]);
                storeLocal(locals[i]);
            }
            injectPluginInstance();
            for (int i = 0; i <= argCount; i++) {
                loadLocal(locals[i]);
            }
            super.visitMethodInsn(INVOKESTATIC, patcherOwner, newName, newDesc, false);
            return true;
        }

        private void injectPluginInstance() {
            if (isPluginClass) {
                loadThis();
            } else {
                loadThis();
                getField(Type.getObjectType(pluginFieldOwner), pluginFieldName, pluginFieldType);
            }
        }
    }
}
