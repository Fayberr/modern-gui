package net.fayber.faybergui;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fayber GUI: a general-purpose modern GUI widget library for Minecraft mods.
 *
 * <p>Mods build screens out of the widgets in {@code net.fayber.faybergui.widget} (dark rounded
 * cards, physical-pixel rendering, anti-aliased corners, bundled Inter font) and lay them out on
 * their own {@code Screen}s. {@link net.fayber.faybergui.screen.FayberGuiScreen} is the base
 * screen that hosts popups (dropdowns, modals, toasts, rich tooltips).
 *
 * <p>The library is designed to be embedded: a mod either ships it as a jar-in-jar (loom
 * {@code include}) or declares it an optional dependency and falls back when absent.
 */
public class FayberGuiClient implements ClientModInitializer {
    public static final String MOD_ID = "faybergui";
    public static final Logger LOGGER = LoggerFactory.getLogger("FayberGui");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Fayber GUI initialized");

        // /faybergui showcase: open the widget catalog in-game (no startup flag needed).
        ShowcaseCommand.register();

        // Widget catalog workbench, only ever active with -Dfaybergui.preview=true (dev runs).
        if (net.fayber.faybergui.dev.PreviewHook.enabled()) {
            net.fayber.faybergui.dev.PreviewHook.register();
        }
    }
}
