package noendermangrief.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.EnderMan;
import noendermangrief.fabric.command.EndermanCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NoEndermanGriefMod implements ModInitializer {

    public static final String MOD_ID = "no-enderman-grief";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static NoEndermanGriefConfig config;

    @Override
    public void onInitialize() {
        config = NoEndermanGriefConfig.load();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                EndermanCommand.register(dispatcher));
        LOGGER.info("NoEndermanGrief has been initialized.");
    }

    public static NoEndermanGriefConfig getConfig() {
        return config;
    }

    public static void setConfig(NoEndermanGriefConfig newConfig) {
        config = newConfig;
    }

    /**
     * Called by the pickup/placement mixins whenever a block change was prevented. Logs the same
     * short message to the console/log file that's shown in chat (matching the Paper plugin's log
     * wording), so players — not just admins reading logs — can see it happened.
     */
    public static void announceBlocked(EnderMan enderman, String action) {
        if (!config.loggingEnabled) {
            return;
        }

        String coords = "(" + enderman.getBlockX() + ", " + enderman.getBlockY() + ", " + enderman.getBlockZ() + ")";

        LOGGER.info("[NoEndermanGrief] Denied " + action + " at " + coords + ".");

        if (enderman.level() instanceof ServerLevel serverLevel) {
            MutableComponent chatMessage = Component.literal("[NoEndermanGrief] ")
                    .withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal("Denied " + action + " at ")
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(coords + ".").withStyle(ChatFormatting.GREEN));
            serverLevel.getServer().getPlayerList().broadcastSystemMessage(chatMessage, false);
        }
    }
}
