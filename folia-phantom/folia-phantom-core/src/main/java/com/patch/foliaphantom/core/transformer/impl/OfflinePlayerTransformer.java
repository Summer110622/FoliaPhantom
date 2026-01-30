/*
 * Folia Phantom - Offline Player Transformer
 *
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

import java.util.logging.Logger;

/**
 * Transforms Bukkit.getOfflinePlayer and Server.getOfflinePlayer calls to use
 * the thread-safe FoliaPatcher implementation.
 */
public class OfflinePlayerTransformer implements ClassTransformer {
    private final Logger logger;
    private final String relocatedPatcherPath;

    public OfflinePlayerTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor parent) {
        return new OfflinePlayerVisitor(parent);
    }

    private class OfflinePlayerVisitor extends ClassVisitor {
        public OfflinePlayerVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            return new OfflinePlayerMethodVisitor(mv, access, name, desc);
        }
    }

    private class OfflinePlayerMethodVisitor extends AdviceAdapter {
        protected OfflinePlayerMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
            super(Opcodes.ASM9, mv, access, name, desc);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            // Handle Bukkit.getOfflinePlayer(...) [Static]
            if (opcode == INVOKESTATIC && owner.equals("org/bukkit/Bukkit") && name.equals("getOfflinePlayer")) {
                if (desc.equals("(Ljava/lang/String;)Lorg/bukkit/OfflinePlayer;") ||
                    desc.equals("(Ljava/util/UUID;)Lorg/bukkit/OfflinePlayer;")) {

                    logger.fine("[OfflinePlayerTransformer] Redirecting Bukkit.getOfflinePlayer call");
                    super.visitMethodInsn(INVOKESTATIC, relocatedPatcherPath + "/FoliaPatcher", "safeGetOfflinePlayer", desc, false);
                    return;
                }
            }

            // Handle Server.getOfflinePlayer(...) [Interface/Virtual]
            if ((opcode == INVOKEINTERFACE || opcode == INVOKEVIRTUAL) &&
                (owner.equals("org/bukkit/Server") || owner.equals("org/bukkit/Bukkit")) &&
                name.equals("getOfflinePlayer")) {

                if (desc.equals("(Ljava/lang/String;)Lorg/bukkit/OfflinePlayer;") ||
                    desc.equals("(Ljava/util/UUID;)Lorg/bukkit/OfflinePlayer;")) {

                    logger.fine("[OfflinePlayerTransformer] Redirecting Server.getOfflinePlayer call");

                    // Stack: [server, arg]
                    if (desc.equals("(Ljava/lang/String;)Lorg/bukkit/OfflinePlayer;")) {
                        int nameLocal = newLocal(Type.getType(String.class));
                        storeLocal(nameLocal);
                        pop(); // pop server
                        loadLocal(nameLocal);
                    } else {
                        int uuidLocal = newLocal(Type.getType(java.util.UUID.class));
                        storeLocal(uuidLocal);
                        pop(); // pop server
                        loadLocal(uuidLocal);
                    }

                    super.visitMethodInsn(INVOKESTATIC, relocatedPatcherPath + "/FoliaPatcher", "safeGetOfflinePlayer", desc, false);
                    return;
                }
            }

            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }
    }
}
