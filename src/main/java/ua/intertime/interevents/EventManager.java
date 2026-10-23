package ua.intertime.interevents;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import ua.intertime.interevents.events.AirdropEvent;
import ua.intertime.interevents.events.ChatGameEvent;
import ua.intertime.interevents.events.DeathChestEvent;
import ua.intertime.interevents.events.MeteorEvent;
import ua.intertime.interevents.events.MysticChestEvent;
import ua.intertime.interevents.events.WorldBossEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EventManager {
    private final InterEvents plugin;
    private final Map<String, GameEvent> events = new LinkedHashMap<String, GameEvent>();
    private GameEvent active;
    private BukkitTask autoTask;
    private boolean autoEnabled;
    private final File arenaFile;
    private final YamlConfiguration arenaCfg;

    public EventManager(InterEvents plugin) {
        this.plugin = plugin;
        this.arenaFile = new File(plugin.getDataFolder(), "arenas.yml");
        this.arenaCfg = YamlConfiguration.loadConfiguration(arenaFile);
        register(new MysticChestEvent(plugin));
        register(new DeathChestEvent(plugin));
        register(new MeteorEvent(plugin));
        register(new AirdropEvent(plugin));
        register(new WorldBossEvent(plugin));
        register(new ChatGameEvent(plugin));
    }

    private void register(GameEvent e) {
        events.put(e.getId(), e);
    }

    public Collection<GameEvent> all() {
        return events.values();
    }

    public GameEvent get(String id) {
        return id == null ? null : events.get(id.toLowerCase());
    }

    public GameEvent getActive() {
        return active;
    }

    public void clearActive(GameEvent e) {
        if (active == e) {
            active = null;
        }
    }

    public boolean isEnabled(String id) {
        return plugin.getConfig().getBoolean("events." + id + ".enabled", true);
    }

    /** Повертає null при успіху або текст помилки. */
    public String open(String id) {
        GameEvent e = get(id);
        if (e == null) {
            return "&cНевідомий івент. Список: &e/event list";
        }
        if (active != null) {
            return "&cВже йде івент: " + active.getName();
        }
        if (!isEnabled(e.getId())) {
            return "&cЦей івент вимкнено в config.yml";
        }
        active = e;
        String err = e.open();
        if (err != null) {
            if (active == e) {
                active = null;
            }
            return err;
        }
        return null;
    }

    public String join(Player p) {
        if (active == null) {
            return plugin.m("not-open", "&cЗараз немає відкритого лобі.");
        }
        return active.join(p);
    }

    public String leave(Player p) {
        if (active == null || !active.isParticipant(p)) {
            return plugin.m("not-in", "&cВи не в івенті.");
        }
        return active.leave(p);
    }

    public boolean stopActive() {
        if (active == null) {
            return false;
        }
        active.forceStop(true);
        return true;
    }

    public void shutdown() {
        if (autoTask != null) {
            autoTask.cancel();
            autoTask = null;
        }
        if (active != null) {
            active.forceStop(false);
        }
    }

    // ------------------------------------------------------------------ арени

    public Location getArena(String id) {
        String w = arenaCfg.getString(id + ".world");
        if (w == null) {
            return null;
        }
        World world = Bukkit.getWorld(w);
        if (world == null) {
            return null;
        }
        return new Location(world, arenaCfg.getInt(id + ".x"), arenaCfg.getInt(id + ".y"), arenaCfg.getInt(id + ".z"));
    }

    public void setArena(String id, Location l) {
        arenaCfg.set(id + ".world", l.getWorld().getName());
        arenaCfg.set(id + ".x", l.getBlockX());
        arenaCfg.set(id + ".y", l.getBlockY());
        arenaCfg.set(id + ".z", l.getBlockZ());
        try {
            arenaCfg.save(arenaFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Не вдалося зберегти arenas.yml: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ автозапуск

    public boolean isAutoEnabled() {
        return autoEnabled;
    }

    public void setAutoEnabled(boolean on) {
        autoEnabled = on;
        startAuto();
    }

    public void startAuto() {
        if (autoTask != null) {
            autoTask.cancel();
            autoTask = null;
        }
        if (!autoEnabled && !plugin.getConfig().getBoolean("auto.enabled", false)) {
            return;
        }
        autoEnabled = true;
        long period = Math.max(1, plugin.getConfig().getInt("auto.interval-minutes", 30)) * 1200L;
        autoTask = new BukkitRunnable() {
            @Override
            public void run() {
                tryAuto();
            }
        }.runTaskTimer(plugin, period, period);
    }

    public void stopAuto() {
        autoEnabled = false;
        if (autoTask != null) {
            autoTask.cancel();
            autoTask = null;
        }
    }

    private void tryAuto() {
        if (active != null) {
            return;
        }
        if (Bukkit.getOnlinePlayers().size() < plugin.getConfig().getInt("auto.min-online", 3)) {
            return;
        }
        List<String> pool = new ArrayList<String>();
        List<String> allowed = plugin.getConfig().getStringList("auto.events");
        for (GameEvent e : events.values()) {
            if (!isEnabled(e.getId())) {
                continue;
            }
            if (!allowed.isEmpty() && !allowed.contains(e.getId())) {
                continue;
            }
            if (!e.isGlobal() && getArena(e.getId()) == null) {
                continue;
            }
            pool.add(e.getId());
        }
        if (pool.isEmpty()) {
            return;
        }
        open(pool.get(Util.rng().nextInt(pool.size())));
    }
}
