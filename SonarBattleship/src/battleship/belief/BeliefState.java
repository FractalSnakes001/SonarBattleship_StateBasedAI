package battleship.belief;

import battleship.engine.Action;
import battleship.engine.Board;
import battleship.engine.Config;
import battleship.engine.Event;
import battleship.engine.FireResult;
import battleship.engine.Ship;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A belief state: the set of all the states of the enemy grid compatible with everything a player has observed so
 * far. A state fixes the position and the damaged squares of every enemy boat (the three ships and the sonar
 * boat). All the states of a belief state are considered equally likely.
 *
 * Belief states are immutable. The main operations are:
 * <ul>
 *   <li>{@link #extendsBeliefState(Action)}: RESULT of one of my attacks: one belief state per possible percept
 *       (UPDATE), with the probability of each percept;</li>
 *   <li>{@link #predict()}: PREDICT for the opponent's turn: he may have moved one of his undamaged boats by
 *       one square, or not.</li>
 * </ul>
 * My own silent moves do not change what I know about the enemy grid.
 *
 * The probability functions return 7x7 arrays: p[row][col] is the probability for the square of row `row` and
 * column `col`, as everywhere else in the engine.
 */
public final class BeliefState implements Comparable<BeliefState> {
    // percept keys of a FIRE action: type in the 3 low bits, revealed squares above
    static final long MISS = 0, HIT = 1, SUNK = 4, BOAT_SUNK = 5;

    final BeliefModel m;
    final int size;
    final long[] keys, lo, hi;
    /** All the squares where my rockets hit a boat. */
    final long hits;
    final boolean sonarOK;
    /** Number of enemy boats sunk (the sonar boat included). */
    final int sunk;
    /** Bonus points for the boats sunk so far. */
    final int bonus;
    final boolean won;
    final double nStates;
    private int hash;
    private long[][] sorted;

    private BeliefState(BeliefModel m, int size, long[] keys, long[] lo, long[] hi, long hits, boolean sonarOK,
                        int sunk, int bonus, boolean won) {
        this.m = m;
        this.size = size;
        this.keys = keys;
        this.lo = lo;
        this.hi = hi;
        this.hits = hits;
        this.sonarOK = sonarOK;
        this.sunk = sunk;
        this.bonus = bonus;
        this.won = won;
        double w = 0;
        for (int i = 0; i < size; i++) w += Long.bitCount(lo[i]) + Long.bitCount(hi[i]);
        nStates = won ? 0 : w;
    }

    // ================================================================== construction

    /** Accumulates states, merging states with the same fleet. */
    static final class Builder {
        private final BeliefModel m;
        private final LongIntMap index;
        private long[] k, l, h;
        private int n;

        Builder(BeliefModel m, int expected) {
            this.m = m;
            int cap = Math.max(16, expected);
            index = new LongIntMap(cap);
            k = new long[cap];
            l = new long[cap];
            h = new long[cap];
        }

        void add(long key, long lo, long hi) {
            if ((lo | hi) == 0) return;
            int i = index.putIfAbsent(key, n);
            if (i >= 0) {
                l[i] |= lo;
                h[i] |= hi;
                return;
            }
            if (n == k.length) {
                int cap = n * 2;
                k = Arrays.copyOf(k, cap);
                l = Arrays.copyOf(l, cap);
                h = Arrays.copyOf(h, cap);
            }
            k[n] = key;
            l[n] = lo;
            h[n] = hi;
            n++;
        }

        BeliefState build(long hits, boolean sonarOK, int sunk, int bonus, boolean won) {
            return new BeliefState(m, n, k, l, h, hits, sonarOK, sunk, bonus, won);
        }
    }

    /** Initial belief state: every possible enemy fleet, and every possible position of the enemy sonar boat. */
    public static BeliefState initial(Config cfg) {
        BeliefModel m = BeliefModel.get(cfg);
        Builder b = new Builder(m, 1 << 16);
        enumerate(m, b, 0, 0L, 0L);
        return b.build(0L, true, 0, 0, false);
    }

    private static void enumerate(BeliefModel m, Builder b, int s, long key, long occ) {
        if (s == m.nShips) {
            long l = 0, h = 0;
            for (int p = 0; p < m.nSonar; p++)
                if ((m.sbMask[p] & occ) == 0) {
                    if (p < 64) l |= 1L << p; else h |= 1L << (p - 64);
                }
            b.add(key, l, h);
            return;
        }
        for (int p = 0; p < m.plMask[s].length; p++)
            if ((m.plMask[s][p] & occ) == 0) enumerate(m, b, s + 1, m.withPos(key, s, p), occ | m.plMask[s][p]);
    }

    // ================================================================== information about the belief state

    /** My score in this belief state (points for my hits and for the boats I sank; see Game.score). */
    public int getScore() { return Long.bitCount(hits) * m.cfg.pointsPerHit + bonus; }

    /** Number of states (possible enemy grids) in this belief state. */
    public double getNbrOfStates() { return nStates; }

    /** Number of distinct possible enemy fleets (positions and damage of the three ships): internal. */
    int getNbrOfFleets() { return size; }

    /** Number of enemy boats sunk (the sonar boat included). The game is won when it reaches 4. */
    public int getNbrOfSunkBoats() { return sunk; }

    /** True if every enemy boat is sunk. */
    public boolean isWon() { return won; }

    /** True if I may still use my sonar. */
    public boolean isSonarAvailable() { return sonarOK; }

    /** True if one of my rockets hit a boat on square (r,c). */
    public boolean isHit(int r, int c) { return (hits >>> (r * m.n + c) & 1) != 0; }

    /** The attacks I can perform: FIRE on every square, and SONAR on every square if my sonar is available. */
    public List<Action> getAttacks() {
        List<Action> res = new ArrayList<>();
        for (int c = 0; c < m.cells; c++) res.add(Action.fire(c / m.n, c % m.n));
        if (sonarOK) for (int c = 0; c < m.cells; c++) res.add(Action.sonar(c / m.n, c % m.n));
        return res;
    }

    /** Probability that each square holds a part of an enemy boat (damaged or not): p[row][col]. */
    public double[][] getBoatProbabilities() { return grid(probabilities(false)); }

    /**
     * Probability that each square holds an undamaged part of an enemy boat, p[row][col]: the probability
     * that a rocket fired there gives a new hit.
     */
    public double[][] getIntactBoatProbabilities() { return grid(probabilities(true)); }

    /** Probability that each square holds a part of the enemy sonar boat: p[row][col]. */
    public double[][] getSonarBoatProbabilities() { return grid(sonarBoatProbabilities()); }

    /** Converts an array indexed by square (row * 7 + col) into a 7x7 array. */
    private double[][] grid(double[] flat) {
        double[][] g = new double[m.n][m.n];
        for (int r = 0; r < m.n; r++) System.arraycopy(flat, r * m.n, g[r], 0, m.n);
        return g;
    }

    /** Probability of the enemy sonar boat for each square, indexed by square (row * 7 + col). */
    private double[] sonarBoatProbabilities() {
        double[] cnt = new double[m.nSonar];
        for (int i = 0; i < size; i++) {
            long l = lo[i], h = hi[i];
            while (l != 0) { cnt[Long.numberOfTrailingZeros(l)]++; l &= l - 1; }
            while (h != 0) { cnt[64 + Long.numberOfTrailingZeros(h)]++; h &= h - 1; }
        }
        double[] p = new double[m.cells];
        for (int q = 0; q < m.nSonar; q++) {
            if (cnt[q] == 0) continue;
            long sq = m.sbMask[q];
            while (sq != 0) { p[Long.numberOfTrailingZeros(sq)] += cnt[q]; sq &= sq - 1; }
        }
        if (nStates > 0) for (int c = 0; c < p.length; c++) p[c] /= nStates;
        return p;
    }

    /** Probabilities indexed by square (row * 7 + col). */
    private double[] probabilities(boolean intactOnly) {
        double[] p = new double[m.cells];
        for (int i = 0; i < size; i++) {
            int w = Long.bitCount(lo[i]) + Long.bitCount(hi[i]);
            long key = keys[i];
            for (int s = 0; s < m.nShips; s++) {
                int pos = m.pos(key, s), d = m.dmg(key, s);
                for (int j = 0; j < m.len[s]; j++)
                    if (!intactOnly || (d >>> j & 1) == 0) p[m.plCell[s][pos][j]] += w;
            }
        }
        if (nStates > 0) for (int c = 0; c < p.length; c++) p[c] /= nStates;
        double[] boat = sonarBoatProbabilities();
        for (int c = 0; c < p.length; c++)
            if (!intactOnly || (hits >>> c & 1) == 0) p[c] += boat[c];
        return p;
    }

    /** The same belief state with the given availability of my sonar (set by the engine). */
    public BeliefState withSonarAvailable(boolean ok) {
        return ok == sonarOK ? this : new BeliefState(m, size, keys, lo, hi, hits, ok, sunk, bonus, won);
    }

    // ================================================================== RESULT of an attack (UPDATE)

    /**
     * RESULT(b, a) for one of my attacks (FIRE or SONAR; a silent move in the action is ignored): one belief state
     * per possible percept, with the probability of each percept.
     */
    public Result extendsBeliefState(Action a) {
        return compute(a.withoutMove(), true);
    }

    /**
     * Same as {@link #extendsBeliefState(Action)}, but the belief states themselves are not built: each branch
     * only gives its probability, its score, its number of states... (getBeliefState returns null).
     * Much faster: useful at the leaves of a search tree.
     */
    public Result extendsBeliefStateFast(Action a) {
        return compute(a.withoutMove(), false);
    }

    /** RESULT of every attack I can perform (see {@link #getAttacks()}). Slow when there are many states. */
    public Plans extendsBeliefState() {
        Plans p = new Plans();
        for (Action a : getAttacks()) p.addPlan(a, extendsBeliefState(a));
        return p;
    }

    private interface Sink {
        void add(long key, long lo, long hi);
    }

    /** Sink that only counts states. */
    private static final class Counter implements Sink {
        double nStates;

        @Override
        public void add(long key, long l, long h) { nStates += Long.bitCount(l) + Long.bitCount(h); }
    }

    private static final class BuilderSink implements Sink {
        final Builder b;

        BuilderSink(Builder b) { this.b = b; }

        @Override
        public void add(long key, long l, long h) { b.add(key, l, h); }
    }

    /** When not ALL, only the branch with this percept key is built (used to process real observations). */
    private long only = ALL;
    private static final long ALL = Long.MIN_VALUE;
    private static final Sink IGNORE = (k, l, h) -> { };

    /** The branch of RESULT(b, a) with the given percept, or null if this percept is impossible. */
    Result.Branch branch(Action a, long perceptKey) {
        only = perceptKey;
        try {
            return compute(a, true).find(perceptKey);
        } finally {
            only = ALL;
        }
    }

    private Result compute(Action a, boolean full) {
        if (won) throw new IllegalStateException("the game is won: no more attacks");
        if (a.type == Action.Type.FIRE) return fire(a.row * m.n + a.col, full);
        if (!sonarOK) throw new IllegalArgumentException("sonar not available");
        return full ? sonarFull(a.row * m.n + a.col) : sonarFast(a.row * m.n + a.col);
    }

    private Result fire(int x, boolean full) {
        Map<Long, Sink> sinks = new LinkedHashMap<>();
        long bit = 1L << x;
        for (int i = 0; i < size; i++) {
            long key = keys[i], l = lo[i], h = hi[i];
            int ship = -1, part = -1;
            for (int s = 0; s < m.nShips && ship < 0; s++) {
                int p = m.pos(key, s);
                if ((m.plMask[s][p] & bit) != 0) {
                    ship = s;
                    for (int j = 0; j < m.len[s]; j++) if (m.plCell[s][p][j] == x) part = j;
                }
            }
            if (ship >= 0) { // a ship of the fleet is on square x
                int fullDmg = (1 << m.len[ship]) - 1;
                int d = m.dmg(key, ship), nd = d | (1 << part);
                long nk = m.withDmg(key, ship, nd);
                long pk = (d != fullDmg && nd == fullDmg) ? SUNK | (m.plMask[ship][m.pos(key, ship)] << 3) : HIT;
                // states differing only by the damage of square x merge after the shot: always build them
                sink(sinks, pk, full || pk == HIT, size).add(nk, l, h);
            } else {
                long cl = l & m.coverLo[x], ch = h & m.coverHi[x];     // the sonar boat is on square x
                long rl = l & ~m.coverLo[x], rh = h & ~m.coverHi[x];   // nothing on square x
                if ((rl | rh) != 0) sink(sinks, MISS, full, size).add(key, rl, rh);
                if ((cl | ch) != 0) {
                    long sl = 0, sh = 0; // positions where this shot sinks the sonar boat
                    for (int p : m.coverList[x]) {
                        boolean in = p < 64 ? (cl >>> p & 1) != 0 : (ch >>> (p - 64) & 1) != 0;
                        if (!in) continue;
                        if (((m.sbMask[p] & ~bit) & ~hits) == 0 && (hits & bit) == 0) {
                            if (p < 64) sl |= 1L << p; else sh |= 1L << (p - 64);
                        }
                    }
                    long hl = cl & ~sl, hh = ch & ~sh;
                    if ((hl | hh) != 0) sink(sinks, HIT, true, size).add(key, hl, hh);
                    for (int p : m.coverList[x]) {
                        boolean in = p < 64 ? (sl >>> p & 1) != 0 : (sh >>> (p - 64) & 1) != 0;
                        if (!in) continue;
                        long pk = BOAT_SUNK | (m.sbMask[p] << 3);
                        sink(sinks, pk, full, size).add(key, p < 64 ? 1L << p : 0, p < 64 ? 0 : 1L << (p - 64));
                    }
                }
            }
        }
        Result r = new Result();
        for (Map.Entry<Long, Sink> e : sinks.entrySet()) {
            long pk = e.getKey();
            int type = (int) (pk & 7);
            int nsunk = sunk + (type == SUNK || type == BOAT_SUNK ? 1 : 0);
            int len = Long.bitCount(pk >>> 3);
            int nbonus = bonus + (type == SUNK || type == BOAT_SUNK ? len * m.cfg.sinkBonusPerSquare : 0)
                    + (type == BOAT_SUNK ? m.cfg.sonarBoatBonus : 0);
            long nhits = type == MISS ? hits : hits | bit;
            String desc = type == MISS ? "MISS" : type == HIT ? "HIT" : type == BOAT_SUNK ? "SONAR BOAT SUNK" : "SUNK";
            r.add(branch(e.getValue(), pk, desc, nhits, sonarOK, nsunk, nbonus, nsunk == m.toSink, full));
        }
        r.normalize();
        return r;
    }

    private Sink sink(Map<Long, Sink> sinks, long pk, boolean full, int expected) {
        if (only != ALL && pk != only) return IGNORE;
        Sink s = sinks.get(pk);
        if (s == null) {
            s = full ? new BuilderSink(new Builder(m, Math.min(expected, 1 << 12))) : new Counter();
            sinks.put(pk, s);
        }
        return s;
    }

    private Result.Branch branch(Sink s, long pk, String desc, long nhits, boolean sonar, int nsunk, int nbonus,
                                 boolean nwon, boolean keepState) {
        BeliefState st = null;
        double w;
        if (s instanceof BuilderSink) {
            st = ((BuilderSink) s).b.build(nhits, sonar, nsunk, nbonus, nwon);
            w = 0;
            for (int i = 0; i < st.size; i++) w += Long.bitCount(st.lo[i]) + Long.bitCount(st.hi[i]);
        } else {
            w = ((Counter) s).nStates;
        }
        int score = Long.bitCount(nhits) * m.cfg.pointsPerHit + nbonus;
        // a fast result never gives belief states (even when one was built internally to merge states)
        return new Result.Branch(pk, desc, keepState ? st : null, w, nsunk, score, sonar, nwon);
    }

    private Result sonarFull(int x) {
        long w = m.window[x];
        BeliefModel.Group[] gs = m.groups[x];
        Map<Long, Sink> sinks = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            long key = keys[i], l = lo[i], h = hi[i];
            long fp = m.occupancy(key) & w;
            for (BeliefModel.Group g : gs) {
                long sl = l & g.lo, sh = h & g.hi;
                if ((sl | sh) == 0) continue;
                sink(sinks, fp | g.boatPat, true, size / 8 + 16).add(key, sl, sh);
            }
        }
        Result r = new Result();
        for (Map.Entry<Long, Sink> e : sinks.entrySet())
            r.add(branch(e.getValue(), e.getKey(), describeSonar(e.getKey()), hits, sonarOK, sunk, bonus, false, true));
        r.normalize();
        return r;
    }

    private Result sonarFast(int x) {
        long w = m.window[x];
        BeliefModel.Group[] gs = m.groups[x];
        int bits = m.windowCells[x].length;
        int ng = gs.length;
        Map<Long, double[]> acc = new HashMap<>();
        double[] cnt = null;
        // dense arrays pay off only for large belief states; small ones use a hash map
        boolean useArray = bits <= 13 && size > ((1 << bits) * ng) / 4;
        if (useArray) cnt = new double[(1 << bits) * ng];
        for (int i = 0; i < size; i++) {
            long key = keys[i], l = lo[i], h = hi[i];
            long fp = m.occupancy(key) & w;
            int fpc = useArray ? m.compress(fp, x) : 0;
            for (int gi = 0; gi < ng; gi++) {
                BeliefModel.Group g = gs[gi];
                int c = Long.bitCount(l & g.lo) + Long.bitCount(h & g.hi);
                if (c == 0) continue;
                if (useArray) cnt[fpc * ng + gi] += c;
                else acc.computeIfAbsent(fp | g.boatPat, k -> new double[1])[0] += c;
            }
        }
        if (useArray) {
            int[] wc = m.windowCells[x];
            for (int idx = 0; idx < cnt.length; idx++) {
                if (cnt[idx] == 0) continue;
                int fpc = idx / ng;
                long fp = 0;
                for (int b = 0; b < wc.length; b++) if ((fpc >>> b & 1) != 0) fp |= 1L << wc[b];
                acc.computeIfAbsent(fp | gs[idx % ng].boatPat, k -> new double[1])[0] += cnt[idx];
            }
        }
        Result r = new Result();
        for (Map.Entry<Long, double[]> e : acc.entrySet())
            r.add(new Result.Branch(e.getKey(), describeSonar(e.getKey()), null, e.getValue()[0], sunk, getScore(),
                    sonarOK, false));
        r.normalize();
        return r;
    }

    /** Description of a sonar percept: the squares where a boat was detected (e.g. "SONAR: contacts at C3, D3"). */
    private String describeSonar(long pk) {
        if (pk == 0) return "SONAR: no contact";
        StringBuilder sb = new StringBuilder(Long.bitCount(pk) == 1 ? "SONAR: contact at " : "SONAR: contacts at ");
        boolean first = true;
        for (long x = pk; x != 0; x &= x - 1) {
            int c = Long.numberOfTrailingZeros(x);
            if (!first) sb.append(", ");
            sb.append(Config.square(c / m.n, c % m.n));
            first = false;
        }
        return sb.toString();
    }

    // ================================================================== PREDICT for the opponent's turn

    /**
     * PREDICT for the opponent's turn: he may have moved one of his undamaged boats by one square (forward or
     * backward, onto free squares), or not moved at all. Each state is kept and also replaced by all the states
     * reachable with one move.
     */
    public BeliefState predict() {
        if (won) return this;
        Builder b = new Builder(m, size * 3);
        for (int i = 0; i < size; i++) {
            long key = keys[i], l = lo[i], h = hi[i];
            long occ = m.occupancy(key);
            b.add(key, l, h); // no move
            for (int s = 0; s < m.nShips; s++) {
                if (m.dmg(key, s) != 0) continue; // damaged or sunk ships cannot move
                int p = m.pos(key, s);
                for (int d = 0; d < 2; d++) {
                    int p2 = m.plNext[s][p][d];
                    if (p2 < 0) continue;
                    long entered = m.plMask[s][p2] & ~m.plMask[s][p];
                    if ((entered & occ) != 0) continue; // blocked by another ship
                    int c = Long.numberOfTrailingZeros(entered);
                    // states where the sonar boat lies on the entered square are impossible
                    b.add(m.withPos(key, s, p2), l & ~m.coverLo[c], h & ~m.coverHi[c]);
                }
            }
            // or he moved his sonar boat (if undamaged)
            long nl = 0, nh = 0;
            for (int p = 0; p < m.nSonar; p++) {
                boolean in = p < 64 ? (l >>> p & 1) != 0 : (h >>> (p - 64) & 1) != 0;
                if (!in || (m.sbMask[p] & hits) != 0) continue;
                for (int d = 0; d < 2; d++) {
                    int p2 = m.sbNext[p][d];
                    if (p2 < 0 || (m.sbMask[p2] & occ) != 0) continue;
                    if (p2 < 64) nl |= 1L << p2; else nh |= 1L << (p2 - 64);
                }
            }
            b.add(key, nl, nh);
        }
        return b.build(hits, sonarOK, sunk, bonus, won);
    }

    // ================================================================== an attack whose result is known

    /**
     * The belief state after the given attack when the attacked grid is known: the result of the attack (its
     * percept) is computed on this grid. Use it on the opponent's belief state about your grid, with your own grid,
     * to simulate an attack of the opponent: opponentBeliefState.afterAttack(attack, view.myBoard).
     */
    public BeliefState afterAttack(Action attack, Board grid) {
        Action a = attack.withoutMove();
        long pk;
        if (a.type == Action.Type.FIRE) {
            Ship s = grid.shipAt(a.row, a.col);
            if (s == null) pk = MISS;
            else {
                int k = s.indexOf(a.row, a.col);
                boolean sinks = !s.isDamaged(k) && s.damageCount() == s.length() - 1;
                if (!sinks) pk = HIT;
                else {
                    long mask = 0;
                    for (int j = 0; j < s.length(); j++) mask |= 1L << (s.cellRow(j) * m.n + s.cellCol(j));
                    pk = (s.isSonarBoat() ? BOAT_SUNK : SUNK) | (mask << 3);
                }
            }
        } else {
            pk = 0;
            for (int c : m.windowCells[a.row * m.n + a.col])
                if (grid.sonarReading(c / m.n, c % m.n)) pk |= 1L << c;
        }
        Result.Branch b = branch(a, pk);
        if (b != null) return b.getBeliefState();
        return new Builder(m, 16).build(hits, sonarOK, sunk, bonus, false); // impossible percept: empty
    }

    // ================================================================== real observations (used by the engine)

    /** Percept key of an attack I performed. */
    long perceptKey(Event e) {
        if (e.action.type == Action.Type.FIRE) {
            FireResult f = e.fireResult;
            if (f == FireResult.MISS) return MISS;
            if (f == FireResult.HIT) return HIT;
            long mask = 0;
            for (int[] c : e.sunkCells) mask |= 1L << (c[0] * m.n + c[1]);
            return (f == FireResult.SONAR_BOAT_SUNK ? BOAT_SUNK : SUNK) | (mask << 3);
        }
        long fp = 0;
        for (int[] c : e.sonarCells) if (c[2] == 1) fp |= 1L << (c[0] * m.n + c[1]);
        return fp;
    }

    /**
     * Belief state after an event that really happened, seen by player me: UPDATE with my attack, or PREDICT for
     * the opponent's turn. Returns an empty belief state if the event is impossible (which should never happen).
     */
    BeliefState observe(Event e, int me) {
        if (e.player != me) return predict();
        Result.Branch b = branch(e.action.withoutMove(), perceptKey(e));
        if (b != null) return b.getBeliefState();
        return new Builder(m, 16).build(hits, sonarOK, sunk, bonus, false);
    }

    /** True if the real enemy grid is one of the states of this belief state (for testing). */
    public boolean contains(Board enemy) {
        long key = 0;
        for (int s = 0; s < m.nShips; s++) {
            Ship sh = enemy.ship(s);
            int p = m.plIndex[s][sh.row() * m.n + sh.col()][sh.isHorizontal() ? 1 : 0];
            int d = 0;
            for (int j = 0; j < sh.length(); j++) if (sh.isDamaged(j)) d |= 1 << j;
            key = m.withDmg(m.withPos(key, s, p), s, d);
        }
        Ship sb = enemy.sonarBoat();
        long cellsMask = 0;
        for (int j = 0; j < sb.length(); j++) cellsMask |= 1L << (sb.cellRow(j) * m.n + sb.cellCol(j));
        int sp = -1;
        for (int q = 0; q < m.nSonar; q++) if (m.sbMask[q] == cellsMask) sp = q;
        for (int i = 0; i < size; i++)
            if (keys[i] == key) return sp < 64 ? (lo[i] >>> sp & 1) != 0 : (hi[i] >>> (sp - 64) & 1) != 0;
        return false;
    }

    // ================================================================== comparison (for HashMap / TreeMap)

    /** The states sorted by fleet key: a canonical form independent of how the belief state was built. */
    private long[][] canonical() {
        if (sorted == null) {
            Integer[] idx = new Integer[size];
            for (int i = 0; i < size; i++) idx[i] = i;
            Arrays.sort(idx, (a, b) -> Long.compare(keys[a], keys[b]));
            long[][] s = new long[3][size];
            for (int i = 0; i < size; i++) {
                s[0][i] = keys[idx[i]];
                s[1][i] = lo[idx[i]];
                s[2][i] = hi[idx[i]];
            }
            sorted = s;
        }
        return sorted;
    }

    /** Two belief states are equal if they contain the same states (and the same score, sonar, sunk boats). */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BeliefState)) return false;
        BeliefState b = (BeliefState) o;
        if (b.size != size || b.hits != hits || b.sonarOK != sonarOK || b.sunk != sunk || b.won != won
                || b.nStates != nStates || b.hashCode() != hashCode()) return false;
        long[][] x = canonical(), y = b.canonical();
        return Arrays.equals(x[0], y[0]) && Arrays.equals(x[1], y[1]) && Arrays.equals(x[2], y[2]);
    }

    @Override
    public int hashCode() {
        if (hash == 0) {
            long h = hits * 31 + (sonarOK ? 1 : 0) + sunk * 7L;
            for (int i = 0; i < size; i++) { // order-independent combination of the states
                long z = keys[i] * 0x9E3779B97F4A7C15L ^ lo[i] * 0xBF58476D1CE4E5B9L ^ hi[i] * 0x94D049BB133111EBL;
                z ^= z >>> 31;
                h += z;
            }
            hash = (int) (h ^ (h >>> 32));
            if (hash == 0) hash = 1;
        }
        return hash;
    }

    /** A total order on belief states, consistent with equals (0 if and only if they are equal). */
    @Override
    public int compareTo(BeliefState b) {
        if (this == b) return 0;
        int c = Integer.compare(hashCode(), b.hashCode());
        if (c != 0) return c;
        c = Integer.compare(size, b.size);
        if (c != 0) return c;
        c = Long.compare(hits, b.hits);
        if (c != 0) return c;
        c = Boolean.compare(sonarOK, b.sonarOK);
        if (c != 0) return c;
        c = Integer.compare(sunk, b.sunk);
        if (c != 0) return c;
        long[][] x = canonical(), y = b.canonical();
        for (int k = 0; k < 3; k++)
            for (int i = 0; i < size; i++) {
                c = Long.compare(x[k][i], y[k][i]);
                if (c != 0) return c;
            }
        return 0;
    }

    @Override
    public String toString() {
        return String.format("BeliefState[%d fleets, %.0f states, score %d, %d boats sunk%s]", size, nStates,
                getScore(), sunk, won ? ", WON" : "");
    }
}
