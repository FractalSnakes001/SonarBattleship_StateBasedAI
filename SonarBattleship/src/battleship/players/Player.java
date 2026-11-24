package battleship.players;

import battleship.engine.Action;
import battleship.engine.Config;
import battleship.engine.GameView;
import battleship.engine.Placement;

import java.util.Random;

/** An artificial player. Human players are handled directly by the GUI. */
public interface Player {

    /** Name displayed in the GUI and in the arena. */
    String name();

    /** Chooses the initial placement of the player's boats (his fleet and his sonar boat). */
    default Placement placeShips(Config cfg, Random rnd) {
        return Placement.random(cfg, rnd);
    }

    /** Chooses the next action (an optional silent move and an attack) from what the player knows. */
    Action chooseAction(GameView view);
}
