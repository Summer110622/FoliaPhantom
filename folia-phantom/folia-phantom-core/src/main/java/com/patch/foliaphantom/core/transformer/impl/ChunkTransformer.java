/*
 * Folia Phantom - Chunk Transformer
 *
 * This transformer intercepts calls to the Bukkit Chunk API, redirecting them
 * to thread-safe FoliaPatcher equivalents to ensure compatibility with
 * Folia's multi-threaded environment.
 *
 * Copyright (c) 2026 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import java.util.logging.Logger;
import java.util.HashMap;
import java.util.Map;

public class ChunkTransformer implements ClassTransformer {

    private static final String CHUNK_OWNER = "org/bukkit/Chunk";

    private final Logger logger;
    private final String relocatedPatcherPath;
    private final Map<String, MethodMapping> methodMappings;

    public ChunkTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
        this.methodMappings = new HashMap<>();

        // Chunk Mappings
        methodMappings.put("getEntities()[Lorg/bukkit/entity/Entity;", new MethodMapping("safeGetEntities", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/Chunk;)[Lorg/bukkit/entity/Entity;"));
        methodMappings.put("getTileEntities()[Lorg/bukkit/block/BlockState;", new MethodMapping("safeGetTileEntities", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/Chunk;)[Lorg/bukkit/block/BlockState;"));
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor parent) {
        return new ChunkClassVisitor(parent);
    }

    private static class MethodMapping {
        final String newName;
        final String newDescriptor;
        MethodMapping(String newName, String newDescriptor) {
            this.newName = newName;
            this.newDescriptor = newDescriptor;
        }
    }

    private class ChunkClassVisitor extends ClassVisitor {
        private String className;
        private String pluginFieldName = null;
        private boolean isPluginClass = false;

        public ChunkClassVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM9, classVisitor);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name;
            if ("org/bukkit/plugin/java/JavaPlugin".equals(superName)) {
                isPluginClass = true;
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (pluginFieldName == null && "Lorg/bukkit/plugin/Plugin;".equals(descriptor)) {
                pluginFieldName = name;
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new ChunkMethodVisitor(mv, access, name, descriptor);
        }

        private class ChunkMethodVisitor extends AdviceAdapter {
            ChunkMethodVisitor(MethodVisitor methodVisitor, int access, String name, String descriptor) {
                super(Opcodes.ASM9, methodVisitor, access, name, descriptor);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                if (opcode != Opcodes.INVOKEINTERFACE || !CHUNK_OWNER.equals(owner)) {
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    return;
                }

                String methodKey = name + descriptor;
                MethodMapping mapping = methodMappings.get(methodKey);

                if (mapping != null) {
                    if (isPluginClass || pluginFieldName != null) {
                        logger.finer("Transforming Chunk call '" + name + "' in " + className);

                        // The 'Chunk' instance is on the stack. We need to insert the 'Plugin' instance before it.
                        // Stack: [..., chunk] -> [..., plugin, chunk]

                        // Store the chunk instance in a local variable
                        int chunkLocal = newLocal(Type.getObjectType(owner));
                        storeLocal(chunkLocal);

                        // Load the plugin instance
                        if (isPluginClass) {
                            loadThis(); // 'this' is the plugin instance
                        } else {
                            loadThis(); // load 'this' of the current class
                            getField(Type.getObjectType(className), pluginFieldName, Type.getType("Lorg/bukkit/plugin/Plugin;"));
                        }

                        // Load the chunk instance back
                        loadLocal(chunkLocal);

                        // Call the static FoliaPatcher method
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, relocatedPatcherPath + "/FoliaPatcher", mapping.newName, mapping.newDescriptor, false);
                    } else {
                        logger.warning("Could not find Plugin field for Chunk transformation in " + className + ". The call to " + name + " will not be transformed.");
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }
                } else {
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }
            }
        }
    }
}
