/*
 * Folia Phantom - Entity Transformer
 *
 * Copyright (c) 2024 Marv
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

/**
 * Transforms thread-unsafe Bukkit Entity API calls into their thread-safe FoliaPatcher equivalents.
 */
public class EntityTransformer implements ClassTransformer {

    private final Logger logger;
    private final String relocatedPatcherPath;

    public EntityTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor parent) {
        return new EntityClassVisitor(parent, relocatedPatcherPath, logger);
    }

    private static class EntityClassVisitor extends ClassVisitor {
        private final String relocatedPatcherPath;
        private final Logger logger;
        private String className;
        private String pluginField;
        private String pluginDesc;

        public EntityClassVisitor(ClassVisitor cv, String relocatedPatcherPath, Logger logger) {
            super(Opcodes.ASM9, cv);
            this.relocatedPatcherPath = relocatedPatcherPath;
            this.logger = logger;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name;
            if (superName != null && superName.equals("org/bukkit/plugin/java/JavaPlugin")) {
                pluginField = "this";
                pluginDesc = "L" + className + ";";
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public org.objectweb.asm.FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (descriptor.equals("Lorg/bukkit/plugin/Plugin;") || descriptor.equals("Lorg/bukkit/plugin/java/JavaPlugin;")) {
                pluginField = name;
                pluginDesc = descriptor;
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (pluginField == null) {
                return mv;
            }
            return new EntityAdviceAdapter(Opcodes.ASM9, mv, access, name, descriptor, relocatedPatcherPath, className, pluginField, pluginDesc, logger);
        }
    }

    private static class EntityAdviceAdapter extends AdviceAdapter {
        private final String patcherPath;
        private final String className;
        private final String pluginField;
        private final String pluginDesc;
        private final Logger logger;
        private final boolean isStatic;

        protected EntityAdviceAdapter(int api, MethodVisitor mv, int access, String name, String descriptor, String patcherPath, String className, String pluginField, String pluginDesc, Logger logger) {
            super(api, mv, access, name, descriptor);
            this.patcherPath = patcherPath;
            this.className = className;
            this.pluginField = pluginField;
            this.pluginDesc = pluginDesc;
            this.logger = logger;
            this.isStatic = (access & Opcodes.ACC_STATIC) != 0;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (!isStatic && opcode == INVOKEINTERFACE && owner.equals("org/bukkit/entity/Entity")) {
                String targetName = null;
                String targetDesc = null;

                switch (name) {
                    case "remove":
                        if (descriptor.equals("()V")) {
                            targetName = "safeRemove";
                            targetDesc = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Entity;)V";
                        }
                        break;
                    case "setVelocity":
                        if (descriptor.equals("(Lorg/bukkit/util/Vector;)V")) {
                            targetName = "safeSetVelocity";
                            targetDesc = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Entity;Lorg/bukkit/util/Vector;)V";
                        }
                        break;
                    case "teleport":
                        if (descriptor.equals("(Lorg/bukkit/Location;)Z")) {
                            targetName = "safeTeleportEntity";
                            targetDesc = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Entity;Lorg/bukkit/Location;)Z";
                        }
                        break;
                    case "setFireTicks":
                        if (descriptor.equals("(I)V")) {
                            targetName = "safeSetFireTicks";
                            targetDesc = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Entity;I)V";
                        }
                        break;
                    case "setCustomName":
                        if (descriptor.equals("(Ljava/lang/String;)V")) {
                            targetName = "safeSetCustomName";
                            targetDesc = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Entity;Ljava/lang/String;)V";
                        }
                        break;
                    case "setGravity":
                        if (descriptor.equals("(Z)V")) {
                            targetName = "safeSetGravity";
                            targetDesc = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Entity;Z)V";
                        }
                        break;
                }

                if (targetName != null) {
                    logger.fine("[EntityTransformer] Redirecting " + owner + "#" + name + " in " + className);

                    Type[] argTypes = Type.getArgumentTypes(descriptor);
                    int[] locals = new int[argTypes.length];
                    for (int i = argTypes.length - 1; i >= 0; i--) {
                        locals[i] = newLocal(argTypes[i]);
                        storeLocal(locals[i]);
                    }

                    int entityLocal = newLocal(Type.getObjectType(owner));
                    storeLocal(entityLocal);

                    // Load plugin instance
                    if (pluginField.equals("this")) {
                        loadThis();
                    } else {
                        loadThis();
                        getField(Type.getObjectType(className), pluginField, Type.getType(pluginDesc));
                    }

                    // Load entity instance
                    loadLocal(entityLocal);

                    // Load arguments
                    for (int local : locals) {
                        loadLocal(local);
                    }

                    super.visitMethodInsn(INVOKESTATIC, patcherPath + "/FoliaPatcher", targetName, targetDesc, false);
                    return;
                }
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }
    }
}
