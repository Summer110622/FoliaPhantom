import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class TestPlugin extends JavaPlugin {
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

        return false;
    }
}
