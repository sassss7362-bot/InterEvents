package ua.intertime.interevents;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Світова подія з "зоною": стоїть у певному місці світу, гравці в зоні під античітом
 * (без польоту, без tp-команд). Містить хелпери для анімацій і голограм.
 */
public abstract class ZoneEvent extends GameEvent {
    protected Location center;
    protected Arena site;
    protected final List<Entity> props = new ArrayList<Entity>();
    protected final Set<UUID> denied = new HashSet<UUID>();
    protected final List<BukkitTask> tasks = new ArrayList<BukkitTask>();
    protected final List<UUID> fixedRanking = new ArrayList<UUID>();
    private final Set<UUID> flyRemoved = new HashSet<UUID>();

    protected ZoneEvent(InterEvents plugin, String id) {
        super(plugin, id);
    }

    @Override
    public boolean isGlobal() {
        return true;
    }

    @Override
    public Location getZoneCenter() {
        return center;
    }

    protected double zoneRadius() {
        return cfgDouble("zone-radius", 15.0);
    }

    protected void setCenter(Location l) {
        center = l.clone();
        site = new Arena(center);
    }

    @Override
    public boolean monitors(Player p) {
        if (center == null || center.getWorld() == null || !p.getWorld().equals(center.getWorld())) {
            return false;
        }
        if (p.getGameMode() == GameMode.SPECTATOR || p.hasPermission("interevents.bypass")) {
            return false;
        }
        return Util.flat(p.getLocation(), center) <= zoneRadius() + 3.0;
    }

    @Override
    public void onCheat(Player p) {
        if (denied.add(p.getUniqueId())) {
            p.sendMessage(plugin.prefix() + Util.color("&cВи позбавлені участі в цьому івенті (підозра на чіти)."));
        }
    }

    @Override
    protected List<UUID> buildRanking() {
        return new ArrayList<UUID>(fixedRanking);
    }

    /** Випадкове безпечне місце на поверхні навколо центру арени (або спавну). Повертає блок НАД поверхнею. */
    protected Location findSurface(int radius) {
        World w = arena.world();
        for (int i = 0; i < 40; i++) {
            int x = arena.center.getBlockX() + Util.rnd(-radius, radius);
            int z = arena.center.getBlockZ() + Util.rnd(-radius, radius);
            int y = w.getHighestBlockYAt(x, z);
            if (y < 1) {
                continue;
            }
            Block top = w.getBlockAt(x, y, z);
            String n = top.getType().name();
            if (top.isLiquid() || n.contains("LEAVES") || n.contains("ICE") || n.contains("LAVA")
                    || n.contains("CACTUS") || n.contains("MAGMA") || n.contains("FIRE")) {
                continue;
            }
            return new Location(w, x, y + 1, z);
        }
        return null;
    }

    protected ArmorStand stand(Location l, Material head, String name) {
        Entity en = l.getWorld().spawnEntity(l, EntityType.ARMOR_STAND);
        ArmorStand as = (ArmorStand) en;
        as.setVisible(false);
        as.setGravity(false);
        as.setMarker(true);
        as.setInvulnerable(true);
        as.setSilent(true);
        try {
            as.setBasePlate(false);
        } catch (Throwable ignored) {
        }
        try {
            as.setPersistent(false);
        } catch (Throwable ignored) {
        }
        as.addScoreboardTag("interevents");
        if (head != null) {
            as.setHelmet(new ItemStack(head));
        }
        if (name != null) {
            as.setCustomName(Util.color(name));
            as.setCustomNameVisible(true);
        }
        props.add(as);
        return as;
    }

    protected void removeProp(Entity e) {
        if (e != null) {
            e.remove();
            props.remove(e);
        }
    }

    protected void track(BukkitTask t) {
        tasks.add(t);
    }

    protected List<Player> zonePlayers() {
        List<Player> out = new ArrayList<Player>();
        if (center == null) {
            return out;
        }
        for (Player p : center.getWorld().getPlayers()) {
            if (monitors(p) && Util.flat(p.getLocation(), center) <= zoneRadius()) {
                out.add(p);
            }
        }
        return out;
    }

    /** Велике оголошення всім гравцям: чат + title + звук. */
    protected void announce(String headline, String sub, String[] lines, String sound) {
        Bukkit.broadcastMessage(Util.color("&8&m-----------------------------------------"));
        Bukkit.broadcastMessage(Util.color("  " + headline));
        for (String l : lines) {
            Bukkit.broadcastMessage(Util.color("  " + l));
        }
        Bukkit.broadcastMessage(Util.color("&8&m-----------------------------------------"));
        for (Player p : Bukkit.getOnlinePlayers()) {
            Util.title(p, headline, sub, 10, 70, 20);
            Util.sound(p, sound, 0.7f, 1.0f);
        }
    }

    /** Забороняє політ усередині зони (відновлює при виході з неї). Викликати в onTick. */
    protected void zoneGuard(int tick) {
        if (tick % 10 != 0 || center == null) {
            return;
        }
        boolean disable = plugin.getConfig().getBoolean("zone.disable-fly", true);
        for (Player p : center.getWorld().getPlayers()) {
            UUID u = p.getUniqueId();
            boolean in = monitors(p);
            if (in && disable && p.getGameMode() != GameMode.CREATIVE && (p.isFlying() || p.getAllowFlight())) {
                if (flyRemoved.add(u)) {
                    p.sendMessage(plugin.prefix() + Util.color("&cУ зоні івенту політ вимкнено."));
                }
                p.setFlying(false);
                p.setAllowFlight(false);
            } else if (!in && flyRemoved.remove(u)) {
                p.setAllowFlight(true);
            }
        }
    }

    @Override
    public void handlePlayerQuit(Player p) {
        if (flyRemoved.remove(p.getUniqueId())) {
            p.setAllowFlight(true);
        }
    }

    @Override
    protected void onCleanup() {
        for (BukkitTask t : tasks) {
            t.cancel();
        }
        tasks.clear();
        for (Entity e : new ArrayList<Entity>(props)) {
            e.remove();
        }
        props.clear();
        for (UUID u : new ArrayList<UUID>(flyRemoved)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                p.setAllowFlight(true);
            }
        }
        flyRemoved.clear();
        denied.clear();
        if (site != null) {
            site.restore();
        }
    }
}
