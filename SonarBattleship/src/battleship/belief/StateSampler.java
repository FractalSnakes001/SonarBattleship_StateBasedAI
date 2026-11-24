package battleship.belief;

import battleship.engine.Action;
import battleship.engine.Board;
import battleship.engine.Config;
import battleship.engine.Placement;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Draws states of a belief state at random, all the states being equally likely: a possible enemy grid, with the
 * positions and the damaged squares of his boats. Useful for methods that simulate games on possible enemy grids,
 * such as Monte Carlo tree search. Create one StateSampler per belief state (its construction takes a little time),
 * then draw as many states as needed.
 */
public final class StateSampler {
    private final BeliefState b;
    private final double[] cumulative;

    public StateSampler(BeliefState b) {
        this.b = b;
        cumulative = new double[b.size];
        double total = 0;
        for (int i = 0; i < b.size; i++) {
            total += Long.bitCount(b.lo[i]) + Long.bitCount(b.hi[i]);
            cumulative[i] = total;
        }
    }

    /**
     * A state drawn at random, as a Board: the enemy boats (boat(0) to boat(2) for the ships, boat(3) for the sonar
     * boat), with their damaged squares.
     */
    public Board sampleBoard(Random rnd) {
        long[] s = sample(rnd);
        List<Placement.Spot> fleet = new ArrayList<>();
        Placement.Spot sonar = null;
        for (int k = 0; k < 4; k++) {
            long m = s[k];
            int first = Long.numberOfTrailingZeros(m);
            boolean horizontal = (m >>> (first + 1) & 1) != 0 && first % 7 != 6;
            Placement.Spot spot = new Placement.Spot(first / 7, first % 7, horizontal);
            if (k < 3) fleet.add(spot); else sonar = spot;
        }
        Board board = Board.preview(new Config(), fleet, sonar);
        for (int k = 0; k < 4; k++)
            for (long d = s[4 + k]; d != 0; d &= d - 1) {
                int x = Long.numberOfTrailingZeros(d);
                board = board.afterAttack(Action.fire(x / 7, x % 7));
            }
        return board;
    }

    /** True if there is no state to draw (the belief state is empty or won). */
    public boolean isEmpty() { return b.size == 0 || b.won; }

    /**
     * A state drawn at random, in a compact form (much faster than sampleBoard): the squares of the 3 ships and of the sonar boat (indexes 0 to 3), then their damaged
     * squares (indexes 4 to 7). Squares are numbered row * 7 + col.
     */
    public long[] sample(Random rnd) {
        double u = rnd.nextDouble() * cumulative[b.size - 1];
        int lo = 0, hi = b.size - 1;
        while (lo < hi) { // first fleet whose cumulative count exceeds u
            int mid = (lo + hi) >>> 1;
            if (cumulative[mid] > u) hi = mid; else lo = mid + 1;
        }
        BeliefModel m = b.m;
        long key = b.keys[lo];
        int nl = Long.bitCount(b.lo[lo]), nh = Long.bitCount(b.hi[lo]);
        int k = rnd.nextInt(nl + nh), p;
        if (k < nl) p = nthBit(b.lo[lo], k);
        else p = 64 + nthBit(b.hi[lo], k - nl);
        long[] res = new long[8];
        for (int s = 0; s < m.nShips; s++) res[s] = m.plMask[s][m.pos(key, s)];
        res[3] = m.sbMask[p];
        for (int s = 0; s < 4; s++) res[4 + s] = res[s] & b.hits;
        return res;
    }

    // ---- reading a state drawn by sample (row and column indexes between 0 and 6)

    /** True if square (row, col) holds a boat (damaged or not) in the state s drawn by sample. */
    public static boolean hasBoat(long[] s, int row, int col) {
        return boatAt(s, row, col) >= 0;
    }

    /** The index of the boat on square (row, col) in the state s (0 to 2: ships, 3: sonar boat), or -1 if none. */
    public static int boatAt(long[] s, int row, int col) {
        long bit = 1L << (row * 7 + col);
        for (int k = 0; k < 4; k++) if ((s[k] & bit) != 0) return k;
        return -1;
    }

    /** True if square (row, col) holds a damaged part of a boat in the state s. */
    public static boolean isDamaged(long[] s, int row, int col) {
        long bit = 1L << (row * 7 + col);
        return ((s[4] | s[5] | s[6] | s[7]) & bit) != 0;
    }

    /** True if boat i (0 to 2: ships, 3: sonar boat) is sunk in the state s (all its squares are damaged). */
    public static boolean isSunk(long[] s, int i) {
        return s[4 + i] == s[i];
    }

    private static int nthBit(long x, int n) {
        for (int i = 0; i < n; i++) x &= x - 1;
        return Long.numberOfTrailingZeros(x);
    }
}
