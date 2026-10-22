package net.fayber.moderngui.font;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fayber.moderngui.ModernGuiClient;
import net.fayber.moderngui.render.TextSizing;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A typeface Modern GUI can draw with: one TTF per weight plus the knobs that make it look right.
 * Inter is the built-in default ({@link FontFamilies#INTER}); a consumer registers its own with
 * {@link FontFamilies#register} and either makes it the default
 * ({@link net.fayber.moderngui.ModernGui#setDefaultFont}) or passes it to the draw calls that take
 * a family.
 *
 * <h2>Crisp at every size</h2>
 * The family is only a description; the fonts that Minecraft actually loads are generated from it,
 * one per (weight, size factor, GUI scale): a TTF provider with {@code size = 9 * factor} and
 * {@code oversample = guiScale}, so every glyph is rasterised at exactly its physical pixel size
 * (see {@link TextSizing}). {@link #definition} produces that json and {@link FontPack} puts it
 * where the resource loader finds it, so a consumer never writes one by hand. Glyphs the TTF lacks
 * fall back to vanilla's default and unifont providers, same as before.
 *
 * <h2>Cost</h2>
 * Each generated font opens its own copy of the TTF (about the file size in native memory), so a
 * family costs {@code weights x sizes x scales} copies: 60 for the default five sizes, two weights
 * and six scales. {@link Builder#sizes} and {@link Builder#maxScale} trim that.
 *
 * <p>Immutable once built; safe to share.
 */
public final class FontFamily {
    private final Identifier id;
    private final String slug;
    private final Map<FontWeight, Identifier> files;
    private final int[] percents;
    private final int maxScale;
    private final float baseline;

    /** True once the generated fonts are on disk and registered; until then the vanilla font is used. */
    private volatile boolean installed;

    /** [weight][percent index][scale], filled lazily on the render thread. */
    private final Style[][][] styles;

    private FontFamily(Builder b, float baseline) {
        this.id = b.id;
        this.slug = slugOf(b.id);
        this.files = new EnumMap<>(b.files);
        this.percents = b.percents.clone();
        this.maxScale = b.maxScale;
        this.baseline = baseline;
        this.styles = new Style[FontWeight.values().length][this.percents.length][this.maxScale + 1];
    }

    /** True when this family's fonts were generated; a family that failed to install draws vanilla text. */
    public boolean installed() {
        return this.installed;
    }

    void markInstalled() {
        this.installed = true;
    }

    /** Starts a family description. {@code id} names it; it is also the key in {@link FontFamilies}. */
    public static Builder builder(Identifier id) {
        return new Builder(id);
    }

    public Identifier id() {
        return this.id;
    }

    /** Filesystem and font-id safe name derived from the id ({@code mymod:fancy} becomes {@code mymod_fancy}). */
    public String slug() {
        return this.slug;
    }

    /** The size factors, in percent, this family generates fonts for. */
    public int[] percents() {
        return this.percents.clone();
    }

    /** The highest GUI scale with its own generated fonts. */
    public int maxScale() {
        return this.maxScale;
    }

    /**
     * Where the baseline sits in a 9px line box, in pixels from the top; 7 for Inter. Measured from
     * the regular face's cap height at registration unless the builder overrode it.
     */
    public float baseline() {
        return this.baseline;
    }

    /** The weights this family really has a file for (the others fall back). */
    public List<FontWeight> declaredWeights() {
        List<FontWeight> out = new ArrayList<>(this.files.keySet());
        out.sort(null);
        return out;
    }

    /** The weight that is actually used when {@code weight} is requested. */
    public FontWeight resolve(FontWeight weight) {
        return this.files.containsKey(weight) ? weight : weight.fallback();
    }

    /** The TTF behind {@code weight} (after fallback). */
    public Identifier file(FontWeight weight) {
        return this.files.get(this.resolve(weight));
    }

    /** The size factor, in percent, a requested multiplier snaps to for this family. */
    public int snapPercent(float factor) {
        return TextSizing.snapPercent(factor, this.percents);
    }

    /** The GUI scale variant used for a window GUI scale. */
    public int scaleIndex(float guiScale) {
        return TextSizing.scaleIndex(guiScale, this.maxScale);
    }

    /** The id of the generated font for a declared weight, size percent and GUI scale. */
    public Identifier fontId(FontWeight weight, int percent, int scale) {
        return Identifier.fromNamespaceAndPath(ModernGuiClient.MOD_ID,
                this.slug + "/" + this.resolve(weight).key() + "_x" + scale + "_f" + percent);
    }

    /**
     * The font json Minecraft loads for one variant: the family's TTF at {@code size = 9 * factor}
     * and {@code oversample = scale}, vertically shifted so the baseline sits where
     * {@link #baseline()} says, then vanilla's space, default and unifont providers as fallbacks.
     */
    public String definition(FontWeight weight, int percent, int scale) {
        JsonObject ttf = new JsonObject();
        ttf.addProperty("type", "ttf");
        ttf.addProperty("file", this.file(weight).toString());
        JsonArray shift = new JsonArray();
        shift.add(0.0f);
        shift.add(TextSizing.baselineShift(percent, scale, this.baseline));
        ttf.add("shift", shift);
        ttf.addProperty("size", TextSizing.BASE_SIZE * percent / 100.0f);
        ttf.addProperty("oversample", (float) scale);

        JsonArray providers = new JsonArray();
        providers.add(ttf);
        for (String ref : new String[] {"space", "default", "unifont"}) {
            JsonObject reference = new JsonObject();
            reference.addProperty("type", "reference");
            reference.addProperty("id", "minecraft:include/" + ref);
            providers.add(reference);
        }
        JsonObject root = new JsonObject();
        root.add("providers", providers);
        return new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n";
    }

    /** Every variant this family generates, as (resource path under {@code font/}, json). */
    public Map<String, String> definitions() {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (FontWeight weight : this.declaredWeights()) {
            for (int percent : this.percents) {
                for (int scale = 1; scale <= this.maxScale; scale++) {
                    out.put(this.fontId(weight, percent, scale).getPath() + ".json", this.definition(weight, percent, scale));
                }
            }
        }
        return out;
    }

    /**
     * The style that selects this family's font for a weight, size factor (1.0 = normal) and the
     * given GUI scale. Cached.
     */
    public Style style(FontWeight weight, float sizeFactor, float guiScale) {
        if (!this.installed) {
            return Style.EMPTY;
        }
        int w = this.resolve(weight).ordinal();
        int p = TextSizing.percentIndex(sizeFactor, this.percents);
        int s = this.scaleIndex(guiScale);
        Style style = this.styles[w][p][s];
        if (style == null) {
            style = Style.EMPTY.withFont(new FontDescription.Resource(
                    this.fontId(this.resolve(weight), this.percents[p], s)));
            this.styles[w][p][s] = style;
        }
        return style;
    }

    @Override
    public String toString() {
        return "FontFamily[" + this.id + "]";
    }

    static String slugOf(Identifier id) {
        String raw = id.getNamespace() + "_" + id.getPath();
        StringBuilder out = new StringBuilder(raw.length());
        for (char c : raw.toCharArray()) {
            out.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' ? c : '_');
        }
        return out.toString();
    }

    /** True when both describe the same family (same files, sizes, scale cap and baseline). */
    boolean sameAs(FontFamily other) {
        return this.id.equals(other.id) && this.files.equals(other.files)
                && Arrays.equals(this.percents, other.percents) && this.maxScale == other.maxScale
                && this.baseline == other.baseline;
    }

    /** Fluent description of a family. {@link #regular} and {@link #bold} are required. */
    public static final class Builder {
        private final Identifier id;
        private final Map<FontWeight, Identifier> files = new EnumMap<>(FontWeight.class);
        private int[] percents = TextSizing.DEFAULT_PERCENTS.clone();
        private int maxScale = TextSizing.DEFAULT_MAX_SCALE;
        private Float baseline;

        private Builder(Identifier id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        /**
         * The TTF for a weight, as a resource id: {@code mymod:fancy_regular.ttf} is the file
         * {@code assets/mymod/font/fancy_regular.ttf}, the same form a vanilla font json uses.
         */
        public Builder weight(FontWeight weight, Identifier ttf) {
            this.files.put(weight, Objects.requireNonNull(ttf, "ttf"));
            return this;
        }

        public Builder regular(Identifier ttf) {
            return this.weight(FontWeight.REGULAR, ttf);
        }

        public Builder bold(Identifier ttf) {
            return this.weight(FontWeight.BOLD, ttf);
        }

        /** Optional; without it {@link FontWeight#MEDIUM} uses the regular file. */
        public Builder medium(Identifier ttf) {
            return this.weight(FontWeight.MEDIUM, ttf);
        }

        /** Optional; without it {@link FontWeight#SEMIBOLD} uses the bold file. */
        public Builder semibold(Identifier ttf) {
            return this.weight(FontWeight.SEMIBOLD, ttf);
        }

        /**
         * The size factors, in percent of the 9px base, to generate (default 75, 100, 125, 150,
         * 200). 100 is always included. Any list is fine, e.g. {@code sizes(100, 300)} for a
         * display face; fewer sizes cost less memory.
         */
        public Builder sizes(int... percents) {
            int[] sorted = Arrays.stream(percents).filter(p -> p > 0).distinct().sorted().toArray();
            if (Arrays.stream(sorted).noneMatch(p -> p == 100)) {
                int[] with = Arrays.copyOf(sorted, sorted.length + 1);
                with[sorted.length] = 100;
                Arrays.sort(with);
                sorted = with;
            }
            this.percents = sorted;
            return this;
        }

        /** Highest GUI scale to generate fonts for (default 6); a larger window scale uses this one. */
        public Builder maxScale(int maxScale) {
            this.maxScale = Math.clamp(maxScale, 1, 16);
            return this;
        }

        /**
         * Overrides the baseline, in pixels below the top of a 9px line box (Inter's is 7). Leave
         * unset to have it measured from the regular TTF's cap height so capitals centre like
         * Inter's; set it when a face looks too high (raise the number) or too low (lower it).
         */
        public Builder baseline(float baseline) {
            this.baseline = baseline;
            return this;
        }

        boolean hasBaseline() {
            return this.baseline != null;
        }

        /** Builds with an explicit baseline (the registry passes the measured one when none was set). */
        FontFamily build(float measuredBaseline) {
            if (!this.files.containsKey(FontWeight.REGULAR) || !this.files.containsKey(FontWeight.BOLD)) {
                throw new IllegalStateException("A FontFamily needs at least a regular and a bold TTF: " + this.id);
            }
            return new FontFamily(this, this.baseline != null ? this.baseline : measuredBaseline);
        }

        Identifier regularFile() {
            return this.files.get(FontWeight.REGULAR);
        }

        /** Builds with Inter's baseline unless one was set; use {@link FontFamilies#register} to measure it. */
        public FontFamily build() {
            return this.build(FontMetrics.REFERENCE_BASELINE);
        }
    }
}
