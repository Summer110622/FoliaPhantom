/*
 * Folia Phantom - EntityTransformer
 *
 * Copyright (c) 2026 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

import java.util.logging.Logger;

public class EntityTransformer implements ClassTransformer {

    private static final String ENTITY_OWNER = "org/bukkit/entity/Entity";
    private static final String SET_VELOCITY_METHOD = "setVelocity";
    private static final String SET_VELOCITY_DESCRIPTOR = "(Lorg/bukkit/util/Vector;)V";
    private static final String TELEPORT_METHOD = "teleport";
    private static final String TELEPORT_DESCRIPTOR = "(Lorg/bukkit/Location;)Z";
    private static final String PLUGIN_DESCRIPTOR = "Lorg/bukkit/plugin/Plugin;";

    protected final Logger logger;
    protected final String relocatedPatcherPath;

    public EntityTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor parent) {
        return new EntityClassVisitor(parent, this.relocatedPatcherPath, this.logger);
    }

    private static class EntityClassVisitor extends ClassVisitor {
        private final String relocatedPatcherPath;
        private final Logger logger;
        private String className;
        private boolean isPluginClass = false;
        private int pluginFieldAccess = -1;
        private String pluginFieldName;

        public EntityClassVisitor(ClassVisitor cv, String relocatedPatcherPath, Logger logger) {
            super(Opcodes.ASM9, cv);
            this.relocatedPatcherPath = relocatedPatcherPath;
            this.logger = logger;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name;
            if (superName.equals("org/bukkit/plugin/java/JavaPlugin")) {
                isPluginClass = true;
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public org.objectweb.asm.FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (descriptor.equals(PLUGIN_DESCRIPTOR)) {
                this.pluginFieldAccess = access;
                this.pluginFieldName = name;
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new EntityMethodVisitor(mv, access, name, descriptor, relocatedPatcherPath, logger, this);
        }

        boolean isPluginClass() {
            return isPluginClass;
        }

        boolean hasPluginField() {
            return pluginFieldName != null;
        }

        void loadPluginInstance(MethodVisitor mv) {
            if (isPluginClass) {
                mv.visitVarInsn(Opcodes.ALOAD, 0); // this
            } else if (hasPluginField()) {
                if ((pluginFieldAccess & Opcodes.ACC_STATIC) != 0) {
                    mv.visitFieldInsn(Opcodes.GETSTATIC, className, pluginFieldName, PLUGIN_DESCRIPTOR);
                } else {
                    mv.visitVarInsn(Opcodes.ALOAD, 0); // this
                    mv.visitFieldInsn(Opcodes.GETFIELD, className, pluginFieldName, PLUGIN_DESCRIPTOR);
                }
            } else {
                mv.visitInsn(Opcodes.ACONST_NULL);
                logger.warning("Could not find Plugin instance in class " + className + ". Cannot patch Entity calls correctly. Please report this to the FoliaPhantom developers.");
            }
        }
    }

    private static class EntityMethodVisitor extends AdviceAdapter {
        private final String relocatedPatcherPath;
        private final Logger logger;
        private final EntityClassVisitor classVisitor;

        protected EntityMethodVisitor(MethodVisitor mv, int access, String name, String descriptor, String relocatedPatcherPath, Logger logger, EntityClassVisitor classVisitor) {
            super(Opcodes.ASM9, mv, access, name, descriptor);
            this.relocatedPatcherPath = relocatedPatcherPath;
            this.logger = logger;
            this.classVisitor = classVisitor;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKEINTERFACE && owner.equals(ENTITY_OWNER)) {
                if (name.equals(SET_VELOCITY_METHOD) && descriptor.equals(SET_VELOCITY_DESCRIPTOR)) {
                    logger.fine("Found call to Entity.setVelocity in " + classVisitor.className);

                    // Original stack: [..., entity, vector]
                    int vectorVar = newLocal(Type.getType("Lorg/bukkit/util/Vector;"));
                    int entityVar = newLocal(Type.getType("Lorg/bukkit/entity/Entity;"));

                    storeLocal(vectorVar); // vector -> local, stack: [..., entity]
                    storeLocal(entityVar); // entity -> local, stack: [...]

                    classVisitor.loadPluginInstance(mv); // stack: [..., plugin]
                    loadLocal(entityVar); // stack: [..., plugin, entity]
                    loadLocal(vectorVar); // stack: [..., plugin, entity, vector]

                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, relocatedPatcherPath + "/FoliaPatcher", "safeSetVelocity", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Entity;Lorg/bukkit/util/Vector;)V", false);
                    return;
                } else if (name.equals(TELEPORT_METHOD) && descriptor.equals(TELEPORT_DESCRIPTOR)) {
                    logger.fine("Found call to Entity.teleport in " + classVisitor.className);

                    // Original stack: [..., entity, location]
                    int locationVar = newLocal(Type.getType("Lorg/bukkit/Location;"));
                    int entityVar = newLocal(Type.getType("Lorg/bukkit/entity/Entity;"));

                    storeLocal(locationVar); // location -> local, stack: [..., entity]
                    storeLocal(entityVar);   // entity -> local, stack: [...]

                    classVisitor.loadPluginInstance(mv); // stack: [..., plugin]
                    loadLocal(entityVar);   // stack: [..., plugin, entity]
                    loadLocal(locationVar); // stack: [..., plugin, entity, location]

                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, relocatedPatcherPath + "/FoliaPatcher", "safeTeleportEntity", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Entity;Lorg/bukkit/Location;)Z", false);
                    return;
                }
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }
    }
}
