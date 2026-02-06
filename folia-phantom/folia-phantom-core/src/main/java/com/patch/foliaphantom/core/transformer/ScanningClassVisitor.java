/*
 * Folia Phantom - Scanning Class Visitor
 *
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import java.util.Set;

/**
 * A lightweight ClassVisitor that performs a fast scan of method calls.
 *
 * <p>Its purpose is to determine if a class needs patching without performing
 * full bytecode transformation. This significantly improves performance for
 * plugins with many classes that don't use Bukkit scheduler or threading APIs.
 * </p>
 */
public class ScanningClassVisitor extends ClassVisitor {
    private boolean needsPatching = false;

    // Use a Set for O(1) lookups, which is faster than long boolean chains.
    private static final Set<String> INTERESTING_OWNERS = Set.of(
        "com/patch/foliaphantom/core/patcher/FoliaPatcher",
        "org/bukkit/scheduler/BukkitScheduler",
        "org/bukkit/scheduler/BukkitRunnable",
        "org/bukkit/WorldCreator",
        "org/bukkit/entity/Player",
        "org/bukkit/scoreboard/Scoreboard",
        "org/bukkit/scoreboard/Team",
        "org/bukkit/scoreboard/Objective",
        "org/bukkit/scoreboard/Score",
        "org/bukkit/Server",
        "org/bukkit/plugin/PluginManager",
        "org/bukkit/block/Block",
        "org/bukkit/World",
        "org/bukkit/Bukkit",
        "org/bukkit/plugin/Plugin",
        "org/bukkit/plugin/java/JavaPlugin",
        "org/bukkit/entity/Entity",
        "org/bukkit/entity/LivingEntity",
        "org/bukkit/entity/Damageable",
        "org/bukkit/block/BlockState"
    );

    public ScanningClassVisitor(String relocatedPatcherPath) {
        // relocatedPatcherPath is not used here but kept for constructor consistency.
        super(Opcodes.ASM9);
    }

    /**
     * @return true if the class contains calls that require transformation
     */
    public boolean needsPatching() {
        return needsPatching;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        if (needsPatching) {
            return null; // Stop scanning if already found.
        }
        return new ScanningMethodVisitor();
    }

    private class ScanningMethodVisitor extends MethodVisitor {
        public ScanningMethodVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if (needsPatching) {
                return;
            }

            // Fast path: check owner first.
            if (INTERESTING_OWNERS.contains(owner)) {
                 // At this point, the owner is interesting. Check the method name for specifics.
                switch (owner) {
                    case "org/bukkit/plugin/PluginManager":
                        if ("callEvent".equals(name)) needsPatching = true;
                        break;
                    case "org/bukkit/block/Block":
                        if ("setType".equals(name) || "setBlockData".equals(name)) needsPatching = true;
                        break;
                    case "org/bukkit/World":
                        switch (name) {
                            case "spawn":
                            case "loadChunk":
                            case "getEntities":
                            case "getLivingEntities":
                            case "getPlayers":
                            case "getNearbyEntities":
                            case "getHighestBlockAt":
                                needsPatching = true;
                                break;
                        }
                        break;
                    case "org/bukkit/Bukkit":
                    case "org/bukkit/Server":
                        if ("getOnlinePlayers".equals(name) || "getWorlds".equals(name) || "getPlayer".equals(name) || "getWorld".equals(name) || "createWorld".equals(name) || "dispatchCommand".equals(name) || "getOfflinePlayer".equals(name)) needsPatching = true;
                        break;
                    case "org/bukkit/plugin/Plugin":
                        if ("getDefaultWorldGenerator".equals(name)) needsPatching = true;
                        break;
                    case "org/bukkit/entity/Entity":
                    case "org/bukkit/entity/LivingEntity":
                    case "org/bukkit/entity/Damageable":
                    case "org/bukkit/entity/Player":
                         switch (name) {
                            case "addPotionEffect":
                            case "removePotionEffect":
                            case "addPassenger":
                            case "removePassenger":
                            case "eject":
                            case "addScoreboardTag":
                            case "removeScoreboardTag":
                            case "remove":
                            case "setVelocity":
                            case "teleport":
                            case "setFireTicks":
                            case "setCustomName":
                            case "setGravity":
                            case "damage":
                            case "setAI":
                            case "setGameMode":
                            case "getHealth":
                                needsPatching = true;
                                break;
                        }
                        break;
                    case "org/bukkit/block/BlockState":
                        if ("update".equals(name)) needsPatching = true;
                        break;
                    // For other owners in the set, their presence alone is enough.
                    default:
                        needsPatching = true;
                        break;
                }
            }

            if (needsPatching) return;

            // Secondary check for BukkitRunnable subclasses, which have a variable owner.
            if (opcode == Opcodes.INVOKEVIRTUAL && isBukkitRunnableInstanceMethod(name, desc)) {
                needsPatching = true;
            }
        }

        private boolean isBukkitRunnableInstanceMethod(String name, String desc) {
            String combined = name + desc;
            switch (combined) {
                case "runTask(Lorg/bukkit/plugin/Plugin;)Lorg/bukkit/scheduler/BukkitTask;":
                case "runTaskLater(Lorg/bukkit/plugin/Plugin;J)Lorg/bukkit/scheduler/BukkitTask;":
                case "runTaskTimer(Lorg/bukkit/plugin/Plugin;JJ)Lorg/bukkit/scheduler/BukkitTask;":
                case "runTaskAsynchronously(Lorg/bukkit/plugin/Plugin;)Lorg/bukkit/scheduler/BukkitTask;":
                case "runTaskLaterAsynchronously(Lorg/bukkit/plugin/Plugin;J)Lorg/bukkit/scheduler/BukkitTask;":
                case "runTaskTimerAsynchronously(Lorg/bukkit/plugin/Plugin;JJ)Lorg/bukkit/scheduler/BukkitTask;":
                    return true;
                default:
                    return false;
            }
        }
    }
}
