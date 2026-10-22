package net.fayber.moderngui.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fayber.moderngui.ModernGuiClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Util;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;

/**
 * The picture-in-picture renderer behind {@link ItemIcon}: draws an item model into its own
 * texture sized to the icon's physical pixels and blits that, so a block item is real 3D geometry
 * rasterised at the size it is shown at.
 *
 * <p>Why not the vanilla item path: since 26.1 {@code GuiGraphicsExtractor.item} renders the item
 * into a shared atlas slot of {@code 16 * guiScale} pixels and blits that slot as a 16x16 quad.
 * The pose can scale the quad up, but the texels were already baked, so a 2.2x pumpkin is a
 * magnified bitmap. Vanilla's own answer for big items is the same mechanism used here
 * ({@code OversizedItemRenderer}, a {@code PictureInPictureRenderer}), but it is picked
 * automatically and only for models that exceed 16x16, so it cannot be asked for at an
 * arbitrary size.
 *
 * <p>The base {@link PictureInPictureRenderer} owns exactly one texture, which would be reallocated
 * whenever two icons of different size draw in the same frame. So this renderer is a thin pool: one
 * tile (a private single-texture renderer) per distinct (model, size, rotation), kept while it keeps
 * being drawn. A static icon is rendered once and then only blitted every frame, which is also what
 * makes sliding one around cheap (the pose moves it, the texture is reused); animated models
 * re-render each frame.
 */
public final class ItemIconRenderer extends PictureInPictureRenderer<ItemIconRenderState> {
    /** A tile unused for this long is freed. */
    private static final long IDLE_MS = 2000L;
    private static final long SWEEP_EVERY_MS = 500L;

    private static boolean registered;

    private final Map<ItemIconRenderState.Key, Tile> tiles = new HashMap<>();
    private long lastSweep;

    private ItemIconRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    /**
     * Registers the renderer with Fabric. Idempotent; Modern GUI's own initializer calls it, which
     * is before the game renderer builds its picture-in-picture list (the registry is closed after).
     */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        PictureInPictureRendererRegistry.register(context ->
                new ItemIconRenderer(context.minecraft().renderBuffers().bufferSource()));
        ModernGuiClient.LOGGER.info("Registered the item icon renderer");
    }

    static boolean isRegistered() {
        return registered;
    }

    @Override
    public Class<ItemIconRenderState> getRenderStateClass() {
        return ItemIconRenderState.class;
    }

    @Override
    public void prepare(ItemIconRenderState state, GuiRenderState guiRenderState, int guiScale) {
        long now = Util.getMillis();
        Tile tile = this.tiles.computeIfAbsent(state.key(), key -> new Tile(this.bufferSource));
        tile.lastUsed = now;
        tile.prepare(state, guiRenderState, guiScale);
        if (now - this.lastSweep > SWEEP_EVERY_MS) {
            this.lastSweep = now;
            this.sweep(now);
        }
    }

    private void sweep(long now) {
        this.tiles.values().removeIf(tile -> {
            if (now - tile.lastUsed <= IDLE_MS) {
                return false;
            }
            tile.close();
            return true;
        });
    }

    @Override
    protected void renderToTexture(ItemIconRenderState state, PoseStack poseStack) {
        // Never called: prepare() hands every state to a tile, which owns the texture.
    }

    @Override
    protected String getTextureLabel() {
        return "item_icon_pool";
    }

    @Override
    public void close() {
        this.tiles.values().forEach(Tile::close);
        this.tiles.clear();
        super.close();
    }

    /** One cached icon texture. */
    private static final class Tile extends PictureInPictureRenderer<ItemIconRenderState> {
        long lastUsed;
        private ItemIconRenderState.Key rendered;

        Tile(MultiBufferSource.BufferSource bufferSource) {
            super(bufferSource);
        }

        @Override
        public Class<ItemIconRenderState> getRenderStateClass() {
            return ItemIconRenderState.class;
        }

        @Override
        protected void renderToTexture(ItemIconRenderState state, PoseStack poseStack) {
            // Same set-up as vanilla's OversizedItemRenderer: the base class has scaled the stack to
            // "one model unit = scale() GUI pixels" around the texture centre; flip y/z into model space.
            poseStack.scale(1.0f, -1.0f, -1.0f);
            if (state.yaw() != 0.0f || state.pitch() != 0.0f || state.roll() != 0.0f) {
                poseStack.mulPose(new Quaternionf().rotationXYZ(
                        (float) Math.toRadians(state.pitch()),
                        (float) Math.toRadians(state.yaw()),
                        (float) Math.toRadians(state.roll())));
            }
            boolean flat = !state.item().usesBlockLight();
            Minecraft.getInstance().gameRenderer.getLighting()
                    .setupFor(flat ? Lighting.Entry.ITEMS_FLAT : Lighting.Entry.ITEMS_3D);

            FeatureRenderDispatcher dispatcher = Minecraft.getInstance().gameRenderer.getFeatureRenderDispatcher();
            SubmitNodeStorage storage = dispatcher.getSubmitNodeStorage();
            state.item().submit(poseStack, storage, 15728880, OverlayTexture.NO_OVERLAY, 0);
            dispatcher.renderAllFeatures();
            this.rendered = state.key();
        }

        @Override
        protected boolean textureIsReadyToBlit(ItemIconRenderState state) {
            return !state.item().isAnimated() && state.key().equals(this.rendered);
        }

        @Override
        protected float getTranslateY(int height, int guiScale) {
            return height / 2.0f;
        }

        @Override
        protected String getTextureLabel() {
            return "item_icon";
        }
    }
}
