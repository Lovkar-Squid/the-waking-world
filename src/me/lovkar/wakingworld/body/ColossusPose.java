package me.lovkar.wakingworld.body;

import net.minecraft.util.Mth;

/**
 * The animation state of a colossus at one instant: how far each limb swings, how the head and
 * torso turn, plus the attack overlay. Angles are radians. Limbs rotate about the X axis of their
 * pivot (positive = the lower end swings towards -Z, i.e. forward), the head about Y (yaw) and X
 * (pitch), the torso about X (pitch, positive = leaning forward) and Y (twist). The head and the
 * arms ride on the torso.
 */
public final class ColossusPose {
    public float leftLeg, rightLeg, leftArm, rightArm, headYaw, headPitch;
    /** Vertical bob of everything above the legs, in blocks. */
    public float bob;
    public float torsoPitch, torsoYaw;
    /** Arms also swing sideways (about Z) during a slam so both fists meet in front. */
    public float armSpread;

    public static final ColossusPose REST = new ColossusPose();

    /** Leg swing of the walk, radians each way. */
    public static final float STRIDE_SWING = 0.38f;

    /** Ground covered by one full walk cycle (two steps), blocks: the feet then stay planted instead of skating. */
    public static float cycleLength(int height) {
        float leg = 0.36f * height;
        return 4f * leg * Mth.sin(STRIDE_SWING);
    }

    /**
     * @param stride    ground covered so far, blocks (the walk cycle is driven by distance, not time)
     * @param walkSpeed walkAnimation.speed(partialTick), 0..1 - only eases the swing in and out
     * @param headYawDeg head yaw relative to the body, degrees
     * @param headPitchDeg head pitch, degrees
     * @param height    body height in blocks
     */
    public static ColossusPose walking(float stride, float walkSpeed, float headYawDeg, float headPitchDeg, int height) {
        ColossusPose p = new ColossusPose();
        float phase = stride * (Mth.TWO_PI / cycleLength(height));
        float ease = Mth.clamp(walkSpeed * 2.5f, 0f, 1f);
        float amp = STRIDE_SWING * ease;
        float leg = Mth.cos(phase) * amp;
        float arm = Mth.cos(phase + Mth.PI) * amp * 0.6f;
        p.leftLeg = leg;
        p.rightLeg = -leg;
        p.leftArm = arm;
        p.rightArm = -arm;
        p.bob = Math.abs(Mth.sin(phase)) * 0.35f * ease * (height / 40f);
        p.headYaw = Mth.clamp(headYawDeg, -60f, 60f) * Mth.DEG_TO_RAD;
        p.headPitch = Mth.clamp(headPitchDeg, -30f, 45f) * Mth.DEG_TO_RAD;
        return p;
    }

    /**
     * The recoil from a blow: t runs 0..1 over the flinch, power 0..1 with the weight of the blow.
     * The torso rocks back and the head with it, the arms come up a little, the body dips - and
     * settles. Nothing a giant would do for an arrow; that is decided before this is called.
     */
    public void flinch(float t, float power, int height) {
        float k = Mth.sin(Mth.clamp(t, 0f, 1f) * Mth.PI) * power;
        torsoPitch -= 0.09f * k;
        headPitch -= 0.25f * k;
        leftArm -= 0.16f * k;
        rightArm -= 0.16f * k;
        armSpread += 0.08f * k;
        bob -= 0.25f * k * (height / 40f);
    }

    /**
     * Layers an attack over the walking pose. attackId = Attack.id, progress 0..1 through the
     * attack, impact = the attack's impact tick / duration, rightFoot = which foot stomps.
     */
    public void attack(int attackId, float progress, float impact, boolean rightFoot) {
        attack(attackId, progress, impact, rightFoot, false);
    }

    /** As above; {@code airborne} keeps a leap in its flying pose past its usual length while the body is still in the air. */
    public void attack(int attackId, float progress, float impact, boolean rightFoot, boolean airborne) {
        if (attackId == 0) return;
        float wind = Mth.clamp(progress / impact, 0f, 1f);              // 0..1 during the wind-up
        float after = Mth.clamp((progress - impact) / (1f - impact), 0f, 1f); // 0..1 during recovery
        float windS = smooth(wind);
        float recover = 1f - smooth(after);
        switch (attackId) {
            case 1 -> { // STOMP: the foot lifts forward-up, hangs, comes down at impact
                float lift = progress < impact ? Mth.sin(windS * Mth.PI * 0.5f) * 1.0f : 1.0f * (1f - smooth(Math.min(1f, after * 4f)));
                if (rightFoot) rightLeg = lift; else leftLeg = lift;
                torsoPitch = progress < impact ? -0.08f * windS : 0.12f * recover;
                bob += progress < impact ? 0.6f * windS : -0.8f * recover;
            }
            case 2 -> { // SWIPE: right arm back, then a fast sweep across; the torso twists with it
                if (progress < impact) {
                    rightArm = -1.3f * windS;
                    torsoYaw = -0.35f * windS;
                    torsoPitch = -0.05f * windS;
                } else {
                    float swing = smooth(Math.min(1f, after * 3f));
                    rightArm = -1.3f + 2.9f * swing - 1.6f * smooth(after);
                    torsoYaw = -0.35f + 0.7f * swing - 0.35f * smooth(after);
                    torsoPitch = 0.18f * swing * recover;
                }
                armSpread = progress < impact ? 0.5f * windS : 0.5f * recover;
            }
            case 3 -> { // SLAM: both arms overhead, then into the ground in front
                if (progress < impact) {
                    leftArm = rightArm = -2.3f * windS;
                    torsoPitch = -0.25f * windS;
                    bob += 1.2f * windS;
                } else {
                    float down = smooth(Math.min(1f, after * 3f));
                    leftArm = rightArm = -2.3f + 3.3f * down - 1.0f * smooth(after);
                    torsoPitch = -0.25f + 0.6f * down - 0.35f * smooth(after);
                    bob += 1.2f - 2.4f * down + 1.2f * smooth(after);
                }
                armSpread = -0.35f * (progress < impact ? windS : recover);
            }
            case 4 -> { // BOULDER: right arm winds back over the shoulder, then whips forward
                if (progress < impact) {
                    rightArm = -2.1f * windS;
                    torsoYaw = -0.3f * windS;          // right shoulder back
                    torsoPitch = -0.12f * windS;
                } else {
                    float throwS = smooth(Math.min(1f, after * 3f));
                    rightArm = -2.1f + 3.2f * throwS - 1.1f * smooth(after);
                    torsoYaw = -0.3f + 0.65f * throwS - 0.35f * smooth(after);
                    torsoPitch = 0.2f * throwS * recover;
                }
            }
            case 5 -> { // ROAR: head up, chest out, arms flung wide
                float open = progress < impact ? windS : recover;
                headPitch = -0.75f * open;
                torsoPitch = -0.2f * open;
                leftArm = rightArm = 0.6f * open;
                armSpread = 0.9f * open;
                bob += 0.6f * open;
            }
            case 6 -> { // UPROOT: both arms reach down and forward, heave the tree up over the shoulder, throw
                float grab = smooth(Mth.clamp(progress / (impact * 0.45f), 0f, 1f));      // reach down
                float heave = smooth(Mth.clamp((progress - impact * 0.45f) / (impact * 0.55f), 0f, 1f)); // lift
                if (progress < impact) {
                    float a = 1.4f * grab - 3.5f * heave;         // +1.4 = arms forward-down, -2.1 = over the shoulder
                    leftArm = rightArm = a;
                    torsoPitch = 0.45f * grab - 0.6f * heave;      // bends down, then leans back with the weight
                    bob += -1.5f * grab + 1.5f * heave;
                    torsoYaw = -0.25f * heave;
                } else {
                    float throwS = smooth(Math.min(1f, after * 3f));
                    leftArm = rightArm = -2.1f + 3.2f * throwS - 1.1f * smooth(after);
                    torsoYaw = -0.25f + 0.55f * throwS - 0.3f * smooth(after);
                    torsoPitch = -0.15f + 0.45f * throwS - 0.3f * smooth(after);
                }
            }
            case 7 -> { // CHARGE: head down like a bull, torso forward, arms back; the run itself is the walk cycle
                float lower = progress < impact ? windS : (progress > 0.83f ? recover * 0.3f : 1f);
                headPitch = 0.55f * lower;
                torsoPitch = 0.4f * lower;
                leftArm = rightArm = -0.7f * lower;
                armSpread = -0.2f * lower;
            }
            case 8 -> { // LEAP: crouch, then arms up and legs tucked in the air, then a heavy landing
                if (progress < impact) {
                    torsoPitch = 0.35f * windS;
                    bob -= 2.0f * windS;
                    leftArm = rightArm = -0.8f * windS;
                    leftLeg = rightLeg = 0.25f * windS;
                } else if (progress < 0.78f || airborne) {
                    float air = smooth(Mth.clamp((progress - impact) / 0.15f, 0f, 1f));
                    leftArm = rightArm = -0.8f - 1.4f * air;
                    leftLeg = rightLeg = 0.25f + 0.5f * air;
                    torsoPitch = 0.35f - 0.5f * air;
                } else {
                    float landS = smooth(Mth.clamp((progress - 0.78f) / 0.22f, 0f, 1f));
                    bob -= 2.5f * (1f - landS);
                    torsoPitch = 0.4f * (1f - landS);
                    leftArm = rightArm = 0.5f * (1f - landS);
                }
            }
            case 10 -> { // GRAB: bends, the right hand closes on the ground ahead (impact), lifts the catch up before its face, then hurls it
                float ticks = progress * 96f;
                if (progress < impact) {
                    torsoPitch = 0.6f * windS;
                    headPitch = 0.5f * windS;
                    rightArm = 0.85f * windS;
                    armSpread = 0.1f * windS;
                    bob -= 2.0f * windS;
                } else if (ticks < 64f) {
                    // up before the face, with a squeeze
                    float up = smooth(Mth.clamp((ticks - 22f) / 20f, 0f, 1f));
                    float hold = Mth.clamp((ticks - 42f) / 22f, 0f, 1f);
                    torsoPitch = 0.6f - 0.55f * up;
                    headPitch = 0.5f - 0.4f * up;
                    headYaw = 0.45f * up;
                    rightArm = 0.85f + 0.6f * up + Mth.sin(ticks * 1.3f) * 0.03f * hold;
                    armSpread = 0.1f + 0.1f * up;
                    bob -= 2.0f * (1f - up);
                    torsoYaw = -0.2f * up;
                } else {
                    // the throw: a whip forward and over, then it straightens up
                    float throwS = smooth(Mth.clamp((ticks - 64f) / 8f, 0f, 1f));
                    float rec = smooth(Mth.clamp((ticks - 72f) / 24f, 0f, 1f));
                    rightArm = (1.45f + 0.95f * throwS) * (1f - rec);
                    torsoYaw = (-0.2f + 0.55f * throwS) * (1f - rec);
                    torsoPitch = (0.05f + 0.3f * throwS) * (1f - rec);
                    headPitch = 0.1f * (1f - rec);
                    headYaw = 0.45f * (1f - throwS) * (1f - rec);
                    armSpread = 0.2f * (1f - rec);
                }
            }
            case 11, 16 -> { // FROST BREATH / WATER JET: head comes down to aim, torso leans in, arms back; holds while the stream runs
                float hold = progress < impact ? windS : (progress < 0.8f ? 1f : recover);
                headPitch = 0.4f * hold + (progress > impact && progress < 0.8f ? Mth.sin(progress * 90f) * 0.02f : 0f);
                torsoPitch = 0.28f * hold;
                leftArm = rightArm = -0.55f * hold;
                armSpread = 0.25f * hold;
                bob -= 0.8f * hold;
            }
            case 12 -> { // ICE SPIKES: the right fist overhead, then into the ground
                if (progress < impact) {
                    rightArm = -2.3f * windS;
                    torsoPitch = -0.2f * windS;
                    torsoYaw = -0.2f * windS;
                    bob += 1.0f * windS;
                } else {
                    float down = smooth(Math.min(1f, after * 3f));
                    rightArm = -2.3f + 3.4f * down - 1.1f * smooth(after);
                    torsoPitch = -0.2f + 0.6f * down - 0.4f * smooth(after);
                    torsoYaw = -0.2f + 0.3f * down - 0.1f * smooth(after);
                    bob += 1.0f - 2.2f * down + 1.2f * smooth(after);
                }
                armSpread = 0.15f * (progress < impact ? windS : recover);
            }
            case 13 -> { // SANDSTORM: arms flung wide, the whole upper body sweeps side to side
                float open = progress < impact ? windS : (progress < 0.85f ? 1f : recover);
                leftArm = rightArm = 0.5f * open;
                armSpread = 1.1f * open;
                headPitch = -0.3f * open;
                torsoPitch = -0.1f * open;
                if (progress > impact && progress < 0.85f) {
                    float t = (progress - impact) / (0.85f - impact);
                    torsoYaw = Mth.sin(t * Mth.PI * 3f) * 0.45f;
                }
            }
            case 14 -> attack(1, progress, impact, rightFoot); // SAND GEYSER: a stamp
            case 15 -> { // TIDAL WAVE: both arms wound back and out, then swept forward together
                if (progress < impact) {
                    leftArm = rightArm = -1.6f * windS;
                    armSpread = 0.7f * windS;
                    torsoPitch = -0.15f * windS;
                    bob += 0.6f * windS;
                } else {
                    float sweep = smooth(Math.min(1f, after * 2.5f));
                    leftArm = rightArm = -1.6f + 2.9f * sweep - 1.3f * smooth(after);
                    armSpread = 0.7f - 0.9f * sweep + 0.2f * smooth(after);
                    torsoPitch = -0.15f + 0.5f * sweep - 0.35f * smooth(after);
                    bob += 0.6f - 1.4f * sweep + 0.8f * smooth(after);
                }
            }
            case 17 -> { // GRASPING ROOTS: bends and drives the right hand into the earth, holds it there, straightens
                float in = progress < impact ? windS : (progress < 0.6f ? 1f : recover);
                torsoPitch = 0.6f * in;
                headPitch = 0.5f * in;
                rightArm = 0.85f * in;
                armSpread = 0.1f * in;
                bob -= 2.0f * in;
                if (progress > impact && progress < 0.6f) torsoPitch += Mth.sin(progress * 70f) * 0.03f;
            }
            case 18 -> attack(9, progress, impact, rightFoot); // SPORE CLOUD: the shake
            case 19 -> { // ROCKFALL: head thrown back, then it beats its chest while the sky comes down
                float up = progress < impact ? windS : (progress < 0.86f ? 1f : recover);
                headPitch = -0.7f * up;
                torsoPitch = -0.25f * up;
                bob += 0.8f * up;
                if (progress > impact && progress < 0.86f) {
                    float t = (progress - impact) * 60f;
                    float beat = Mth.sin(t);
                    leftArm = -1.4f + 0.5f * beat;
                    rightArm = -1.4f - 0.5f * beat;
                    armSpread = -0.2f;
                } else {
                    leftArm = rightArm = -1.4f * up;
                    armSpread = -0.2f * up;
                }
            }
            case 20 -> attack(3, progress, impact, rightFoot); // QUAKE: both fists into the ground
            case 9 -> { // RUBBLE: it shakes itself like a wet dog
                float shake = progress < impact ? windS : recover;
                float t = progress * 60f;
                torsoYaw = Mth.sin(t) * 0.22f * shake;
                torsoPitch = Mth.cos(t * 0.7f) * 0.1f * shake;
                leftArm = 0.5f * shake + Mth.sin(t + 1f) * 0.3f * shake;
                rightArm = 0.5f * shake - Mth.sin(t + 1f) * 0.3f * shake;
                armSpread = 0.4f * shake;
                bob += Mth.sin(t * 2f) * 0.4f * shake;
            }
            default -> { }
        }
    }

    /** Waking: it comes up bowed - head down, arms hanging forward - and straightens as it rises (t = 0..1). */
    public void rising(float t) {
        float s = 1f - smooth(Mth.clamp(t, 0f, 1f));
        torsoPitch += 0.35f * s;
        headPitch += 0.55f * s;
        leftArm += 0.45f * s;
        rightArm += 0.45f * s;
        armSpread += 0.15f * s;
    }

    // ---- death: the mountain comes down slowly ----------------------------------------------

    /** Ticks from the killing blow to the final burst; the victory music is cut to the same length. */
    public static final int DEATH_TICKS = 600;
    public static final int DEATH_STAGGER_END = 60, DEATH_KNEE_HIT = 120, DEATH_KNEE_END = 180, DEATH_BRACE_END = 280,
            DEATH_FALL_START = 280, DEATH_FALL_HIT = 336, DEATH_LYING = 360, DEATH_FINAL = 470;

    /** Vertical offset of the whole body, blocks (negative = sunk down), applied inside the fall rotation. */
    public float drop;
    /** Rotation of the whole body about the X axis through the feet, radians; negative = forward onto its face. */
    public float fall;
    /** Vertical offset applied outside the fall rotation (world up), blocks - the fallen body rests on the ground, not in it. */
    public float lift;

    /**
     * The death, tick by tick over {@link #DEATH_TICKS}: it staggers (the cores go out), drops onto
     * one knee with the hands braced forward, slumps onto the right arm, the arm gives way and it
     * topples face-down, and lies there while it crumbles limb by limb (the renderer stops drawing
     * crumbled parts, see {@link #crumbled}). The kneel needs the leg length, hence the body height.
     */
    public void dying(float ticks, int height) {
        float leg = 0.36f * height;
        // stagger: everything sags, the head drops, a shudder runs through it
        float sh = smooth(Mth.clamp(ticks / DEATH_STAGGER_END, 0f, 1f));
        float tp = 0.15f * sh, hp = 0.35f * sh, bb = -0.6f * sh, la = 0.25f * sh, ra = 0.25f * sh, sp = 0f;
        float ll = 0f, rl = 0f, dr = 0f;
        if (ticks < DEATH_FALL_HIT) {
            float calm = 1f - Mth.clamp((ticks - DEATH_FALL_START) / 56f, 0f, 1f);
            torsoYaw += Mth.sin(ticks * 1.7f) * 0.03f * calm;
        }
        if (ticks > DEATH_STAGGER_END) {
            // down onto the right knee: that leg folds back under it, the left steps forward, the body
            // sinks, the hands come forward to brace
            float k = smooth(Mth.clamp((ticks - DEATH_STAGGER_END) / (DEATH_KNEE_HIT - DEATH_STAGGER_END), 0f, 1f));
            float bounce = ticks > DEATH_KNEE_HIT && ticks < DEATH_KNEE_END
                    ? Mth.sin((ticks - DEATH_KNEE_HIT) / (float) (DEATH_KNEE_END - DEATH_KNEE_HIT) * Mth.PI) * 0.06f : 0f;
            rl += -1.1f * k;
            ll += 0.9f * k;
            dr += -0.5f * leg * k + bounce * leg;
            tp += 0.2f * k;
            hp += 0.2f * k;
            la += 0.55f * k;
            ra += 0.55f * k;
        }
        if (ticks > DEATH_KNEE_END) {
            // it slumps forward onto the right arm; the whole body trembles
            float b = smooth(Mth.clamp((ticks - DEATH_KNEE_END) / 70f, 0f, 1f));
            tp += 0.4f * b;
            hp += 0.35f * b;
            ra += 0.45f * b;
            la += 0.2f * b;
            sp += 0.15f * b;
            bb += -1.5f * b;
            tp += Mth.sin(ticks * 0.9f) * 0.02f * b;
        }
        if (ticks > DEATH_FALL_START) {
            // the arm gives way: it topples forward, faster and faster; everything unfolds into a
            // face-down sprawl - legs straight, arms along the sides - and the body comes to rest on
            // the ground rather than in it
            float f = Mth.clamp((ticks - DEATH_FALL_START) / (float) (DEATH_FALL_HIT - DEATH_FALL_START), 0f, 1f);
            fall = -80f * Mth.DEG_TO_RAD * f * f;
            if (ticks > DEATH_FALL_HIT) {
                float after = Mth.clamp((ticks - DEATH_FALL_HIT) / 24f, 0f, 1f);
                fall += 3f * Mth.DEG_TO_RAD * Mth.sin(after * Mth.PI);
            }
            float u = smooth(f);
            float keep = 1f - u;
            tp *= keep; hp *= keep; bb *= keep; la *= keep; ra *= keep; ll *= keep; rl *= keep; dr *= keep;
            sp = sp * keep + 0.3f * u;
            lift = 0.13f * height * f * f;
        }
        if (ticks > DEATH_LYING) {
            // lying there: the last tremors as it comes apart
            fall += Mth.sin(ticks * 0.6f) * 0.004f;
        }
        torsoPitch += tp; headPitch += hp; bob += bb;
        leftArm += la; rightArm += ra; armSpread += sp;
        leftLeg += ll; rightLeg += rl; drop += dr;
    }

    /** The tick of the death at which a part comes apart into rubble (the torso last, at the final burst). */
    public static int crumbleTick(PartDef.Kind kind) {
        return switch (kind) {
            case LEFT_LEG -> 380;
            case RIGHT_LEG -> 398;
            case LEFT_ARM -> 416;
            case RIGHT_ARM -> 434;
            case HEAD -> 452;
            case TORSO -> DEATH_FINAL;
        };
    }

    public static boolean crumbled(PartDef.Kind kind, float deathTicks) {
        return deathTicks >= crumbleTick(kind);
    }

    /**
     * Carries a body-space point through this pose the way ColossusRenderer carries the part's
     * vertices: into the part's pivot space, the part's own rotation (limb swing about X, the arm
     * spread about Z, the head's yaw and pitch), then - for the torso and everything riding on it -
     * the offset from the hips, the torso's pitch and twist and the bob; legs only swing and stand
     * where they are. The point is changed in place. The body yaw is not applied here.
     */
    public void transform(double[] v, PartDef def, PartDef torso) {
        boolean leg = def.isLeg();
        float lift = leg ? 0f : bob;
        v[0] -= def.px; v[1] -= def.py; v[2] -= def.pz;
        switch (def.kind) {
            case HEAD -> { rotX(v, -headPitch); rotY(v, -headYaw); }
            case TORSO -> { }
            case LEFT_ARM, RIGHT_ARM -> {
                rotX(v, limbAngle(def.kind));
                rotZ(v, def.kind == PartDef.Kind.RIGHT_ARM ? armSpread : -armSpread);
            }
            default -> rotX(v, limbAngle(def.kind));
        }
        if (torso != null && !leg) {
            v[0] += def.px - torso.px; v[1] += def.py - torso.py; v[2] += def.pz - torso.pz;
            rotX(v, -torsoPitch);
            rotY(v, torsoYaw);
            v[0] += torso.px; v[1] += torso.py + lift; v[2] += torso.pz;
        } else {
            v[0] += def.px; v[1] += def.py + lift; v[2] += def.pz;
        }
    }

    /** Right-handed rotation about +X (the same sense as Axis.XP.rotation). */
    public static void rotX(double[] v, double a) {
        if (a == 0) return;
        double c = Math.cos(a), s = Math.sin(a);
        double y = v[1] * c - v[2] * s, z = v[1] * s + v[2] * c;
        v[1] = y; v[2] = z;
    }

    public static void rotY(double[] v, double a) {
        if (a == 0) return;
        double c = Math.cos(a), s = Math.sin(a);
        double x = v[0] * c + v[2] * s, z = -v[0] * s + v[2] * c;
        v[0] = x; v[2] = z;
    }

    public static void rotZ(double[] v, double a) {
        if (a == 0) return;
        double c = Math.cos(a), s = Math.sin(a);
        double x = v[0] * c - v[1] * s, y = v[0] * s + v[1] * c;
        v[0] = x; v[1] = y;
    }

    public float limbAngle(PartDef.Kind kind) {
        switch (kind) {
            case LEFT_LEG: return leftLeg;
            case RIGHT_LEG: return rightLeg;
            case LEFT_ARM: return leftArm;
            case RIGHT_ARM: return rightArm;
            default: return 0f;
        }
    }

    private static float smooth(float t) {
        return t * t * (3f - 2f * t);
    }
}
