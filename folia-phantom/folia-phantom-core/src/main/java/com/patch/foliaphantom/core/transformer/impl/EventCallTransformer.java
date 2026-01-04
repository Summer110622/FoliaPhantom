/*
 * Folia Phantom - Event Call Transformer
 *
 * Copyright (c) 2024 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import java.util.logging.Logger;

/**
 * Transforms {@code PluginManager.callEvent(Event)} to be thread-safe under Folia.
 *
 * <p>
 * This transformer intercepts calls to {@code callEvent} and redirects them to
 * a static {@code FoliaPatcher.safeCallEvent(Plugin, Event)} method.
 * This ensures that events are dispatched on the correct thread (e.g., entity's region)
 * based on the event's context.
 * </p>
 */
public class EventCallTransformer implements ClassTransformer {
    private final Logger logger;
    private final String relocatedPatcherPath;
    private static final String PATCHER_CLASS = "FoliaPatcher";
    private static final String PLUGIN_MANAGER_OWNER = "org/bukkit/plugin/PluginManager";
    private static final String PLUGIN_DESC = "Lorg/bukkit/plugin/Plugin;";
    private static final String JAVA_PLUGIN_DESC = "Lorg/bukkit/plugin/java/JavaPlugin;";

    public EventCallTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new EventCallClassVisitor(next, relocatedPatcherPath + "/" + PATCHER_CLASS);
    }

    private static class EventCallClassVisitor extends ClassVisitor {
        private final String patcherPath;
        private String className;
        private String superName;
        private String pluginField = null;
        private boolean isPluginItself = false;

        public EventCallClassVisitor(ClassVisitor cv, String patcherPath) {
            super(Opcodes.ASM9, cv);
            this.patcherPath = patcherPath;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name;
            this.superName = superName;
            if (JAVA_PLUGIN_DESC.equals("L" + superName + ";")) {
                isPluginItself = true;
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (pluginField == null && PLUGIN_DESC.equals(descriptor)) {
                pluginField = name;
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            if (pluginField != null || isPluginItself) {
                 return new EventCallMethodVisitor(mv, access, name, desc, patcherPath, className, pluginField, isPluginItself);
            }
            return mv;
        }
    }

    private static class EventCallMethodVisitor extends AdviceAdapter {
        private final String patcherPath;
        private final String className;
        private final String pluginField;
        private final boolean isPluginItself;

        protected EventCallMethodVisitor(MethodVisitor mv, int access, String name, String desc, String patcherPath, String className, String pluginField, boolean isPluginItself) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherPath = patcherPath;
            this.className = className;
            this.pluginField = pluginField;
            this.isPluginItself = isPluginItself;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            // Redirect: pluginManager.callEvent(event) -> FoliaPatcher.safeCallEvent(plugin, event)
            if (PLUGIN_MANAGER_OWNER.equals(owner) && "callEvent".equals(name) && "(Lorg/bukkit/event/Event;)V".equals(desc) && opcode == INVOKEINTERFACE) {
                // Original stack: [pluginManager, event]
                // We need to transform it to: [plugin, event] and call our static method.

                // Store the event in a local variable.
                int eventLocal = newLocal(Type.getType("Lorg/bukkit/event/Event;"));
                storeLocal(eventLocal);

                // Pop the pluginManager instance.
                pop();

                // Load the plugin instance.
                if (isPluginItself) {
                    loadThis();
                } else {
                    loadThis();
                    getField(Type.getObjectType(className), pluginField, Type.getType(PLUGIN_DESC));
                }

                // Load the event back from the local variable.
                loadLocal(eventLocal);

                // Call the static FoliaPatcher method.
                String newDesc = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/event/Event;)V";
                super.visitMethodInsn(INVOKESTATIC, patcherPath, "safeCallEvent", newDesc, false);
                return;
            }

            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }
    }
}