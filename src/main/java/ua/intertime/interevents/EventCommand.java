package ua.intertime.interevents;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class EventCommand implements CommandExecutor, TabCompleter {
    private final InterEvents plugin;

    public EventCommand(InterEvents plugin) {
        this.plugin = plugin;
    }

    private void msg(CommandSender s, String text) {
        s.sendMessage(plugin.prefix() + Util.color(text));
    }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("interevents.admin")) {
            return true;
        }
        msg(s, "&cУ вас немає прав.");
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        EventManager em = plugin.getEventManager();
        switch (sub) {
            case "join": {
                if (!(sender instanceof Player)) {
                    msg(sender, "&cТільки для гравців.");
                    return true;
                }
                String err = em.join((Player) sender);
                if (err != null) {
                    sender.sendMessage(plugin.prefix() + Util.color(err));
                } else {
                    msg(sender, plugin.m("joined", "&aВи приєдналися до івенту! Чекайте старту."));
                }
                return true;
            }
            case "leave": {
                if (!(sender instanceof Player)) {
                    msg(sender, "&cТільки для гравців.");
                    return true;
                }
                String err = em.leave((Player) sender);
                if (err != null) {
                    sender.sendMessage(plugin.prefix() + Util.color(err));
                } else {
                    msg(sender, plugin.m("left", "&eВи покинули івент."));
                }
                return true;
            }
            case "list": {
                msg(sender, "&6Доступні івенти:");
                for (GameEvent e : em.all()) {
                    String st = em.getActive() == e ? " &a[ЙДЕ: " + e.getState().name() + "]" : "";
                    String off = em.isEnabled(e.getId()) ? "" : " &c[вимкнено]";
                    String arena = e.isGlobal() ? "" : (em.getArena(e.getId()) == null ? " &8(арена не задана)" : "");
                    sender.sendMessage(Util.color(" &e" + e.getId() + " &7- " + e.getName() + st + off + arena));
                }
                return true;
            }
            case "info": {
                GameEvent e = args.length > 1 ? em.get(args[1]) : em.getActive();
                if (e == null) {
                    msg(sender, "&cВкажіть івент: /event info <івент>");
                    return true;
                }
                msg(sender, e.getName());
                sender.sendMessage(Util.color(" &7" + e.getDescription()));
                if (em.getActive() == e && e.getZoneCenter() != null) {
                    sender.sendMessage(Util.color(" &7Координати: &fX " + e.getZoneCenter().getBlockX() + " Y "
                            + e.getZoneCenter().getBlockY() + " Z " + e.getZoneCenter().getBlockZ()));
                }
                if (!e.isGlobal()) {
                    sender.sendMessage(Util.color(" &7Гравців: &f"
                            + plugin.getConfig().getInt("events." + e.getId() + ".min-players", 2) + "-"
                            + plugin.getConfig().getInt("events." + e.getId() + ".max-players", 30)));
                }
                if (em.getActive() == e) {
                    sender.sendMessage(Util.color(" &7Стан: &a" + e.getState().name() + " &7| учасників: &f" + e.playerCount()));
                }
                return true;
            }
            case "top": {
                List<Map.Entry<String, Integer>> top = plugin.getStats().top("wins", 10);
                msg(sender, "&6Топ переможців івентів:");
                if (top.isEmpty()) {
                    sender.sendMessage(Util.color(" &7Поки порожньо."));
                }
                int i = 1;
                for (Map.Entry<String, Integer> en : top) {
                    sender.sendMessage(Util.color(" &e" + (i++) + ". &f" + en.getKey() + " &7- &a" + en.getValue() + " перемог"));
                }
                return true;
            }
            case "stats": {
                OfflinePlayer target = null;
                if (args.length > 1) {
                    target = Bukkit.getOfflinePlayer(args[1]);
                } else if (sender instanceof Player) {
                    target = (Player) sender;
                }
                if (target == null) {
                    msg(sender, "&cВкажіть гравця.");
                    return true;
                }
                msg(sender, "&6Статистика &f" + target.getName() + "&6: &7ігор &f" + plugin.getStats().get(target.getUniqueId(), "played")
                        + " &7| перемог &a" + plugin.getStats().get(target.getUniqueId(), "wins")
                        + " &7| вбивств &c" + plugin.getStats().get(target.getUniqueId(), "kills"));
                return true;
            }
            case "start": {
                if (!admin(sender)) {
                    return true;
                }
                if (args.length < 2) {
                    msg(sender, "&cВикористання: /event start <івент>");
                    return true;
                }
                String err = em.open(args[1]);
                if (err != null) {
                    msg(sender, err);
                } else {
                    msg(sender, "&aІвент запущено.");
                }
                return true;
            }
            case "stop": {
                if (!admin(sender)) {
                    return true;
                }
                msg(sender, em.stopActive() ? "&aІвент зупинено." : "&cЗараз немає активного івенту.");
                return true;
            }
            case "setarena": {
                if (!admin(sender)) {
                    return true;
                }
                if (!(sender instanceof Player) || args.length < 2) {
                    msg(sender, "&cВикористання (гравцем): /event setarena <івент>");
                    return true;
                }
                if (args[1].equalsIgnoreCase("all")) {
                    for (GameEvent ev : em.all()) {
                        em.setArena(ev.getId(), ((Player) sender).getLocation());
                    }
                    msg(sender, "&aЦентр пошуку встановлено для всіх івентів. Події з'являтимуться навколо цієї точки.");
                    return true;
                }
                GameEvent e = em.get(args[1]);
                if (e == null) {
                    msg(sender, "&cНевідомий івент.");
                    return true;
                }
                em.setArena(e.getId(), ((Player) sender).getLocation());
                msg(sender, "&aЦентр для &f" + e.getId() + " &aвстановлено тут. Подія з'являтиметься в радіусі навколо цієї точки (radius у config.yml).");
                return true;
            }
            case "give": {
                if (!admin(sender)) {
                    return true;
                }
                if (args.length < 3) {
                    msg(sender, "&cВикористання: /event give <гравець|*> <предмет> [кількість]. Предмети: /event items");
                    return true;
                }
                int amount = 1;
                if (args.length > 3) {
                    try {
                        amount = Math.max(1, Math.min(2304, Integer.parseInt(args[3])));
                    } catch (NumberFormatException ex) {
                        msg(sender, "&cКількість має бути числом.");
                        return true;
                    }
                }
                String itemId = args[2].toLowerCase();
                if (plugin.getItems().create(itemId, 1) == null) {
                    msg(sender, "&cНевідомий предмет. Список: /event items");
                    return true;
                }
                List<Player> targets = new ArrayList<Player>();
                if (args[1].equals("*")) {
                    targets.addAll(Bukkit.getOnlinePlayers());
                } else {
                    Player t = Bukkit.getPlayerExact(args[1]);
                    if (t == null) {
                        msg(sender, "&cГравця не знайдено онлайн.");
                        return true;
                    }
                    targets.add(t);
                }
                for (Player t : targets) {
                    plugin.getItems().give(t, itemId, amount);
                    t.sendMessage(plugin.prefix() + Util.color("&aВи отримали &f" + amount + "x &a" + itemId));
                }
                msg(sender, "&aВидано " + amount + "x " + itemId + " гравцям: " + targets.size());
                return true;
            }
            case "items": {
                if (!admin(sender)) {
                    return true;
                }
                msg(sender, "&6Предмети для /event give: &f" + String.join(", ", plugin.getItems().ids()));
                return true;
            }
            case "reload": {
                if (!admin(sender)) {
                    return true;
                }
                plugin.reload();
                msg(sender, "&aКонфіг перезавантажено.");
                return true;
            }
            case "auto": {
                if (!admin(sender)) {
                    return true;
                }
                if (args.length < 2) {
                    msg(sender, "&7Автоматичні івенти: " + (em.isAutoEnabled() ? "&aувімкнено" : "&cвимкнено") + " &7(/event auto on|off)");
                    return true;
                }
                if (args[1].equalsIgnoreCase("on")) {
                    em.setAutoEnabled(true);
                    msg(sender, "&aАвтозапуск увімкнено.");
                } else {
                    em.stopAuto();
                    msg(sender, "&eАвтозапуск вимкнено (до перезавантаження конфігу).");
                }
                return true;
            }
            case "ac": {
                if (!sender.hasPermission("interevents.alerts") && !sender.hasPermission("interevents.admin")) {
                    msg(sender, "&cУ вас немає прав.");
                    return true;
                }
                if (args.length < 2) {
                    msg(sender, "&cВикористання: /event ac <гравець>");
                    return true;
                }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) {
                    msg(sender, "&cГравця не знайдено.");
                    return true;
                }
                msg(sender, "&6Античіт &f" + t.getName() + "&6: &7" + plugin.getAntiCheat().summary(t));
                return true;
            }
            case "tp": {
                if (!admin(sender)) {
                    return true;
                }
                if (!(sender instanceof Player)) {
                    msg(sender, "&cТільки для гравців.");
                    return true;
                }
                GameEvent act = em.getActive();
                if (act == null || act.getZoneCenter() == null) {
                    msg(sender, "&cЗараз немає події з відомими координатами.");
                    return true;
                }
                ((Player) sender).teleport(act.getZoneCenter().clone().add(0, 2, 0));
                msg(sender, "&aТелепортовано до зони події.");
                return true;
            }
            default:
                help(sender);
                return true;
        }
    }

    private void help(CommandSender s) {
        s.sendMessage(Util.color("&6&m------&e InterEvents &6&m------"));
        s.sendMessage(Util.color(" &e/event join &7- приєднатись до лобі"));
        s.sendMessage(Util.color(" &e/event leave &7- вийти з івенту"));
        s.sendMessage(Util.color(" &e/event list &7- усі івенти"));
        s.sendMessage(Util.color(" &e/event info [івент] &7- опис"));
        s.sendMessage(Util.color(" &e/event top &7| &e/event stats [гравець]"));
        if (s.hasPermission("interevents.admin")) {
            s.sendMessage(Util.color(" &c/event start <івент> &7- запустити лобі"));
            s.sendMessage(Util.color(" &c/event stop &7- зупинити поточний івент"));
            s.sendMessage(Util.color(" &c/event setarena <івент> &7- арена (де ви стоїте)"));
            s.sendMessage(Util.color(" &c/event give <гравець|*> <предмет> [к-сть]"));
            s.sendMessage(Util.color(" &c/event items &7| &c/event reload &7| &c/event auto on|off"));
            s.sendMessage(Util.color(" &c/event ac <гравець> &7- порушення античіта"));
            s.sendMessage(Util.color(" &c/event tp &7- до зони активної події"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        boolean adm = sender.hasPermission("interevents.admin");
        if (args.length == 1) {
            List<String> subs = new ArrayList<String>(Arrays.asList("join", "leave", "list", "info", "top", "stats", "help"));
            if (adm) {
                subs.addAll(Arrays.asList("start", "stop", "setarena", "give", "items", "reload", "auto", "ac", "tp"));
            }
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) {
                    out.add(s);
                }
            }
            return out;
        }
        String sub = args[0].toLowerCase();
        if (args.length == 2) {
            if (sub.equals("info") || (adm && (sub.equals("start") || sub.equals("setarena")))) {
                for (GameEvent e : plugin.getEventManager().all()) {
                    if (e.getId().startsWith(args[1].toLowerCase())) {
                        out.add(e.getId());
                    }
                }
                if (sub.equals("setarena") && "all".startsWith(args[1].toLowerCase())) {
                    out.add("all");
                }
            } else if (adm && sub.equals("give")) {
                out.add("*");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        out.add(p.getName());
                    }
                }
            } else if (adm && sub.equals("auto")) {
                out.add("on");
                out.add("off");
            } else if (sub.equals("stats") || sub.equals("ac")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        out.add(p.getName());
                    }
                }
            }
        } else if (args.length == 3 && adm && sub.equals("give")) {
            for (String id : plugin.getItems().ids()) {
                if (id.startsWith(args[2].toLowerCase())) {
                    out.add(id);
                }
            }
        } else if (args.length == 4 && adm && sub.equals("give")) {
            out.addAll(Arrays.asList("1", "5", "16", "64"));
        }
        return out;
    }
}
