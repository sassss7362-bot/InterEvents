package ua.intertime.interevents;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class InterEvents extends JavaPlugin {
    private EventManager eventManager;
    private AntiCheat antiCheat;
    private StatsManager stats;
    private SnapshotManager snapshots;
    private ItemManager items;
    private UniqueItems unique;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        stats = new StatsManager(this);
        snapshots = new SnapshotManager(this);
        items = new ItemManager(this);
        antiCheat = new AntiCheat(this);
        eventManager = new EventManager(this);
        unique = new UniqueItems(this);
        cleanupStrayStands();

        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        getServer().getPluginManager().registerEvents(antiCheat, this);
        getServer().getPluginManager().registerEvents(unique, this);
        unique.start();

        EventCommand cmd = new EventCommand(this);
        PluginCommand pc = getCommand("event");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }
        antiCheat.start();
        eventManager.startAuto();
        getLogger().info("InterEvents увімкнено. Івентів: " + eventManager.all().size());
    }

    @Override
    public void onDisable() {
        if (eventManager != null) {
            eventManager.shutdown();
        }
        if (antiCheat != null) {
            antiCheat.stop();
        }
        if (unique != null) {
            unique.stop();
        }
        if (stats != null) {
            stats.save();
        }
    }

    /** Видаляє стійки-анімації, що лишились після краху сервера. */
    private void cleanupStrayStands() {
        for (org.bukkit.World w : getServer().getWorlds()) {
            for (org.bukkit.entity.ArmorStand as : w.getEntitiesByClass(org.bukkit.entity.ArmorStand.class)) {
                if (as.getScoreboardTags().contains("interevents")) {
                    as.remove();
                }
            }
        }
    }

    public void reload() {
        reloadConfig();
        eventManager.startAuto();
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public AntiCheat getAntiCheat() {
        return antiCheat;
    }

    public StatsManager getStats() {
        return stats;
    }

    public SnapshotManager getSnapshots() {
        return snapshots;
    }

    public ItemManager getItems() {
        return items;
    }

    public String prefix() {
        return Util.color(getConfig().getString("settings.prefix", "&8[&6Інтер&eІвенти&8] &7"));
    }

    /** Повідомлення з config.yml (розділ messages) з запасним значенням. */
    public String m(String key, String def) {
        return Util.color(getConfig().getString("messages." + key, def));
    }
}
