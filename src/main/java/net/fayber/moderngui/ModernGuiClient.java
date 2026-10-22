package net.fayber.moderngui;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Modern GUI: a general-purpose modern GUI widget library for Minecraft mods. Mods build screens
 * out of the widgets in {@code net.fayber.moderngui.widget}; {@link
 * net.fayber.moderngui.screen.ModernGuiScreen} is the base screen that hosts popups. A mod either
 * ships the library as a jar-in-jar (loom {@code include}) or declares it an optional dependency.
 */
public class ModernGuiClient implements ClientModInitializer {
    public static final String MOD_ID = "moderngui";
    public static final Logger LOGGER = LoggerFactory.getLogger("ModernGui");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Modern GUI initialized");

        // Registers the bundled families (Inter, Inter Display) on first touch, then any fonts the
        // player dropped into config/moderngui/fonts. Before anything else: the generated font pack
        // must be registered while the resource packs are still being discovered.
        net.fayber.moderngui.font.FontFamilies.loadUserFonts();

        // Sharp item icons at any size (net.fayber.moderngui.render.ItemIcon). Must happen during
        // client init: the game renderer snapshots the picture-in-picture renderers when it is built.
        net.fayber.moderngui.render.ItemIconRenderer.register();

        // /moderngui showcase: open the widget catalog in-game (no startup flag needed).
        ShowcaseCommand.register();

        // Widget catalog workbench, only ever active with -Dmoderngui.preview=true (dev runs).
        if (net.fayber.moderngui.dev.PreviewHook.enabled()) {
            net.fayber.moderngui.dev.PreviewHook.register();
        }
    }
}
