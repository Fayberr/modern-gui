package net.fayber.moderngui.font;

/**
 * The weights a {@link FontFamily} can provide. {@link #REGULAR} and {@link #BOLD} are required;
 * {@link #MEDIUM} and {@link #SEMIBOLD} are optional and fall back to the nearest required one
 * ({@link #fallback()}), so asking a family for a weight it does not have is always safe.
 */
public enum FontWeight {
    REGULAR("regular"),
    MEDIUM("medium"),
    SEMIBOLD("semibold"),
    BOLD("bold");

    private final String key;

    FontWeight(String key) {
        this.key = key;
    }

    /** Short lower-case name used in generated font ids. */
    public String key() {
        return this.key;
    }

    /** The weight used in place of this one when a family lacks it (itself when required). */
    public FontWeight fallback() {
        return switch (this) {
            case MEDIUM -> REGULAR;
            case SEMIBOLD -> BOLD;
            default -> this;
        };
    }
}
