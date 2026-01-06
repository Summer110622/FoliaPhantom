/*
 * Folia Phantom - Thread Safety Class Transformer
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
 * Transforms thread-unsafe Bukkit API calls into their thread-safe FoliaPatcher equivalents.
 *
 * <p>This transformer robustly finds a reference to the plugin instance within a class
 * and injects it into the method call, redirecting it to a static method in the
 * {@code FoliaPatcher} runtime. It uses {@link AdviceAdapter} for safe and reliable
 * bytecode stack manipulation.</p>
 */
public class ThreadSafetyTransformer implements ClassTransformer {
    private final String relocatedPatcherPath;
    private static final String PATCHER_CLASS = "FoliaPatcher";
    private static final String PLUGIN_DESC = "Lorg/bukkit/plugin/Plugin;";
    private static final String JAVA_PLUGIN_DESC = "Lorg/bukkit/plugin/java/JavaPlugin;";

    public ThreadSafetyTransformer(Logger logger, String relocatedPatcherPath) {
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new ThreadSafetyVisitor(next, relocatedPatcherPath + "/" + PATCHER_CLASS);
    }

    private static class ThreadSafetyVisitor extends ClassVisitor {
        private String className;
        private String pluginFieldName;
        private String pluginFieldDesc;
        private final String patcherPath;

        public ThreadSafetyVisitor(ClassVisitor cv, String patcherPath) {
            super(Opcodes.ASM9, cv);
            this.patcherPath = patcherPath;
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
            if (pluginFieldName != null) {
                return new ThreadSafetyMethodVisitor(mv, access, name, desc, patcherPath, className, pluginFieldName, pluginFieldDesc);
            }
            return mv;
        }
    }

    private static class ThreadSafetyMethodVisitor extends AdviceAdapter {
        private final String patcherOwner;
        private final String pluginFieldOwner;
        private final String pluginFieldName;
        private final Type pluginFieldType;

        protected ThreadSafetyMethodVisitor(MethodVisitor mv, int access, String name, String desc,
                String patcherPath, String owner, String pfn, String pfd) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherOwner = patcherPath;
            this.pluginFieldOwner = owner;
            this.pluginFieldName = pfn;
            this.pluginFieldType = Type.getType(pfd);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if (owner.equals("org/bukkit/block/Block") && tryHandle(name, desc)) {
                return;
            }
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }

        private boolean tryHandle(String name, String desc) {
            switch (name) {
                case "setType":
                    if ("(Lorg/bukkit/Material;)V".equals(desc)) return transform(1, "safeSetType", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/block/Block;Lorg/bukkit/Material;)V");
                    if ("(Lorg/bukkit/Material;Z)V".equals(desc)) return transform(2, "safeSetTypeWithPhysics", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/block/Block;Lorg/bukkit/Material;Z)V");
                    break;
                case "setBlockData":
                    if ("(Lorg/bukkit/block/data/BlockData;Z)V".equals(desc)) {
                        return transform(2, "safeSetBlockData", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/block/Block;Lorg/bukkit/block/data/BlockData;Z)V");
                    }
                    break;
            }
            return false;
        }

        private boolean transform(int argCount, String newName, String newDesc) {
            Type[] argTypes = Type.getArgumentTypes(newDesc);
            int[] locals = new int[argCount + 1];
            for (int i = argCount; i >= 0; i--) {
                locals[i] = newLocal(argTypes[i + 1]);
                storeLocal(locals[i]);
            }

            injectPluginInstance();

            for (int i = 0; i <= argCount; i++) {
                loadLocal(locals[i]);
            }

            super.visitMethodInsn(INVOKESTATIC, patcherOwner, newName, newDesc, false);
            return true;
        }

        private void injectPluginInstance() {
            loadThis();
            getField(Type.getObjectType(pluginFieldOwner), pluginFieldName, pluginFieldType);
        }
    }
}
