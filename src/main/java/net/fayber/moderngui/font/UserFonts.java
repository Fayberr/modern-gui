package net.fayber.moderngui.font;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns a folder listing of font files into families, so an end user can drop TTFs into
 * {@code config/moderngui/fonts/} and pick them. Pure string handling.
 *
 * <p>Files are grouped by name: {@code Manrope-Regular.ttf} and {@code Manrope-Bold.ttf} are the
 * regular and bold of the family "manrope" (suffixes: regular, book, medium, semibold, demibold,
 * bold; separated by {@code -} or {@code _}). A file without a weight suffix is a regular face. A
 * family with no bold file uses its regular file for bold too, since there is nothing to
 * synthesise a bold from; emphasis then comes from colour and size. A family without a regular
 * file is ignored.
 */
final class UserFonts {
    /** One discovered family: a lower-case name and its files by weight. */
    record Entry(String name, Map<FontWeight, String> files) {
    }

    private UserFonts() {
    }

    static List<Entry> group(List<String> fileNames) {
        Map<String, Map<FontWeight, String>> byName = new TreeMap<>();
        for (String file : fileNames) {
            String lower = file.toLowerCase(Locale.ROOT);
            if (!(lower.endsWith(".ttf") || lower.endsWith(".otf"))) {
                continue;
            }
            String stem = lower.substring(0, lower.length() - 4);
            FontWeight weight = FontWeight.REGULAR;
            int cut = Math.max(stem.lastIndexOf('-'), stem.lastIndexOf('_'));
            if (cut > 0) {
                FontWeight suffix = weightOf(stem.substring(cut + 1));
                if (suffix != null) {
                    weight = suffix;
                    stem = stem.substring(0, cut);
                }
            }
            byName.computeIfAbsent(slug(stem), k -> new EnumMap<>(FontWeight.class)).putIfAbsent(weight, file);
        }
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<String, Map<FontWeight, String>> e : byName.entrySet()) {
            Map<FontWeight, String> files = e.getValue();
            if (!files.containsKey(FontWeight.REGULAR) || e.getKey().isEmpty()) {
                continue;
            }
            files.putIfAbsent(FontWeight.BOLD, files.get(FontWeight.REGULAR));
            out.add(new Entry(e.getKey(), files));
        }
        return out;
    }

    private static FontWeight weightOf(String suffix) {
        return switch (suffix) {
            case "regular", "book", "normal" -> FontWeight.REGULAR;
            case "medium" -> FontWeight.MEDIUM;
            case "semibold", "demibold", "semi" -> FontWeight.SEMIBOLD;
            case "bold" -> FontWeight.BOLD;
            default -> null;
        };
    }

    /** Lower-case, resource-id safe name: anything outside {@code a-z0-9_.-} becomes {@code _}. */
    static String slug(String raw) {
        StringBuilder out = new StringBuilder();
        for (char c : raw.toLowerCase(Locale.ROOT).toCharArray()) {
            out.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' ? c : '_');
        }
        return out.toString();
    }
}
