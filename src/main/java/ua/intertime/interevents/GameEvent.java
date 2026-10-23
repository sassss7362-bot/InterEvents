package ua.intertime.interevents;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Базовий клас будь-якого івенту: лобі -> відлік -> гра -> нагороди -> прибирання.
 * "Глобальні" івенти (isGlobal) не мають лобі - вони стартують одразу для всього сервера.
 */
public abstract class GameEvent {
    public enum State { IDLE, LOBBY, COUNTDOWN, RUNNING, ENDING }

    protected final InterEvents plugin;
    private final String id;
    protected State state = State.IDLE;
    protected final Set<UUID> participants = new LinkedHashSet<UUID>();
    protected final Set<UUID> alive = new LinkedHashSet<UUID>();
    protected final List<UUID> eliminated = new ArrayList<UUID>();
    protected final Map<UUID, UUID> lastDamager = new HashMap<UUID, UUID>();
    protected final Map<UUID, Integer> kills = new HashMap<UUID, Integer>();
    private final Set<UUID> paidTicket = new HashSet<UUID>();
    protected Arena arena;
    protected BossBar bar;
    protected long startMillis;
    protected int tick;
    private int lobbyLeft;
    private int countdownLeft;
    private BukkitTask lobbyTask;
    private BukkitTask loopTask;

    protected GameEvent(InterEvents plugin, String id) {
        this.plugin = plugin;
        this.id = id;
    }

    // ------------------------------------------------------------------ опис / конфіг

    public String getId() {
        return id;
    }

    public String getName() {
        return Util.color(plugin.getConfig().getString("events." + id + ".display-name", defaultName()));
    }

    protected abstract String defaultName();

    public abstract String getDescription();

    public boolean isGlobal() {
        return false;
    }

    /** true, якщо івент базується на копанні (спліф) - античіт тоді не рахує CPS. */
    public boolean usesDigging() {
        return false;
    }

    public State getState() {
        return state;
    }

    public int playerCount() {
        return participants.size();
    }

    protected int cfgInt(String k, int d) {
        return plugin.getConfig().getInt("events." + id + "." + k, d);
    }

    protected double cfgDouble(String k, double d) {
        return plugin.getConfig().getDouble("events." + id + "." + k, d);
    }

    protected boolean cfgBool(String k, boolean d) {
        return plugin.getConfig().getBoolean("events." + id + "." + k, d);
    }

    protected List<String> cfgList(String k) {
        return plugin.getConfig().getStringList("events." + id + "." + k);
    }

    // ------------------------------------------------------------------ хуки для івентів

    protected void buildArena() {
    }

    protected void onGameStart() {
    }

    /** Викликається кожні 2 тіки (0.1 c) поки івент у стані RUNNING. */
    protected void onTick(int tick) {
    }

    protected void onCleanup() {
    }

    protected boolean checkEnd() {
        return alive.size() <= 1;
    }

    protected void onTimeUp() {
        finish();
    }

    protected void onEliminated(Player p) {
    }

    protected void onPlayerLeft(Player p) {
    }

    protected boolean pvpEnabled() {
        return false;
    }

    protected boolean damageAllowed(EntityDamageEvent e) {
        return false;
    }

    protected double spawnSpread() {
        return 3.0;
    }

    protected boolean autoRewards() {
        return true;
    }

    protected long endDelayTicks() {
        return 100L;
    }

    protected String barText() {
        return getName() + " &7| Живі: &f" + alive.size() + " &7| &f" + Util.time(seconds());
    }

    protected double barProgress() {
        return 1.0;
    }

    protected void onLethal(Player p, EntityDamageEvent e) {
        UUID k = lastDamager.get(p.getUniqueId());
        Player killer = k == null ? null : Bukkit.getPlayer(k);
        if (killer != null && !killer.equals(p) && alive.contains(killer.getUniqueId())) {
            Integer old = kills.get(killer.getUniqueId());
            kills.put(killer.getUniqueId(), old == null ? 1 : old + 1);
            eliminate(p, "вбитий гравцем &f" + killer.getName());
        } else {
            eliminate(p, "загинув");
        }
    }

    // ------------------------------------------------------------------ життєвий цикл

    public String open() {
        if (state != State.IDLE) {
            return plugin.m("busy", "&cЦей івент вже запущений.");
        }
        participants.clear();
        alive.clear();
        eliminated.clear();
        lastDamager.clear();
        kills.clear();
        paidTicket.clear();
        tick = 0;
        Location base = plugin.getEventManager().getArena(id);
        if (base == null) {
            if (!isGlobal()) {
                return plugin.m("arena-not-set", "&cАрену для цього івенту не задано. Станьте на місце й напишіть &e/event setarena " + id);
            }
            base = Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        arena = new Arena(base);
        startMillis = System.currentTimeMillis();
        try {
            if (isGlobal()) {
                state = State.RUNNING;
                startLoop();
                onGameStart();
                return null;
            }
            state = State.LOBBY;
            buildArena();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Помилка запуску івенту " + id, t);
            cleanup();
            return "&cВнутрішня помилка запуску (див. консоль).";
        }
        bar = Bukkit.createBossBar(Util.color(getName()), BarColor.YELLOW, BarStyle.SOLID);
        lobbyLeft = cfgInt("lobby-seconds", plugin.getConfig().getInt("settings.lobby-seconds", 60));
        announceOpen();
        lobbyTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    lobbyTick();
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.SEVERE, "Помилка лобі " + id, t);
                    forceStop(true);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
        return null;
    }

    private void announceOpen() {
        String line = "&6&m-----------------------------------------";
        Bukkit.broadcastMessage(Util.color(line));
        Bukkit.broadcastMessage(Util.color("  &e&lІВЕНТ: " + getName()));
        Bukkit.broadcastMessage(Util.color("  &7" + getDescription()));
        Bukkit.broadcastMessage(Util.color("  &fПриєднатись: &a/event join &7(до старту: &f" + lobbyLeft + " c&7)"));
        Bukkit.broadcastMessage(Util.color(line));
    }

    private void lobbyTick() {
        if (state == State.LOBBY) {
            lobbyLeft--;
            int min = cfgInt("min-players", 2);
            int max = cfgInt("max-players", 30);
            setBar("&eОчікування гравців: &f" + participants.size() + "/" + max + " &7| до старту &f" + Math.max(lobbyLeft, 0) + " c",
                    Math.max(0.0, (double) lobbyLeft / Math.max(1, cfgInt("lobby-seconds", plugin.getConfig().getInt("settings.lobby-seconds", 60)))));
            if (lobbyLeft == 30 || lobbyLeft == 10) {
                Bukkit.broadcastMessage(plugin.prefix() + "Івент " + getName() + " &7стартує через &f" + lobbyLeft
                        + " c &7- &a/event join &7(гравців: " + participants.size() + "/" + max + ")");
            }
            if (lobbyLeft <= 0 || participants.size() >= max) {
                if (participants.size() >= min) {
                    state = State.COUNTDOWN;
                    countdownLeft = 10;
                } else {
                    Bukkit.broadcastMessage(plugin.prefix() + "&cІвент " + getName() + " &cскасовано: замало гравців (потрібно " + min + ").");
                    cleanup();
                }
            }
        } else if (state == State.COUNTDOWN) {
            countdownLeft--;
            if (participants.size() < cfgInt("min-players", 2)) {
                state = State.LOBBY;
                lobbyLeft = 20;
                broadcastParticipants("&cЗамало гравців, відлік скасовано.");
                return;
            }
            setBar("&aСтарт через &f" + Math.max(countdownLeft, 0) + " c", Math.max(0.0, countdownLeft / 10.0));
            if (countdownLeft <= 5 && countdownLeft > 0) {
                for (Player p : onlineParticipants()) {
                    Util.title(p, "&e" + countdownLeft, "&7Приготуйтесь!", 0, 20, 0);
                    Util.sound(p, "block.note_block.pling", 1f, 1f);
                }
            }
            if (countdownLeft <= 0) {
                if (lobbyTask != null) {
                    lobbyTask.cancel();
                    lobbyTask = null;
                }
                beginGame();
            }
        }
    }

    protected void beginGame() {
        state = State.RUNNING;
        alive.addAll(participants);
        startMillis = System.currentTimeMillis();
        tick = 0;
        for (Player p : onlineParticipants()) {
            plugin.getStats().addPlayed(p);
            Util.title(p, "&a&lСТАРТ!", getName(), 0, 30, 10);
            Util.sound(p, "entity.ender_dragon.growl", 0.6f, 1.2f);
        }
        startLoop();
        try {
            onGameStart();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Помилка старту " + id, t);
            forceStop(true);
        }
    }

    private void startLoop() {
        loopTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != State.RUNNING) {
                    return;
                }
                tick++;
                try {
                    onTick(tick);
                    if (state != State.RUNNING) {
                        return;
                    }
                    if (tick % 5 == 0) {
                        updateBar();
                        if (!isGlobal()) {
                            if (checkEnd()) {
                                finish();
                                return;
                            }
                            int max = cfgInt("max-seconds", 0);
                            if (max > 0 && seconds() >= max) {
                                onTimeUp();
                            }
                        }
                    }
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.SEVERE, "Помилка в івенті " + id, t);
                    forceStop(true);
                }
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    protected void finish() {
        if (state != State.RUNNING) {
            return;
        }
        state = State.ENDING;
        List<UUID> ranking = buildRanking();
        if (autoRewards()) {
            deliverRewards(ranking);
        }
        for (Map.Entry<UUID, Integer> en : kills.entrySet()) {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p != null) {
                plugin.getStats().addKills(p, en.getValue());
                for (int i = 0; i < en.getValue(); i++) {
                    runCommands(cfgList("rewards.per-kill"), p, 0);
                }
            }
        }
        plugin.getStats().save();
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanup();
            }
        }.runTaskLater(plugin, endDelayTicks());
    }

    protected List<UUID> buildRanking() {
        LinkedHashSet<UUID> r = new LinkedHashSet<UUID>(alive);
        List<UUID> el = new ArrayList<UUID>(eliminated);
        Collections.reverse(el);
        r.addAll(el);
        return new ArrayList<UUID>(r);
    }

    protected void deliverRewards(List<UUID> ranking) {
        if (ranking.isEmpty()) {
            return;
        }
        Bukkit.broadcastMessage(Util.color("&6&m-----------------------------------------"));
        Bukkit.broadcastMessage(Util.color("  &e&lРезультати: " + getName()));
        int place = 0;
        for (UUID u : ranking) {
            place++;
            if (place <= 3) {
                String medal = place == 1 ? "&6&l1." : place == 2 ? "&7&l2." : "&c&l3.";
                Bukkit.broadcastMessage(Util.color("  " + medal + " &f" + nameOf(u)));
            }
            Player p = Bukkit.getPlayer(u);
            if (p == null) {
                continue;
            }
            if (place == 1) {
                plugin.getStats().addWin(p);
            }
            if (place <= 3) {
                runCommands(cfgList("rewards." + place), p, place);
            } else {
                runCommands(cfgList("rewards.participation"), p, place);
            }
        }
        Bukkit.broadcastMessage(Util.color("&6&m-----------------------------------------"));
    }

    protected String nameOf(UUID u) {
        Player p = Bukkit.getPlayer(u);
        if (p != null) {
            return p.getName();
        }
        String n = Bukkit.getOfflinePlayer(u).getName();
        return n == null ? "???" : n;
    }

    protected void runCommands(List<String> cmds, Player p, int place) {
        for (String c : cmds) {
            String cmd = c.replace("%player%", p.getName()).replace("%place%", String.valueOf(place)).replace("%event%", id);
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            } catch (Throwable t) {
                plugin.getLogger().warning("Помилка команди нагороди '" + cmd + "': " + t.getMessage());
            }
        }
    }

    public void forceStop(boolean announce) {
        if (state == State.IDLE) {
            return;
        }
        if (announce) {
            Bukkit.broadcastMessage(plugin.prefix() + "&cІвент " + getName() + " &cзавершено достроково.");
        }
        cleanup();
    }

    protected void cleanup() {
        if (lobbyTask != null) {
            lobbyTask.cancel();
            lobbyTask = null;
        }
        if (loopTask != null) {
            loopTask.cancel();
            loopTask = null;
        }
        try {
            onCleanup();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Помилка прибирання " + id, t);
        }
        if (bar != null) {
            bar.removeAll();
            bar = null;
        }
        for (UUID u : new ArrayList<UUID>(participants)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                plugin.getSnapshots().restore(p);
            }
        }
        if (arena != null) {
            arena.restore();
        }
        participants.clear();
        alive.clear();
        eliminated.clear();
        lastDamager.clear();
        kills.clear();
        paidTicket.clear();
        plugin.getAntiCheat().clear();
        state = State.IDLE;
        plugin.getEventManager().clearActive(this);
    }

    // ------------------------------------------------------------------ учасники

    public String join(Player p) {
        if (isGlobal()) {
            return plugin.m("global-no-join", "&cДо цього івенту не потрібно приєднуватись - він іде для всіх.");
        }
        if (state != State.LOBBY && state != State.COUNTDOWN) {
            return plugin.m("not-open", "&cЗараз немає відкритого лобі.");
        }
        UUID u = p.getUniqueId();
        if (participants.contains(u)) {
            return plugin.m("already-in", "&cВи вже в івенті.");
        }
        if (participants.size() >= cfgInt("max-players", 30)) {
            return plugin.m("full", "&cЛобі заповнене.");
        }
        if (p.isDead()) {
            return plugin.m("dead", "&cВи мертві.");
        }
        if (plugin.getConfig().getBoolean("settings.require-ticket", false) && !p.hasPermission("interevents.bypass")) {
            if (!plugin.getItems().consume(p, "ticket")) {
                return plugin.m("no-ticket", "&cДля участі потрібен квиток (/event give ... ticket).");
            }
            paidTicket.add(u);
        }
        plugin.getSnapshots().save(p);
        plugin.getSnapshots().prepare(p);
        participants.add(u);
        if (bar != null) {
            bar.addPlayer(p);
        }
        p.teleport(spawnPoint());
        broadcastParticipants("&a+ &f" + p.getName() + " &7приєднався (" + participants.size() + "/" + cfgInt("max-players", 30) + ")");
        return null;
    }

    public String leave(Player p) {
        if (!participants.contains(p.getUniqueId())) {
            return plugin.m("not-in", "&cВи не в івенті.");
        }
        removePlayer(p, true);
        return null;
    }

    public void handleQuit(Player p) {
        removePlayer(p, false);
    }

    private void removePlayer(Player p, boolean restore) {
        UUID u = p.getUniqueId();
        if (!participants.contains(u)) {
            return;
        }
        boolean wasAlive = alive.remove(u);
        if (state == State.RUNNING && wasAlive) {
            eliminated.add(u);
            broadcastParticipants("&c" + p.getName() + " &7покинув гру. &8(лишилось: " + alive.size() + ")");
        }
        participants.remove(u);
        if (bar != null) {
            bar.removePlayer(p);
        }
        boolean refund = (state == State.LOBBY || state == State.COUNTDOWN) && paidTicket.remove(u);
        if (restore) {
            plugin.getSnapshots().restore(p);
            if (refund) {
                plugin.getItems().give(p, "ticket", 1);
            }
        }
        onPlayerLeft(p);
    }

    public boolean isParticipant(Player p) {
        return participants.contains(p.getUniqueId());
    }

    public boolean isAlive(Player p) {
        return state == State.RUNNING && alive.contains(p.getUniqueId());
    }

    public void eliminate(Player p, String reason) {
        UUID u = p.getUniqueId();
        if (state != State.RUNNING || !alive.remove(u)) {
            return;
        }
        eliminated.add(u);
        broadcastParticipants("&c" + p.getName() + " &7вибув: " + reason + " &8(лишилось: " + alive.size() + ")");
        Util.sound(p, "entity.villager.no", 1f, 1f);
        scheduleSpectator(p);
        onEliminated(p);
    }

    protected void scheduleSpectator(final Player p) {
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (p.isOnline() && participants.contains(p.getUniqueId()) && state != State.IDLE) {
                    toSpectator(p);
                }
            }
        });
    }

    protected void toSpectator(Player p) {
        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.setVelocity(new Vector(0, 0, 0));
        p.setGameMode(GameMode.SPECTATOR);
        p.teleport(arena.center.clone().add(0.5, 8.0, 0.5));
    }

    /** Повертає ліквідованого гравця в гру (для івентів з відродженням). */
    protected void revive(Player p) {
        UUID u = p.getUniqueId();
        eliminated.remove(u);
        alive.add(u);
        p.setGameMode(GameMode.SURVIVAL);
        try {
            p.setHealth(20.0);
        } catch (Throwable ignored) {
        }
        p.setFoodLevel(20);
        p.setFallDistance(0f);
        p.teleport(spawnPoint());
    }

    protected Location spawnPoint() {
        double s = spawnSpread();
        double ox = s <= 0 ? 0 : (Util.rng().nextDouble() * 2 - 1) * s;
        double oz = s <= 0 ? 0 : (Util.rng().nextDouble() * 2 - 1) * s;
        return arena.center.clone().add(0.5 + ox, 0.0, 0.5 + oz);
    }

    protected List<Player> onlineParticipants() {
        List<Player> l = new ArrayList<Player>();
        for (UUID u : new ArrayList<UUID>(participants)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) {
                l.add(p);
            }
        }
        return l;
    }

    protected List<Player> alivePlayers() {
        List<Player> l = new ArrayList<Player>();
        for (UUID u : new ArrayList<UUID>(alive)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) {
                l.add(p);
            }
        }
        return l;
    }

    protected void broadcastParticipants(String msg) {
        String m = plugin.prefix() + Util.color(msg);
        for (Player p : onlineParticipants()) {
            p.sendMessage(m);
        }
    }

    protected int seconds() {
        return (int) ((System.currentTimeMillis() - startMillis) / 1000L);
    }

    // ------------------------------------------------------------------ boss bar

    protected void setBar(String text, double progress) {
        if (bar == null) {
            return;
        }
        bar.setTitle(Util.color(text));
        bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
    }

    protected void updateBar() {
        if (bar != null && state == State.RUNNING) {
            setBar(barText(), barProgress());
        }
    }

    /** Створює boss bar для глобальних івентів і додає всіх онлайн-гравців. */
    protected void makeGlobalBar(BarColor color, BarStyle style) {
        bar = Bukkit.createBossBar(Util.color(getName()), color, style);
        for (Player p : Bukkit.getOnlinePlayers()) {
            bar.addPlayer(p);
        }
    }

    public void handleJoin(Player p) {
        if (isGlobal() && bar != null) {
            bar.addPlayer(p);
        }
    }

    // ------------------------------------------------------------------ обробка подій

    /** Чи перебуває гравець у зоні/грі, де діє античіт. */
    public boolean monitors(Player p) {
        return isAlive(p);
    }

    /** Центр зони події (для /event tp), або null. */
    public Location getZoneCenter() {
        return null;
    }

    /** Античіт досяг порогу - покарання в межах івенту. */
    public void onCheat(Player p) {
        if (isParticipant(p)) {
            eliminate(p, "&cпідозра на чіти");
        }
    }

    public void handlePlayerQuit(Player p) {
    }

    public void handleExplode(EntityExplodeEvent e) {
    }

    public void handleBlockExplode(BlockExplodeEvent e) {
    }

    public void handleMove(PlayerMoveEvent e) {
    }

    public void handleDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) {
            return;
        }
        Player p = (Player) e.getEntity();
        if (!participants.contains(p.getUniqueId())) {
            return;
        }
        if (state != State.RUNNING || !alive.contains(p.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        if (e instanceof EntityDamageByEntityEvent) {
            Player damager = Util.shooter(((EntityDamageByEntityEvent) e).getDamager());
            if (damager != null) {
                if (!alive.contains(damager.getUniqueId()) || !pvpEnabled()) {
                    e.setCancelled(true);
                    return;
                }
                lastDamager.put(p.getUniqueId(), damager.getUniqueId());
            }
        }
        if (!damageAllowed(e)) {
            e.setCancelled(true);
            return;
        }
        if (e.getFinalDamage() >= p.getHealth()) {
            e.setCancelled(true);
            onLethal(p, e);
        }
    }

    public void handleBlockBreak(BlockBreakEvent e) {
        if (participants.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    public void handleBlockPlace(BlockPlaceEvent e) {
        if (participants.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    public void handleBlockDamage(BlockDamageEvent e) {
    }

    public void handleInteract(PlayerInteractEvent e) {
        if (participants.contains(e.getPlayer().getUniqueId()) && e.getClickedBlock() != null) {
            e.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    /** Викликається з асинхронного потоку чату! Повертає true, якщо повідомлення треба сховати. */
    public boolean handleChat(Player p, String msg) {
        return false;
    }

    public void handleEntityDeath(EntityDeathEvent e) {
    }

    public void handleDeath(Player p) {
        if (state == State.RUNNING && alive.remove(p.getUniqueId())) {
            eliminated.add(p.getUniqueId());
        }
    }

    // ------------------------------------------------------------------ набори

    protected void giveCombatKit(Player p, int arrows) {
        PlayerInventory inv = p.getInventory();
        inv.addItem(new ItemStack(Material.IRON_SWORD), new ItemStack(Material.BOW),
                new ItemStack(Material.ARROW, arrows), new ItemStack(Material.COOKED_BEEF, 16),
                new ItemStack(Material.GOLDEN_APPLE, 2));
        inv.setHelmet(new ItemStack(Material.IRON_HELMET));
        inv.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        inv.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        inv.setBoots(new ItemStack(Material.IRON_BOOTS));
    }
}
