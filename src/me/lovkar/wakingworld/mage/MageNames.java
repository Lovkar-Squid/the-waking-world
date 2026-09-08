package me.lovkar.wakingworld.mage;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

/**
 * The name of the man in the tower, from where his tower stands - so he is the same man every time
 * you come back to him, and a different one in the next wood, without anything being written down.
 */
public final class MageNames {
    private static final String[] FIRST = {
            "Verrath", "Ossric", "Calivan", "Mordraine", "Sevren", "Thalgor", "Ilmaric", "Corvane",
            "Ashgar", "Neverin", "Dolmath", "Kestrivan", "Varnu", "Halloth", "Ordwin", "Sythe"};
    private static final String[] STYLE = {
            "the Patient", "of the Long Wait", "the Unasked", "who Counts the Days", "the Sleepless",
            "of the Black Study", "the Unwelcome", "who Reads the Weather", "the Twice-Buried",
            "of the Cold Lamp", "the Unforgiven", "who Answers Anyway"};

    private MageNames() {
    }

    public static String of(BlockPos tower) {
        RandomSource rnd = RandomSource.create(tower.asLong() * 0x9E3779B97F4A7C15L);
        return FIRST[rnd.nextInt(FIRST.length)] + " " + STYLE[rnd.nextInt(STYLE.length)];
    }
}
