package battleship.engine;

/**
 * What a player does during his turn: an optional silent move of one of his boats (one square forward or
 * backward along its axis), then an attack on the enemy grid: a rocket (FIRE) or a sonar (SONAR).
 * <pre>
 *   Action.fire(2, 3)                    // fire at D3, no move
 *   Action.sonar(4, 4).withMove(1, -1)   // move boat 1 one square backward, then a sonar centred on E5
 * </pre>
 * The opponent sees the attack, never the move.
 */
public final class Action {
    public enum Type { FIRE, SONAR }

    public final Type type;
    /** Target square of the attack. */
    public final int row, col;
    /** Silent move: the boat moved (0, 1, 2 = fleet ships, 3 = sonar boat), or -1 for no move. */
    public final int moveBoat;
    /** Silent move: +1 (forward: towards larger row or column) or -1 (backward); 0 for no move. */
    public final int moveDir;

    private Action(Type type, int row, int col, int moveBoat, int moveDir) {
        this.type = type;
        this.row = row;
        this.col = col;
        this.moveBoat = moveBoat;
        this.moveDir = moveDir;
    }

    public static Action fire(int row, int col) { return new Action(Type.FIRE, row, col, -1, 0); }
    public static Action sonar(int row, int col) { return new Action(Type.SONAR, row, col, -1, 0); }

    /** This attack, preceded by the silent move of boat `boat` in direction `dir` (+1 or -1). */
    public Action withMove(int boat, int dir) { return new Action(type, row, col, boat, dir); }

    /** The same attack without the silent move. */
    public Action withoutMove() { return hasMove() ? new Action(type, row, col, -1, 0) : this; }

    /** True if this action includes a silent move. */
    public boolean hasMove() { return moveBoat >= 0 && moveDir != 0; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Action)) return false;
        Action a = (Action) o;
        return a.type == type && a.row == row && a.col == col && a.moveBoat == moveBoat && a.moveDir == moveDir;
    }

    @Override
    public int hashCode() {
        return (((type.ordinal() * 31 + row) * 31 + col) * 31 + moveBoat) * 7 + moveDir;
    }

    @Override
    public String toString() {
        String move = hasMove() ? "move boat " + moveBoat + (moveDir > 0 ? " forward" : " backward") + " + " : "";
        return move + type + " " + Config.square(row, col);
    }
}
