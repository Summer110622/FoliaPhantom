package com.patch.foliaphantom.core.transformer;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * A lightweight ClassVisitor that scans for method calls that need patching.
 * This is used as a fast-fail heuristic to avoid expensive ClassWriter
 * operations
 * on classes that don't need any transformations.
 */
public class ScanningClassVisitor extends ClassVisitor {
    private boolean needsPatching = false;

    public ScanningClassVisitor() {
        super(Opcodes.ASM9);
    }

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
            // Check for BukkitScheduler calls
            if ("org/bukkit/scheduler/BukkitScheduler".equals(owner)) {
                needsPatching = true;
                return;
            }

            // Check for BukkitRunnable calls
            if ("org/bukkit/scheduler/BukkitRunnable".equals(owner)) {
                needsPatching = true;
                return;
            }

            // Check for Block.setType calls
            if ("org/bukkit/block/Block".equals(owner) && name.equals("setType")) {
                needsPatching = true;
                return;
            }

            // Check for WorldCreator calls
            if ("org/bukkit/WorldCreator".equals(owner)) {
                needsPatching = true;
                return;
            }

            // Check for Bukkit.createWorld calls
            if ("org/bukkit/Bukkit".equals(owner) && name.equals("createWorld")) {
                needsPatching = true;
                return;
            }

            // Check for Plugin.getDefaultWorldGenerator calls
            if ("org/bukkit/plugin/Plugin".equals(owner) && name.equals("getDefaultWorldGenerator")) {
                needsPatching = true;
                return;
            }

            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }
    }
}
