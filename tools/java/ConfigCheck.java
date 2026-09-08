import com.electronwill.nightconfig.core.UnmodifiableConfig;
import me.lovkar.wakingworld.WakingConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The config spec, without a game. Building it is where an unbalanced push/pop, a duplicate key or a
 * default outside its own range blows up, and none of those show themselves until a world is loaded.
 * Prints every path in both specs so a section that moved is visible in the diff.
 */
public final class ConfigCheck {
    public static void main(String[] args) {
        List<String> server = walk(WakingConfig.SPEC.getSpec());
        List<String> client = walk(WakingConfig.CLIENT_SPEC.getSpec());
        System.out.println("server config: " + server.size() + " settings");
        for (String s : server) System.out.println("  " + s);
        System.out.println("client config: " + client.size() + " settings");
        for (String s : client) System.out.println("  " + s);

        // the parts of the mod must all be there, and under [features]
        String[] parts = {"features.colossi", "features.titan", "features.cataclysms",
                "features.kingdoms", "features.ruins", "features.namedLands"};
        for (String p : parts) {
            if (!server.contains(p)) throw new AssertionError("missing: " + p);
        }
        if (server.contains("lands.namedLands")) throw new AssertionError("namedLands is in two places");
        if (server.contains("cataclysms.greeting")) throw new AssertionError("the greeting is not a cataclysm");
        if (!server.contains("story.greeting")) throw new AssertionError("missing: story.greeting");
        // nothing is unloaded-safe by accident: with no config loaded every part answers false, and
        // every switch under a part answers false with it
        if (WakingConfig.colossi() || WakingConfig.cataclysms() || WakingConfig.kingdoms()
                || WakingConfig.ruins() || WakingConfig.titan() || WakingConfig.namedLands()) {
            throw new AssertionError("a part answered true with no config loaded");
        }
        if (WakingConfig.tornadoes() || WakingConfig.volcanoes() || WakingConfig.bloodMoons()
                || WakingConfig.earthquakes() || WakingConfig.meteorShowers() || WakingConfig.unrest()
                || WakingConfig.blight() || WakingConfig.omens() || WakingConfig.bloodMoonColossi()) {
            throw new AssertionError("a cataclysm answered true with no config loaded");
        }
        if (WakingConfig.answerChance() != 0.0) throw new AssertionError("answerChance with no config loaded");
        System.out.println("config: OK");
    }

    private static List<String> walk(UnmodifiableConfig config) {
        List<String> out = new ArrayList<>();
        walk(config, "", out);
        return out;
    }

    private static void walk(UnmodifiableConfig config, String prefix, List<String> out) {
        for (Map.Entry<String, Object> e : config.valueMap().entrySet()) {
            String path = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            if (e.getValue() instanceof UnmodifiableConfig sub) walk(sub, path, out);
            else out.add(path);
        }
    }
}
