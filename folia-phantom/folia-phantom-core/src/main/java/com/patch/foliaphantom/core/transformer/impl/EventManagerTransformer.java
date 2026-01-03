/*
 * Folia Phantom - Event Manager Class Transformer
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
 * Transforms calls to {@code PluginManager.callEvent} into thread-safe
 * {@code FoliaPatcher.safeCallEvent} calls.
 *
 * <p>This transformer is crucial for ensuring that custom events fired from
 * asynchronous threads are dispatched to the correct Folia scheduler,
 * preventing server instability and race conditions.</p>
 */
public class EventManagerTransformer implements ClassTransformer {
    private final String relocatedPatcherPath;
    private static final String PATCHER_CLASS = "FoliaPatcher";
    private static final String PLUGIN_DESC = "Lorg/bukkit/plugin/Plugin;";
    private static final String JAVA_PLUGIN_DESC = "Lorg/bukkit/plugin/java/JavaPlugin;";
    private static final String PLUGIN_MANAGER_OWNER = "org/bukkit/plugin/PluginManager";
    private static final String CALL_EVENT_NAME = "callEvent";
    private static final String CALL_EVENT_DESC = "(Lorg/bukkit/event/Event;)V";

    public EventManagerTransformer(Logger logger, String relocatedPatcherPath) {
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new EventManagerVisitor(next, relocatedPatcherPath + "/" + PATCHER_CLASS);
    }

    private static class EventManagerVisitor extends ClassVisitor {
        private String className;
        private String pluginFieldName;
        private String pluginFieldDesc;
        private boolean isPluginClass;
        private final String patcherPath;

        public EventManagerVisitor(ClassVisitor cv, String patcherPath) {
            super(Opcodes.ASM9, cv);
            this.patcherPath = patcherPath;
            this.isPluginClass = false;
        }

        @Override
        public void visit(int version, int access, String name, String sig, String superName, String[] interfaces) {
            this.className = name;
            if (superName != null && superName.equals("org/bukkit/plugin/java/JavaPlugin")) {
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
            if (pluginFieldName != null || isPluginClass) {
                return new EventManagerMethodVisitor(mv, access, name, desc, patcherPath, className, pluginFieldName, pluginFieldDesc, isPluginClass);
            }
            return mv;
        }
    }

    private static class EventManagerMethodVisitor extends AdviceAdapter {
        private final String patcherOwner;
        private final String pluginFieldOwner;
        private final String pluginFieldName;
        private final Type pluginFieldType;
        private final boolean isPluginClass;

        protected EventManagerMethodVisitor(MethodVisitor mv, int access, String name, String desc,
                String patcherPath, String owner, String pfn, String pfd, boolean isPluginClass) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherOwner = patcherPath;
            this.pluginFieldOwner = owner;
            this.pluginFieldName = pfn;
            this.pluginFieldType = (pfd != null) ? Type.getType(pfd) : null;
            this.isPluginClass = isPluginClass;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if (isInterface && owner.equals(PLUGIN_MANAGER_OWNER) && name.equals(CALL_EVENT_NAME) && desc.equals(CALL_EVENT_DESC)) {
                // Stack before: [pluginManagerInstance, eventInstance]

                // Store eventInstance in a local variable.
                int eventVar = newLocal(Type.getType("Lorg/bukkit/event/Event;"));
                storeLocal(eventVar); // Stack now: [pluginManagerInstance]

                // Pop the now-unneeded pluginManagerInstance.
                pop(); // Stack now: []

                // Push the plugin instance onto the stack.
                injectPluginInstance(); // Stack now: [pluginInstance]

                // Load the eventInstance back from the local variable.
                loadLocal(eventVar); // Stack now: [pluginInstance, eventInstance]

                // Call our static helper, which consumes the top two stack values.
                super.visitMethodInsn(INVOKESTATIC, patcherOwner, "safeCallEvent", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/event/Event;)V", false);
            } else {
                super.visitMethodInsn(opcode, owner, name, desc, isInterface);
            }
        }

        private void injectPluginInstance() {
            loadThis();
            if (!isPluginClass) {
                getField(Type.getObjectType(pluginFieldOwner), pluginFieldName, pluginFieldType);
            }
        }
    }
}
