package net.fayber.moderngui.render;

import net.fayber.moderngui.ModernGuiClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Read access to the two private pieces of {@link GuiGraphicsExtractor} that a custom
 * picture-in-picture element needs: the {@link GuiRenderState} to queue the element on, and the
 * active scissor rectangle to clip it with. Vanilla only exposes entity, skin, sign, banner and
 * chart elements through typed methods, so there is no public way to queue an element of our own
 * class.
 *
 * <p>This is reflection, not a mixin, which keeps the library at zero mixins. 26.1 ships
 * unobfuscated Mojang names, so the field names are the same in dev and production. If anything
 * is missing (a future version renames them) every lookup returns null and callers fall back to
 * plain vanilla drawing instead of crashing; the failure is logged once.
 */
final class GuiInternals {
    @Nullable
    private static final Field RENDER_STATE = field("guiRenderState");
    @Nullable
    private static final Field SCISSOR_STACK = field("scissorStack");
    @Nullable
    private static final Method SCISSOR_PEEK = peekMethod();

    private GuiInternals() {
    }

    /** True when both internals resolved, i.e. custom elements can be queued. */
    static boolean available() {
        return RENDER_STATE != null && SCISSOR_STACK != null && SCISSOR_PEEK != null;
    }

    @Nullable
    static GuiRenderState renderState(GuiGraphicsExtractor gfx) {
        try {
            return RENDER_STATE == null ? null : (GuiRenderState) RENDER_STATE.get(gfx);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** The active scissor rectangle, or null when none is set. */
    @Nullable
    static ScreenRectangle scissor(GuiGraphicsExtractor gfx) {
        try {
            if (SCISSOR_STACK == null || SCISSOR_PEEK == null) {
                return null;
            }
            return (ScreenRectangle) SCISSOR_PEEK.invoke(SCISSOR_STACK.get(gfx));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static Field field(String name) {
        try {
            Field f = GuiGraphicsExtractor.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException | RuntimeException e) {
            ModernGuiClient.LOGGER.warn("GuiGraphicsExtractor.{} is not accessible; sharp item icons are "
                    + "disabled and items fall back to magnified vanilla drawing", name);
            return null;
        }
    }

    @Nullable
    private static Method peekMethod() {
        try {
            Method m = Class.forName("net.minecraft.client.gui.GuiGraphicsExtractor$ScissorStack")
                    .getDeclaredMethod("peek");
            m.setAccessible(true);
            return m;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
