package battleship.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Initial placement chosen by one player on his own grid: the position of each ship of his fleet (in the order
 * of {@link Config#fleet}) and the position of his sonar boat.
 */
public final class Placement {

    /** Position of the first square of a boat and its orientation. */
    public static final class Spot {
        public final int row, col;
        public final boolean horizontal;

        public Spot(int row, int col, boolean horizontal) {
            this.row = row;
            this.col = col;
            this.horizontal = horizontal;
        }

        public int cellRow(int i) { return horizontal ? row : row + i; }
        public int cellCol(int i) { return horizontal ? col + i : col; }

        @Override
        public String toString() { return Config.square(row, col) + (horizontal ? "-H" : "-V"); }
    }

    public final List<Spot> fleet;
    public final Spot sonarBoat;

    public Placement(List<Spot> fleet, Spot sonarBoat) {
        this.fleet = Collections.unmodifiableList(new ArrayList<>(fleet));
        this.sonarBoat = sonarBoat;
    }

    /** Returns null if the placement is valid, or a message explaining the problem. */
    public String check(Config cfg) {
        if (fleet.size() != cfg.fleet.length) return "wrong number of ships";
        if (sonarBoat == null) return "the sonar boat is not placed";
        boolean[][] used = new boolean[cfg.size][cfg.size];
        for (int k = 0; k <= fleet.size(); k++) {
            Spot s = k < fleet.size() ? fleet.get(k) : sonarBoat;
            int len = cfg.boatLength(k);
            for (int i = 0; i < len; i++) {
                int r = s.cellRow(i), c = s.cellCol(i);
                if (!cfg.inside(r, c)) return "a boat is outside the grid";
                if (used[r][c]) return "two boats overlap at " + Config.square(r, c);
                used[r][c] = true;
            }
        }
        return null;
    }

    /** True if a boat of length len at spot s fits inside the grid without touching the occupied squares. */
    public static boolean fits(Config cfg, boolean[][] used, Spot s, int len) {
        for (int i = 0; i < len; i++) {
            int r = s.cellRow(i), c = s.cellCol(i);
            if (!cfg.inside(r, c) || used[r][c]) return false;
        }
        return true;
    }

    public static void mark(boolean[][] used, Spot s, int len) {
        for (int i = 0; i < len; i++) used[s.cellRow(i)][s.cellCol(i)] = true;
    }

    /** All spots where a boat of length len fits. */
    public static List<Spot> freeSpots(Config cfg, boolean[][] used, int len) {
        List<Spot> res = new ArrayList<>();
        for (int r = 0; r < cfg.size; r++)
            for (int c = 0; c < cfg.size; c++) {
                Spot h = new Spot(r, c, true);
                if (fits(cfg, used, h, len)) res.add(h);
                if (len > 1) {
                    Spot v = new Spot(r, c, false);
                    if (fits(cfg, used, v, len)) res.add(v);
                }
            }
        return res;
    }

    /** Uniformly random placement of the fleet, then of the sonar boat. */
    public static Placement random(Config cfg, Random rnd) {
        while (true) {
            boolean[][] used = new boolean[cfg.size][cfg.size];
            List<Spot> spots = new ArrayList<>();
            for (int len : cfg.fleet) {
                List<Spot> free = freeSpots(cfg, used, len);
                Spot s = free.get(rnd.nextInt(free.size()));
                mark(used, s, len);
                spots.add(s);
            }
            List<Spot> free = freeSpots(cfg, used, cfg.sonarBoatLength);
            if (!free.isEmpty()) return new Placement(spots, free.get(rnd.nextInt(free.size())));
        }
    }
}
