/*
 * Folia Phantom - Server#getWorlds Transformer
 *
 * Copyright (c) 2024 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.logging.Logger;

public class ServerGetWorldsTransformer implements ClassTransformer {
    private final Logger logger;
    private final String relocatedPatcherPath;

    public ServerGetWorldsTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor parent) {
        return new ServerGetWorldsClassVisitor(parent, logger, relocatedPatcherPath);
    }

    private static class ServerGetWorldsClassVisitor extends ClassVisitor {
        private final Logger logger;
        private final String relocatedPatcherPath;
        private String className;
        private boolean isPluginClass = false;

        public ServerGetWorldsClassVisitor(ClassVisitor cv, Logger logger, String relocatedPatcherPath) {
            super(Opcodes.ASM9, cv);
            this.logger = logger;
            this.relocatedPatcherPath = relocatedPatcherPath;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name;
            // Crude check, but effective for most cases. A better implementation
            // would involve checking the class hierarchy.
            if (superName != null && superName.equals("org/bukkit/plugin/java/JavaPlugin")) {
                isPluginClass = true;
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (isPluginClass) {
                return new ServerGetWorldsMethodVisitor(mv, logger, relocatedPatcherPath);
            }
            return mv;
        }
    }

    private static class ServerGetWorldsMethodVisitor extends MethodVisitor {
        private final Logger logger;
        private final String relocatedPatcherPath;

        public ServerGetWorldsMethodVisitor(MethodVisitor mv, Logger logger, String relocatedPatcherPath) {
            super(Opcodes.ASM9, mv);
            this.logger = logger;
            this.relocatedPatcherPath = relocatedPatcherPath;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKEINTERFACE &&
                owner.equals("org/bukkit/Server") &&
                name.equals("getWorlds") &&
                descriptor.equals("()Ljava/util/List;")) {

                logger.fine("Redirecting Server#getWorlds call to FoliaPatcher#safeGetWorlds");

                // Pop the server instance from the stack
                super.visitInsn(Opcodes.POP);

                // Load 'this' (the plugin instance) onto the stack
                super.visitVarInsn(Opcodes.ALOAD, 0);

                // Call the static FoliaPatcher method
                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                    relocatedPatcherPath + "/FoliaPatcher",
                    "safeGetWorlds",
                    "(Lorg/bukkit/plugin/Plugin;)Ljava/util/List;",
                    false);
            } else {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        }
    }
}
