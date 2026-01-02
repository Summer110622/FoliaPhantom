/*
 * Folia Phantom - Plugin Patcher Core
 *
 * This module provides the core bytecode transformation logic for converting
 * Bukkit plugins to be compatible with Folia's region-based threading model.
 *
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer;

/**
 * Enum representing the different types of bytecode transformers available.
 * <p>
 * This is used to selectively enable or disable specific patch operations.
 * </p>
 */
public enum TransformerType {
    /**
     * Transforms Bukkit event handlers to be region-aware.
     * @see com.patch.foliaphantom.core.transformer.impl.EventHandlerTransformer
     */
    EVENT_HANDLER,

    /**
     * Transforms teleport calls to use Folia's async teleport API.
     * @see com.patch.foliaphantom.core.transformer.impl.TeleportTransformer
     */
    TELEPORT,

    /**
     * Transforms various thread-unsafe Bukkit API calls (e.g., Block.setType)
     * into their thread-safe FoliaPatcher equivalents.
     * @see com.patch.foliaphantom.core.transformer.impl.ThreadSafetyTransformer
     */
    THREAD_SAFETY,

    /**
     * Transforms world generation related calls.
     * @see com.patch.foliaphantom.core.transformer.impl.WorldGenClassTransformer
     */
    WORLD_GEN,

    /**
     * Transforms entity-specific scheduler calls.
     * @see com.patch.foliaphantom.core.transformer.impl.EntitySchedulerTransformer
     */
    ENTITY_SCHEDULER,

    /**
     * Transforms Bukkit Scoreboard API calls.
     * @see com.patch.foliaphantom.core.transformer.impl.ScoreboardTransformer
     */
    SCOREBOARD,

    /**
     * Transforms general Bukkit scheduler calls (BukkitScheduler, BukkitRunnable).
     * @see com.patch.foliaphantom.core.transformer.impl.SchedulerClassTransformer
     */
    SCHEDULER_CLASS;
}
