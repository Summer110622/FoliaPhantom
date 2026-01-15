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
 * Transforms thread-unsafe Bukkit Inventory API calls into their thread-safe FoliaPatcher equivalents.
 *
 * <p>This transformer robustly finds a reference to the plugin instance within a class
 * and injects it into the method call, redirecting it to a static method in the
 * {@code FoliaPatcher} runtime. It uses {@link AdviceAdapter} for safe and reliable
 * bytecode stack manipulation.</p>
 */
public class InventoryTransformer implements ClassTransformer {
    private final String relocatedPatcherPath;
    private static final String PATCHER_CLASS = "FoliaPatcher";
    private static final String PLUGIN_DESC = "Lorg/bukkit/plugin/Plugin;";
    private static final String JAVA_PLUGIN_DESC = "Lorg/bukkit/plugin/java/JavaPlugin;";

    public InventoryTransformer(Logger logger, String relocatedPatcherPath) {
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new InventoryVisitor(next, relocatedPatcherPath + "/" + PATCHER_CLASS);
    }

    private static class InventoryVisitor extends ClassVisitor {
        private String className;
        private String pluginFieldName;
        private String pluginFieldDesc;
        private final String patcherPath;

        public InventoryVisitor(ClassVisitor cv, String patcherPath) {
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
                return new InventoryMethodVisitor(mv, access, name, desc, patcherPath, className, pluginFieldName, pluginFieldDesc);
            }
            return mv;
        }
    }

    private static class InventoryMethodVisitor extends AdviceAdapter {
        private final String patcherOwner;
        private final String pluginFieldOwner;
        private final String pluginFieldName;
        private final Type pluginFieldType;

        protected InventoryMethodVisitor(MethodVisitor mv, int access, String name, String desc,
                String patcherPath, String owner, String pfn, String pfd) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherOwner = patcherPath;
            this.pluginFieldOwner = owner;
            this.pluginFieldName = pfn;
            this.pluginFieldType = Type.getType(pfd);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if (isInterface && owner.equals("org/bukkit/inventory/Inventory")) {
                if (tryHandleInventory(name, desc)) {
                    return;
                }
            }
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }

        private boolean tryHandleInventory(String name, String desc) {
            switch (name) {
                case "setItem":
                    if ("(ILorg/bukkit/inventory/ItemStack;)V".equals(desc)) {
                        return transform(2, "safeSetItem", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/inventory/Inventory;ILorg/bukkit/inventory/ItemStack;)V");
                    }
                    break;
                case "addItem":
                    if ("([Lorg/bukkit/inventory/ItemStack;)Ljava/util/HashMap;".equals(desc)) {
                        return transform(1, "safeAddItem", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/inventory/Inventory;[Lorg/bukkit/inventory/ItemStack;)Ljava/util/HashMap;");
                    }
                    break;
                case "clear":
                    if ("()V".equals(desc)) {
                        return transform(0, "safeClear", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/inventory/Inventory;)V");
                    }
                    break;
            }
            return false;
        }

        private boolean transform(int argCount, String newName, String newDesc) {
            Type[] argTypes = Type.getArgumentTypes(newDesc);

            // Store all original arguments (including the instance) into local variables.
            int[] locals = new int[argCount + 1];
            for (int i = argCount; i >= 0; i--) {
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
