package net.fayber.moderngui.font;

import net.fabricmc.loader.api.FabricLoader;
import net.fayber.moderngui.ModernGuiClient;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Registry and entry point for fonts. A consumer registers its typeface once, during client
 * initialisation:
 *
 * <pre>{@code
 * FontFamily fancy = FontFamilies.register(
 *         Identifier.fromNamespaceAndPath("mymod", "fancy"),
 *         Identifier.fromNamespaceAndPath("mymod", "fancy_regular.ttf"),   // assets/mymod/font/fancy_regular.ttf
 *         Identifier.fromNamespaceAndPath("mymod", "fancy_bold.ttf"));
 * ModernGui.setDefaultFont(fancy);                       // every Ui.ui / uiBold / SizedText call, or:
 * SizedText.draw(gfx, text, x, y, 1.5f, FontWeight.BOLD, fancy, color);   // just this call
 * }</pre>
 *
 * <p>Registration generates, in the game folder, the font definitions for every weight, size and
 * GUI scale (see {@link FontFamily}) and hands them to the resource loader through {@link
 * FontPack}; the consumer ships only its TTFs. Do it from {@code onInitializeClient}: the packs
 * are discovered right after, and a family registered later only appears after the next resource
 * reload ({@link #reloadResources}).
 *
 * <p>{@link #INTER} is the built-in default. Fonts dropped into {@code config/moderngui/fonts/} are
 * registered too ({@link #userFonts}) so a player can choose them.
 */
public final class FontFamilies {
    private static final FontRegistry REGISTRY = new FontRegistry();
    private static final java.util.List<FontFamily> USER = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static boolean userFontsLoaded;

    /** The bundled Inter: regular, and its semibold as the bold weight. */
    public static final FontFamily INTER = register(FontFamily.builder(Identifier.fromNamespaceAndPath(ModernGuiClient.MOD_ID, "inter"))
            .regular(Identifier.fromNamespaceAndPath(ModernGuiClient.MOD_ID, "inter_regular.ttf"))
            .bold(Identifier.fromNamespaceAndPath(ModernGuiClient.MOD_ID, "inter_semibold.ttf"))
            .baseline(FontMetrics.REFERENCE_BASELINE));

    /** Inter Display, the optical size cut for large text; here as a second bundled family. */
    public static final FontFamily INTER_DISPLAY = register(FontFamily.builder(Identifier.fromNamespaceAndPath(ModernGuiClient.MOD_ID, "inter_display"))
            .regular(Identifier.fromNamespaceAndPath(ModernGuiClient.MOD_ID, "inter_display_regular.ttf"))
            .bold(Identifier.fromNamespaceAndPath(ModernGuiClient.MOD_ID, "inter_display_bold.ttf")));

    private FontFamilies() {
    }

    /** Registers a family from two TTFs; see {@link #register(FontFamily.Builder)} for the full form. */
    public static FontFamily register(Identifier id, Identifier regularTtf, Identifier boldTtf) {
        return register(FontFamily.builder(id).regular(regularTtf).bold(boldTtf));
    }

    /**
     * Registers a family. Measures the baseline from the regular TTF unless the builder set one,
     * writes the generated fonts and returns the (possibly already registered, identical) family.
     * A failure to write them is logged and leaves the family installed-less: text drawn with it
     * falls back to the vanilla font instead of crashing.
     *
     * @throws IllegalStateException if the id is taken by a different description, or a weight
     *                               required for the family is missing
     */
    public static FontFamily register(FontFamily.Builder builder) {
        float baseline = builder.hasBaseline() ? 0.0f : FontMetrics.baselineFor(measureCapHeight(builder.regularFile()));
        FontFamily family = REGISTRY.add(builder.build(baseline));
        if (!family.installed()) {
            try {
                FontPack.install(family);
                family.markInstalled();
            } catch (IOException | RuntimeException e) {
                ModernGuiClient.LOGGER.error("Could not generate fonts for {}; it will draw in the vanilla font", family.id(), e);
            }
        }
        return family;
    }

    /** The family registered under {@code id}, or null. */
    public static @Nullable FontFamily get(Identifier id) {
        return REGISTRY.get(id);
    }

    /** Every registered family, built-in and user, in registration order. */
    public static Collection<FontFamily> all() {
        return REGISTRY.all();
    }

    /** The family one of the generated fonts belongs to (from the font id in a style), or null. */
    public static @Nullable FontFamily ofFontId(Identifier fontId) {
        return REGISTRY.ofFontId(fontId);
    }

    /** Families found in {@code config/moderngui/fonts/}, in name order. Empty until loaded. */
    public static List<FontFamily> userFonts() {
        return List.copyOf(USER);
    }

    /** Where players put TTF/OTF files to make them selectable. */
    public static Path userFontDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("moderngui").resolve("fonts");
    }

    /**
     * Registers every font in {@link #userFontDir} (once). Called by Modern GUI's initializer; a
     * consumer that offers a font picker reads {@link #userFonts()} afterwards. Unreadable or
     * corrupt files are skipped with a log line; Minecraft itself ignores a TTF it cannot parse.
     */
    public static synchronized void loadUserFonts() {
        if (userFontsLoaded) {
            return;
        }
        userFontsLoaded = true;
        Path dir = userFontDir();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            ModernGuiClient.LOGGER.warn("Could not create {}", dir, e);
            return;
        }
        List<String> names;
        try (Stream<Path> files = Files.list(dir)) {
            names = files.filter(Files::isRegularFile).map(p -> p.getFileName().toString()).toList();
        } catch (IOException e) {
            ModernGuiClient.LOGGER.warn("Could not list {}", dir, e);
            return;
        }
        for (UserFonts.Entry entry : UserFonts.group(names)) {
            try {
                FontFamily.Builder builder = FontFamily.builder(Identifier.fromNamespaceAndPath(ModernGuiClient.MOD_ID, "user/" + entry.name()));
                for (Map.Entry<FontWeight, String> file : entry.files().entrySet()) {
                    builder.weight(file.getKey(), FontPack.copyUserFont(dir.resolve(file.getValue())));
                }
                FontFamily family = registerDiskFamily(builder, dir.resolve(entry.files().get(FontWeight.REGULAR)));
                USER.add(family);
                ModernGuiClient.LOGGER.info("Registered user font {}", family.id());
            } catch (IOException | RuntimeException e) {
                ModernGuiClient.LOGGER.warn("Skipping user font {}", entry.name(), e);
            }
        }
    }

    /** Disk fonts measure from the original file (the pack copy is not readable through the classpath). */
    private static FontFamily registerDiskFamily(FontFamily.Builder builder, Path regular) {
        float cap = Float.NaN;
        try {
            cap = FontMetrics.capHeightEm(Files.readAllBytes(regular));
        } catch (IOException e) {
            // Falls back to Inter's baseline below.
        }
        FontFamily family = REGISTRY.add(builder.build(builder.hasBaseline() ? 0.0f : FontMetrics.baselineFor(cap)));
        try {
            FontPack.install(family);
            family.markInstalled();
        } catch (IOException | RuntimeException e) {
            ModernGuiClient.LOGGER.error("Could not generate fonts for {}", family.id(), e);
        }
        return family;
    }

    /**
     * Reloads resource packs so families registered after startup load. Only needed for a family
     * added at runtime; this is a full resource reload (a second or two), so do it on a user action.
     */
    public static void reloadResources() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(mc::reloadResourcePacks);
    }

    private static float measureCapHeight(Identifier ttf) {
        String path = "assets/" + ttf.getNamespace() + "/font/" + ttf.getPath();
        try (InputStream in = FontFamilies.class.getClassLoader().getResourceAsStream(path)) {
            return in == null ? Float.NaN : FontMetrics.capHeightEm(in.readAllBytes());
        } catch (IOException e) {
            return Float.NaN;
        }
    }
}
