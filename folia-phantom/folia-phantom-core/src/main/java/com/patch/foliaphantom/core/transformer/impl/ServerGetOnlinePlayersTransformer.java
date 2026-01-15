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

    public ServerGetOnlinePlayersTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor classVisitor) {
        return new ServerGetOnlinePlayersClassVisitor(classVisitor);
    }

    private class ServerGetOnlinePlayersClassVisitor extends ClassVisitor {
        private String className;
        private boolean isJavaPlugin;
        private String pluginField;
        private String pluginFieldType; // To store descriptor like "Lorg/bukkit/plugin/Plugin;"

        public ServerGetOnlinePlayersClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name;
            this.isJavaPlugin = "org/bukkit/plugin/java/JavaPlugin".equals(superName);
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public org.objectweb.asm.FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (descriptor.equals("Lorg/bukkit/plugin/Plugin;") || descriptor.equals("Lorg/bukkit/plugin/java/JavaPlugin;")) {
                this.pluginField = name;
                this.pluginFieldType = descriptor;
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            // We need a plugin instance to make the safe call. If this class is not a plugin
            // and does not contain a plugin field, we cannot transform it.
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
                if (opcode == Opcodes.INVOKEINTERFACE &&
                    "org/bukkit/Server".equals(owner) &&
                    "getOnlinePlayers".equals(name) &&
                    "()Ljava/util/Collection;".equals(desc)) {

                    logger.fine("[ServerGetOnlinePlayersTransformer] Transforming " + owner + "#" + name + " in " + className);

                    // The original call `Bukkit.getServer().getOnlinePlayers()` leaves a Server instance on the stack.
                    // We need to pop it because our static helper doesn't need it.
                    pop();

                    // Load the plugin instance onto the stack.
                    loadPluginInstance();

                    // Call our static helper method.
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        relocatedPatcherPath + "/FoliaPatcher",
                        "safeGetOnlinePlayers",
                        "(Lorg/bukkit/plugin/Plugin;)Ljava/util/Collection;",
                        false
                    );
                } else {
                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                }
            }

            private void loadPluginInstance() {
                if (isJavaPlugin) {
                    // If the class is a subclass of JavaPlugin, `this` is the plugin instance.
                    visitVarInsn(ALOAD, 0); // this
                } else if (pluginField != null) {
                    // If the class holds a reference to the plugin in a field, load it.
                    visitVarInsn(ALOAD, 0); // this
                    visitFieldInsn(GETFIELD, className, pluginField, pluginFieldType);
                } else {
                    // This case should be prevented by the check in visitMethod.
                    throw new IllegalStateException("Cannot transform getOnlinePlayers call in " + className + ": No plugin instance found.");
                }
            }
        }
    }
}
