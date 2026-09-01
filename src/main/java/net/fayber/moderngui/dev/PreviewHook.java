package net.fayber.moderngui.dev;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fayber.moderngui.ModernGuiClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Widget catalog workbench: opens the widget test screen shortly after the dev client reaches the
 * title screen, so the look can be iterated on headlessly (see {@code tools/preview.sh}). Inert
 * unless the JVM is started with {@code -Dmoderngui.preview=true}.
 */
public final class PreviewHook {
    private PreviewHook() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean("moderngui.preview");
    }

    public static void register() {
        ModernGuiClient.LOGGER.info("Modern GUI preview hook armed");
        ClientTickEvents.END_CLIENT_TICK.register(new ClientTickEvents.EndTick() {
            private int ticks = 0;
            private boolean opened = false;

            @Override
            public void onEndTick(Minecraft client) {
                if (this.opened || !(client.screen instanceof TitleScreen)) {
                    return;
                }
                if (++this.ticks < 20) {
                    return;
                }
                this.opened = true;
                ModernGuiClient.LOGGER.info("PREVIEW: opening the widget test screen");
                client.setScreen(new net.fayber.moderngui.dev.WidgetTestScreen());
            }
        });
    }
}
