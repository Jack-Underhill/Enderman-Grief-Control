package endermangriefcontrol.heldblock;

import endermangriefcontrol.EndermanGriefControlPlugin;
import org.bukkit.World;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/**
 * Finds and resolves endermen that are stuck holding a block placement can no longer clear (e.g.
 * picked up before the plugin was enabled, or during a window where it was toggled off).
 *
 * Detection is fully demand-driven: a discovery scan (over currently loaded endermen only — an
 * unloaded holder isn't contributing to any mob cap either, so there's nothing to miss) seeds a
 * set of known-holder UUIDs, and a resolution pass re-checks only that set on a fixed interval.
 * The resolution task only runs while the set is non-empty, so nothing ticks in the background
 * when there's nothing to track. A UUID is only ever removed explicitly (resolved via clearing,
 * or the enderman died) — never inferred from a lookup miss, since that's ambiguous between
 * "unloaded" and "dead."
 */
public final class HeldBlockMonitor implements Listener {

    private static final long RESOLUTION_PERIOD_TICKS = 20L * 60 * 2; // 2 minutes

    private final EndermanGriefControlPlugin plugin;
    private final Set<UUID> knownHolders = new HashSet<>();
    private BukkitTask resolutionTask;

    public HeldBlockMonitor(EndermanGriefControlPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Scans all currently loaded endermen for a carried block and adds any found to the
     * known-holders set. Never removes anything — absence from this scan doesn't mean resolved,
     * it could just mean unloaded.
     */
    public void runDiscoveryScan() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Enderman enderman : world.getEntitiesByClass(Enderman.class)) {
                if (enderman.getCarriedBlock() != null) {
                    knownHolders.add(enderman.getUniqueId());
                }
            }
        }

        if (!knownHolders.isEmpty()) {
            ensureResolutionTaskRunning();
        }
    }

    /**
     * Re-checks each known-holder UUID directly (not a full re-scan) and acts per that enderman's
     * world handling mode. A UUID that can't currently be resolved to a loaded entity is left in
     * the set as-is; it'll resolve itself once that chunk loads again.
     */
    void runResolutionPass() {
        Iterator<UUID> iterator = knownHolders.iterator();
        while (iterator.hasNext()) {
            UUID id = iterator.next();
            Entity entity = plugin.getServer().getEntity(id);
            if (!(entity instanceof Enderman enderman) || enderman.getCarriedBlock() == null) {
                if (entity != null) {
                    iterator.remove(); // Found, but no longer holding anything - resolved.
                }
                continue;
            }

            switch (plugin.getHeldBlockHandling(enderman.getWorld().getName())) {
                case ALERT -> plugin.logHeldBlockAlert(enderman);
                case AUTO_CLEAR -> {
                    enderman.setCarriedBlock(null);
                    plugin.logHeldBlockCleared(enderman);
                    iterator.remove();
                }
                case OFF -> {
                    // Leave it tracked and untouched; picked up again if the mode later changes.
                }
            }
        }

        if (knownHolders.isEmpty()) {
            stopResolutionTask();
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntityType() == EntityType.ENDERMAN) {
            knownHolders.remove(event.getEntity().getUniqueId());
        }
    }

    private void ensureResolutionTaskRunning() {
        if (resolutionTask == null) {
            resolutionTask = plugin.getServer().getScheduler().runTaskTimer(
                    plugin, this::runResolutionPass, RESOLUTION_PERIOD_TICKS, RESOLUTION_PERIOD_TICKS);
        }
    }

    private void stopResolutionTask() {
        if (resolutionTask != null) {
            resolutionTask.cancel();
            resolutionTask = null;
        }
    }

    public boolean isTrackingAnyHolder() {
        return !knownHolders.isEmpty();
    }

    public boolean isResolutionTaskRunning() {
        return resolutionTask != null;
    }
}
