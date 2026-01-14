/*
 * Folia Phantom - World.getPlayers() Transformer
 *
 * Copyright (c) 2024 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import java.util.logging.Logger;

public class WorldGetPlayersTransformer implements ClassTransformer {
    private final String relocatedPatcherPath;
    private static final String PATCHER_CLASS = "FoliaPatcher";
    private static final String PLUGIN_DESC = "Lorg/bukkit/plugin/Plugin;";
    private static final String JAVA_PLUGIN_DESC = "Lorg/bukkit/plugin/java/JavaPlugin;";

    public WorldGetPlayersTransformer(Logger logger, String relocatedPatcherPath) {
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new WorldGetPlayersVisitor(next, relocatedPatcherPath + "/" + PATCHER_CLASS);
    }

    private static class WorldGetPlayersVisitor extends ClassVisitor {
        private String className;
        private String pluginFieldName;
        private String pluginFieldDesc;
        private boolean isSubclassOfJavaPlugin;
        private final String patcherPath;

        public WorldGetPlayersVisitor(ClassVisitor cv, String patcherPath) {
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
            if (pluginFieldName == null && (desc.equals(PLUGIN_DESC) || desc.equals(JAVA_PLUGIN_DESC))) {
                this.pluginFieldName = name;
                this.pluginFieldDesc = desc;
            }
            return super.visitField(access, name, desc, sig, val);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            if (pluginFieldName != null || isSubclassOfJavaPlugin) {
                return new WorldGetPlayersMethodVisitor(mv, access, name, desc, patcherPath, className,
                        pluginFieldName, pluginFieldDesc, isSubclassOfJavaPlugin);
            }
            return mv;
        }
    }

    private static class WorldGetPlayersMethodVisitor extends AdviceAdapter {
        private final String patcherOwner;
        private final String pluginFieldOwner;
        private final String pluginFieldName;
        private final Type pluginFieldType;
        private final boolean isPluginClass;

        protected WorldGetPlayersMethodVisitor(MethodVisitor mv, int access, String name, String desc,
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
            if ("org/bukkit/World".equals(owner) && "getPlayers".equals(name) && "()Ljava/util/List;".equals(desc)) {
                transform();
                return;
            }
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }

        private void transform() {
            // Stack before: [world]
            // We need to transform this to a static call to FoliaPatcher.safeGetPlayers(plugin, world)
            // Stack needed: [plugin, world]
            int worldVar = newLocal(Type.getType("Lorg/bukkit/World;"));
            storeLocal(worldVar); // stack: []

            injectPluginInstance(); // stack: [plugin]

            loadLocal(worldVar); // stack: [plugin, world]

            super.visitMethodInsn(INVOKESTATIC, patcherOwner, "safeGetPlayers", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;)Ljava/util/List;", false);
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
