package ua.intertime.interevents;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Унікальні предмети.
 * Талісмани діють, поки лежать будь-де в інвентарі. Сфери діють у руці (основній або лівій),
 * ПКМ - активна здібність з перезарядкою.
 */
public class UniqueItems implements Listener {
    private final InterEvents plugin;
    private final Map<UUID, Set<String>> carried = new HashMap<UUID, Set<String>>();
    private final Map<String, Long> cooldowns = new HashMap<String, Long>();
    private BukkitTask task;

    public UniqueItems(InterEvents plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                passives();
            }
        }, 20L, 10L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    // ------------------------------------------------------------------ допоміжне

    private double num(String id, String key, double def) {
        return plugin.getConfig().getDouble("unique." + id + "." + key, def);
    }

    private Set<String> scan(Player p) {
        Set<String> s = new HashSet<String>();
        for (ItemStack it : p.getInventory().getContents()) {
            String id = plugin.getItems().idOf(it);
            if (id != null) {
                s.add(id);
            }
        }
        return s;
    }

    private boolean has(Player p, String id) {
        Set<String> s = carried.get(p.getUniqueId());
        return s != null && s.contains(id);
    }

    private boolean held(Player p, String id) {
        return id.equals(plugin.getItems().idOf(p.getInventory().getItemInMainHand()))
                || id.equals(plugin.getItems().idOf(p.getInventory().getItemInOffHand()));
    }

    private void passive(Player p, int amp, String... names) {
        org.bukkit.potion.PotionEffectType t = Util.effect(names);
        if (t != null) {
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(t, 40, amp, true, false, true));
        }
    }

    private void passives() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Set<String> s = scan(p);
            carried.put(p.getUniqueId(), s);
            if (s.contains("talisman_griffin")) {
                passive(p, (int) num("talisman_griffin", "speed-level", 0), "SPEED");
            }
            if (!s.isEmpty()) {
                if (held(p, "sphere_fire")) {
                    passive(p, 0, "FIRE_RESISTANCE");
                }
                if (held(p, "sphere_life")) {
                    passive(p, 0, "REGENERATION");
                }
                if (held(p, "sphere_shadow")) {
                    passive(p, 0, "NIGHT_VISION");
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        carried.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ талісмани

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Player att = Util.shooter(e.getDamager());
        if (att != null && has(att, "talisman_dragon")) {
            e.setDamage(e.getDamage() * (1.0 + num("talisman_dragon", "damage-bonus", 0.12)));
        }
        if (e.getEntity() instanceof Player) {
            Player v = (Player) e.getEntity();
            if (has(v, "talisman_titan")) {
                e.setDamage(e.getDamage() * (1.0 - num("talisman_titan", "damage-reduction", 0.12)));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Player att = Util.shooter(e.getDamager());
        if (att == null || !(e.getEntity() instanceof LivingEntity) || e.getEntity().equals(att)) {
            return;
        }
        LivingEntity victim = (LivingEntity) e.getEntity();
        if (has(att, "talisman_vampire")) {
            double heal = e.getFinalDamage() * num("talisman_vampire", "lifesteal", 0.15);
            try {
                double now = att.getHealth();
                att.setHealth(Math.min(Math.max(now, 20.0), now + heal));
                Util.particle(att.getLocation().add(0, 1.2, 0), "HEART", 2, 0.3);
            } catch (Throwable ignored) {
            }
        }
        double r = Util.rng().nextDouble();
        if (held(att, "sphere_chaos") && r < num("sphere_chaos", "chance", 0.2)) {
            randomDebuff(victim, 60);
        }
        if (held(att, "sphere_ice") && r < num("sphere_ice", "chance", 0.25)) {
            Util.addEffect(victim, 40, 1, "SLOWNESS", "SLOW");
            Util.particle(victim.getLocation().add(0, 1, 0), "SNOWFLAKE|CLOUD", 12, 0.4);
        }
        if (held(att, "sphere_fire") && r < num("sphere_fire", "chance", 0.2)) {
            victim.setFireTicks(60);
            Util.particle(victim.getLocation().add(0, 1, 0), "FLAME", 12, 0.4);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player && e.getCause() == EntityDamageEvent.DamageCause.FALL
                && has((Player) e.getEntity(), "talisman_griffin")) {
            e.setCancelled(true);
        }
    }

    /** Талісман Фенікса: рятує від смертельного удару раз на N секунд. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethal(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) {
            return;
        }
        Player p = (Player) e.getEntity();
        if (!has(p, "talisman_phoenix") || e.getFinalDamage() < p.getHealth()) {
            return;
        }
        if (cooldownLeft(p, "talisman_phoenix") > 0) {
            return;
        }
        e.setCancelled(true);
        try {
            p.setHealth(Math.min(20.0, num("talisman_phoenix", "heal", 12.0)));
        } catch (Throwable ignored) {
        }
        p.setFireTicks(0);
        Util.addEffect(p, 100, 1, "ABSORPTION");
        Util.addEffect(p, 100, 1, "REGENERATION");
        Util.addEffect(p, 100, 0, "FIRE_RESISTANCE");
        setCooldown(p, "talisman_phoenix", (long) num("talisman_phoenix", "cooldown", 180));
        Location l = p.getLocation().add(0, 1, 0);
        Fx.burst(l, "FLAME", 120, 0.8, 0.25);
        Fx.shockwave(plugin, p.getLocation(), 6, "FLAME", 12);
        Util.sound(p.getLocation(), "item.totem.use", 1f, 1f);
        p.sendMessage(plugin.prefix() + Util.color("&6Талісман Фенікса врятував вас від смерті!"));
    }

    // ------------------------------------------------------------------ сфери (ПКМ)

    private long cooldownLeft(Player p, String id) {
        Long until = cooldowns.get(p.getUniqueId() + ":" + id);
        if (until == null) {
            return 0;
        }
        return Math.max(0L, (until - System.currentTimeMillis() + 999L) / 1000L);
    }

    private void setCooldown(Player p, String id, long seconds) {
        cooldowns.put(p.getUniqueId() + ":" + id, System.currentTimeMillis() + seconds * 1000L);
    }

    private List<LivingEntity> enemies(Player p, double r) {
        List<LivingEntity> out = new ArrayList<LivingEntity>();
        for (Entity en : p.getNearbyEntities(r, r, r)) {
            if (!(en instanceof LivingEntity) || en instanceof ArmorStand) {
                continue;
            }
            if (en instanceof Player && ((Player) en).getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            out.add((LivingEntity) en);
        }
        return out;
    }

    private void randomDebuff(LivingEntity v, int ticks) {
        int r = Util.rng().nextInt(4);
        if (r == 0) {
            Util.addEffect(v, ticks, 0, "BLINDNESS");
        } else if (r == 1) {
            Util.addEffect(v, ticks + 40, 0, "NAUSEA", "CONFUSION");
        } else if (r == 2) {
            Util.addEffect(v, ticks + 40, 0, "WEAKNESS");
        } else {
            Util.addEffect(v, ticks, 1, "SLOWNESS", "SLOW");
        }
        Util.particle(v.getLocation().add(0, 1, 0), "SPELL_WITCH|WITCH", 20, 0.4);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        String id = plugin.getItems().idOf(e.getItem());
        if (id == null || !id.startsWith("sphere_")) {
            return;
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!plugin.getConfig().getBoolean("unique.abilities-enabled", true)) {
            return;
        }
        long left = cooldownLeft(p, id);
        if (left > 0) {
            p.sendMessage(plugin.prefix() + Util.color("&cЗдібність на перезарядці: &f" + left + " c"));
            return;
        }
        boolean ok;
        switch (id) {
            case "sphere_ice":
                ok = ice(p);
                break;
            case "sphere_fire":
                ok = fire(p);
                break;
            case "sphere_life":
                ok = life(p);
                break;
            case "sphere_shadow":
                ok = shadow(p);
                break;
            case "sphere_chaos":
                ok = chaos(p);
                break;
            default:
                ok = false;
                break;
        }
        if (ok) {
            setCooldown(p, id, (long) num(id, "cooldown", 30));
        }
    }

    private boolean ice(Player p) {
        double r = num("sphere_ice", "radius", 7);
        int ticks = (int) (num("sphere_ice", "freeze-seconds", 3) * 20);
        List<LivingEntity> list = enemies(p, r);
        Fx.shockwave(plugin, p.getLocation(), r, "SNOWFLAKE|CLOUD", 14);
        Util.sound(p.getLocation(), "block.glass.break", 1.5f, 0.6f);
        for (LivingEntity le : list) {
            Util.addEffect(le, ticks, 5, "SLOWNESS", "SLOW");
            Util.addEffect(le, ticks, 1, "MINING_FATIGUE", "SLOW_DIGGING");
            Fx.burst(le.getLocation().add(0, 1, 0), "SNOWFLAKE|CLOUD", 30, 0.5, 0.02);
        }
        p.sendMessage(plugin.prefix() + Util.color("&bСфера льоду заморозила: &f" + list.size()));
        return true;
    }

    private boolean fire(Player p) {
        double r = num("sphere_fire", "radius", 6);
        double dmg = num("sphere_fire", "damage", 4.0);
        List<LivingEntity> list = enemies(p, r);
        Fx.shockwave(plugin, p.getLocation(), r, "FLAME", 12);
        Fx.burst(p.getLocation().add(0, 1, 0), "FLAME", 80, 1.2, 0.15);
        Util.sound(p.getLocation(), "item.firecharge.use", 1.5f, 0.7f);
        Util.addEffect(p, 100, 0, "FIRE_RESISTANCE");
        for (LivingEntity le : list) {
            le.setFireTicks(100);
            le.damage(dmg, p);
        }
        p.sendMessage(plugin.prefix() + Util.color("&6Сфера вогню спалила: &f" + list.size()));
        return true;
    }

    private boolean life(Player p) {
        try {
            double now = p.getHealth();
            p.setHealth(Math.min(Math.max(now, 20.0), now + num("sphere_life", "heal", 8.0)));
        } catch (Throwable ignored) {
        }
        Util.addEffect(p, 120, 1, "ABSORPTION");
        Fx.burst(p.getLocation().add(0, 1.2, 0), "HEART", 20, 0.6, 0.0);
        Fx.shockwave(plugin, p.getLocation(), 3, "HEART", 10);
        Util.sound(p.getLocation(), "entity.player.levelup", 1f, 1.4f);
        return true;
    }

    private boolean shadow(Player p) {
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 0.0001) {
            return false;
        }
        dir.normalize().multiply(num("sphere_shadow", "power", 1.8)).setY(0.25);
        Fx.burst(p.getLocation().add(0, 1, 0), "SMOKE_NORMAL|SMOKE", 40, 0.4, 0.05);
        plugin.getAntiCheat().exempt(p, 2500L);
        p.setVelocity(dir);
        Util.addEffect(p, 60, 0, "INVISIBILITY");
        Util.sound(p.getLocation(), "entity.enderman.teleport", 1f, 1.2f);
        return true;
    }

    private boolean chaos(Player p) {
        double r = num("sphere_chaos", "radius", 7);
        List<LivingEntity> list = enemies(p, r);
        Fx.shockwave(plugin, p.getLocation(), r, "SPELL_WITCH|WITCH", 14);
        Util.sound(p.getLocation(), "entity.evoker.cast_spell", 1.5f, 0.8f);
        for (LivingEntity le : list) {
            randomDebuff(le, 80);
        }
        p.sendMessage(plugin.prefix() + Util.color("&5Сфера хаосу вразила: &f" + list.size()));
        return true;
    }
}
