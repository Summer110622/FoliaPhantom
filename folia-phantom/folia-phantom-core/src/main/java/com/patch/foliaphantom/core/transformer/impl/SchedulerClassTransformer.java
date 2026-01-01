/*
 * Folia Phantom - Scheduler Class Transformer
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
 * Transforms {@code BukkitScheduler} and {@code BukkitRunnable} method calls to
 * be Folia-compatible.
 *
 * <p>
 * This class uses advanced bytecode manipulation with {@link AdviceAdapter} to
 * redirect scheduler calls to the {@code FoliaPatcher} runtime. It correctly
 * handles both static and virtual calls, ensuring the owning plugin's context
 * is always passed.
 * </p>
 */
public class SchedulerClassTransformer implements ClassTransformer {
    private final Logger logger;
    private final String relocatedPatcherPath;
    private static final String PATCHER_CLASS = "FoliaPatcher";
    private static final String BUKKIT_SCHEDULER_OWNER = "org/bukkit/scheduler/BukkitScheduler";

    public SchedulerClassTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new SchedulerClassVisitor(next, relocatedPatcherPath + "/" + PATCHER_CLASS);
    }

    private static class SchedulerClassVisitor extends ClassVisitor {
        private final String patcherPath;

        public SchedulerClassVisitor(ClassVisitor cv, String patcherPath) {
            super(Opcodes.ASM9, cv);
            this.patcherPath = patcherPath;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            return new SchedulerMethodVisitor(mv, access, name, desc, patcherPath);
        }
    }

    private static class SchedulerMethodVisitor extends AdviceAdapter {
        private final String patcherPath;

        protected SchedulerMethodVisitor(MethodVisitor mv, int access, String name, String desc, String patcherPath) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherPath = patcherPath;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            // Redirect: scheduler.runTask(plugin, ...) -> FoliaPatcher.runTask(plugin, ...)
            if (BUKKIT_SCHEDULER_OWNER.equals(owner) && opcode == INVOKEINTERFACE && isSchedulerMethod(name, desc)) {
                // Original stack: [scheduler, plugin, arg1, arg2...]
                // We need to call a static method, so we must pop the scheduler instance.
                // To do this robustly, we store args into locals, pop, then load args back.
                Type[] argTypes = Type.getArgumentTypes(desc);
                int[] locals = new int[argTypes.length];
                for (int i = argTypes.length - 1; i >= 0; i--) {
                    locals[i] = newLocal(argTypes[i]);
                    storeLocal(locals[i]);
                }

                // Stack is now just [scheduler]. Pop it.
                pop();

                // Load args back onto the stack.
                for (int i = 0; i < argTypes.length; i++) {
                    loadLocal(locals[i]);
                }

                // Call the static FoliaPatcher method. The descriptor is the same as the interface method.
                super.visitMethodInsn(INVOKESTATIC, patcherPath, name, desc, false);
                return;
            }

            // Redirect: runnable.runTask(plugin) -> FoliaPatcher.runTask_onRunnable(runnable, plugin)
            if (opcode == INVOKEVIRTUAL && isBukkitRunnableInstanceMethod(name, desc)) {
                // The owner of the call is the BukkitRunnable instance.
                // Stack before: [runnable, plugin, ...]
                // The static method needs the runnable as the first argument, which is already in place.
                String newName = name + "_onRunnable";
                String newDesc = "(Ljava/lang/Runnable;" + desc.substring(1);
                super.visitMethodInsn(INVOKESTATIC, patcherPath, newName, newDesc, false);
                return;
            }

            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }

        private boolean isSchedulerMethod(String name, String desc) {
            boolean isCancel = name.startsWith("cancelTask"); // cancelTask(id) or cancelTasks(plugin)
            boolean isScheduler = name.startsWith("runTask") || name.startsWith("scheduleSync") || name.startsWith("scheduleAsync");
            return isCancel || isScheduler;
        }

        private boolean isBukkitRunnableInstanceMethod(String name, String desc) {
            // Heuristic: any virtual call to a method named runTask* that takes a Plugin as its first arg.
            return name.startsWith("runTask") && desc.startsWith("(Lorg/bukkit/plugin/Plugin;");
        }
    }
}
