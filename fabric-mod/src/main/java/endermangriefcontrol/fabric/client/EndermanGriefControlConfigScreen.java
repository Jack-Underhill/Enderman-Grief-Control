package endermangriefcontrol.fabric.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import endermangriefcontrol.fabric.EndermanGriefControlConfig;
import endermangriefcontrol.fabric.EndermanGriefControlMod;
import endermangriefcontrol.fabric.heldblock.HeldBlockHandling;

/**
 * A hand-rolled vanilla settings screen (no Cloth Config dependency) shown by Mod Menu. Mutates
 * the shared static config instance in place and saves on every change, so effects are immediate:
 * the pickup/placement mixins and HeldBlockMonitor already re-read that same instance on every
 * check, so no restart or reload is needed.
 */
public final class EndermanGriefControlConfigScreen extends Screen {

    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SPACING = 24;

    private final Screen parent;

    public EndermanGriefControlConfigScreen(Screen parent) {
        super(Component.literal("EndermanGriefControl"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        EndermanGriefControlConfig config = EndermanGriefControlMod.getConfig();
        int centerX = this.width / 2 - BUTTON_WIDTH / 2;
        int startY = this.height / 2 - SPACING * 2;

        this.addRenderableWidget(CycleButton.onOffBuilder(config.enabled)
                .withTooltip(EndermanGriefControlConfigScreen::preventionTooltip)
                .create(centerX, startY, BUTTON_WIDTH, BUTTON_HEIGHT,
                        Component.literal("Prevent Enderman Grief"),
                        (button, value) -> {
                            config.enabled = value;
                            config.save();
                        }));

        this.addRenderableWidget(new StringWidget(centerX, startY + SPACING, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.literal("Logging"), this.font).alignCenter());

        this.addRenderableWidget(CycleButton.onOffBuilder(config.loggingEnabled)
                .withTooltip(EndermanGriefControlConfigScreen::loggingTooltip)
                .create(centerX, startY + SPACING * 2, BUTTON_WIDTH, BUTTON_HEIGHT,
                        Component.literal("Log Denied Attempts"),
                        (button, value) -> {
                            config.loggingEnabled = value;
                            config.save();
                        }));

        HeldBlockHandling currentHandling =
                HeldBlockHandling.fromConfig(config.heldBlockHandling, HeldBlockHandling.AUTO_CLEAR);
        this.addRenderableWidget(CycleButton.<HeldBlockHandling>builder(EndermanGriefControlConfigScreen::displayName)
                .withValues(HeldBlockHandling.AUTO_CLEAR, HeldBlockHandling.ALERT, HeldBlockHandling.OFF)
                .withInitialValue(currentHandling)
                .withTooltip(EndermanGriefControlConfigScreen::heldBlockTooltip)
                .create(centerX, startY + SPACING * 3, BUTTON_WIDTH, BUTTON_HEIGHT,
                        Component.literal("Stuck Holders"),
                        (button, value) -> {
                            config.heldBlockHandling = value.toConfigValue();
                            config.save();
                        }));

        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
                .bounds(centerX, startY + SPACING * 4, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    private static Component displayName(HeldBlockHandling handling) {
        return switch (handling) {
            case AUTO_CLEAR -> Component.literal("Auto-Clear");
            case ALERT -> Component.literal("Alert");
            case OFF -> Component.literal("Off");
        };
    }

    private static Tooltip preventionTooltip(boolean enabled) {
        return Tooltip.create(Component.literal(enabled
                ? "Endermen cannot pick up or place blocks. Every other mob is unaffected."
                : "Vanilla enderman griefing behavior is restored."));
    }

    private static Tooltip loggingTooltip(boolean loggingEnabled) {
        return Tooltip.create(Component.literal(loggingEnabled
                ? "Announces each prevented pickup/placement attempt in chat and the log file."
                : "Prevented pickup/placement attempts are not announced or logged."));
    }

    private static Tooltip heldBlockTooltip(HeldBlockHandling handling) {
        return Tooltip.create(Component.literal(switch (handling) {
            case AUTO_CLEAR -> "Removes the block a stuck enderman is holding. Nothing is dropped.";
            case ALERT -> "Leaves the block, but periodically announces the enderman's location so you can hunt it down yourself.";
            case OFF -> "Leaves stuck holders alone entirely.";
        }));
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
