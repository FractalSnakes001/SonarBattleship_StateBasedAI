package battleship.players;

import battleship.engine.Action;
import battleship.engine.Board;
import battleship.engine.Config;
import battleship.engine.GameView;
import battleship.engine.Knowledge;
import battleship.engine.Ship;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A simple rule-based opponent. It does not use belief states nor any search: it fires around recent hits and
 * sonar contacts, scans unexplored areas, and moves a boat away from the area the opponent has just scanned.
 */
public class BasicAI implements Player {
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private final Random rnd = new Random();

    @Override
    public String name() { return "Basic AI"; }

    @Override
    public Action chooseAction(GameView v) {
        Config cfg = v.config;
        Knowledge k = v.knowledge();
        Action attack = attack(v, cfg, k);
        // defence: move a boat out of the area the opponent has just scanned (most of the time)
        int[] last = k.lastOpponentSonar();
        if (last != null && last[2] == v.turn - 1 && rnd.nextDouble() < 0.6) {
            int[] move = escape(cfg, v.myBoard, last[0], last[1]);
            if (move != null) return attack.withMove(move[0], move[1]);
        }
        return attack;
    }

    private Action attack(GameView v, Config cfg, Knowledge k) {
        // 1. Targets around hits and recent sonar contacts
        double best = 0;
        List<int[]> targets = new ArrayList<>();
        for (int r = 0; r < cfg.size; r++)
            for (int c = 0; c < cfg.size; c++) {
                if (!worthShooting(k, r, c)) continue;
                double score = 0;
                if (k.state(r, c) == Knowledge.CONTACT && k.isFresh(r, c)) score = 1.5;
                for (int[] d : DIRS) {
                    int rr = r + d[0], cc = c + d[1];
                    if (!hit(k, cfg, rr, cc)) continue;
                    score = Math.max(score, 1);
                    if (hit(k, cfg, rr + d[0], cc + d[1])) score = Math.max(score, 3); // aligned with two hits
                }
                if (score <= 0) continue;
                if (score > best) { best = score; targets.clear(); }
                if (score == best) targets.add(new int[]{r, c});
            }
        if (!targets.isEmpty()) {
            int[] t = targets.get(rnd.nextInt(targets.size()));
            return Action.fire(t[0], t[1]);
        }

        // 2. Sonar on the least known area
        if (v.mySonarAvailable && rnd.nextDouble() < 0.45) {
            int bestCount = 0;
            List<int[]> centres = new ArrayList<>();
            for (int r = 0; r < cfg.size; r++)
                for (int c = 0; c < cfg.size; c++) {
                    int n = 0;
                    for (int rr = 0; rr < cfg.size; rr++)
                        for (int cc = 0; cc < cfg.size; cc++)
                            if (cfg.inSonarArea(r, c, rr, cc) && worthShooting(k, rr, cc)) n++;
                    if (n > bestCount) { bestCount = n; centres.clear(); }
                    if (n == bestCount) centres.add(new int[]{r, c});
                }
            if (bestCount >= 4) {
                int[] t = centres.get(rnd.nextInt(centres.size()));
                return Action.sonar(t[0], t[1]);
            }
        }

        // 3. Random rocket on a square that may hold a boat, preferring a checkerboard pattern
        List<int[]> even = new ArrayList<>(), any = new ArrayList<>();
        for (int r = 0; r < cfg.size; r++)
            for (int c = 0; c < cfg.size; c++)
                if (worthShooting(k, r, c)) {
                    any.add(new int[]{r, c});
                    if ((r + c) % 2 == 0) even.add(new int[]{r, c});
                }
        List<int[]> pool = !even.isEmpty() ? even : any;
        if (pool.isEmpty()) // everything looks known: anywhere but on sunk boats
            for (int r = 0; r < cfg.size; r++)
                for (int c = 0; c < cfg.size; c++)
                    if (k.state(r, c) != Knowledge.SUNK) pool.add(new int[]{r, c});
        int[] t = pool.get(rnd.nextInt(pool.size()));
        return Action.fire(t[0], t[1]);
    }

    /** A square is worth a rocket unless we know for sure there is nothing new to hit there. */
    private static boolean worthShooting(Knowledge k, int r, int c) {
        int s = k.state(r, c);
        if (s == Knowledge.SUNK || s == Knowledge.HIT) return false;
        return !(s == Knowledge.EMPTY && k.isFresh(r, c));
    }

    private static boolean hit(Knowledge k, Config cfg, int r, int c) {
        return cfg.inside(r, c) && k.state(r, c) == Knowledge.HIT;
    }

    /** {boat, dir}: a move that takes squares of an undamaged boat out of the scanned area, or null. */
    private int[] escape(Config cfg, Board mine, int cr, int cc) {
        for (int i = mine.boatCount() - 1; i >= 0; i--) { // the sonar boat first
            Ship s = mine.boat(i);
            if (!s.isIntact()) continue;
            int now = overlap(cfg, s, cr, cc, 0);
            if (now == 0) continue;
            int bestDir = 0, bestVal = now;
            for (int d = -1; d <= 1; d += 2)
                if (mine.canMove(i, d)) {
                    int val = overlap(cfg, s, cr, cc, d);
                    if (val < bestVal) { bestVal = val; bestDir = d; }
                }
            if (bestDir != 0) return new int[]{i, bestDir};
        }
        return null;
    }

    private static int overlap(Config cfg, Ship s, int cr, int cc, int shift) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            int r = s.cellRow(i) + (s.isHorizontal() ? 0 : shift);
            int c = s.cellCol(i) + (s.isHorizontal() ? shift : 0);
            if (cfg.inSonarArea(cr, cc, r, c)) n++;
        }
        return n;
    }
}
