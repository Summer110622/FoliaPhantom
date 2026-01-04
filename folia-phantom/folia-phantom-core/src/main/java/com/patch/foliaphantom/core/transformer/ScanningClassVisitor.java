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

/**
 * A lightweight ClassVisitor that performs a fast scan of method calls.
 * 
 * <p>
 * Its purpose is to determine if a class needs patching without performing
 * full bytecode transformation. This significantly improves performance for
 * plugins with many classes that don't use Bukkit scheduler or threading APIs.
 * </p>
 */
public class ScanningClassVisitor extends ClassVisitor {
    private boolean needsPatching = false;
    private final String relocatedPatcherPath;
    private static final String ORIGINAL_PATCHER_PATH = "com/patch/foliaphantom/core/patcher/FoliaPatcher";

    public ScanningClassVisitor(String relocatedPatcherPath) {
        super(Opcodes.ASM9);
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    /**
     * @return true if the class contains calls that require transformation
     */
    public boolean needsPatching() {
        return needsPatching;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        return new ScanningMethodVisitor();
    }

    private class ScanningMethodVisitor extends MethodVisitor {
        public ScanningMethodVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if (needsPatching) return;

            if (isPatcherRelated(owner) || isScheduler(owner) || isWorldCreator(owner, name) || isBlockOperation(owner, name) || isWorldOperation(owner, name) || isBukkitRunnableCall(opcode, name, desc)) {
                needsPatching = true;
            }
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }

        private boolean isPatcherRelated(String owner) {
            return ORIGINAL_PATCHER_PATH.equals(owner);
        }

        private boolean isScheduler(String owner) {
            return "org/bukkit/scheduler/BukkitScheduler".equals(owner) || "org/bukkit/scheduler/BukkitRunnable".equals(owner);
        }

        private boolean isWorldCreator(String owner, String name) {
            return "org/bukkit/WorldCreator".equals(owner) ||
                   ("org/bukkit/Bukkit".equals(owner) && "createWorld".equals(name)) ||
                   ("org/bukkit/plugin/Plugin".equals(owner) && "getDefaultWorldGenerator".equals(name));
        }

        private boolean isBlockOperation(String owner, String name) {
            if (!"org/bukkit/block/Block".equals(owner)) return false;
            switch (name) {
                case "setType":
                case "setBlockData":
                case "getState":
                case "getChunk":
                case "getX":
                case "getY":
                case "getZ":
                case "getLightLevel":
                case "getLightFromSky":
                case "getLightFromBlocks":
                    return true;
                default:
                    return false;
            }
        }

        private boolean isWorldOperation(String owner, String name) {
            return "org/bukkit/World".equals(owner) && ("spawn".equals(name) || "loadChunk".equals(name));
        }

        private boolean isBukkitRunnableCall(int opcode, String name, String desc) {
            if (opcode != Opcodes.INVOKEVIRTUAL) return false;
            String methodSignature = name + desc;
            return methodSignature.equals("runTask(Lorg/bukkit/plugin/Plugin;)Lorg/bukkit/scheduler/BukkitTask;") ||
                   methodSignature.equals("runTaskLater(Lorg/bukkit/plugin/Plugin;J)Lorg/bukkit/scheduler/BukkitTask;") ||
                   methodSignature.equals("runTaskTimer(Lorg/bukkit/plugin/Plugin;JJ)Lorg/bukkit/scheduler/BukkitTask;") ||
                   methodSignature.equals("runTaskAsynchronously(Lorg/bukkit/plugin/Plugin;)Lorg/bukkit/scheduler/BukkitTask;") ||
                   methodSignature.equals("runTaskLaterAsynchronously(Lorg/bukkit/plugin/Plugin;J)Lorg/bukkit/scheduler/BukkitTask;") ||
                   methodSignature.equals("runTaskTimerAsynchronously(Lorg/bukkit/plugin/Plugin;JJ)Lorg/bukkit/scheduler/BukkitTask;");
        }
    }
}
