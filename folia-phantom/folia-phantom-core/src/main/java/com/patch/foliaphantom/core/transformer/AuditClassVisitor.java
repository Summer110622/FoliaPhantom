/*
 * Folia Phantom - Audit Class Visitor
 *
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer;

import com.patch.foliaphantom.core.audit.AuditResult;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import java.util.Set;

/**
 * A ClassVisitor that scans for all thread-unsafe Bukkit API calls.
 */
public class AuditClassVisitor extends ClassVisitor {
  private final AuditResult auditResult;
  private String className;

  private static final Set<String> INTERESTING_OWNERS = Set.of(
    "org/bukkit/scheduler/BukkitScheduler",
    "org/bukkit/scheduler/BukkitRunnable",
    "org/bukkit/WorldCreator",
    "org/bukkit/entity/Player",
    "org/bukkit/scoreboard/Scoreboard",
    "org/bukkit/scoreboard/Team",
    "org/bukkit/scoreboard/Objective",
    "org/bukkit/scoreboard/Score",
    "org/bukkit/Server",
    "org/bukkit/plugin/PluginManager",
    "org/bukkit/block/Block",
    "org/bukkit/World",
    "org/bukkit/Bukkit",
    "org/bukkit/plugin/Plugin",
    "org/bukkit/plugin/java/JavaPlugin",
    "org/bukkit/entity/Entity",
    "org/bukkit/entity/LivingEntity",
    "org/bukkit/entity/Damageable",
    "org/bukkit/block/BlockState",
    "org/bukkit/inventory/Inventory",
    "org/bukkit/Chunk",
    "org/bukkit/attribute/Attributable",
    "org/bukkit/boss/BossBar",
    "org/bukkit/boss/KeyedBossBar"
  );

  public AuditClassVisitor(AuditResult auditResult) {
    super(Opcodes.ASM9);
    this.auditResult = auditResult;
  }

  @Override
  public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
    this.className = name.replace('/', '.');
    super.visit(version, access, name, signature, superName, interfaces);
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
    return new AuditMethodVisitor(name);
  }

  private class AuditMethodVisitor extends MethodVisitor {
    private final String currentMethodName;

    public AuditMethodVisitor(String methodName) {
      super(Opcodes.ASM9);
      this.currentMethodName = methodName;
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
      if (INTERESTING_OWNERS.contains(owner)) {
        String reason = null;
        switch (owner) {
          case "org/bukkit/plugin/PluginManager":
            if ("callEvent".equals(name)) reason = "Async event calling";
            break;
          case "org/bukkit/block/Block":
            if ("setType".equals(name) || "setBlockData".equals(name)) reason = "Thread-unsafe block modification";
            break;
          case "org/bukkit/World":
            switch (name) {
              case "spawn": reason = "Thread-unsafe entity spawn"; break;
              case "loadChunk": reason = "Thread-unsafe chunk loading"; break;
              case "getEntities":
              case "getLivingEntities":
              case "getPlayers":
              case "getNearbyEntities":
                reason = "Thread-unsafe world entity access"; break;
              case "getHighestBlockAt":
                reason = "Thread-unsafe world block access"; break;
              case "rayTraceBlocks":
              case "rayTraceEntities":
                reason = "Thread-unsafe raytracing"; break;
              case "spawnParticle":
                reason = "Thread-unsafe particle spawning"; break;
            }
            break;
          case "org/bukkit/Bukkit":
          case "org/bukkit/Server":
            if ("getOnlinePlayers".equals(name) || "getWorlds".equals(name) || "getPlayer".equals(name) || "getWorld".equals(name))
                reason = "Thread-unsafe global mirroring";
            else if ("createWorld".equals(name)) reason = "Thread-unsafe world creation";
            else if ("dispatchCommand".equals(name)) reason = "Thread-unsafe command dispatch";
            else if ("getOfflinePlayer".equals(name)) reason = "Blocking offline player access";
            break;
          case "org/bukkit/scheduler/BukkitScheduler":
          case "org/bukkit/scheduler/BukkitRunnable":
            reason = "Legacy Bukkit scheduler usage";
            break;
          case "org/bukkit/entity/Entity":
          case "org/bukkit/entity/LivingEntity":
          case "org/bukkit/entity/Damageable":
          case "org/bukkit/entity/Player":
            switch (name) {
              case "remove":
              case "setVelocity":
              case "teleport":
              case "setFireTicks":
              case "setCustomName":
              case "setGravity":
              case "damage":
              case "setAI":
              case "setGameMode":
                reason = "Thread-unsafe entity modification"; break;
              case "getHealth":
                reason = "Thread-unsafe entity state access"; break;
              case "addPotionEffect":
              case "removePotionEffect":
              case "hasPotionEffect":
              case "getPotionEffect":
                reason = "Thread-unsafe potion effect modification"; break;
              case "addPassenger":
              case "removePassenger":
              case "eject":
                reason = "Thread-unsafe passenger modification"; break;
              case "getNearbyEntities":
                reason = "Thread-unsafe nearby entity access"; break;
              case "addScoreboardTag":
              case "removeScoreboardTag":
                reason = "Thread-unsafe scoreboard tag modification"; break;
            }
            break;
          case "org/bukkit/block/BlockState":
            if ("update".equals(name)) reason = "Thread-unsafe block state update";
            break;
          case "org/bukkit/inventory/Inventory":
            if ("setItem".equals(name) || "addItem".equals(name) || "clear".equals(name))
                reason = "Thread-unsafe inventory modification";
            break;
          case "org/bukkit/Chunk":
            if ("getEntities".equals(name)) reason = "Thread-unsafe chunk entity access";
            else if ("load".equals(name) || "unload".equals(name)) reason = "Thread-unsafe chunk state change";
            break;
          case "org/bukkit/attribute/Attributable":
            if ("getAttribute".equals(name)) reason = "Thread-unsafe attribute access";
            break;
          case "org/bukkit/boss/BossBar":
          case "org/bukkit/boss/KeyedBossBar":
            if ("addPlayer".equals(name) || "removePlayer".equals(name) || "removeAll".equals(name))
                reason = "Thread-unsafe bossbar modification";
            break;
          case "org/bukkit/scoreboard/Scoreboard":
          case "org/bukkit/scoreboard/Team":
          case "org/bukkit/scoreboard/Objective":
          case "org/bukkit/scoreboard/Score":
            reason = "Thread-unsafe scoreboard interaction";
            break;
        }

        if (reason != null) {
          auditResult.addFinding(className, currentMethodName, reason + " (" + owner.replace('/', '.') + "#" + name + ")");
        }
      }

      if (opcode == Opcodes.INVOKEVIRTUAL && isBukkitRunnableInstanceMethod(name, desc)) {
        auditResult.addFinding(className, currentMethodName, "Legacy BukkitRunnable task scheduling");
      }
    }

    private boolean isBukkitRunnableInstanceMethod(String name, String desc) {
      String combined = name + desc;
      switch (combined) {
        case "runTask(Lorg/bukkit/plugin/Plugin;)Lorg/bukkit/scheduler/BukkitTask;":
        case "runTaskLater(Lorg/bukkit/plugin/Plugin;J)Lorg/bukkit/scheduler/BukkitTask;":
        case "runTaskTimer(Lorg/bukkit/plugin/Plugin;JJ)Lorg/bukkit/scheduler/BukkitTask;":
        case "runTaskAsynchronously(Lorg/bukkit/plugin/Plugin;)Lorg/bukkit/scheduler/BukkitTask;":
        case "runTaskLaterAsynchronously(Lorg/bukkit/plugin/Plugin;J)Lorg/bukkit/scheduler/BukkitTask;":
        case "runTaskTimerAsynchronously(Lorg/bukkit/plugin/Plugin;JJ)Lorg/bukkit/scheduler/BukkitTask;":
          return true;
        default:
          return false;
      }
    }
  }
}
