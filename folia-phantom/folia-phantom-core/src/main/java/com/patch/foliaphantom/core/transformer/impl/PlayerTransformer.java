/*
 * Folia Phantom - Player Class Transformer
 *
 * Copyright (c) 2024 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import java.util.Map;
import java.util.logging.Logger;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

/**
 * Transforms {@code org.bukkit.entity.Player} method calls to be Folia-compatible by injecting
 * the owning plugin instance into the call.
 *
 * <p>This transformer mirrors the strategy of {@link ThreadSafetyTransformer}, locating a
 * {@code Plugin} field within the class and using it as the context for scheduling tasks
 * via {@code FoliaPatcher}.
 */
public class PlayerTransformer implements ClassTransformer {
    private final String relocatedPatcherPath;
    private static final String PATCHER_CLASS = "FoliaPatcher";
    private static final String PLAYER_OWNER = "org/bukkit/entity/Player";
    private static final String PLUGIN_DESC = "Lorg/bukkit/plugin/Plugin;";
    private static final String JAVA_PLUGIN_DESC = "Lorg/bukkit/plugin/java/JavaPlugin;";

    private static final Map<String, String> DANGEROUS_METHODS = Map.of(
        "sendMessage", "(Ljava/lang/String;)V",
        "kickPlayer", "(Ljava/lang/String;)V",
        "performCommand", "(Ljava/lang/String;)Z"
    );
    private static final Map<String, String> DANGEROUS_METHODS_ARRAY = Map.of(
        "sendMessage", "([Ljava/lang/String;)V"
    );

    public PlayerTransformer(Logger logger, String relocatedPatcherPath) {
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new PlayerClassVisitor(next, relocatedPatcherPath + "/" + PATCHER_CLASS);
    }

    private static class PlayerClassVisitor extends ClassVisitor {
        private final String patcherPath;
        private String className;
        private String pluginFieldName;
        private String pluginFieldDesc;

        public PlayerClassVisitor(ClassVisitor cv, String patcherPath) {
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
                return new PlayerMethodVisitor(mv, access, name, desc, patcherPath, className, pluginFieldName, pluginFieldDesc);
            }
            return mv;
        }
    }

    private static class PlayerMethodVisitor extends AdviceAdapter {
        private final String patcherPath;
        private final String pluginFieldOwner;
        private final String pluginFieldName;
        private final Type pluginFieldType;

        protected PlayerMethodVisitor(MethodVisitor mv, int access, String name, String desc, String patcherPath, String owner, String pfn, String pfd) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherPath = patcherPath;
            this.pluginFieldOwner = owner;
            this.pluginFieldName = pfn;
            this.pluginFieldType = Type.getType(pfd);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if (opcode == INVOKEINTERFACE && PLAYER_OWNER.equals(owner)) {
                if (DANGEROUS_METHODS.containsKey(name) && DANGEROUS_METHODS.get(name).equals(desc)) {
                    transformCall(name, desc, 1);
                    return;
                }
                if (DANGEROUS_METHODS_ARRAY.containsKey(name) && DANGEROUS_METHODS_ARRAY.get(name).equals(desc)) {
                    transformCall(name, desc, 1);
                    return;
                }
            }
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }

        private void transformCall(String name, String desc, int argCount) {
            // Determine the new method signature.
            String newName = "safe" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            String newDesc = "(Lorg/bukkit/plugin/Plugin;L" + PLAYER_OWNER + ";" + desc.substring(1);
            Type[] argTypes = Type.getArgumentTypes(newDesc);

            // Store original arguments (player instance + method args) into local variables.
            int[] locals = new int[argCount + 1];
            for (int i = argCount; i >= 0; i--) {
                locals[i] = newLocal(argTypes[i + 1]); // Offset by 1 because of the new plugin arg
                storeLocal(locals[i]);
            }

            // Inject the plugin instance onto the stack.
            injectPluginInstance();

            // Load the original arguments back onto the stack.
            for (int i = 0; i <= argCount; i++) {
                loadLocal(locals[i]);
            }

            super.visitMethodInsn(INVOKESTATIC, patcherPath, newName, newDesc, false);
        }

        private void injectPluginInstance() {
            loadThis();
            getField(Type.getObjectType(pluginFieldOwner), pluginFieldName, pluginFieldType);
        }
    }
}
