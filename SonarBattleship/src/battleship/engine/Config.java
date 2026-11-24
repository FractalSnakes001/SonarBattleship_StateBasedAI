package battleship.engine;

/**
 * The rules of the game. They are fixed: do not change these values (the belief states are built for them).
 */
public final class Config {
    /** Number of rows and columns of each grid. */
    public final int size = 7;
    /** Lengths of the ships of each fleet (do not modify this array). */
    public final int[] fleet = {4, 3, 2};
    /** Length of the sonar boat that each player places on his own grid. */
    public final int sonarBoatLength = 2;
    /** Number of boats on each grid: the fleet plus the sonar boat. */
    public final int boats = fleet.length + 1;
    /** The sonar reveals the 3x3 area centred on its target (radius 1). */
    public final int sonarRadius = 1;
    /** Maximum total number of actions (both players together). */
    public final int maxTurns = 200;
    /** Score: points per square hit on an enemy boat. */
    public final int pointsPerHit = 1;
    /** Score: bonus per square of a boat when it is sunk (a sunk boat of length L gives 2L points). */
    public final int sinkBonusPerSquare = 1;
    /** Score: extra bonus for sinking the enemy sonar boat. */
    public final int sonarBoatBonus = 2;

    /** The rules (they are always the same; this object exists for convenience). */
    public Config() { }

    /** A new Config with the same (fixed) rules: each player gets his own, so nobody can alter the engine's. */
    public Config copy() { return new Config(); }

    /** Length of boat number i: the fleet ships first (4, 3, 2), then the sonar boat (2). */
    public int boatLength(int i) { return i < fleet.length ? fleet[i] : sonarBoatLength; }

    /** True if square (r,c) is inside the sonar area centred on (cr,cc). */
    public boolean inSonarArea(int cr, int cc, int r, int c) {
        return Math.max(Math.abs(cr - r), Math.abs(cc - c)) <= sonarRadius;
    }

    public boolean inside(int r, int c) {
        return r >= 0 && c >= 0 && r < size && c < size;
    }

    /** Converts a square into the usual "C4" notation (column letter, row number). */
    public static String square(int r, int c) {
        return "" + (char) ('A' + c) + (r + 1);
    }

    @Override
    public String toString() {
        return size + "x" + size + ", fleet 4,3,2 + sonar boat 2, sonar 3x3, silent 1-square move";
    }
}
