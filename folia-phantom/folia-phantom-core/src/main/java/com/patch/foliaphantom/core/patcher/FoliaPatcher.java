/*
 * Folia Phantom - Runtime Patcher Bridge
 *
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.patcher;

import com.patch.foliaphantom.core.exception.FoliaPatcherTimeoutException;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.*;
import org.bukkit.scheduler.*;
import org.bukkit.potion.*;
import org.bukkit.generator.*;
import org.bukkit.attribute.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import java.util.logging.*;

public final class FoliaPatcher {
  public static final boolean FAIL_FAST = false;
  public static final boolean AGGRESSIVE_EVENT_OPTIMIZATION = false;
  public static final boolean FIRE_AND_FORGET = false;
  public static final long API_TIMEOUT_MS = 100;
  private static final Set<String> FIRE_AND_FORGET_EVENTS = Collections.emptySet();
  private static final Logger LOGGER = Logger.getLogger("FoliaPhantom-Patcher");
  private static final ExecutorService worldGenExecutor = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "FoliaPhantom-WorldGen-Worker"); t.setDaemon(true); return t; });
  private static final AtomicInteger taskIdCounter = new AtomicInteger(1000000);
  private static final Map<Integer, ScheduledTask> runningTasks = new ConcurrentHashMap<>();

  public static volatile Collection<? extends Player> _cp = Collections.emptyList();
  public static volatile List<World> _cw = Collections.emptyList();
  public static volatile Map<String, Player> _cps = Collections.emptyMap();
  public static volatile Map<UUID, Player> _cpu = Collections.emptyMap();
  public static volatile Map<String, World> _cwn = Collections.emptyMap();
  public static volatile Map<UUID, World> _cwu = Collections.emptyMap();
  public static volatile boolean _ii = false;

  public static void _i(Plugin p) {
    if (_ii) return; _ii = true;
    Bukkit.getGlobalRegionScheduler().runAtFixedRate(p, t -> {
      Collection<? extends Player> online = Bukkit.getOnlinePlayers();
      List<World> worlds = Bukkit.getWorlds();
      Map<String, Player> nps = new HashMap<>(online.size());
      Map<UUID, Player> ups = new HashMap<>(online.size());
      for (Player pl : online) { nps.put(pl.getName(), pl); ups.put(pl.getUniqueId(), pl); }
      Map<String, World> nws = new HashMap<>(worlds.size());
      Map<UUID, World> uws = new HashMap<>(worlds.size());
      for (World w : worlds) { nws.put(w.getName(), w); uws.put(w.getUID(), w); }
      _cp = Collections.unmodifiableCollection(new ArrayList<>(online));
      _cw = Collections.unmodifiableList(new ArrayList<>(worlds));
      _cps = Collections.unmodifiableMap(nps);
      _cpu = Collections.unmodifiableMap(ups);
      _cwn = Collections.unmodifiableMap(nws);
      _cwu = Collections.unmodifiableMap(uws);
    }, 1L, 1L);
  }

  public static Collection<? extends Player> _o() { return _cp; }
  public static List<World> _w() { return _cw; }
  public static Player _ps(String n) { return _cps.get(n); }
  public static Player _pu(UUID u) { return _cpu.get(u); }
  public static World _ws(String n) { return _cwn.get(n); }
  public static World _wu(UUID u) { return _cwu.get(u); }

  public static <T> T _b(Plugin p, Callable<T> c) {
    if (Bukkit.isPrimaryThread()) try { return c.call(); } catch (Exception e) { throw new RuntimeException(e); }
    CompletableFuture<T> f = new CompletableFuture<>();
    Bukkit.getGlobalRegionScheduler().run(p, t -> { try { f.complete(c.call()); } catch (Exception e) { f.completeExceptionally(e); } });
    try { return f.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS); } catch (Exception e) { return null; }
  }

  public static <T> T _be(Plugin p, Entity e, Callable<T> c) {
    if (Bukkit.isOwnedByCurrentRegion(e)) try { return c.call(); } catch (Exception ex) { throw new RuntimeException(ex); }
    CompletableFuture<T> f = new CompletableFuture<>();
    e.getScheduler().run(p, t -> { try { f.complete(c.call()); } catch (Exception ex) { f.completeExceptionally(ex); } }, null);
    try { return f.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS); } catch (Exception ex) { return null; }
  }

  public static <T> T _bl(Plugin p, Location l, Callable<T> c) {
    if (Bukkit.isOwnedByCurrentRegion(l)) try { return c.call(); } catch (Exception ex) { throw new RuntimeException(ex); }
    CompletableFuture<T> f = new CompletableFuture<>();
    Bukkit.getRegionScheduler().run(p, l, t -> { try { f.complete(c.call()); } catch (Exception ex) { f.completeExceptionally(ex); } });
    try { return f.get(API_TIMEOUT_MS, TimeUnit.MILLISECONDS); } catch (Exception ex) { return null; }
  }

  public static void _g(Plugin p, Runnable r) { if (Bukkit.isPrimaryThread()) r.run(); else Bukkit.getGlobalRegionScheduler().run(p, t -> r.run()); }
  public static void _r(Plugin p, Location l, Runnable r) { if (Bukkit.isOwnedByCurrentRegion(l)) r.run(); else Bukkit.getRegionScheduler().run(p, l, t -> r.run()); }
  public static void _e(Plugin p, Entity e, Runnable r) { if (Bukkit.isOwnedByCurrentRegion(e)) r.run(); else e.getScheduler().run(p, t -> r.run(), null); }

  public static boolean _b_dc(Plugin p, org.bukkit.command.CommandSender s, String c) { Boolean r = _b(p, () -> Bukkit.dispatchCommand(s, c)); return r != null && r; }
  public static OfflinePlayer _b_gop(Plugin p, String n) { return _b(p, () -> Bukkit.getOfflinePlayer(n)); }
  public static OfflinePlayer _b_gop(Plugin p, UUID u) { return _b(p, () -> Bukkit.getOfflinePlayer(u)); }

  public static boolean _ape(Plugin p, LivingEntity e, PotionEffect pe) { if (Bukkit.isOwnedByCurrentRegion(e)) return e.addPotionEffect(pe); Boolean r = _be(p, e, () -> e.addPotionEffect(pe)); return r != null && r; }
  public static void _rpe(Plugin p, LivingEntity e, PotionEffectType t) { if (Bukkit.isOwnedByCurrentRegion(e)) e.removePotionEffect(t); else _e(p, e, () -> e.removePotionEffect(t)); }
  public static boolean _hpe(Plugin p, LivingEntity e, PotionEffectType t) { if (Bukkit.isOwnedByCurrentRegion(e)) return e.hasPotionEffect(t); Boolean r = _be(p, e, () -> e.hasPotionEffect(t)); return r != null && r; }
  public static PotionEffect _gpe(Plugin p, LivingEntity e, PotionEffectType t) { if (Bukkit.isOwnedByCurrentRegion(e)) return e.getPotionEffect(t); return _be(p, e, () -> e.getPotionEffect(t)); }
  public static AttributeInstance _ga(Plugin p, Attributable a, Attribute at) { if (a instanceof Entity e) { if (Bukkit.isOwnedByCurrentRegion(e)) return a.getAttribute(at); return _be(p, e, () -> a.getAttribute(at)); } return _b(p, () -> a.getAttribute(at)); }

  public static void _rem(Plugin p, Entity e) { _e(p, e, e::remove); }
  public static void _sv(Plugin p, Entity e, org.bukkit.util.Vector v) { _e(p, e, () -> e.setVelocity(v)); }
  public static boolean _te(Plugin p, Entity e, Location l) { if (Bukkit.isOwnedByCurrentRegion(e)) return e.teleport(l); Boolean r = _be(p, e, () -> e.teleport(l)); return r != null && r; }
  public static boolean _ap(Plugin p, Entity e, Entity pa) { if (Bukkit.isOwnedByCurrentRegion(e)) return e.addPassenger(pa); Boolean r = _be(p, e, () -> e.addPassenger(pa)); return r != null && r; }
  public static boolean _rp(Plugin p, Entity e, Entity pa) { if (Bukkit.isOwnedByCurrentRegion(e)) return e.removePassenger(pa); Boolean r = _be(p, e, () -> e.removePassenger(pa)); return r != null && r; }
  public static boolean _ej(Plugin p, Entity e) { if (Bukkit.isOwnedByCurrentRegion(e)) return e.eject(); Boolean r = _be(p, e, e::eject); return r != null && r; }

  public static void _st(Plugin p, Block b, Material m) { _r(p, b.getLocation(), () -> b.setType(m)); }
  public static void _stwp(Plugin p, Block b, Material m, boolean ph) { _r(p, b.getLocation(), () -> b.setType(m, ph)); }
  public static void _bd(Plugin p, Block b, BlockData d) { _r(p, b.getLocation(), () -> b.setBlockData(d)); }
  public static void _bdwp(Plugin p, Block b, BlockData d, boolean ph) { _r(p, b.getLocation(), () -> b.setBlockData(d, ph)); }
  public static boolean _up(Plugin p, BlockState s, boolean f, boolean ph) { if (Bukkit.isOwnedByCurrentRegion(s.getLocation())) return s.update(f, ph); Boolean r = _bl(p, s.getLocation(), () -> s.update(f, ph)); return r != null && r; }

  public static void _bm(Plugin p, String m) { _g(p, () -> Bukkit.broadcastMessage(m)); }
  public static List<Player> _gp(Plugin p, World w) { return _b(p, w::getPlayers); }

  public static void _sm(Plugin p, Player pl, String m) { _e(p, pl, () -> pl.sendMessage(m)); }
  public static void _sm(Plugin p, Player pl, String[] m) { _e(p, pl, () -> pl.sendMessage(m)); }
  public static void _kp(Plugin p, Player pl, String m) { _e(p, pl, () -> pl.kickPlayer(m)); }
  public static void _sh(Plugin p, Player pl, double h) { _e(p, pl, () -> pl.setHealth(h)); }
  public static void _sf(Plugin p, Player pl, int f) { _e(p, pl, () -> pl.setFoodLevel(f)); }
  public static void _ge(Plugin p, Player pl, int e) { _e(p, pl, () -> pl.giveExp(e)); }
  public static void _sl(Plugin p, Player pl, int l) { _e(p, pl, () -> pl.setLevel(l)); }
  public static void _psd(Plugin p, Player pl, Location l, Sound s, float v, float pi) { _e(p, pl, () -> pl.playSound(l, s, v, pi)); }
  public static void _stt(Plugin p, Player pl, String t, String s, int fi, int st, int fo) { _e(p, pl, () -> pl.sendTitle(t, s, fi, st, fo)); }
  public static void _ci(Plugin p, Player pl) { _e(p, pl, pl::closeInventory); }
  public static InventoryView _oi(Plugin p, Player pl, Inventory i) { if (Bukkit.isOwnedByCurrentRegion(pl)) return pl.openInventory(i); return _be(p, pl, () -> pl.openInventory(i)); }

  public static <T extends Entity> T _ss(Plugin p, World w, Location l, Class<T> c) { if (Bukkit.isOwnedByCurrentRegion(l)) return w.spawn(l, c); return _bl(p, l, () -> w.spawn(l, c)); }
  public static void _cl(Plugin p, World w, int x, int z, boolean g) { if (Bukkit.isOwnedByCurrentRegion(w, x, z)) w.loadChunk(x, z, g); else Bukkit.getRegionScheduler().run(p, w, x, z, t -> w.loadChunk(x, z, g)); }
  public static void _cu(Plugin p, World w, int x, int z, boolean s) { if (Bukkit.isOwnedByCurrentRegion(w, x, z)) w.unloadChunk(x, z, s); else Bukkit.getRegionScheduler().run(p, w, x, z, t -> w.unloadChunk(x, z, s)); }
  public static Item _di(Plugin p, World w, Location l, ItemStack i) { if (Bukkit.isOwnedByCurrentRegion(l)) return w.dropItem(l, i); return _bl(p, l, () -> w.dropItem(l, i)); }
  public static Item _dn(Plugin p, World w, Location l, ItemStack i) { if (Bukkit.isOwnedByCurrentRegion(l)) return w.dropItemNaturally(l, i); return _bl(p, l, () -> w.dropItemNaturally(l, i)); }
  public static boolean _ex(Plugin p, World w, Location l, float po, boolean f, boolean b) { if (Bukkit.isOwnedByCurrentRegion(l)) return w.createExplosion(l, po, f, b); Boolean r = _bl(p, l, () -> w.createExplosion(l, po, f, b)); return r != null && r; }
  public static <T> void _pe(Plugin p, World w, Location l, Effect e, T d) { _r(p, l, () -> w.playEffect(l, e, d)); }
  public static void _sd(Plugin p, World w, Location l, Sound s, float v, float pi) { _r(p, l, () -> w.playSound(l, s, v, pi)); }
  public static LightningStrike _sl(Plugin p, World w, Location l) { if (Bukkit.isOwnedByCurrentRegion(l)) return w.strikeLightning(l); return _bl(p, l, () -> w.strikeLightning(l)); }
  public static boolean _gt(Plugin p, World w, Location l, TreeType t) { if (Bukkit.isOwnedByCurrentRegion(l)) return w.generateTree(l, t); Boolean r = _bl(p, l, () -> w.generateTree(l, t)); return r != null && r; }
  public static <T> boolean _sr(Plugin p, World w, GameRule<T> r, T v) { return _b(p, () -> w.setGameRule(r, v)); }
  public static Entity[] _ce(Plugin p, Chunk c) { return _b(p, () -> c.getEntities()); }
  public static List<Entity> _ge(Plugin p, World w) { return _b(p, w::getEntities); }
  public static List<LivingEntity> _gl(Plugin p, World w) { return _b(p, w::getLivingEntities); }
  public static Collection<Entity> _gne(Plugin p, World w, Location l, double x, double y, double z) { return _bl(p, l, () -> w.getNearbyEntities(l, x, y, z)); }
  public static Block _hb(Plugin p, World w, int x, int z) { return _bl(p, new Location(w, x, 0, z), () -> w.getHighestBlockAt(x, z)); }

  public static final String CACHED_SERVER_VERSION = Bukkit.getVersion();
  public static final String CACHED_BUKKIT_VERSION = Bukkit.getBukkitVersion();
  private FoliaPatcher() {}

  public static ChunkGenerator getDefaultWorldGenerator(Plugin plugin, String worldName, String id) { ChunkGenerator g = plugin.getDefaultWorldGenerator(worldName, id); return g == null ? null : new FoliaChunkGenerator(g); }
  public static World createWorld(WorldCreator c) { Future<World> f = worldGenExecutor.submit(c::createWorld); try { return f.get(); } catch (Exception e) { return null; } }

  public static class FoliaChunkGenerator extends ChunkGenerator {
    private final ChunkGenerator o;
    public FoliaChunkGenerator(ChunkGenerator o) { this.o = o; }
    @Override public ChunkData generateChunkData(World w, Random r, int x, int z, BiomeGrid b) { return o.generateChunkData(w, r, x, z, b); }
    @Override public boolean shouldGenerateNoise() { return o.shouldGenerateNoise(); }
    @Override public boolean shouldGenerateSurface() { return o.shouldGenerateSurface(); }
    @Override public boolean shouldGenerateBedrock() { return o.shouldGenerateBedrock(); }
    @Override public boolean shouldGenerateCaves() { return o.shouldGenerateCaves(); }
    @Override public boolean shouldGenerateDecorations() { return o.shouldGenerateDecorations(); }
    @Override public boolean shouldGenerateMobs() { return o.shouldGenerateMobs(); }
    @Override public Location getFixedSpawnLocation(World w, Random r) { return o.getFixedSpawnLocation(w, r); }
  }

  private static void cancelTaskById(int taskId) { ScheduledTask task = runningTasks.remove(taskId); if (task != null && !task.isCancelled()) task.cancel(); }
  public static BukkitTask runTask(Plugin p, Runnable r) { int id = taskIdCounter.getAndIncrement(); ScheduledTask t = Bukkit.getGlobalRegionScheduler().run(p, st -> { try { r.run(); } finally { runningTasks.remove(id); } }); runningTasks.put(id, t); return new FoliaBukkitTask(id, p, FoliaPatcher::cancelTaskById, true, t); }
  public static BukkitTask runTaskLater(Plugin p, Runnable r, long d) { int id = taskIdCounter.getAndIncrement(); ScheduledTask t = Bukkit.getGlobalRegionScheduler().runDelayed(p, st -> { try { r.run(); } finally { runningTasks.remove(id); } }, Math.max(1, d)); runningTasks.put(id, t); return new FoliaBukkitTask(id, p, FoliaPatcher::cancelTaskById, true, t); }
  public static BukkitTask runTaskTimer(Plugin p, Runnable r, long d, long pr) { int id = taskIdCounter.getAndIncrement(); ScheduledTask t = Bukkit.getGlobalRegionScheduler().runAtFixedRate(p, st -> r.run(), Math.max(1, d), Math.max(1, pr)); runningTasks.put(id, t); return new FoliaBukkitTask(id, p, FoliaPatcher::cancelTaskById, true, t); }
  public static BukkitTask runTaskAsynchronously(Plugin p, Runnable r) { int id = taskIdCounter.getAndIncrement(); ScheduledTask t = Bukkit.getAsyncScheduler().runNow(p, st -> { try { r.run(); } finally { runningTasks.remove(id); } }); runningTasks.put(id, t); return new FoliaBukkitTask(id, p, FoliaPatcher::cancelTaskById, false, t); }
  public static BukkitTask runTaskLaterAsynchronously(Plugin p, Runnable r, long d) { int id = taskIdCounter.getAndIncrement(); ScheduledTask t = Bukkit.getAsyncScheduler().runDelayed(p, st -> { try { r.run(); } finally { runningTasks.remove(id); } }, d * 50, TimeUnit.MILLISECONDS); runningTasks.put(id, t); return new FoliaBukkitTask(id, p, FoliaPatcher::cancelTaskById, false, t); }
  public static BukkitTask runTaskTimerAsynchronously(Plugin p, Runnable r, long d, long pr) { int id = taskIdCounter.getAndIncrement(); ScheduledTask t = Bukkit.getAsyncScheduler().runAtFixedRate(p, st -> r.run(), d * 50, pr * 50, TimeUnit.MILLISECONDS); runningTasks.put(id, t); return new FoliaBukkitTask(id, p, FoliaPatcher::cancelTaskById, false, t); }

  public static final class FoliaBukkitTask implements BukkitTask {
    private final int id; private final Plugin o; private final IntConsumer cb; private final boolean s; private final ScheduledTask t;
    public FoliaBukkitTask(int id, Plugin o, IntConsumer cb, boolean s, ScheduledTask t) { this.id = id; this.o = o; this.cb = cb; this.s = s; this.t = t; }
    @Override public int getTaskId() { return id; } @Override public Plugin getOwner() { return o; } @Override public boolean isSync() { return s; } @Override public boolean isCancelled() { return t.isCancelled(); } @Override public void cancel() { if (!isCancelled()) cb.accept(this.id); }
  }

  // ALIASES for backward compatibility
  public static void safeSetType(Plugin p, Block b, Material m) { _st(p, b, m); }
  public static void safeSetTypeWithPhysics(Plugin p, Block b, Material m, boolean ph) { _stwp(p, b, m, ph); }
  public static void safeSetBlockData(Plugin p, Block b, BlockData d, boolean ph) { _bdwp(p, b, d, ph); }
  public static <T extends Entity> T safeSpawnEntity(Plugin p, World w, Location l, Class<T> c) { return _ss(p, w, l, c); }
  public static void safeLoadChunk(Plugin p, World w, int x, int z, boolean g) { _cl(p, w, x, z, g); }
  public static void safeRemove(Plugin p, Entity e) { _rem(p, e); }
  public static void safeSetVelocity(Plugin p, Entity e, org.bukkit.util.Vector v) { _sv(p, e, v); }
  public static boolean safeTeleportEntity(Plugin p, Entity e, Location l) { return _te(p, e, l); }
  public static double safeGetHealth(Plugin p, Player pl) { Double r = _be(p, pl, pl::getHealth); return r != null ? r : 0.0; }
  public static boolean safeUpdateBlockState(Plugin p, BlockState s, boolean f, boolean ph) { return _up(p, s, f, ph); }
  public static void safeSendMessage(Plugin p, Player pl, String m) { _sm(p, pl, m); }
  public static void safeSendMessages(Plugin p, Player pl, String[] m) { _sm(p, pl, m); }
  public static void safeKickPlayer(Plugin p, Player pl, String m) { _kp(p, pl, m); }
  public static void safeSetHealth(Plugin p, Player pl, double h) { _sh(p, pl, h); }
  public static void safeSetFoodLevel(Plugin p, Player pl, int f) { _sf(p, pl, f); }
  public static void safeGiveExp(Plugin p, Player pl, int e) { _ge(p, pl, e); }
  public static void safeSetLevel(Plugin p, Player pl, int l) { _sl(p, pl, l); }
  public static void safePlaySound(Plugin p, Player pl, Location l, Sound s, float v, float pi) { _psd(p, pl, l, s, v, pi); }
  public static void safeSendTitle(Plugin p, Player pl, String t, String s, int fi, int st, int fo) { _stt(p, pl, t, s, fi, st, fo); }
  public static void safeCloseInventory(Plugin p, Player pl) { _ci(p, pl); }
  public static InventoryView safeOpenInventory(Plugin p, Player pl, Inventory i) { return _oi(p, pl, i); }
  public static List<Player> safeGetPlayers(Plugin p, World w) { return _gp(p, w); }
  public static void safeBroadcastMessage(Plugin p, String m) { _bm(p, m); }
  public static void safeSetBlockData(Plugin p, Block b, BlockData d) { _bd(p, b, d); }
  public static Block safeGetHighestBlockAt(Plugin p, World w, int x, int z) { return _hb(p, w, x, z); }
  public static void safeSetBlockType(Plugin p, Block b, Material m) { _st(p, b, m); }
  public static void safeSetBlockTypeWithPhysics(Plugin p, Block b, Material m, boolean ph) { _stwp(p, b, m, ph); }
}
