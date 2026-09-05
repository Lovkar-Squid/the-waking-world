package me.lovkar.wakingworld.worldgen;

/** Little geometry shared by the column-drawn pieces. */
public final class Geo {
    private Geo() {
    }

    /** The angle of (dx, dz) in degrees, 0 = +x, 90 = +z, in [0, 360). */
    public static double angle(int dx, int dz) {
        double a = Math.toDegrees(Math.atan2(dz, dx));
        return a < 0 ? a + 360 : a;
    }
}
