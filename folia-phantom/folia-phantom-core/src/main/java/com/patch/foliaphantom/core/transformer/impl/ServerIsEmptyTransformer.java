package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import java.util.logging.Logger;

public class ServerIsEmptyTransformer implements ClassTransformer {

    private final String relocatedPatcherPath;
    private final Logger logger;

    public ServerIsEmptyTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor parent) {
        return new ServerIsEmptyVisitor(parent);
    }

    private class ServerIsEmptyVisitor extends ClassVisitor {
        public ServerIsEmptyVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM9, classVisitor);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new IsEmptyMethodVisitor(mv);
        }
    }

    private class IsEmptyMethodVisitor extends MethodVisitor {
        private String getOnlinePlayersOwner = null;
        private int getOnlinePlayersOpcode;
        private boolean getOnlinePlayersIsInterface;

        public IsEmptyMethodVisitor(MethodVisitor methodVisitor) {
            super(Opcodes.ASM9, methodVisitor);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if ((opcode == Opcodes.INVOKEINTERFACE || opcode == Opcodes.INVOKESTATIC) &&
                (owner.equals("org/bukkit/Server") || owner.equals("org/bukkit/Bukkit")) &&
                name.equals("getOnlinePlayers") &&
                descriptor.equals("()Ljava/util/Collection;")) {

                this.getOnlinePlayersOwner = owner;
                this.getOnlinePlayersOpcode = opcode;
                this.getOnlinePlayersIsInterface = isInterface;
                // Don't write the instruction yet, wait to see if the next one is isEmpty()
                return;
            }

            if (this.getOnlinePlayersOwner != null &&
                opcode == Opcodes.INVOKEINTERFACE &&
                (owner.equals("java/util/Collection") || owner.equals("java/util/List")) &&
                name.equals("isEmpty") &&
                descriptor.equals("()Z")) {

                // Pattern matched: ...getOnlinePlayers().isEmpty()
                // The object/instance for getOnlinePlayers is already on the stack.
                // We need to pop it if it's an interface call.
                if (getOnlinePlayersOpcode == Opcodes.INVOKEINTERFACE) {
                    super.visitInsn(Opcodes.POP);
                }

                // Now, call our static helper which takes a Plugin instance.
                // We assume ALOAD 0 is the plugin instance.
                super.visitVarInsn(Opcodes.ALOAD, 0);
                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        relocatedPatcherPath + "/FoliaPatcher",
                        "isPlayerListEmpty",
                        "(Lorg/bukkit/plugin/Plugin;)Z",
                        false);

                this.getOnlinePlayersOwner = null; // Reset state
                return;
            }

            // If we have a pending getOnlinePlayers call, but the current instruction is not isEmpty(),
            // we must replay the getOnlinePlayers instruction before processing the current one.
            if (this.getOnlinePlayersOwner != null) {
                super.visitMethodInsn(this.getOnlinePlayersOpcode, this.getOnlinePlayersOwner, "getOnlinePlayers", "()Ljava/util/Collection;", this.getOnlinePlayersIsInterface);
                this.getOnlinePlayersOwner = null; // Reset state
            }

            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }
    }
}
