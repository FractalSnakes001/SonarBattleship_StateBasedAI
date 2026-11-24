package battleship.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The grid of one player: his fleet (boats 0, 1, 2) and his sonar boat (boat 3).
 */
public final class Board {
    private final int size;
    private final List<Ship> fleet = new ArrayList<>();
    private Ship sonarBoat;

    private Board(int size) {
        this.size = size;
    }

    /** Builds the board of a player from his placement. */
    public static Board fromPlacement(Config cfg, Placement p) {
        String err = p.check(cfg);
        if (err != null) throw new IllegalArgumentException("Invalid placement: " + err);
        return preview(cfg, p.fleet, p.sonarBoat);
    }

    /** Builds a (possibly incomplete) board, e.g. while a human is placing his boats. No validity check. */
    public static Board preview(Config cfg, List<Placement.Spot> fleetSpots, Placement.Spot sonar) {
        Board b = new Board(cfg.size);
        for (int k = 0; k < fleetSpots.size(); k++) {
            Placement.Spot s = fleetSpots.get(k);
            b.fleet.add(new Ship(cfg.fleet[k], s.row, s.col, s.horizontal, false));
        }
        if (sonar != null) b.sonarBoat = new Ship(cfg.sonarBoatLength, sonar.row, sonar.col, sonar.horizontal, true);
        return b;
    }

    public Board copy() {
        Board b = new Board(size);
        for (Ship s : fleet) b.fleet.add(s.copy());
        b.sonarBoat = sonarBoat == null ? null : sonarBoat.copy();
        return b;
    }

    public int size() { return size; }
    /** The three ships of the fleet. */
    public List<Ship> fleet() { return Collections.unmodifiableList(fleet); }
    public Ship ship(int i) { return fleet.get(i); }
    /** The sonar boat of the owner of this grid. */
    public Ship sonarBoat() { return sonarBoat; }

    /** Number of boats: the fleet and the sonar boat. */
    public int boatCount() { return fleet.size() + (sonarBoat != null ? 1 : 0); }

    /** Boat number i: the fleet ships (0, 1, 2), then the sonar boat (3). */
    public Ship boat(int i) {
        if (i >= 0 && i < fleet.size()) return fleet.get(i);
        if (i == fleet.size()) return sonarBoat;
        return null;
    }

    public boolean inside(int r, int c) { return r >= 0 && c >= 0 && r < size && c < size; }

    /** Boat occupying square (r,c), or null. */
    public Ship shipAt(int r, int c) {
        for (Ship s : fleet) if (s.occupies(r, c)) return s;
        if (sonarBoat != null && sonarBoat.occupies(r, c)) return sonarBoat;
        return null;
    }

    /** Square entered by boat i when it moves in direction dir (+1 forward, -1 backward), as {row, col}. */
    public int[] moveTarget(int i, int dir) {
        Ship s = boat(i);
        int idx = dir > 0 ? s.length() : -1;
        int r = s.isHorizontal() ? s.row() : s.row() + idx;
        int c = s.isHorizontal() ? s.col() + idx : s.col();
        return new int[]{r, c};
    }

    /** A boat can move if it has no damaged square and the square it enters is free and inside the grid. */
    public boolean canMove(int i, int dir) {
        Ship s = boat(i);
        if (s == null || (dir != 1 && dir != -1) || !s.isIntact()) return false;
        int[] t = moveTarget(i, dir);
        return inside(t[0], t[1]) && shipAt(t[0], t[1]) == null;
    }

    /**
     * Moves boat i on this board. Meant for AIs that simulate their own moves on a copy of their grid
     * (GameView.myBoard is already a copy).
     */
    public void move(int i, int dir) {
        if (!canMove(i, dir)) throw new IllegalArgumentException("boat " + i + " cannot move " + dir);
        boat(i).shift(dir);
    }

    /**
     * Every silent move I can perform before the given attack: the list starts with the attack without any move,
     * followed by the attack preceded by each legal move of one of my boats (at most 9 actions in total).
     */
    public List<Action> getMoves(Action attack) {
        Action base = attack.withoutMove();
        List<Action> res = new ArrayList<>();
        res.add(base);
        for (int i = 0; i < boatCount(); i++)
            for (int d = -1; d <= 1; d += 2)
                if (canMove(i, d)) res.add(base.withMove(i, d));
        return res;
    }

    /** A copy of this grid after the silent move of the given action (an identical copy if it has no move). */
    public Board afterMove(Action action) {
        Board b = copy();
        if (action.hasMove()) b.move(action.moveBoat, action.moveDir);
        return b;
    }

    /**
     * A copy of this grid after the given attack of the opponent: a rocket damages the boat on its square (if
     * any); a sonar changes nothing. Useful to simulate the opponent's attacks on your own grid.
     */
    public Board afterAttack(Action attack) {
        Board b = copy();
        if (attack.type == Action.Type.FIRE) b.fire(attack.row, attack.col);
        return b;
    }

    /** Number of damaged squares on the boats that are still afloat. */
    public int damageAfloat() {
        int d = 0;
        for (int i = 0; i < boatCount(); i++) if (!boat(i).isSunk()) d += boat(i).damageCount();
        return d;
    }

    /** Fires a rocket at (r,c) of this grid. */
    FireResult fire(int r, int c) {
        Ship s = shipAt(r, c);
        if (s == null) return FireResult.MISS;
        boolean wasSunk = s.isSunk();
        s.damage(s.indexOf(r, c));
        if (!s.isSunk() || wasSunk) return FireResult.HIT;
        return s.isSonarBoat() ? FireResult.SONAR_BOAT_SUNK : FireResult.SUNK;
    }

    /** What a sonar sees on square (r,c): true if a boat (of any kind, in any state) is there. */
    public boolean sonarReading(int r, int c) {
        return shipAt(r, c) != null;
    }

    /** True when every boat (the fleet and the sonar boat) is sunk. */
    public boolean allSunk() {
        for (int i = 0; i < boatCount(); i++) if (!boat(i).isSunk()) return false;
        return true;
    }

    /** True when the sonar boat is sunk (its owner can no longer use his sonar). */
    public boolean sonarBoatSunk() {
        return sonarBoat != null && sonarBoat.isSunk();
    }

    /** Number of boats still afloat. */
    public int boatsAfloat() {
        int n = 0;
        for (int i = 0; i < boatCount(); i++) if (!boat(i).isSunk()) n++;
        return n;
    }
}
