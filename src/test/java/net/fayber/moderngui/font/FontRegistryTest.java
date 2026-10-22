package net.fayber.moderngui.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class FontRegistryTest {
    private static FontFamily family(String path, String boldFile) {
        return FontFamily.builder(Identifier.fromNamespaceAndPath("mymod", path))
                .regular(Identifier.fromNamespaceAndPath("mymod", path + "_r.ttf"))
                .bold(Identifier.fromNamespaceAndPath("mymod", boldFile))
                .build();
    }

    @Test
    void keepsRegistrationOrderAndLooksUpById() {
        FontRegistry registry = new FontRegistry();
        FontFamily a = registry.add(family("a", "a_b.ttf"));
        FontFamily b = registry.add(family("b", "b_b.ttf"));
        assertEquals(List.of(a, b), List.copyOf(registry.all()));
        assertSame(b, registry.get(Identifier.fromNamespaceAndPath("mymod", "b")));
        assertNull(registry.get(Identifier.fromNamespaceAndPath("mymod", "nope")));
    }

    @Test
    void registeringTheSameDescriptionAgainReturnsTheFirstInstance() {
        FontRegistry registry = new FontRegistry();
        FontFamily first = registry.add(family("a", "a_b.ttf"));
        assertSame(first, registry.add(family("a", "a_b.ttf")));
        assertEquals(1, registry.all().size());
    }

    @Test
    void aDifferentDescriptionUnderATakenIdIsRejected() {
        FontRegistry registry = new FontRegistry();
        registry.add(family("a", "a_b.ttf"));
        assertThrows(IllegalStateException.class, () -> registry.add(family("a", "other.ttf")));
    }

    @Test
    void mapsAGeneratedFontIdBackToItsFamily() {
        FontRegistry registry = new FontRegistry();
        FontFamily a = registry.add(family("a", "a_b.ttf"));
        FontFamily b = registry.add(family("b", "b_b.ttf"));
        assertSame(b, registry.ofFontId(b.fontId(FontWeight.BOLD, 100, 2)));
        assertSame(a, registry.ofFontId(a.fontId(FontWeight.REGULAR, 150, 1)));
        assertNull(registry.ofFontId(Identifier.fromNamespaceAndPath("minecraft", "default")));
    }
}
