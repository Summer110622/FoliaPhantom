/*
 * Folia Phantom - Offline Player Transformer
 *
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import java.util.logging.Logger;

/**
 * Transforms OfflinePlayer lookup calls to be Folia-compatible.
 *
 * <p>Redirects Bukkit.getOfflinePlayer and Server.getOfflinePlayer to
 * FoliaPatcher._b_gop, which ensures the blocking lookup is performed
 * off the main thread or on the global region scheduler and blocks for the result.</p>
 */
public class OfflinePlayerTransformer implements ClassTransformer {
    private final String relocatedPatcherPath;

    public OfflinePlayerTransformer(Logger logger, String relocatedPatcherPath) {
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new OfflinePlayerVisitor(next, relocatedPatcherPath + "/FoliaPatcher");
    }

    private static class OfflinePlayerVisitor extends ClassVisitor {
        private final String patcherPath;
        private String className;
        private String pluginFieldName;
        private String pluginFieldDesc;
        private boolean isJavaPlugin;

        public OfflinePlayerVisitor(ClassVisitor cv, String patcherPath) {
            super(Opcodes.ASM9, cv);
            this.patcherPath = patcherPath;
        }

        @Override
        public void visit(int version, int access, String name, String sig, String superName, String[] interfaces) {
            this.className = name;
            this.isJavaPlugin = "org/bukkit/plugin/java/JavaPlugin".equals(superName);
            super.visit(version, access, name, sig, superName, interfaces);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String sig, Object val) {
            if (pluginFieldName == null && (desc.equals("Lorg/bukkit/plugin/Plugin;") || desc.equals("Lorg/bukkit/plugin/java/JavaPlugin;"))) {
                this.pluginFieldName = name;
                this.pluginFieldDesc = desc;
            }
            return super.visitField(access, name, desc, sig, val);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
            if ((isJavaPlugin || pluginFieldName != null) && !isStatic) {
                return new OfflinePlayerMethodVisitor(mv, access, name, desc, patcherPath, className, pluginFieldName, pluginFieldDesc, isJavaPlugin);
            }
            return mv;
        }
    }

    private static class OfflinePlayerMethodVisitor extends AdviceAdapter {
        private final String patcherPath;
        private final String classOwner;
        private final String pluginFieldName;
        private final Type pluginFieldType;
        private final boolean isJavaPlugin;

        protected OfflinePlayerMethodVisitor(MethodVisitor mv, int access, String name, String desc, String patcherPath, String owner, String pfn, String pfd, boolean isJavaPlugin) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherPath = patcherPath;
            this.classOwner = owner;
            this.pluginFieldName = pfn;
            this.pluginFieldType = pfd != null ? Type.getType(pfd) : null;
            this.isJavaPlugin = isJavaPlugin;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if ("getOfflinePlayer".equals(name)) {
                if ("(Ljava/lang/String;)Lorg/bukkit/OfflinePlayer;".equals(desc)) {
                    if ("org/bukkit/Bukkit".equals(owner) && opcode == INVOKESTATIC) {
                        redirect("(Lorg/bukkit/plugin/Plugin;Ljava/lang/String;)Lorg/bukkit/OfflinePlayer;", false);
                        return;
                    } else if ("org/bukkit/Server".equals(owner) && opcode == INVOKEINTERFACE) {
                        redirect("(Lorg/bukkit/plugin/Plugin;Ljava/lang/String;)Lorg/bukkit/OfflinePlayer;", true);
                        return;
                    }
                } else if ("(Ljava/util/UUID;)Lorg/bukkit/OfflinePlayer;".equals(desc)) {
                    if ("org/bukkit/Bukkit".equals(owner) && opcode == INVOKESTATIC) {
                        redirect("(Lorg/bukkit/plugin/Plugin;Ljava/util/UUID;)Lorg/bukkit/OfflinePlayer;", false);
                        return;
                    } else if ("org/bukkit/Server".equals(owner) && opcode == INVOKEINTERFACE) {
                        redirect("(Lorg/bukkit/plugin/Plugin;Ljava/util/UUID;)Lorg/bukkit/OfflinePlayer;", true);
                        return;
                    }
                }
            }
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }

        private void redirect(String newDesc, boolean isInstance) {
            Type argType = Type.getArgumentTypes(newDesc)[1];
            int argLocal = newLocal(argType);
            storeLocal(argLocal);
            if (isInstance) pop(); // Pop Server instance

            if (isJavaPlugin) {
                loadThis();
            } else {
                loadThis();
                getField(Type.getObjectType(classOwner), pluginFieldName, pluginFieldType);
            }
            loadLocal(argLocal);
            super.visitMethodInsn(INVOKESTATIC, patcherPath, "_b_gop", newDesc, false);
        }
    }
}
