
package com.patch.foliaphantom.core.transformer;

import com.patch.foliaphantom.core.transformer.impl.EntitySchedulerTransformer;
import com.patch.foliaphantom.core.transformer.impl.EventCallTransformer;
import com.patch.foliaphantom.core.transformer.impl.EventHandlerTransformer;
import com.patch.foliaphantom.core.transformer.impl.InventoryTransformer;
import com.patch.foliaphantom.core.transformer.impl.PlayerTransformer;
import com.patch.foliaphantom.core.transformer.impl.SchedulerClassTransformer;
import com.patch.foliaphantom.core.transformer.impl.ScoreboardTransformer;
import com.patch.foliaphantom.core.transformer.impl.TeleportTransformer;
import com.patch.foliaphantom.core.transformer.impl.ThreadSafetyTransformer;
import com.patch.foliaphantom.core.transformer.impl.WorldGenClassTransformer;

/**
 * Enumeration of available class transformers.
 * <p>
 * This allows for type-safe identification and management of transformers,
 * enabling them to be selectively enabled or disabled.
 */
public enum TransformerType {
    EVENT_HANDLER(EventHandlerTransformer.class),
    TELEPORT(TeleportTransformer.class),
    THREAD_SAFETY(ThreadSafetyTransformer.class),
    PLAYER(PlayerTransformer.class),
    INVENTORY(InventoryTransformer.class),
    WORLD_GEN(WorldGenClassTransformer.class),
    ENTITY_SCHEDULER(EntitySchedulerTransformer.class),
    SCOREBOARD(ScoreboardTransformer.class),
    SCHEDULER(SchedulerClassTransformer.class),
    EVENT_CALL(EventCallTransformer.class);

    private final Class<? extends ClassTransformer> transformerClass;

    TransformerType(Class<? extends ClassTransformer> transformerClass) {
        this.transformerClass = transformerClass;
    }

    /**
     * Gets the implementation class of the transformer.
     *
     * @return The transformer's class object.
     */
    public Class<? extends ClassTransformer> getTransformerClass() {
        return transformerClass;
    }
}
