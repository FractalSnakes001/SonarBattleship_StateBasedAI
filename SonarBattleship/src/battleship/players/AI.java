package battleship.players;

import battleship.belief.BeliefState;
import battleship.engine.Action;
import battleship.engine.Config;
import battleship.engine.GameView;
import battleship.engine.Placement;

import java.util.Random;

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
