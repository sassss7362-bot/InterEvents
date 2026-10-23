package ua.intertime.interevents;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Лут для скринь і руди. Формат рядка: НАЗВА:мін-макс:шанс% , НАЗВА - матеріал або id предмета з items. */
public final class LootUtil {
    private LootUtil() {
    }

    public static List<ItemStack> roll(InterEvents plugin, List<String> entries) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        for (String s : entries) {
            try {
                String[] parts = s.split(":");
                String name = parts[0].trim();
                int min = 1;
                int max = 1;
                if (parts.length > 1) {
                    String[] mm = parts[1].split("-");
                    min = Integer.parseInt(mm[0].trim());
                    max = mm.length > 1 ? Integer.parseInt(mm[1].trim()) : min;
                }
                int chance = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 100;
                if (Util.rng().nextInt(100) >= chance) {
                    continue;
                }
                int amount = Util.rnd(min, max);
                Material m = Material.matchMaterial(name);
                ItemStack it;
                if (m != null && m != Material.AIR) {
                    it = new ItemStack(m, Math.max(1, Math.min(amount, m.getMaxStackSize())));
                } else {
                    it = plugin.getItems().create(name.toLowerCase(), amount);
                }
                if (it != null) {
                    out.add(it);
                }
            } catch (Exception ignored) {
                // некоректний рядок - пропускаємо
            }
        }
        return out;
    }

    /** Вибір id за вагами: "talisman_dragon:10". */
    public static String weighted(List<String> pool) {
        int total = 0;
        List<Integer> w = new ArrayList<Integer>();
        for (String s : pool) {
            int v = 1;
            try {
                String[] p = s.split(":");
                if (p.length > 1) {
                    v = Math.max(1, Integer.parseInt(p[1].trim()));
                }
            } catch (Exception ignored) {
            }
            w.add(v);
            total += v;
        }
        if (total <= 0) {
            return null;
        }
        int r = Util.rng().nextInt(total);
        for (int i = 0; i < pool.size(); i++) {
            r -= w.get(i);
            if (r < 0) {
                return pool.get(i).split(":")[0].trim().toLowerCase();
            }
        }
        return null;
    }

    public static ItemStack uniqueItem(InterEvents plugin, List<String> pool) {
        String id = weighted(pool);
        return id == null ? null : plugin.getItems().create(id, 1);
    }

    /** Кладе предмети у випадкові вільні слоти. */
    public static void place(Inventory inv, List<ItemStack> items) {
        for (ItemStack it : items) {
            List<Integer> free = new ArrayList<Integer>();
            for (int i = 0; i < inv.getSize(); i++) {
                if (inv.getItem(i) == null) {
                    free.add(i);
                }
            }
            if (free.isEmpty()) {
                inv.addItem(it);
            } else {
                inv.setItem(free.get(Util.rng().nextInt(free.size())), it);
            }
        }
    }

    public static boolean isEmpty(Inventory inv) {
        for (ItemStack it : inv.getContents()) {
            if (it != null && it.getType() != Material.AIR) {
                return false;
            }
        }
        return true;
    }
}
