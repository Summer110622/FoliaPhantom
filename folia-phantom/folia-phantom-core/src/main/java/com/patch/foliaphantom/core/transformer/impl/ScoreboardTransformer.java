/*
 * Folia Phantom - Scoreboard Transformer
 *
 * Copyright (c) 2025 Marv
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
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class ScoreboardTransformer implements ClassTransformer {

    private static final String SCOREBOARD_OWNER = "org/bukkit/scoreboard/Scoreboard";
    private static final String TEAM_OWNER = "org/bukkit/scoreboard/Team";
    private static final String OBJECTIVE_OWNER = "org/bukkit/scoreboard/Objective";
    private static final String SCORE_OWNER = "org/bukkit/scoreboard/Score";

    private final Logger logger;
    private final String relocatedPatcherPath;
    private final Map<String, Map<String, MethodMapping>> methodMappings;

    public ScoreboardTransformer(Logger logger, String relocatedPatcherPath) {
        this.logger = logger;
        this.relocatedPatcherPath = relocatedPatcherPath;
        this.methodMappings = new HashMap<>();

        // Scoreboard Mappings
        Map<String, MethodMapping> scoreboardMap = new HashMap<>();
        scoreboardMap.put("registerNewObjective(Ljava/lang/String;Ljava/lang/String;)Lorg/bukkit/scoreboard/Objective;", new MethodMapping("safeRegisterNewObjective", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Scoreboard;Ljava/lang/String;Ljava/lang/String;)Lorg/bukkit/scoreboard/Objective;"));
        scoreboardMap.put("registerNewTeam(Ljava/lang/String;)Lorg/bukkit/scoreboard/Team;", new MethodMapping("safeRegisterNewTeam", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Scoreboard;Ljava/lang/String;)Lorg/bukkit/scoreboard/Team;"));
        scoreboardMap.put("resetScores(Ljava/lang/String;)V", new MethodMapping("safeResetScores", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Scoreboard;Ljava/lang/String;)V"));
        scoreboardMap.put("clearSlot(Lorg/bukkit/scoreboard/DisplaySlot;)V", new MethodMapping("safeClearSlot", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Scoreboard;Lorg/bukkit/scoreboard/DisplaySlot;)V"));
        methodMappings.put(SCOREBOARD_OWNER, scoreboardMap);

        // Team Mappings
        Map<String, MethodMapping> teamMap = new HashMap<>();
        teamMap.put("addEntry(Ljava/lang/String;)V", new MethodMapping("safeAddEntry", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Team;Ljava/lang/String;)V"));
        teamMap.put("removeEntry(Ljava/lang/String;)Z", new MethodMapping("safeRemoveEntry", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Team;Ljava/lang/String;)Z"));
        teamMap.put("setPrefix(Ljava/lang/String;)V", new MethodMapping("safeSetPrefix", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Team;Ljava/lang/String;)V"));
        teamMap.put("setSuffix(Ljava/lang/String;)V", new MethodMapping("safeSetSuffix", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Team;Ljava/lang/String;)V"));
        teamMap.put("setDisplayName(Ljava/lang/String;)V", new MethodMapping("safeSetTeamDisplayName", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Team;Ljava/lang/String;)V"));
        teamMap.put("setColor(Lorg/bukkit/ChatColor;)V", new MethodMapping("safeSetColor", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Team;Lorg/bukkit/ChatColor;)V"));
        teamMap.put("setAllowFriendlyFire(Z)V", new MethodMapping("safeSetAllowFriendlyFire", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Team;Z)V"));
        teamMap.put("setCanSeeFriendlyInvisibles(Z)V", new MethodMapping("safeSetCanSeeFriendlyInvisibles", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Team;Z)V"));
        teamMap.put("setOption(Lorg/bukkit/scoreboard/Team$Option;Lorg/bukkit/scoreboard/Team$OptionStatus;)V", new MethodMapping("safeSetOption", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Team;Lorg/bukkit/scoreboard/Team$Option;Lorg/bukkit/scoreboard/Team$OptionStatus;)V"));
        teamMap.put("unregister()V", new MethodMapping("safeUnregisterTeam", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Team;)V"));
        methodMappings.put(TEAM_OWNER, teamMap);

        // Objective Mappings
        Map<String, MethodMapping> objectiveMap = new HashMap<>();
        objectiveMap.put("setDisplayName(Ljava/lang/String;)V", new MethodMapping("safeSetDisplayName", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Objective;Ljava/lang/String;)V"));
        objectiveMap.put("setDisplaySlot(Lorg/bukkit/scoreboard/DisplaySlot;)V", new MethodMapping("safeSetDisplaySlot", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Objective;Lorg/bukkit/scoreboard/DisplaySlot;)V"));
        objectiveMap.put("setRenderType(Lorg/bukkit/scoreboard/RenderType;)V", new MethodMapping("safeSetRenderType", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Objective;Lorg/bukkit/scoreboard/RenderType;)V"));
        objectiveMap.put("unregister()V", new MethodMapping("safeUnregisterObjective", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/scoreboard/Objective;)V"));
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
        private String pluginFieldName = null;
        private boolean isPluginClass = false;

        public ScoreboardClassVisitor(ClassVisitor classVisitor) {
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
            return new ScoreboardMethodVisitor(mv, access, name, descriptor);
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
                    if (isPluginClass || pluginFieldName != null) {
                        logger.finer("Transforming " + owner.substring(owner.lastIndexOf('/') + 1) + " call '" + name + "' in " + className);

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

                        // Load the plugin instance
                        if (isPluginClass) {
                            loadThis();
                        } else {
                            loadThis();
                            getField(Type.getObjectType(className), pluginFieldName, Type.getType("Lorg/bukkit/plugin/Plugin;"));
                        }

                        // Load the owner object and then the arguments
                        loadLocal(ownerLocal);
                        for (int local : locals) {
                            loadLocal(local);
                        }

                        // Call the static FoliaPatcher method
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, relocatedPatcherPath, mapping.newName, mapping.newDescriptor, false);
                    } else {
                        logger.warning("Could not find Plugin field for Scoreboard transformation in " + className);
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }
                } else {
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }
            }
        }
    }
}
