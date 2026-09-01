package net.fayber.faybergui.dev;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fayber.faybergui.FayberGuiClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Widget catalog workbench: opens the widget test screen shortly after the dev client reaches the
 * title screen, so the look can be iterated on headlessly (see {@code tools/preview.sh}). Inert
 * unless the JVM is started with {@code -Dfaybergui.preview=true}.
 */
public final class PreviewHook {
    private PreviewHook() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean("faybergui.preview");
    }

    public static void register() {
        FayberGuiClient.LOGGER.info("Fayber GUI preview hook armed");
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
                FayberGuiClient.LOGGER.info("PREVIEW: opening the widget test screen");
                client.setScreen(new net.fayber.faybergui.dev.WidgetTestScreen());
            }
        });
    }
}
