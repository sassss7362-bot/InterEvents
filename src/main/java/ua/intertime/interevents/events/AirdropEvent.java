package ua.intertime.interevents.events;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import ua.intertime.interevents.ChestEvent;
import ua.intertime.interevents.Fx;
import ua.intertime.interevents.InterEvents;
import ua.intertime.interevents.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Небесний дар (Airdrop): сундук спускається на парашуті, його треба захопити, утримуючи точку сам на сам. */
public class AirdropEvent extends ChestEvent {
    private static final String CLOUD = "CLOUD";

    private int phase; // 0 сигнал, 1 спуск, 2 захоплення, 3 відкрито
    private int left;
    private Location landing;
    private ArmorStand holo;
    private double progress;
    private UUID capturer;

    public AirdropEvent(InterEvents plugin) {
        super(plugin, "airdrop");
    }

    @Override
    protected String defaultName() {
        return "&6&l✈ Небесний дар ✈";
    }

    @Override
    public String getDescription() {
        return "Парашутний сундук із небес. Станьте поруч сам на сам і утримуйте точку, щоб захопити його!";
    }

    @Override
    protected long endDelayTicks() {
        return 80L;
    }

    @Override
    protected void onGameStart() {
        Location t = findSurface(cfgInt("radius", 400));
        if (t == null) {
            Bukkit.broadcastMessage(plugin.prefix() + Util.color("&cНе вдалося знайти місце для аірдропу."));
            forceStop(false);
            return;
        }
        setCenter(t);
        landing = t.clone().add(0.5, 0, 0.5);
        phase = 0;
        left = cfgInt("warn-seconds", 10) * 10;
        makeGlobalBar(BarColor.YELLOW, BarStyle.SOLID);
        for (Player p : t.getWorld().getPlayers()) {
            p.setCompassTarget(t);
        }
        announce("&6&l✈ НЕБЕСНИЙ ДАР ✈", "&7Скидання через &f" + (left / 10) + " &7секунд!",
                new String[]{"&7Світ: &f" + t.getWorld().getName(),
                        "&7Координати: &fX " + t.getBlockX() + " Y " + t.getBlockY() + " Z " + t.getBlockZ(),
                        "&7Захопіть сундук: стійте поруч &f" + cfgInt("capture-seconds", 12) + " c &7сам на сам!"},
                "entity.firework_rocket.launch");
    }

    @Override
    protected void onTick(int tick) {
        zoneGuard(tick);
        if (landing == null) {
            return;
        }
        switch (phase) {
            case 0:
                if (tick % 5 == 0) {
                    Fx.column(landing, 45, 1.0, CLOUD);
                    Fx.ring(landing, 2.5, 24, "END_ROD", 0.2);
                }
                if (--left <= 0) {
                    startDrop();
                }
                break;
            case 2:
                captureTick(tick);
                break;
            case 3:
                left--;
                if (tick % 10 == 0) {
                    updateHolo("&a✈ ВІДКРИТО ✈ &7(" + Util.time(left / 10) + ")");
                    Fx.burst(landing.clone().add(0, 1.2, 0), "END_ROD", 8, 0.4, 0.05);
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

    private void startDrop() {
        phase = 1;
        final int ticks = 240;
        final Location from = landing.clone().add(0, 60, 0);
        final ArmorStand chestStand = stand(from, Material.CHEST, null);
        final ArmorStand canopy = stand(from, Util.mat("RED_WOOL", Material.WHITE_WOOL), null);
        Util.sound(landing, "entity.firework_rocket.launch", 3f, 0.8f);
        track(Fx.travel(plugin, canopy, from, landing, ticks, 2.0, 1.55, null, null, new Runnable() {
            @Override
            public void run() {
                removeProp(canopy);
            }
        }));
        track(Fx.travel(plugin, chestStand, from, landing, ticks, 2.0, -1.35, CLOUD, "FIREWORKS_SPARK|FIREWORK", new Runnable() {
            @Override
            public void run() {
                land(chestStand);
            }
        }));
    }

    private void land(ArmorStand cs) {
        removeProp(cs);
        Util.sound(landing, "block.anvil.land", 2f, 1.2f);
        Fx.shockwave(plugin, landing, 8, CLOUD, 12);
        Fx.burst(landing.clone().add(0, 1, 0), "FIREWORKS_SPARK|FIREWORK", 60, 0.6, 0.15);
        placeChest(center);
        holo = stand(landing.clone().add(0, 1.0, 0), null, "&6✈ Небесний дар ✈");
        phase = 2;
        progress = 0;
        capturer = null;
        Bukkit.broadcastMessage(plugin.prefix() + Util.color("&6Небесний дар приземлився! &7Захоплюйте - X "
                + center.getBlockX() + " Y " + center.getBlockY() + " Z " + center.getBlockZ()));
    }

    private boolean valid(Player p) {
        return p.getGameMode() == GameMode.SURVIVAL && !p.isDead() && !p.isFlying() && !p.isInsideVehicle()
                && !denied.contains(p.getUniqueId())
                && p.getWorld().equals(landing.getWorld())
                && Util.flat(p.getLocation(), landing) <= 4.0
                && Math.abs(p.getLocation().getY() - landing.getY()) <= 3.0;
    }

    private void captureTick(int tick) {
        if (seconds() >= cfgInt("timeout-seconds", 900)) {
            Bukkit.broadcastMessage(plugin.prefix() + Util.color("&eНебесний дар зник - ніхто його не захопив."));
            finish();
            return;
        }
        List<Player> near = new ArrayList<Player>();
        for (Player p : landing.getWorld().getPlayers()) {
            if (valid(p)) {
                near.add(p);
            }
        }
        double need = cfgInt("capture-seconds", 12) * 20.0;
        if (tick % 5 == 0) {
            Fx.ring(landing, 4.0, 40, "END_ROD", 0.2);
        }
        if (near.size() == 1) {
            Player c = near.get(0);
            if (!c.getUniqueId().equals(capturer)) {
                capturer = c.getUniqueId();
                progress = 0;
                Bukkit.broadcastMessage(plugin.prefix() + Util.color("&f" + c.getName() + " &eпочав захоплювати небесний дар!"));
            }
            progress += 2;
            if (progress >= need) {
                capture(c);
                return;
            }
            if (tick % 10 == 0) {
                int pct = (int) (progress / need * 100);
                updateHolo("&6✈ Захоплення: &f" + pct + "% &6✈");
                Fx.ring(landing, 4.0 * (1 - progress / need) + 0.5, 30, "FLAME", 0.3);
            }
        } else if (near.size() > 1) {
            if (tick % 10 == 0) {
                updateHolo("&c✈ Точка оскаржується! ✈");
            }
        } else {
            progress = Math.max(0, progress - 1);
            if (progress == 0) {
                capturer = null;
            }
            if (tick % 10 == 0) {
                updateHolo("&6✈ Небесний дар ✈");
            }
        }
    }

    private void capture(Player p) {
        unlocked = true;
        firstOpened = true;
        fixedRanking.clear();
        fixedRanking.add(p.getUniqueId());
        phase = 3;
        left = cfgInt("open-seconds", 45) * 10;
        Fx.burst(landing.clone().add(0, 1.2, 0), "FIREWORKS_SPARK|FIREWORK", 100, 0.8, 0.3);
        Fx.shockwave(plugin, landing, 9, "END_ROD", 14);
        Util.sound(p, "entity.player.levelup", 1f, 1f);
        Bukkit.broadcastMessage(plugin.prefix() + Util.color("&a&l" + p.getName() + " &aзахопив небесний дар! Сундук відкрито для всіх &7("
                + cfgInt("open-seconds", 45) + " c)"));
    }

    @Override
    protected String barText() {
        String xz = "X " + (center == null ? 0 : center.getBlockX()) + " Z " + (center == null ? 0 : center.getBlockZ());
        switch (phase) {
            case 0:
                return "&6✈ Небесний дар через &f" + (left / 10) + " c &8(" + xz + ")";
            case 1:
                return "&6✈ Сундук спускається на парашуті... &8(" + xz + ")";
            case 2:
                if (capturer != null) {
                    Player c = Bukkit.getPlayer(capturer);
                    return "&6✈ Захоплює: &f" + (c == null ? "?" : c.getName()) + " &8(" + xz + ")";
                }
                return "&6✈ Захопіть сундук! &8(" + xz + ")";
            default:
                return "&a✈ Сундук відкрито! &7Зникне через &f" + Util.time(left / 10);
        }
    }

    @Override
    protected double barProgress() {
        if (phase == 2) {
            return Math.min(1.0, progress / Math.max(1.0, cfgInt("capture-seconds", 12) * 20.0));
        }
        if (phase == 3) {
            return (double) left / Math.max(1, cfgInt("open-seconds", 45) * 10);
        }
        return 1.0;
    }
}
