package me.lovkar.wakingworld.body;

import me.lovkar.wakingworld.supporter.SupporterTiers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Locale;

/**
 * The looks a supporter can give the colossi they wake - a Patreon cosmetic, nothing more. A style
 * swaps the blocks the giant is built from and the way its glow is laid out; the silhouette stays
 * the land's own, the hit boxes stay where they were, and nothing about the fight changes. The
 * catalogue is mirrored on the supporter service, which is where a choice is made and checked
 * against the tier that paid for it; the server applies what the list says when the rite wakes the
 * giant, and every client just builds the synced palette like any other. The Titan is never
 * dressed - the last one looks as it looks.
 */
public enum ColossusStyle {
    NONE("none", 0, null, null, null, true, 0),
    /** The Sentinel: a war machine of blackstone and iron, glowing seams at every joint, a visor for eyes. */
    SENTINEL("sentinel", 2, new Object[][]{
            {Blocks.POLISHED_BLACKSTONE, 26}, {Blocks.POLISHED_BLACKSTONE_BRICKS, 16}, {Blocks.BLACKSTONE, 12},
            {Blocks.DEEPSLATE_TILES, 12}, {Blocks.POLISHED_DEEPSLATE, 14}, {Blocks.IRON_BLOCK, 3},
            {Blocks.CHISELED_POLISHED_BLACKSTONE, 6}, {Blocks.GILDED_BLACKSTONE, 3}},
            Blocks.SEA_LANTERN, Blocks.SEA_LANTERN, false, 0),
    /** The Eldest: a shrine guardian of deepslate, its old carvings picked out in gold light, a little moss in the cracks. */
    ELDEST("eldest", 2, new Object[][]{
            {Blocks.DEEPSLATE, 24}, {Blocks.POLISHED_DEEPSLATE, 16}, {Blocks.DEEPSLATE_BRICKS, 16}, {Blocks.DEEPSLATE_TILES, 12},
            {Blocks.COBBLED_DEEPSLATE, 12}, {Blocks.CRACKED_DEEPSLATE_BRICKS, 6}, {Blocks.CHISELED_DEEPSLATE, 5},
            {Blocks.MOSS_BLOCK, 5}, {Blocks.GOLD_BLOCK, 3}},
            Blocks.GLOWSTONE, Blocks.GLOWSTONE, true, 0),
    /** The Seraph: sleek white plating, its cores and edges in the dark violet of crying obsidian (lit cracks on white), a visor, lit horn tips and a circlet. */
    SERAPH("seraph", 3, new Object[][]{
            {Blocks.QUARTZ_BLOCK, 26}, {Blocks.SMOOTH_QUARTZ, 22}, {Blocks.QUARTZ_BRICKS, 14}, {Blocks.CALCITE, 12},
            {Blocks.WHITE_CONCRETE, 10}, {Blocks.CHISELED_QUARTZ_BLOCK, 6}, {Blocks.QUARTZ_PILLAR, 4}, {Blocks.AMETHYST_BLOCK, 3}},
            Blocks.CRYING_OBSIDIAN, Blocks.CRYING_OBSIDIAN, false, 1);

    public final String id;
    /** The tier that unlocks it (2 = Colossus, 3 = Titan); 0 = always allowed. */
    public final int rank;
    /** {block, weight} pairs for the body; null for NONE. */
    final Object[][] body;
    public final Block core;
    public final Block eye;
    /** Whether the land's own glowing veins and chest sigil are drawn (the machine and the seraph wear straight lines instead). */
    public final boolean veins;
    /** 0 = none, 1 = the crest: the tips of the head's own horns, antlers or spikes lit, and a circlet of light round the crown. */
    public final int crest;

    ColossusStyle(String id, int rank, Object[][] body, Block core, Block eye, boolean veins, int crest) {
        this.id = id;
        this.rank = rank;
        this.body = body;
        this.core = core;
        this.eye = eye;
        this.veins = veins;
        this.crest = crest;
    }

    public static ColossusStyle byId(String id) {
        if (id == null) return null;
        String k = id.trim().toLowerCase(Locale.ROOT);
        for (ColossusStyle s : values()) if (s.id.equals(k)) return s;
        return null;
    }

    /** The style a supporter's giants wear: their choice if their tier unlocks it (the service checks the same), else none. */
    public static ColossusStyle resolve(String tier, String chosen) {
        int rank = SupporterTiers.rank(tier);
        if (rank <= 0) return NONE;
        ColossusStyle s = byId(chosen);
        return s != null && s.rank <= rank ? s : NONE;
    }
}
