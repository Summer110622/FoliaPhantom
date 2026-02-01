/*
 * Folia Phantom - Plugin Enable Transformer
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
 * Ensures the FoliaPatcher runtime is initialized when the plugin is enabled.
 *
 * <p>This transformer injects a call to {@code FoliaPatcher._i(this)} at the
 * beginning of the {@code onEnable} method in classes extending {@code JavaPlugin}.
 * This initialization is required for High-Performance API Mirroring (HPAM).</p>
 */
public class PluginEnableTransformer implements ClassTransformer {
    private final String relocatedPatcherPath;

    public PluginEnableTransformer(Logger logger, String relocatedPatcherPath) {
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new PluginEnableVisitor(next, relocatedPatcherPath + "/FoliaPatcher");
    }

    private static class PluginEnableVisitor extends ClassVisitor {
        private final String patcherPath;
        private boolean isJavaPlugin;

        public PluginEnableVisitor(ClassVisitor cv, String patcherPath) {
            super(Opcodes.ASM9, cv);
            this.patcherPath = patcherPath;
        }

        @Override
        public void visit(int version, int access, String name, String sig, String superName, String[] interfaces) {
            // We check for JavaPlugin as the direct superclass, which is the standard for Bukkit plugins.
            this.isJavaPlugin = "org/bukkit/plugin/java/JavaPlugin".equals(superName);
            super.visit(version, access, name, sig, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            // Inject into onEnable()V
            if (isJavaPlugin && "onEnable".equals(name) && "()V".equals(desc)) {
                return new PluginEnableMethodVisitor(mv, access, name, desc, patcherPath);
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
            // FoliaPatcher._i(this);
            loadThis();
            visitMethodInsn(INVOKESTATIC, patcherPath, "_i", "(Lorg/bukkit/plugin/Plugin;)V", false);
        }
    }
}
