/*
 * Folia Phantom - Scoreboard Transformer
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
import java.util.HashMap;
import java.util.Map;

public class ScoreboardTransformer implements ClassTransformer {

    private static final String SCOREBOARD_OWNER = "org/bukkit/scoreboard/Scoreboard";
    private static final String TEAM_OWNER = "org/bukkit/scoreboard/Team";
    private static final String OBJECTIVE_OWNER = "org/bukkit/scoreboard/Objective";
    private static final String SCORE_OWNER = "org/bukkit/scoreboard/Score";
    private static final String PATCHER_CLASS = "FoliaPatcher";

    private final Logger logger;
    private final String relocatedPatcherPath;
    private final Map<String, Map<String, MethodMapping>> methodMappings;

    public ScoreboardTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath + "/" + PATCHER_CLASS;
        this.methodMappings = new HashMap<>();

        // Scoreboard Mappings
        Map<String, MethodMapping> scoreboardMap = new HashMap<>();
        scoreboardMap.put("registerNewObjective(Ljava/lang/String;Ljava/lang/String;)Lorg/bukkit/scoreboard/Objective;", new MethodMapping("safeRegisterNewObjective", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Scoreboard;Ljava/lang/String;Ljava/lang/String;)Lorg/bukkit/scoreboard/Objective;"));
        scoreboardMap.put("registerNewTeam(Ljava/lang/String;)Lorg/bukkit/scoreboard/Team;", new MethodMapping("safeRegisterNewTeam", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Scoreboard;Ljava/lang/String;)Lorg/bukkit/scoreboard/Team;"));
        scoreboardMap.put("resetScores(Ljava/lang/String;)V", new MethodMapping("safeResetScores", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Scoreboard;Ljava/lang/String;)V"));
        scoreboardMap.put("clearSlot(Lorg/bukkit/scoreboard/DisplaySlot;)V", new MethodMapping("safeClearSlot", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Scoreboard;Lorg/bukkit/scoreboard/DisplaySlot;)V"));
        scoreboardMap.put("getObjective(Ljava/lang/String;)Lorg/bukkit/scoreboard/Objective;", new MethodMapping("safeGetObjective", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Scoreboard;Ljava/lang/String;)Lorg/bukkit/scoreboard/Objective;"));
        scoreboardMap.put("getObjectivesByCriteria(Ljava/lang/String;)Ljava/util/Set;", new MethodMapping("safeGetObjectivesByCriteria", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Scoreboard;Ljava/lang/String;)Ljava/util/Set;"));
        scoreboardMap.put("getObjectives()Ljava/util/Set;", new MethodMapping("safeGetObjectives", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Scoreboard;)Ljava/util/Set;"));
        scoreboardMap.put("getEntries()Ljava/util/Set;", new MethodMapping("safeGetEntries", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Scoreboard;)Ljava/util/Set;"));
        scoreboardMap.put("getTeam(Ljava/lang/String;)Lorg/bukkit/scoreboard/Team;", new MethodMapping("safeGetTeam", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Scoreboard;Ljava/lang/String;)Lorg/bukkit/scoreboard/Team;"));
        scoreboardMap.put("getTeams()Ljava/util/Set;", new MethodMapping("safeGetTeams", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Scoreboard;)Ljava/util/Set;"));
        methodMappings.put(SCOREBOARD_OWNER, scoreboardMap);

        // Team Mappings
        Map<String, MethodMapping> teamMap = new HashMap<>();
        teamMap.put("addEntry(Ljava/lang/String;)V", new MethodMapping("safeAddEntry", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Team;Ljava/lang/String;)V"));
        teamMap.put("removeEntry(Ljava/lang/String;)Z", new MethodMapping("safeRemoveEntry", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Team;Ljava/lang/String;)Z"));
        teamMap.put("setPrefix(Ljava/lang/String;)V", new MethodMapping("safeSetPrefix", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Team;Ljava/lang/String;)V"));
        teamMap.put("setSuffix(Ljava/lang/String;)V", new MethodMapping("safeSetSuffix", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Team;Ljava/lang/String;)V"));
        teamMap.put("unregister()V", new MethodMapping("safeUnregisterTeam", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Team;)V"));
        teamMap.put("getEntries()Ljava/util/Set;", new MethodMapping("safeGetTeamEntries", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Team;)Ljava/util/Set;"));
        teamMap.put("getPlayers()Ljava/util/Set;", new MethodMapping("safeGetPlayers", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Team;)Ljava/util/Set;"));
        teamMap.put("getSize()I", new MethodMapping("safeGetSize", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Team;)I"));
        methodMappings.put(TEAM_OWNER, teamMap);

        // Objective Mappings
        Map<String, MethodMapping> objectiveMap = new HashMap<>();
        objectiveMap.put("setDisplayName(Ljava/lang/String;)V", new MethodMapping("safeSetDisplayName", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Objective;Ljava/lang/String;)V"));
        objectiveMap.put("unregister()V", new MethodMapping("safeUnregisterObjective", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Objective;)V"));
        objectiveMap.put("getScore(Ljava/lang/String;)Lorg/bukkit/scoreboard/Score;", new MethodMapping("safeGetScore", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Objective;Ljava/lang/String;)Lorg/bukkit/scoreboard/Score;"));
        methodMappings.put(OBJECTIVE_OWNER, objectiveMap);

        // Score Mappings
        Map<String, MethodMapping> scoreMap = new HashMap<>();
        scoreMap.put("setScore(I)V", new MethodMapping("safeSetScore", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Score;I)V"));
        methodMappings.put(SCORE_OWNER, scoreMap);
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor parent) {
        return new ScoreboardClassVisitor(parent);
    }

    private static class MethodMapping {
        final String newName;
        final String newDescriptor;
        MethodMapping(String newName, String newDescriptor) {
            this.newName = newName;
            this.newDescriptor = newDescriptor;
        }
    }

    private class ScoreboardClassVisitor extends ClassVisitor {
        private String className;
        private String outerClassName = null;
        private boolean isPluginSubclass = false;

        public ScoreboardClassVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM9, classVisitor);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            this.className = name;
            if ("org/bukkit/plugin/java/JavaPlugin".equals(superName)) {
                this.isPluginSubclass = true;
            }
        }

        @Override
        public void visitOuterClass(String owner, String name, String descriptor) {
            super.visitOuterClass(owner, name, descriptor);
            this.outerClassName = owner;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
            // Only apply to instance methods where we can get a 'this' or 'this$0' reference
            if (!isStatic) {
                 return new ScoreboardMethodVisitor(mv, access, name, descriptor);
            }
            return mv;
        }

        private class ScoreboardMethodVisitor extends AdviceAdapter {
            ScoreboardMethodVisitor(MethodVisitor methodVisitor, int access, String name, String descriptor) {
                super(Opcodes.ASM9, methodVisitor, access, name, descriptor);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                if (opcode != Opcodes.INVOKEINTERFACE || !methodMappings.containsKey(owner)) {
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    return;
                }

                String methodKey = name + descriptor;
                MethodMapping mapping = methodMappings.get(owner).get(methodKey);

                if (mapping != null) {
                    logger.finer("Found potential Scoreboard API call: " + owner + "#" + name);

                    // Store arguments in local variables
                    Type[] argTypes = Type.getArgumentTypes(descriptor);
                    int[] locals = new int[argTypes.length];
                    for (int i = argTypes.length - 1; i >= 0; i--) {
                        locals[i] = newLocal(argTypes[i]);
                        storeLocal(locals[i]);
                    }

                    // Store 'this' object (scoreboard, team, etc.) in a local variable
                    int ownerLocal = newLocal(Type.getObjectType(owner));
                    storeLocal(ownerLocal);

                    // --- Plugin Instance Loading Logic ---
                    boolean pluginFound = false;
                    if (isPluginSubclass) {
                        loadThis(); // 'this' is the plugin instance
                        pluginFound = true;
                    } else if (outerClassName != null) {
                        loadThis();
                        super.visitFieldInsn(GETFIELD, className, "this$0", "L" + outerClassName + ";");
                        pluginFound = true;
                    }

                    if (pluginFound) {
                        logger.finer("Transforming " + owner.substring(owner.lastIndexOf('/') + 1) + " call '" + name + "' in " + className);

                        // Load the owner object (scoreboard, team, etc.)
                        loadLocal(ownerLocal);

                        // Load the original arguments back
                        for (int local : locals) {
                            loadLocal(local);
                        }

                        // Call the static FoliaPatcher method
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, relocatedPatcherPath, mapping.newName, mapping.newDescriptor, false);

                    } else {
                        logger.warning("Could not find Plugin instance for Scoreboard transformation in " + className + ". Aborting transform for this call.");
                        // Abort, reload original arguments and call original method
                        loadLocal(ownerLocal);
                        for (int local : locals) {
                            loadLocal(local);
                        }
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }
                } else {
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }
            }
        }
    }
}
