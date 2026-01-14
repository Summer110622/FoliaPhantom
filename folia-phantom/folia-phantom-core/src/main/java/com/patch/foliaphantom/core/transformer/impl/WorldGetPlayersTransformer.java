/*
 * Folia Phantom - World#getPlayers() Transformer
 *
 * Copyright (c) 2024 Marv
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

public class WorldGetPlayersTransformer implements ClassTransformer {
    private final Logger logger;
    private final String relocatedPatcherPath;
    private static final String PATCHER_CLASS = "FoliaPatcher";
    private static final String PLUGIN_DESC = "Lorg/bukkit/plugin/Plugin;";
    private static final String JAVA_PLUGIN_DESC = "Lorg/bukkit/plugin/java/JavaPlugin;";
    private static final String JAVA_PLUGIN_SUPER = "org/bukkit/plugin/java/JavaPlugin";

    public WorldGetPlayersTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new WorldGetPlayersClassVisitor(next, relocatedPatcherPath, logger);
    }

    private static class WorldGetPlayersClassVisitor extends ClassVisitor {
        private String className;
        private String pluginFieldName;
        private String pluginFieldDesc;
        private boolean isPluginClass = false;
        private final String patcherPath;
        private final Logger logger;

        public WorldGetPlayersClassVisitor(ClassVisitor cv, String patcherPath, Logger logger) {
            super(Opcodes.ASM9, cv);
            this.patcherPath = patcherPath;
            this.logger = logger;
        }

        @Override
        public void visit(int version, int access, String name, String sig, String superName, String[] interfaces) {
            this.className = name;
            if (superName != null && superName.equals(JAVA_PLUGIN_SUPER)) {
                this.isPluginClass = true;
            }
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
            if (pluginFieldName == null && !isPluginClass) {
                 // We can't find a plugin instance, so don't transform anything.
                return mv;
            }
            return new WorldGetPlayersMethodVisitor(mv, access, name, desc, patcherPath, className, pluginFieldName, pluginFieldDesc, isPluginClass);
        }
    }

    private static class WorldGetPlayersMethodVisitor extends AdviceAdapter {
        private final String patcherPath;
        private final String pluginFieldOwner;
        private final String pluginFieldName;
        private final String pluginFieldDesc;
        private final boolean isPluginClass;

        protected WorldGetPlayersMethodVisitor(MethodVisitor mv, int access, String name, String desc,
                String patcherPath, String owner, String pfn, String pfd, boolean isPlugin) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherPath = patcherPath;
            this.pluginFieldOwner = owner;
            this.pluginFieldName = pfn;
            this.pluginFieldDesc = pfd;
            this.isPluginClass = isPlugin;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if (opcode == Opcodes.INVOKEINTERFACE &&
                "org/bukkit/World".equals(owner) &&
                "getPlayers".equals(name) &&
                "()Ljava/util/List;".equals(desc)) {

                String patcherOwner = patcherPath + "/" + PATCHER_CLASS;

                int worldLocal = newLocal(Type.getType("Lorg/bukkit/World;"));
                storeLocal(worldLocal);

                if (isPluginClass) {
                    loadThis(); // 'this' is the plugin instance
                } else {
                    loadThis();
                    getField(Type.getObjectType(pluginFieldOwner), pluginFieldName, Type.getType(pluginFieldDesc));
                }

                loadLocal(worldLocal);

                String newDesc = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;)Ljava/util/List;";
                super.visitMethodInsn(INVOKESTATIC, patcherOwner, "safeGetPlayers", newDesc, false);
                return;
            }
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }
    }
}
