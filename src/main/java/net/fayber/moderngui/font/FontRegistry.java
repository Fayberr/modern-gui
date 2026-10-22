package net.fayber.moderngui.font;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The bookkeeping half of {@link FontFamilies}: which families exist, by id, in registration order.
 * Separate from it because it touches nothing but the families themselves, so it runs under a
 * plain unit test (the facade installs resource packs, which needs a running game).
 */
final class FontRegistry {
    private final Map<Identifier, FontFamily> families = new LinkedHashMap<>();

    /**
     * Adds a family. Registering the same description again returns the existing instance (a mod
     * initialised twice is harmless); a different description under a taken id is a mistake.
     *
     * @return the family that is now registered under {@code family.id()}
     * @throws IllegalStateException if the id is taken by a different description
     */
    synchronized FontFamily add(FontFamily family) {
        FontFamily existing = this.families.get(family.id());
        if (existing != null) {
            if (!existing.sameAs(family)) {
                throw new IllegalStateException("A different font family is already registered as " + family.id());
            }
            return existing;
        }
        this.families.put(family.id(), family);
        return family;
    }

    synchronized @Nullable FontFamily get(Identifier id) {
        return this.families.get(id);
    }

    /** All families in registration order. */
    synchronized Collection<FontFamily> all() {
        return List.copyOf(new ArrayList<>(this.families.values()));
    }

    /** The family one of its generated fonts belongs to, from the font id, or null. */
    synchronized @Nullable FontFamily ofFontId(Identifier fontId) {
        String path = fontId.getPath();
        int slash = path.indexOf('/');
        if (slash < 0) {
            return null;
        }
        String slug = path.substring(0, slash);
        for (FontFamily family : this.families.values()) {
            if (family.slug().equals(slug)) {
                return family;
            }
        }
        return null;
    }
}
