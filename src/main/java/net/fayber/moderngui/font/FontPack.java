package net.fayber.moderngui.font;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.fayber.moderngui.ModernGuiClient;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Gets the generated font jsons in front of Minecraft's resource loader without a mixin.
 *
 * <p>Vanilla only reads fonts from resource packs. Fabric API can register a built-in pack, but
 * only as a directory inside a {@link ModContainer}'s root paths, and has no in-memory pack. So
 * the jsons are written to a directory under the game folder at startup (rewritten every launch
 * from the registered families, nothing in it is user data) and a built-in pack is registered whose
 * container is a thin wrapper around Modern GUI's own: identical metadata, but its root is that
 * directory. Fabric then opens it like any mod pack, always enabled, and vanilla's font manager
 * loads the fonts with the rest of the resources. Families registered during client init (which is
 * before the resource packs are discovered) need nothing more; later ones are picked up by the
 * next resource reload.
 */
public final class FontPack {
    /** The built-in pack's id; its files live in {@code resourcepacks/generated_fonts} of the root. */
    static final Identifier PACK_ID = Identifier.fromNamespaceAndPath(ModernGuiClient.MOD_ID, "generated_fonts");

    private static final String PACK_MCMETA = """
            {
              "pack": {
                "description": "Modern GUI generated fonts",
                "min_format": 65,
                "max_format": 999
              }
            }
            """;

    private static boolean prepared;
    private static boolean registered;

    private FontPack() {
    }

    /** The directory the generated pack lives in (a mod root for the wrapper container). */
    public static Path root() {
        return FabricLoader.getInstance().getGameDir().resolve("moderngui").resolve("generated");
    }

    /** Directory of the pack itself under {@link #root()}. */
    static Path packDir(Path root) {
        return root.resolve("resourcepacks").resolve(PACK_ID.getPath());
    }

    /** Where font jsons and copied TTFs go inside a pack directory. */
    static Path fontDir(Path packDir) {
        return packDir.resolve("assets").resolve(ModernGuiClient.MOD_ID).resolve("font");
    }

    /**
     * Writes every definition of {@code family} into {@code packDir}, plus {@code pack.mcmeta}.
     * Plain file IO against whatever directory it is given, which is what the unit tests use.
     */
    static void write(Path packDir, FontFamily family) throws IOException {
        Files.createDirectories(packDir);
        Path mcmeta = packDir.resolve("pack.mcmeta");
        if (!Files.exists(mcmeta)) {
            Files.writeString(mcmeta, PACK_MCMETA, StandardCharsets.UTF_8);
        }
        Path fonts = fontDir(packDir);
        for (Map.Entry<String, String> def : family.definitions().entrySet()) {
            Path target = fonts.resolve(def.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, def.getValue(), StandardCharsets.UTF_8);
        }
    }

    /** Deletes a previous launch's output so removed families do not linger. Only once per run. */
    private static synchronized void prepare() throws IOException {
        if (prepared) {
            return;
        }
        prepared = true;
        Path root = root();
        if (Files.exists(root)) {
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(p);
                }
            }
        }
        Files.createDirectories(packDir(root));
    }

    /**
     * Copies a TTF from disk into the pack so a font json can reference it, returning the resource
     * id to use in {@link FontFamily.Builder}.
     */
    static synchronized Identifier copyUserFont(Path source) throws IOException {
        prepare();
        String name = UserFonts.slug(source.getFileName().toString());
        Path target = fontDir(packDir(root())).resolve("user").resolve(name);
        Files.createDirectories(target.getParent());
        Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return Identifier.fromNamespaceAndPath(ModernGuiClient.MOD_ID, "user/" + name);
    }

    /** Writes the family's fonts and, the first time, registers the pack with Fabric. */
    static synchronized void install(FontFamily family) throws IOException {
        prepare();
        write(packDir(root()), family);
        if (!registered) {
            ModContainer self = FabricLoader.getInstance().getModContainer(ModernGuiClient.MOD_ID).orElseThrow();
            boolean ok = ResourceLoader.registerBuiltinPack(PACK_ID, new GeneratedRoot(self, root()),
                    Component.literal("Modern GUI generated fonts"), PackActivationType.ALWAYS_ENABLED);
            if (!ok) {
                throw new IOException("Fabric did not accept the generated font pack at " + packDir(root()));
            }
            registered = true;
        }
    }

    /**
     * Modern GUI's own container with a different root: everything (id, version, origin) is the
     * real mod's, so the pack shows up as part of Modern GUI, but its files come from the
     * generated directory.
     */
    private record GeneratedRoot(ModContainer real, Path root) implements ModContainer {
        @Override
        public ModMetadata getMetadata() {
            return this.real.getMetadata();
        }

        @Override
        public List<Path> getRootPaths() {
            return List.of(this.root);
        }

        @Override
        public Optional<Path> findPath(String file) {
            Path path = this.root.resolve(file);
            return Files.exists(path) ? Optional.of(path) : Optional.empty();
        }

        @Override
        public ModOrigin getOrigin() {
            return this.real.getOrigin();
        }

        @Override
        public Optional<ModContainer> getContainingMod() {
            return this.real.getContainingMod();
        }

        @Override
        public Collection<ModContainer> getContainedMods() {
            return List.of();
        }

        @Override
        public Path getRootPath() {
            return this.root;
        }

        @Override
        public Path getPath(String file) {
            return this.root.resolve(file);
        }
    }
}
