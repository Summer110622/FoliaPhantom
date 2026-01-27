/*
 * Folia Phantom - Player#getStatistic() Transformer
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

public class PlayerStatisticTransformer implements ClassTransformer {

    private final Logger logger;
    private final String relocatedPatcherPath;

    public PlayerStatisticTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor classVisitor) {
        return new PlayerStatisticClassVisitor(classVisitor);
    }

    private class PlayerStatisticClassVisitor extends ClassVisitor {
        private String className;
        private boolean isJavaPlugin;
        private String pluginField;
        private String pluginFieldType;

        public PlayerStatisticClassVisitor(ClassVisitor cv) {
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
                return new GetStatisticMethodVisitor(mv, access, name, descriptor);
            }
            return mv;
        }

        private class GetStatisticMethodVisitor extends AdviceAdapter {
            GetStatisticMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
                super(Opcodes.ASM9, mv, access, name, desc);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                if (opcode == Opcodes.INVOKEINTERFACE &&
                    "org/bukkit/entity/Player".equals(owner) &&
                    "getStatistic".equals(name) &&
                    (desc.equals("(Lorg/bukkit/Statistic;)I") ||
                     desc.equals("(Lorg/bukkit/Statistic;Lorg/bukkit/Material;)I") ||
                     desc.equals("(Lorg/bukkit/Statistic;Lorg/bukkit/entity/EntityType;)I"))) {

                    logger.fine("[FoliaPhantom] Transforming Player#getStatistic call in " + className);

                    Type[] args = Type.getArgumentTypes(desc);
                    int[] locals = new int[args.length];
                    for (int i = args.length - 1; i >= 0; i--) {
                        locals[i] = newLocal(args[i]);
                        storeLocal(locals[i]);
                    }

                    // Pop the player object
                    int playerLocal = newLocal(Type.getObjectType(owner));
                    storeLocal(playerLocal);

                    loadPluginInstance();
                    loadLocal(playerLocal);

                    for (int local : locals) {
                        loadLocal(local);
                    }

                    String newDesc = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Player;" + desc.substring(1);
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        relocatedPatcherPath + "/FoliaPatcher",
                        "safeGetStatistic",
                        newDesc,
                        false
                    );
                } else {
                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                }
            }

            private void loadPluginInstance() {
                if (isJavaPlugin) {
                    visitVarInsn(ALOAD, 0); // this
                } else if (pluginField != null) {
                    visitVarInsn(ALOAD, 0); // this
                    visitFieldInsn(GETFIELD, className, pluginField, pluginFieldType);
                } else {
                    throw new IllegalStateException("Cannot transform getStatistic call in " + className + ": No plugin instance found.");
                }
            }
        }
    }
}
