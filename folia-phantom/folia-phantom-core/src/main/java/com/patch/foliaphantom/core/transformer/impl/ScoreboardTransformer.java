/*
 * Folia Phantom - Scoreboard API Class Transformer
 *
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;

/**
 * Transforms Bukkit Scoreboard API calls into their thread-safe FoliaPatcher equivalents.
 * This covers {@code ScoreboardManager}, {@code Scoreboard}, {@code Objective}, and {@code Team}.
 */
public class ScoreboardTransformer implements ClassTransformer {
    private final String relocatedPatcherPath;
    private static final String PATCHER_CLASS = "FoliaPatcher";
    private static final String PLUGIN_DESC = "Lorg/bukkit/plugin/Plugin;";
    private static final String JAVA_PLUGIN_DESC = "Lorg/bukkit/plugin/java/JavaPlugin;";

    public ScoreboardTransformer(String relocatedPatcherPath) {
        this.relocatedPatcherPath = relocatedPatcherPath;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new ScoreboardVisitor(next, relocatedPatcherPath + "/" + PATCHER_CLASS);
    }

    private static class ScoreboardVisitor extends ClassVisitor {
        private String className;
        private String pluginFieldName;
        private String pluginFieldDesc;
        private boolean isSubclassOfJavaPlugin;
        private final String patcherPath;

        public ScoreboardVisitor(ClassVisitor cv, String patcherPath) {
            super(Opcodes.ASM9, cv);
            this.patcherPath = patcherPath;
        }

        @Override
        public void visit(int version, int access, String name, String sig, String superName, String[] interfaces) {
            this.className = name;
            this.isSubclassOfJavaPlugin = "org/bukkit/plugin/java/JavaPlugin".equals(superName);
            super.visit(version, access, name, sig, superName, interfaces);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String sig, Object val) {
            if (pluginFieldName == null && (desc.equals(PLUGIN_DESC) || desc.equals(JAVA_PLUGIN_DESC))) {
                this.pluginFieldName = name;
                this.pluginFieldDesc = desc;
            }
            return super.visitField(access, name, desc, sig, val);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            if (pluginFieldName != null || isSubclassOfJavaPlugin) {
                return new ScoreboardMethodVisitor(mv, access, name, desc, patcherPath, className,
                        pluginFieldName, pluginFieldDesc, isSubclassOfJavaPlugin);
            }
            return mv;
        }
    }

    private static class ScoreboardMethodVisitor extends AdviceAdapter {
        private final String patcherOwner;
        private final String pluginFieldOwner;
        private final String pluginFieldName;
        private final Type pluginFieldType;
        private final boolean isPluginClass;

        protected ScoreboardMethodVisitor(MethodVisitor mv, int access, String name, String desc,
                                          String patcherPath, String owner, String pfn, String pfd, boolean isPlugin) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.patcherOwner = patcherPath;
            this.pluginFieldOwner = owner;
            this.pluginFieldName = pfn;
            this.pluginFieldType = pfd != null ? Type.getType(pfd) : null;
            this.isPluginClass = isPlugin;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if (tryHandle(owner, name, desc)) {
                return;
            }
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }

        private boolean tryHandle(String owner, String name, String desc) {
            switch (owner) {
                case "org/bukkit/scoreboard/ScoreboardManager":
                    // This is handled by Bukkit.getScoreboardManager() which returns the main scoreboard.
                    // We don't transform getNewScoreboard as it's less common and harder to track.
                    break;
                case "org/bukkit/scoreboard/Scoreboard":
                    if ("registerNewObjective".equals(name) && "(Ljava/lang/String;Ljava/lang/String;)Lorg/bukkit/scoreboard/Objective;".equals(desc)) {
                        return transform(2, "safeRegisterNewObjective", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Scoreboard;Ljava/lang/String;Ljava/lang/String;)Lorg/bukkit/scoreboard/Objective;");
                    }
                    if ("registerNewTeam".equals(name) && "(Ljava/lang/String;)Lorg/bukkit/scoreboard/Team;".equals(desc)) {
                        return transform(1, "safeRegisterNewTeam", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Scoreboard;Ljava/lang/String;)Lorg/bukkit/scoreboard/Team;");
                    }
                    if ("clearSlot".equals(name) && "(Lorg/bukkit/scoreboard/DisplaySlot;)V".equals(desc)) {
                        return transform(1, "safeClearSlot", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Scoreboard;Lorg/bukkit/scoreboard/DisplaySlot;)V");
                    }
                    if ("resetScores".equals(name) && "(Ljava/lang/String;)V".equals(desc)) {
                        return transform(1, "safeResetScores", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Scoreboard;Ljava/lang/String;)V");
                    }
                    if ("getObjective".equals(name) && "(Ljava/lang/String;)Lorg/bukkit/scoreboard/Objective;".equals(desc)) {
                        return transform(1, "safeGetObjective", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Scoreboard;Ljava/lang/String;)Lorg/bukkit/scoreboard/Objective;");
                    }
                    if ("getObjectivesByCriteria".equals(name) && "(Ljava/lang/String;)Ljava/util/Set;".equals(desc)) {
                        return transform(1, "safeGetObjectivesByCriteria", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Scoreboard;Ljava/lang/String;)Ljava/util/Set;");
                    }
                    if ("getObjectives".equals(name) && "()Ljava/util/Set;".equals(desc)) {
                        return transform(0, "safeGetObjectives", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Scoreboard;)Ljava/util/Set;");
                    }
                    if ("getEntries".equals(name) && "()Ljava/util/Set;".equals(desc)) {
                        return transform(0, "safeGetEntries", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Scoreboard;)Ljava/util/Set;");
                    }
                    if ("getTeam".equals(name) && "(Ljava/lang/String;)Lorg/bukkit/scoreboard/Team;".equals(desc)) {
                        return transform(1, "safeGetTeam", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Scoreboard;Ljava/lang/String;)Lorg/bukkit/scoreboard/Team;");
                    }
                     if ("getTeams".equals(name) && "()Ljava/util/Set;".equals(desc)) {
                        return transform(0, "safeGetTeams", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Scoreboard;)Ljava/util/Set;");
                    }
                    break;
                case "org/bukkit/scoreboard/Objective":
                    if ("getScore".equals(name) && "(Ljava/lang/String;)Lorg/bukkit/scoreboard/Score;".equals(desc)) {
                        return transform(1, "safeGetScore", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Objective;Ljava/lang/String;)Lorg/bukkit/scoreboard/Score;");
                    }
                    if ("unregister".equals(name) && "()V".equals(desc)) {
                        return transform(0, "safeUnregisterObjective", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Objective;)V");
                    }
                     if ("setDisplayName".equals(name) && "(Ljava/lang/String;)V".equals(desc)) {
                        return transform(1, "safeSetDisplayName", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Objective;Ljava/lang/String;)V");
                    }
                    break;
                case "org/bukkit/scoreboard/Team":
                    if ("addEntry".equals(name) && "(Ljava/lang/String;)V".equals(desc)) {
                        return transform(1, "safeAddEntry", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Team;Ljava/lang/String;)V");
                    }
                    if ("removeEntry".equals(name) && "(Ljava/lang/String;)Z".equals(desc)) {
                        return transform(1, "safeRemoveEntry", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Team;Ljava/lang/String;)Z");
                    }
                    if ("unregister".equals(name) && "()V".equals(desc)) {
                        return transform(0, "safeUnregisterTeam", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Team;)V");
                    }
                    if ("setPrefix".equals(name) && "(Ljava/lang/String;)V".equals(desc)) {
                        return transform(1, "safeSetPrefix", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Team;Ljava/lang/String;)V");
                    }
                    if ("setSuffix".equals(name) && "(Ljava/lang/String;)V".equals(desc)) {
                        return transform(1, "safeSetSuffix", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Team;Ljava/lang/String;)V");
                    }
                    if ("getEntries".equals(name) && "()Ljava/util/Set;".equals(desc)) {
                        return transform(0, "safeGetTeamEntries", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Team;)Ljava/util/Set;");
                    }
                    if ("getPlayers".equals(name) && "()Ljava/util/Set;".equals(desc)) {
                        return transform(0, "safeGetPlayers", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Team;)Ljava/util/Set;");
                    }
                    if ("getSize".equals(name) && "()I".equals(desc)) {
                        return transform(0, "safeGetSize", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Team;)I");
                    }
                    break;
                case "org/bukkit/scoreboard/Score":
                    if ("setScore".equals(name) && "(I)V".equals(desc)) {
                        return transform(1, "safeSetScore", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Score;I)V");
                    }
                    break;
            }
            return false;
        }

        private boolean transform(int argCount, String newName, String newDesc) {
            Type[] argTypes = Type.getArgumentTypes(newDesc);
            int[] locals = new int[argCount + 1];
            for (int i = argCount; i >= 0; i--) {
                locals[i] = newLocal(argTypes[i + 1]);
                storeLocal(locals[i]);
            }

            injectPluginInstance();

            for (int i = 0; i <= argCount; i++) {
                loadLocal(locals[i]);
            }

            super.visitMethodInsn(INVOKESTATIC, patcherOwner, newName, newDesc, false);
            return true;
        }

        private void injectPluginInstance() {
            if (isPluginClass) {
                loadThis();
            } else {
                loadThis();
                getField(Type.getObjectType(pluginFieldOwner), pluginFieldName, pluginFieldType);
            }
        }
    }
}
