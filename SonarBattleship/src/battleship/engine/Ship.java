package battleship.engine;

/**
 * A boat lying on a grid: a ship of the fleet, or the sonar boat of the owner of the grid.
 * Square i of the boat is at (row, col + i) if horizontal, (row + i, col) otherwise.
 */
public final class Ship {
    private static final String[] NAMES = {"Boat", "Submarine", "Destroyer", "Cruiser", "Battleship", "Carrier"};

    private final int length;
    private int row, col;
    private final boolean horizontal;
    private final boolean[] damaged;
    private final boolean sonarBoat;

    public Ship(int length, int row, int col, boolean horizontal, boolean sonarBoat) {
        this.length = length;
        this.row = row;
        this.col = col;
        this.horizontal = horizontal;
        this.sonarBoat = sonarBoat;
        this.damaged = new boolean[length];
    }

    public Ship copy() {
        Ship s = new Ship(length, row, col, horizontal, sonarBoat);
        System.arraycopy(damaged, 0, s.damaged, 0, length);
        return s;
    }

    public int length() { return length; }
    public int row() { return row; }
    public int col() { return col; }
    public boolean isHorizontal() { return horizontal; }
    public boolean isSonarBoat() { return sonarBoat; }

    public int cellRow(int i) { return horizontal ? row : row + i; }
    public int cellCol(int i) { return horizontal ? col + i : col; }

    /** Index of square (r,c) in this boat, or -1 if the boat does not occupy it. */
    public int indexOf(int r, int c) {
        for (int i = 0; i < length; i++)
            if (cellRow(i) == r && cellCol(i) == c) return i;
        return -1;
    }

    public boolean occupies(int r, int c) { return indexOf(r, c) >= 0; }
    public boolean isDamaged(int i) { return damaged[i]; }

    public int damageCount() {
        int n = 0;
        for (boolean d : damaged) if (d) n++;
        return n;
    }

    public boolean isSunk() { return damageCount() == length; }
    /** True if no square of the boat is damaged (only such a boat can move). */
    public boolean isIntact() { return damageCount() == 0; }

    void damage(int i) { damaged[i] = true; }

    /** Moves the boat one square along its axis (+1: towards larger row/column, -1: backward). */
    void shift(int dir) {
        if (horizontal) col += dir; else row += dir;
    }

    public String name() {
        if (sonarBoat) return "Sonar boat";
        return length < NAMES.length ? NAMES[length] : "Ship(" + length + ")";
    }

    @Override
    public String toString() {
        return name() + "@" + Config.square(row, col) + (horizontal ? "-H" : "-V");
    }
}
