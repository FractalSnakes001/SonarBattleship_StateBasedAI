package battleship.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The referee: holds the true state of both grids, checks and applies actions, and builds the (partial) view of
 * each player.
 */
public final class Game {
    private final Config cfg;
    private final Board[] boards = new Board[2];
    private final List<Event> history = new ArrayList<>();
    private int current;
    private int turn;
    private int winner = -1;
    private boolean over;
    private String endReason = "";

    /**
     * @param p0 placement chosen by player 0 (his fleet and his sonar boat)
     * @param p1 placement chosen by player 1
     * @param firstPlayer index of the player who starts
     */
    public Game(Config cfg, Placement p0, Placement p1, int firstPlayer) {
        this.cfg = cfg;
        boards[0] = Board.fromPlacement(cfg, p0);
        boards[1] = Board.fromPlacement(cfg, p1);
        current = firstPlayer;
    }

    public Config config() { return cfg; }
    public int currentPlayer() { return current; }
    public int turn() { return turn; }
    public boolean isOver() { return over; }
    /** 0 or 1, or -1 for a draw / game not over. */
    public int winner() { return winner; }
    public String endReason() { return endReason; }
    /** A player can use his sonar as long as his sonar boat is afloat. */
    public boolean sonarAvailable(int p) { return !boards[p].sonarBoatSunk(); }
    public List<Event> history() { return Collections.unmodifiableList(history); }

    /** True grid of a player (referee / display only: never give it to an AI). */
    public Board board(int p) { return boards[p]; }

    /** Returns null if the action is legal for the current player, or the reason why it is not. */
    public String illegalReason(Action a) {
        if (over) return "the game is over";
        return illegalReason(cfg, boards[current], sonarAvailable(current), a);
    }

    static String illegalReason(Config cfg, Board mine, boolean sonarOk, Action a) {
        if (a == null) return "no action";
        if (a.hasMove()) {
            Ship s = mine.boat(a.moveBoat);
            if (s == null) return "no such boat";
            if (!s.isIntact()) return "a damaged boat cannot move";
            if (!mine.canMove(a.moveBoat, a.moveDir)) return "this boat cannot move there";
        }
        if (!cfg.inside(a.row, a.col)) return "target outside the grid";
        if (a.type == Action.Type.SONAR && !sonarOk) return "your sonar boat is sunk: no more sonar";
        return null;
    }

    /** All legal actions (every attack, with no move and with each possible move): about 1,000 actions. */
    public static List<Action> legalActions(Config cfg, Board mine, boolean sonarOk) {
        List<Action> attacks = new ArrayList<>();
        for (int r = 0; r < cfg.size; r++)
            for (int c = 0; c < cfg.size; c++) attacks.add(Action.fire(r, c));
        if (sonarOk)
            for (int r = 0; r < cfg.size; r++)
                for (int c = 0; c < cfg.size; c++) attacks.add(Action.sonar(r, c));
        List<Action> res = new ArrayList<>(attacks);
        for (int i = 0; i < mine.boatCount(); i++)
            for (int d = -1; d <= 1; d += 2)
                if (mine.canMove(i, d))
                    for (Action a : attacks) res.add(a.withMove(i, d));
        return res;
    }

    public List<Action> legalActions() {
        return legalActions(cfg, boards[current], sonarAvailable(current));
    }

    /** Plays the action of the current player and returns the full event. */
    public Event play(Action a) {
        String err = illegalReason(a);
        if (err != null) throw new IllegalArgumentException("Illegal action " + a + ": " + err);
        int p = current;
        Board target = boards[1 - p];
        if (a.hasMove()) boards[p].move(a.moveBoat, a.moveDir); // the silent move first, on my own grid
        FireResult fr = null;
        List<int[]> sonar = null, sunk = null;
        if (a.type == Action.Type.FIRE) {
            fr = target.fire(a.row, a.col);
            if (fr.isSunk()) sunk = cells(target.shipAt(a.row, a.col));
            if (fr.isSunk() && target.allSunk()) finish(p, "sank all the enemy boats");
        } else {
            sonar = sonarReadings(cfg, target, a.row, a.col);
        }
        Event e = new Event(turn, p, a, fr, sonar, sunk, false);
        history.add(e);
        turn++;
        if (!over && turn >= cfg.maxTurns) {
            int s0 = score(0), s1 = score(1);
            if (s0 == s1) finish(-1, "action limit reached, equal scores " + s0 + " - " + s1);
            else finish(s0 > s1 ? 0 : 1, "higher score at the action limit (" + Math.max(s0, s1) + " - "
                    + Math.min(s0, s1) + ")");
        }
        current = 1 - p;
        return e;
    }

    private static List<int[]> cells(Ship s) {
        List<int[]> l = new ArrayList<>();
        for (int i = 0; i < s.length(); i++) l.add(new int[]{s.cellRow(i), s.cellCol(i)});
        return l;
    }

    private static List<int[]> sonarReadings(Config cfg, Board target, int cr, int cc) {
        List<int[]> l = new ArrayList<>();
        for (int r = 0; r < cfg.size; r++)
            for (int c = 0; c < cfg.size; c++)
                if (cfg.inSonarArea(cr, cc, r, c)) l.add(new int[]{r, c, target.sonarReading(r, c) ? 1 : 0});
        return l;
    }

    /**
     * Score of player p: points for each square he hit on an enemy boat, plus a bonus for each enemy boat he
     * sank. Both players know both scores. It decides the game at the action limit.
     */
    public int score(int p) {
        return score(cfg, boards[1 - p]);
    }

    /** Score earned by the attacker of the given grid. */
    public static int score(Config cfg, Board target) {
        int s = 0;
        for (int i = 0; i < target.boatCount(); i++) {
            Ship b = target.boat(i);
            s += b.damageCount() * cfg.pointsPerHit;
            if (b.isSunk()) s += b.length() * cfg.sinkBonusPerSquare + (b.isSonarBoat() ? cfg.sonarBoatBonus : 0);
        }
        return s;
    }

    private void finish(int winner, String reason) {
        this.over = true;
        this.winner = winner;
        this.endReason = reason;
    }

    /** History as seen by player p: his own events in full, the opponent's events masked. */
    public List<Event> historyFor(int p) {
        List<Event> res = new ArrayList<>(history.size());
        for (Event e : history) res.add(e.player == p ? e.copy() : e.maskedFor()); // copies only
        return res;
    }

    /** Everything player p is allowed to know. Safe to give to an AI (contains copies only). */
    public GameView viewFor(int p) {
        return new GameView(cfg.copy(), p, turn, boards[p].copy(), sonarAvailable(p), sonarAvailable(1 - p),
                score(p), score(1 - p), historyFor(p));
    }
}
