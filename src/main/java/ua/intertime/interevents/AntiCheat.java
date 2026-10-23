package ua.intertime.interevents;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Античіт, що працює лише для гравців, які зараз грають в івенті (живі учасники).
 * Перевірки: швидкість, політ/зависання, reach, автоклікер (CPS), дальність/швидкість ламання блоків,
 * режим гри, телепорти (перли/хорус/команди), заборонені команди. Також захист chat-game від ботів.
 */
public class AntiCheat implements Listener {
    private final InterEvents plugin;
    private final Map<UUID, Data> data = new HashMap<UUID, Data>();
    private BukkitTask loop;
    private int decayTicker;

    private static class Data {
        final Map<String, Double> vl = new HashMap<String, Double>();
        final Map<String, Long> lastAlert = new HashMap<String, Long>();
        long speedStart;
        double speedDist;
        int hover;
        long cpsStart;
        int clicks;
        long breakStart;
        int breaks;
        long exemptUntil;
    }

    public AntiCheat(InterEvents plugin) {
        this.plugin = plugin;
    }

    public void start() {
        loop = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                tickChecks();
            }
        }, 20L, 10L);
    }

    public void stop() {
        if (loop != null) {
            loop.cancel();
            loop = null;
        }
    }

    public void clear() {
        data.clear();
    }

    // ------------------------------------------------------------------ допоміжне

    private boolean on(String check) {
        return plugin.getConfig().getBoolean("anticheat.enabled", true)
                && plugin.getConfig().getBoolean("anticheat.checks." + check + ".enabled", true);
    }

    private double num(String check, String key, double def) {
        return plugin.getConfig().getDouble("anticheat.checks." + check + "." + key, def);
    }

    private boolean monitored(Player p) {
        GameEvent a = plugin.getEventManager().getActive();
        return a != null && a.monitors(p) && !p.hasPermission("interevents.bypass");
    }

    private Data data(Player p) {
        Data d = data.get(p.getUniqueId());
        if (d == null) {
            d = new Data();
            data.put(p.getUniqueId(), d);
        }
        return d;
    }

    /** Тимчасово звільняє гравця від перевірки швидкості (ривки здібностей, відкидання). */
    public void exempt(Player p, long millis) {
        Data d = data(p);
        d.exemptUntil = System.currentTimeMillis() + millis;
        d.speedStart = 0L;
        d.speedDist = 0.0;
        d.hover = 0;
    }

    public String summary(Player p) {
        Data d = data.get(p.getUniqueId());
        if (d == null || d.vl.isEmpty()) {
            return "чисто";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Double> en : d.vl.entrySet()) {
            sb.append(en.getKey()).append("=").append(String.format("%.1f", en.getValue())).append(" ");
        }
        return sb.toString().trim();
    }

    /** Повідомлення персоналу з правом interevents.alerts + запис у консоль. */
    public void alert(String msg) {
        String full = Util.color("&8[&cAC&8] &f" + msg);
        for (Player s : Bukkit.getOnlinePlayers()) {
            if (s.hasPermission("interevents.alerts")) {
                s.sendMessage(full);
            }
        }
        plugin.getLogger().info("[AntiCheat] " + org.bukkit.ChatColor.stripColor(full));
    }

    public void flag(final Player p, final String check, double add, String info) {
        Data d = data(p);
        Double cur = d.vl.get(check);
        double v = (cur == null ? 0.0 : cur) + add;
        d.vl.put(check, v);
        long now = System.currentTimeMillis();
        Long last = d.lastAlert.get(check);
        if (last == null || now - last > 2000L) {
            d.lastAlert.put(check, now);
            alert(p.getName() + " &7" + check + " &c(VL " + String.format("%.1f", v) + ") &8" + info);
        }
        if (v >= num(check, "max-vl", 8.0)) {
            d.vl.put(check, 0.0);
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    punish(p, check);
                }
            });
        }
    }

    private void punish(Player p, String check) {
        GameEvent a = plugin.getEventManager().getActive();
        if (a != null) {
            a.onCheat(p);
        }
        for (String c : plugin.getConfig().getStringList("anticheat.punish-commands")) {
            String cmd = c.replace("%player%", p.getName()).replace("%check%", check);
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            } catch (Throwable ignored) {
            }
        }
    }

    // ------------------------------------------------------------------ періодичні перевірки

    private void tickChecks() {
        GameEvent a = plugin.getEventManager().getActive();
        decayTicker++;
        if (decayTicker >= 10) { // раз на ~5 секунд
            decayTicker = 0;
            for (Data d : data.values()) {
                for (Map.Entry<String, Double> en : d.vl.entrySet()) {
                    en.setValue(Math.max(0.0, en.getValue() - num(en.getKey(), "decay", 1.0)));
                }
            }
        }
        if (a == null) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!a.monitors(p) || p.hasPermission("interevents.bypass")) {
                continue;
            }
            if (on("gamemode") && p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) {
                p.setGameMode(GameMode.SURVIVAL);
                flag(p, "gamemode", 4.0, "режим " + p.getGameMode().name());
            }
            if (on("fly") && p.getGameMode() != GameMode.CREATIVE && (p.isFlying() || p.getAllowFlight())) {
                p.setAllowFlight(false);
                p.setFlying(false);
                flag(p, "fly", 4.0, "увімкнений політ");
            }
        }
    }

    // ------------------------------------------------------------------ рух: швидкість і зависання

    private boolean airBelow(Location l, double depth) {
        double[][] offs = {{0, 0}, {0.3, 0.3}, {-0.3, 0.3}, {0.3, -0.3}, {-0.3, -0.3}};
        for (double[] o : offs) {
            Block b = l.getWorld().getBlockAt(
                    (int) Math.floor(l.getX() + o[0]), (int) Math.floor(l.getY() - depth), (int) Math.floor(l.getZ() + o[1]));
            if (!b.getType().isAir()) {
                return false;
            }
        }
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!monitored(p)) {
            return;
        }
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null || from.getWorld() != to.getWorld()) {
            return;
        }
        if (p.isInsideVehicle() || p.isGliding() || p.isRiptiding()) {
            return;
        }
        Data d = data(p);
        long now = System.currentTimeMillis();
        if (now < d.exemptUntil) {
            return;
        }
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (on("speed")) {
            if (d.speedStart == 0L) {
                d.speedStart = now;
            }
            d.speedDist += dist;
            long el = now - d.speedStart;
            if (el >= 1000L) {
                double bps = d.speedDist / (el / 1000.0);
                double max = num("speed", "max-bps", 13.0);
                PotionEffect pe = p.getPotionEffect(PotionEffectType.SPEED);
                if (pe != null) {
                    max *= 1.0 + 0.3 * (pe.getAmplifier() + 1);
                }
                if (bps > max) {
                    flag(p, "speed", 1.0, String.format("%.1f блоків/с (макс %.1f)", bps, max));
                }
                d.speedStart = now;
                d.speedDist = 0.0;
            }
        }

        boolean floaty = p.hasPotionEffect(PotionEffectType.SLOW_FALLING) || p.hasPotionEffect(PotionEffectType.LEVITATION);
        if (on("fly") && !floaty) {
            double dy = to.getY() - from.getY();
            if (dy >= -0.01 && airBelow(to, 0.1) && airBelow(to, 1.1)) {
                d.hover++;
            } else {
                d.hover = 0;
            }
            if (d.hover >= (int) num("fly", "max-air-moves", 18)) {
                d.hover = 0;
                flag(p, "fly", 2.0, "зависання/політ у повітрі");
            }
        }
    }

    // ------------------------------------------------------------------ reach

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player) || !on("reach")) {
            return;
        }
        Player p = (Player) e.getDamager();
        if (!monitored(p)) {
            return;
        }
        Entity victim = e.getEntity();
        BoundingBox box = victim.getBoundingBox();
        Location eye = p.getEyeLocation();
        double cx = Math.max(box.getMinX(), Math.min(eye.getX(), box.getMaxX()));
        double cy = Math.max(box.getMinY(), Math.min(eye.getY(), box.getMaxY()));
        double cz = Math.max(box.getMinZ(), Math.min(eye.getZ(), box.getMaxZ()));
        double dist = Math.sqrt(Math.pow(eye.getX() - cx, 2) + Math.pow(eye.getY() - cy, 2) + Math.pow(eye.getZ() - cz, 2));
        if (dist > num("reach", "cancel", 6.0)) {
            e.setCancelled(true);
            flag(p, "reach", 2.0, String.format("%.2f блоків", dist));
        } else if (dist > num("reach", "flag", 4.8)) {
            flag(p, "reach", 1.0, String.format("%.2f блоків", dist));
        }
    }

    // ------------------------------------------------------------------ автоклікер

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwing(PlayerAnimationEvent e) {
        Player p = e.getPlayer();
        if (!on("cps") || !monitored(p)) {
            return;
        }
        GameEvent a = plugin.getEventManager().getActive();
        if (a != null && a.usesDigging()) {
            return;
        }
        Data d = data(p);
        long now = System.currentTimeMillis();
        if (now - d.cpsStart >= 1000L) {
            d.cpsStart = now;
            d.clicks = 0;
        }
        d.clicks++;
        if (d.clicks > (int) num("cps", "max-cps", 22)) {
            d.clicks = 0;
            flag(p, "cps", 1.0, "підозрілий автоклікер");
        }
    }

    // ------------------------------------------------------------------ ламання блоків

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!on("nuker") || !monitored(p)) {
            return;
        }
        Data d = data(p);
        Location c = e.getBlock().getLocation().add(0.5, 0.5, 0.5);
        double dist = p.getEyeLocation().distance(c);
        if (dist > num("nuker", "max-distance", 6.5)) {
            e.setCancelled(true);
            flag(p, "nuker", 2.0, String.format("блок за %.1f блоків", dist));
            return;
        }
        long now = System.currentTimeMillis();
        if (now - d.breakStart >= 1000L) {
            d.breakStart = now;
            d.breaks = 0;
        }
        d.breaks++;
        if (d.breaks > (int) num("nuker", "max-per-second", 14)) {
            e.setCancelled(true);
            flag(p, "nuker", 1.0, "забагато блоків за секунду");
        }
    }

    // ------------------------------------------------------------------ телепорти й команди

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        GameEvent a = plugin.getEventManager().getActive();
        if (a == null || !a.monitors(p) || p.hasPermission("interevents.bypass") || !on("teleport")) {
            return;
        }
        // телепорт командою (/tp, /home, /spawn...) у зоні заборонено; перли й хорус - частина PvP
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        GameEvent a = plugin.getEventManager().getActive();
        if (a == null || p.hasPermission("interevents.bypass")) {
            return;
        }
        String cmd = e.getMessage().toLowerCase().split(" ")[0];
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }
        int colon = cmd.indexOf(':');
        if (colon >= 0) {
            cmd = cmd.substring(colon + 1);
        }
        boolean block = false;
        if (a.isParticipant(p)) {
            block = !plugin.getConfig().getStringList("settings.allowed-commands").contains(cmd);
        } else if (a.monitors(p) && on("zone-commands")) {
            block = plugin.getConfig().getStringList("anticheat.zone-blocked-commands").contains(cmd);
        }
        if (block) {
            e.setCancelled(true);
            p.sendMessage(plugin.prefix() + plugin.m("command-blocked", "&cУ зоні івенту ця команда заборонена."));
        }
    }
}
