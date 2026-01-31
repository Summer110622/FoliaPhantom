/*
 * Folia Phantom - Server#getOnlinePlayers() Transformer
 *
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.AdviceAdapter;

import java.util.logging.Logger;

/**
 * Transforms {@code Server#getOnlinePlayers()} calls to a thread-safe equivalent
 * in {@code FoliaPatcher}. This ensures that plugins accessing the player list
 * from asynchronous threads do not cause concurrency issues.
 */
public class ServerGetOnlinePlayersTransformer implements ClassTransformer {

    private final Logger logger;
    private final String relocatedPatcherPath;
    private boolean hasTransformed = false;

    public ServerGetOnlinePlayersTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor classVisitor) {
        return new ServerGetOnlinePlayersClassVisitor(classVisitor);
    }

    /**
     * Checks if the transformer has made any changes to the class.
     *
     * @return {@code true} if a transformation was applied, {@code false} otherwise.
     */
    public boolean hasTransformed() {
        return hasTransformed;
    }

    private class ServerGetOnlinePlayersClassVisitor extends ClassVisitor {
        private String className;
        private boolean isJavaPlugin;
        private String pluginField;
        private String pluginFieldType;

        public ServerGetOnlinePlayersClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name;
            // Simplified check for any class that extends JavaPlugin, directly or indirectly.
            // A more robust solution might involve parsing class hierarchy, but this is sufficient for most cases.
            if ("org/bukkit/plugin/java/JavaPlugin".equals(superName)) {
                this.isJavaPlugin = true;
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public org.objectweb.asm.FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            // Find a field of type Plugin or a subclass, to be used as a context for the static call.
            if (descriptor.equals("Lorg/bukkit/plugin/Plugin;") || descriptor.equals("Lorg/bukkit/plugin/java/JavaPlugin;")) {
                if (this.pluginField == null) { // Prefer the first declared plugin field
                    this.pluginField = name;
                    this.pluginFieldType = descriptor;
                }
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (isJavaPlugin || pluginField != null) {
                return new GetOnlinePlayersMethodVisitor(mv, access, name, descriptor);
            }
            return mv;
        }

        private class GetOnlinePlayersMethodVisitor extends AdviceAdapter {
            GetOnlinePlayersMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
                super(Opcodes.ASM9, mv, access, name, desc);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                boolean isServerGetOnlinePlayers = opcode == Opcodes.INVOKEINTERFACE
                    && "org/bukkit/Server".equals(owner)
                    && "getOnlinePlayers".equals(name)
                    && "()Ljava/util/Collection;".equals(desc);

                boolean isBukkitGetOnlinePlayers = opcode == Opcodes.INVOKESTATIC
                    && "org/bukkit/Bukkit".equals(owner)
                    && "getOnlinePlayers".equals(name)
                    && "()Ljava/util/Collection;".equals(desc);

                if (isServerGetOnlinePlayers) {
                    logger.fine("[FoliaPhantom] Transforming Server#getOnlinePlayers call in " + className);
                    pop(); // Pop the Server instance from the stack
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        relocatedPatcherPath + "/FoliaPatcher",
                        "_o",
                        "()Ljava/util/Collection;",
                        false
                    );
                    hasTransformed = true;
                } else if (isBukkitGetOnlinePlayers) {
                    logger.fine("[FoliaPhantom] Transforming Bukkit#getOnlinePlayers call in " + className);
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        relocatedPatcherPath + "/FoliaPatcher",
                        "_o",
                        "()Ljava/util/Collection;",
                        false
                    );
                    hasTransformed = true;
                } else {
                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                }
            }

            private void loadPluginInstance() {
                if (isJavaPlugin) {
                    visitVarInsn(ALOAD, 0); // this
                } else if (pluginField != null) {
                    visitVarInsn(ALOAD, 0); // this
                    visitFieldInsn(GETFIELD, className, pluginField, pluginFieldType);
                } else {
                    // This should not be reached due to the check in visitMethod.
                    throw new IllegalStateException("Cannot transform getOnlinePlayers call in " + className + ": No plugin instance found.");
                }
            }
        }
    }
}
