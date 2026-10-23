package ua.intertime.interevents;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Базовий клас для подій зі скринею: лут, замок, захист від ламання/вибухів, перший відкривач. */
public abstract class ChestEvent extends ZoneEvent {
    protected Block chestBlock;
    protected boolean unlocked;
    protected boolean firstOpened;
    private long lastLockedMsg;

    protected ChestEvent(InterEvents plugin, String id) {
        super(plugin, id);
    }

    protected void placeChest(Location ground) {
        chestBlock = ground.getBlock();
        site.set(chestBlock, Material.CHEST);
        BlockState st = chestBlock.getState();
        if (!(st instanceof Chest)) {
            return;
        }
        Inventory inv = ((Chest) st).getBlockInventory();
        List<ItemStack> items = new ArrayList<ItemStack>(LootUtil.roll(plugin, cfgList("loot")));
        List<String> pool = cfgList("unique-pool");
        int n = cfgInt("unique-count", 2);
        for (int i = 0; i < n && !pool.isEmpty(); i++) {
            ItemStack u = LootUtil.uniqueItem(plugin, pool);
            if (u != null) {
                items.add(u);
            }
        }
        LootUtil.place(inv, items);
    }

    protected boolean chestEmpty() {
        if (chestBlock == null) {
            return true;
        }
        BlockState st = chestBlock.getState();
        if (!(st instanceof Chest)) {
            return true;
        }
        return LootUtil.isEmpty(((Chest) st).getBlockInventory());
    }

    @Override
    public void handleInteract(PlayerInteractEvent e) {
        if (chestBlock == null || e.getClickedBlock() == null || !e.getClickedBlock().equals(chestBlock)) {
            return;
        }
        Player p = e.getPlayer();
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            e.setUseInteractedBlock(Event.Result.DENY);
            return;
        }
        if (!unlocked) {
            e.setUseInteractedBlock(Event.Result.DENY);
            long now = System.currentTimeMillis();
            if (now - lastLockedMsg > 1500L) {
                lastLockedMsg = now;
                p.sendMessage(plugin.prefix() + Util.color("&cСкриня ще зачинена!"));
            }
            return;
        }
        if (denied.contains(p.getUniqueId())) {
            e.setUseInteractedBlock(Event.Result.DENY);
            p.sendMessage(plugin.prefix() + Util.color("&cВам заборонено відкривати цю скриню."));
            return;
        }
        if (p.isFlying() || p.getEyeLocation().distance(chestBlock.getLocation().add(0.5, 0.5, 0.5)) > 6.5) {
            e.setUseInteractedBlock(Event.Result.DENY);
            plugin.getAntiCheat().flag(p, "reach", 2.0, "скриня event з підозрілої позиції");
            return;
        }
        if (!firstOpened) {
            firstOpened = true;
            fixedRanking.add(p.getUniqueId());
            Util.sound(p, "block.chest.open", 1f, 1f);
            org.bukkit.Bukkit.broadcastMessage(plugin.prefix() + Util.color("&f" + p.getName() + " &eпершим відкрив скриню!"));
        }
    }

    @Override
    public void handleBlockBreak(BlockBreakEvent e) {
        if (chestBlock != null && e.getBlock().equals(chestBlock)) {
            e.setCancelled(true);
        }
    }

    @Override
    public void handleExplode(EntityExplodeEvent e) {
        if (chestBlock != null) {
            e.blockList().remove(chestBlock);
        }
    }

    @Override
    public void handleBlockExplode(BlockExplodeEvent e) {
        if (chestBlock != null) {
            e.blockList().remove(chestBlock);
        }
    }
}
