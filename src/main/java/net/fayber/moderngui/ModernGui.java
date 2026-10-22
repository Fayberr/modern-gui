package net.fayber.moderngui;

import net.fayber.moderngui.font.FontFamilies;
import net.fayber.moderngui.font.FontFamily;

import java.util.Objects;

/**
 * Global switches of the toolkit. Currently the default font: the family every {@code Ui.ui},
 * {@code Ui.uiBold} and {@code SizedText} call without an explicit family draws with.
 */
public final class ModernGui {
    private static volatile FontFamily defaultFont = FontFamilies.INTER;

    private ModernGui() {
    }

    /** The family used when a call does not name one. Inter unless a consumer changed it. */
    public static FontFamily defaultFont() {
        return defaultFont;
    }

    /**
     * Makes {@code family} the default for all text drawn afterwards. Components already built
     * keep the font they were built with ({@code Ui.ui(...)} bakes it in), so screens should be
     * rebuilt (for example {@code rebuildWidgets()}) after switching.
     */
    public static void setDefaultFont(FontFamily family) {
        defaultFont = Objects.requireNonNull(family, "family");
    }
}
