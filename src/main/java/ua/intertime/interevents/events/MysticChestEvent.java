package ua.intertime.interevents.events;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import ua.intertime.interevents.ChestEvent;
import ua.intertime.interevents.Fx;
import ua.intertime.interevents.InterEvents;
import ua.intertime.interevents.Util;

/**
 * Містичний сундук: промінь із неба -> сундук повільно спускається по спіралі -> удар блискавки й хвиля ->
 * зона зачекання (PvP за лут) -> сундук відкривається, перший відкривач отримує приз.
 */
public class MysticChestEvent extends ChestEvent {
    private static final String MAGIC = "ENCHANTMENT_TABLE|ENCHANT";
    private static final String WITCH = "SPELL_WITCH|WITCH";
    private static final String TOTEM = "TOTEM|TOTEM_OF_UNDYING";

    private int phase; // 0 попередження, 1 спуск, 2 замок, 3 відкрито
    private int left;  // залишок фази, по 0.1 с
    private Location landing;
    private ArmorStand holo;

    public MysticChestEvent(InterEvents plugin) {
        super(plugin, "mysticchest");
    }

    @Override
    protected String defaultName() {
        return "&d&l✦ Містичний сундук ✦";
    }

    @Override
    public String getDescription() {
        return "З неба падає містичний сундук. Тримайте зону, поки він зачинений - усередині унікальні талісмани та сфери!";
    }

    @Override
    protected long endDelayTicks() {
        return 80L;
    }

    @Override
    protected void onGameStart() {
        Location t = findSurface(cfgInt("radius", 400));
        if (t == null) {
            Bukkit.broadcastMessage(plugin.prefix() + Util.color("&cНе вдалося знайти місце для сундука."));
            forceStop(false);
            return;
        }
        setCenter(t);
        landing = t.clone().add(0.5, 0, 0.5);
        phase = 0;
        left = cfgInt("warn-seconds", 15) * 10;
        makeGlobalBar(BarColor.PURPLE, BarStyle.SOLID);
        announce("&d&l✦ МІСТИЧНИЙ СУНДУК ✦", "&7Падає за &f" + (left / 10) + " &7секунд!",
                new String[]{"&7Світ: &f" + t.getWorld().getName(),
                        "&7Координати: &fX " + t.getBlockX() + " Y " + t.getBlockY() + " Z " + t.getBlockZ(),
                        "&7Усередині: &dунікальні талісмани та сфери &7- готуйтесь до бою!"},
                "block.beacon.activate");
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
                    double r = 1.5 + 3.5 * ((left % 20) / 20.0);
                    Fx.ring(landing, r, 36, WITCH, 0.2);
                }
                if (tick % 5 == 0) {
                    Fx.column(landing, 50, 1.0, "END_ROD");
                }
                if (tick % 20 == 0) {
                    Util.sound(landing, "block.beacon.ambient", 2f, 1.4f);
                }
                if (--left <= 0) {
                    startFall();
                }
                break;
            case 2:
                left--;
                if (tick % 10 == 0) {
                    updateHolo("&d✦ Містичний сундук ✦ &7(" + Util.time(left / 10) + ")");
                    Fx.ring(landing, zoneRadius(), 80, WITCH, 0.3);
                    Fx.burst(landing.clone().add(0, 1.0, 0), MAGIC, 25, 0.6, 0.3);
                }
                if (tick % 120 == 0) {
                    double a = Util.rng().nextDouble() * Math.PI * 2;
                    Location edge = landing.clone().add(Math.cos(a) * zoneRadius(), 0, Math.sin(a) * zoneRadius());
                    edge.getWorld().strikeLightningEffect(edge);
                }
                if (left <= 0) {
                    openChest();
                }
                break;
            case 3:
                left--;
                if (tick % 10 == 0) {
                    updateHolo("&a✦ ВІДКРИТО ✦ &7(" + Util.time(left / 10) + ")");
                    Fx.burst(landing.clone().add(0, 1.2, 0), TOTEM, 10, 0.5, 0.2);
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

    private void startFall() {
        phase = 1;
        final Location from = landing.clone().add(0, 45, 0);
        final ArmorStand fs = stand(from, Material.CHEST, null);
        Util.sound(landing, "entity.ender_dragon.flap", 3f, 0.8f);
        track(Fx.helix(plugin, landing, 45, 1.6, 100, "END_ROD"));
        track(Fx.travel(plugin, fs, from, landing, 100, 1.2, -1.35, MAGIC, WITCH, new Runnable() {
            @Override
            public void run() {
                land(fs);
            }
        }));
    }

    private void land(ArmorStand fs) {
        removeProp(fs);
        World w = landing.getWorld();
        w.strikeLightningEffect(landing);
        Util.sound(landing, "entity.generic.explode", 2f, 0.8f);
        Fx.shockwave(plugin, landing, 14, WITCH, 18);
        Fx.burst(landing.clone().add(0, 1, 0), "END_ROD", 90, 0.7, 0.25);
        for (Player p : zonePlayers()) {
            if (Util.flat(p.getLocation(), landing) < 4.5) {
                Vector v = p.getLocation().toVector().subtract(landing.toVector()).setY(0);
                if (v.lengthSquared() > 0.0001) {
                    v.normalize().multiply(0.9);
                }
                p.setVelocity(v.setY(0.5));
            }
        }
        placeChest(center);
        holo = stand(landing.clone().add(0, 1.0, 0), null, "&d✦ Містичний сундук ✦");
        phase = 2;
        left = cfgInt("lock-seconds", 180) * 10;
        Bukkit.broadcastMessage(plugin.prefix() + Util.color("&dМістичний сундук приземлився! &7Відкриється через &f"
                + Util.time(left / 10) + " &7- X " + center.getBlockX() + " Z " + center.getBlockZ()));
    }

    private void openChest() {
        unlocked = true;
        phase = 3;
        left = cfgInt("open-seconds", 90) * 10;
        Fx.burst(landing.clone().add(0, 1.2, 0), TOTEM, 120, 0.8, 0.5);
        Fx.shockwave(plugin, landing, 10, TOTEM, 14);
        for (Player p : Bukkit.getOnlinePlayers()) {
            Util.sound(p, "ui.toast.challenge_complete", 1f, 1f);
        }
        announce("&a&l✦ СУНДУК ВІДКРИТО ✦", "&7Забирайте лут!",
                new String[]{"&7X " + center.getBlockX() + " Y " + center.getBlockY() + " Z " + center.getBlockZ(),
                        "&7Хто перший відкриє - отримає додатковий приз!"},
                "block.chest.open");
    }

    @Override
    protected String barText() {
        String xz = "X " + (center == null ? 0 : center.getBlockX()) + " Z " + (center == null ? 0 : center.getBlockZ());
        switch (phase) {
            case 0:
                return "&d✦ Містичний сундук &7падає через &f" + (left / 10) + " c &8(" + xz + ")";
            case 1:
                return "&d✦ Сундук спускається з небес... &8(" + xz + ")";
            case 2:
                return "&d✦ Сундук відкриється через &f" + Util.time(left / 10) + " &8(" + xz + ")";
            default:
                return "&a✦ Сундук відкрито! &7Зникне через &f" + Util.time(left / 10) + " &8(" + xz + ")";
        }
    }

    @Override
    protected double barProgress() {
        if (phase == 2) {
            int total = Math.max(1, cfgInt("lock-seconds", 180) * 10);
            return 1.0 - (double) left / total;
        }
        if (phase == 3) {
            return (double) left / Math.max(1, cfgInt("open-seconds", 90) * 10);
        }
        return 1.0;
    }
}
