/*
 * Folia Phantom - Custom Event Class Transformer
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
 * Transforms direct `PluginManager.callEvent` calls to a thread-safe
 * FoliaPatcher equivalent.
 *
 * This transformer intercepts calls to `Bukkit.getPluginManager().callEvent(event)`
 * and redirects them to `FoliaPatcher.safeCallEvent(plugin, event)`. It
 * intelligently finds the plugin instance within the class and injects it as the
 * first argument for the new static method call.
 */
public class CustomEventTransformer implements ClassTransformer {
    private final String relocatedPatcherPath;
    private static final String PATCHER_CLASS = "FoliaPatcher";
    private static final String PLUGIN_DESC = "Lorg/bukkit/plugin/Plugin;";
    private static final String JAVA_PLUGIN_DESC = "Lorg/bukkit/plugin/java/JavaPlugin;";

    public CustomEventTransformer(Logger logger, String relocatedPatcherPath) {
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new CustomEventVisitor(next, relocatedPatcherPath + "/" + PATCHER_CLASS);
    }

    private static class CustomEventVisitor extends ClassVisitor {
        private String className;
        private String pluginFieldName;
        private String pluginFieldDesc;
        private final String patcherPath;

        public CustomEventVisitor(ClassVisitor cv, String patcherPath) {
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
                return new CustomEventMethodVisitor(mv, access, name, desc, patcherPath, className, pluginFieldName, pluginFieldDesc);
            }
            return mv;
        }
    }

    private static class CustomEventMethodVisitor extends AdviceAdapter {
        private final String patcherOwner;
        private final String pluginFieldOwner;
        private final String pluginFieldName;
        private final Type pluginFieldType;

        protected CustomEventMethodVisitor(MethodVisitor mv, int access, String name, String desc,
                String patcherPath, String owner, String pfn, String pfd) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherOwner = patcherPath;
            this.pluginFieldOwner = owner;
            this.pluginFieldName = pfn;
            this.pluginFieldType = Type.getType(pfd);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            // We are looking for INVOKEINTERFACE org/bukkit/plugin/PluginManager.callEvent (Lorg/bukkit/event/Event;)V
            if (opcode == INVOKEINTERFACE &&
                "org/bukkit/plugin/PluginManager".equals(owner) &&
                "callEvent".equals(name) &&
                "(Lorg/bukkit/event/Event;)V".equals(desc)) {

                // The stack before this instruction is: [..., pluginManagerInstance, eventInstance]
                // We need to transform it to: [..., pluginInstance, eventInstance]
                // and then call our static method.

                // 1. The event instance is already on top of the stack. We need to keep it.
                // 2. The pluginManager instance is below it. We don't need it.
                // 3. We need to inject the Plugin instance before the event.

                // Store the event instance in a local variable.
                int eventLocal = newLocal(Type.getType("Lorg/bukkit/event/Event;"));
                storeLocal(eventLocal);

                // Pop the pluginManager instance from the stack.
                pop();

                // Inject the Plugin instance.
                injectPluginInstance();

                // Load the event instance back onto the stack.
                loadLocal(eventLocal);

                // Call the static patcher method.
                super.visitMethodInsn(INVOKESTATIC, patcherOwner, "safeCallEvent", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/event/Event;)V", false);
            } else {
                super.visitMethodInsn(opcode, owner, name, desc, isInterface);
            }
        }

        private void injectPluginInstance() {
            loadThis();
            getField(Type.getObjectType(pluginFieldOwner), pluginFieldName, pluginFieldType);
        }
    }
}
