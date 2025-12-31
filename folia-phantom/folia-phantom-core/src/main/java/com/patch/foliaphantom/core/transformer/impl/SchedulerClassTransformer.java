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
 * is always passed, thus avoiding the critical static reference bug.
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
        private String className;
        private String pluginFieldName;
        private String pluginFieldDesc;

        public SchedulerClassVisitor(ClassVisitor cv, String patcherPath) {
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
            if (pluginFieldName == null && (desc.equals("Lorg/bukkit/plugin/Plugin;") || desc.equals("Lorg/bukkit/plugin/java/JavaPlugin;"))) {
                this.pluginFieldName = name;
                this.pluginFieldDesc = desc;
            }
            return super.visitField(access, name, desc, sig, val);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            return new SchedulerMethodVisitor(mv, access, name, desc, patcherPath, className, pluginFieldName,
                    pluginFieldDesc);
        }
    }

    private static class SchedulerMethodVisitor extends AdviceAdapter {
        private final String patcherPath;
        private final String pluginFieldOwner;
        private final String pluginFieldName;
        private final String pluginFieldDesc;

        protected SchedulerMethodVisitor(MethodVisitor mv, int access, String name, String desc,
                String patcherPath, String owner, String pfn, String pfd) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherPath = patcherPath;
            this.pluginFieldOwner = owner;
            this.pluginFieldName = pfn;
            this.pluginFieldDesc = pfd;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if (BUKKIT_SCHEDULER_OWNER.equals(owner) && opcode == INVOKEINTERFACE && isSchedulerMethod(name, desc)) {
                // Stack: [scheduler, plugin, runnable, ...]
                // We need to insert the scheduler instance before the plugin instance for the static call.
                // The new signature is (scheduler, plugin, ...). The original is (plugin, ...).
                String newDesc = "(Lorg/bukkit/scheduler/BukkitScheduler;" + desc.substring(1);
                super.visitMethodInsn(INVOKESTATIC, patcherPath, name, newDesc, false);
                return;
            }

            if (opcode == INVOKEVIRTUAL && isBukkitRunnableInstanceMethod(name, desc)) {
                if (pluginFieldName == null) {
                    // Cannot patch without a plugin field reference.
                    super.visitMethodInsn(opcode, owner, name, desc, isInterface);
                    return;
                }

                // Stack before call: [runnable, plugin, arg1, arg2...]
                // We need to transform this to a static call:
                // FoliaPatcher.method(runnable, plugin, arg1, arg2...)
                String newName = name + "_onRunnable";
                String newDesc = "(Ljava/lang/Runnable;" + desc.substring(1);
                super.visitMethodInsn(INVOKESTATIC, patcherPath, newName, newDesc, false);
                return;
            }

            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }

        private boolean isSchedulerMethod(String name, String desc) {
            return (name.startsWith("runTask") || name.startsWith("scheduleSync") || name.startsWith("scheduleAsync")
                    || name.startsWith("cancel"))
                    && desc.contains("Lorg/bukkit/plugin/Plugin;");
        }

        private boolean isBukkitRunnableInstanceMethod(String name, String desc) {
            return name.startsWith("runTask") && desc.startsWith("(Lorg/bukkit/plugin/Plugin;");
        }
    }
}
