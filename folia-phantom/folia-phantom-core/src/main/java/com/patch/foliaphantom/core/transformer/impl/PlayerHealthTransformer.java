/*
 * Folia Phantom - Player Health Transformer
 *
 * Copyright (c) 2026 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import java.util.logging.Logger;

/**
 * Transforms Player.getHealth() and Player.setHealth() calls to use the asynchronous, thread-safe
 * FoliaPatcher implementation.
 */
public class PlayerHealthTransformer implements ClassTransformer {
    private final Logger logger;
    private final String relocatedPatcherPath;
    private static final String PATCHER_CLASS = "FoliaPatcher";
    private static final String PLUGIN_DESC = "Lorg/bukkit/plugin/Plugin;";
    private static final String JAVA_PLUGIN_DESC = "Lorg/bukkit/plugin/java/JavaPlugin;";

    public PlayerHealthTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new PlayerHealthClassVisitor(next, relocatedPatcherPath, logger);
    }

    private static class PlayerHealthClassVisitor extends ClassVisitor {
        private String className;
        private String pluginFieldName;
        private String pluginFieldDesc;
        private final String patcherPath;
        private final Logger logger;

        public PlayerHealthClassVisitor(ClassVisitor cv, String patcherPath, Logger logger) {
            super(Opcodes.ASM9, cv);
            this.patcherPath = patcherPath;
            this.logger = logger;
        }

        @Override
        public void visit(int version, int access, String name, String sig, String superName, String[] interfaces) {
            this.className = name;
            super.visit(version, access, name, sig, superName, interfaces);
        }



        @Override
        public FieldVisitor visitField(int access, String name, String desc, String sig, Object val) {
            if (pluginFieldName == null && (desc.equals(PLUGIN_DESC) || desc.equals(JAVA_PLUGIN_DESC))) {
                this.pluginFieldName = name;
                this.pluginFieldDesc = desc;
            }
            return super.visitField(access, name, desc, sig, val);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            if (pluginFieldName == null) {
                return mv;
            }
            return new PlayerHealthMethodVisitor(mv, access, name, desc, patcherPath, className, pluginFieldName, pluginFieldDesc);
        }
    }

    private static class PlayerHealthMethodVisitor extends AdviceAdapter {
        private final String patcherPath;
        private final String pluginFieldOwner;
        private final String pluginFieldName;
        private final String pluginFieldDesc;

        protected PlayerHealthMethodVisitor(MethodVisitor mv, int access, String name, String desc,
                                              String patcherPath, String owner, String pfn, String pfd) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherPath = patcherPath;
            this.pluginFieldOwner = owner;
            this.pluginFieldName = pfn;
            this.pluginFieldDesc = pfd;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if ("org/bukkit/entity/Player".equals(owner)) {
                String patcherOwner = patcherPath + "/" + PATCHER_CLASS;
                if ("getHealth".equals(name) && "()D".equals(desc)) {
                    // Stack: [player]
                    int playerLocal = newLocal(Type.getType("Lorg/bukkit/entity/Player;"));
                    storeLocal(playerLocal);

                    // Stack: []
                    loadThis();
                    getField(Type.getObjectType(pluginFieldOwner), pluginFieldName, Type.getType(pluginFieldDesc));
                    loadLocal(playerLocal);

                    // Stack: [plugin, player]
                    String newDesc = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Player;)D";
                    super.visitMethodInsn(INVOKESTATIC, patcherOwner, "safeGetHealth", newDesc, false);
                    return;
                }

                if ("setHealth".equals(name) && "(D)V".equals(desc)) {
                    // Stack: [player, health]
                    int healthLocal = newLocal(Type.DOUBLE_TYPE);
                    storeLocal(healthLocal);
                    int playerLocal = newLocal(Type.getType("Lorg/bukkit/entity/Player;"));
                    storeLocal(playerLocal);

                    // Stack: []
                    loadThis();
                    getField(Type.getObjectType(pluginFieldOwner), pluginFieldName, Type.getType(pluginFieldDesc));
                    loadLocal(playerLocal);
                    loadLocal(healthLocal);

                    // Stack: [plugin, player, health]
                    String newDesc = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Player;D)V";
                    super.visitMethodInsn(INVOKESTATIC, patcherOwner, "safeSetHealth", newDesc, false);
                    return;
                }
            }
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }
    }
}
