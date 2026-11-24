package battleship.belief;

import battleship.engine.Config;
import battleship.engine.Event;

import java.util.List;

/**
 * Keeps the belief state of one player up to date with the history of the game (internal: the engine uses it to
 * give your AI its belief states).
 */
public final class BeliefTracker {
    private final int me;
    private final Config cfg;
    private BeliefState state;
    private int processed;

    public BeliefTracker(Config cfg, int me) {
        this.me = me;
        this.cfg = cfg;
        this.state = BeliefState.initial(cfg);
    }

    /** Processes the events not seen yet and returns the current belief state of player me. */
    public BeliefState update(List<Event> history) {
        if (history.size() < processed) { // new game
            state = BeliefState.initial(cfg);
            processed = 0;
        }
        for (int i = processed; i < history.size(); i++) state = state.observe(history.get(i), me);
        processed = history.size();
        return state;
    }

    public BeliefState current() { return state; }
}
