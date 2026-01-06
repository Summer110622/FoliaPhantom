/*
 * Folia Phantom - World Access Transformer
 *
 * This transformer intercepts calls to thread-unsafe methods of the org.bukkit.World
 * interface and redirects them to thread-safe static equivalents in FoliaPatcher.
 *
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import java.util.logging.Logger;

/**
 * Transforms calls to {@code org.bukkit.World} methods to their thread-safe
 * Folia equivalents.
 */
public class WorldAccessTransformer implements ClassTransformer {

    private final Logger logger;
    private final String relocatedPatcherPath;

    /**
     * Constructs a new WorldAccessTransformer.
     *
     * @param logger               Logger for diagnostics
     * @param relocatedPatcherPath The relocated path for the FoliaPatcher runtime
     */
    public WorldAccessTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new WorldAccessClassVisitor(next);
    }

    private class WorldAccessClassVisitor extends ClassVisitor {
        public WorldAccessClassVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM9, classVisitor);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new WorldAccessMethodVisitor(mv);
        }
    }

    private class WorldAccessMethodVisitor extends MethodVisitor {
        public WorldAccessMethodVisitor(MethodVisitor methodVisitor) {
            super(Opcodes.ASM9, methodVisitor);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKEINTERFACE && "org/bukkit/World".equals(owner)) {
                String newDescriptor = null;
                boolean transformed = false;

                switch (name) {
                    case "spawnEntity":
                        if ("(Lorg/bukkit/Location;Lorg/bukkit/entity/EntityType;)Lorg/bukkit/entity/Entity;".equals(descriptor)) {
                            newDescriptor = "(Lorg/bukkit/World;Lorg/bukkit/Location;Lorg/bukkit/entity/EntityType;)Lorg/bukkit/entity/Entity;";
                            transformed = true;
                        }
                        break;
                    case "strikeLightning":
                        if ("(Lorg/bukkit/Location;)Lorg/bukkit/entity/LightningStrike;".equals(descriptor)) {
                            newDescriptor = "(Lorg/bukkit/World;Lorg/bukkit/Location;)Lorg/bukkit/entity/LightningStrike;";
                            transformed = true;
                        }
                        break;
                    case "generateTree":
                        if ("(Lorg/bukkit/Location;Lorg/bukkit/TreeType;)Z".equals(descriptor)) {
                            newDescriptor = "(Lorg/bukkit/World;Lorg/bukkit/Location;Lorg/bukkit/TreeType;)Z";
                            transformed = true;
                        }
                        break;
                    default:
                        newDescriptor = null;
                        break;
                }

                if (transformed) {
                    logger.fine("[FoliaPhantom] Transforming World#" + name + " call");
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, relocatedPatcherPath + "/FoliaPatcher", name, newDescriptor, false);
                    return;
                }
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }
    }
}
