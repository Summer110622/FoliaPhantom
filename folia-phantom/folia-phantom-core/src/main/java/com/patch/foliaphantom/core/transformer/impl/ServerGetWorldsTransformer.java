/*
 * Folia Phantom - Server#getWorlds() Transformer
 *
 * Copyright (c) 2024 Marv
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
 * Transforms {@code Server#getWorlds()} calls to a thread-safe equivalent
 * in {@code FoliaPatcher}. This ensures that plugins accessing the world list
 * from asynchronous threads do not cause concurrency issues. It also handles
 * the static {@code Bukkit#getWorlds()} call.
 */
public class ServerGetWorldsTransformer implements ClassTransformer {

    private final Logger logger;
    private final String relocatedPatcherPath;
    private boolean hasTransformed = false;

    public ServerGetWorldsTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor classVisitor) {
        return new ServerGetWorldsClassVisitor(classVisitor);
    }

    public boolean hasTransformed() {
        return hasTransformed;
    }

    private class ServerGetWorldsClassVisitor extends ClassVisitor {
        private String className;
        private boolean isJavaPlugin;
        private String pluginField;
        private String pluginFieldType;

        public ServerGetWorldsClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name;
            // A simple check for direct subclasses of JavaPlugin. A more robust check might
            // involve checking the entire class hierarchy, but this is often sufficient.
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
            return new GetWorldsMethodVisitor(mv, access, name, descriptor);
        }

        private class GetWorldsMethodVisitor extends AdviceAdapter {
            GetWorldsMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
                super(Opcodes.ASM9, mv, access, name, desc);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                // Check if the call is to Server.getWorlds() or Bukkit.getWorlds()
                boolean isServerGetWorlds = (opcode == Opcodes.INVOKEINTERFACE && "org/bukkit/Server".equals(owner) && "getWorlds".equals(name) && "()Ljava/util/List;".equals(desc));
                boolean isBukkitGetWorlds = (opcode == Opcodes.INVOKESTATIC && "org/bukkit/Bukkit".equals(owner) && "getWorlds".equals(name) && "()Ljava/util/List;".equals(desc));

                if (isServerGetWorlds || isBukkitGetWorlds) {
                    logger.fine("[FoliaPhantom] Transforming " + owner + "#" + name + " call in " + className);

                    if (isServerGetWorlds) {
                        // The stack has a Server instance on it, which we don't need for the static call.
                        pop(); // Pop the Server instance
                    }

                    // Call the static FoliaPatcher method.
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        relocatedPatcherPath + "/FoliaPatcher",
                        "_w",
                        "()Ljava/util/List;",
                        false
                    );
                    ServerGetWorldsTransformer.this.hasTransformed = true;
                } else {
                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                }
            }
        }
    }
}