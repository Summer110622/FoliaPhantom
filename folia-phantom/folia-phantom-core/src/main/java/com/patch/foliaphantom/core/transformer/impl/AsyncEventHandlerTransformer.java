/*
 * Folia Phantom - Async EventHandler Transformer
 *
 * Copyright (c) 2024 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public class AsyncEventHandlerTransformer implements ClassTransformer {

    private static final String EVENT_HANDLER_DESC = "Lorg/bukkit/event/EventHandler;";
    private static final String PLUGIN_DESC = "Lorg/bukkit/plugin/Plugin;";
    private static final String JAVA_PLUGIN_DESC = "Lorg/bukkit/plugin/java/JavaPlugin;";
    private static final String FOLIA_PATCHER_INTERNAL_NAME = "com/patch/foliaphantom/core/patcher/FoliaPatcher";

    private final Logger logger;
    private final String relocatedPatcherPath;
    private final Set<String> asyncEventHandlers;

    public AsyncEventHandlerTransformer(Logger logger, String relocatedPatcherPath, Set<String> asyncEventHandlers) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
        this.asyncEventHandlers = asyncEventHandlers != null ? asyncEventHandlers : Collections.emptySet();
    }

    public byte[] transform(byte[] classBytes) {
        if (asyncEventHandlers.isEmpty()) {
            return classBytes;
        }

        ClassReader classReader = new ClassReader(classBytes);
        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, ClassReader.EXPAND_FRAMES);

        if ((classNode.access & Opcodes.ACC_INTERFACE) != 0) {
            return classBytes;
        }

        FieldNode pluginField = findPluginField(classNode);
        boolean isPluginClass = isPluginClass(classNode);

        if (pluginField == null && !isPluginClass) {
            return classBytes; // No plugin instance found, cannot transform.
        }

        boolean transformed = false;
        List<MethodNode> originalMethods = new ArrayList<>(classNode.methods);

        for (MethodNode methodNode : originalMethods) {
            String fullMethodName = classNode.name.replace('/', '.') + "#" + methodNode.name;
            if (asyncEventHandlers.contains(fullMethodName) && isEventHandler(methodNode)) {
                logger.info("Transforming designated async event handler: " + fullMethodName);
                transformMethod(classNode, methodNode, pluginField, isPluginClass);
                transformed = true;
            }
        }

        if (transformed) {
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            classNode.accept(classWriter);
            return classWriter.toByteArray();
        }

        return classBytes;
    }

    private void transformMethod(ClassNode classNode, MethodNode originalMethod, FieldNode pluginField, boolean isPluginClass) {
        String asyncMethodName = originalMethod.name + "$foliaAsync$";
        int access = Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC;

        // 1. Create a new private method and move the original code into it.
        MethodNode asyncMethod = new MethodNode(access, asyncMethodName, originalMethod.desc, originalMethod.signature, originalMethod.exceptions.toArray(new String[0]));
        originalMethod.accept(asyncMethod);
        classNode.methods.add(asyncMethod);

        // 2. Clear the original method's body.
        originalMethod.instructions.clear();
        originalMethod.tryCatchBlocks.clear();
        originalMethod.localVariables.clear();

        // 3. Generate the new body for the original method.
        MethodVisitor mv = originalMethod;
        mv.visitCode();

        // Load plugin instance onto the stack
        if (isPluginClass) {
            mv.visitVarInsn(Opcodes.ALOAD, 0); // this
        } else {
            mv.visitVarInsn(Opcodes.ALOAD, 0); // this
            mv.visitFieldInsn(Opcodes.GETFIELD, classNode.name, pluginField.name, pluginField.desc);
        }

        // Load 'this' and the event argument for the lambda
        mv.visitVarInsn(Opcodes.ALOAD, 0); // this (listener instance)
        mv.visitVarInsn(Opcodes.ALOAD, 1); // event object

        String invokedynamicDescriptor = Type.getMethodDescriptor(Type.getType(Runnable.class), Type.getObjectType(classNode.name), Type.getArgumentTypes(originalMethod.desc)[0]);

        Handle bootstrapMethodHandle = new Handle(
                Opcodes.H_INVOKESTATIC,
                "java/lang/invoke/LambdaMetafactory",
                "metafactory",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                false
        );

        Handle implementationMethodHandle = new Handle(
                Opcodes.H_INVOKESPECIAL,
                classNode.name,
                asyncMethodName,
                originalMethod.desc,
                false
        );

        mv.visitInvokeDynamicInsn(
            "run",
            invokedynamicDescriptor,
            bootstrapMethodHandle,
            Type.getType("()V"),
            implementationMethodHandle,
            Type.getType("()V")
        );

        // Call FoliaPatcher.executeAsync(Plugin, Runnable)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, relocatedPatcherPath, "executeAsync", "(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;)V", false);

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0); // Will be recomputed by ClassWriter
        mv.visitEnd();
    }

    private boolean isPluginClass(ClassNode classNode) {
        final String JAVA_PLUGIN_INTERNAL_NAME = "org/bukkit/plugin/java/JavaPlugin";
        final String PLUGIN_INTERNAL_NAME = "org/bukkit/plugin/Plugin";

        if (classNode.superName != null && classNode.superName.equals(JAVA_PLUGIN_INTERNAL_NAME)) {
            return true;
        }
        if (classNode.interfaces != null) {
            for (String iface : classNode.interfaces) {
                if (iface.equals(PLUGIN_INTERNAL_NAME)) {
                    return true;
                }
            }
        }
        return false;
    }

    private FieldNode findPluginField(ClassNode classNode) {
        // This implementation does not check for inherited fields from superclasses.
        // This is a known limitation that covers the vast majority of plugin listener patterns.
        for (FieldNode field : classNode.fields) {
            if (field.desc.equals(PLUGIN_DESC) || field.desc.equals(JAVA_PLUGIN_DESC)) {
                return field;
            }
        }
        return null;
    }

    private boolean isEventHandler(MethodNode methodNode) {
        if (methodNode.visibleAnnotations == null) {
            return false;
        }

        for (AnnotationNode annotation : methodNode.visibleAnnotations) {
            if (annotation.desc.equals(EVENT_HANDLER_DESC)) {
                if (methodNode.desc.startsWith("(Lorg/bukkit/event/player/AsyncPlayerPreLoginEvent;)") ||
                    methodNode.desc.startsWith("(Lorg/bukkit/event/server/ServerListPingEvent;)")) {
                    return false; // Already async events
                }

                // Don't transform high-priority or monitoring events to avoid breaking logic
                if (annotation.values != null) {
                    for (int i = 0; i < annotation.values.size(); i += 2) {
                        String name = (String) annotation.values.get(i);
                        Object value = annotation.values.get(i + 1);
                        if (name.equals("priority")) {
                            String[] enumValues = (String[]) value;
                            String priority = enumValues[1];
                            if (priority.equals("MONITOR") || priority.equals("HIGHEST")) {
                                return false;
                            }
                        }
                    }
                }

                Type[] args = Type.getArgumentTypes(methodNode.desc);
                return args.length == 1 && args[0].getDescriptor().contains("Lorg/bukkit/event/");
            }
        }
        return false;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor parent) {
        // This transformer uses ClassNode and is incompatible with the visitor chain.
        // The transform(byte[]) method should be called directly.
        throw new UnsupportedOperationException("This transformer requires ClassNode processing.");
    }
}