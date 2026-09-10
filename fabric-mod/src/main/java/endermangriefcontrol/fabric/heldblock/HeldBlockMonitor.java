package endermangriefcontrol.fabric.heldblock;

import endermangriefcontrol.fabric.EndermanGriefControlMod;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/**
 * Finds and resolves endermen that are stuck holding a block placement can no longer clear (e.g.
 * picked up before the mod was enabled, or during a window where it was toggled off).
 *
 * Detection is fully demand-driven: a discovery scan (over currently loaded endermen only - an
 * unloaded holder isn't contributing to any mob cap either, so there's nothing to miss) seeds a
 * set of known-holder UUIDs, and a resolution pass re-checks only that set every ~2 minutes. Unlike
 * the Paper plugin's start/stop BukkitTask, Fabric's END_SERVER_TICK callback is registered once
 * for the mod's lifetime (the idiomatic pattern here) and internally no-ops whenever the set is
 * empty, so there's still no real scanning work done when there's nothing to track. A UUID is only
 * ever removed explicitly (resolved via clearing, or the enderman died) - never inferred from a
 * lookup miss, since that's ambiguous between "unloaded" and "dead."
 */
public final class HeldBlockMonitor {

    private static final long RESOLUTION_PERIOD_TICKS = 20L * 60 * 2; // 2 minutes

    private final Set<UUID> knownHolders = new HashSet<>();
    private MinecraftServer server;
    private long tickCounter;

    public void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(startedServer -> {
            server = startedServer;
            runDiscoveryScan();
        });
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
        ServerLivingEntityEvents.AFTER_DEATH.register(this::onEntityDeath);
    }

    /**
     * Scans all currently loaded endermen for a carried block and adds any found to the
     * known-holders set. Never removes anything - absence from this scan doesn't mean resolved, it
     * could just mean unloaded. A no-op before the server has started (nothing to scan yet).
     */
    public void runDiscoveryScan() {
        if (server == null) {
            return;
        }

        for (ServerLevel level : server.getAllLevels()) {
            for (EnderMan enderman : level.getEntities(EntityTypeTest.forClass(EnderMan.class), e -> true)) {
                if (enderman.getCarriedBlock() != null) {
                    knownHolders.add(enderman.getUUID());
                }
            }
        }
    }

    private void onServerTick(MinecraftServer tickedServer) {
        if (knownHolders.isEmpty()) {
            return;
        }

        tickCounter++;
        if (tickCounter % RESOLUTION_PERIOD_TICKS == 0) {
            runResolutionPass();
        }
    }

    /**
     * Re-checks each known-holder UUID directly (not a full re-scan) and acts per the mod's held-
     * block handling mode. A UUID that can't currently be resolved to a loaded entity is left in
     * the set as-is; it'll resolve itself once that chunk loads again.
     */
    private void runResolutionPass() {
        Iterator<UUID> iterator = knownHolders.iterator();
        while (iterator.hasNext()) {
            UUID id = iterator.next();
            Entity found = findEntity(id);
            if (!(found instanceof EnderMan enderman) || enderman.getCarriedBlock() == null) {
                if (found != null) {
                    iterator.remove(); // Found, but no longer holding anything - resolved.
                }
                continue;
            }

            HeldBlockHandling handling = HeldBlockHandling.fromConfig(
                    EndermanGriefControlMod.getConfig().heldBlockHandling, HeldBlockHandling.AUTO_CLEAR);

            switch (handling) {
                case ALERT -> EndermanGriefControlMod.announceHeldBlockAlert(enderman);
                case AUTO_CLEAR -> {
                    enderman.setCarriedBlock(null);
                    EndermanGriefControlMod.announceHeldBlockCleared(enderman);
                    iterator.remove();
                }
                case OFF -> {
                    // Leave it tracked and untouched; picked up again if the mode later changes.
                }
            }
        }
    }

    private Entity findEntity(UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private void onEntityDeath(LivingEntity entity, DamageSource damageSource) {
        if (entity instanceof EnderMan) {
            knownHolders.remove(entity.getUUID());
        }
    }

    public boolean isTrackingAnyHolder() {
        return !knownHolders.isEmpty();
    }
}
