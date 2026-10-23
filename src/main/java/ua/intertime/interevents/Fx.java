package ua.intertime.interevents;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/** Красиві ефекти й анімації на частинках (безпечні для всіх версій). */
public final class Fx {
    private Fx() {
    }

    /** Коло частинок навколо точки. */
    public static void ring(Location c, double r, int points, String particle, double yOff) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            Util.particle(c.clone().add(Math.cos(a) * r, yOff, Math.sin(a) * r), particle, 1, 0.0);
        }
    }

    /** Вертикальний промінь від землі вгору. */
    public static void column(Location base, double height, double step, String particle) {
        for (double y = 0; y <= height; y += step) {
            Util.particle(base.clone().add(0, y, 0), particle, 1, 0.05);
        }
    }

    public static void burst(Location c, String particle, int count, double spread, double speed) {
        Util.particle(c, particle, count, spread, speed);
    }

    /** Хвиля, що розходиться від центру за кілька тіків. */
    public static void shockwave(Plugin pl, final Location c, final double maxR, final String particle, final int ticks) {
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                t++;
                double r = maxR * t / ticks;
                Fx.ring(c, r, Math.max(16, (int) (r * 9)), particle, 0.3);
                if (t >= ticks) {
                    cancel();
                }
            }
        }.runTaskTimer(pl, 0L, 1L);
    }

    /** Спіраль, що піднімається вгору. */
    public static BukkitTask helix(Plugin pl, final Location base, final double height, final double radius,
                                   final int durationTicks, final String particle) {
        return new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                t++;
                for (int strand = 0; strand < 3; strand++) {
                    double y = (t * 0.9 + strand * height / 3.0) % height;
                    double a = t * 0.45 + strand * Math.PI * 2 / 3;
                    Util.particle(base.clone().add(Math.cos(a) * radius, y, Math.sin(a) * radius), particle, 1, 0.0);
                }
                if (t >= durationTicks) {
                    cancel();
                }
            }
        }.runTaskTimer(pl, 0L, 1L);
    }

    /**
     * Плавне падіння/підйом стійки з блоком на голові з точки from в точку to, з розгойдуванням і шлейфом.
     * yOff - зсув стійки відносно шляху (щоб блок на голові стояв на землі).
     */
    public static BukkitTask travel(Plugin pl, final ArmorStand stand, final Location from, final Location to,
                                    final int ticks, final double sway, final double yOff,
                                    final String trailA, final String trailB, final Runnable onArrive) {
        return new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!stand.isValid()) {
                    cancel();
                    return;
                }
                t++;
                double f = Math.min(1.0, (double) t / ticks);
                double x = from.getX() + (to.getX() - from.getX()) * f;
                double y = from.getY() + (to.getY() - from.getY()) * f;
                double z = from.getZ() + (to.getZ() - from.getZ()) * f;
                if (sway > 0) {
                    x += Math.sin(t * 0.15) * sway * (1 - f);
                    z += Math.cos(t * 0.15) * sway * (1 - f);
                }
                Location l = new Location(from.getWorld(), x, y + yOff, z, t * 14f, 0f);
                stand.teleport(l);
                Location at = new Location(from.getWorld(), x, y + 0.6, z);
                if (trailA != null) {
                    Util.particle(at, trailA, 4, 0.25, 0.01);
                }
                if (trailB != null) {
                    Util.particle(at, trailB, 2, 0.15, 0.0);
                }
                if (t >= ticks) {
                    cancel();
                    if (onArrive != null) {
                        onArrive.run();
                    }
                }
            }
        }.runTaskTimer(pl, 1L, 1L);
    }
}
