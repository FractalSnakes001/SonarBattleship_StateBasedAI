package battleship.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What happened during one turn. The full event is known by the player who acted; the opponent receives a
 * masked copy, without the silent move (see {@link #maskedFor}).
 */
public final class Event {
    /** Index of the turn (0 for the first action of the game). */
    public final int turn;
    /** Player who acted (0 or 1). */
    public final int player;
    public final Action action;
    /** Answer to a FIRE action (null for a SONAR). */
    public final FireResult fireResult;
    /** Answer to a SONAR action: list of {row, col, reading} with reading 1 if a boat is there, 0 otherwise. */
    public final List<int[]> sonarCells;
    /** Squares of the boat sunk by this rocket (revealed to both players); null otherwise. */
    public final List<int[]> sunkCells;
    /** True if this is the opponent's (masked) version of the event. */
    public final boolean masked;

    Event(int turn, int player, Action action, FireResult fireResult, List<int[]> sonarCells,
          List<int[]> sunkCells, boolean masked) {
        this.turn = turn;
        this.player = player;
        this.action = action;
        this.fireResult = fireResult;
        this.sonarCells = sonarCells == null ? null : Collections.unmodifiableList(sonarCells);
        this.sunkCells = sunkCells == null ? null : Collections.unmodifiableList(sunkCells);
        this.masked = masked;
    }

    /**
     * The version of this event observed by the opponent: the attack and its result (he sees where it landed on
     * his own grid and, knowing his grid, what a sonar revealed), but not the silent move.
     */
    Event maskedFor() {
        return new Event(turn, player, action.withoutMove(), fireResult, copy(sonarCells), copy(sunkCells), true);
    }

    /** A deep copy of this event (a player can modify it without changing the game or the opponent's view). */
    Event copy() {
        return new Event(turn, player, action, fireResult, copy(sonarCells), copy(sunkCells), masked);
    }

    private static List<int[]> copy(List<int[]> cells) {
        if (cells == null) return null;
        List<int[]> res = new ArrayList<>(cells.size());
        for (int[] c : cells) res.add(c.clone());
        return res;
    }

    /** Short human-readable description. */
    public String describe() {
        StringBuilder sb = new StringBuilder(action.toString());
        if (fireResult != null) sb.append(" \u2192 ").append(fireResult.name().replace('_', ' '));
        if (sonarCells != null) {
            int n = 0;
            for (int[] c : sonarCells) if (c[2] == 1) n++;
            sb.append(" \u2192 ").append(n).append(n == 1 ? " contact" : " contacts");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "T" + turn + " P" + player + " " + describe();
    }
}
