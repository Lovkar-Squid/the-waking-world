package me.lovkar.wakingworld.body;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The pose of every part of a body, as one affine transform per part (3x3 matrix plus offset),
 * built once per tick from {@link ColossusPose#transform} so that thousands of cells can be carried
 * into the posed body with nine multiplications each instead of a chain of trigonometry. Body space
 * in, posed body space out (the body yaw, the fall and the drop of a dying giant are applied by
 * the caller).
 */
public final class PosedBody {
    private final Map<PartDef, double[]> transforms = new IdentityHashMap<>();

    public PosedBody(ColossusBody body, ColossusPose pose) {
        PartDef torso = body.part(PartDef.Kind.TORSO);
        double[] o = new double[3], ex = new double[3], ey = new double[3], ez = new double[3];
        for (PartDef def : body.parts) {
            o[0] = 0; o[1] = 0; o[2] = 0;
            ex[0] = 1; ex[1] = 0; ex[2] = 0;
            ey[0] = 0; ey[1] = 1; ey[2] = 0;
            ez[0] = 0; ez[1] = 0; ez[2] = 1;
            pose.transform(o, def, torso);
            pose.transform(ex, def, torso);
            pose.transform(ey, def, torso);
            pose.transform(ez, def, torso);
            // columns of the matrix are the images of the axes, less the image of the origin
            transforms.put(def, new double[]{
                    ex[0] - o[0], ey[0] - o[0], ez[0] - o[0],
                    ex[1] - o[1], ey[1] - o[1], ez[1] - o[1],
                    ex[2] - o[2], ey[2] - o[2], ez[2] - o[2],
                    o[0], o[1], o[2]});
        }
    }

    /** Carries a body-space point of the given part into the posed body, in place. */
    public void apply(PartDef def, double[] v) {
        double[] m = transforms.get(def);
        if (m == null) return;
        double x = v[0], y = v[1], z = v[2];
        v[0] = m[0] * x + m[1] * y + m[2] * z + m[9];
        v[1] = m[3] * x + m[4] * y + m[5] * z + m[10];
        v[2] = m[6] * x + m[7] * y + m[8] * z + m[11];
    }
}
