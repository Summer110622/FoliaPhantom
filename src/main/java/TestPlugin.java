
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.Location;

public class TestPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        World world = getServer().getWorlds().get(0);
        if (world != null) {
            world.spawnEntity(new Location(world, 0, 0, 0), EntityType.ZOMBIE);
        }
    }
}
