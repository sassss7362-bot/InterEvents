package ua.intertime.interevents;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Зберігає стан гравця (інвентар, позиція, режим гри...) на диск перед івентом.
 * Якщо сервер впаде під час івенту - стан відновиться при наступному вході.
 */
public class SnapshotManager {
    private final InterEvents plugin;
    private final File dir;

    public SnapshotManager(InterEvents plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "snapshots");
        this.dir.mkdirs();
    }

    private File file(UUID id) {
        return new File(dir, id + ".yml");
    }

    public boolean has(UUID id) {
        return file(id).exists();
    }

    public void save(Player p) {
        if (has(p.getUniqueId())) {
            return; // зберігаємо найпершу (справжню) копію
        }
        YamlConfiguration c = new YamlConfiguration();
        c.set("contents", Arrays.asList(p.getInventory().getContents()));
        c.set("location", p.getLocation());
        c.set("gamemode", p.getGameMode().name());
        c.set("health", p.getHealth());
        c.set("food", p.getFoodLevel());
        c.set("saturation", (double) p.getSaturation());
        c.set("level", p.getLevel());
        c.set("exp", (double) p.getExp());
        c.set("allow-flight", p.getAllowFlight());
        c.set("flying", p.isFlying());
        c.set("effects", new ArrayList<PotionEffect>(p.getActivePotionEffects()));
        try {
            c.save(file(p.getUniqueId()));
        } catch (IOException ex) {
            plugin.getLogger().warning("Не вдалося зберегти стан " + p.getName() + ": " + ex.getMessage());
        }
    }

    /** Очищає гравця для участі в івенті. */
    public void prepare(Player p) {
        p.getInventory().clear();
        for (PotionEffect e : new ArrayList<PotionEffect>(p.getActivePotionEffects())) {
            p.removePotionEffect(e.getType());
        }
        p.setGameMode(GameMode.SURVIVAL);
        p.setAllowFlight(false);
        p.setFlying(false);
        try {
            p.setHealth(20.0);
        } catch (Throwable ignored) {
        }
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.setLevel(0);
        p.setExp(0f);
    }

    public boolean restore(Player p) {
        File f = file(p.getUniqueId());
        if (!f.exists()) {
            return false;
        }
        YamlConfiguration c = YamlConfiguration.loadConfiguration(f);
        for (PotionEffect e : new ArrayList<PotionEffect>(p.getActivePotionEffects())) {
            p.removePotionEffect(e.getType());
        }
        List<?> list = c.getList("contents");
        ItemStack[] arr = new ItemStack[p.getInventory().getContents().length];
        if (list != null) {
            for (int i = 0; i < list.size() && i < arr.length; i++) {
                Object o = list.get(i);
                if (o instanceof ItemStack) {
                    arr[i] = (ItemStack) o;
                }
            }
        }
        p.getInventory().setContents(arr);
        List<?> eff = c.getList("effects");
        if (eff != null) {
            for (Object o : eff) {
                if (o instanceof PotionEffect) {
                    p.addPotionEffect((PotionEffect) o);
                }
            }
        }
        try {
            p.setGameMode(GameMode.valueOf(c.getString("gamemode", "SURVIVAL")));
        } catch (Throwable ex) {
            p.setGameMode(GameMode.SURVIVAL);
        }
        p.setAllowFlight(c.getBoolean("allow-flight", false));
        if (c.getBoolean("flying", false)) {
            p.setFlying(true);
        }
        try {
            p.setHealth(Math.max(1.0, Math.min(20.0, c.getDouble("health", 20.0))));
        } catch (Throwable ignored) {
        }
        p.setFoodLevel(c.getInt("food", 20));
        p.setSaturation((float) c.getDouble("saturation", 5.0));
        p.setLevel(c.getInt("level", 0));
        p.setExp((float) c.getDouble("exp", 0.0));
        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        Object loc = c.get("location");
        if (loc instanceof Location && ((Location) loc).getWorld() != null) {
            p.teleport((Location) loc);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
        return true;
    }
}
