package ua.intertime.interevents;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.projectiles.ProjectileSource;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Допоміжні функції, безпечні для версій 1.16.5 - 26.x. */
public final class Util {
    private static final Random RNG = new Random();
    private static Class<?> particleClass;
    private static Method spawnMethod;
    private static boolean particleInit;
    private static boolean particleBroken;
    private static final Map<String, Object> PARTICLES = new HashMap<String, Object>();
    private static final Set<String> MISSING = new HashSet<String>();

    private Util() {
    }

    public static Random rng() {
        return RNG;
    }

    public static String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    public static Material mat(String name, Material fallback) {
        Material m = Material.matchMaterial(name);
        return m != null ? m : fallback;
    }

    public static int rnd(int min, int max) {
        if (max < min) {
            return min;
        }
        return min + RNG.nextInt(max - min + 1);
    }

    public static String time(int sec) {
        if (sec < 0) {
            sec = 0;
        }
        return String.format("%d:%02d", sec / 60, sec % 60);
    }

    public static void sound(Player p, String key, float vol, float pitch) {
        try {
            p.playSound(p.getLocation(), key, vol, pitch);
        } catch (Throwable ignored) {
        }
    }

    public static void sound(Location l, String key, float vol, float pitch) {
        try {
            l.getWorld().playSound(l, key, vol, pitch);
        } catch (Throwable ignored) {
        }
    }

    public static void title(Player p, String title, String sub, int in, int stay, int out) {
        try {
            p.sendTitle(color(title), color(sub), in, stay, out);
        } catch (Throwable t) {
            p.sendMessage(color(title) + " " + color(sub));
        }
    }

    /**
     * Частинки через рефлексію. Імена можна давати через "|" (нове|старе) - беремо перше, що існує у цій версії.
     * Напр. "SMOKE_NORMAL|SMOKE".
     */
    public static void particle(Location l, String names, int count, double offset) {
        particle(l, names, count, offset, 0.0);
    }

    public static void particle(Location l, String names, int count, double offset, double speed) {
        if (particleBroken || l == null || l.getWorld() == null) {
            return;
        }
        try {
            if (!particleInit) {
                particleInit = true;
                particleClass = Class.forName("org.bukkit.Particle");
                spawnMethod = World.class.getMethod("spawnParticle", particleClass, Location.class,
                        int.class, double.class, double.class, double.class, double.class);
            }
            Object p = PARTICLES.get(names);
            if (p == null) {
                if (MISSING.contains(names)) {
                    return;
                }
                for (String n : names.split("\\|")) {
                    try {
                        p = particleClass.getField(n.trim()).get(null);
                        break;
                    } catch (Throwable ignored) {
                        // пробуємо наступне ім'я
                    }
                }
                if (p == null) {
                    MISSING.add(names);
                    return;
                }
                PARTICLES.put(names, p);
            }
            spawnMethod.invoke(l.getWorld(), p, l, count, offset, offset, offset, speed);
        } catch (Throwable t) {
            particleBroken = true;
        }
    }

    /** Ефект зілля за кількома можливими іменами (імена змінились у 1.20.5). */
    public static org.bukkit.potion.PotionEffectType effect(String... names) {
        for (String n : names) {
            try {
                org.bukkit.potion.PotionEffectType t = org.bukkit.potion.PotionEffectType.getByName(n);
                if (t != null) {
                    return t;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static void addEffect(org.bukkit.entity.LivingEntity e, int ticks, int amp, String... names) {
        org.bukkit.potion.PotionEffectType t = effect(names);
        if (t != null) {
            e.addPotionEffect(new org.bukkit.potion.PotionEffect(t, ticks, amp, true, true, true));
        }
    }

    public static double flat(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static Player shooter(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof Projectile) {
            ProjectileSource s = ((Projectile) damager).getShooter();
            if (s instanceof Player) {
                return (Player) s;
            }
        }
        return null;
    }
}
