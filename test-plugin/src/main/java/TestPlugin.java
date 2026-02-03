import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Level;

public class TestPlugin extends JavaPlugin implements Listener {
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        getLogger().info("Player " + event.getPlayer().getName() + " is joining. Simulating a long task...");
        try {
            // This will hang the server if not executed asynchronously
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        getLogger().info("Long task simulation finished for " + event.getPlayer().getName());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("testremove")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                // Get a nearby entity to test with
                for (Entity entity : player.getNearbyEntities(5, 5, 5)) {
                    // This is a thread-unsafe call that should be patched
                    entity.remove();
                    player.sendMessage("Removed entity: " + entity.getType());
                    return true;
                }
                player.sendMessage("No nearby entities found to remove.");
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("testgetonlineplayers")) {
            sender.sendMessage("Testing getOnlinePlayers by calling it from an async thread...");
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    // This call will be transformed to use the safeGetOnlinePlayers helper.
                    int playerCount = Bukkit.getOnlinePlayers().size();
                    sender.sendMessage("Success! Found " + playerCount + " online players via async call.");
                } catch (Exception e) {
                    getLogger().severe("Caught unexpected exception during getOnlinePlayers test: " + e.getClass().getName());
                    e.printStackTrace();
                }
            });
            return true;
        }

        if (command.getName().equalsIgnoreCase("testtimeout")) {
            sender.sendMessage("Testing timeout by calling a blocking API from an async thread...");
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    // This call will be transformed and should time out if the patch is applied correctly
                    int playerCount = Bukkit.getServer().getOnlinePlayers().size();
                    sender.sendMessage("Success! Found " + playerCount + " players.");
                } catch (Exception e) {
                    getLogger().severe("Caught expected exception during timeout test: " + e.getClass().getName());
                    e.printStackTrace();
                }
            });
            return true;
        }

        if (command.getName().equalsIgnoreCase("testplayers")) {
            sender.sendMessage("Testing getOnlinePlayers size optimization and regular usage from an async thread...");
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                // This should be optimized to safeGetOnlinePlayersSize
                int playerCount = Bukkit.getServer().getOnlinePlayers().size();
                sender.sendMessage("Optimized call successful! Player count: " + playerCount);

                // This should be transformed to safeGetOnlinePlayers
                for (Player p : Bukkit.getServer().getOnlinePlayers()) {
                    getLogger().info("Found player: " + p.getName());
                }
                sender.sendMessage("Regular iteration call successful!");
            });
            return true;
        }

        if (command.getName().equalsIgnoreCase("testhighestblock")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                sender.sendMessage("Testing getHighestBlockAt from an async thread...");
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    try {
                        // This call will be transformed to use the safeGetHighestBlockAt helper.
                        org.bukkit.block.Block highestBlock = player.getWorld().getHighestBlockAt(player.getLocation().getBlockX(), player.getLocation().getBlockZ());
                        sender.sendMessage("Success! Highest block at your location is: " + highestBlock.getType());
                    } catch (Exception e) {
                        getLogger().severe("Caught unexpected exception during getHighestBlockAt test: " + e.getClass().getName());
                        e.printStackTrace();
                    }
                });
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("testforeach")) {
            sender.sendMessage("Testing for-each loop with getOnlinePlayers...");
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    int count = 0;
                    for (Player p : Bukkit.getServer().getOnlinePlayers()) {
                        count++;
                    }
                    sender.sendMessage("For-each loop completed. Players found: " + count);
                } catch (Exception e) {
                    getLogger().log(Level.SEVERE, "Error in testforeach command", e);
                }
            });
            return true;
        }

        if (command.getName().equalsIgnoreCase("testversion")) {
            sender.sendMessage("Testing getVersion and getBukkitVersion...");
            // These calls should be transformed to use the cached static fields
            String serverVersion = Bukkit.getVersion();
            String bukkitVersion = Bukkit.getBukkitVersion();
            sender.sendMessage("Server Version: " + serverVersion);
            sender.sendMessage("Bukkit Version: " + bukkitVersion);
            return true;
        }

        if (command.getName().equalsIgnoreCase("testnew")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                sender.sendMessage("Testing new transformations from an async thread...");
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    try {
                        // 1. Damageable#damage
                        player.damage(1.0);
                        player.damage(1.0, (org.bukkit.entity.Entity) player);
                        player.sendMessage("Self-damaged 1.0");

                        // 2. LivingEntity#setAI
                        player.setAI(true);
                        player.sendMessage("Set AI to true");

                        // 3. Player#setGameMode
                        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
                        player.sendMessage("Set GameMode to Survival");

                        // 4. BlockState#update
                        org.bukkit.block.BlockState state = player.getLocation().getBlock().getState();
                        state.update();
                        player.sendMessage("Updated block state at your feet");

                        sender.sendMessage("New transformations test successful!");
                    } catch (Exception e) {
                        getLogger().log(Level.SEVERE, "Error in testnew command", e);
                    }
                });
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("testmore")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                sender.sendMessage("Testing more transformations from an async thread...");
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    try {
                        // 1. Potion Effects
                        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 100, 1));
                        player.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
                        player.sendMessage("Potion effect test successful");

                        // 2. Passengers
                        org.bukkit.entity.Entity creeper = player.getWorld().spawn(player.getLocation(), org.bukkit.entity.Creeper.class);
                        player.addPassenger(creeper);
                        player.removePassenger(creeper);
                        player.eject();
                        creeper.remove();
                        player.sendMessage("Passenger test successful");

                        // 3. Chunk
                        org.bukkit.Chunk chunk = player.getLocation().getChunk();
                        chunk.getEntities();
                        chunk.load(false);
                        chunk.unload();
                        player.sendMessage("Chunk test successful");

                        // 4. Raytrace
                        player.getWorld().rayTraceBlocks(player.getEyeLocation(), player.getEyeLocation().getDirection(), 10.0, org.bukkit.FluidCollisionMode.NEVER, true);
                        player.sendMessage("Raytrace test successful");

                        sender.sendMessage("All more transformations test successful!");
                    } catch (Exception e) {
                        getLogger().log(Level.SEVERE, "Error in testmore command", e);
                    }
                });
            }
            return true;
        }

        return false;
    }
}
