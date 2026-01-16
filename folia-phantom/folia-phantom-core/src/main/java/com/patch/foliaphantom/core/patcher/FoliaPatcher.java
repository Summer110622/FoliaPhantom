/*
 * Folia Phantom - Runtime Patcher Bridge
 * 
 * This class provides the redirected method implementations that patched plugins
 * call at runtime. It bridges standard Bukkit/Paper API calls to Folia-specific
 * region-based or async schedulers.
 * 
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.patcher;

import com.patch.foliaphantom.core.exception.FoliaPatcherTimeoutException;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runtime bridge for Folia compatibility.
 * 
 * <p>
 * This class is bundled into patched plugins and serves as the destination
 * for redirected method calls. It handles the complexity of mapping global
 * scheduler calls to Folia's region-based or asynchronous schedulers.
 * </p>
 */
public final class FoliaPatcher {
    /**
     * If true, timeout exceptions will be thrown rather than logged.
     * This field is set dynamically at patch time via ASM.
     */
    public static final boolean FAIL_FAST = false;

    /**
     * If true, event calls from async threads will not block and wait for completion.
     * This improves performance but may break plugins that expect synchronous event handling.
     * This field is set dynamically at patch time via ASM.
     */
    public static final boolean AGGRESSIVE_EVENT_OPTIMIZATION = false;

    /**
     * If true, API calls that return a value will not block and wait for completion,
     * returning a default value (e.g., null, false) instead. This maximizes performance
     * but is a breaking change for plugins that rely on the return value.
     * This field is set dynamically at patch time via ASM.
     */
    public static final boolean FIRE_AND_FORGET = false;

    /**
     * The timeout in milliseconds for API calls that block for a result.
     * This field is set dynamically at patch time via ASM.
     */
    public static final long API_TIMEOUT_MS = 100;

    /**
     * A set of event class names that should be fired asynchronously without waiting.
     * This field is set dynamically at patch time via ASM.
     */
    private static final Set<String> FIRE_AND_FORGET_EVENTS = Collections.emptySet();

    private static final Logger LOGGER = Logger.getLogger("FoliaPhantom-Patcher");
    private static final ExecutorService worldGenExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "FoliaPhantom-WorldGen-Worker");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicInteger taskIdCounter = new AtomicInteger(1000000);
    private static final Map<Integer, ScheduledTask> runningTasks = new ConcurrentHashMap<>();

    private FoliaPatcher() {
    }

    // --- World Generation Wrappers ---

    /**
     * Wraps a ChunkGenerator to ensure Folia compatibility.
     */
    public static org.bukkit.generator.ChunkGenerator getDefaultWorldGenerator(Plugin plugin, String worldName,
            String id) {
        org.bukkit.generator.ChunkGenerator originalGenerator = plugin.getDefaultWorldGenerator(worldName, id);
        if (originalGenerator == null)
            return null;
        return new FoliaChunkGenerator(originalGenerator);
    }

    /**
     * Safely creates a world by offloading to a dedicated thread, as world creation
     * in Folia/Paper can be sensitive to the calling thread context.
     */
    public static World createWorld(org.bukkit.WorldCreator creator) {
        LOGGER.info("[FoliaPhantom] Intercepting world creation: " + creator.name());
        Future<World> future = worldGenExecutor.submit(creator::createWorld);
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.severe("[FoliaPhantom] Failed to create world '" + creator.name() + "': " + e.getMessage());
            return null;
        }
    }

    /**
     * Safely gets the highest block at a given location.
     */
    public static Block safeGetHighestBlockAt(Plugin plugin, World world, int x, int z) {
        if (Bukkit.isPrimaryThread()) {
            return world.getHighestBlockAt(x, z);
        } else {
            if (FIRE_AND_FORGET) {
                return null;
            }
            CompletableFuture<Block> future = new CompletableFuture<>();
            Bukkit.getRegionScheduler().run(plugin, world, x, z, task -> {
                try {
                    future.complete(world.getHighestBlockAt(x, z));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            try {
                return future.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to get highest block at " + x + ", " + z, e);
                return null;
            } catch (TimeoutException e) {
                if (FAIL_FAST) {
                    throw new FoliaPatcherTimeoutException("Failed to get highest block at " + x + ", " + z, e);
                }
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Timed out while getting highest block at " + x + ", " + z, e);
                return null;
            }
        }
    }

    /**
     * Internal wrapper for Folia's ChunkGenerator.
     */
    public static class FoliaChunkGenerator extends org.bukkit.generator.ChunkGenerator {
        private final org.bukkit.generator.ChunkGenerator original;

        public FoliaChunkGenerator(org.bukkit.generator.ChunkGenerator original) {
            this.original = original;
        }

        @Override
        public ChunkData generateChunkData(World world, java.util.Random random, int x, int z, BiomeGrid biome) {
            return original.generateChunkData(world, random, x, z, biome);
        }

        @Override
        public boolean shouldGenerateNoise() {
            return original.shouldGenerateNoise();
        }

        @Override
        public boolean shouldGenerateSurface() {
            return original.shouldGenerateSurface();
        }

        @Override
        public boolean shouldGenerateBedrock() {
            return original.shouldGenerateBedrock();
        }

        @Override
        public boolean shouldGenerateCaves() {
            return original.shouldGenerateCaves();
        }

        @Override
        public boolean shouldGenerateDecorations() {
            return original.shouldGenerateDecorations();
        }

        @Override
        public boolean shouldGenerateMobs() {
            return original.shouldGenerateMobs();
        }

        @Override
        public Location getFixedSpawnLocation(World world, java.util.Random random) {
            return original.getFixedSpawnLocation(world, random);
        }
    }

    // --- Helper Methods ---

    private static Location getFallbackLocation() {
        World mainWorld = Bukkit.getWorlds().get(0);
        return mainWorld != null ? mainWorld.getSpawnLocation() : null;
    }

    private static void cancelTaskById(int taskId) {
        ScheduledTask task = runningTasks.remove(taskId);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    private static Runnable wrapRunnable(Runnable original, int taskId, boolean isRepeating) {
        if (isRepeating)
            return original;
        return () -> {
            try {
                original.run();
            } finally {
                runningTasks.remove(taskId);
            }
        };
    }

    // --- Scheduler Redirections ---

    public static BukkitTask runTask(BukkitScheduler ignored, Plugin plugin, Runnable runnable) {
        int taskId = taskIdCounter.getAndIncrement();
        Runnable wrapped = wrapRunnable(runnable, taskId, false);
        Location loc = getFallbackLocation();

        ScheduledTask foliaTask = (loc != null)
                ? Bukkit.getRegionScheduler().run(plugin, loc, t -> wrapped.run())
                : Bukkit.getGlobalRegionScheduler().run(plugin, t -> wrapped.run());

        runningTasks.put(taskId, foliaTask);
        return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, true, foliaTask);
    }

    public static BukkitTask runTaskLater(BukkitScheduler ignored, Plugin plugin, Runnable runnable, long delay) {
        int taskId = taskIdCounter.getAndIncrement();
        Runnable wrapped = wrapRunnable(runnable, taskId, false);
        Location loc = getFallbackLocation();
        long finalDelay = Math.max(1, delay);

        ScheduledTask foliaTask = (loc != null)
                ? Bukkit.getRegionScheduler().runDelayed(plugin, loc, t -> wrapped.run(), finalDelay)
                : Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> wrapped.run(), finalDelay);

        runningTasks.put(taskId, foliaTask);
        return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, true, foliaTask);
    }

    public static BukkitTask runTaskTimer(BukkitScheduler ignored, Plugin plugin, Runnable runnable, long delay,
            long period) {
        int taskId = taskIdCounter.getAndIncrement();
        Location loc = getFallbackLocation();
        long d = Math.max(1, delay);
        long p = Math.max(1, period);

        ScheduledTask foliaTask = (loc != null)
                ? Bukkit.getRegionScheduler().runAtFixedRate(plugin, loc, t -> runnable.run(), d, p)
                : Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> runnable.run(), d, p);

        runningTasks.put(taskId, foliaTask);
        return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, true, foliaTask);
    }

    public static BukkitTask runTaskAsynchronously(BukkitScheduler ignored, Plugin plugin, Runnable runnable) {
        int taskId = taskIdCounter.getAndIncrement();
        Runnable wrapped = wrapRunnable(runnable, taskId, false);
        ScheduledTask foliaTask = Bukkit.getAsyncScheduler().runNow(plugin, t -> wrapped.run());
        runningTasks.put(taskId, foliaTask);
        return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, false, foliaTask);
    }

    public static BukkitTask runTaskLaterAsynchronously(BukkitScheduler ignored, Plugin plugin, Runnable runnable,
            long delay) {
        int taskId = taskIdCounter.getAndIncrement();
        Runnable wrapped = wrapRunnable(runnable, taskId, false);
        ScheduledTask foliaTask = Bukkit.getAsyncScheduler().runDelayed(plugin, t -> wrapped.run(), delay * 50,
                TimeUnit.MILLISECONDS);
        runningTasks.put(taskId, foliaTask);
        return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, false, foliaTask);
    }

    public static BukkitTask runTaskTimerAsynchronously(BukkitScheduler ignored, Plugin plugin, Runnable runnable,
            long delay, long period) {
        int taskId = taskIdCounter.getAndIncrement();
        ScheduledTask foliaTask = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> runnable.run(), delay * 50,
                period * 50, TimeUnit.MILLISECONDS);
        runningTasks.put(taskId, foliaTask);
        return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, false, foliaTask);
    }

    // --- NEW Scheduler Redirections (without scheduler instance) ---

    public static BukkitTask runTask(Plugin plugin, Runnable runnable) {
        return runTask(null, plugin, runnable);
    }

    public static BukkitTask runTaskLater(Plugin plugin, Runnable runnable, long delay) {
        return runTaskLater(null, plugin, runnable, delay);
    }

    public static BukkitTask runTaskTimer(Plugin plugin, Runnable runnable, long delay, long period) {
        return runTaskTimer(null, plugin, runnable, delay, period);
    }

    public static BukkitTask runTaskAsynchronously(Plugin plugin, Runnable runnable) {
        return runTaskAsynchronously(null, plugin, runnable);
    }

    public static BukkitTask runTaskLaterAsynchronously(Plugin plugin, Runnable runnable, long delay) {
        return runTaskLaterAsynchronously(null, plugin, runnable, delay);
    }

    public static BukkitTask runTaskTimerAsynchronously(Plugin plugin, Runnable runnable, long delay, long period) {
        return runTaskTimerAsynchronously(null, plugin, runnable, delay, period);
    }

    // --- BukkitRunnable Instance Method Wrappers ---

    /**
     * Safely gets all players in a world.
     */
    public static java.util.List<org.bukkit.entity.Player> safeGetPlayers(Plugin plugin, World world) {
        if (Bukkit.isPrimaryThread()) {
            return world.getPlayers();
        }
        if (FIRE_AND_FORGET) {
            return java.util.Collections.emptyList();
        }
        CompletableFuture<java.util.List<org.bukkit.entity.Player>> future = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            try {
                future.complete(world.getPlayers());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        try {
            return future.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to get players for world " + world.getName(), e);
            return java.util.Collections.emptyList();
        } catch (TimeoutException e) {
            if (FAIL_FAST) {
                throw new FoliaPatcherTimeoutException("Failed to get players for world " + world.getName(), e);
            }
            LOGGER.log(Level.WARNING, "[FoliaPhantom] Timed out while getting players for world " + world.getName(), e);
            return java.util.Collections.emptyList();
        }
    }

    public static int safeGetOnlinePlayersSize(final Plugin plugin) {
        if (!isFolia()) {
            return Bukkit.getOnlinePlayers().size();
        }
        if (FIRE_AND_FORGET) {
            return 0;
        }
        CompletableFuture<Integer> future = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            try {
                future.complete(Bukkit.getOnlinePlayers().size());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        try {
            return future.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            handleException("Failed to get online players size", e);
            return 0;
        }
    }

    /**
     * Safely gets the online players from the server.
     * This is a global operation, so it uses the global region scheduler.
     */
    public static java.util.Collection<? extends org.bukkit.entity.Player> safeGetOnlinePlayers(Plugin plugin) {
        if (!isFolia()) {
            return Bukkit.getServer().getOnlinePlayers();
        }
        if (FIRE_AND_FORGET) {
            return java.util.Collections.emptyList();
        }
        CompletableFuture<java.util.Collection<? extends org.bukkit.entity.Player>> future = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            try {
                future.complete(new java.util.ArrayList<>(Bukkit.getServer().getOnlinePlayers()));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        try {
            return future.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            handleException("Failed to get online players", e);
            return java.util.Collections.emptyList();
        }
    }

    private static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static void handleException(String message, Throwable e) {
        if (e instanceof TimeoutException) {
            if (FAIL_FAST) {
                throw new FoliaPatcherTimeoutException(message, e);
            }
            LOGGER.log(Level.WARNING, "[FoliaPhantom] Timed out: " + message, e);
        } else {
            LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed: " + message, e);
        }
    }

    public static BukkitTask runTask_onRunnable(Runnable runnable, Plugin plugin) {
        return runTask(plugin, runnable);
    }

    public static BukkitTask runTaskLater_onRunnable(Runnable runnable, Plugin plugin, long delay) {
        return runTaskLater(null, plugin, runnable, delay);
    }

    public static BukkitTask runTaskTimer_onRunnable(Runnable runnable, Plugin plugin, long delay, long period) {
        return runTaskTimer(null, plugin, runnable, delay, period);
    }

    public static BukkitTask runTaskAsynchronously_onRunnable(Runnable runnable, Plugin plugin) {
        return runTaskAsynchronously(null, plugin, runnable);
    }

    public static BukkitTask runTaskLaterAsynchronously_onRunnable(Runnable runnable, Plugin plugin, long delay) {
        return runTaskLaterAsynchronously(null, plugin, runnable, delay);
    }

    public static BukkitTask runTaskTimerAsynchronously_onRunnable(Runnable runnable, Plugin plugin, long delay,
            long period) {
        return runTaskTimerAsynchronously(null, plugin, runnable, delay, period);
    }

    /**
     * Implementation of BukkitTask for Folia.
     */
    public static final class FoliaBukkitTask implements BukkitTask {
        private final int taskId;
        private final Plugin owner;
        private final IntConsumer cancellationCallback;
        private final boolean isSync;
        private final ScheduledTask underlyingTask;

        public FoliaBukkitTask(int taskId, Plugin owner, IntConsumer cc, boolean isSync, ScheduledTask underlyingTask) {
            this.taskId = taskId;
            this.owner = owner;
            this.cancellationCallback = cc;
            this.isSync = isSync;
            this.underlyingTask = underlyingTask;
        }

        @Override
        public int getTaskId() {
            return taskId;
        }

        @Override
        public Plugin getOwner() {
            return owner;
        }

        @Override
        public boolean isSync() {
            return isSync;
        }

        @Override
        public boolean isCancelled() {
            return underlyingTask.isCancelled();
        }

        @Override
        public void cancel() {
            if (!isCancelled()) {
                cancellationCallback.accept(this.taskId);
            }
        }
    }

    // --- Thread-Safe World Operations ---

    /**
     * Safely gets all entities in a world.
     */
    public static java.util.List<Entity> safeGetEntities(Plugin plugin, World world) {
        if (Bukkit.isPrimaryThread()) {
            return world.getEntities();
        }
        if (FIRE_AND_FORGET) {
            return java.util.Collections.emptyList();
        }
        CompletableFuture<java.util.List<Entity>> future = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            try {
                future.complete(world.getEntities());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        try {
            return future.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to get entities for world " + world.getName(), e);
            return java.util.Collections.emptyList();
        } catch (TimeoutException e) {
            if (FAIL_FAST) {
                throw new FoliaPatcherTimeoutException("Failed to get entities for world " + world.getName(), e);
            }
            LOGGER.log(Level.WARNING, "[FoliaPhantom] Timed out while getting entities for world " + world.getName(), e);
            return java.util.Collections.emptyList();
        }
    }

    /**
     * Safely gets all living entities in a world.
     */
    public static java.util.List<org.bukkit.entity.LivingEntity> safeGetLivingEntities(Plugin plugin, World world) {
        if (Bukkit.isPrimaryThread()) {
            return world.getLivingEntities();
        }
        if (FIRE_AND_FORGET) {
            return java.util.Collections.emptyList();
        }
        CompletableFuture<java.util.List<org.bukkit.entity.LivingEntity>> future = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            try {
                future.complete(world.getLivingEntities());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        try {
            return future.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to get living entities for world " + world.getName(), e);
            return java.util.Collections.emptyList();
        } catch (TimeoutException e) {
            if (FAIL_FAST) {
                throw new FoliaPatcherTimeoutException("Failed to get living entities for world " + world.getName(), e);
            }
            LOGGER.log(Level.WARNING, "[FoliaPhantom] Timed out while getting living entities for world " + world.getName(), e);
            return java.util.Collections.emptyList();
        }
    }

    /**
     * Safely gets nearby entities to a location.
     */
    public static java.util.Collection<Entity> safeGetNearbyEntities(Plugin plugin, World world, Location location, double x, double y, double z) {
        if (Bukkit.isPrimaryThread()) {
            return world.getNearbyEntities(location, x, y, z);
        }
        if (FIRE_AND_FORGET) {
            return java.util.Collections.emptyList();
        }
        CompletableFuture<java.util.Collection<Entity>> future = new CompletableFuture<>();
        Bukkit.getRegionScheduler().run(plugin, location, task -> {
            try {
                future.complete(world.getNearbyEntities(location, x, y, z));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        try {
            return future.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to get nearby entities", e);
            return java.util.Collections.emptyList();
        } catch (TimeoutException e) {
            if (FAIL_FAST) {
                throw new FoliaPatcherTimeoutException("Failed to get nearby entities", e);
            }
            LOGGER.log(Level.WARNING, "[FoliaPhantom] Timed out while getting nearby entities", e);
            return java.util.Collections.emptyList();
        }
    }

    public static void safeSetType(Plugin plugin, Block block, org.bukkit.Material material) {
        if (Bukkit.isPrimaryThread()) {
            block.setType(material);
        } else {
            Bukkit.getRegionScheduler().run(plugin, block.getLocation(), task -> block.setType(material));
        }
    }

    public static void safeSetTypeWithPhysics(Plugin plugin, Block block, org.bukkit.Material material, boolean applyPhysics) {
        if (Bukkit.isPrimaryThread()) {
            block.setType(material, applyPhysics);
        } else {
            Bukkit.getRegionScheduler().run(plugin, block.getLocation(), task -> block.setType(material, applyPhysics));
        }
    }

    /**
     * Safely spawns an entity in the world at the given location.
     * If not on the main thread, this will schedule the spawn and block until it completes.
     */
    public static <T extends Entity> T safeSpawnEntity(Plugin plugin, World world, Location location, Class<T> clazz) {
        if (Bukkit.isPrimaryThread()) {
            return world.spawn(location, clazz);
        } else {
            CompletableFuture<T> future = new CompletableFuture<>();
            Bukkit.getRegionScheduler().run(plugin, location, task -> {
                try {
                    future.complete(world.spawn(location, clazz));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });

            if (FIRE_AND_FORGET) {
                return null;
            }

            try {
                // Block for a short time to prevent server hangs
                return future.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to spawn entity of type " + clazz.getSimpleName(), e);
                return null;
            } catch (TimeoutException e) {
                if (FAIL_FAST) {
                    throw new FoliaPatcherTimeoutException("Failed to spawn entity of type " + clazz.getSimpleName(), e);
                }
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to spawn entity of type " + clazz.getSimpleName(), e);
                return null;
            }
        }
    }

    /**
     * Safely sets the block data for a block.
     */
    public static void safeSetBlockData(Plugin plugin, Block block, BlockData data, boolean applyPhysics) {
        if (Bukkit.isPrimaryThread()) {
            block.setBlockData(data, applyPhysics);
        } else {
            Bukkit.getRegionScheduler().run(plugin, block.getLocation(), task -> block.setBlockData(data, applyPhysics));
        }
    }

    /**
     * Safely loads a chunk, generating it if specified.
     */
    public static void safeLoadChunk(Plugin plugin, World world, int x, int z, boolean generate) {
        if (Bukkit.isPrimaryThread()) {
            world.loadChunk(x, z, generate);
        } else {
            // execute() is better for this as it doesn't imply a delay
            Bukkit.getRegionScheduler().execute(plugin, world, x, z, () -> world.loadChunk(x, z, generate));
        }
    }

    /**
     * Safely teleports a player to a new location.
     * If not on the main thread, this will use async teleport and block for the result.
     */
    public static boolean safeTeleport(Plugin plugin, org.bukkit.entity.Player player, Location location) {
        if (Bukkit.isPrimaryThread()) {
            return player.teleport(location);
        } else {
            if (FIRE_AND_FORGET) {
                player.teleportAsync(location);
                return true;
            }
            try {
                // We use teleportAsync and wait for it to complete.
                return player.teleportAsync(location).get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to teleport player " + player.getName(), e);
                return false;
            } catch (TimeoutException e) {
                if (FAIL_FAST) {
                    throw new FoliaPatcherTimeoutException("Failed to teleport player " + player.getName(), e);
                }
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to teleport player " + player.getName(), e);
                return false;
            }
        }
    }

    /**
     * Safely drops an item at the specified location.
     */
    public static org.bukkit.entity.Item safeDropItem(Plugin plugin, World world, Location location, ItemStack item) {
        if (Bukkit.isPrimaryThread()) {
            return world.dropItem(location, item);
        } else {
            CompletableFuture<org.bukkit.entity.Item> future = new CompletableFuture<>();
            Bukkit.getRegionScheduler().run(plugin, location, task -> {
                try {
                    future.complete(world.dropItem(location, item));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            if (FIRE_AND_FORGET) {
                return null;
            }
            try {
                return future.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to drop item", e);
                return null;
            } catch (TimeoutException e) {
                if (FAIL_FAST) {
                    throw new FoliaPatcherTimeoutException("Failed to drop item", e);
                }
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to drop item", e);
                return null;
            }
        }
    }

    /**
     * Safely drops an item naturally at the specified location.
     */
    public static org.bukkit.entity.Item safeDropItemNaturally(Plugin plugin, World world, Location location, ItemStack item) {
        if (Bukkit.isPrimaryThread()) {
            return world.dropItemNaturally(location, item);
        } else {
            CompletableFuture<org.bukkit.entity.Item> future = new CompletableFuture<>();
            Bukkit.getRegionScheduler().run(plugin, location, task -> {
                try {
                    future.complete(world.dropItemNaturally(location, item));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            if (FIRE_AND_FORGET) {
                return null;
            }
            try {
                return future.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to drop item naturally", e);
                return null;
            } catch (TimeoutException e) {
                if (FAIL_FAST) {
                    throw new FoliaPatcherTimeoutException("Failed to drop item naturally", e);
                }
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to drop item naturally", e);
                return null;
            }
        }
    }

    /**
     * Safely creates an explosion. Using the modern method signature.
     */
    public static boolean safeCreateExplosion(Plugin plugin, World world, Location location, float power, boolean setFire, boolean breakBlocks) {
        if (Bukkit.isPrimaryThread()) {
            return world.createExplosion(location, power, setFire, breakBlocks);
        } else {
            if (FIRE_AND_FORGET) {
                Bukkit.getRegionScheduler().run(plugin, location, task -> world.createExplosion(location, power, setFire, breakBlocks));
                return true;
            }
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            Bukkit.getRegionScheduler().run(plugin, location, task -> future.complete(world.createExplosion(location, power, setFire, breakBlocks)));
            try {
                return future.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to create explosion", e);
                return false;
            } catch (TimeoutException e) {
                if (FAIL_FAST) {
                    throw new FoliaPatcherTimeoutException("Failed to create explosion", e);
                }
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to create explosion", e);
                return false;
            }
        }
    }

    /**
     * Safely plays a particle effect.
     */
    public static <T> void safePlayEffect(Plugin plugin, World world, Location location, Effect effect, T data) {
        if (Bukkit.isPrimaryThread()) {
            world.playEffect(location, effect, data);
        } else {
            Bukkit.getRegionScheduler().run(plugin, location, task -> world.playEffect(location, effect, data));
        }
    }

    /**
     * Safely plays a sound.
     */
    public static void safePlaySound(Plugin plugin, World world, Location location, Sound sound, float volume, float pitch) {
        if (Bukkit.isPrimaryThread()) {
            world.playSound(location, sound, volume, pitch);
        } else {
            Bukkit.getRegionScheduler().run(plugin, location, task -> world.playSound(location, sound, volume, pitch));
        }
    }

    /**
     * Safely strikes lightning.
     */
    public static org.bukkit.entity.LightningStrike safeStrikeLightning(Plugin plugin, World world, Location location) {
        if (Bukkit.isPrimaryThread()) {
            return world.strikeLightning(location);
        } else {
            CompletableFuture<org.bukkit.entity.LightningStrike> future = new CompletableFuture<>();
            Bukkit.getRegionScheduler().run(plugin, location, task -> {
                try {
                    future.complete(world.strikeLightning(location));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            if (FIRE_AND_FORGET) {
                return null;
            }
            try {
                return future.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to strike lightning", e);
                return null;
            } catch (TimeoutException e) {
                if (FAIL_FAST) {
                    throw new FoliaPatcherTimeoutException("Failed to strike lightning", e);
                }
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to strike lightning", e);
                return null;
            }
        }
    }

    /**
     * Safely generates a tree.
     */
    public static boolean safeGenerateTree(Plugin plugin, World world, Location location, TreeType type) {
        if (Bukkit.isPrimaryThread()) {
            return world.generateTree(location, type);
        } else {
            if (FIRE_AND_FORGET) {
                Bukkit.getRegionScheduler().run(plugin, location, task -> world.generateTree(location, type));
                return true;
            }
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            Bukkit.getRegionScheduler().run(plugin, location, task -> future.complete(world.generateTree(location, type)));
            try {
                return future.get(500, TimeUnit.MILLISECONDS); // Tree gen can be slow
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to generate tree", e);
                return false;
            } catch (TimeoutException e) {
                if (FAIL_FAST) {
                    throw new FoliaPatcherTimeoutException("Failed to generate tree", e);
                }
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to generate tree", e);
                return false;
            }
        }
    }

    /**
     * Safely sets a game rule. This is a global operation.
     */
    public static <T> boolean safeSetGameRule(Plugin plugin, World world, GameRule<T> rule, T value) {
        if (Bukkit.isPrimaryThread()) {
            return world.setGameRule(rule, value);
        } else {
            if (FIRE_AND_FORGET) {
                Bukkit.getGlobalRegionScheduler().run(plugin, task -> world.setGameRule(rule, value));
                return true;
            }
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            // Game rules are global, so use the global scheduler
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> future.complete(world.setGameRule(rule, value)));
            try {
                return future.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to set game rule " + rule.getName(), e);
                return false;
            } catch (TimeoutException e) {
                if (FAIL_FAST) {
                    throw new FoliaPatcherTimeoutException("Failed to set game rule " + rule.getName(), e);
                }
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to set game rule " + rule.getName(), e);
                return false;
            }
        }
    }

    // --- Thread-Safe Scoreboard Operations ---

    public static org.bukkit.scoreboard.Objective safeRegisterNewObjective(Plugin plugin, org.bukkit.scoreboard.Scoreboard scoreboard, String name, String criteria) {
        if (Bukkit.isPrimaryThread()) {
            return scoreboard.registerNewObjective(name, criteria);
        } else {
            CompletableFuture<org.bukkit.scoreboard.Objective> future = new CompletableFuture<>();
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                try {
                    future.complete(scoreboard.registerNewObjective(name, criteria));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            if (FIRE_AND_FORGET) {
                return null;
            }
            try {
                return future.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to register new objective '" + name + "'", e);
                return null;
            } catch (TimeoutException e) {
                if (FAIL_FAST) {
                    throw new FoliaPatcherTimeoutException("Failed to register new objective '" + name + "'", e);
                }
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to register new objective '" + name + "'", e);
                return null;
            }
        }
    }

    public static org.bukkit.scoreboard.Team safeRegisterNewTeam(Plugin plugin, org.bukkit.scoreboard.Scoreboard scoreboard, String name) {
        if (Bukkit.isPrimaryThread()) {
            return scoreboard.registerNewTeam(name);
        } else {
            CompletableFuture<org.bukkit.scoreboard.Team> future = new CompletableFuture<>();
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                try {
                    future.complete(scoreboard.registerNewTeam(name));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            if (FIRE_AND_FORGET) {
                return null;
            }
            try {
                return future.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to register new team '" + name + "'", e);
                return null;
            } catch (TimeoutException e) {
                if (FAIL_FAST) {
                    throw new FoliaPatcherTimeoutException("Failed to register new team '" + name + "'", e);
                }
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to register new team '" + name + "'", e);
                return null;
            }
        }
    }

    public static void safeResetScores(Plugin plugin, org.bukkit.scoreboard.Scoreboard scoreboard, String entry) {
        if (Bukkit.isPrimaryThread()) {
            scoreboard.resetScores(entry);
        } else {
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> scoreboard.resetScores(entry));
        }
    }

    public static void safeClearSlot(Plugin plugin, org.bukkit.scoreboard.Scoreboard scoreboard, org.bukkit.scoreboard.DisplaySlot slot) {
        if (Bukkit.isPrimaryThread()) {
            scoreboard.clearSlot(slot);
        } else {
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> scoreboard.clearSlot(slot));
        }
    }

    // --- Thread-Safe Team Operations ---

    public static void safeAddEntry(Plugin plugin, org.bukkit.scoreboard.Team team, String entry) {
        if (Bukkit.isPrimaryThread()) {
            team.addEntry(entry);
        } else {
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> team.addEntry(entry));
        }
    }

    public static boolean safeRemoveEntry(Plugin plugin, org.bukkit.scoreboard.Team team, String entry) {
        if (Bukkit.isPrimaryThread()) {
            return team.removeEntry(entry);
        } else {
            if (FIRE_AND_FORGET) {
                Bukkit.getGlobalRegionScheduler().run(plugin, task -> team.removeEntry(entry));
                return false;
            }
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> future.complete(team.removeEntry(entry)));
            try {
                return future.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to remove team entry '" + entry + "'", e);
                return false;
            } catch (TimeoutException e) {
                if (FAIL_FAST) {
                    throw new FoliaPatcherTimeoutException("Failed to remove team entry '" + entry + "'", e);
                }
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to remove team entry '" + entry + "'", e);
                return false;
            }
        }
    }

    public static void safeSetPrefix(Plugin plugin, org.bukkit.scoreboard.Team team, String prefix) {
        if (Bukkit.isPrimaryThread()) {
            team.setPrefix(prefix);
        } else {
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> team.setPrefix(prefix));
        }
    }

    public static void safeSetSuffix(Plugin plugin, org.bukkit.scoreboard.Team team, String suffix) {
        if (Bukkit.isPrimaryThread()) {
            team.setSuffix(suffix);
        } else {
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> team.setSuffix(suffix));
        }
    }

    public static void safeUnregisterTeam(Plugin plugin, org.bukkit.scoreboard.Team team) {
        if (Bukkit.isPrimaryThread()) {
            team.unregister();
        } else {
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> team.unregister());
        }
    }

    // --- Thread-Safe Objective Operations ---

    public static void safeSetDisplayName(Plugin plugin, org.bukkit.scoreboard.Objective objective, String displayName) {
        if (Bukkit.isPrimaryThread()) {
            objective.setDisplayName(displayName);
        } else {
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> objective.setDisplayName(displayName));
        }
    }

    public static void safeUnregisterObjective(Plugin plugin, org.bukkit.scoreboard.Objective objective) {
        if (Bukkit.isPrimaryThread()) {
            objective.unregister();
        } else {
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> objective.unregister());
        }
    }

    // --- Thread-Safe Score Operations ---

    public static void safeSetScore(Plugin plugin, org.bukkit.scoreboard.Score score, int scoreValue) {
        if (Bukkit.isPrimaryThread()) {
            score.setScore(scoreValue);
        } else {
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> score.setScore(scoreValue));
        }
    }

    // --- Thread-Safe Scoreboard READ Operations ---

    public static org.bukkit.scoreboard.Objective safeGetObjective(Plugin plugin, org.bukkit.scoreboard.Scoreboard scoreboard, String name) {
        if (Bukkit.isPrimaryThread()) {
            return scoreboard.getObjective(name);
        }
        if (FIRE_AND_FORGET) {
            return null;
        }
        CompletableFuture<org.bukkit.scoreboard.Objective> future = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            try {
                future.complete(scoreboard.getObjective(name));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        try {
            return future.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to get objective '" + name + "'", e);
            return null;
        } catch (TimeoutException e) {
            if (FAIL_FAST) {
                throw new FoliaPatcherTimeoutException("Failed to get objective '" + name + "'", e);
            }
            LOGGER.log(Level.WARNING, "[FoliaPhantom] Timed out while getting objective '" + name + "'", e);
            return null;
        }
    }

    public static java.util.Set<org.bukkit.scoreboard.Objective> safeGetObjectivesByCriteria(Plugin plugin, org.bukkit.scoreboard.Scoreboard scoreboard, String criteria) {
        if (Bukkit.isPrimaryThread()) {
            return scoreboard.getObjectivesByCriteria(criteria);
        }
        if (FIRE_AND_FORGET) {
            return java.util.Collections.emptySet();
        }
        CompletableFuture<java.util.Set<org.bukkit.scoreboard.Objective>> future = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            try {
                future.complete(scoreboard.getObjectivesByCriteria(criteria));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        try {
            return future.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to get objectives by criteria '" + criteria + "'", e);
            return java.util.Collections.emptySet();
        } catch (TimeoutException e) {
            if (FAIL_FAST) {
                throw new FoliaPatcherTimeoutException("Failed to get objectives by criteria '" + criteria + "'", e);
            }
            LOGGER.log(Level.WARNING, "[FoliaPhantom] Timed out while getting objectives by criteria '" + criteria + "'", e);
            return java.util.Collections.emptySet();
        }
    }

    public static java.util.Set<org.bukkit.scoreboard.Objective> safeGetObjectives(Plugin plugin, org.bukkit.scoreboard.Scoreboard scoreboard) {
        if (Bukkit.isPrimaryThread()) {
            return scoreboard.getObjectives();
        }
        if (FIRE_AND_FORGET) {
            return java.util.Collections.emptySet();
        }
        CompletableFuture<java.util.Set<org.bukkit.scoreboard.Objective>> future = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            try {
                future.complete(scoreboard.getObjectives());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        try {
            return future.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to get objectives", e);
            return java.util.Collections.emptySet();
        } catch (TimeoutException e) {
            if (FAIL_FAST) {
                throw new FoliaPatcherTimeoutException("Failed to get objectives", e);
            }
            LOGGER.log(Level.WARNING, "[FoliaPhantom] Timed out while getting objectives", e);
            return java.util.Collections.emptySet();
        }
    }

    public static java.util.Set<String> safeGetEntries(Plugin plugin, org.bukkit.scoreboard.Scoreboard scoreboard) {
        if (Bukkit.isPrimaryThread()) {
            return scoreboard.getEntries();
        }
        if (FIRE_AND_FORGET) {
            return java.util.Collections.emptySet();
        }
        CompletableFuture<java.util.Set<String>> future = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            try {
                future.complete(scoreboard.getEntries());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        try {
            return future.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to get scoreboard entries", e);
            return java.util.Collections.emptySet();
        } catch (TimeoutException e) {
            if (FAIL_FAST) {
                throw new FoliaPatcherTimeoutException("Failed to get scoreboard entries", e);
            }
            LOGGER.log(Level.WARNING, "[FoliaPhantom] Timed out while getting scoreboard entries", e);
            return java.util.Collections.emptySet();
        }
    }

    public static org.bukkit.scoreboard.Team safeGetTeam(Plugin plugin, org.bukkit.scoreboard.Scoreboard scoreboard, String teamName) {
        if (Bukkit.isPrimaryThread()) {
            return scoreboard.getTeam(teamName);
        }
        if (FIRE_AND_FORGET) {
            return null;
        }
        CompletableFuture<org.bukkit.scoreboard.Team> future = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            try {
                future.complete(scoreboard.getTeam(teamName));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        try {
            return future.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to get team '" + teamName + "'", e);
            return null;
        } catch (TimeoutException e) {
            if (FAIL_FAST) {
                throw new FoliaPatcherTimeoutException("Failed to get team '" + teamName + "'", e);
            }
            LOGGER.log(Level.WARNING, "[FoliaPhantom] Timed out while getting team '" + teamName + "'", e);
            return null;
        }
    }

    public static java.util.Set<org.bukkit.scoreboard.Team> safeGetTeams(Plugin plugin, org.bukkit.scoreboard.Scoreboard scoreboard) {
        if (Bukkit.isPrimaryThread()) {
            return scoreboard.getTeams();
        }
        if (FIRE_AND_FORGET) {
            return java.util.Collections.emptySet();
        }
        CompletableFuture<java.util.Set<org.bukkit.scoreboard.Team>> future = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            try {
                future.complete(scoreboard.getTeams());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        try {
            return future.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to get teams", e);
            return java.util.Collections.emptySet();
        } catch (TimeoutException e) {
            if (FAIL_FAST) {
                throw new FoliaPatcherTimeoutException("Failed to get teams", e);
            }
            LOGGER.log(Level.WARNING, "[FoliaPhantom] Timed out while getting teams", e);
            return java.util.Collections.emptySet();
        }
    }

    // --- Thread-Safe Objective READ Operations ---

    public static org.bukkit.scoreboard.Score safeGetScore(Plugin plugin, org.bukkit.scoreboard.Objective objective, String entry) {
        if (Bukkit.isPrimaryThread()) {
            return objective.getScore(entry);
        }
        if (FIRE_AND_FORGET) {
            return null;
        }
        CompletableFuture<org.bukkit.scoreboard.Score> future = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            try {
                future.complete(objective.getScore(entry));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        try {
            return future.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to get score for entry '" + entry + "'", e);
            return null;
        } catch (TimeoutException e) {
            if (FAIL_FAST) {
                throw new FoliaPatcherTimeoutException("Failed to get score for entry '" + entry + "'", e);
            }
            LOGGER.log(Level.WARNING, "[FoliaPhantom] Timed out while getting score for entry '" + entry + "'", e);
            return null;
        }
    }

    // --- Thread-Safe Team READ Operations ---

    public static java.util.Set<String> safeGetTeamEntries(Plugin plugin, org.bukkit.scoreboard.Team team) {
        if (Bukkit.isPrimaryThread()) {
            return team.getEntries();
        }
        if (FIRE_AND_FORGET) {
            return java.util.Collections.emptySet();
        }
        CompletableFuture<java.util.Set<String>> future = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            try {
                future.complete(team.getEntries());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        try {
            return future.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to get team entries", e);
            return java.util.Collections.emptySet();
        } catch (TimeoutException e) {
            if (FAIL_FAST) {
                throw new FoliaPatcherTimeoutException("Failed to get team entries", e);
            }
            LOGGER.log(Level.WARNING, "[FoliaPhantom] Timed out while getting team entries", e);
            return java.util.Collections.emptySet();
        }
    }

    public static java.util.Set<org.bukkit.OfflinePlayer> safeGetPlayers(Plugin plugin, org.bukkit.scoreboard.Team team) {
        if (Bukkit.isPrimaryThread()) {
            return team.getPlayers();
        }
        if (FIRE_AND_FORGET) {
            return java.util.Collections.emptySet();
        }
        CompletableFuture<java.util.Set<org.bukkit.OfflinePlayer>> future = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            try {
                future.complete(team.getPlayers());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        try {
            return future.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to get team players", e);
            return java.util.Collections.emptySet();
        } catch (TimeoutException e) {
            if (FAIL_FAST) {
                throw new FoliaPatcherTimeoutException("Failed to get team players", e);
            }
            LOGGER.log(Level.WARNING, "[FoliaPhantom] Timed out while getting team players", e);
            return java.util.Collections.emptySet();
        }
    }

    public static int safeGetSize(Plugin plugin, org.bukkit.scoreboard.Team team) {
        if (Bukkit.isPrimaryThread()) {
            return team.getSize();
        }
        if (FIRE_AND_FORGET) {
            return 0;
        }
        CompletableFuture<Integer> future = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            try {
                future.complete(team.getSize());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        try {
            return future.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to get team size", e);
            return 0;
        } catch (TimeoutException e) {
            if (FAIL_FAST) {
                throw new FoliaPatcherTimeoutException("Failed to get team size", e);
            }
            LOGGER.log(Level.WARNING, "[FoliaPhantom] Timed out while getting team size", e);
            return 0;
        }
    }

    // --- Thread-Safe Inventory Operations ---

    /**
     * Safely sets an item in an inventory slot.
     * Schedules the operation on the appropriate region scheduler if not on the main thread.
     */
    public static void safeSetItem(Plugin plugin, org.bukkit.inventory.Inventory inventory, int slot, org.bukkit.inventory.ItemStack item) {
        if (Bukkit.isPrimaryThread()) {
            inventory.setItem(slot, item);
        } else {
            org.bukkit.inventory.InventoryHolder holder = inventory.getHolder();
            Location loc = (holder instanceof Entity) ? ((Entity) holder).getLocation() : inventory.getLocation();
            if (loc != null) {
                Bukkit.getRegionScheduler().run(plugin, loc, ignored -> inventory.setItem(slot, item));
            } else {
                Bukkit.getGlobalRegionScheduler().run(plugin, ignored -> inventory.setItem(slot, item));
            }
        }
    }

    /**
     * Safely adds items to an inventory.
     * Schedules the operation and blocks for the result if not on the main thread.
     */
    public static java.util.HashMap<Integer, org.bukkit.inventory.ItemStack> safeAddItem(Plugin plugin, org.bukkit.inventory.Inventory inventory, org.bukkit.inventory.ItemStack... items) {
        if (Bukkit.isPrimaryThread()) {
            return inventory.addItem(items);
        } else {
            java.util.function.Consumer<ScheduledTask> task = ignored -> {
                inventory.addItem(items);
            };

            org.bukkit.inventory.InventoryHolder holder = inventory.getHolder();
            Location loc = (holder instanceof Entity) ? ((Entity) holder).getLocation() : inventory.getLocation();

            if (FIRE_AND_FORGET) {
                if (loc != null) {
                    Bukkit.getRegionScheduler().run(plugin, loc, task);
                } else {
                    Bukkit.getGlobalRegionScheduler().run(plugin, task);
                }
                return new java.util.HashMap<>();
            }

            CompletableFuture<java.util.HashMap<Integer, org.bukkit.inventory.ItemStack>> future = new CompletableFuture<>();
            java.util.function.Consumer<ScheduledTask> futureTask = ignored -> {
                try {
                    future.complete(inventory.addItem(items));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            };

            if (loc != null) {
                Bukkit.getRegionScheduler().run(plugin, loc, futureTask);
            } else {
                Bukkit.getGlobalRegionScheduler().run(plugin, futureTask);
            }

            try {
                return future.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to add item to inventory", e);
                // On non-timeout failure, return original items as per Bukkit API contract
                java.util.HashMap<Integer, org.bukkit.inventory.ItemStack> remainingItems = new java.util.HashMap<>();
                for (int i = 0; i < items.length; i++) {
                    remainingItems.put(i, items[i]);
                }
                return remainingItems;
            } catch (TimeoutException e) {
                if (FAIL_FAST) {
                    throw new FoliaPatcherTimeoutException("Failed to add item to inventory", e);
                }
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to add item to inventory", e);
                java.util.HashMap<Integer, org.bukkit.inventory.ItemStack> remainingItems = new java.util.HashMap<>();
                for (int i = 0; i < items.length; i++) {
                    remainingItems.put(i, items[i]);
                }
                return remainingItems;
            }
        }
    }

    /**
     * Safely clears an inventory.
     * Schedules the operation on the appropriate region scheduler if not on the main thread.
     */
    public static void safeClear(Plugin plugin, org.bukkit.inventory.Inventory inventory) {
        if (Bukkit.isPrimaryThread()) {
            inventory.clear();
        } else {
            org.bukkit.inventory.InventoryHolder holder = inventory.getHolder();
            Location loc = (holder instanceof Entity) ? ((Entity) holder).getLocation() : inventory.getLocation();
            if (loc != null) {
                Bukkit.getRegionScheduler().run(plugin, loc, ignored -> inventory.clear());
            } else {
                Bukkit.getGlobalRegionScheduler().run(plugin, ignored -> inventory.clear());
            }
        }
    }

    // --- Thread-Safe Player Operations ---

    public static void safeSendMessage(Plugin plugin, org.bukkit.entity.Player player, String message) {
        if (Bukkit.isPrimaryThread()) {
            player.sendMessage(message);
        } else {
            player.getScheduler().run(plugin, task -> player.sendMessage(message), null);
        }
    }

    public static void safeSendMessages(Plugin plugin, org.bukkit.entity.Player player, String[] messages) {
        if (Bukkit.isPrimaryThread()) {
            player.sendMessage(messages);
        } else {
            player.getScheduler().run(plugin, task -> player.sendMessage(messages), null);
        }
    }

    public static void safeKickPlayer(Plugin plugin, org.bukkit.entity.Player player, String message) {
        if (Bukkit.isPrimaryThread()) {
            player.kickPlayer(message);
        } else {
            player.getScheduler().run(plugin, task -> player.kickPlayer(message), null);
        }
    }

    public static void safeSetHealth(Plugin plugin, org.bukkit.entity.Player player, double health) {
        if (Bukkit.isPrimaryThread()) {
            player.setHealth(health);
        } else {
            player.getScheduler().run(plugin, task -> player.setHealth(health), null);
        }
    }

    public static void safeSetFoodLevel(Plugin plugin, org.bukkit.entity.Player player, int level) {
        if (Bukkit.isPrimaryThread()) {
            player.setFoodLevel(level);
        } else {
            player.getScheduler().run(plugin, task -> player.setFoodLevel(level), null);
        }
    }

    public static void safeGiveExp(Plugin plugin, org.bukkit.entity.Player player, int amount) {
        if (Bukkit.isPrimaryThread()) {
            player.giveExp(amount);
        } else {
            player.getScheduler().run(plugin, task -> player.giveExp(amount), null);
        }
    }

    public static void safeSetLevel(Plugin plugin, org.bukkit.entity.Player player, int level) {
        if (Bukkit.isPrimaryThread()) {
            player.setLevel(level);
        } else {
            player.getScheduler().run(plugin, task -> player.setLevel(level), null);
        }
    }

    public static void safePlaySound(Plugin plugin, org.bukkit.entity.Player player, Location location, Sound sound, float volume, float pitch) {
        if (Bukkit.isPrimaryThread()) {
            player.playSound(location, sound, volume, pitch);
        } else {
            player.getScheduler().run(plugin, task -> player.playSound(location, sound, volume, pitch), null);
        }
    }

    public static void safeSendTitle(Plugin plugin, org.bukkit.entity.Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (Bukkit.isPrimaryThread()) {
            player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        } else {
            player.getScheduler().run(plugin, task -> player.sendTitle(title, subtitle, fadeIn, stay, fadeOut), null);
        }
    }

    public static org.bukkit.inventory.InventoryView safeOpenInventory(Plugin plugin, org.bukkit.entity.Player player, org.bukkit.inventory.Inventory inventory) {
        if (Bukkit.isPrimaryThread()) {
            return player.openInventory(inventory);
        } else {
            if (FIRE_AND_FORGET) {
                player.getScheduler().run(plugin, task -> player.openInventory(inventory), null);
                return null;
            }
            CompletableFuture<org.bukkit.inventory.InventoryView> future = new CompletableFuture<>();
            player.getScheduler().run(plugin, task -> {
                try {
                    future.complete(player.openInventory(inventory));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }, null);
            try {
                return future.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to open inventory for player " + player.getName(), e);
                return null;
            } catch (TimeoutException e) {
                if (FAIL_FAST) {
                    throw new FoliaPatcherTimeoutException("Failed to open inventory for player " + player.getName(), e);
                }
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to open inventory for player " + player.getName(), e);
                return null;
            }
        }
    }

    public static void safeCloseInventory(Plugin plugin, org.bukkit.entity.Player player) {
        if (Bukkit.isPrimaryThread()) {
            player.closeInventory();
        } else {
            player.getScheduler().run(plugin, task -> player.closeInventory(), null);
        }
    }

    // --- Thread-Safe Entity Operations ---

    public static void safeRemove(Plugin plugin, Entity entity) {
        if (Bukkit.isPrimaryThread()) {
            entity.remove();
        } else {
            entity.getScheduler().run(plugin, task -> entity.remove(), null);
        }
    }

    public static void safeSetVelocity(Plugin plugin, Entity entity, org.bukkit.util.Vector velocity) {
        if (Bukkit.isPrimaryThread()) {
            entity.setVelocity(velocity);
        } else {
            entity.getScheduler().run(plugin, task -> entity.setVelocity(velocity), null);
        }
    }

    public static boolean safeTeleportEntity(Plugin plugin, Entity entity, Location location) {
        if (Bukkit.isPrimaryThread()) {
            return entity.teleport(location);
        } else {
            if (FIRE_AND_FORGET) {
                entity.getScheduler().run(plugin, task -> entity.teleport(location), null);
                return true;
            }
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            entity.getScheduler().run(plugin, task -> {
                try {
                    future.complete(entity.teleport(location));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }, null);
            try {
                return future.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to teleport entity " + entity.getUniqueId(), e);
                return false;
            } catch (TimeoutException e) {
                if (FAIL_FAST) {
                    throw new FoliaPatcherTimeoutException("Failed to teleport entity " + entity.getUniqueId(), e);
                }
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to teleport entity " + entity.getUniqueId(), e);
                return false;
            }
        }
    }

    public static void safeSetFireTicks(Plugin plugin, Entity entity, int ticks) {
        if (Bukkit.isPrimaryThread()) {
            entity.setFireTicks(ticks);
        } else {
            entity.getScheduler().run(plugin, task -> entity.setFireTicks(ticks), null);
        }
    }

    public static void safeSetCustomName(Plugin plugin, Entity entity, String name) {
        if (Bukkit.isPrimaryThread()) {
            entity.setCustomName(name);
        } else {
            entity.getScheduler().run(plugin, task -> entity.setCustomName(name), null);
        }
    }

    public static void safeSetGravity(Plugin plugin, Entity entity, boolean gravity) {
        if (Bukkit.isPrimaryThread()) {
            entity.setGravity(gravity);
        } else {
            entity.getScheduler().run(plugin, task -> entity.setGravity(gravity), null);
        }
    }

    // --- Legacy / Int-returning Method Mappings ---

    public static int scheduleSyncDelayedTask(BukkitScheduler s, Plugin p, Runnable r, long d) {
        return runTaskLater(s, p, r, d).getTaskId();
    }

    public static int scheduleSyncRepeatingTask(BukkitScheduler s, Plugin p, Runnable r, long d, long pr) {
        return runTaskTimer(s, p, r, d, pr).getTaskId();
    }

    public static int scheduleAsyncDelayedTask(BukkitScheduler s, Plugin p, Runnable r, long d) {
        return runTaskLaterAsynchronously(s, p, r, d).getTaskId();
    }

    public static int scheduleAsyncRepeatingTask(BukkitScheduler s, Plugin p, Runnable r, long d, long pr) {
        return runTaskTimerAsynchronously(s, p, r, d, pr).getTaskId();
    }

    public static void cancelTask(BukkitScheduler ignored, int taskId) {
        cancelTaskById(taskId);
    }

    public static void cancelTasks(BukkitScheduler ignored, Plugin plugin) {
        runningTasks.entrySet().removeIf(entry -> {
            ScheduledTask task = entry.getValue();
            if (task.getOwningPlugin().equals(plugin)) {
                if (!task.isCancelled())
                    task.cancel();
                return true;
            }
            return false;
        });
    }

    public static void cancelAllTasks() {
        runningTasks.values().forEach(task -> {
            if (!task.isCancelled())
                task.cancel();
        });
        runningTasks.clear();
    }

    // --- Thread-Safe Event Calling ---

    /**
     * Safely calls a Bukkit event, dispatching it to the appropriate scheduler
     * based on the event's context (player, entity, location, etc.).
     * This ensures thread safety when events are called from async tasks.
     *
     * @param plugin The plugin calling the event.
     * @param event The event to be called.
     */
    public static void safeCallEvent(Plugin plugin, Event event) {
        if (Bukkit.isPrimaryThread()) {
            Bukkit.getPluginManager().callEvent(event);
            return;
        }

        LOGGER.fine("[FoliaPhantom] Intercepted async event call: " + event.getEventName());

        CompletableFuture<Void> future = new CompletableFuture<>();
        Runnable task = () -> {
            try {
                Bukkit.getPluginManager().callEvent(event);
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        };

        if (event instanceof PlayerEvent) {
            ((PlayerEvent) event).getPlayer().getScheduler().run(plugin, t -> task.run(), null);
        } else if (event instanceof EntityEvent) {
            ((EntityEvent) event).getEntity().getScheduler().run(plugin, t -> task.run(), null);
        } else if (event instanceof BlockEvent) {
            Block block = ((BlockEvent) event).getBlock();
            Bukkit.getRegionScheduler().run(plugin, block.getLocation(), t -> task.run());
        } else {
            // Fallback to global scheduler for events without specific context
            Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run());
        }

        // If aggressive optimization is disabled, block until the event is processed
        // to maintain the original execution flow. A timeout is used to prevent hangs.
        if (!AGGRESSIVE_EVENT_OPTIMIZATION) {
            try {
                future.get(API_TIMEOUT_MS * 50, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.log(Level.SEVERE, "[FoliaPhantom] Failed to process event " + event.getEventName() + " synchronously.", e);
            } catch (TimeoutException e) {
                if (FAIL_FAST) {
                    throw new FoliaPatcherTimeoutException("Failed to process event " + event.getEventName() + " synchronously.", e);
                }
                LOGGER.log(Level.SEVERE, "[FoliaPhantom] Failed to process event " + event.getEventName() + " synchronously.", e);
            }
        }
    }

    /**
     * Safely calls a Bukkit event, providing an option for a "fire-and-forget"
     * mechanism for performance-critical scenarios.
     *
     * @param pluginManager The plugin manager instance.
     * @param event The event to be called.
     */
    public static void safeCallEvent(Plugin plugin, PluginManager pluginManager, Event event) {
        // If fire-and-forget is enabled and the event is in the designated set,
        // execute it asynchronously without waiting.
        if (FIRE_AND_FORGET && FIRE_AND_FORGET_EVENTS.contains(event.getClass().getName())) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, () -> pluginManager.callEvent(event));
            return;
        }

        // Otherwise, fall back to the existing safe event calling logic.
        safeCallEvent(plugin, event);
    }
}
