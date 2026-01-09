import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class TestPlugin extends JavaPlugin {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            // Get a nearby entity to test with
            for (Entity entity : player.getNearbyEntities(5, 5, 5)) {
                // This is a thread-unsafe call that should be patched
                entity.remove();
                player.sendMessage("Removed entity: " + entity.getType());
                return true;
            }
        }
        return false;
    }
}
