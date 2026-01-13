package com.example;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class TestPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("TestPlugin enabled. Setting up scoreboard test.");

        // Use a delayed task to simulate an async context
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Scoreboard mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

            // These calls should be transformed by ScoreboardTransformer
            Objective testObjective = mainScoreboard.registerNewObjective("test", "dummy");
            testObjective.setDisplayName("Test Objective");
            testObjective.setDisplaySlot(DisplaySlot.SIDEBAR);

            Team testTeam = mainScoreboard.registerNewTeam("testTeam");
            testTeam.setPrefix("Test-");
            testTeam.addEntry("FakePlayer");

            // This call on a Score object should also be transformed
            testObjective.getScore("FakePlayer").setScore(10);

            getLogger().info("Scoreboard operations performed from a delayed task.");

        }, 20L); // 1 second delay
    }

    @Override
    public void onDisable() {
        getLogger().info("TestPlugin disabled.");
        // Clean up scoreboard elements
        Scoreboard mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective obj = mainScoreboard.getObjective("test");
        if (obj != null) {
            obj.unregister();
        }
        Team team = mainScoreboard.getTeam("testTeam");
        if (team != null) {
            team.unregister();
        }
    }
}
