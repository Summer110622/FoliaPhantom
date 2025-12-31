/*
 * Folia Phantom - Entity Scheduler Transformer
 * 
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.*;
import java.util.logging.Logger;

/**
 * Transforms scheduler calls on Entity instances.
 * 
 * <p>
 * Modern Folia plugins should use Entity.getScheduler(), but many legacy
 * plugins still use global schedulers for entity tasks. This transformer
 * can be expanded to catch those patterns.
 * </p>
 */
public class EntitySchedulerTransformer implements ClassTransformer {
    private final Logger logger;
    private final String relocatedPatcherPath;

    public EntitySchedulerTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new EntitySchedulerVisitor(next, relocatedPatcherPath);
    }

    private static class EntitySchedulerVisitor extends ClassVisitor {
        private final String patcherPath;
        private String ownerName;
        private String pluginField = null;
        private boolean isPluginClass = false;

        public EntitySchedulerVisitor(ClassVisitor cv, String patcherPath) {
            super(Opcodes.ASM9, cv);
            this.patcherPath = patcherPath;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.ownerName = name;
            if (superName != null && superName.equals("org/bukkit/plugin/java/JavaPlugin")) {
                isPluginClass = true;
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (descriptor != null && descriptor.equals("Lorg/bukkit/plugin/Plugin;")) {
                pluginField = name;
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            return new EntitySchedulerMethodVisitor(super.visitMethod(access, name, desc, sig, ex), patcherPath, ownerName, pluginField, isPluginClass);
        }
    }

    private static class EntitySchedulerMethodVisitor extends MethodVisitor {
        private final String patcherPath;
        private final String ownerName;
        private final String pluginField;
        private final boolean isPluginClass;

        public EntitySchedulerMethodVisitor(MethodVisitor mv, String patcherPath, String ownerName, String pluginField, boolean isPluginClass) {
            super(Opcodes.ASM9, mv);
            this.patcherPath = patcherPath;
            this.ownerName = ownerName;
            this.pluginField = pluginField;
            this.isPluginClass = isPluginClass;
        }

        private void loadPluginInstance() {
            if (isPluginClass) {
                super.visitVarInsn(Opcodes.ALOAD, 0); // this
            } else if (pluginField != null) {
                super.visitVarInsn(Opcodes.ALOAD, 0);
                super.visitFieldInsn(Opcodes.GETFIELD, ownerName, pluginField, "Lorg/bukkit/plugin/Plugin;");
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if ("org/bukkit/entity/Entity".equals(owner) && "teleport".equals(name) && "(Lorg/bukkit/Location;)Z".equals(desc)) {
                loadPluginInstance();
                super.visitMethodInsn(Opcodes.INVOKESTATIC, patcherPath, "safeTeleport", "(Lorg/bukkit/entity/Entity;Lorg/bukkit/Location;Lorg/bukkit/plugin/Plugin;)Z", false);
                return;
            }
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }
    }
}
