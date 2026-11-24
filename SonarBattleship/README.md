# Sonar Battleship

A variant of Battleship with silent moves and sonars, used for the Artificial Intelligence project.
The project description (rules, classes, work to do) is in the PDF/Word document published with this code.
It needs Java 11 or later and no external library.

## Running it

**Eclipse:** File → Import… → General → *Existing Projects into Workspace* → *Select archive file* → choose the
archive → Finish. Then right-click `battleship/Main.java` → Run As → Java Application.

From a terminal:

```
javac -d bin $(find src -name "*.java")
java -cp bin battleship.Main
```

On Windows (PowerShell), compile with:
`javac -d bin (Get-ChildItem -Recurse src -Filter *.java).FullName`

To play many games between two AIs without any display (here 100 games of your AI against BasicAI):

```
java -cp bin battleship.Arena AI BasicAI 100
```

It reports the results and statistics about the time taken by your AI (mean, standard deviation, median,
minimum, maximum, and number of actions over the time limit of 5 seconds). Options: `-csv times.csv` writes every
time measure to a file (game, turn, player, time in ms, size of the belief state); `-limit 2000` changes the time
limit used in the report.

## Rules

- Each player hides on his 7×7 grid a fleet (lengths 4, 3 and 2) and his **sonar boat** (length 2). Boats never
  share a square.
- On his turn, a player:
  1. **may** move one of his undamaged boats (the sonar boat included) one square forward or backward along its
     axis, onto free squares. The move is **silent**: the opponent never learns whether a boat moved;
  2. **must** then **fire** a rocket at a square of the enemy grid (MISS, HIT or SUNK), or launch a **sonar**
     centred on a square of the enemy grid: it shows which squares of the 3×3 area hold a boat (type and damage
     are not shown).
- A damaged boat can no longer move. A sunk boat is announced, and its squares are revealed.
- A player can use his sonar as long as his sonar boat is afloat.
- Each player sees where the opponent's rockets and sonars land on his grid, never the opponent's moves.
- **Winning:** sink all the enemy boats, the sonar boat included. After 200 actions in total, the higher score
  wins: 1 point per square hit, plus, for each boat sunk, a bonus equal to its length (+2 for the sonar boat). The
  game is a draw if the scores are equal.

## The game window

- **Players** (Game → New game): Human, AI (your AI, `AI.java`), BasicAI (a simple rule-based opponent),
  RandomAI. Two AIs can play against each other (Pause / Step).
- **Your turn:** F / S / M choose the tool (Fire, Sonar, Move). With Move, click one of your boats, then a
  highlighted square: the move is planned (shown in green); then fire or launch a sonar to end your turn.
- **View → Show belief state** (Ctrl+B) colours the enemy grid with the probability that each square holds a boat,
  and shows in purple the probability of the enemy sonar boat. **View → Reveal enemy ships** shows the real enemy
  boats (for debugging).
- At the end of a game, the log shows the mean, the standard deviation and the maximum of the time taken by your AI
  to choose its actions, and the number of actions over 5 seconds.

## Code layout

| Package | Content |
|---|---|
| `battleship.engine` | The rules: `Config` (fixed rules), `Action`, `Board`, `Ship`, `Placement`, `Event`, `FireResult`, `Game` (the referee), `GameView` (what a player knows), `Knowledge` (a simple summary used by the display and BasicAI) |
| `battleship.belief` | Belief states: `BeliefState`, `Result`, `Plans`, `StateSampler` (random states of a belief state), and internal classes |
| `battleship.players` | `AI` (**your AI: the file to complete**), `AIPlayer` (connects your AI to the game and measures its time), `TimeStats` (time statistics), `BasicAI`, `RandomAI`, `Player`, `Players` |
| `battleship.gui` | The game window |
| `battleship` | `Main` (game window) and `Arena` (many games without display) |

You must only modify `src/battleship/players/AI.java`. Your whole AI must be contained in the `AI` class (fields,
methods and nested classes). A new `AI` object is created for each game. Each action must be chosen in less than
5 seconds.
