package me.lovkar.wakingworld.client;

import java.util.ArrayList;
import java.util.List;

/**
 * The lands this player has walked, as the Wayfarer's Chart draws them.
 *
 * <p>Held on the client and filled in by the server when the chart is opened. It is deliberately
 * not kept up to date between openings: a chart is a thing somebody wrote down, and it being a
 * moment out of date is not a bug. The ground under it ({@link LandMap}) is a different matter and
 * does keep itself current.</p>
 */
public final class LandAtlas {
    /**
     * One land: its name, what it is made of, the square it was grown from, and every square it
     * owns as {@code x, z} pairs. A land is a region now, so the chart draws that shape rather
     * than a rectangle.
     */
    public record Entry(String name, String lore, String kind, int cellX, int cellZ, int[] cells) {
        public int count() {
            return cells.length / 2;
        }

        public int x(int i) {
            return cells[i * 2];
        }

        public int z(int i) {
            return cells[i * 2 + 1];
        }
    }

    /** A place its owner wanted to find again: in world blocks, not squares. */
    public record Pin(String name, int x, int z) {
    }

    private static List<Entry> lands = List.of();
    private static List<Pin> pins = List.of();
    private static int size = 160;

    private LandAtlas() {
    }

    public static void set(String payload) {
        List<Entry> out = new ArrayList<>();
        List<Pin> marks = new ArrayList<>();
        for (String line : payload.split("\n")) {
            if (line.isBlank()) continue;
            if (line.charAt(0) == '#') {                          // the header: how wide a square is
                try {
                    size = Integer.parseInt(line.substring(1).trim());
                } catch (NumberFormatException ignored) {
                    // keep whatever we had; a wrong scale draws a wrong map, a crash draws nothing
                }
                continue;
            }
            if (line.charAt(0) == '@') {                          // a pin: x, z, name
                String[] pf = line.substring(1).split("\t", 3);
                if (pf.length < 3) continue;
                try {
                    marks.add(new Pin(pf[2], Integer.parseInt(pf[0].trim()), Integer.parseInt(pf[1].trim())));
                } catch (NumberFormatException ignored) {
                    // a pin we cannot read is a pin we leave out
                }
                continue;
            }
            String[] f = line.split("\t", -1);
            if (f.length < 5) continue;
            try {
                int cx = Integer.parseInt(f[3]), cz = Integer.parseInt(f[4]);
                out.add(new Entry(f[0], f[1], f[2], cx, cz, cells(f.length > 5 ? f[5] : "", cx, cz)));
            } catch (NumberFormatException ignored) {
                // a line we cannot read is a line we leave out; the rest of the chart is still good
            }
        }
        lands = List.copyOf(out);
        pins = List.copyOf(marks);
    }

    public static List<Pin> pins() {
        return pins;
    }

    /** {@code "x,z;x,z;..."} into a flat pair array, falling back to the one square it grew from. */
    private static int[] cells(String field, int cx, int cz) {
        if (field.isBlank()) return new int[]{cx, cz};
        String[] parts = field.split(";");
        int[] out = new int[parts.length * 2];
        int n = 0;
        for (String p : parts) {
            int comma = p.indexOf(',');
            if (comma <= 0) continue;
            try {
                out[n++] = Integer.parseInt(p.substring(0, comma).trim());
                out[n++] = Integer.parseInt(p.substring(comma + 1).trim());
            } catch (NumberFormatException ignored) {
                n = n & ~1;                                       // drop the half-read pair
            }
        }
        if (n == 0) return new int[]{cx, cz};
        if (n == out.length) return out;
        int[] trimmed = new int[n];
        System.arraycopy(out, 0, trimmed, 0, n);
        return trimmed;
    }

    public static List<Entry> lands() {
        return lands;
    }

    /** How wide one square of the map is, in blocks, as the server has it. */
    public static int size() {
        return size;
    }
}
