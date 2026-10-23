package ua.intertime.interevents.events;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import ua.intertime.interevents.ChestEvent;
import ua.intertime.interevents.Fx;
import ua.intertime.interevents.InterEvents;
import ua.intertime.interevents.Util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Сундук смерті: блискавки й душі -> сундук виринає з-під землі -> прокляття зони та хвилі охоронців ->
 * після останньої хвилі сундук відкривається. Усередині найцінніші сфери й талісмани.
 */
public class DeathChestEvent extends ChestEvent {
    private static final String SOUL = "SOUL";
    private static final String SOUL_FIRE = "SOUL_FIRE_FLAME";
    private static final String SMOKE = "SMOKE_LARGE|LARGE_SMOKE";

    private int phase; // 0 попередження, 1 виринання, 2 бій, 3 відкрито
    private int left;
    private Location landing;
    private ArmorStand holo;
    private int wave;
    private int waves;
    private int nextWaveIn;
    private boolean waveActive;
    private final Set<UUID> guardians = new HashSet<UUID>();

    public DeathChestEvent(InterEvents plugin) {
        super(plugin, "deathchest");
    }

    @Override
    protected String defaultName() {
        return "&4&l☠ Сундук смерті ☠";
    }

    @Override
    public String getDescription() {
        return "Проклятий сундук виринає з-під землі. Знищіть усіх охоронців і витримайте прокляття - нагорода найвища!";
    }

    @Override
    protected long endDelayTicks() {
        return 80L;
    }

    @Override
    protected void onGameStart() {
        Location t = findSurface(cfgInt("radius", 400));
        if (t == null) {
            Bukkit.broadcastMessage(plugin.prefix() + Util.color("&cНе вдалося знайти місце для сундука смерті."));
            forceStop(false);
            return;
        }
        setCenter(t);
        landing = t.clone().add(0.5, 0, 0.5);
        waves = Math.max(1, cfgInt("waves", 3));
        phase = 0;
        left = cfgInt("warn-seconds", 15) * 10;
        makeGlobalBar(BarColor.RED, BarStyle.SEGMENTED_6);
        announce("&4&l☠ СУНДУК СМЕРТІ ☠", "&7Виринає через &f" + (left / 10) + " &7секунд...",
                new String[]{"&7Світ: &f" + t.getWorld().getName(),
                        "&7Координати: &fX " + t.getBlockX() + " Y " + t.getBlockY() + " Z " + t.getBlockZ(),
                        "&cУвага: &7зона проклята, її охороняють мерці!"},
                "entity.wither.spawn");
    }

    @Override
    protected void onTick(int tick) {
        zoneGuard(tick);
        if (landing == null) {
            return;
        }
        if (phase < 3 && seconds() >= cfgInt("timeout-seconds", 900)) {
            Bukkit.broadcastMessage(plugin.prefix() + Util.color("&7Сундук смерті розчинився в темряві - ніхто не здолав охоронців."));
            killGuardians();
            finish();
            return;
        }
        switch (phase) {
            case 0:
                if (tick % 2 == 0) {
                    double r = 1.5 + 4.0 * ((left % 20) / 20.0);
                    Fx.ring(landing, r, 36, SOUL_FIRE, 0.2);
                }
                if (tick % 5 == 0) {
                    Fx.column(landing, 40, 1.0, SOUL);
                }
                if (tick % 15 == 0) {
                    Location l = landing.clone().add(Util.rnd(-6, 6), 0, Util.rnd(-6, 6));
                    l.getWorld().strikeLightningEffect(l);
                }
                if (--left <= 0) {
                    startRise();
                }
                break;
            case 2:
                fightTick(tick);
                break;
            case 3:
                left--;
                if (tick % 10 == 0) {
                    updateHolo("&a☠ ВІДКРИТО ☠ &7(" + Util.time(left / 10) + ")");
                    Fx.burst(landing.clone().add(0, 1.2, 0), SOUL_FIRE, 12, 0.5, 0.05);
                    if (chestEmpty()) {
                        finish();
                        return;
                    }
                }
                if (left <= 0) {
                    finish();
                }
                break;
            default:
                break;
        }
    }

    private void updateHolo(String text) {
        if (holo != null && holo.isValid()) {
            holo.setCustomName(Util.color(text));
        }
    }

    private void startRise() {
        phase = 1;
        final Location from = landing.clone().add(0, -2.4, 0);
        final ArmorStand cs = stand(from, Material.CHEST, null);
        Util.sound(landing, "entity.wither.ambient", 3f, 0.6f);
        track(Fx.helix(plugin, landing, 5, 1.4, 70, SOUL_FIRE));
        for (int i = 0; i < 3; i++) {
            final int delay = i * 22;
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    if (state == State.RUNNING) {
                        Fx.shockwave(plugin, landing, 9, SOUL, 16);
                        Util.sound(landing, "block.stone.break", 3f, 0.5f);
                    }
                }
            }, delay);
        }
        track(Fx.travel(plugin, cs, from, landing, 70, 0.0, -1.35, SOUL, SMOKE, new Runnable() {
            @Override
            public void run() {
                emerged(cs);
            }
        }));
    }

    private void emerged(ArmorStand cs) {
        removeProp(cs);
        landing.getWorld().strikeLightningEffect(landing);
        Util.sound(landing, "entity.wither.break_block", 2f, 0.7f);
        Fx.burst(landing.clone().add(0, 1, 0), SOUL_FIRE, 100, 0.8, 0.2);
        placeChest(center);
        holo = stand(landing.clone().add(0, 1.0, 0), null, "&4☠ Сундук смерті ☠");
        phase = 2;
        wave = 0;
        nextWaveIn = 30;
        waveActive = false;
        Bukkit.broadcastMessage(plugin.prefix() + Util.color("&4Сундук смерті виринув! &7Охоронці прокидаються... X "
                + center.getBlockX() + " Z " + center.getBlockZ()));
    }

    private void fightTick(int tick) {
        // прокляття: повільне виснаження всіх у зоні
        if (tick % 50 == 0) {
            double dmg = cfgDouble("curse-damage", 1.0);
            for (Player p : zonePlayers()) {
                if (p.getHealth() > 4.0 && dmg > 0) {
                    p.damage(dmg);
                }
                Util.particle(p.getLocation().add(0, 1, 0), SOUL, 6, 0.4, 0.02);
            }
        }
        if (tick % 10 == 0) {
            Fx.ring(landing, zoneRadius(), 80, SOUL_FIRE, 0.3);
            updateHolo("&4☠ Сундук смерті ☠ &7(охоронців: " + guardians.size() + ")");
        }
        if (nextWaveIn > 0) {
            nextWaveIn--;
            if (nextWaveIn == 0) {
                spawnWave();
            }
            return;
        }
        if (waveActive) {
            for (UUID u : new ArrayList<UUID>(guardians)) {
                Entity en = Bukkit.getEntity(u);
                if (en == null || !en.isValid() || en.isDead()) {
                    guardians.remove(u);
                }
            }
            if (guardians.isEmpty()) {
                waveActive = false;
                if (wave >= waves) {
                    openChest();
                } else {
                    nextWaveIn = 80;
                    Bukkit.broadcastMessage(plugin.prefix() + Util.color("&7Хвилю " + wave + " знищено. Наступна хвиля охоронців скоро!"));
                }
            }
        }
    }

    private void spawnWave() {
        wave++;
        World w = landing.getWorld();
        int count = cfgInt("guardians-base", 4) + wave * 2 + Math.min(6, zonePlayers().size());
        for (int i = 0; i < count; i++) {
            double ang = Util.rng().nextDouble() * Math.PI * 2;
            double r = 5 + Util.rng().nextDouble() * 4;
            int x = landing.getBlockX() + (int) (Math.cos(ang) * r);
            int z = landing.getBlockZ() + (int) (Math.sin(ang) * r);
            int y = w.getHighestBlockYAt(x, z) + 1;
            Location l = new Location(w, x + 0.5, y, z + 0.5);
            boolean reaper = wave == waves && i == 0;
            EntityType type = (reaper || Util.rng().nextInt(100) < 70) ? EntityType.WITHER_SKELETON : EntityType.SKELETON;
            Entity en = w.spawnEntity(l, type);
            if (!(en instanceof LivingEntity)) {
                en.remove();
                continue;
            }
            LivingEntity le = (LivingEntity) en;
            le.setRemoveWhenFarAway(false);
            le.setGlowing(true);
            le.setCustomName(Util.color(reaper ? "&4&lЖнець смерті" : "&8Охоронець смерті"));
            le.setCustomNameVisible(true);
            Util.addEffect(le, Integer.MAX_VALUE, 0, "FIRE_RESISTANCE");
            Util.addEffect(le, Integer.MAX_VALUE, 0, "SPEED");
            if (reaper) {
                try {
                    le.setMaxHealth(80.0);
                    le.setHealth(80.0);
                } catch (Throwable ignored) {
                }
                EntityEquipment eq = le.getEquipment();
                if (eq != null) {
                    eq.setHelmet(new ItemStack(Material.DIAMOND_HELMET));
                    eq.setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
                    eq.setHelmetDropChance(0f);
                    eq.setChestplateDropChance(0f);
                }
            }
            guardians.add(en.getUniqueId());
            Fx.burst(l.clone().add(0, 1, 0), SOUL_FIRE, 25, 0.4, 0.05);
            if (i % 3 == 0) {
                w.strikeLightningEffect(l);
            }
        }
        waveActive = true;
        Util.sound(landing, "entity.wither.spawn", 2f, 1.2f);
        for (Player p : zonePlayers()) {
            Util.title(p, "&4&lХВИЛЯ " + wave + "/" + waves, "&7Охоронців: " + guardians.size(), 0, 40, 10);
        }
    }

    private void openChest() {
        unlocked = true;
        phase = 3;
        left = cfgInt("open-seconds", 90) * 10;
        Fx.burst(landing.clone().add(0, 1.2, 0), SOUL_FIRE, 150, 0.9, 0.3);
        Fx.shockwave(plugin, landing, 12, SOUL, 16);
        announce("&a&l☠ СУНДУК СМЕРТІ ВІДКРИТО ☠", "&7Охоронців знищено!",
                new String[]{"&7X " + center.getBlockX() + " Y " + center.getBlockY() + " Z " + center.getBlockZ(),
                        "&7Забирайте нагороду - перший відкривач отримає бонус!"},
                "entity.wither.death");
    }

    private void killGuardians() {
        for (UUID u : new ArrayList<UUID>(guardians)) {
            Entity en = Bukkit.getEntity(u);
            if (en != null) {
                en.remove();
            }
        }
        guardians.clear();
    }

    @Override
    public void handleEntityDeath(EntityDeathEvent e) {
        if (guardians.remove(e.getEntity().getUniqueId())) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }

    @Override
    protected void onCleanup() {
        killGuardians();
        super.onCleanup();
    }

    @Override
    protected String barText() {
        switch (phase) {
            case 0:
                return "&4☠ Сундук смерті виринає через &f" + (left / 10) + " c";
            case 1:
                return "&4☠ Сундук виринає з-під землі...";
            case 2:
                return "&4☠ Хвиля &f" + Math.max(1, wave) + "/" + waves + " &7| Охоронців: &f" + guardians.size()
                        + " &8(X " + center.getBlockX() + " Z " + center.getBlockZ() + ")";
            default:
                return "&a☠ Сундук відкрито! &7Зникне через &f" + Util.time(left / 10);
        }
    }

    @Override
    protected double barProgress() {
        if (phase == 2 && waves > 0) {
            return Math.min(1.0, (double) wave / waves);
        }
        if (phase == 3) {
            return (double) left / Math.max(1, cfgInt("open-seconds", 90) * 10);
        }
        return 1.0;
    }
}
