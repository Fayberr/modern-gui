package net.fayber.moderngui.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class UserFontsTest {
    @Test
    void groupsWeightFilesIntoOneFamily() {
        List<UserFonts.Entry> out = UserFonts.group(List.of(
                "Manrope-Regular.ttf", "Manrope-Bold.ttf", "Manrope-Medium.ttf", "Manrope-SemiBold.ttf"));
        assertEquals(1, out.size());
        UserFonts.Entry e = out.get(0);
        assertEquals("manrope", e.name());
        assertEquals("Manrope-Regular.ttf", e.files().get(FontWeight.REGULAR));
        assertEquals("Manrope-Bold.ttf", e.files().get(FontWeight.BOLD));
        assertEquals("Manrope-Medium.ttf", e.files().get(FontWeight.MEDIUM));
        assertEquals("Manrope-SemiBold.ttf", e.files().get(FontWeight.SEMIBOLD));
    }

    @Test
    void aSingleFileIsRegularAndBoldFallsBackToIt() {
        List<UserFonts.Entry> out = UserFonts.group(List.of("Fancy Script.otf"));
        assertEquals(1, out.size());
        assertEquals("fancy_script", out.get(0).name());
        assertEquals("Fancy Script.otf", out.get(0).files().get(FontWeight.REGULAR));
        assertEquals("Fancy Script.otf", out.get(0).files().get(FontWeight.BOLD));
    }

    @Test
    void ignoresNonFontsAndFamiliesWithoutARegularFace() {
        assertTrue(UserFonts.group(List.of("readme.txt", "LICENSE", "Only-Bold.ttf")).isEmpty());
    }

    @Test
    void separatesDifferentFamiliesAndAcceptsUnderscoreSuffixes() {
        List<UserFonts.Entry> out = UserFonts.group(List.of("Alpha_Regular.ttf", "Alpha_Bold.ttf", "Beta.ttf"));
        assertEquals(List.of("alpha", "beta"), out.stream().map(UserFonts.Entry::name).toList());
    }

    @Test
    void slugsAreResourceIdSafe() {
        assertEquals("my_font_v2.1", UserFonts.slug("My Font V2.1"));
        assertEquals("a-b_c", UserFonts.slug("a-b_c"));
    }
}
