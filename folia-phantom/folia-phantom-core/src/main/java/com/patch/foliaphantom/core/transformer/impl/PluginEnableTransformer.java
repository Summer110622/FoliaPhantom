/*
 * Folia Phantom - Plugin Enable Transformer
 *
 * Copyright (c) 2025 Marv
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
 * Transforms JavaPlugin#onEnable to initialize the FoliaPatcher runtime.
 * This is the core of the HPAM system, ensuring that the patcher has a
 * valid plugin instance for its background tasks.
 */
public class PluginEnableTransformer implements ClassTransformer {
    private final Logger logger;
    private final String relocatedPatcherPath;

    public PluginEnableTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor cv) {
        return new PluginEnableClassVisitor(cv, relocatedPatcherPath);
    }

    private static class PluginEnableClassVisitor extends ClassVisitor {
        private final String patcherPath;
        private boolean isJavaPlugin;

        public PluginEnableClassVisitor(ClassVisitor cv, String patcherPath) {
            super(Opcodes.ASM9, cv);
            this.patcherPath = patcherPath + "/FoliaPatcher";
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            if ("org/bukkit/plugin/java/JavaPlugin".equals(superName)) {
                this.isJavaPlugin = true;
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (isJavaPlugin && "onEnable".equals(name) && "()V".equals(descriptor)) {
                return new PluginEnableMethodVisitor(mv, access, name, descriptor, patcherPath);
            }
            return mv;
        }
    }

    private static class PluginEnableMethodVisitor extends AdviceAdapter {
        private final String patcherPath;

        protected PluginEnableMethodVisitor(MethodVisitor mv, int access, String name, String desc, String patcherPath) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherPath = patcherPath;
        }

        @Override
        protected void onMethodEnter() {
            loadThis();
            super.visitMethodInsn(INVOKESTATIC, patcherPath, "_i", "(Lorg/bukkit/plugin/Plugin;)V", false);
        }
    }
}
