package me.lovkar.wakingworld.body;

/**
 * A solid primitive in body space, in units of the body height (so a body is described once and
 * built at any size): an ellipsoid (optionally tilted about the X axis) or a tapered capsule -
 * a segment with a radius that runs from r0 at A to r1 at B; r1 = 0 makes a cone, a spike, a tooth.
 * Bodies are unions of these; voxelizing a union is just "is the cell centre inside any of them".
 */
public final class Prim {
    private final boolean ellipsoid;
    private final double ax, ay, az, bx, by, bz; // ellipsoid: centre in a*, radii in b*
    private final double r0, r1;
    private final double cosT, sinT;              // ellipsoid tilt about X

    private Prim(boolean ellipsoid, double ax, double ay, double az, double bx, double by, double bz,
                 double r0, double r1, double tiltRad) {
        this.ellipsoid = ellipsoid;
        this.ax = ax; this.ay = ay; this.az = az;
        this.bx = bx; this.by = by; this.bz = bz;
        this.r0 = r0; this.r1 = r1;
        this.cosT = Math.cos(tiltRad); this.sinT = Math.sin(tiltRad);
    }

    public static Prim ell(double cx, double cy, double cz, double rx, double ry, double rz) {
        return new Prim(true, cx, cy, cz, rx, ry, rz, 0, 0, 0);
    }

    /** Ellipsoid tilted about the X axis by tiltDeg (negative leans the top forward, towards -Z). */
    public static Prim ell(double cx, double cy, double cz, double rx, double ry, double rz, double tiltDeg) {
        return new Prim(true, cx, cy, cz, rx, ry, rz, 0, 0, Math.toRadians(tiltDeg));
    }

    public static Prim cap(double ax, double ay, double az, double bx, double by, double bz, double r) {
        return new Prim(false, ax, ay, az, bx, by, bz, r, r, 0);
    }

    public static Prim cap(double ax, double ay, double az, double bx, double by, double bz, double r0, double r1) {
        return new Prim(false, ax, ay, az, bx, by, bz, r0, r1, 0);
    }

    /** The same primitive on the other side of the body (x mirrored). */
    public Prim mirror() {
        return new Prim(ellipsoid, -ax, ay, az, ellipsoid ? bx : -bx, by, bz, r0, r1, Math.atan2(sinT, cosT));
    }

    public boolean contains(double x, double y, double z) {
        if (ellipsoid) {
            double dx = x - ax, dy = y - ay, dz = z - az;
            if (sinT != 0) {
                double ny = dy * cosT - dz * sinT;
                double nz = dy * sinT + dz * cosT;
                dy = ny; dz = nz;
            }
            double qx = dx / bx, qy = dy / by, qz = dz / bz;
            return qx * qx + qy * qy + qz * qz <= 1.0;
        }
        double abx = bx - ax, aby = by - ay, abz = bz - az;
        double l2 = abx * abx + aby * aby + abz * abz;
        double t = l2 == 0 ? 0 : ((x - ax) * abx + (y - ay) * aby + (z - az) * abz) / l2;
        if (t < 0) t = 0; else if (t > 1) t = 1;
        double px = ax + t * abx, py = ay + t * aby, pz = az + t * abz;
        double r = r0 + (r1 - r0) * t;
        double dx = x - px, dy = y - py, dz = z - pz;
        return dx * dx + dy * dy + dz * dz <= r * r;
    }

    /** Conservative bounds, for skipping cells quickly. */
    private double rmax() { return ellipsoid ? Math.max(bx, Math.max(by, bz)) : Math.max(r0, r1); }
    private boolean tilted() { return ellipsoid && sinT != 0; }
    public double minX() { return ellipsoid ? ax - bx : Math.min(ax, bx) - rmax(); }
    public double maxX() { return ellipsoid ? ax + bx : Math.max(ax, bx) + rmax(); }
    public double minY() { return ellipsoid ? ay - (tilted() ? rmax() : by) : Math.min(ay, by) - rmax(); }
    public double maxY() { return ellipsoid ? ay + (tilted() ? rmax() : by) : Math.max(ay, by) + rmax(); }
    public double minZ() { return ellipsoid ? az - (tilted() ? rmax() : bz) : Math.min(az, bz) - rmax(); }
    public double maxZ() { return ellipsoid ? az + (tilted() ? rmax() : bz) : Math.max(az, bz) + rmax(); }
}
