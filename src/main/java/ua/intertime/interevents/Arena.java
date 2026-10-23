package ua.intertime.interevents;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Арена івенту. Усі зміни блоків запам'ятовуються й відновлюються після завершення.
 * center - блок, на якому стояв адмін (ноги). Підлога = center.y - 1.
 */
public class Arena {
    public final Location center;
    private final Map<Block, BlockData> saved = new LinkedHashMap<Block, BlockData>();

    public Arena(Location c) {
        this.center = new Location(c.getWorld(), c.getBlockX(), c.getBlockY(), c.getBlockZ());
    }

    public World world() {
        return center.getWorld();
    }

    public Block at(int dx, int dy, int dz) {
        return center.getWorld().getBlockAt(center.getBlockX() + dx, center.getBlockY() + dy, center.getBlockZ() + dz);
    }

    public void set(Block b, Material m) {
        if (!saved.containsKey(b)) {
            saved.put(b, b.getBlockData());
        }
        b.setType(m, false);
    }

    public void fill(int dx1, int dy1, int dz1, int dx2, int dy2, int dz2, Material m) {
        for (int x = Math.min(dx1, dx2); x <= Math.max(dx1, dx2); x++) {
            for (int y = Math.min(dy1, dy2); y <= Math.max(dy1, dy2); y++) {
                for (int z = Math.min(dz1, dz2); z <= Math.max(dz1, dz2); z++) {
                    set(at(x, y, z), m);
                }
            }
        }
    }

    /** Диск/кільце: блоки з rMin < відстань <= rMax. rMin &lt; 0 означає суцільний диск. dy - відносно center.y. */
    public void disc(int dy, double rMin, double rMax, Material m) {
        int r = (int) Math.ceil(rMax);
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double d2 = x * x + z * z;
                if (d2 <= rMax * rMax && (rMin < 0 || d2 > rMin * rMin)) {
                    set(at(x, dy, z), m);
                }
            }
        }
    }

    public void restore() {
        for (Map.Entry<Block, BlockData> en : saved.entrySet()) {
            try {
                en.getKey().setBlockData(en.getValue(), false);
            } catch (Throwable ignored) {
            }
        }
        saved.clear();
    }
}
