package battleship.belief;

import battleship.engine.Action;

import java.util.ArrayList;
import java.util.List;

/**
 * A list of (action, result) pairs: the RESULT of every attack from the same belief state, as returned by
 * BeliefState.extendsBeliefState(). The pairs are numbered from 0 to size() - 1.
 */
public final class Plans {
    private final List<Action> actions = new ArrayList<>();
    private final List<Result> results = new ArrayList<>();

    Plans() { }

    void addPlan(Action action, Result result) {
        actions.add(action);
        results.add(result);
    }

    /** Number of (action, result) pairs. */
    public int size() { return actions.size(); }
    /** The action of pair i. */
    public Action getAction(int i) { return actions.get(i); }
    /** The result of pair i. */
    public Result getResult(int i) { return results.get(i); }
}
