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

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
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
            try {
                // Block for a short time to prevent server hangs
                return future.get(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
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
            try {
                // We use teleportAsync and wait for it to complete.
                return player.teleportAsync(location).get(1, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to teleport player " + player.getName(), e);
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
            try {
                return future.get(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
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
            try {
                return future.get(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
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
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> future.complete(team.removeEntry(entry)));
            try {
                return future.get(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
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

    // --- Thread-Safe Inventory Operations ---

    /**
     * Safely creates an inventory.
     * Schedules the operation on the global region scheduler and blocks for the result if not on the main thread.
     */
    public static org.bukkit.inventory.Inventory safeCreateInventory(Plugin plugin, org.bukkit.inventory.InventoryHolder owner, int size) {
        if (Bukkit.isPrimaryThread()) {
            return Bukkit.createInventory(owner, size);
        } else {
            CompletableFuture<org.bukkit.inventory.Inventory> future = new CompletableFuture<>();
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                try {
                    future.complete(Bukkit.createInventory(owner, size));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            try {
                return future.get(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to create inventory", e);
                return null;
            }
        }
    }

    /**
     * Safely creates an inventory with a title.
     * Schedules the operation on the global region scheduler and blocks for the result if not on the main thread.
     */
    public static org.bukkit.inventory.Inventory safeCreateInventory(Plugin plugin, org.bukkit.inventory.InventoryHolder owner, int size, String title) {
        if (Bukkit.isPrimaryThread()) {
            return Bukkit.createInventory(owner, size, title);
        } else {
            CompletableFuture<org.bukkit.inventory.Inventory> future = new CompletableFuture<>();
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                try {
                    future.complete(Bukkit.createInventory(owner, size, title));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            try {
                return future.get(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to create inventory with title '" + title + "'", e);
                return null;
            }
        }
    }

    /**
     * Safely creates an inventory with a specific type.
     * Schedules the operation on the global region scheduler and blocks for the result if not on the main thread.
     */
    public static org.bukkit.inventory.Inventory safeCreateInventory(Plugin plugin, org.bukkit.inventory.InventoryHolder owner, InventoryType type) {
        if (Bukkit.isPrimaryThread()) {
            return Bukkit.createInventory(owner, type);
        } else {
            CompletableFuture<org.bukkit.inventory.Inventory> future = new CompletableFuture<>();
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                try {
                    future.complete(Bukkit.createInventory(owner, type));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            try {
                return future.get(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to create inventory of type " + type, e);
                return null;
            }
        }
    }

    /**
     * Safely creates an inventory with a specific type and title.
     * Schedules the operation on the global region scheduler and blocks for the result if not on the main thread.
     */
    public static org.bukkit.inventory.Inventory safeCreateInventory(Plugin plugin, org.bukkit.inventory.InventoryHolder owner, InventoryType type, String title) {
        if (Bukkit.isPrimaryThread()) {
            return Bukkit.createInventory(owner, type, title);
        } else {
            CompletableFuture<org.bukkit.inventory.Inventory> future = new CompletableFuture<>();
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                try {
                    future.complete(Bukkit.createInventory(owner, type, title));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            try {
                return future.get(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                LOGGER.log(Level.WARNING, "[FoliaPhantom] Failed to create inventory of type " + type + " with title '" + title + "'", e);
                return null;
            }
        }
    }

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
            CompletableFuture<java.util.HashMap<Integer, org.bukkit.inventory.ItemStack>> future = new CompletableFuture<>();
            java.util.function.Consumer<ScheduledTask> task = ignored -> {
                try {
                    future.complete(inventory.addItem(items));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            };

            org.bukkit.inventory.InventoryHolder holder = inventory.getHolder();
            Location loc = (holder instanceof Entity) ? ((Entity) holder).getLocation() : inventory.getLocation();
            if (loc != null) {
                Bukkit.getRegionScheduler().run(plugin, loc, task);
            } else {
                Bukkit.getGlobalRegionScheduler().run(plugin, task);
            }

            try {
                return future.get(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
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
}
