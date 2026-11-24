package battleship.engine;

import java.util.Collections;
import java.util.List;

/**
 * Everything a player knows at the moment he must act: his own grid, whether each player may still use the
 * sonar, both scores, and the history of the game as he observed it.
 */
public final class GameView {
    public final Config config;
    /** Index of the player owning this view (0 or 1). */
    public final int me;
    /** Index of the current turn (number of actions played so far by both players). */
    public final int turn;
    /** Copy of my grid (my boats, their positions and damage). */
    public final Board myBoard;
    public final boolean mySonarAvailable;
    public final boolean opponentSonarAvailable;
    /** Current scores (see Game.score). */
    public final int myScore, opponentScore;
    /** History as seen by me (the opponent's silent moves are not shown). */
    public final List<Event> history;
    private Knowledge knowledge;

    GameView(Config config, int me, int turn, Board myBoard, boolean mySonar, boolean oppSonar, int myScore,
             int oppScore, List<Event> history) {
        this.config = config;
        this.me = me;
        this.turn = turn;
        this.myBoard = myBoard;
        this.mySonarAvailable = mySonar;
        this.opponentSonarAvailable = oppSonar;
        this.myScore = myScore;
        this.opponentScore = oppScore;
        this.history = Collections.unmodifiableList(history);
    }

    /** Simple summary of what I know about the opponent's grid (used by the display and BasicAI). */
    public Knowledge knowledge() {
        if (knowledge == null) knowledge = new Knowledge(config, me, history);
        return knowledge;
    }

    /** All my legal actions (every attack, with and without each possible move). */
    public List<Action> legalActions() {
        return Game.legalActions(config, myBoard, mySonarAvailable);
    }

    public boolean isLegal(Action a) {
        return Game.illegalReason(config, myBoard, mySonarAvailable, a) == null;
    }

    /** Number of actions left before the action limit. */
    public int actionsLeft() { return config.maxTurns - turn; }

    /** Last event of the opponent, or null. */
    public Event lastOpponentEvent() {
        for (int i = history.size() - 1; i >= 0; i--)
            if (history.get(i).player != me) return history.get(i);
        return null;
    }
}
