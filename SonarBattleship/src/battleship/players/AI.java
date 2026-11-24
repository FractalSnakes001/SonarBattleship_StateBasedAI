package battleship.players;

import battleship.belief.BeliefState;
import battleship.engine.Action;
import battleship.engine.Config;
import battleship.engine.GameView;
import battleship.engine.Placement;

import java.util.Random;



/**
 *  (P)
    *  (See the Notes.md file i left this here in case people do not see it)
        Since our algo must be fast here are some constraints:

            - No recursion / Bottom-up & iterative style only if possible.

        For the algo, We have so many states that we must use a depth limit or/and even time 
        limit since we need to be under 5s per move (both attack + defense).

        We are limited to a AND-OR algorithm, Minimax from the course is a bad idea. 
        unless we rework it, it does not take into account non-determinism.
        We should use the probabilty to guide our state exploration. i would go towards ExpectiMax
        
        We need to also need find some Evaluation function 

        We can customily prune the tree as we need. ex: no sonar on the edges (makes no sense)

        We can "cheat" by pre-calculating some information/ compressing it and putting 
        inside the javafile. Pretty sure it is allowed. We just need to explain it on the oral defense.
        ex: Best 2 initial moves both in defense and attack.

        We can start easy then slowly add optimizations everywhere to minimize our time.

        It is a bonus, but it will probably count for the marks so lets also code the bonus AI. 
        probably MonteCarlo Search imo
    (/P)
 * 
 */





/**
 * YOUR AI. This is the only file you must complete. Your whole AI must be contained in this class: you may add
 * fields, methods and nested classes to it, but no other class outside it. A new AI object is created for each
 * game.
 */
public final class AI {

    /**
     * Chooses where to place your boats at the beginning of a game (your fleet and your sonar boat).
     * By default they are placed at random; you may change this.
     */
    public Placement placeBoats(Config cfg, Random rnd) {
        return Placement.random(cfg, rnd);
    }

    /**
     * Chooses your next action: an optional silent move of one of your boats, and an attack (FIRE or SONAR).
     *
     * @param view                your grid (view.myBoard, to choose your silent move with getMoves) and the number
     *                            of actions left before the limit (view.actionsLeft())
     * @param beliefState         your belief state about the opponent's grid: all the states of his grid compatible
     *                            with what you have observed so far
     * @param opponentBeliefState the opponent's belief state about YOUR grid: what he may know about it
     * @return the action to play: an attack (e.g. one of beliefState.getAttacks()), or one of the actions returned
     *         by view.myBoard.getMoves(attack) to add a silent move
     */
    public Action findNextMove(GameView view, BeliefState beliefState, BeliefState opponentBeliefState) {
        // TODO: replace this by your AND-OR search
        return Action.fire(0, 0);
    }
}
