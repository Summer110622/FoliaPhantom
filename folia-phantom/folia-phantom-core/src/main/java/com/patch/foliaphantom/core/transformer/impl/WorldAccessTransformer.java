/*
 * Folia Phantom - World Access Class Transformer
 *
 * Copyright (c) 2026 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import java.util.logging.Logger;

public class WorldAccessTransformer implements ClassTransformer {
    private final String relocatedPatcherPath;
    private static final String PATCHER_CLASS = "FoliaPatcher";
    private static final String PLUGIN_DESC = "Lorg/bukkit/plugin/Plugin;";
    private static final String JAVA_PLUGIN_DESC = "Lorg/bukkit/plugin/java/JavaPlugin;";

    public WorldAccessTransformer(Logger logger, String relocatedPatcherPath) {
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new WorldAccessVisitor(next, relocatedPatcherPath + "/" + PATCHER_CLASS);
    }

    private static class WorldAccessVisitor extends ClassVisitor {
        private String className;
        private String pluginFieldName;
        private String pluginFieldDesc;
        private final String patcherPath;
        private boolean isPluginClass;

        public WorldAccessVisitor(ClassVisitor cv, String patcherPath) {
            super(Opcodes.ASM9, cv);
            this.patcherPath = patcherPath;
        }

        @Override
        public void visit(int version, int access, String name, String sig, String superName, String[] interfaces) {
            this.className = name;
            this.isPluginClass = "org/bukkit/plugin/java/JavaPlugin".equals(superName);
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
                return new WorldAccessMethodVisitor(mv, access, name, desc, patcherPath, className, pluginFieldName, pluginFieldDesc, isPluginClass);
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
                String patcherPath, String owner, String pfn, String pfd, boolean isPlugin) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherOwner = patcherPath;
            this.pluginFieldOwner = owner;
            this.pluginFieldName = pfn;
            this.pluginFieldType = pfd != null ? Type.getType(pfd) : null;
            this.isPluginClass = isPlugin;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if (tryHandle(owner, name, desc)) {
                return;
            }
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }

        private boolean tryHandle(String owner, String name, String desc) {
            if (!"org/bukkit/World".equals(owner)) {
                return false;
            }

            switch (name) {
                case "getEntities":
                    if ("()Ljava/util/List;".equals(desc)) {
                        return transform(0, "safeGetEntities", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;)Ljava/util/List;");
                    }
                    break;
                case "spawnEntity":
                     if ("(Lorg/bukkit/Location;Lorg/bukkit/entity/EntityType;)Lorg/bukkit/entity/Entity;".equals(desc)) {
                        return transform(2, "safeSpawnEntity", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;Lorg/bukkit/entity/EntityType;)Lorg/bukkit/entity/Entity;");
                    }
                    break;
                case "spawn":
                    if ("(Lorg/bukkit/Location;Ljava/lang/Class;)Lorg/bukkit/entity/Entity;".equals(desc)) {
                        return transform(2, "safeSpawnLegacy", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;Ljava/lang/Class;)Lorg/bukkit/entity/Entity;");
                    }
                    break;
                case "strikeLightning":
                    if ("(Lorg/bukkit/Location;)Lorg/bukkit/entity/LightningStrike;".equals(desc)) {
                        return transform(1, "safeStrikeLightning", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;)Lorg/bukkit/entity/LightningStrike;");
                    }
                    break;
                case "generateTree":
                    if ("(Lorg/bukkit/Location;Lorg/bukkit/TreeType;)Z".equals(desc)) {
                        return transform(2, "safeGenerateTree", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;Lorg/bukkit/TreeType;)Z");
                    }
                    break;
                case "createExplosion":
                    if ("(DDDFZZ)Z".equals(desc)) {
                        return transform(6, "safeCreateExplosion", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;DDDFZZ)Z");
                    }
                    if ("(Lorg/bukkit/Location;F)Z".equals(desc)) {
                        return transform(2, "safeCreateExplosion", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;F)Z");
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
            if (isPluginClass) {
                loadThis();
            } else {
                loadThis();
                getField(Type.getObjectType(pluginFieldOwner), pluginFieldName, pluginFieldType);
            }
        }
    }
}
