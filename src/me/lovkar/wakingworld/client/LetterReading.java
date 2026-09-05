package me.lovkar.wakingworld.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where the voice is in the letter: the words of the letter laid against the sound, so the screen
 * can mark the word being read. Gemini gives no word timings, so we find them ourselves - the
 * pauses in the sound cut it into phrases, the words are dealt out over the phrases (each word's
 * share of time grows with its letters and syllables; a phrase likes to end on punctuation and
 * dislikes swallowing a full stop), and inside a phrase each word takes its share.
 * <p>
 * Checked against a speech recogniser's word times on two real letters (a quick child's voice with
 * whole clauses between pauses, a slow monk's with a pause at nearly every word): the median word
 * is within 0.1-0.15 s, nineteen in twenty within 0.3-0.6 s. The highlight leads the ear by an
 * eighth of a second, which reads as "with" it, and lets go in the pauses.
 */
public final class LetterReading {
    /** A word as the screen draws it: on which page, its box, and the text. */
    public record Word(int page, int x0, int x1, int y, String text) {
        private static final Pattern VOWELS = Pattern.compile("[aeiouy]+");

        /** Its share of a phrase's time: a floor every word gets, plus its letters and syllables. */
        int weight() {
            int letters = 0;
            for (int i = 0; i < text.length(); i++) if (Character.isLetterOrDigit(text.charAt(i))) letters++;
            int syllables = 0;
            Matcher m = VOWELS.matcher(text.toLowerCase());
            while (m.find()) syllables++;
            return 4 + letters + syllables;
        }

        /** Ends a sentence - the voice all but always pauses here. */
        boolean sentence() {
            String t = text;
            while (!t.isEmpty() && "\"')]".indexOf(t.charAt(t.length() - 1)) >= 0) t = t.substring(0, t.length() - 1);
            return !t.isEmpty() && ".!?:".indexOf(t.charAt(t.length() - 1)) >= 0;
        }

        /** Ends a clause - a pause is likely, not sure. */
        boolean clause() {
            return !text.isEmpty() && ",;-—…".indexOf(text.charAt(text.length() - 1)) >= 0;
        }
    }

    private static final int RATE = 16000;
    private static final int FRAME = RATE / 50; // 20 ms
    private static final double LEAD = 0.12;
    // the last word of a phrase is drawn out, the first comes in quick
    private static final double FINAL_STRETCH = 1.6, FIRST_STRETCH = 0.8, HOLD = 0.25;
    // the prices, in squared seconds of misfit: a phrase ending mid-clause, at a comma, and a full stop inside a phrase
    private static final double NO_PUNCTUATION = 0.4, CLAUSE = 0.01, SWALLOWED_STOP = 0.3;

    private final List<Word> words;
    private final double[] start, end;

    private LetterReading(List<Word> words, double[] start, double[] end) {
        this.words = words;
        this.start = start;
        this.end = end;
    }

    public List<Word> words() {
        return words;
    }

    /** The word being read at {@code t} seconds into the sound, or -1 in a pause, before the first and after the last. */
    public int wordAt(double t) {
        if (words.isEmpty()) return -1;
        t += LEAD;
        if (t < start[0]) return -1;
        int lo = 0, hi = start.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (start[mid] <= t) lo = mid;
            else hi = mid - 1;
        }
        return t <= end[lo] ? lo : -1;
    }

    /** Lays the words against 16 kHz 16-bit mono sound. */
    public static LetterReading align(byte[] pcm, List<Word> words) {
        int n = words.size();
        double total = pcm.length / 2.0 / RATE;
        if (n == 0) return new LetterReading(words, new double[0], new double[0]);
        List<double[]> segs = segments(pcm);
        double[] pre = new double[n + 1];
        int[] stops = new int[n + 1]; // full stops among the first i words
        for (int i = 0; i < n; i++) {
            pre[i + 1] = pre[i] + words.get(i).weight();
            stops[i + 1] = stops[i] + (words.get(i).sentence() ? 1 : 0);
        }
        double[] start = new double[n], end = new double[n];
        if (segs.isEmpty()) {
            for (int i = 0; i < n; i++) {
                start[i] = total * pre[i] / pre[n];
                end[i] = total * pre[i + 1] / pre[n];
            }
            return new LetterReading(words, start, end);
        }
        // never more phrases than words: close the smallest gaps first
        while (segs.size() > n) {
            int at = 0;
            double best = Double.MAX_VALUE;
            for (int i = 0; i + 1 < segs.size(); i++) {
                double gap = segs.get(i + 1)[0] - segs.get(i)[1];
                if (gap < best) {
                    best = gap;
                    at = i;
                }
            }
            segs.set(at, new double[]{segs.get(at)[0], segs.get(at + 1)[1]});
            segs.remove(at + 1);
        }
        int m = segs.size();
        double spoken = 0;
        for (double[] s : segs) spoken += s[1] - s[0];
        double k = spoken / pre[n]; // seconds per unit of weight
        // deal the words out over the phrases: least misfit in length, boundaries drawn to punctuation
        double[][] best = new double[m][n];
        int[][] from = new int[m][n];
        for (double[] row : best) Arrays.fill(row, Double.MAX_VALUE);
        for (int s = 0; s < m; s++) {
            double dur = segs.get(s)[1] - segs.get(s)[0];
            for (int b = s; b <= n - (m - s); b++) {
                int aMin = s == 0 ? 0 : s, aMax = s == 0 ? 0 : b;
                double boundary = b == n - 1 ? 0 : words.get(b).sentence() ? 0 : words.get(b).clause() ? CLAUSE : NO_PUNCTUATION;
                for (int a = aMin; a <= aMax; a++) {
                    double before = s == 0 ? 0 : best[s - 1][a - 1];
                    if (before == Double.MAX_VALUE) continue;
                    double e = Math.abs(dur - k * (pre[b + 1] - pre[a]));
                    double misfit = e < 0.5 ? e * e : 0.25 + 0.5 * (e - 0.5); // a big misfit counts once, not squared
                    double c = before + misfit + boundary + SWALLOWED_STOP * (stops[b] - stops[a]);
                    if (c < best[s][b]) {
                        best[s][b] = c;
                        from[s][b] = a;
                    }
                }
            }
        }
        // walk the choice back and place the words inside their phrases: each takes its share of the
        // phrase's time, the last drawn out and the first quick; the highlight lets go a little after the phrase
        int b = n - 1;
        for (int s = m - 1; s >= 0; s--) {
            int a = from[s][b];
            double s0 = segs.get(s)[0], s1 = segs.get(s)[1], dur = s1 - s0;
            double[] share = new double[b - a + 1];
            double sum = 0;
            for (int i = a; i <= b; i++) {
                double w = words.get(i).weight();
                if (i == b) w *= FINAL_STRETCH;
                if (i == a) w *= FIRST_STRETCH;
                share[i - a] = w;
                sum += w;
            }
            double at = s0;
            for (int i = a; i <= b; i++) {
                start[i] = at;
                at += dur * share[i - a] / sum;
                end[i] = i == b ? s1 + HOLD : at;
            }
            b = a - 1;
        }
        return new LetterReading(words, start, end);
    }

    /** The stretches of speech between the pauses, in seconds: [from, to] each. */
    static List<double[]> segments(byte[] pcm) {
        int frames = pcm.length / 2 / FRAME;
        if (frames == 0) return new ArrayList<>();
        double[] rms = new double[frames];
        for (int f = 0; f < frames; f++) {
            double sum = 0;
            int at = f * FRAME * 2;
            for (int i = 0; i < FRAME; i++) {
                int s = (short) ((pcm[at + 2 * i] & 0xFF) | (pcm[at + 2 * i + 1] << 8));
                sum += (double) s * s;
            }
            rms[f] = Math.sqrt(sum / FRAME);
        }
        double[] sorted = rms.clone();
        Arrays.sort(sorted);
        double loud = sorted[(int) (frames * 0.95)];
        double thresh = Math.max(loud * 0.03, 30);
        boolean[] speech = new boolean[frames];
        for (int f = 0; f < frames; f++) speech[f] = rms[f] >= thresh;
        // the little gaps inside words are not pauses
        int minGap = 7; // 140 ms
        for (int f = 0; f < frames; ) {
            if (speech[f]) {
                f++;
                continue;
            }
            int g = f;
            while (g < frames && !speech[g]) g++;
            if (f > 0 && g < frames && g - f < minGap) Arrays.fill(speech, f, g, true);
            f = g;
        }
        List<double[]> segs = new ArrayList<>();
        for (int f = 0; f < frames; ) {
            if (!speech[f]) {
                f++;
                continue;
            }
            int g = f;
            while (g < frames && speech[g]) g++;
            if (g - f >= 6) segs.add(new double[]{f * 0.02, g * 0.02}); // a click or a breath is not a word
            f = g;
        }
        return segs;
    }
}
