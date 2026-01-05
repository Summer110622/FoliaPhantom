/*
 * Folia Phantom - World Access Transformer
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
 * Transforms thread-unsafe Bukkit World API calls into their thread-safe FoliaPatcher equivalents.
 *
 * <p>This transformer robustly finds a reference to the plugin instance within a class
 * and injects it into the method call, redirecting it to a static method in the
 * {@code FoliaPatcher} runtime. It uses {@link AdviceAdapter} for safe and reliable
 * bytecode stack manipulation.</p>
 */
public class WorldAccessTransformer implements ClassTransformer {
    private final String relocatedPatcherPath;
    private static final String PATCHER_CLASS = "FoliaPatcher";
    private static final String PLUGIN_DESC = "Lorg/bukkit/plugin/Plugin;";
    private static final String JAVA_PLUGIN_DESC = "Lorg/bukkit/plugin/java/JavaPlugin;";

    public WorldAccessTransformer(Logger logger, String relocatedPatcherPath) {
        // The logger is currently unused but kept for API consistency.
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new WorldAccessClassVisitor(next, relocatedPatcherPath + "/" + PATCHER_CLASS);
    }

    private static class WorldAccessClassVisitor extends ClassVisitor {
        private String className;
        private String superName;
        private String pluginFieldName;
        private String pluginFieldDesc;
        private boolean isPluginClass;
        private final String patcherPath;

        public WorldAccessClassVisitor(ClassVisitor cv, String patcherPath) {
            super(Opcodes.ASM9, cv);
            this.patcherPath = patcherPath;
        }

        @Override
        public void visit(int version, int access, String name, String sig, String superName, String[] interfaces) {
            this.className = name;
            this.superName = superName;
            this.isPluginClass = JAVA_PLUGIN_DESC.equals("L" + superName + ";");
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
            if (pluginFieldName != null || isPluginClass) {
                return new WorldAccessMethodVisitor(mv, access, name, desc, patcherPath, className,
                                                     isPluginClass, pluginFieldName, pluginFieldDesc);
            }
            return mv;
        }
    }

    private static class WorldAccessMethodVisitor extends AdviceAdapter {
        private final String patcherOwner;
        private final String pluginFieldOwner;
        private final String pluginFieldName;
        private final Type pluginFieldType;
        private final boolean isPluginClass;

        protected WorldAccessMethodVisitor(MethodVisitor mv, int access, String name, String desc,
                String patcherPath, String owner, boolean isPlugin, String pfn, String pfd) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherOwner = patcherPath;
            this.pluginFieldOwner = owner;
            this.isPluginClass = isPlugin;
            this.pluginFieldName = pfn;
            this.pluginFieldType = pfd != null ? Type.getType(pfd) : null;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if (isInterface && opcode == INVOKEINTERFACE && "org/bukkit/World".equals(owner)) {
                if (tryHandleWorldMethods(name, desc)) {
                    return;
                }
            }
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }

        private boolean tryHandleWorldMethods(String name, String desc) {
            switch (name) {
                case "getEntities":
                    if ("()Ljava/util/List;".equals(desc)) {
                        return transform(0, "getEntities", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;)Ljava/util/List;");
                    }
                    break;
                case "getPlayers":
                     if ("()Ljava/util/List;".equals(desc)) {
                        return transform(0, "getPlayers", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;)Ljava/util/List;");
                    }
                    break;
                case "spawnEntity":
                    if ("(Lorg/bukkit/Location;Lorg/bukkit/entity/EntityType;)Lorg/bukkit/entity/Entity;".equals(desc)) {
                        return transform(2, "spawnEntity", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;Lorg/bukkit/entity/EntityType;)Lorg/bukkit/entity/Entity;");
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
            if (!isPluginClass) {
                getField(Type.getObjectType(pluginFieldOwner), pluginFieldName, pluginFieldType);
            }
        }
    }
}
