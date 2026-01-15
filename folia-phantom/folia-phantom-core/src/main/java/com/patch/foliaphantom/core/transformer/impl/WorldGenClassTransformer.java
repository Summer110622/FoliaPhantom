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
 * Transforms world creation and generator-related calls for Folia
 * compatibility.
 *
 * <p>
 * This transformer uses {@link AdviceAdapter} to safely redirect calls like
 * {@code Plugin.getDefaultWorldGenerator} and {@code WorldCreator.createWorld}
 * to thread-safe implementations in the {@code FoliaPatcher} runtime. It ensures
 * that plugin-specific calls receive the correct plugin context.
 * </p>
 */
public class WorldGenClassTransformer implements ClassTransformer {
    private final Logger logger;
    private final String relocatedPatcherPath;
    private static final String PATCHER_CLASS = "FoliaPatcher";
    private static final String PLUGIN_OWNER = "org/bukkit/plugin/Plugin";
    private static final String WORLD_CREATOR_DESC = "(Lorg/bukkit/WorldCreator;)Lorg/bukkit/World;";

    public WorldGenClassTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new WorldGenVisitor(next, relocatedPatcherPath + "/" + PATCHER_CLASS);
    }

    private static class WorldGenVisitor extends ClassVisitor {
        private final String patcherPath;

        public WorldGenVisitor(ClassVisitor cv, String patcherPath) {
            super(Opcodes.ASM9, cv);
            this.patcherPath = patcherPath;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            return new WorldGenMethodVisitor(mv, access, name, desc, patcherPath);
        }
    }

    private static class WorldGenMethodVisitor extends AdviceAdapter {
        private final String patcherPath;

        protected WorldGenMethodVisitor(MethodVisitor mv, int access, String name, String desc, String patcherPath) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherPath = patcherPath;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            // Redirect Plugin.getDefaultWorldGenerator
            if ((opcode == INVOKEINTERFACE || opcode == INVOKEVIRTUAL) &&
                    PLUGIN_OWNER.equals(owner) &&
                    "getDefaultWorldGenerator".equals(name)) {

                // Stack: [plugin, worldName, id]
                // The static call needs the same signature, but we change the opcode.
                String newDesc = "(Lorg/bukkit/plugin/Plugin;" + desc.substring(1);
                super.visitMethodInsn(INVOKESTATIC, patcherPath, name, newDesc, false);
                return;
            }

            // Redirect Bukkit.createWorld or WorldCreator.createWorld
            if ("createWorld".equals(name) && WORLD_CREATOR_DESC.equals(desc)) {
                // This is a static call, so the stack is just [worldCreator].
                // We redirect it to our static helper which has the same signature.
                super.visitMethodInsn(INVOKESTATIC, patcherPath, "createWorld", desc, false);
                return;
            }

            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }
    }
}
