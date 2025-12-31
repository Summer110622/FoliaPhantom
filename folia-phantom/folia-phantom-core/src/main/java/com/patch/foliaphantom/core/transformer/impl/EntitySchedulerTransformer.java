/*
 * Folia Phantom - Entity Scheduler Transformer
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
 * Transforms scheduler calls on Entity instances to be Folia-compatible.
 *
 * <p>
 * This transformer is prepared to intercept calls related to entity-specific
 * scheduling. It uses the same robust, context-aware plugin injection
 * mechanism as other transformers, ensuring that any future transformations
 * will be safe in multi-plugin environments.
 * </p>
 */
public class EntitySchedulerTransformer implements ClassTransformer {
    private final Logger logger;
    private final String relocatedPatcherPath;
    private static final String PATCHER_CLASS = "FoliaPatcher";

    public EntitySchedulerTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new EntitySchedulerVisitor(next, relocatedPatcherPath + "/" + PATCHER_CLASS);
    }

    private static class EntitySchedulerVisitor extends ClassVisitor {
        private final String patcherPath;
        private String className;
        private String pluginFieldName;
        private String pluginFieldDesc;

        public EntitySchedulerVisitor(ClassVisitor cv, String patcherPath) {
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
            return new EntitySchedulerMethodVisitor(mv, access, name, desc, patcherPath, className, pluginFieldName,
                    pluginFieldDesc);
        }
    }

    private static class EntitySchedulerMethodVisitor extends AdviceAdapter {
        private final String patcherPath;
        private final String pluginFieldOwner;
        private final String pluginFieldName;
        private final String pluginFieldDesc;

        protected EntitySchedulerMethodVisitor(MethodVisitor mv, int access, String name, String desc,
                String patcherPath, String owner, String pfn, String pfd) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherPath = patcherPath;
            this.pluginFieldOwner = owner;
            this.pluginFieldName = pfn;
            this.pluginFieldDesc = pfd;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            // TODO: Implement transformations for entity scheduler calls.
            // Example: Redirect calls that use the global scheduler for entity tasks
            // to entity.getScheduler() or a FoliaPatcher equivalent.
            // The plugin instance can be injected using pluginFieldName and pluginFieldOwner.
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }
    }
}
