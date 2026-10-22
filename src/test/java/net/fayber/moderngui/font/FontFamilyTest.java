package net.fayber.moderngui.font;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class FontFamilyTest {
    private static Identifier id(String ns, String path) {
        return Identifier.fromNamespaceAndPath(ns, path);
    }

    private static FontFamily.Builder fancy() {
        return FontFamily.builder(id("mymod", "fancy"))
                .regular(id("mymod", "fancy_regular.ttf"))
                .bold(id("mymod", "fancy_bold.ttf"));
    }

    @Test
    void regularAndBoldAreRequired() {
        assertThrows(IllegalStateException.class,
                () -> FontFamily.builder(id("mymod", "x")).regular(id("mymod", "r.ttf")).build());
        assertThrows(IllegalStateException.class,
                () -> FontFamily.builder(id("mymod", "x")).bold(id("mymod", "b.ttf")).build());
    }

    @Test
    void optionalWeightsFallBackToTheNearestRequiredOne() {
        FontFamily f = fancy().build();
        assertEquals(FontWeight.REGULAR, f.resolve(FontWeight.MEDIUM));
        assertEquals(FontWeight.BOLD, f.resolve(FontWeight.SEMIBOLD));
        assertEquals(id("mymod", "fancy_bold.ttf"), f.file(FontWeight.SEMIBOLD));
        assertEquals(List.of(FontWeight.REGULAR, FontWeight.BOLD), f.declaredWeights());

        FontFamily full = fancy().medium(id("mymod", "m.ttf")).semibold(id("mymod", "s.ttf")).build();
        assertEquals(FontWeight.MEDIUM, full.resolve(FontWeight.MEDIUM));
        assertEquals(id("mymod", "s.ttf"), full.file(FontWeight.SEMIBOLD));
        assertEquals(4, full.declaredWeights().size());
    }

    @Test
    void fontIdsAreSlugCombinedAndUseTheResolvedWeight() {
        FontFamily f = fancy().build();
        assertEquals("mymod_fancy", f.slug());
        assertEquals(id("moderngui", "mymod_fancy/regular_x3_f150"), f.fontId(FontWeight.REGULAR, 150, 3));
        // A missing semibold shares the bold fonts rather than needing its own.
        assertEquals(f.fontId(FontWeight.BOLD, 100, 2), f.fontId(FontWeight.SEMIBOLD, 100, 2));
        assertEquals("mymod_user_my_font", FontFamily.slugOf(id("mymod", "user/my_font")));
    }

    @Test
    void sizesAlwaysIncludeNormalAndAreSortedAndDeduplicated() {
        assertArrayEquals(new int[] {75, 100, 125, 150, 200}, fancy().build().percents());
        assertArrayEquals(new int[] {100, 300}, fancy().sizes(300).build().percents());
        assertArrayEquals(new int[] {50, 100, 200}, fancy().sizes(200, 50, 200, -5, 0).build().percents());
        assertEquals(300, fancy().sizes(100, 300).build().snapPercent(2.4f));
    }

    @Test
    void maxScaleClampsAndSelectsScaleVariants() {
        FontFamily f = fancy().maxScale(4).build();
        assertEquals(4, f.maxScale());
        assertEquals(4, f.scaleIndex(7.0f));
        assertEquals(1, f.scaleIndex(0.4f));
        assertEquals(16, fancy().maxScale(99).build().maxScale());
        assertEquals(1, fancy().maxScale(0).build().maxScale());
    }

    @Test
    void definitionIsExactSizeAndOversampleWithVanillaFallbacks() {
        FontFamily f = fancy().baseline(7.0f).build();
        JsonObject root = JsonParser.parseString(f.definition(FontWeight.BOLD, 125, 3)).getAsJsonObject();
        JsonArray providers = root.getAsJsonArray("providers");
        assertEquals(4, providers.size());

        JsonObject ttf = providers.get(0).getAsJsonObject();
        assertEquals("ttf", ttf.get("type").getAsString());
        assertEquals("mymod:fancy_bold.ttf", ttf.get("file").getAsString());
        // size = 9 * factor, oversample = GUI scale: the whole point of the generated fonts.
        assertEquals(11.25f, ttf.get("size").getAsFloat(), 1e-6f);
        assertEquals(3.0f, ttf.get("oversample").getAsFloat(), 1e-6f);
        assertEquals(0.0f, ttf.getAsJsonArray("shift").get(0).getAsFloat(), 1e-6f);

        // Unknown glyphs fall back to vanilla exactly like the old hand-written fonts.
        assertEquals("minecraft:include/space", providers.get(1).getAsJsonObject().get("id").getAsString());
        assertEquals("minecraft:include/default", providers.get(2).getAsJsonObject().get("id").getAsString());
        assertEquals("minecraft:include/unifont", providers.get(3).getAsJsonObject().get("id").getAsString());
    }

    @Test
    void definitionShiftCarriesTheFamilyBaselineAndSize() {
        // Reference baseline at normal size: no shift, identical to the old Inter fonts.
        FontFamily inter = fancy().baseline(7.0f).build();
        JsonObject normal = JsonParser.parseString(inter.definition(FontWeight.REGULAR, 100, 2)).getAsJsonObject();
        assertEquals(0.0f, normal.getAsJsonArray("providers").get(0).getAsJsonObject()
                .getAsJsonArray("shift").get(1).getAsFloat(), 1e-6f);

        // 200% moves the baseline from 7 to 14.
        JsonObject big = JsonParser.parseString(inter.definition(FontWeight.REGULAR, 200, 2)).getAsJsonObject();
        assertEquals(7.0f, big.getAsJsonArray("providers").get(0).getAsJsonObject()
                .getAsJsonArray("shift").get(1).getAsFloat(), 1e-6f);

        // A face that sits half a pixel low gets that pulled back at scale 2.
        FontFamily low = fancy().baseline(7.5f).build();
        JsonObject lowDef = JsonParser.parseString(low.definition(FontWeight.REGULAR, 100, 2)).getAsJsonObject();
        assertEquals(0.5f, lowDef.getAsJsonArray("providers").get(0).getAsJsonObject()
                .getAsJsonArray("shift").get(1).getAsFloat(), 1e-6f);
    }

    @Test
    void definitionsCoverEveryWeightSizeAndScale() {
        FontFamily f = fancy().build();
        Map<String, String> defs = f.definitions();
        // 2 weights x 5 sizes x 6 scales: the 60 files the old hand-written set held for Inter.
        assertEquals(60, defs.size());
        assertTrue(defs.containsKey("mymod_fancy/regular_x1_f75.json"));
        assertTrue(defs.containsKey("mymod_fancy/bold_x6_f200.json"));
        assertFalse(defs.containsKey("mymod_fancy/semibold_x1_f100.json"));

        FontFamily trimmed = fancy().sizes(100).maxScale(3).medium(id("mymod", "m.ttf")).build();
        assertEquals(3 * 1 * 3, trimmed.definitions().size());
    }

    @Test
    void uninstalledFamiliesDrawVanillaText() {
        FontFamily f = fancy().build();
        assertFalse(f.installed());
        assertEquals(net.minecraft.network.chat.Style.EMPTY, f.style(FontWeight.REGULAR, 1.0f, 2.0f));
    }

    @Test
    void sameDescriptionIsSameAndDifferentIsNot() {
        assertTrue(fancy().build().sameAs(fancy().build()));
        assertFalse(fancy().build().sameAs(fancy().sizes(100).build()));
        assertFalse(fancy().build().sameAs(fancy().baseline(6.0f).build()));
        assertFalse(fancy().build().sameAs(
                FontFamily.builder(id("mymod", "fancy")).regular(id("mymod", "fancy_regular.ttf"))
                        .bold(id("mymod", "other_bold.ttf")).build()));
    }
}
