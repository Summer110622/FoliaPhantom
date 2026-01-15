/*
 * This file is part of Folia Phantom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 Marv
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
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
            if (needsPatching)
                return;

            // Define targets that trigger the 'needsPatching' flag
            if (ORIGINAL_PATCHER_PATH.equals(owner) ||
                    "org/bukkit/scheduler/BukkitScheduler".equals(owner) ||
                    "org/bukkit/scheduler/BukkitRunnable".equals(owner) ||
                    "org/bukkit/WorldCreator".equals(owner) ||
                    "org/bukkit/entity/Player".equals(owner) ||
                    "org/bukkit/scoreboard/Scoreboard".equals(owner) ||
                    "org/bukkit/scoreboard/Team".equals(owner) ||
                    "org/bukkit/scoreboard/Objective".equals(owner) ||
                    "org/bukkit/scoreboard/Score".equals(owner) ||
                    ("org/bukkit/Server".equals(owner) && name.equals("getOnlinePlayers")) || // Added for ServerGetOnlinePlayersTransformer
                    ("org/bukkit/plugin/PluginManager".equals(owner) && name.equals("callEvent")) || // Added for EventCallTransformer
                    ("org/bukkit/block/Block".equals(owner) && name.equals("setType")) ||
                    ("org/bukkit/block/Block".equals(owner) && name.equals("setBlockData")) ||
                    ("org/bukkit/World".equals(owner) && name.equals("spawn")) ||
                    ("org/bukkit/World".equals(owner) && name.equals("loadChunk")) ||
                    ("org/bukkit/World".equals(owner) && name.equals("getEntities")) ||
                    ("org/bukkit/World".equals(owner) && name.equals("getLivingEntities")) ||
                    ("org/bukkit/World".equals(owner) && name.equals("getPlayers")) ||
                    ("org/bukkit/World".equals(owner) && name.equals("getNearbyEntities")) ||
                    ("org/bukkit/World".equals(owner) && name.equals("getPlayers")) ||
                    ("org/bukkit/Bukkit".equals(owner) && name.equals("createWorld")) ||
                    ("org/bukkit/plugin/Plugin".equals(owner) && name.equals("getDefaultWorldGenerator"))) {
                needsPatching = true;
                return;
            }

            // Check for unsafe Entity methods
            if ("org/bukkit/entity/Entity".equals(owner)) {
                switch (name) {
                    case "remove":
                    case "setVelocity":
                    case "teleport":
                    case "setFireTicks":
                    case "setCustomName":
                    case "setGravity":
                        needsPatching = true;
                        return;
                }
            }

            // Check for BukkitRunnable instance method calls
            if (opcode == Opcodes.INVOKEVIRTUAL && isBukkitRunnableInstanceMethod(name, desc)) {
                needsPatching = true;
                return;
            }

            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }

        private boolean isBukkitRunnableInstanceMethod(String name, String desc) {
            String mk = name + desc;
            return mk.equals("runTask(Lorg/bukkit/plugin/Plugin;)Lorg/bukkit/scheduler/BukkitTask;") ||
                    mk.equals("runTaskLater(Lorg/bukkit/plugin/Plugin;J)Lorg/bukkit/scheduler/BukkitTask;") ||
                    mk.equals("runTaskTimer(Lorg/bukkit/plugin/Plugin;JJ)Lorg/bukkit/scheduler/BukkitTask;") ||
                    mk.equals("runTaskAsynchronously(Lorg/bukkit/plugin/Plugin;)Lorg/bukkit/scheduler/BukkitTask;") ||
                    mk.equals(
                            "runTaskLaterAsynchronously(Lorg/bukkit/plugin/Plugin;J)Lorg/bukkit/scheduler/BukkitTask;")
                    ||
                    mk.equals(
                            "runTaskTimerAsynchronously(Lorg/bukkit/plugin/Plugin;JJ)Lorg/bukkit/scheduler/BukkitTask;");
        }
    }
}
