package battleship.players;

import battleship.engine.Action;
import battleship.engine.Board;
import battleship.engine.GameView;

import java.util.Random;

/** Fires at a random square (sometimes launches a sonar instead), and sometimes moves a random boat. */
public class RandomAI implements Player {
    private final Random rnd = new Random();

    @Override
    public String name() { return "Random AI"; }

    @Override
    public Action chooseAction(GameView view) {
        int n = view.config.size;
        Action a = view.mySonarAvailable && rnd.nextDouble() < 0.2 ? Action.sonar(rnd.nextInt(n), rnd.nextInt(n))
                : Action.fire(rnd.nextInt(n), rnd.nextInt(n));
        if (rnd.nextDouble() < 0.15) { // a random move
            Board mine = view.myBoard;
            int boat = rnd.nextInt(mine.boatCount()), dir = rnd.nextBoolean() ? 1 : -1;
            if (mine.canMove(boat, dir)) a = a.withMove(boat, dir);
        }
        return a;
    }
}
