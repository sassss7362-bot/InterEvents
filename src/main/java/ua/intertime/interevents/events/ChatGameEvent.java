package ua.intertime.interevents.events;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ua.intertime.interevents.GameEvent;
import ua.intertime.interevents.InterEvents;
import ua.intertime.interevents.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Чат-ігри: приклади, перемішані слова й швидкий набір. Перший правильний - отримує приз. */
public class ChatGameEvent extends GameEvent {
    private final AtomicBoolean awaiting = new AtomicBoolean(false);
    private volatile String answer = "";
    private volatile boolean caseSensitive;
    private volatile long askedAt;
    private int round;
    private int roundTicksLeft;
    private int nextRoundIn;
    private final Map<UUID, Integer> won = new LinkedHashMap<UUID, Integer>();

    public ChatGameEvent(InterEvents plugin) {
        super(plugin, "chatgame");
    }

    @Override
    protected String defaultName() {
        return "&b&lЧат-гра";
    }

    @Override
    public String getDescription() {
        return "Перший, хто напише правильну відповідь у чат - отримає приз!";
    }

    @Override
    public boolean isGlobal() {
        return true;
    }

    @Override
    protected boolean autoRewards() {
        return false; // призи видаються після кожного раунду
    }

    @Override
    protected long endDelayTicks() {
        return 20L;
    }

    @Override
    protected void onGameStart() {
        round = 0;
        won.clear();
        Bukkit.broadcastMessage(plugin.prefix() + Util.color("&bЧат-гра стартує! Раундів: &f" + cfgInt("rounds", 3) + " &7- будьте готові писати в чат."));
        nextRoundIn = 50;
    }

    @Override
    protected void onTick(int tick) {
        if (nextRoundIn > 0) {
            nextRoundIn--;
            if (nextRoundIn == 0) {
                startRound();
            }
            return;
        }
        if (awaiting.get()) {
            roundTicksLeft--;
            if (roundTicksLeft <= 0 && awaiting.compareAndSet(true, false)) {
                Bukkit.broadcastMessage(plugin.prefix() + Util.color("&cЧас вийшов! Відповідь: &f" + answer));
                afterRound();
            }
        }
    }

    private void afterRound() {
        if (round >= cfgInt("rounds", 3)) {
            finish();
        } else {
            nextRoundIn = 40;
        }
    }

    private void startRound() {
        round++;
        int type = Util.rng().nextInt(3);
        String question;
        if (type == 0) {
            int a = Util.rnd(3, 40);
            int b = Util.rnd(2, 15);
            int c = Util.rnd(1, 50);
            int op = Util.rng().nextInt(3);
            if (op == 0) {
                answer = String.valueOf(a + b * c);
                question = a + " + " + b + " × " + c;
            } else if (op == 1) {
                answer = String.valueOf(a * b - c);
                question = a + " × " + b + " - " + c;
            } else {
                answer = String.valueOf(a * b + c);
                question = a + " × " + b + " + " + c;
            }
            caseSensitive = false;
            question = "Розв'яжіть: &f" + question;
        } else if (type == 1) {
            List<String> words = plugin.getConfig().getStringList("events.chatgame.words");
            if (words.isEmpty()) {
                words = Arrays.asList("minecraft", "diamond", "creeper", "enderman", "redstone", "netherite", "pickaxe", "villager");
            }
            String word = words.get(Util.rng().nextInt(words.size()));
            answer = word;
            caseSensitive = false;
            question = "Відновіть слово: &f" + scramble(word);
        } else {
            String chars = "abcdefghjkmnpqrstuvxyzABCDEFGHJKMNPQRSTUVXYZ23456789";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 7; i++) {
                sb.append(chars.charAt(Util.rng().nextInt(chars.length())));
            }
            answer = sb.toString();
            caseSensitive = true;
            question = "Перший, хто перепише код: &f" + answer;
        }
        roundTicksLeft = cfgInt("answer-seconds", 30) * 10;
        askedAt = System.currentTimeMillis();
        awaiting.set(true);
        Bukkit.broadcastMessage(Util.color("&b&m------------------------------------"));
        Bukkit.broadcastMessage(Util.color("  &b&lЧАТ-ГРА &7(раунд " + round + "/" + cfgInt("rounds", 3) + ")"));
        Bukkit.broadcastMessage(Util.color("  &e" + question));
        Bukkit.broadcastMessage(Util.color("&b&m------------------------------------"));
    }

    private String scramble(String word) {
        List<Character> chars = new ArrayList<Character>();
        for (char c : word.toCharArray()) {
            chars.add(c);
        }
        String out = word;
        for (int i = 0; i < 10 && out.equals(word); i++) {
            Collections.shuffle(chars);
            StringBuilder sb = new StringBuilder();
            for (char c : chars) {
                sb.append(c);
            }
            out = sb.toString();
        }
        return out;
    }

    /** Викликається з асинхронного потоку чату - тут тільки швидка перевірка. */
    @Override
    public boolean handleChat(Player p, String msg) {
        if (!awaiting.get()) {
            return false;
        }
        String m = msg.trim();
        boolean ok = caseSensitive ? m.equals(answer) : m.equalsIgnoreCase(answer);
        if (!ok || !awaiting.compareAndSet(true, false)) {
            return false;
        }
        final long took = System.currentTimeMillis() - askedAt;
        final UUID u = p.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                onAnswer(u, took);
            }
        });
        return true;
    }

    private void onAnswer(UUID u, long took) {
        Player p = Bukkit.getPlayer(u);
        if (state != State.RUNNING) {
            return;
        }
        if (p == null) {
            awaiting.set(true);
            return;
        }
        if (took < cfgInt("min-answer-ms", 600)) {
            plugin.getAntiCheat().alert(p.getName() + " &7відповів у чат-грі за &c" + took + " мс &7(підозра на бота) - приз не видано");
            p.sendMessage(plugin.prefix() + Util.color("&cЗанадто швидка відповідь - приз не зараховано."));
            awaiting.set(true);
            return;
        }
        Integer old = won.get(u);
        won.put(u, old == null ? 1 : old + 1);
        plugin.getStats().addWin(p);
        runCommands(cfgList("rewards.1"), p, 1);
        Bukkit.broadcastMessage(plugin.prefix() + Util.color("&a" + p.getName() + " &7відповів першим за &f"
                + String.format("%.2f", took / 1000.0) + " c&7!"));
        Util.sound(p, "entity.player.levelup", 1f, 1.2f);
        afterRound();
    }

    @Override
    protected List<UUID> buildRanking() {
        List<Map.Entry<UUID, Integer>> list = new ArrayList<Map.Entry<UUID, Integer>>(won.entrySet());
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
    protected String barText() {
        return getName();
    }

    @Override
    protected void onCleanup() {
        awaiting.set(false);
        won.clear();
    }
}
