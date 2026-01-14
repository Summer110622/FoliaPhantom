/*
 * Folia Phantom - EventHandler Transformer
 *
 * Copyright (c) 2024 Marv
 * Licensed under MARV License
 */

package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import java.util.logging.Logger;

public class EventHandlerTransformer implements ClassTransformer {

    private static final String EVENT_HANDLER_DESC = "Lorg/bukkit/event/EventHandler;";
    private static final String CANCELLABLE_INTERNAL_NAME = "org/bukkit/event/Cancellable";
    private final Logger logger;
    private final String relocatedPatcherPath;

    public EventHandlerTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor parent) {
        return new EventHandlerClassVisitor(parent);
    }

    private class EventHandlerClassVisitor extends ClassVisitor {
        public EventHandlerClassVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM9, classVisitor);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new EventHandlerMethodVisitor(mv, access, name, descriptor);
        }
    }

    private class EventHandlerMethodVisitor extends AdviceAdapter {
        private boolean isEventHandler = false;
        private boolean isMonitorPriority = false;
        private boolean ignoreCancelled = false; // Defaults to false, as per Bukkit's annotation

        protected EventHandlerMethodVisitor(MethodVisitor methodVisitor, int access, String name, String descriptor) {
            super(Opcodes.ASM9, methodVisitor, access, name, descriptor);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (EVENT_HANDLER_DESC.equals(descriptor)) {
                isEventHandler = true;
                return new EventHandlerAnnotationVisitor(super.visitAnnotation(descriptor, visible));
            }
            return super.visitAnnotation(descriptor, visible);
        }

        @Override
        protected void onMethodEnter() {
            // Inject check only if it's a handler, not MONITOR priority, and doesn't ignore cancelled events
            if (isEventHandler && !isMonitorPriority && !ignoreCancelled) {
                Type[] args = Type.getArgumentTypes(methodDesc);
                if (args.length == 1) {
                    Label skipReturnLabel = new Label();

                    // Check if the event is an instance of Cancellable
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitTypeInsn(INSTANCEOF, CANCELLABLE_INTERNAL_NAME);
                    mv.visitJumpInsn(IFEQ, skipReturnLabel);

                    // Check if event.isCancelled() is true
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitTypeInsn(CHECKCAST, CANCELLABLE_INTERNAL_NAME);
                    mv.visitMethodInsn(INVOKEINTERFACE, CANCELLABLE_INTERNAL_NAME, "isCancelled", "()Z", true);
                    mv.visitJumpInsn(IFEQ, skipReturnLabel);

                    // If so, return
                    mv.visitInsn(RETURN);

                    mv.visitLabel(skipReturnLabel);
                    logger.fine("Injected cancellation check into: " + getName());
                }
            }
        }

        private class EventHandlerAnnotationVisitor extends AnnotationVisitor {
            public EventHandlerAnnotationVisitor(AnnotationVisitor annotationVisitor) {
                super(Opcodes.ASM9, annotationVisitor);
            }

            @Override
            public void visitEnum(String name, String descriptor, String value) {
                if ("priority".equals(name) && "MONITOR".equals(value)) {
                    isMonitorPriority = true;
                }
                super.visitEnum(name, descriptor, value);
            }

            @Override
            public void visit(String name, Object value) {
                if ("ignoreCancelled".equals(name) && value instanceof Boolean) {
                    EventHandlerMethodVisitor.this.ignoreCancelled = (Boolean) value;
                }
                super.visit(name, value);
            }
        }
    }
}
