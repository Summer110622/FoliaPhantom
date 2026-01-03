/*
 * Folia Phantom - BlockState Update Transformer
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
 * Transforms {@code BlockState.update()} calls into their thread-safe FoliaPatcher equivalents.
 *
 * <p>This transformer operates similarly to {@link ThreadSafetyTransformer}, finding a
 * plugin instance within the class and injecting it into a static wrapper call in the
 * {@code FoliaPatcher} runtime. It handles all three overloads of the {@code update} method.</p>
 */
public class BlockStateTransformer implements ClassTransformer {
    private final String relocatedPatcherPath;
    private static final String PATCHER_CLASS = "FoliaPatcher";
    private static final String PLUGIN_DESC = "Lorg/bukkit/plugin/Plugin;";
    private static final String JAVA_PLUGIN_DESC = "Lorg/bukkit/plugin/java/JavaPlugin;";
    private static final String BLOCKSTATE_OWNER = "org/bukkit/block/BlockState";

    public BlockStateTransformer(Logger logger, String relocatedPatcherPath) {
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new BlockStateVisitor(next, relocatedPatcherPath + "/" + PATCHER_CLASS);
    }

    private static class BlockStateVisitor extends ClassVisitor {
        private String className;
        private String pluginFieldName;
        private String pluginFieldDesc;
        private final String patcherPath;

        public BlockStateVisitor(ClassVisitor cv, String patcherPath) {
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
                return new BlockStateMethodVisitor(mv, access, name, desc, patcherPath, className, pluginFieldName, pluginFieldDesc);
            }
            return mv;
        }
    }

    private static class BlockStateMethodVisitor extends AdviceAdapter {
        private final String patcherOwner;
        private final String pluginFieldOwner;
        private final String pluginFieldName;
        private final Type pluginFieldType;

        protected BlockStateMethodVisitor(MethodVisitor mv, int access, String name, String desc,
                                          String patcherPath, String owner, String pfn, String pfd) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherOwner = patcherPath;
            this.pluginFieldOwner = owner;
            this.pluginFieldName = pfn;
            this.pluginFieldType = Type.getType(pfd);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if (isInterface && BLOCKSTATE_OWNER.equals(owner) && "update".equals(name)) {
                if ("()Z".equals(desc)) {
                    transform(0, "safeBlockStateUpdate", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/block/BlockState;)Z");
                    return;
                }
                if ("(Z)Z".equals(desc)) {
                    transform(1, "safeBlockStateUpdate", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/block/BlockState;Z)Z");
                    return;
                }
                if ("(ZZ)Z".equals(desc)) {
                    transform(2, "safeBlockStateUpdate", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/block/BlockState;ZZ)Z");
                    return;
                }
            }
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }

        private void transform(int argCount, String newName, String newDesc) {
            Type[] argTypes = Type.getArgumentTypes(newDesc);
            int[] locals = new int[argCount + 1];

            // Store original arguments (including the BlockState instance) into local variables.
            for (int i = argCount; i >= 0; i--) {
                locals[i] = newLocal(argTypes[i + 1]);
                storeLocal(locals[i]);
            }

            // Push the plugin instance onto the stack.
            injectPluginInstance();

            // Push the original arguments back onto the stack.
            for (int i = 0; i <= argCount; i++) {
                loadLocal(locals[i]);
            }

            super.visitMethodInsn(INVOKESTATIC, patcherOwner, newName, newDesc, false);
        }

        private void injectPluginInstance() {
            loadThis();
            getField(Type.getObjectType(pluginFieldOwner), pluginFieldName, pluginFieldType);
        }
    }
}
