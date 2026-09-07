package me.lovkar.wakingworld.client;

import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.lang.reflect.Method;

/**
 * Puts a named land on whatever map mod the player happens to run.
 *
 * <p>Neither mod is a dependency, at compile time or at run time: everything here is reached by
 * reflection inside a try/catch, and a player with no map mod - or with a version that has moved
 * its classes - simply gets nothing. That is also why each lookup is done once and remembered as
 * "not here" on the first failure: a missing class must cost one failed lookup, not one per land
 * for the rest of the session.</p>
 *
 * <p>JourneyMap's call is taken from the jar shipped for 1.21.1 (6.0.7):
 * {@code ClientWaypointFactoryImpl.createWaypoint(modId, pos, name, dimension, colour)}. Xaero's is
 * written to the shape its API has had for years but is <b>unverified</b> - it is not installed on
 * the machine this was written on, so it has never actually run. It fails the same way a missing
 * mod does, so an error in it costs a waypoint and nothing else.</p>
 */
public final class LandWaypoints {
    private static final int GOLD = 0xE2B24A;

    private static Method journeyMap;
    private static boolean journeyMapLooked;
    private static boolean xaeroLooked, xaeroHere;

    private LandWaypoints() {
    }

    /** A land, on the player's map. Never throws. */
    public static void place(String name, int x, int y, int z) {
        if (name == null || name.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        String dimension = mc.level.dimension().location().toString();
        BlockPos at = new BlockPos(x, y, z);
        journeyMap(name, at, dimension);
        xaero(name, at);
    }

    // ---- JourneyMap ---------------------------------------------------------------------------

    private static void journeyMap(String name, BlockPos at, String dimension) {
        if (!journeyMapLooked) {
            journeyMapLooked = true;
            try {
                Class<?> factory = Class.forName("journeymap.api.client.waypoint.ClientWaypointFactoryImpl");
                journeyMap = factory.getMethod("createWaypoint", String.class, BlockPos.class, String.class, String.class, int.class);
                WakingWorld.LOGGER.info("lands: JourneyMap is here - named lands will get a waypoint");
            } catch (Throwable t) {
                journeyMap = null;                       // not installed, or it has moved: fine
            }
        }
        if (journeyMap == null) return;
        try {
            journeyMap.invoke(null, WakingWorld.MODID, at, name, dimension, GOLD);
        } catch (Throwable t) {
            WakingWorld.LOGGER.warn("lands: JourneyMap would not take the waypoint: {}", t.toString());
            journeyMap = null;                           // once is enough - do not ask again this session
        }
    }

    // ---- Xaero's Minimap / World Map ----------------------------------------------------------

    /**
     * Xaero keeps waypoints in a set per world: session -> waypoints manager -> current world ->
     * current set -> a list of {@code Waypoint}. The constructor used here is the long-standing
     * (x, y, z, name, initials, colour) one.
     */
    private static void xaero(String name, BlockPos at) {
        if (!xaeroLooked) {
            xaeroLooked = true;
            try {
                Class.forName("xaero.common.minimap.waypoints.Waypoint");
                xaeroHere = true;
                WakingWorld.LOGGER.info("lands: Xaero's is here - named lands will get a waypoint");
            } catch (Throwable t) {
                xaeroHere = false;
            }
        }
        if (!xaeroHere) return;
        try {
            Object session = Class.forName("xaero.common.minimap.waypoints.WaypointsManager")
                    .getMethod("getCurrentWorld").invoke(minimapSession());
            if (session == null) return;
            Object set = session.getClass().getMethod("getCurrentSet").invoke(session);
            if (set == null) return;
            @SuppressWarnings("unchecked")
            java.util.List<Object> list = (java.util.List<Object>) set.getClass().getMethod("getList").invoke(set);
            Class<?> wp = Class.forName("xaero.common.minimap.waypoints.Waypoint");
            Object point = wp.getConstructor(int.class, int.class, int.class, String.class, String.class, int.class)
                    .newInstance(at.getX(), at.getY(), at.getZ(), name, initials(name), 4);
            list.add(point);
        } catch (Throwable t) {
            WakingWorld.LOGGER.warn("lands: Xaero's would not take the waypoint: {}", t.toString());
            xaeroHere = false;
        }
    }

    private static Object minimapSession() throws Exception {
        Class<?> hud = Class.forName("xaero.common.HudMod");
        Object instance = hud.getField("INSTANCE").get(null);
        return hud.getMethod("getMinimapSession").invoke(instance);
    }

    /** The two or three letters Xaero draws when a waypoint is too far to write out. */
    private static String initials(String name) {
        StringBuilder sb = new StringBuilder();
        for (String word : name.split("\\s+")) {
            if (!word.isEmpty() && sb.length() < 3) sb.append(Character.toUpperCase(word.charAt(0)));
        }
        return sb.isEmpty() ? "L" : sb.toString();
    }
}
