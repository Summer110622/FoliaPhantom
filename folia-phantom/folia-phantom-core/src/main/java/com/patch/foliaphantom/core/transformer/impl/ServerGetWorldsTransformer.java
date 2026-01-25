/*
 * Folia Phantom - Server#getWorlds() Transformer
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
 * Transforms {@code Server#getWorlds()} calls to a thread-safe equivalent
 * in {@code FoliaPatcher}. This ensures that plugins accessing the world list
 * from asynchronous threads do not cause concurrency issues.
 */
public class ServerGetWorldsTransformer implements ClassTransformer {

    private final Logger logger;
    private final String relocatedPatcherPath;

    public ServerGetWorldsTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor classVisitor) {
        return new ServerGetWorldsClassVisitor(classVisitor);
    }

    private class ServerGetWorldsClassVisitor extends ClassVisitor {
        private String className;
        private boolean isJavaPlugin;
        private String pluginField;
        private String pluginFieldType;

        public ServerGetWorldsClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name;
            if ("org/bukkit/plugin/java/JavaPlugin".equals(superName)) {
                this.isJavaPlugin = true;
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public org.objectweb.asm.FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (descriptor.equals("Lorg/bukkit/plugin/Plugin;") || descriptor.equals("Lorg/bukkit/plugin/java/JavaPlugin;")) {
                if (this.pluginField == null) {
                    this.pluginField = name;
                    this.pluginFieldType = descriptor;
                }
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (isJavaPlugin || pluginField != null) {
                return new GetWorldsMethodVisitor(mv, access, name, descriptor);
            }
            return mv;
        }

        private class GetWorldsMethodVisitor extends AdviceAdapter {
            GetWorldsMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
                super(Opcodes.ASM9, mv, access, name, desc);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                if ("getWorlds".equals(name) && "()Ljava/util/List;".equals(desc)) {
                    boolean isServerInterfaceCall = opcode == Opcodes.INVOKEINTERFACE && "org/bukkit/Server".equals(owner);
                    boolean isBukkitStaticCall = opcode == Opcodes.INVOKESTATIC && "org/bukkit/Bukkit".equals(owner);

                    if (isServerInterfaceCall || isBukkitStaticCall) {
                        if (isServerInterfaceCall) {
                            logger.fine("[FoliaPhantom] Transforming Server#getWorlds() call in " + className);
                            pop(); // Pop the Server instance for the interface call
                        } else {
                            logger.fine("[FoliaPhantom] Transforming Bukkit.getWorlds() call in " + className);
                            // No instance to pop for a static call
                        }

                        loadPluginInstance(); // Push the Plugin instance
                        super.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            relocatedPatcherPath + "/FoliaPatcher",
                            "safeGetWorlds",
                            "(Lorg/bukkit/plugin/Plugin;)Ljava/util/List;",
                            false
                        );
                        return; // Avoid calling super with the original instruction
                    }
                }
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }

            private void loadPluginInstance() {
                if (isJavaPlugin) {
                    visitVarInsn(ALOAD, 0); // this
                } else if (pluginField != null) {
                    visitVarInsn(ALOAD, 0); // this
                    visitFieldInsn(GETFIELD, className, pluginField, pluginFieldType);
                } else {
                    throw new IllegalStateException("Cannot transform getWorlds call in " + className + ": No plugin instance found.");
                }
            }
        }
    }
}
