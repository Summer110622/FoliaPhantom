/*
 * Folia Phantom - Player GetTargetBlock Transformer
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

public class PlayerGetTargetBlockTransformer implements ClassTransformer {

    private final Logger logger;
    private final String relocatedPatcherPath;
    private static final String PLAYER_OWNER = "org/bukkit/entity/Player";
    private static final String GET_TARGET_BLOCK_DESC = "(Ljava/util/Set;I)Lorg/bukkit/block/Block;";
    // This is the deprecated version, but some plugins might still use it.
    private static final String GET_TARGET_BLOCK_DESC_DEPRECATED = "(Ljava/util/HashSet;I)Lorg/bukkit/block/Block;";


    public PlayerGetTargetBlockTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor classVisitor) {
        return new PlayerGetTargetBlockClassVisitor(classVisitor);
    }

    private class PlayerGetTargetBlockClassVisitor extends ClassVisitor {
        private String className;
        private boolean isJavaPlugin;
        private String pluginField;

        public PlayerGetTargetBlockClassVisitor(ClassVisitor cv) {
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
                return new PlayerGetTargetBlockMethodVisitor(mv, access, name, descriptor);
            }
            return mv;
        }

        private class PlayerGetTargetBlockMethodVisitor extends AdviceAdapter {

            PlayerGetTargetBlockMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
                super(Opcodes.ASM9, mv, access, name, desc);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                if (opcode == Opcodes.INVOKEINTERFACE && PLAYER_OWNER.equals(owner) && "getTargetBlock".equals(name) && (GET_TARGET_BLOCK_DESC.equals(desc) || GET_TARGET_BLOCK_DESC_DEPRECATED.equals(desc)) ) {
                    redirectCall(desc);
                    logger.fine("[PlayerGetTargetBlockTransformer] Transformed " + owner + "#" + name + " in " + className);
                    return;
                }
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }

            private void redirectCall(String originalDesc) {
                // Store arguments and the player instance in local variables
                Type[] args = Type.getArgumentTypes(originalDesc);
                int[] locals = new int[args.length];
                for (int i = args.length - 1; i >= 0; i--) {
                    locals[i] = newLocal(args[i]);
                    storeLocal(locals[i]);
                }

                int playerLocal = newLocal(Type.getObjectType(PLAYER_OWNER));
                storeLocal(playerLocal);

                // Load the plugin instance
                loadPluginInstance();

                // Load the player instance
                loadLocal(playerLocal);

                // Load arguments
                for (int i = 0; i < args.length; i++) {
                    loadLocal(locals[i]);
                }

                // Call the static FoliaPatcher method
                String patcherDesc = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Player;" + originalDesc.substring(1);
                super.visitMethodInsn(INVOKESTATIC, relocatedPatcherPath + "/FoliaPatcher", "safeGetTargetBlock", patcherDesc, false);
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
