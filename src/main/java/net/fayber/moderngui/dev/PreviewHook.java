package net.fayber.moderngui.dev;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fayber.moderngui.ModernGuiClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Widget catalog workbench: opens the widget test screen shortly after the dev client reaches the
 * title screen, so the look can be iterated on headlessly. Inert unless the JVM is started with
 * {@code -Dmoderngui.preview=true}. Extra dev switches (see build.gradle):
 *
 * <ul>
 *   <li>{@code moderngui.previewWorld=true}: create and join a flat creative world first. Vanilla
 *       binds item components only when a world loads, so item icons need one to show.
 *   <li>{@code moderngui.previewShot=<png>}: a few seconds after the showcase opens, save the
 *       framebuffer and quit.
 * </ul>
 */
public final class PreviewHook {
    private PreviewHook() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean("moderngui.preview");
    }

    public static void register() {
        ModernGuiClient.LOGGER.info("Modern GUI preview hook armed");
        boolean world = Boolean.getBoolean("moderngui.previewWorld");
        ClientTickEvents.END_CLIENT_TICK.register(new ClientTickEvents.EndTick() {
            private int ticks = 0;
            private boolean opened = false;
            private boolean creating = false;

            @Override
            public void onEndTick(Minecraft client) {
                if (this.opened || client.getOverlay() != null) {
                    return;
                }
                if (world) {
                    if (client.level == null) {
                        if (client.screen != null && !this.creating) {
                            this.creating = true;
                            ModernGuiClient.LOGGER.info("PREVIEW: creating a flat world");
                            client.createWorldOpenFlows().createFreshLevel("mg_preview",
                                    new LevelSettings("mg_preview", GameType.CREATIVE,
                                            new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false),
                                            true, WorldDataConfiguration.DEFAULT),
                                    WorldOptions.defaultWithRandomSeed(), WorldPresets::createFlatWorldDimensions,
                                    new TitleScreen());
                        }
                        return;
                    }
                    if (client.player == null || client.screen != null || ++this.ticks < 100) {
                        return;
                    }
                } else if (client.screen == null || client.screen instanceof WidgetTestScreen
                        // Any screen will do: a fresh dev profile first shows the onboarding screen.
                        || ++this.ticks < 20) {
                    return;
                }
                this.opened = true;
                ModernGuiClient.LOGGER.info("PREVIEW: opening the widget test screen");
                client.setScreen(new WidgetTestScreen());
            }
        });
        String shot = System.getProperty("moderngui.previewShot");
        if (shot == null) {
            return;
        }
        // Dev screenshots: a few seconds after the showcase opens (icons need frames to bake and
        // the first slide to settle), save the framebuffer and quit.
        ClientTickEvents.END_CLIENT_TICK.register(new ClientTickEvents.EndTick() {
            private int ticks = 0;
            private boolean done = false;

            @Override
            public void onEndTick(Minecraft client) {
                if (this.done || client.getOverlay() != null || !(client.screen instanceof WidgetTestScreen)) {
                    return;
                }
                if (++this.ticks < 100) {
                    return;
                }
                this.done = true;
                net.minecraft.client.Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
                    try {
                        image.writeToFile(java.nio.file.Path.of(shot));
                        ModernGuiClient.LOGGER.info("PREVIEW: wrote {}", shot);
                    } catch (java.io.IOException e) {
                        ModernGuiClient.LOGGER.error("PREVIEW: screenshot failed", e);
                    } finally {
                        image.close();
                        client.execute(client::stop);
                    }
                });
            }
        });
    }
}
