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

        if (command.getName().equalsIgnoreCase("testnewfeatures")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                sender.sendMessage("Testing new features from an async thread...");
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    try {
                        // 1. addScoreboardTag
                        player.addScoreboardTag("folia_patch_test");
                        player.sendMessage("Added scoreboard tag");

                        // 2. getNearbyEntities (Entity version)
                        java.util.List<Entity> nearby = player.getNearbyEntities(10, 10, 10);
                        player.sendMessage("Nearby entities count: " + nearby.size());

                        // 3. addPotionEffect
                        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 100, 1));
                        player.sendMessage("Added Speed effect");

                        // 4. Chunk operations
                        org.bukkit.Chunk chunk = player.getLocation().getChunk();
                        chunk.load(false);
                        player.sendMessage("Requested chunk load at " + chunk.getX() + ", " + chunk.getZ());

                        // 5. World rayTrace
                        org.bukkit.util.RayTraceResult res = player.getWorld().rayTraceBlocks(player.getEyeLocation(), player.getLocation().getDirection(), 5, org.bukkit.FluidCollisionMode.NEVER, true);
                        if (res != null) player.sendMessage("Raytrace hit block at " + res.getHitBlock().getType());

                        // 6. Passengers
                        player.eject();
                        player.sendMessage("Ejected passengers");

                        sender.sendMessage("All new features transformation successful!");
                    } catch (Exception e) {
                        getLogger().log(Level.SEVERE, "Error in testnewfeatures command", e);
                    }
                });
            }
            return true;
        }

        return false;
    }
}
