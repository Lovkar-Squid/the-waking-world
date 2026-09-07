package me.lovkar.wakingworld.client;

import java.util.ArrayList;
import java.util.List;

/**
 * The lands this player has walked, as the Almanac draws them.
 *
 * <p>Held on the client and filled in by the server when the book is opened. It is deliberately not
 * kept up to date between openings: an atlas is a thing somebody wrote down, and it being a moment
 * out of date is not a bug.</p>
 */
public final class LandAtlas {
    /** One land: its name, what it is made of, and which square of the world it is. */
    public record Entry(String name, String lore, String kind, int cellX, int cellZ) {
    }

    private static List<Entry> lands = List.of();

    private LandAtlas() {
    }

    public static void set(String payload) {
        List<Entry> out = new ArrayList<>();
        for (String line : payload.split("\n")) {
            if (line.isBlank()) continue;
            String[] f = line.split("\t", -1);
            if (f.length < 5) continue;
            try {
                out.add(new Entry(f[0], f[1], f[2], Integer.parseInt(f[3]), Integer.parseInt(f[4])));
            } catch (NumberFormatException ignored) {
                // a line we cannot read is a line we leave out; the rest of the atlas is still good
            }
        }
        lands = List.copyOf(out);
    }

    public static List<Entry> lands() {
        return lands;
    }
}
