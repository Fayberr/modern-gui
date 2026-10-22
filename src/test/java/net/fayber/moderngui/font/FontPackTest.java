package net.fayber.moderngui.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FontPackTest {
    private static FontFamily fancy() {
        return FontFamily.builder(Identifier.fromNamespaceAndPath("mymod", "fancy"))
                .regular(Identifier.fromNamespaceAndPath("mymod", "fancy_regular.ttf"))
                .bold(Identifier.fromNamespaceAndPath("mymod", "fancy_bold.ttf"))
                .sizes(100, 150).maxScale(2).build();
    }

    @Test
    void writesAPackWithMcmetaAndEveryDefinition(@TempDir Path dir) throws IOException {
        FontPack.write(dir, fancy());

        Path mcmeta = dir.resolve("pack.mcmeta");
        assertTrue(Files.exists(mcmeta));
        assertTrue(JsonParser.parseString(Files.readString(mcmeta)).getAsJsonObject().has("pack"));

        Path fonts = dir.resolve("assets/moderngui/font/mymod_fancy");
        try (Stream<Path> files = Files.list(fonts)) {
            // 2 weights x 2 sizes x 2 scales.
            assertEquals(8, files.count());
        }
        String json = Files.readString(fonts.resolve("bold_x2_f150.json"), StandardCharsets.UTF_8);
        var ttf = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("providers").get(0).getAsJsonObject();
        assertEquals("mymod:fancy_bold.ttf", ttf.get("file").getAsString());
        assertEquals(13.5f, ttf.get("size").getAsFloat(), 1e-6f);
        assertEquals(2.0f, ttf.get("oversample").getAsFloat(), 1e-6f);
    }

    @Test
    void writingASecondFamilyKeepsTheFirstAndTheMcmeta(@TempDir Path dir) throws IOException {
        FontPack.write(dir, fancy());
        FontFamily other = FontFamily.builder(Identifier.fromNamespaceAndPath("other", "plain"))
                .regular(Identifier.fromNamespaceAndPath("other", "r.ttf"))
                .bold(Identifier.fromNamespaceAndPath("other", "b.ttf"))
                .sizes(100).maxScale(1).build();
        FontPack.write(dir, other);
        assertTrue(Files.exists(dir.resolve("assets/moderngui/font/mymod_fancy/regular_x1_f100.json")));
        assertTrue(Files.exists(dir.resolve("assets/moderngui/font/other_plain/bold_x1_f100.json")));
    }

    @Test
    void generatedFontPathsMatchTheIdsTheStylesPointAt(@TempDir Path dir) throws IOException {
        FontFamily f = fancy();
        FontPack.write(dir, f);
        Identifier id = f.fontId(FontWeight.REGULAR, 150, 2);
        // Minecraft resolves moderngui:<path> to assets/moderngui/font/<path>.json.
        assertTrue(Files.exists(dir.resolve("assets").resolve(id.getNamespace()).resolve("font")
                .resolve(id.getPath() + ".json")));
    }
}
