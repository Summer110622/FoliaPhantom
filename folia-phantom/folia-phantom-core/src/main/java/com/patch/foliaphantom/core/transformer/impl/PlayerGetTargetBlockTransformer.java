/*
 * Folia Phantom - PlayerGetTargetBlockTransformer Class Transformer
 *
 * Copyright (c) 2024 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;

import java.util.logging.Logger;

public class PlayerGetTargetBlockTransformer implements ClassTransformer {

    private final String relocatedPatcherPath;
    private static final String PATCHER_CLASS = "FoliaPatcher";
    private static final String PLUGIN_DESC = "Lorg/bukkit/plugin/Plugin;";
    private static final String JAVA_PLUGIN_DESC = "Lorg/bukkit/plugin/java/JavaPlugin;";

    public PlayerGetTargetBlockTransformer(Logger logger, String relocatedPatcherPath) {
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new PlayerGetTargetBlockVisitor(next, relocatedPatcherPath + "/" + PATCHER_CLASS);
    }

    private static class PlayerGetTargetBlockVisitor extends ClassVisitor {
        private String className;
        private String pluginFieldName;
        private String pluginFieldDesc;
        private final String patcherPath;

        public PlayerGetTargetBlockVisitor(ClassVisitor cv, String patcherPath) {
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
                return new PlayerGetTargetBlockAdviceAdapter(mv, access, name, desc, patcherPath, className, pluginFieldName, pluginFieldDesc);
            }
            return mv;
        }
    }

    private static class PlayerGetTargetBlockAdviceAdapter extends AdviceAdapter {
        private final String patcherOwner;
        private final String pluginFieldOwner;
        private final String pluginFieldName;
        private final Type pluginFieldType;

        protected PlayerGetTargetBlockAdviceAdapter(MethodVisitor mv, int access, String name, String desc,
                                                      String patcherPath, String owner, String pfn, String pfd) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherOwner = patcherPath;
            this.pluginFieldOwner = owner;
            this.pluginFieldName = pfn;
            this.pluginFieldType = Type.getType(pfd);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if (isInterface && owner.equals("org/bukkit/entity/Player") && name.equals("getTargetBlock")
                    && desc.equals("(Ljava/util/Set;I)Lorg/bukkit/block/Block;")) {

                // Pop arguments (Set, int) and the Player instance
                int maxDistance = newLocal(Type.INT_TYPE);
                storeLocal(maxDistance);
                int transparent = newLocal(Type.getType(java.util.Set.class));
                storeLocal(transparent);
                int player = newLocal(Type.getType("Lorg/bukkit/entity/Player;"));
                storeLocal(player);

                // Inject plugin instance
                injectPluginInstance();

                // Reload Player, Set, and int
                loadLocal(player);
                loadLocal(transparent);
                loadLocal(maxDistance);

                // Call the static patcher method
                super.visitMethodInsn(INVOKESTATIC, patcherOwner, "safeGetTargetBlock",
                        "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Player;Ljava/util/Set;I)Lorg/bukkit/block/Block;", false);
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
