/*
 * Folia Phantom - Audit Result
 *
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.audit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Represents the results of a plugin audit.
 */
public class AuditResult {
  private final String pluginName;
  private final Map<String, List<Finding>> findingsByClass = new TreeMap<>();
  private int totalFindings = 0;

  public AuditResult(String pluginName) {
    this.pluginName = pluginName;
  }

  public void addFinding(String className, String methodName, String reason) {
    findingsByClass.computeIfAbsent(className, k -> new ArrayList<>())
        .add(new Finding(methodName, reason));
    totalFindings++;
  }

  public String getPluginName() {
    return pluginName;
  }

  public Map<String, List<Finding>> getFindingsByClass() {
    return findingsByClass;
  }

  public int getTotalFindings() {
    return totalFindings;
  }

  public static class Finding {
    private final String methodName;
    private final String reason;

    public Finding(String methodName, String reason) {
      this.methodName = methodName;
      this.reason = reason;
    }

    public String getMethodName() {
      return methodName;
    }

    public String getReason() {
      return reason;
    }
  }
}
