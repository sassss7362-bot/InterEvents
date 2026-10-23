package ua.intertime.interevents.events;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import ua.intertime.interevents.Fx;
import ua.intertime.interevents.InterEvents;
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
 * Світовий бос: спільний бій для всього сервера. Здоров'я боса віртуальне (рахується плагіном),
 * тому працює однаково на всіх версіях. Здібності: блискавки, ударна хвиля, прислужники, затягування.
 */
public class WorldBossEvent extends ZoneEvent {
    private LivingEntity boss;
    private UUID bossId;
    private double hp;
    private double maxHp;
    private int phase = 1;
    private int abilityCd = 100;
    private boolean dead;
    private UUID lastHit;
    private final Map<UUID, Double> damage = new HashMap<UUID, Double>();
    private final Set<UUID> minions = new HashSet<UUID>();
    private final List<UUID> ranking = new ArrayList<UUID>();
    private int introLeft;
    private Location spawnLoc;

    public WorldBossEvent(InterEvents plugin) {
        super(plugin, "worldboss");
    }

    @Override
    protected String defaultName() {
        return "&c&lСтраж Безодні";
    }

    @Override
    public String getDescription() {
        return "Світовий бос! Усі разом атакуйте його. Нагороди - за нанесену шкоду та останній удар.";
    }

    @Override
    protected double zoneRadius() {
        return cfgDouble("zone-radius", 40.0);
    }

    @Override
    protected boolean autoRewards() {
        return true;
    }

    @Override
    protected long endDelayTicks() {
        return 60L;
    }

    @Override
    protected void onGameStart() {
        Location t = findSurface(cfgInt("radius", 150));
        if (t == null) {
            t = arena.center.clone();
        }
        spawnLoc = t.clone().add(0.5, 0, 0.5);
        setCenter(spawnLoc);
        maxHp = cfgInt("health", 3000) + Bukkit.getOnlinePlayers().size() * cfgInt("health-per-player", 250);
        hp = maxHp;
        introLeft = cfgInt("intro-seconds", 8) * 10;
        makeGlobalBar(BarColor.RED, BarStyle.SEGMENTED_10);
        announce("&c&l☠ СВІТОВИЙ БОС ☠", "&7Пробуджується &f" + getName(),
                new String[]{"&7Світ: &f" + spawnLoc.getWorld().getName(),
                        "&7Координати: &fX " + spawnLoc.getBlockX() + " Y " + spawnLoc.getBlockY() + " Z " + spawnLoc.getBlockZ(),
                        "&7Здоров'я: &c" + (int) maxHp + " &7| нагороди за шкоду та останній удар!"},
                "entity.wither.spawn");
        track(Fx.helix(plugin, spawnLoc, 6, 2.0, introLeft * 2, "SOUL_FIRE_FLAME"));
    }

    private void spawnBoss() {
        Location loc = spawnLoc.clone();
        World w = loc.getWorld();
        w.strikeLightningEffect(loc);
        Fx.shockwave(plugin, loc, 16, "SOUL_FIRE_FLAME", 20);
        Fx.burst(loc.clone().add(0, 1, 0), "SOUL_FIRE_FLAME", 150, 1.0, 0.25);
        Util.sound(loc, "entity.wither.break_block", 3f, 0.6f);
        Entity en = w.spawnEntity(loc, EntityType.ZOMBIE);
        if (!(en instanceof LivingEntity)) {
            en.remove();
            forceStop(false);
            return;
        }
        boss = (LivingEntity) en;
        bossId = en.getUniqueId();
        boss.setCustomName(Util.color(getName()));
        boss.setCustomNameVisible(true);
        boss.setRemoveWhenFarAway(false);
        boss.setCanPickupItems(false);
        boss.setGlowing(true);
        Util.addEffect(boss, Integer.MAX_VALUE, 0, "FIRE_RESISTANCE");
        Util.addEffect(boss, Integer.MAX_VALUE, 0, "SPEED");
        EntityEquipment eq = boss.getEquipment();
        if (eq != null) {
            eq.setHelmet(new ItemStack(Util.mat("NETHERITE_HELMET", Material.DIAMOND_HELMET)));
            eq.setChestplate(new ItemStack(Util.mat("NETHERITE_CHESTPLATE", Material.DIAMOND_CHESTPLATE)));
            eq.setLeggings(new ItemStack(Util.mat("NETHERITE_LEGGINGS", Material.DIAMOND_LEGGINGS)));
            eq.setBoots(new ItemStack(Util.mat("NETHERITE_BOOTS", Material.DIAMOND_BOOTS)));
            eq.setItemInMainHand(new ItemStack(Util.mat("NETHERITE_SWORD", Material.DIAMOND_SWORD)));
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setLeggingsDropChance(0f);
            eq.setBootsDropChance(0f);
            eq.setItemInMainHandDropChance(0f);
        }
        Bukkit.broadcastMessage(plugin.prefix() + Util.color("&cБос з'явився! &7Усі в бій - X " + loc.getBlockX() + " Z " + loc.getBlockZ()));
    }

    private List<Player> playersNear(Location l, double r) {
        List<Player> out = new ArrayList<Player>();
        for (Player p : l.getWorld().getPlayers()) {
            if (!p.isDead() && p.getLocation().distance(l) <= r) {
                out.add(p);
            }
        }
        return out;
    }

    @Override
    protected void onTick(int tick) {
        zoneGuard(tick);
        if (dead) {
            return;
        }
        if (introLeft > 0) {
            introLeft--;
            if (tick % 2 == 0) {
                Fx.ring(spawnLoc, 1.0 + (100 - introLeft) * 0.05, 36, "SOUL_FIRE_FLAME", 0.2);
            }
            if (tick % 12 == 0) {
                Location l = spawnLoc.clone().add(Util.rnd(-7, 7), 0, Util.rnd(-7, 7));
                l.getWorld().strikeLightningEffect(l);
            }
            if (introLeft == 0) {
                spawnBoss();
            }
            return;
        }
        if (boss != null && boss.isValid() && tick % 5 == 0) {
            center = boss.getLocation();
        }
        if (boss == null || !boss.isValid()) {
            Bukkit.broadcastMessage(plugin.prefix() + Util.color("&eБос зник, і битва завершена без переможця."));
            ranking.clear();
            damage.clear();
            finish();
            return;
        }
        if (seconds() >= cfgInt("timeout-seconds", 900)) {
            Bukkit.broadcastMessage(plugin.prefix() + Util.color("&eБос втік, його ніхто не здолав..."));
            ranking.clear();
            damage.clear();
            finish();
            return;
        }
        double frac = hp / maxHp;
        if (phase == 1 && frac <= 0.66) {
            phase = 2;
            Bukkit.broadcastMessage(plugin.prefix() + Util.color("&c&lБос розлютився! &7Фаза 2 - він кличе прислужників!"));
        } else if (phase == 2 && frac <= 0.33) {
            phase = 3;
            boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1), true);
            Bukkit.broadcastMessage(plugin.prefix() + Util.color("&4&lЛЮТЬ! &7Фаза 3 - бос рухається швидше й затягує гравців!"));
        }
        if (tick % 5 == 0) {
            boss.setCustomName(Util.color(getName() + " &7[&c" + (int) hp + "&7/&c" + (int) maxHp + "&7]"));
        }
        abilityCd -= 2;
        if (abilityCd <= 0) {
            abilityCd = phase >= 3 ? 60 : (phase == 2 ? 100 : 140);
            useAbility();
        }
    }

    private void useAbility() {
        List<Player> targets = playersNear(boss.getLocation(), 40);
        if (targets.isEmpty()) {
            return;
        }
        int pick = Util.rnd(1, phase >= 3 ? 4 : (phase == 2 ? 3 : 2));
        switch (pick) {
            case 1:
                lightning(targets);
                break;
            case 2:
                shockwave();
                break;
            case 3:
                summon();
                break;
            default:
                pull(targets);
                break;
        }
    }

    private void lightning(List<Player> targets) {
        Collections.shuffle(targets);
        int n = Math.min(3, targets.size());
        final double dmg = cfgDouble("lightning-damage", 8.0);
        for (int i = 0; i < n; i++) {
            final Location mark = targets.get(i).getLocation().clone();
            Util.sound(mark, "entity.lightning_bolt.thunder", 0.6f, 1.6f);
            Util.particle(mark.clone().add(0, 0.2, 0), "CLOUD", 30, 1.2);
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    if (dead || boss == null) {
                        return;
                    }
                    mark.getWorld().strikeLightningEffect(mark);
                    for (Player p : playersNear(mark, 3.5)) {
                        p.damage(dmg, boss);
                    }
                }
            }, 25L);
        }
        broadcastNear("&e⚡ Бос кличе блискавки - тікайте з позначених місць!");
    }

    private void shockwave() {
        Location bl = boss.getLocation();
        double dmg = cfgDouble("shockwave-damage", 5.0);
        for (Player p : playersNear(bl, 8.0)) {
            Vector dir = p.getLocation().toVector().subtract(bl.toVector());
            dir.setY(0);
            if (dir.lengthSquared() < 0.0001) {
                dir = new Vector(1, 0, 0);
            }
            dir.normalize().multiply(1.4).setY(0.6);
            p.setVelocity(dir);
            p.damage(dmg, boss);
        }
        Util.sound(bl, "entity.generic.explode", 1f, 0.7f);
        Util.particle(bl.clone().add(0, 0.5, 0), "CLOUD", 60, 2.5);
        broadcastNear("&6Бос б'є ударною хвилею!");
    }

    private void summon() {
        if (minions.size() >= 8) {
            return;
        }
        Location bl = boss.getLocation();
        for (int i = 0; i < 3; i++) {
            Location l = bl.clone().add(Util.rnd(-3, 3), 0, Util.rnd(-3, 3));
            Entity en = bl.getWorld().spawnEntity(l, EntityType.ZOMBIE);
            if (en instanceof LivingEntity) {
                LivingEntity le = (LivingEntity) en;
                le.setCustomName(Util.color("&7Прислужник"));
                le.setRemoveWhenFarAway(false);
                le.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0));
                minions.add(en.getUniqueId());
            } else {
                en.remove();
            }
        }
        Util.sound(bl, "entity.evoker.prepare_summon", 1f, 1f);
        broadcastNear("&5Бос кличе прислужників!");
    }

    private void pull(List<Player> targets) {
        Location bl = boss.getLocation();
        for (Player p : targets) {
            if (p.getLocation().distance(bl) > 30) {
                continue;
            }
            Vector v = bl.toVector().subtract(p.getLocation().toVector());
            if (v.lengthSquared() < 0.0001) {
                continue;
            }
            v.normalize().multiply(1.1).setY(0.35);
            p.setVelocity(v);
        }
        Util.sound(bl, "entity.enderman.teleport", 1f, 0.5f);
        broadcastNear("&dБос затягує вас до себе!");
    }

    private void broadcastNear(String msg) {
        for (Player p : playersNear(boss.getLocation(), 60)) {
            p.sendMessage(plugin.prefix() + Util.color(msg));
        }
    }

    @Override
    public void handleDamage(EntityDamageEvent e) {
        if (boss == null) {
            return;
        }
        UUID id = e.getEntity().getUniqueId();
        if (id.equals(bossId)) {
            if (dead || !(e instanceof EntityDamageByEntityEvent)) {
                e.setCancelled(true);
                return;
            }
            Player d = Util.shooter(((EntityDamageByEntityEvent) e).getDamager());
            if (d == null) {
                e.setCancelled(true);
                return;
            }
            double dmg = e.getFinalDamage();
            e.setDamage(0.0);
            hp -= dmg;
            Double old = damage.get(d.getUniqueId());
            damage.put(d.getUniqueId(), (old == null ? 0.0 : old) + dmg);
            lastHit = d.getUniqueId();
            if (hp <= 0) {
                kill();
            }
            return;
        }
        if (e instanceof EntityDamageByEntityEvent && ((EntityDamageByEntityEvent) e).getDamager().getUniqueId().equals(bossId)) {
            e.setDamage(e.getDamage() * cfgDouble("damage-multiplier", 2.0));
        }
    }

    private void kill() {
        if (dead) {
            return;
        }
        dead = true;
        hp = 0;
        Location bl = boss.getLocation();
        bl.getWorld().strikeLightningEffect(bl);
        Util.sound(bl, "entity.wither.death", 1f, 1f);
        try {
            boss.setHealth(0.0);
        } catch (Throwable ignored) {
        }
        if (boss.isValid()) {
            boss.remove();
        }
        for (UUID u : new ArrayList<UUID>(minions)) {
            Entity en = Bukkit.getEntity(u);
            if (en != null) {
                en.remove();
            }
        }
        minions.clear();

        double minDmg = cfgDouble("min-damage", 50.0);
        List<Map.Entry<UUID, Double>> list = new ArrayList<Map.Entry<UUID, Double>>(damage.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<UUID, Double>>() {
            @Override
            public int compare(Map.Entry<UUID, Double> a, Map.Entry<UUID, Double> b) {
                return Double.compare(b.getValue(), a.getValue());
            }
        });
        ranking.clear();
        Bukkit.broadcastMessage(Util.color("&a&m-----------------------------------------"));
        Bukkit.broadcastMessage(Util.color("  &a&lБОС ПОВАЛЕНИЙ! &7Найбільше шкоди:"));
        int shown = 0;
        for (Map.Entry<UUID, Double> en : list) {
            if (en.getValue() < minDmg) {
                continue;
            }
            ranking.add(en.getKey());
            if (shown < 3) {
                shown++;
                Bukkit.broadcastMessage(Util.color("  &e" + shown + ". &f" + nameOf(en.getKey()) + " &7- &c" + (int) (double) en.getValue()));
            }
        }
        if (lastHit != null) {
            Bukkit.broadcastMessage(Util.color("  &7Останній удар: &6" + nameOf(lastHit)));
            Player lh = Bukkit.getPlayer(lastHit);
            if (lh != null) {
                runCommands(cfgList("rewards.last-hit"), lh, 0);
            }
        }
        Bukkit.broadcastMessage(Util.color("&a&m-----------------------------------------"));
        finish();
    }

    @Override
    protected List<UUID> buildRanking() {
        return new ArrayList<UUID>(ranking);
    }

    @Override
    public void handleEntityDeath(EntityDeathEvent e) {
        UUID id = e.getEntity().getUniqueId();
        if (id.equals(bossId) || minions.remove(id)) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }

    @Override
    protected String barText() {
        return "&c&lСтраж Безодні &7- &f" + (int) Math.max(0, hp) + "/" + (int) maxHp + " &7(Фаза " + phase + ")";
    }

    @Override
    protected double barProgress() {
        return maxHp <= 0 ? 0 : hp / maxHp;
    }

    @Override
    protected void onCleanup() {
        if (boss != null && boss.isValid()) {
            boss.remove();
        }
        for (UUID u : new ArrayList<UUID>(minions)) {
            Entity en = Bukkit.getEntity(u);
            if (en != null) {
                en.remove();
            }
        }
        minions.clear();
        damage.clear();
        ranking.clear();
        super.onCleanup();
    }
}
