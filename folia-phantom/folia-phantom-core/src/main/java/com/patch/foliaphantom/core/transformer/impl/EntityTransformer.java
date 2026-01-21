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
import org.objectweb.asm.commons.AdviceAdapter;

import java.util.logging.Logger;

/**
 * Transforms thread-unsafe {@code Entity} calls to their thread-safe
 * equivalents in {@code FoliaPatcher}. This transformer targets:
 * <ul>
 *     <li>{@code Entity#setVelocity(Vector)}</li>
 *     <li>{@code Entity#teleport(Location)}</li>
 * </ul>
 */
public class EntityTransformer implements ClassTransformer {

    private final Logger logger;
    private final String relocatedPatcherPath;

    public EntityTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor classVisitor) {
        return new EntityClassVisitor(classVisitor);
    }

    private class EntityClassVisitor extends ClassVisitor {
        private String className;
        private boolean isJavaPlugin;
        private String pluginField;
        private String pluginFieldType;

        public EntityClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name;
            this.isJavaPlugin = "org/bukkit/plugin/java/JavaPlugin".equals(superName);
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public org.objectweb.asm.FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (descriptor.equals("Lorg/bukkit/plugin/Plugin;") || descriptor.equals("Lorg/bukkit/plugin/java/JavaPlugin;")) {
                this.pluginField = name;
                this.pluginFieldType = descriptor;
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (isJavaPlugin || pluginField != null) {
                return new EntityMethodVisitor(mv, access, name, descriptor);
            }
            return mv;
        }

        private class EntityMethodVisitor extends AdviceAdapter {
            EntityMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
                super(Opcodes.ASM9, mv, access, name, desc);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                if (opcode == Opcodes.INVOKEINTERFACE && "org/bukkit/entity/Entity".equals(owner)) {
                    if ("setVelocity".equals(name) && "(Lorg/bukkit/util/Vector;)V".equals(desc)) {
                        transformSetVelocity();
                        return;
                    }
                    if ("teleport".equals(name) && "(Lorg/bukkit/Location;)Z".equals(desc)) {
                        transformTeleport();
                        return;
                    }
                }
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }

            private void transformSetVelocity() {
                logger.fine("[EntityTransformer] Transforming setVelocity(Vector) call in " + className);

                // Stack before: [..., entity, vector]
                // Stack desired: [..., plugin, entity, vector]

                loadPluginInstance(); // Stack is now [..., entity, vector, plugin]
                super.visitInsn(Opcodes.DUP_X2); // Stack is now [..., plugin, entity, vector, plugin]
                super.visitInsn(Opcodes.POP);    // Stack is now [..., plugin, entity, vector]

                super.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    relocatedPatcherPath + "/FoliaPatcher",
                    "safeSetVelocity",
                    "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Entity;Lorg/bukkit/util/Vector;)V",
                    false
                );
            }

            private void transformTeleport() {
                logger.fine("[EntityTransformer] Transforming teleport(Location) call in " + className);

                // Stack before: [..., entity, location]
                // Stack desired: [..., plugin, entity, location]

                loadPluginInstance(); // Stack is now [..., entity, location, plugin]
                super.visitInsn(Opcodes.DUP_X2); // Stack is now [..., plugin, entity, location, plugin]
                super.visitInsn(Opcodes.POP);    // Stack is now [..., plugin, entity, location]

                super.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    relocatedPatcherPath + "/FoliaPatcher",
                    "safeTeleportEntity",
                    "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Entity;Lorg/bukkit/Location;)Z",
                    false
                );
            }

            private void loadPluginInstance() {
                if (isJavaPlugin) {
                    visitVarInsn(ALOAD, 0); // this
                } else if (pluginField != null) {
                    visitVarInsn(ALOAD, 0); // this
                    visitFieldInsn(GETFIELD, className, pluginField, pluginFieldType);
                } else {
                    throw new IllegalStateException("Cannot transform Entity call in " + className + ": No plugin instance found.");
                }
            }
        }
    }
}
