/*
 * Folia Phantom - BossBar Transformer
 *
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import java.util.logging.Logger;

/**
 * Transforms thread-unsafe Bukkit BossBar API calls into their thread-safe FoliaPatcher equivalents.
 */
public class BossBarTransformer implements ClassTransformer {
  private final String relocatedPatcherPath;
  private static final String PATCHER_CLASS = "FoliaPatcher";
  private static final String PLUGIN_DESC = "Lorg/bukkit/plugin/Plugin;";
  private static final String JAVA_PLUGIN_DESC = "Lorg/bukkit/plugin/java/JavaPlugin;";

  public BossBarTransformer(Logger logger, String relocatedPatcherPath) {
    this.relocatedPatcherPath = relocatedPatcherPath;
  }

  @Override
  public ClassVisitor createVisitor(ClassVisitor next) {
    return new BossBarVisitor(next, relocatedPatcherPath + "/" + PATCHER_CLASS);
  }

  private static class BossBarVisitor extends ClassVisitor {
    private String className;
    private String pluginFieldName;
    private String pluginFieldDesc;
    private boolean isSubclassOfJavaPlugin;
    private final String patcherPath;

    public BossBarVisitor(ClassVisitor cv, String patcherPath) {
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
      boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
      if (!isStatic && (pluginFieldName != null || isSubclassOfJavaPlugin)) {
        return new BossBarMethodVisitor(mv, access, name, desc, patcherPath, className,
            pluginFieldName, pluginFieldDesc, isSubclassOfJavaPlugin);
      }
      return mv;
    }
  }

  private static class BossBarMethodVisitor extends AdviceAdapter {
    private final String patcherOwner;
    private final String pluginFieldOwner;
    private final String pluginFieldName;
    private final Type pluginFieldType;
    private final boolean isPluginClass;

    protected BossBarMethodVisitor(MethodVisitor mv, int access, String name, String desc,
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
      if ((owner.equals("org/bukkit/boss/BossBar") || owner.equals("org/bukkit/boss/KeyedBossBar")) && isInterface) {
        if (tryHandle(name, desc)) {
          return;
        }
      }
      super.visitMethodInsn(opcode, owner, name, desc, isInterface);
    }

    private boolean tryHandle(String name, String desc) {
      if ("addPlayer".equals(name) && "(Lorg/bukkit/entity/Player;)V".equals(desc)) {
        return transform(1, "_bb_ap", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/boss/BossBar;Lorg/bukkit/entity/Player;)V");
      }
      if ("removePlayer".equals(name) && "(Lorg/bukkit/entity/Player;)V".equals(desc)) {
        return transform(1, "_bb_rp", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/boss/BossBar;Lorg/bukkit/entity/Player;)V");
      }
      if ("removeAll".equals(name) && "()V".equals(desc)) {
        return transform(0, "_bb_ra", "(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/boss/BossBar;)V");
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
