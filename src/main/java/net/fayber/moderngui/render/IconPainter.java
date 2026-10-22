package net.fayber.moderngui.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Draws an icon into a rectangle. The shared shape of "something icon-like": a vanilla item via
 * {@link ItemIcon}, a vector glyph, a texture. Components that show icons (such as the icon
 * cycler) take these so they do not care which kind they hold. The painter owns how it fits the
 * rectangle; the usual choice is the largest centred square.
 */
@FunctionalInterface
public interface IconPainter {
    void paint(GuiGraphicsExtractor gfx, float x, float y, float width, float height);
}
