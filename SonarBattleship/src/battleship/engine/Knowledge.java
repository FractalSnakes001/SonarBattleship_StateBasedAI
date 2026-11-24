package battleship.engine;

import java.util.List;

/**
 * Simple summary of what a player knows about the opponent's grid: for each square, the most recent information
 * obtained and when it was obtained. The opponent may move an undamaged boat silently at each turn, so
 * information about undamaged boats gets old; damaged boats cannot move.
 *
 * This is NOT a belief state: it is only used by the display and by BasicAI.
 */
public final class Knowledge {
    public static final int UNKNOWN = 0;
    /** No boat (missed rocket or empty sonar square). */
    public static final int EMPTY = 1;
    /** Sonar contact (boat of unknown type and state). */
    public static final int CONTACT = 2;
    /** Rocket hit a boat (a damaged boat cannot move: this never changes until it is sunk). */
    public static final int HIT = 3;
    /** Square of a sunk boat (never changes). */
    public static final int SUNK = 4;

    public static final int FROM_ROCKET = 1, FROM_SONAR = 2;

    private final int size;
    private final int[][] state;
    private final int[][] since;
    private final int[][] source;
    private final int lastTurn;
    private int[] lastSonar; // {row, col, turn} of my last sonar
    private int[] lastOpponentSonar; // {row, col, turn} of the opponent's last sonar

    public Knowledge(Config cfg, int me, List<Event> history) {
        size = cfg.size;
        state = new int[size][size];
        since = new int[size][size];
        source = new int[size][size];
        lastTurn = history.isEmpty() ? -1 : history.get(history.size() - 1).turn;
        for (Event e : history) {
            Action a = e.action;
            if (e.player != me) {
                if (a.type == Action.Type.SONAR) lastOpponentSonar = new int[]{a.row, a.col, e.turn};
                continue;
            }
            if (a.type == Action.Type.FIRE) {
                int v = e.fireResult == FireResult.MISS ? EMPTY : HIT;
                if (state[a.row][a.col] != SUNK) set(a.row, a.col, v, e.turn, FROM_ROCKET);
                if (e.sunkCells != null)
                    for (int[] c : e.sunkCells) set(c[0], c[1], SUNK, e.turn, FROM_ROCKET);
            } else if (e.sonarCells != null) {
                lastSonar = new int[]{a.row, a.col, e.turn};
                for (int[] c : e.sonarCells) {
                    int cur = state[c[0]][c[1]];
                    if (cur == SUNK || cur == HIT) continue; // a damaged boat cannot move
                    set(c[0], c[1], c[2] == 1 ? CONTACT : EMPTY, e.turn, FROM_SONAR);
                }
            }
        }
    }

    private void set(int r, int c, int v, int t, int src) {
        state[r][c] = v;
        since[r][c] = t;
        source[r][c] = src;
    }

    public int size() { return size; }
    public int state(int r, int c) { return state[r][c]; }
    /** Turn at which the information about (r,c) was obtained. */
    public int since(int r, int c) { return since[r][c]; }
    /** FROM_ROCKET or FROM_SONAR (0 if unknown). */
    public int source(int r, int c) { return source[r][c]; }

    /** Number of opponent turns (each one may hide a move) played after the given turn. */
    public int changesSince(int turn) {
        int n = 0;
        for (int t = turn + 1; t <= lastTurn; t++) if ((t - turn) % 2 == 1) n++;
        return n;
    }

    /** True if the information on (r,c) cannot have changed since it was obtained. */
    public boolean isFresh(int r, int c) {
        int s = state[r][c];
        if (s == UNKNOWN) return false;
        if (s == SUNK || s == HIT) return true;
        return changesSince(since[r][c]) == 0;
    }

    /** {row, col, turn} of my last sonar, or null. */
    public int[] lastSonar() { return lastSonar; }
    /** {row, col, turn} of the opponent's last sonar on my grid, or null. */
    public int[] lastOpponentSonar() { return lastOpponentSonar; }
}
