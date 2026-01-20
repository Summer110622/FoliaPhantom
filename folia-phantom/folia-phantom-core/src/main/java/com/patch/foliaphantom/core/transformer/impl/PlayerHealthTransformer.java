/*
 * Folia Phantom - Player Health Transformer
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

public class PlayerHealthTransformer implements ClassTransformer {

    private final Logger logger;
    private final String relocatedPatcherPath;
    private static final String PLAYER_OWNER = "org/bukkit/entity/Player";

    public PlayerHealthTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor classVisitor) {
        return new PlayerHealthClassVisitor(classVisitor);
    }

    private class PlayerHealthClassVisitor extends ClassVisitor {
        private String className;
        private boolean isJavaPlugin;
        private String pluginField;

        public PlayerHealthClassVisitor(ClassVisitor cv) {
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
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (isJavaPlugin || pluginField != null) {
                return new PlayerHealthMethodVisitor(mv, access, name, descriptor);
            }
            return mv;
        }

        private class PlayerHealthMethodVisitor extends AdviceAdapter {

            PlayerHealthMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
                super(Opcodes.ASM9, mv, access, name, desc);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                if (opcode == Opcodes.INVOKEINTERFACE && PLAYER_OWNER.equals(owner) && "getHealth".equals(name) && "()D".equals(desc)) {
                    redirectCall();
                    logger.fine("[PlayerHealthTransformer] Transformed " + owner + "#" + name + " in " + className);
                    return;
                }
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }

            private void redirectCall() {
                // The Player instance is on the stack. We need to store it, load the plugin, then reload the player.
                int playerLocal = newLocal(Type.getObjectType(PLAYER_OWNER));
                storeLocal(playerLocal);

                // Load the plugin instance
                loadPluginInstance();

                // Load the player instance
                loadLocal(playerLocal);

                // Call the static FoliaPatcher method
                String patcherDesc = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Player;)D";
                super.visitMethodInsn(INVOKESTATIC, relocatedPatcherPath + "/FoliaPatcher", "safeGetHealth", patcherDesc, false);
            }

            private void loadPluginInstance() {
                if (isJavaPlugin) {
                    visitVarInsn(ALOAD, 0); // this
                } else if (pluginField != null) {
                    visitVarInsn(ALOAD, 0); // this
                    visitFieldInsn(GETFIELD, className, pluginField, "Lorg/bukkit/plugin/Plugin;");
                } else {
                    throw new IllegalStateException("Could not find a Plugin instance in " + className);
                }
            }
        }
    }
}
