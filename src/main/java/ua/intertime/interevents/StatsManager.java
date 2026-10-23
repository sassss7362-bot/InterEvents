package ua.intertime.interevents;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StatsManager {
    private final InterEvents plugin;
    private final File file;
    private final YamlConfiguration cfg;

    public StatsManager(InterEvents plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "stats.yml");
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    private void add(Player p, String field, int amount) {
        String base = "players." + p.getUniqueId();
        cfg.set(base + ".name", p.getName());
        cfg.set(base + "." + field, cfg.getInt(base + "." + field, 0) + amount);
    }

    public void addWin(Player p) {
        add(p, "wins", 1);
    }

    public void addPlayed(Player p) {
        add(p, "played", 1);
    }

    public void addKills(Player p, int n) {
        if (n > 0) {
            add(p, "kills", n);
        }
    }

    public int get(UUID id, String field) {
        return cfg.getInt("players." + id + "." + field, 0);
    }

    public List<Map.Entry<String, Integer>> top(String field, int n) {
        List<Map.Entry<String, Integer>> list = new ArrayList<Map.Entry<String, Integer>>();
        ConfigurationSection sec = cfg.getConfigurationSection("players");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                int v = sec.getInt(k + "." + field, 0);
                if (v > 0) {
                    list.add(new AbstractMap.SimpleEntry<String, Integer>(sec.getString(k + ".name", "?"), v));
                }
            }
        }
        Collections.sort(list, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return b.getValue() - a.getValue();
            }
        });
        return list.size() > n ? new ArrayList<Map.Entry<String, Integer>>(list.subList(0, n)) : list;
    }

    public void save() {
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Не вдалося зберегти stats.yml: " + ex.getMessage());
        }
    }
}
