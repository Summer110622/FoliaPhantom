/*
 * Folia Phantom - Player API Transformer
 *
 * Copyright (c) 2026 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class PlayerTransformer implements ClassTransformer {

    private final Logger logger;
    private final String relocatedPatcherPath;
    private static final String PLAYER_OWNER = "org/bukkit/entity/Player";
    private static final Map<String, MethodMapping> METHOD_MAPPINGS = new HashMap<>();

    static {
        // Player methods
        addMapping("sendMessage", "(Ljava/lang/String;)V", "safeSendMessage", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Player;Ljava/lang/String;)V");
        addMapping("sendMessage", "([Ljava/lang/String;)V", "safeSendMessages", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Player;[Ljava/lang/String;)V");
        addMapping("kickPlayer", "(Ljava/lang/String;)V", "safeKickPlayer", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Player;Ljava/lang/String;)V");
        addMapping("setHealth", "(D)V", "safeSetHealth", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Player;D)V");
        addMapping("setFoodLevel", "(I)V", "safeSetFoodLevel", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Player;I)V");
        addMapping("giveExp", "(I)V", "safeGiveExp", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Player;I)V");
        addMapping("setLevel", "(I)V", "safeSetLevel", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Player;I)V");
        addMapping("playSound", "(Lorg/bukkit/Location;Lorg/bukkit/Sound;FF)V", "safePlaySound", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Player;Lorg/bukkit/Location;Lorg/bukkit/Sound;FF)V");
        addMapping("sendTitle", "(Ljava/lang/String;Ljava/lang/String;III)V", "safeSendTitle", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Player;Ljava/lang/String;Ljava/lang/String;III)V");
        addMapping("openInventory", "(Lorg/bukkit/inventory/Inventory;)Lorg/bukkit/inventory/InventoryView;", "safeOpenInventory", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Player;Lorg/bukkit/inventory/Inventory;)Lorg/bukkit/inventory/InventoryView;");
        addMapping("closeInventory", "()V", "safeCloseInventory", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Player;)V");
    }

    public PlayerTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    private static void addMapping(String originalName, String originalDesc, String newName, String newDesc) {
        METHOD_MAPPINGS.put(originalName + originalDesc, new MethodMapping(newName, newDesc));
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor classVisitor) {
        return new PlayerClassVisitor(classVisitor);
    }

    private class PlayerClassVisitor extends ClassVisitor {
        private String className;
        private boolean isJavaPlugin;
        private String pluginField;

        public PlayerClassVisitor(ClassVisitor cv) {
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
                return new PlayerMethodVisitor(mv, access, name, descriptor);
            }
            return mv;
        }

        private class PlayerMethodVisitor extends AdviceAdapter {
            private boolean transformed = false;

            PlayerMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
                super(Opcodes.ASM9, mv, access, name, desc);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                if (opcode == Opcodes.INVOKEINTERFACE && PLAYER_OWNER.equals(owner)) {
                    MethodMapping mapping = METHOD_MAPPINGS.get(name + desc);
                    if (mapping != null) {
                        redirectCall(owner, mapping);
                        transformed = true;
                        logger.fine("[PlayerTransformer] Transformed " + owner + "#" + name + " in " + className);
                        return;
                    }
                }
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }

            private void redirectCall(String owner, MethodMapping mapping) {
                // Store arguments and the player instance in local variables
                Type[] args = Type.getArgumentTypes(mapping.originalDesc);
                int[] locals = new int[args.length];
                for (int i = args.length - 1; i >= 0; i--) {
                    locals[i] = newLocal(args[i]);
                    storeLocal(locals[i]);
                }

                int playerLocal = newLocal(Type.getObjectType(owner));
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
                super.visitMethodInsn(INVOKESTATIC, relocatedPatcherPath + "/FoliaPatcher", mapping.newName, mapping.newDesc, false);
            }

            private void loadPluginInstance() {
                if (isJavaPlugin) {
                    visitVarInsn(ALOAD, 0); // this
                } else if (pluginField != null) {
                    visitVarInsn(ALOAD, 0); // this
                    visitFieldInsn(GETFIELD, className, pluginField, "Lorg/bukkit/plugin/Plugin;");
                } else {
                    // This should not happen due to the check in visitMethod
                    throw new IllegalStateException("Could not find a Plugin instance in " + className);
                }
            }
        }
    }

    private static class MethodMapping {
        final String newName;
        final String newDesc;
        final String originalDesc;

        MethodMapping(String newName, String newDesc) {
            this.newName = newName;
            this.newDesc = newDesc;
            this.originalDesc = newDesc.replace("(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Player;", "(");
        }
    }
}
