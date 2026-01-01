/*
 * Folia Phantom - Transformer Type Enum
 *
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer;

/**
 * Enum representing the different types of class transformers available.
 * Used for selectively enabling or disabling specific transformations.
 */
public enum TransformerType {
    /**
     * Handles optimization of Bukkit event handlers.
     * @see com.patch.foliaphantom.core.transformer.impl.EventHandlerTransformer
     */
    EVENT_HANDLER,

    /**
     * Ensures thread-safety for world and block modification calls.
     * @see com.patch.foliaphantom.core.transformer.impl.ThreadSafetyTransformer
     */
    THREAD_SAFETY,

    /**
     * Wraps world generation APIs for Folia compatibility.
     * @see com.patch.foliaphantom.core.transformer.impl.WorldGenClassTransformer
     */
    WORLD_GEN,

    /**
     * Transforms entity-specific scheduler calls.
     * @see com.patch.foliaphantom.core.transformer.impl.EntitySchedulerTransformer
     */
    ENTITY_SCHEDULER,

    /**
     * Redirects general Bukkit scheduler calls to Folia schedulers.
     * @see com.patch.foliaphantom.core.transformer.impl.SchedulerClassTransformer
     */
    SCHEDULER
}
