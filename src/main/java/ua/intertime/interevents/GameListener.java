package ua.intertime.interevents;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Передає події Bukkit у поточний активний івент. */
public class GameListener implements Listener {
    private final InterEvents plugin;

    public GameListener(InterEvents plugin) {
        this.plugin = plugin;
    }

    private GameEvent ev() {
        return plugin.getEventManager().getActive();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        GameEvent a = ev();
        if (a != null) {
            a.handleDamage(e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        GameEvent a = ev();
        if (a != null) {
            a.handleBlockBreak(e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        GameEvent a = ev();
        if (a != null) {
            a.handleBlockPlace(e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent e) {
        GameEvent a = ev();
        if (a != null) {
            a.handleBlockDamage(e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        GameEvent a = ev();
        if (a != null) {
            a.handleInteract(e);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        GameEvent a = ev();
        if (a != null && e.getTo() != null && a.isParticipant(e.getPlayer())) {
            a.handleMove(e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncPlayerChatEvent e) {
        GameEvent a = ev();
        if (a != null && a.handleChat(e.getPlayer(), e.getMessage())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        GameEvent a = ev();
        if (a != null) {
            a.handleEntityDeath(e);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        GameEvent a = ev();
        if (a != null) {
            a.handlePlayerQuit(e.getPlayer());
            if (a.isParticipant(e.getPlayer())) {
                a.handleQuit(e.getPlayer());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        GameEvent a = ev();
        if (a != null) {
            a.handleExplode(e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        GameEvent a = ev();
        if (a != null) {
            a.handleBlockExplode(e);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        GameEvent a = ev();
        if (a != null) {
            a.handleJoin(p);
        }
        if (plugin.getSnapshots().has(p.getUniqueId()) && (a == null || !a.isParticipant(p))) {
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    GameEvent cur = ev();
                    if (p.isOnline() && (cur == null || !cur.isParticipant(p)) && plugin.getSnapshots().restore(p)) {
                        p.sendMessage(plugin.prefix() + Util.color("&aВаш інвентар і позицію відновлено після івенту."));
                    }
                }
            }, 10L);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        GameEvent a = ev();
        if (a != null && a.isParticipant(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        GameEvent a = ev();
        if (a != null && e.getEntity() instanceof Player && a.isParticipant((Player) e.getEntity())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        GameEvent a = ev();
        if (a != null && e.getEntity() instanceof Player && a.isParticipant((Player) e.getEntity())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        GameEvent a = ev();
        Player p = e.getEntity();
        if (a != null && a.isParticipant(p)) {
            e.setKeepInventory(true);
            e.getDrops().clear();
            e.setKeepLevel(true);
            e.setDroppedExp(0);
            e.setDeathMessage(null);
            a.handleDeath(p);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        final GameEvent a = ev();
        final Player p = e.getPlayer();
        if (a != null && a.isParticipant(p)) {
            Location arenaLoc = plugin.getEventManager().getArena(a.getId());
            if (arenaLoc != null) {
                e.setRespawnLocation(arenaLoc.clone().add(0.5, 8.0, 0.5));
            }
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    if (p.isOnline() && a.isParticipant(p)) {
                        p.setGameMode(org.bukkit.GameMode.SPECTATOR);
                    }
                }
            });
        }
    }
}
