package battleship.belief;

import battleship.engine.Config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Precomputed tables used by belief states (internal: you do not need this class).
 *
 * Each state of a belief state (one possible state of the enemy grid) is stored in two parts:
 * <ul>
 *   <li>a 64-bit key describing the enemy fleet: for each ship its position index and its damaged squares
 *       (a damaged ship cannot move, and a ship is sunk when all its squares are damaged);</li>
 *   <li>a 128-bit set (two longs lo/hi) of the possible positions of the enemy sonar boat for that fleet.</li>
 * </ul>
 * Squares are numbered row * 7 + col and handled as 64-bit masks.
 */
final class BeliefModel {
    final Config cfg;
    final int n, cells, nShips;
    final int[] len;

    // ---- fleet placements, per ship index
    /** plMask[s][p]: squares of ship s at position p. */
    final long[][] plMask;
    /** plCell[s][p][i]: square of the i-th part of ship s at position p. */
    final int[][][] plCell;
    /** plNext[s][p][d]: position after moving backward (d=0) or forward (d=1), -1 if outside the grid. */
    final int[][][] plNext;
    /** plIndex[s][square][h]: position index of ship s starting at square, horizontal (1) or not (0); -1 if none. */
    final int[][][] plIndex;

    // ---- key layout
    final int[] posShift, posBits, dmgShift;

    // ---- the enemy sonar boat
    final int nSonar;
    /** sbMask[p]: squares of the sonar boat at position p. */
    final long[] sbMask;
    /** Positions of the sonar boat covering each square (two 64-bit halves). */
    final long[] coverLo, coverHi;
    /** Positions covering each square, as a list. */
    final int[][] coverList;
    /** sbNext[p][d]: position of the sonar boat after moving backward (d=0) / forward (d=1), -1 if impossible. */
    final int[][] sbNext;
    /** Number of boats to sink to win: the fleet and the sonar boat. */
    final int toSink;

    // ---- sonar
    /** Squares scanned by a sonar centred on each square. */
    final long[] window;
    /** windowCells[c]: list of squares of the window (used to compress patterns into small integers). */
    final int[][] windowCells;
    /** Sonar-boat positions grouped by the part of the boat that lies inside each window. */
    final Group[][] groups;

    static final class Group {
        long lo, hi;      // positions of the group
        long boatPat;     // squares of the window covered by the boat (0 for the group outside the window)
    }

    private static BeliefModel instance;

    /** The model of the (fixed) rules, built once. */
    static synchronized BeliefModel get(Config cfg) {
        if (instance == null) instance = new BeliefModel(cfg.copy()); // a private copy of the rules
        return instance;
    }

    private BeliefModel(Config cfg) {
        this.cfg = cfg;
        n = cfg.size;
        cells = n * n;
        nShips = cfg.fleet.length;
        len = cfg.fleet.clone();

        plMask = new long[nShips][];
        plCell = new int[nShips][][];
        plNext = new int[nShips][][];
        plIndex = new int[nShips][cells][2];
        posShift = new int[nShips];
        posBits = new int[nShips];
        dmgShift = new int[nShips];
        int shift = 0;
        for (int s = 0; s < nShips; s++) {
            List<int[]> pl = placements(len[s]);
            int np = pl.size();
            plMask[s] = new long[np];
            plCell[s] = new int[np][len[s]];
            plNext[s] = new int[np][2];
            for (int[] row : plIndex[s]) { row[0] = -1; row[1] = -1; }
            for (int p = 0; p < np; p++) {
                int[] q = pl.get(p);
                plIndex[s][q[0] * n + q[1]][q[2]] = p;
                for (int i = 0; i < len[s]; i++) {
                    int r = q[2] == 1 ? q[0] : q[0] + i, c = q[2] == 1 ? q[1] + i : q[1];
                    plCell[s][p][i] = r * n + c;
                    plMask[s][p] |= 1L << (r * n + c);
                }
            }
            for (int p = 0; p < np; p++) {
                int[] q = pl.get(p);
                for (int d = 0; d < 2; d++) {
                    int dr = q[2] == 1 ? 0 : (d == 1 ? 1 : -1), dc = q[2] == 1 ? (d == 1 ? 1 : -1) : 0;
                    int r = q[0] + dr, c = q[1] + dc;
                    plNext[s][p][d] = (r >= 0 && c >= 0 && r < n && c < n) ? plIndex[s][r * n + c][q[2]] : -1;
                }
            }
            posBits[s] = 32 - Integer.numberOfLeadingZeros(Math.max(1, np - 1));
            posShift[s] = shift;
            shift += posBits[s];
            dmgShift[s] = shift;
            shift += len[s];
        }

        List<int[]> sp = placements(cfg.sonarBoatLength);
        nSonar = sp.size();
        sbMask = new long[nSonar];
        coverLo = new long[cells];
        coverHi = new long[cells];
        List<List<Integer>> cover = new ArrayList<>();
        for (int c = 0; c < cells; c++) cover.add(new ArrayList<>());
        for (int p = 0; p < nSonar; p++) {
            int[] q = sp.get(p);
            for (int i = 0; i < cfg.sonarBoatLength; i++) {
                int r = q[2] == 1 ? q[0] : q[0] + i, c = q[2] == 1 ? q[1] + i : q[1];
                int sq = r * n + c;
                sbMask[p] |= 1L << sq;
                if (p < 64) coverLo[sq] |= 1L << p; else coverHi[sq] |= 1L << (p - 64);
                cover.get(sq).add(p);
            }
        }
        toSink = nShips + 1;
        sbNext = new int[nSonar][2];
        for (int p = 0; p < nSonar; p++) {
            int[] q = sp.get(p);
            for (int d = 0; d < 2; d++) {
                int r = q[0] + (q[2] == 1 ? 0 : (d == 1 ? 1 : -1)), c = q[1] + (q[2] == 1 ? (d == 1 ? 1 : -1) : 0);
                sbNext[p][d] = -1;
                if (r < 0 || c < 0 || r >= n || c >= n) continue;
                for (int p2 = 0; p2 < nSonar; p2++) {
                    int[] q2 = sp.get(p2);
                    if (q2[0] == r && q2[1] == c && q2[2] == q[2]) sbNext[p][d] = p2;
                }
            }
        }
        coverList = new int[cells][];
        for (int c = 0; c < cells; c++) coverList[c] = cover.get(c).stream().mapToInt(Integer::intValue).toArray();

        window = new long[cells];
        windowCells = new int[cells][];
        groups = new Group[cells][];
        for (int center = 0; center < cells; center++) {
            List<Integer> wc = new ArrayList<>();
            for (int sq = 0; sq < cells; sq++)
                if (cfg.inSonarArea(center / n, center % n, sq / n, sq % n)) {
                    window[center] |= 1L << sq;
                    wc.add(sq);
                }
            windowCells[center] = wc.stream().mapToInt(Integer::intValue).toArray();
            Map<Long, Group> byPat = new LinkedHashMap<>();
            for (int p = 0; p < nSonar; p++) {
                long pat = sbMask[p] & window[center];
                Group g = byPat.computeIfAbsent(pat, k -> new Group());
                g.boatPat = pat;
                if (p < 64) g.lo |= 1L << p; else g.hi |= 1L << (p - 64);
            }
            groups[center] = byPat.values().toArray(new Group[0]);
        }
    }

    /** All positions {row, col, horizontal?1:0} of a boat of the given length. */
    private List<int[]> placements(int l) {
        List<int[]> res = new ArrayList<>();
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++) {
                if (c + l <= n) res.add(new int[]{r, c, 1});
                if (l > 1 && r + l <= n) res.add(new int[]{r, c, 0});
            }
        return res;
    }

    // ------------------------------------------------------------------ key access

    int pos(long key, int s) { return (int) ((key >>> posShift[s]) & ((1L << posBits[s]) - 1)); }
    int dmg(long key, int s) { return (int) ((key >>> dmgShift[s]) & ((1L << len[s]) - 1)); }
    boolean sunk(long key, int s) { return dmg(key, s) == (1 << len[s]) - 1; }

    long withPos(long key, int s, int p) {
        long m = ((1L << posBits[s]) - 1) << posShift[s];
        return (key & ~m) | ((long) p << posShift[s]);
    }

    long withDmg(long key, int s, int d) {
        long m = ((1L << len[s]) - 1) << dmgShift[s];
        return (key & ~m) | ((long) d << dmgShift[s]);
    }

    /** Squares occupied by the enemy fleet in this state. */
    long occupancy(long key) {
        long occ = 0;
        for (int s = 0; s < nShips; s++) occ |= plMask[s][pos(key, s)];
        return occ;
    }

    /** Pattern of the bits of mask inside the window of the given center, compressed into a small integer. */
    int compress(long mask, int center) {
        int[] wc = windowCells[center];
        int res = 0;
        for (int i = 0; i < wc.length; i++)
            if ((mask >>> wc[i] & 1L) != 0) res |= 1 << i;
        return res;
    }
}
