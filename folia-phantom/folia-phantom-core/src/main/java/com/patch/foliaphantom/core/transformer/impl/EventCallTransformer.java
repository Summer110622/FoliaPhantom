/*
 * This file is part of Folia Phantom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 Marv
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import java.util.logging.Logger;

/**
 * Transforms {@code PluginManager.callEvent(Event)} calls to be thread-safe.
 *
 * <p>This transformer intercepts calls to {@code callEvent} and redirects them
 * to the {@code FoliaPatcher.safeCallEvent} runtime method. This ensures that
 * events called from asynchronous threads are dispatched on the correct Folia
 * scheduler, preventing concurrency issues and improving server stability.</p>
 */
public class EventCallTransformer implements ClassTransformer {
    private final Logger logger;
    private final String relocatedPatcherPath;
    private static final String PATCHER_CLASS = "FoliaPatcher";
    private static final String PLUGIN_MANAGER_OWNER = "org/bukkit/plugin/PluginManager";
    private static final String CALL_EVENT_NAME = "callEvent";
    private static final String CALL_EVENT_DESC = "(Lorg/bukkit/event/Event;)V";

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
        private String outerClassName = null;
        private boolean isPluginSubclass = false;

        public EventCallClassVisitor(ClassVisitor cv, String patcherPath) {
            super(Opcodes.ASM9, cv);
            this.patcherPath = patcherPath;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            this.className = name;
            if ("org/bukkit/plugin/java/JavaPlugin".equals(superName)) {
                this.isPluginSubclass = true;
            }
        }

        @Override
        public void visitOuterClass(String owner, String name, String descriptor) {
            super.visitOuterClass(owner, name, descriptor);
            this.outerClassName = owner;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
            // Only apply to instance methods where we can get a 'this' or 'this$0' reference
            if (!isStatic) {
                return new EventCallMethodVisitor(mv, access, name, desc, patcherPath, className, outerClassName, isPluginSubclass);
            }
            return mv;
        }
    }

    private static class EventCallMethodVisitor extends AdviceAdapter {
        private final String patcherPath;
        private final String className;
        private final String outerClassName;
        private final boolean isPluginSubclass;

        protected EventCallMethodVisitor(MethodVisitor mv, int access, String name, String desc, String patcherPath, String className, String outerClassName, boolean isPluginSubclass) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherPath = patcherPath;
            this.className = className;
            this.outerClassName = outerClassName;
            this.isPluginSubclass = isPluginSubclass;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if (opcode == INVOKEINTERFACE && PLUGIN_MANAGER_OWNER.equals(owner) &&
                CALL_EVENT_NAME.equals(name) && CALL_EVENT_DESC.equals(desc)) {

                // Stack before: [pluginManager, event]
                // We need to transform this to a static call to FoliaPatcher.safeCallEvent(plugin, event)
                // Stack needed: [plugin, event]

                // Store the event in a local variable to preserve it.
                int eventVar = newLocal(Type.getType("Lorg/bukkit/event/Event;"));
                storeLocal(eventVar); // Stack: [pluginManager]

                // Pop the unneeded pluginManager instance.
                pop(); // Stack: []

                // Now, load the actual plugin instance onto the stack.
                if (isPluginSubclass) {
                    // This class is a direct subclass of JavaPlugin, so 'this' is the plugin instance.
                    loadThis(); // Stack: [plugin]
                } else if (outerClassName != null) {
                    // This is an inner class. Get the plugin instance from the synthetic 'this$0' field.
                    loadThis(); // Stack: [this (inner class instance)]
                    super.visitFieldInsn(GETFIELD, className, "this$0", "L" + outerClassName + ";"); // Stack: [plugin]
                } else {
                    // We cannot reliably determine the plugin instance, so we abort the transformation
                    // and re-invoke the original instruction.
                    // First, reload the arguments.
                    mv.visitMethodInsn(INVOKESTATIC, "org/bukkit/Bukkit", "getPluginManager", "()Lorg/bukkit/plugin/PluginManager;", false);
                    loadLocal(eventVar);
                    super.visitMethodInsn(opcode, owner, name, desc, isInterface);
                    return;
                }

                // Load the event back from the local variable.
                loadLocal(eventVar); // Stack: [plugin, event]

                // Call our static helper method.
                String newDesc = "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/event/Event;)V";
                super.visitMethodInsn(INVOKESTATIC, patcherPath, "safeCallEvent", newDesc, false);
                return;
            }

            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }
    }
}
