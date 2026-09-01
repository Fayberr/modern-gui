package net.fayber.moderngui.screen;

import net.fayber.moderngui.render.Theme;
import net.fayber.moderngui.render.Ui;
import net.fayber.moderngui.widget.FlatButton;
import net.fayber.moderngui.widget.TextField;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntConsumer;

/**
 * An interactive HSV colour picker as a modal layer: a saturation/value square over a hue strip
 * with an optional alpha bar, a live preview swatch and a hex field, plus Cancel/OK. Dragging in
 * the square or on the bars fires {@code onChange} continuously (live write-through, like every
 * other widget in the toolkit); ESC or Cancel runs {@code onCancel} so the opener can restore the
 * colour the row had when the picker opened, and OK (or ENTER) keeps the current value.
 *
 * <p>Hosted through {@link PopupHost#showModal(ModalLayer)}, so the widgets underneath are
 * frozen while it is open. The screen forwards typed characters via {@code charTyped}.
 */
public class ColorPickerModal implements ModalLayer {
    private static final int PAD = 16;
    private static final float RADIUS = 10.0f;
    private static final int BUTTON_H = 24;
    private static final int BUTTON_GAP = 8;
    private static final int SV_MAX_HEIGHT = 120;
    private static final int SV_MIN_HEIGHT = 56;
    private static final int BAR_HEIGHT = 10;
    private static final int SECTION_GAP = 8;
    private static final int FIELD_HEIGHT = 20;
    private static final int FIELD_WIDTH = 92;
    private static final int SWATCH_HEIGHT = 20;
    /** Hex text shape accepted by the field: an optional hash, then up to 8 hex digits. */
    private static final java.util.function.Predicate<String> HEX_TEXT = s -> s.matches("#?[0-9a-fA-F]{0,8}");

    private final Component title;
    private final IntConsumer onChange;
    private final Runnable onCancel;
    private final TextField hexField;
    private final FlatButton cancelButton;
    private final FlatButton okButton;

    private float hue;
    private float sat = 1.0f;
    private float val = 1.0f;
    private int alpha = 0xFF;
    /** Which surface the held mouse button is editing; NONE when the button is up. */
    private DragTarget dragTarget = DragTarget.NONE;
    /** Set by the buttons' onPress; consumed by the click handler that owns the host. */
    private boolean cancelClicked;
    private boolean okClicked;
    /** Card geometry, computed once per extract (cheap, and it tracks window resizes). */
    private int x;
    private int y;
    private int width = 232;
    private int svHeight = SV_MAX_HEIGHT;

    private enum DragTarget {
        NONE,
        SATURATION,
        HUE,
        ALPHA
    }

    /**
     * @param initialArgb the colour to start from (the caller's current value)
     * @param onChange    fired on every change while the picker is open (live write-through)
     * @param onCancel    fired when the picker is dismissed without confirming; the opener
     *                    restores the colour it captured at open time
     */
    public ColorPickerModal(Component title, int initialArgb, IntConsumer onChange, Runnable onCancel) {
        this.title = Ui.uiBold(title);
        this.onChange = onChange;
        this.onCancel = onCancel;
        this.setArgb(initialArgb);
        this.hexField = new TextField(0, 0, FIELD_WIDTH, FIELD_HEIGHT)
                .maxLength(10)
                .validator(HEX_TEXT)
                .onChanged(this::onHexEdited);
        this.cancelButton = new FlatButton(0, 0, 72, BUTTON_H, Component.literal("Cancel"),
                () -> this.cancelClicked = true, FlatButton.Style.GHOST);
        this.okButton = new FlatButton(0, 0, 56, BUTTON_H, Component.literal("OK"),
                () -> this.okClicked = true, FlatButton.Style.PRIMARY);
    }

    /** Loads the picker state from an ARGB colour (no callback; this is not a user edit). */
    private void setArgb(int argb) {
        float[] hsv = rgbToHsv((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF);
        this.hue = hsv[0];
        this.sat = hsv[1];
        this.val = hsv[2];
        this.alpha = (argb >>> 24) & 0xFF;
        this.hexField.value(hexOf(argb));
    }

    private static String hexOf(int argb) {
        return (argb >>> 24) == 0xFF
                ? String.format("#%06X", argb & 0xFFFFFF)
                : String.format("#%08X", argb);
    }

    /** The colour the picker currently points at. */
    private int currentArgb() {
        return (this.alpha << 24) | hsvToRgb(this.hue, this.sat, this.val);
    }

    private void onHexEdited(String text) {
        String hex = text.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 6 && hex.length() != 8) {
            return;
        }
        int parsed;
        try {
            parsed = (int) Long.parseLong(hex, 16);
        } catch (NumberFormatException e) {
            return;
        }
        int argb = hex.length() == 6 ? parsed | 0xFF000000 : parsed;
        int rgb = argb & 0xFFFFFF;
        if (rgb == (this.currentArgb() & 0xFFFFFF) && (argb >>> 24) == this.alpha) {
            return;
        }
        this.setArgb(argb);
        this.fireChange();
    }

    private void fireChange() {
        if (this.onChange != null) {
            this.onChange.accept(this.currentArgb());
        }
    }

    private void cancel() {
        if (this.onCancel != null) {
            this.onCancel.run();
        }
    }

    private void layout(Theme theme) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        this.width = Math.min(232, screenW - 16);
        // Everything except the square is height-fixed; shrink the square first on short screens.
        int fixed = 2 * PAD + Ui.font().lineHeight + 10 + SECTION_GAP + 2 * (BAR_HEIGHT + SECTION_GAP)
                + SWATCH_HEIGHT + SECTION_GAP + BUTTON_H + PAD;
        this.svHeight = Math.clamp(screenH - fixed, SV_MIN_HEIGHT, SV_MAX_HEIGHT);
        int height = fixed - SWATCH_HEIGHT - SECTION_GAP - BUTTON_H - PAD + this.svHeight
                + SWATCH_HEIGHT + SECTION_GAP + BUTTON_H + PAD;
        this.x = (screenW - this.width) / 2;
        this.y = (screenH - height) / 2;
    }

    private int svX() {
        return this.x + PAD;
    }

    private int svY() {
        return this.y + PAD + Ui.font().lineHeight + 10;
    }

    private int svWidth() {
        return this.width - 2 * PAD;
    }

    private int barY(int index) {
        return this.svY() + this.svHeight + SECTION_GAP + index * (BAR_HEIGHT + SECTION_GAP);
    }

    private int previewY() {
        return this.barY(1) + BAR_HEIGHT + SECTION_GAP;
    }

    private int buttonY() {
        return this.previewY() + SWATCH_HEIGHT + SECTION_GAP;
    }

    private int height() {
        return this.buttonY() + BUTTON_H + PAD - this.y;
    }

    @Override
    public boolean handleClick(PopupHost host, MouseButtonEvent event, boolean doubleClick) {
        this.layout(host.theme);
        double mx = event.x();
        double my = event.y();
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (this.inRect(mx, my, this.svX(), this.svY(), this.svWidth(), this.svHeight)) {
                this.dragTarget = DragTarget.SATURATION;
                this.updateFromPointer(mx, my);
                this.hexField.setFocused(false);
                return true;
            }
            for (int i = 0; i < 2; i++) {
                if (this.inRect(mx, my, this.svX(), this.barY(i), this.svWidth(), BAR_HEIGHT)) {
                    this.dragTarget = i == 0 ? DragTarget.HUE : DragTarget.ALPHA;
                    this.updateFromPointer(mx, my);
                    this.hexField.setFocused(false);
                    return true;
                }
            }
            if (this.hexField.isMouseOver(mx, my)) {
                this.hexField.mouseClicked(event, doubleClick);
                return true;
            }
        }
        if (this.cancelButton.mouseClicked(event, doubleClick)) {
            if (this.cancelClicked) {
                this.cancelClicked = false;
                this.cancel();
                host.closeModal();
            }
            return true;
        }
        if (this.okButton.mouseClicked(event, doubleClick)) {
            if (this.okClicked) {
                this.okClicked = false;
                host.closeModal();
            }
            return true;
        }
        // Click on the scrim or card body: swallowed, nothing outside is reachable.
        this.hexField.setFocused(false);
        return true;
    }

    @Override
    public boolean handleDrag(PopupHost host, MouseButtonEvent event, double deltaX, double deltaY) {
        if (this.dragTarget == DragTarget.NONE) {
            return false;
        }
        this.updateFromPointer(event.x(), event.y());
        return true;
    }

    @Override
    public boolean handleRelease(PopupHost host, MouseButtonEvent event) {
        this.dragTarget = DragTarget.NONE;
        return true;
    }

    /** Applies the pointer position to the armed surface; drags clamp, so they work off-surface. */
    private void updateFromPointer(double mx, double my) {
        double wx = this.svWidth();
        switch (this.dragTarget) {
            case SATURATION -> {
                this.sat = (float) Math.clamp((mx - this.svX()) / wx, 0.0, 1.0);
                this.val = 1.0f - (float) Math.clamp((my - this.svY()) / this.svHeight, 0.0, 1.0);
                this.syncHexField();
                this.fireChange();
            }
            case HUE -> {
                this.hue = (float) Math.clamp((mx - this.svX()) / wx, 0.0, 0.9999);
                this.syncHexField();
                this.fireChange();
            }
            case ALPHA -> {
                this.alpha = Math.round(255.0f * (float) Math.clamp((mx - this.svX()) / wx, 0.0, 1.0));
                this.syncHexField();
                this.fireChange();
            }
            case NONE -> {
            }
        }
    }

    /** Reflects a slider-driven change in the hex field; the equal-colour guard stops the loop. */
    private void syncHexField() {
        String hex = hexOf(this.currentArgb());
        if (!hex.equalsIgnoreCase(this.hexField.getValue())) {
            this.hexField.value(hex);
        }
    }

    @Override
    public boolean handleKey(PopupHost host, KeyEvent event) {
        if (event.isEscape()) {
            this.cancel();
            host.closeModal();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            host.closeModal();
            return true;
        }
        this.hexField.keyPressed(event);
        return true;
    }

    @Override
    public boolean handleChar(PopupHost host, CharacterEvent event) {
        this.hexField.charTyped(event);
        return true;
    }

    @Override
    public void extract(GuiGraphicsExtractor gfx, Theme theme, int mouseX, int mouseY, float partialTick) {
        this.layout(theme);
        var mc = net.minecraft.client.Minecraft.getInstance();
        Ui.rect(gfx, 0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), 0x88000000);
        Ui.shadow(gfx, this.x, this.y, this.width, this.height(), RADIUS, 8.0f, 5);
        Ui.roundRect(gfx, this.x, this.y, this.width, this.height(), RADIUS, theme.card);

        Ui.text(gfx, this.title, this.x + PAD, this.y + PAD, theme.text);

        this.extractSaturationSquare(gfx, theme);
        this.extractHueBar(gfx, theme);
        this.extractAlphaBar(gfx);
        this.extractPreviewRow(gfx, theme, mouseX, mouseY, partialTick);

        int by = this.buttonY();
        this.cancelButton.setPosition(this.x + this.width - PAD - 72 - BUTTON_GAP - 56, by);
        this.okButton.setPosition(this.x + this.width - PAD - 56, by);
        this.cancelButton.extractRenderState(gfx, mouseX, mouseY, partialTick);
        this.okButton.extractRenderState(gfx, mouseX, mouseY, partialTick);
    }

    /** The saturation/value surface, drawn as per-column hue washes under a black value veil. */
    private void extractSaturationSquare(GuiGraphicsExtractor gfx, Theme theme) {
        int sx = this.svX();
        int sy = this.svY();
        int w = this.svWidth();
        int h = this.svHeight;
        for (int px = 0; px < w; px++) {
            int rgb = hsvToRgb(this.hue, px / (float) (w - 1), 1.0f);
            Ui.rect(gfx, sx + px, sy, 1, h, 0xFF000000 | rgb);
        }
        for (int py = 0; py < h; py++) {
            int veil = Math.round(255.0f * (py / (float) (h - 1)));
            Ui.rect(gfx, sx, sy + py, w, 1, veil << 24);
        }
        Ui.roundRectBorder(gfx, sx, sy, w, h, 4.0f, 0, theme.cardBorder, 1.0f);
        // Ring cursor at the current saturation/value.
        float cx = sx + this.sat * (w - 1);
        float cy = sy + (1.0f - this.val) * (h - 1);
        Ui.circle(gfx, cx, cy, 5.0f, 0xFFFFFFFF);
        Ui.circle(gfx, cx, cy, 3.5f, 0xFF000000 | hsvToRgb(this.hue, this.sat, this.val));
    }

    /** The hue strip: 72 full-saturation bands and a round knob at the current hue. */
    private void extractHueBar(GuiGraphicsExtractor gfx, Theme theme) {
        int bx = this.svX();
        int by = this.barY(0);
        int w = this.svWidth();
        int bands = 72;
        float bandW = w / (float) bands;
        for (int i = 0; i < bands; i++) {
            Ui.rect(gfx, bx + i * bandW, by, bandW + 0.5f, BAR_HEIGHT, 0xFF000000 | hsvToRgb(i / (float) bands, 1.0f, 1.0f));
        }
        Ui.circle(gfx, bx + this.hue * (w - 1), by + BAR_HEIGHT / 2.0f, BAR_HEIGHT / 2.0f + 1.5f, 0xFFFFFFFF);
        Ui.circle(gfx, bx + this.hue * (w - 1), by + BAR_HEIGHT / 2.0f, BAR_HEIGHT / 2.0f, 0xFF000000 | hsvToRgb(this.hue, 1.0f, 1.0f));
    }

    /** The alpha strip over a checkerboard, so translucency reads as such. */
    private void extractAlphaBar(GuiGraphicsExtractor gfx) {
        int bx = this.svX();
        int by = this.barY(1);
        int w = this.svWidth();
        int cell = 5;
        for (int cy = 0; cy < BAR_HEIGHT; cy += cell) {
            for (int cx = 0; cx < w; cx += cell) {
                boolean even = ((cx / cell) + (cy / cell)) % 2 == 0;
                Ui.rect(gfx, bx + cx, by + cy, Math.min(cell, w - cx), Math.min(cell, BAR_HEIGHT - cy),
                        even ? 0xFF4A4A4A : 0xFF606060);
            }
        }
        Ui.rect(gfx, bx, by, w * (this.alpha / 255.0f), BAR_HEIGHT, (this.alpha << 24) | hsvToRgb(this.hue, this.sat, this.val));
        Ui.circle(gfx, bx + w * (this.alpha / 255.0f), by + BAR_HEIGHT / 2.0f, BAR_HEIGHT / 2.0f + 1.5f, 0xFFFFFFFF);
        Ui.circle(gfx, bx + w * (this.alpha / 255.0f), by + BAR_HEIGHT / 2.0f, BAR_HEIGHT / 2.0f,
                (this.alpha << 24) | hsvToRgb(this.hue, this.sat, this.val));
    }

    private void extractPreviewRow(GuiGraphicsExtractor gfx, Theme theme, int mouseX, int mouseY, float partialTick) {
        int py = this.previewY();
        Ui.roundRectBorder(gfx, this.svX(), py, 48, SWATCH_HEIGHT, 4.0f, this.currentArgb(),
                this.hexField.isHoveredOrFocused() ? theme.cardBorderHover : theme.cardBorder, 1.0f);
        Ui.text(gfx, Ui.ui("Hex"), this.svX() + 48 + 10, py + (SWATCH_HEIGHT - Ui.font().lineHeight) / 2 + 1,
                theme.textSecondary);
        this.hexField.setPosition(this.svX() + 48 + 10 + Ui.font().width(Ui.ui("Hex")) + 8,
                py + (SWATCH_HEIGHT - FIELD_HEIGHT) / 2);
        this.hexField.extractRenderState(gfx, mouseX, mouseY, partialTick);
    }

    private boolean inRect(double mx, double my, int rx, int ry, int rw, int rh) {
        return mx >= rx && mx < rx + rw && my >= ry && my < ry + rh;
    }

    /**
     * RGB (0..255 per channel) to HSV; hue in turns, so it always maps back through
     * {@link #hsvToRgb} without a normalisation step.
     */
    private static float[] rgbToHsv(int r, int g, int b) {
        float rf = r / 255.0f;
        float gf = g / 255.0f;
        float bf = b / 255.0f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;
        float h = 0.0f;
        if (delta > 0.0f) {
            if (max == rf) {
                h = ((gf - bf) / delta) % 6.0f;
            } else if (max == gf) {
                h = (bf - rf) / delta + 2.0f;
            } else {
                h = (rf - gf) / delta + 4.0f;
            }
            h /= 6.0f;
            if (h < 0.0f) {
                h += 1.0f;
            }
        }
        float s = max <= 0.0f ? 0.0f : delta / max;
        return new float[] {h, s, max};
    }

    /** HSV (hue in turns) to a packed 0xRRGGBB. */
    private static int hsvToRgb(float h, float s, float v) {
        float f = h * 6.0f;
        int i = (int) f;
        float p = v * (1.0f - s);
        float q = v * (1.0f - s * (f - i));
        float t = v * (1.0f - s * (1.0f - (f - i)));
        float r;
        float g;
        float b;
        switch (Math.floorMod(i, 6)) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return (Math.round(r * 255.0f) << 16) | (Math.round(g * 255.0f) << 8) | Math.round(b * 255.0f);
    }
}
