package battleship;

import battleship.engine.Action;
import battleship.engine.Config;
import battleship.engine.Game;
import battleship.engine.Placement;
import battleship.players.AIPlayer;
import battleship.players.Player;
import battleship.players.Players;
import battleship.players.TimeStats;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Plays many games between two AIs without any display, and reports the results and the time taken by AI.java.
 * Usage: java -cp bin battleship.Arena [player1] [player2] [number of games] [options]
 * Options: -limit ms (time limit per action, default 5000), -csv file (writes every time measure of AI.java:
 * game, turn, player, time in ms, number of states of its belief state).
 * Example: java -cp bin battleship.Arena AI BasicAI 100 -csv times.csv
 */
public final class Arena {
    public static void main(String[] args) throws FileNotFoundException {
        String a = args.length > 0 ? args[0] : "AI";
        String b = args.length > 1 ? args[1] : "BasicAI";
        int n = args.length > 2 ? Integer.parseInt(args[2]) : 100;
        long limit = AIPlayer.TIME_LIMIT_MS;
        String csv = null;
        for (int i = 3; i + 1 < args.length; i += 2) {
            if (args[i].equals("-limit")) limit = Long.parseLong(args[i + 1]);
            else if (args[i].equals("-csv")) csv = args[i + 1];
        }
        Config cfg = new Config();
        int[] wins = new int[2];
        int draws = 0, illegal = 0;
        long turns = 0;
        TimeStats[] stats = {new TimeStats(), new TimeStats()};
        boolean[] isAI = new boolean[2];
        Map<String, Integer> reasons = new TreeMap<>();
        PrintWriter out = csv == null ? null : new PrintWriter(csv);
        if (out != null) out.println("game,turn,player,time_ms,states");
        for (int g = 0; g < n; g++) {
            Player[] p = {Players.create(a), Players.create(b)};
            Placement[] pl = {p[0].placeShips(cfg.copy(), new Random()), p[1].placeShips(cfg.copy(), new Random())};
            Game game = new Game(cfg, pl[0], pl[1], g % 2);
            while (!game.isOver()) {
                int cur = game.currentPlayer();
                long t0 = System.nanoTime();
                Action act = p[cur].chooseAction(game.viewFor(cur));
                long dt = System.nanoTime() - t0;
                if (p[cur] instanceof AIPlayer) { // only the time spent in AI.findNextMove
                    AIPlayer ai = (AIPlayer) p[cur];
                    dt = ai.lastTimeNanos();
                    isAI[cur] = true;
                    if (out != null)
                        out.printf("%d,%d,%d,%.3f,%.0f%n", g, game.turn(), cur + 1, dt / 1e6, ai.lastBeliefStates());
                }
                stats[cur].add(dt);
                if (game.illegalReason(act) != null) {
                    illegal++;
                    act = Action.fire(0, 0);
                }
                game.play(act);
            }
            turns += game.turn();
            if (game.winner() < 0) draws++; else wins[game.winner()]++;
            reasons.merge(game.winner() < 0 ? "draw" : "P" + (game.winner() + 1) + " won: "
                    + game.endReason().replaceAll(" \\(.*", ""), 1, Integer::sum);
        }
        if (out != null) out.close();
        System.out.printf("%d games, rules: %s%n", n, cfg);
        String[] names = {a, b};
        for (int i = 0; i < 2; i++) {
            System.out.printf("P%d %-10s wins %d%n", i + 1, names[i], wins[i]);
            if (isAI[i]) System.out.printf("   time per action: %s; %d actions over %d ms%n", stats[i], stats[i].countOver(limit), limit);
        }
        System.out.printf("draws %d, illegal actions %d, avg game length %.1f actions%n", draws, illegal, (double) turns / n);
        reasons.forEach((k, v) -> System.out.println("  " + k + ": " + v));
        if (out != null) System.out.println("time measures written to " + csv);
    }
}
