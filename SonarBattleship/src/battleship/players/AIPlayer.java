package battleship.players;

import battleship.belief.BeliefState;
import battleship.belief.BeliefTracker;
import battleship.engine.Action;
import battleship.engine.Config;
import battleship.engine.GameView;
import battleship.engine.Placement;

import java.util.Random;

/**
 * Connects your {@link AI} to the game (do not modify this class): it creates a new AI object for each game,
 * keeps your belief state about the opponent's grid and the opponent's belief state about your grid up to date,
 * calls AI.findNextMove, and measures the time it takes.
 */
public final class AIPlayer implements Player {
    /** Maximum time allowed to choose an action (milliseconds). Longer actions are counted and reported. */
    public static final long TIME_LIMIT_MS = 5000;

    private AI ai;
    private BeliefTracker mine, his;
    private int me = -1;
    private final TimeStats stats = new TimeStats();
    private long lastNanos;
    private double lastStates;

    @Override
    public String name() { return "AI.java"; }

    @Override
    public Placement placeShips(Config cfg, Random rnd) {
        ai = new AI(); // a new game: a new AI, and new belief states
        mine = null;
        return ai.placeBoats(cfg, rnd);
    }

    @Override
    public Action chooseAction(GameView view) {
        if (ai == null) ai = new AI();
        if (mine == null || me != view.me) {
            me = view.me;
            mine = new BeliefTracker(view.config, me);
            his = new BeliefTracker(view.config, 1 - me);
        }
        BeliefState b = mine.update(view.history).withSonarAvailable(view.mySonarAvailable);
        BeliefState o = his.update(view.history).withSonarAvailable(view.opponentSonarAvailable);
        long t = System.nanoTime();
        Action a = ai.findNextMove(view, b, o);
        lastNanos = System.nanoTime() - t;
        lastStates = b.getNbrOfStates();
        stats.add(lastNanos);
        return a;
    }

    /** Statistics about the time taken by AI.findNextMove since this player was created. */
    public TimeStats timeStats() { return stats; }

    /** Time taken by the last call to AI.findNextMove, in nanoseconds. */
    public long lastTimeNanos() { return lastNanos; }

    /** Number of states of the belief state given to the last call to AI.findNextMove. */
    public double lastBeliefStates() { return lastStates; }
}
