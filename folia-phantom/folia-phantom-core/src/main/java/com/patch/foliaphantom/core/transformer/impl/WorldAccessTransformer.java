/*
 * Folia Phantom - World Access Class Transformer
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
 * Transforms thread-unsafe {@code org.bukkit.World} API calls into their thread-safe FoliaPatcher equivalents.
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

        public WorldAccessVisitor(ClassVisitor cv, String patcherPath) {
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
            // Find the first field that is a Plugin or a subclass. This is our best-effort
            // guess for the plugin instance.
            if (pluginFieldName == null && (desc.equals(PLUGIN_DESC) || desc.equals(JAVA_PLUGIN_DESC))) {
                this.pluginFieldName = name;
                this.pluginFieldDesc = desc;
            }
            return super.visitField(access, name, desc, sig, val);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            // Only transform methods if we have found a plugin field to reference.
            // Without it, we cannot inject the required plugin instance.
            if (pluginFieldName != null) {
                return new WorldAccessMethodVisitor(mv, access, name, desc, patcherPath, className, pluginFieldName, pluginFieldDesc);
            }
            return mv;
        }
    }

    private static class WorldAccessMethodVisitor extends AdviceAdapter {
        private final String patcherOwner;
        private final String pluginFieldOwner;
        private final String pluginFieldName;
        private final Type pluginFieldType;

        protected WorldAccessMethodVisitor(MethodVisitor mv, int access, String name, String desc,
                String patcherPath, String owner, String pfn, String pfd) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherOwner = patcherPath;
            this.pluginFieldOwner = owner;
            this.pluginFieldName = pfn;
            this.pluginFieldType = Type.getType(pfd);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if (tryHandle(owner, name, desc)) {
                return;
            }
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }

        private boolean tryHandle(String owner, String name, String desc) {
            switch (owner) {
                case "org/bukkit/World":
                    if ("spawn".equals(name) && "(Lorg/bukkit/Location;Ljava/lang/Class;)Lorg/bukkit/entity/Entity;".equals(desc)) {
                        return transform(2, "safeSpawnEntity", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;Ljava/lang/Class;)Lorg/bukkit/entity/Entity;");
                    }
                    if ("loadChunk".equals(name) && "(IIZ)V".equals(desc)) {
                        return transform(3, "safeLoadChunk", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;IIZ)V");
                    }
                    if ("strikeLightning".equals(name) && "(Lorg/bukkit/Location;)Lorg/bukkit/entity/LightningStrike;".equals(desc)) {
                        return transform(1, "safeStrikeLightning", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;)Lorg/bukkit/entity/LightningStrike;");
                    }
                    if ("createExplosion".equals(name) && "(Lorg/bukkit/Location;F)Z".equals(desc)) {
                        return transform(2, "safeCreateExplosion", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/World;Lorg/bukkit/Location;F)Z");
                    }
                    break;
            }
            return false;
        }

        /**
         * Generic transformation logic.
         * @param argCount Number of arguments on the stack (excluding the instance).
         * @param newName The name of the static method in FoliaPatcher.
         * @param newDesc The descriptor of the static method.
         */
        private boolean transform(int argCount, String newName, String newDesc) {
            Type[] argTypes = Type.getArgumentTypes(newDesc);

            // Store all original arguments (including the instance) into local variables.
            // The instance ('world' or 'block') is at argTypes[1]. The actual args start at [2].
            int[] locals = new int[argCount + 1];
            for (int i = argCount; i >= 0; i--) {
                // The new descriptor has Plugin as the first arg, so original args are offset by 1.
                locals[i] = newLocal(argTypes[i + 1]);
                storeLocal(locals[i]);
            }

            // Stack is now empty.
            // Push the plugin instance onto the stack.
            injectPluginInstance();

            // Push the original arguments back onto the stack from locals.
            for (int i = 0; i <= argCount; i++) {
                loadLocal(locals[i]);
            }

            super.visitMethodInsn(INVOKESTATIC, patcherOwner, newName, newDesc, false);
            return true;
        }

        private void injectPluginInstance() {
            // Load `this` onto the stack, then get the plugin field from it.
            loadThis();
            getField(Type.getObjectType(pluginFieldOwner), pluginFieldName, pluginFieldType);
        }
    }
}
