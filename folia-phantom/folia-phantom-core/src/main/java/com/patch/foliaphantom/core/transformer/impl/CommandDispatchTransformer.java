/*
 * Folia Phantom - Command Dispatch Transformer
 *
 * Copyright (c) 2025 Marv
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
 * Transforms Bukkit.dispatchCommand and Server.dispatchCommand calls to a thread-safe equivalent.
 */
public class CommandDispatchTransformer implements ClassTransformer {
    private final Logger logger;
    private final String relocatedPatcherPath;

    public CommandDispatchTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new CommandDispatchVisitor(next);
    }

    private class CommandDispatchVisitor extends ClassVisitor {
        private String className;
        private boolean isJavaPlugin;
        private String pluginField;
        private String pluginFieldDesc;

        public CommandDispatchVisitor(ClassVisitor cv) {
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
                if (pluginField == null) {
                    this.pluginField = name;
                    this.pluginFieldDesc = descriptor;
                }
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
            if (!isStatic && (isJavaPlugin || pluginField != null)) {
                return new CommandDispatchMethodVisitor(mv, access, name, descriptor);
            }
            return mv;
        }

        private class CommandDispatchMethodVisitor extends AdviceAdapter {
            protected CommandDispatchMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
                super(Opcodes.ASM9, mv, access, name, desc);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                if ("dispatchCommand".equals(name) && "(Lorg/bukkit/command/CommandSender;Ljava/lang/String;)Z".equals(desc)) {
                    if (opcode == Opcodes.INVOKESTATIC && "org/bukkit/Bukkit".equals(owner)) {
                        logger.fine("[CommandDispatchTransformer] Transforming Bukkit.dispatchCommand in " + className);

                        int commandLocal = newLocal(Type.getType(String.class));
                        storeLocal(commandLocal);
                        int senderLocal = newLocal(Type.getObjectType("org/bukkit/command/CommandSender"));
                        storeLocal(senderLocal);

                        loadPluginInstance();
                        loadLocal(senderLocal);
                        loadLocal(commandLocal);

                        super.visitMethodInsn(Opcodes.INVOKESTATIC, relocatedPatcherPath + "/FoliaPatcher", "safeDispatchCommand", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/command/CommandSender;Ljava/lang/String;)Z", false);
                        return;
                    } else if (opcode == Opcodes.INVOKEINTERFACE && "org/bukkit/Server".equals(owner)) {
                        logger.fine("[CommandDispatchTransformer] Transforming Server.dispatchCommand in " + className);

                        int commandLocal = newLocal(Type.getType(String.class));
                        storeLocal(commandLocal);
                        int senderLocal = newLocal(Type.getObjectType("org/bukkit/command/CommandSender"));
                        storeLocal(senderLocal);
                        pop(); // Pop Server instance

                        loadPluginInstance();
                        loadLocal(senderLocal);
                        loadLocal(commandLocal);

                        super.visitMethodInsn(Opcodes.INVOKESTATIC, relocatedPatcherPath + "/FoliaPatcher", "safeDispatchCommand", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/command/CommandSender;Ljava/lang/String;)Z", false);
                        return;
                    }
                }
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }

            private void loadPluginInstance() {
                if (isJavaPlugin) {
                    loadThis();
                } else {
                    loadThis();
                    getField(Type.getObjectType(className), pluginField, Type.getType(pluginFieldDesc));
                }
            }
        }
    }
}
