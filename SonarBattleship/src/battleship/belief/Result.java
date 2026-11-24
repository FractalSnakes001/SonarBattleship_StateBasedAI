package battleship.belief;

import java.util.ArrayList;
import java.util.List;

/**
 * The RESULT of an attack performed from a belief state: one belief state per possible percept (an AND node of the
 * AND-OR search). The percepts are numbered from 0 to size() - 1, and the functions of this class give the
 * information about percept number i. The probability of a percept is proportional to the number of states of
 * its belief state.
 */
public final class Result {

    /** One possible percept (internal). */
    static final class Branch {
        final long perceptKey;
        final String percept;
        final BeliefState state;
        final double nStates;
        final int sunk, score;
        final boolean sonar, won;
        double probability;

        Branch(long perceptKey, String percept, BeliefState state, double nStates, int sunk, int score,
               boolean sonar, boolean won) {
            this.perceptKey = perceptKey;
            this.percept = percept;
            this.state = state;
            this.nStates = nStates;
            this.sunk = sunk;
            this.score = score;
            this.sonar = sonar;
            this.won = won;
        }

        BeliefState getBeliefState() { return state; }
    }

    private final List<Branch> branches = new ArrayList<>();

    void add(Branch b) { branches.add(b); }

    void normalize() {
        double total = 0;
        for (Branch b : branches) total += b.nStates;
        for (Branch b : branches) b.probability = total > 0 ? b.nStates / total : 0;
    }

    Branch find(long perceptKey) {
        for (Branch b : branches) if (b.perceptKey == perceptKey) return b;
        return null;
    }

    /** Number of possible percepts. */
    public int size() { return branches.size(); }

    /** The belief state of percept i (null if the result was computed by extendsBeliefStateFast). */
    public BeliefState getBeliefState(int i) { return branches.get(i).state; }

    /** The probability of percept i. */
    public double getProbability(int i) { return branches.get(i).probability; }

    /** A description of percept i (e.g. "MISS", "HIT", "SUNK", "SONAR: contacts at C3, D3"). */
    public String getPercept(int i) { return branches.get(i).percept; }

    /** My score in the belief state of percept i. */
    public int getScore(int i) { return branches.get(i).score; }

    /** Number of states of the belief state of percept i. */
    public double getNbrOfStates(int i) { return branches.get(i).nStates; }

    /** Number of enemy boats sunk in the belief state of percept i. */
    public int getNbrOfSunkBoats(int i) { return branches.get(i).sunk; }

    /** True if my sonar is available in the belief state of percept i. */
    public boolean isSonarAvailable(int i) { return branches.get(i).sonar; }

    /** True if every enemy boat is sunk in the belief state of percept i. */
    public boolean isWon(int i) { return branches.get(i).won; }
}
