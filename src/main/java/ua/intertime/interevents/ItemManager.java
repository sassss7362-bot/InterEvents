package ua.intertime.interevents;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/** Предмети для команди /event give (описуються в config.yml, розділ items). */
public class ItemManager {
    private final InterEvents plugin;
    private final NamespacedKey key;

    public ItemManager(InterEvents plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "item-id");
    }

    public Set<String> ids() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("items");
        if (s == null) {
            return Collections.<String>emptySet();
        }
        return s.getKeys(false);
    }

    public ItemStack create(String id, int amount) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("items." + id);
        if (s == null) {
            return null;
        }
        Material m = Material.matchMaterial(s.getString("material", "PAPER"));
        if (m == null || m == Material.AIR) {
            m = Material.PAPER;
        }
        ItemStack it = new ItemStack(m, Math.max(1, Math.min(amount, m.getMaxStackSize())));
        ItemMeta meta = it.getItemMeta();
        if (meta == null) {
            return it;
        }
        meta.setDisplayName(Util.color(s.getString("name", "&f" + id)));
        List<String> lore = new ArrayList<String>();
        for (String l : s.getStringList("lore")) {
            lore.add(Util.color(l));
        }
        meta.setLore(lore);
        if (s.getBoolean("glow", false)) {
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        return it;
    }

    public String idOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        return it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public boolean consume(Player p, String id) {
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack it = p.getInventory().getItem(i);
            if (it != null && id.equals(idOf(it))) {
                if (it.getAmount() > 1) {
                    it.setAmount(it.getAmount() - 1);
                } else {
                    p.getInventory().setItem(i, null);
                }
                return true;
            }
        }
        return false;
    }

    /** Видає предмет, залишок, що не влазить, викидає під ноги. Повертає false, якщо такого id немає. */
    public boolean give(Player p, String id, int amount) {
        int left = Math.max(1, amount);
        if (create(id, 1) == null) {
            return false;
        }
        while (left > 0) {
            ItemStack stack = create(id, left);
            left -= stack.getAmount();
            HashMap<Integer, ItemStack> rest = p.getInventory().addItem(stack);
            for (ItemStack r : rest.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), r);
            }
        }
        return true;
    }
}
