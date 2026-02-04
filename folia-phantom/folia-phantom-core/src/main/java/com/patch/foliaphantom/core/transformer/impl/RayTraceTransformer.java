/*
 * Folia Phantom - RayTrace Class Transformer
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
 * Transforms thread-unsafe Bukkit World RayTrace API calls into their thread-safe FoliaPatcher equivalents.
 */
public class RayTraceTransformer implements ClassTransformer {
    private final String relocatedPatcherPath;
    private static final String PATCHER_CLASS = "FoliaPatcher";

    public RayTraceTransformer(Logger logger, String relocatedPatcherPath) {
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new RayTraceVisitor(next, relocatedPatcherPath + "/" + PATCHER_CLASS);
    }

    private static class RayTraceVisitor extends ClassVisitor {
        private String className;
        private String pluginFieldName;
        private String pluginFieldDesc;
        private boolean isSubclassOfJavaPlugin;
        private final String patcherPath;

        public RayTraceVisitor(ClassVisitor cv, String patcherPath) {
            super(Opcodes.ASM9, cv);
            this.patcherPath = patcherPath;
        }

        @Override
        public void visit(int version, int access, String name, String sig, String superName, String[] interfaces) {
            this.className = name;
            this.isSubclassOfJavaPlugin = "org/bukkit/plugin/java/JavaPlugin".equals(superName);
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
            if (pluginFieldName != null || isSubclassOfJavaPlugin) {
                return new RayTraceMethodVisitor(mv, access, name, desc, patcherPath, className,
                        pluginFieldName, pluginFieldDesc, isSubclassOfJavaPlugin);
            }
            return mv;
        }
    }

    private static class RayTraceMethodVisitor extends AdviceAdapter {
        private final String patcherOwner;
        private final String pluginFieldOwner;
        private final String pluginFieldName;
        private final Type pluginFieldType;
        private final boolean isPluginClass;

        protected RayTraceMethodVisitor(MethodVisitor mv, int access, String name, String desc,
                String patcherPath, String owner, String pfn, String pfd, boolean isPlugin) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherOwner = patcherPath;
            this.pluginFieldOwner = owner;
            this.pluginFieldName = pfn;
            this.pluginFieldType = pfd != null ? Type.getType(pfd) : null;
            this.isPluginClass = isPlugin;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            if (owner.equals("org/bukkit/World")) {
                if (name.equals("rayTraceBlocks") && desc.equals("(Lorg/bukkit/Location;Lorg/bukkit/util/Vector;DLorg/bukkit/FluidCollisionMode;Z)Lorg/bukkit/util/RayTraceResult;")) {
                    transform(5, "_rtb", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;Lorg/bukkit/util/Vector;DLorg/bukkit/FluidCollisionMode;Z)Lorg/bukkit/util/RayTraceResult;");
                    return;
                }
                if (name.equals("rayTraceEntities") && desc.equals("(Lorg/bukkit/Location;Lorg/bukkit/util/Vector;DDLjava/util/function/Predicate;)Lorg/bukkit/util/RayTraceResult;")) {
                    transform(5, "_rte", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;Lorg/bukkit/util/Vector;DDLjava/util/function/Predicate;)Lorg/bukkit/util/RayTraceResult;");
                    return;
                }
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }

        private void transform(int argCount, String newName, String newDesc) {
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
        }

        private void injectPluginInstance() {
            if (isPluginClass) {
                loadThis();
            } else {
                loadThis();
                getField(Type.getObjectType(pluginFieldOwner), pluginFieldName, pluginFieldType);
            }
        }
    }
}
