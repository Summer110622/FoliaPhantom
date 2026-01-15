/*
 * Folia Phantom - Teleport Transformer
 *
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import java.util.logging.Logger;

/**
 * Transforms Player.teleport() calls to use the asynchronous, thread-safe
 * FoliaPatcher implementation.
 */
public class TeleportTransformer implements ClassTransformer {
    private final Logger logger;
    private final String relocatedPatcherPath;
    private static final String PATCHER_CLASS = "FoliaPatcher";
    private static final String PLUGIN_DESC = "Lorg/bukkit/plugin/Plugin;";
    private static final String JAVA_PLUGIN_DESC = "Lorg/bukkit/plugin/java/JavaPlugin;";

    public TeleportTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new TeleportClassVisitor(next, relocatedPatcherPath, logger);
    }

    private static class TeleportClassVisitor extends ClassVisitor {
        private String className;
        private boolean isPluginClass;
        private String pluginFieldName;
        private String pluginFieldDesc;
        private final String patcherPath;
        private final Logger logger;

        public TeleportClassVisitor(ClassVisitor cv, String patcherPath, Logger logger) {
            super(Opcodes.ASM9, cv);
            this.patcherPath = patcherPath;
            this.logger = logger;
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
            if (!isPluginClass && pluginFieldName == null) {
                return mv; // Neither the plugin class nor contains a plugin field
            }
            return new TeleportMethodVisitor(mv, access, name, desc, patcherPath, className, pluginFieldName, pluginFieldDesc, isPluginClass);
        }
    }

    private static class TeleportMethodVisitor extends AdviceAdapter {
        private final String patcherPath;
        private final String pluginFieldOwner;
        private final String pluginFieldName;
        private final String pluginFieldDesc;
        private final boolean isPluginClass;

        protected TeleportMethodVisitor(MethodVisitor mv, int access, String name, String desc,
                String patcherPath, String owner, String pfn, String pfd, boolean isPluginClass) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherPath = patcherPath;
            this.pluginFieldOwner = owner;
            this.pluginFieldName = pfn;
            this.pluginFieldDesc = pfd;
            this.isPluginClass = isPluginClass;
        }

        private void loadPluginInstance() {
            loadThis(); // Load the current class instance (`this`)
            if (!isPluginClass) {
                // If the current class is not the plugin, get the plugin field from it
                getField(Type.getObjectType(pluginFieldOwner), pluginFieldName, Type.getType(pluginFieldDesc));
            }
            // If it is the plugin class, `this` is already the plugin instance we need.
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if (opcode == INVOKEINTERFACE && "org/bukkit/entity/Player".equals(owner) && "teleport".equals(name) && "(Lorg/bukkit/Location;)Z".equals(desc)) {
                String patcherOwner = patcherPath + "/" + PATCHER_CLASS;

                // Stack: [player, location]
                int locVar = newLocal(Type.getType("Lorg/bukkit/Location;"));
                storeLocal(locVar); // Location is now in a local var
                int playerVar = newLocal(Type.getType("Lorg/bukkit/entity/Player;"));
                storeLocal(playerVar); // Player is now in a local var

                // Load the arguments for the static `safeTeleport` call.
                loadPluginInstance(); // `this` or `this.plugin`
                loadLocal(playerVar);   // player
                loadLocal(locVar);      // location

                // Unconditionally call the safeTeleport method.
                // The logic to fire-and-forget is now entirely within FoliaPatcher.
                visitMethodInsn(INVOKESTATIC, patcherOwner, "safeTeleport", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Player;Lorg/bukkit/Location;)Z", false);

                return; // Instruction handled.
            }

            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }
    }
}
