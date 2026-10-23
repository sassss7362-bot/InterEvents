package ua.intertime.interevents.events;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import ua.intertime.interevents.Fx;
import ua.intertime.interevents.InterEvents;
import ua.intertime.interevents.LootUtil;
import ua.intertime.interevents.Util;
import ua.intertime.interevents.ZoneEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Метеорит: червоне коло й промінь попереджають про падіння -> вогняна куля летить з неба ->
 * вибух, кратер та руда метеорита. Руду копають усі, нагороди за кількість здобутого.
 */
public class MeteorEvent extends ZoneEvent {
    private static final String FLAME = "FLAME";

    private int phase; // 0 попередження, 1 падіння, 2 видобуток
    private int left;
    private Location landing;
    private ArmorStand holo;
    private final Set<Block> ores = new HashSet<Block>();
    private final Map<UUID, Integer> mined = new HashMap<UUID, Integer>();
    private int totalOres;

    public MeteorEvent(InterEvents plugin) {
        super(plugin, "meteor");
    }

    @Override
    protected String defaultName() {
        return "&6&l☄ Метеорит ☄";
    }

    @Override
    public String getDescription() {
        return "З неба падає метеорит! Біжіть до кратера та видобувайте руду метеорита - там лут і унікальні предмети.";
    }

    @Override
    protected double zoneRadius() {
        return cfgDouble("zone-radius", 20.0);
    }

    @Override
    protected long endDelayTicks() {
        return 100L;
    }

    @Override
    protected void onGameStart() {
        Location t = findSurface(cfgInt("radius", 400));
        if (t == null) {
            Bukkit.broadcastMessage(plugin.prefix() + Util.color("&cНе вдалося знайти місце для метеорита."));
            forceStop(false);
            return;
        }
        setCenter(t);
        landing = t.clone().add(0.5, 0, 0.5);
        phase = 0;
        left = cfgInt("warn-seconds", 20) * 10;
        makeGlobalBar(BarColor.RED, BarStyle.SOLID);
        announce("&6&l☄ МЕТЕОРИТ ☄", "&7Падає через &f" + (left / 10) + " &7секунд!",
                new String[]{"&7Світ: &f" + t.getWorld().getName(),
                        "&7Координати: &fX " + t.getBlockX() + " Y " + t.getBlockY() + " Z " + t.getBlockZ(),
                        "&7Після удару в кратері з'явиться &6руда метеорита&7!"},
                "entity.ghast.warn");
    }

    @Override
    protected void onTick(int tick) {
        zoneGuard(tick);
        if (landing == null) {
            return;
        }
        switch (phase) {
            case 0:
                if (tick % 2 == 0) {
                    double r = 5.0 - 3.5 * ((left % 20) / 20.0);
                    Fx.ring(landing, r, 40, FLAME, 0.2);
                    Fx.ring(landing, 5.2, 24, "LAVA", 0.2);
                }
                if (tick % 5 == 0) {
                    Fx.column(landing, 60, 1.2, FLAME);
                }
                if (tick % 20 == 0) {
                    Util.sound(landing, "entity.blaze.shoot", 2f, 0.6f);
                }
                if (--left <= 0) {
                    startFall();
                }
                break;
            case 2:
                left--;
                if (tick % 10 == 0) {
                    for (Block b : ores) {
                        Util.particle(b.getLocation().add(0.5, 1.1, 0.5), "END_ROD", 2, 0.2, 0.02);
                    }
                    updateHolo();
                    if (ores.isEmpty()) {
                        finish();
                        return;
                    }
                }
                if (left <= 0) {
                    Bukkit.broadcastMessage(plugin.prefix() + Util.color("&7Руда метеорита охолола й розсипалась."));
                    finish();
                }
                break;
            default:
                break;
        }
    }

    private void updateHolo() {
        if (holo != null && holo.isValid()) {
            holo.setCustomName(Util.color("&6☄ Руда метеорита ☄ &7(" + ores.size() + "/" + totalOres + ")"));
        }
    }

    private void startFall() {
        phase = 1;
        World w = landing.getWorld();
        double ang = Util.rng().nextDouble() * Math.PI * 2;
        final Location from = landing.clone().add(Math.cos(ang) * 55, 90, Math.sin(ang) * 55);
        final ArmorStand ms = stand(from, Material.MAGMA_BLOCK, null);
        Util.sound(landing, "entity.ghast.shoot", 4f, 0.5f);
        Util.sound(landing, "entity.firework_rocket.blast_far", 4f, 0.5f);
        track(Fx.travel(plugin, ms, from, landing, 60, 0.0, -1.35, FLAME, "SMOKE_LARGE|LARGE_SMOKE", new Runnable() {
            @Override
            public void run() {
                impact(ms);
            }
        }));
    }

    private void impact(ArmorStand ms) {
        removeProp(ms);
        World w = landing.getWorld();
        w.strikeLightningEffect(landing);
        try {
            w.createExplosion(landing, 0f, false);
        } catch (Throwable ignored) {
        }
        Util.sound(landing, "entity.generic.explode", 4f, 0.6f);
        Fx.shockwave(plugin, landing, 14, FLAME, 16);
        Fx.burst(landing.clone().add(0, 1, 0), "LAVA", 60, 1.5, 0.1);
        Fx.burst(landing.clone().add(0, 1, 0), "SMOKE_LARGE|LARGE_SMOKE", 60, 1.5, 0.1);
        double dmg = cfgDouble("impact-damage", 6.0);
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distance(landing) <= 7.0) {
                Vector v = p.getLocation().toVector().subtract(landing.toVector()).setY(0);
                if (v.lengthSquared() > 0.0001) {
                    v.normalize().multiply(1.2);
                }
                p.setVelocity(v.setY(0.7));
                if (dmg > 0 && !p.hasPermission("interevents.bypass")) {
                    p.damage(dmg);
                }
            }
        }
        buildCrater();
        phase = 2;
        left = cfgInt("mine-seconds", 240) * 10;
        holo = stand(landing.clone().add(0, 2.2, 0), null, "&6☄ Руда метеорита ☄");
        updateHolo();
        Bukkit.broadcastMessage(plugin.prefix() + Util.color("&6Метеорит впав! &7Копайте руду - X " + center.getBlockX()
                + " Y " + center.getBlockY() + " Z " + center.getBlockZ()));
    }

    private void buildCrater() {
        Material dark = Util.mat("BLACKSTONE", Material.OBSIDIAN);
        site.fill(-3, 0, -3, 3, 3, 3, Material.AIR);
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (x * x + z * z <= 10) {
                    Material m = Util.rng().nextInt(100) < 25 ? Material.MAGMA_BLOCK : dark;
                    site.set(site.at(x, -1, z), m);
                }
            }
        }
        Material ore = Util.mat("GILDED_BLACKSTONE", Material.GOLD_BLOCK);
        int want = Math.max(3, cfgInt("ore-count", 9));
        for (int i = 0; i < want * 3 && ores.size() < want; i++) {
            int dx = Util.rnd(-2, 2);
            int dz = Util.rnd(-2, 2);
            Block low = site.at(dx, 0, dz);
            if (!ores.contains(low)) {
                site.set(low, ore);
                ores.add(low);
            } else {
                Block high = site.at(dx, 1, dz);
                if (!ores.contains(high)) {
                    site.set(high, ore);
                    ores.add(high);
                }
            }
        }
        totalOres = ores.size();
    }

    @Override
    public void handleBlockBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!ores.contains(b)) {
            return;
        }
        Player p = e.getPlayer();
        if (denied.contains(p.getUniqueId())) {
            e.setCancelled(true);
            p.sendMessage(plugin.prefix() + Util.color("&cВам заборонено брати участь у цьому івенті."));
            return;
        }
        ores.remove(b);
        e.setDropItems(false);
        Integer old = mined.get(p.getUniqueId());
        mined.put(p.getUniqueId(), old == null ? 1 : old + 1);
        List<ItemStack> loot = new ArrayList<ItemStack>(LootUtil.roll(plugin, cfgList("loot")));
        if (Util.rng().nextDouble() < cfgDouble("unique-chance", 0.15)) {
            ItemStack u = LootUtil.uniqueItem(plugin, cfgList("unique-pool"));
            if (u != null) {
                loot.add(u);
                Bukkit.broadcastMessage(plugin.prefix() + Util.color("&f" + p.getName() + " &eзнайшов унікальний предмет у руді метеорита!"));
            }
        }
        Location drop = b.getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack it : loot) {
            b.getWorld().dropItemNaturally(drop, it);
        }
        Fx.burst(drop, "FLAME", 25, 0.4, 0.08);
        Util.sound(drop, "block.amethyst_block.break", 1f, 1.2f);
        updateHolo();
    }

    @Override
    protected List<UUID> buildRanking() {
        List<Map.Entry<UUID, Integer>> list = new ArrayList<Map.Entry<UUID, Integer>>(mined.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<UUID, Integer>>() {
            @Override
            public int compare(Map.Entry<UUID, Integer> a, Map.Entry<UUID, Integer> b) {
                return b.getValue() - a.getValue();
            }
        });
        List<UUID> r = new ArrayList<UUID>();
        for (Map.Entry<UUID, Integer> en : list) {
            r.add(en.getKey());
        }
        return r;
    }

    @Override
    protected void onCleanup() {
        ores.clear();
        mined.clear();
        super.onCleanup();
    }

    @Override
    protected String barText() {
        String xz = "X " + (center == null ? 0 : center.getBlockX()) + " Z " + (center == null ? 0 : center.getBlockZ());
        switch (phase) {
            case 0:
                return "&6☄ Метеорит падає через &f" + (left / 10) + " c &8(" + xz + ")";
            case 1:
                return "&6☄ Метеорит у небі!";
            default:
                return "&6☄ Руди лишилось: &f" + ores.size() + "/" + totalOres + " &7| &f" + Util.time(left / 10) + " &8(" + xz + ")";
        }
    }

    @Override
    protected double barProgress() {
        if (phase == 2 && totalOres > 0) {
            return (double) ores.size() / totalOres;
        }
        return 1.0;
    }
}
