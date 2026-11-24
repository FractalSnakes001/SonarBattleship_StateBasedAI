package battleship.gui;

import battleship.engine.Action;
import battleship.engine.Board;
import battleship.engine.Config;
import battleship.engine.Event;
import battleship.engine.FireResult;
import battleship.engine.Game;
import battleship.engine.GameView;
import battleship.engine.Knowledge;
import battleship.engine.Placement;
import battleship.engine.Ship;
import battleship.players.Player;
import battleship.players.Players;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Main window: placement phase, turns of human and artificial players, display of both grids. */
@SuppressWarnings("serial")
public final class MainFrame extends JFrame implements BoardPanel.Listener {

    private enum Phase { IDLE, PLACING, PLAYING, OVER }
    private enum Tool { FIRE, SONAR, MOVE }

    private final Random rnd = new Random();

    // game state
    private NewGameDialog.Settings settings = new NewGameDialog.Settings();
    private Config cfg;
    private final Player[] players = new Player[2];
    private final String[] names = new String[2];
    private Game game;
    private Phase phase = Phase.IDLE;
    private int generation;
    private int humans;
    private int viewer;
    private boolean godView;

    // placement
    private final Placement[] placements = new Placement[2];
    private int placingPlayer;
    private final List<Placement.Spot> placedFleet = new ArrayList<>();
    private Placement.Spot placedSonar;
    private boolean horizontal = true;

    // human turn
    private Tool tool = Tool.FIRE;
    private int selectedShip = -1;
    /** Silent-move rule: the move planned for this turn, {ship, distance}, or null. */
    private int[] plannedMove;
    private boolean curtainUp;

    // AI
    private Timer aiTimer;
    private int aiDelay = 700;
    private boolean paused;
    private boolean aiThinking;

    // components
    private final BoardPanel left = new BoardPanel(BoardPanel.Kind.OWN);
    private final BoardPanel right = new BoardPanel(BoardPanel.Kind.ENEMY);
    private final JLabel status = new JLabel(" ");
    private final JLabel turnLabel = new JLabel(" ");
    private final JLabel hint = new JLabel(" ");
    private final JToggleButton fireB = toolButton("Fire  (F)");
    private final JToggleButton sonarB = toolButton("Sonar  (S)");
    private final JToggleButton moveB = toolButton("Move  (M)");
    private final JButton rotateB = button("Rotate  (R / right-click)");
    private final JButton randomB = button("Random");
    private final JButton undoB = button("Undo");
    private final JButton readyB = button("Ready");
    private final JButton pauseB = button("Pause");
    private final JButton stepB = button("Step");
    private final CardLayout barCards = new CardLayout();
    private final JPanel bar = new JPanel(barCards);
    private final DefaultListModel<String> logModel = new DefaultListModel<>();
    private final JList<String> log = new JList<>(logModel);
    private final JLabel[] info = {new JLabel(), new JLabel()};
    private final JCheckBoxMenuItem revealItem = new JCheckBoxMenuItem("Reveal enemy ships (debug)");
    private final JCheckBoxMenuItem beliefItem = new JCheckBoxMenuItem("Show belief state (probabilities)");
    private final battleship.belief.BeliefTracker[] trackers = new battleship.belief.BeliefTracker[2];
    private final Curtain curtain = new Curtain();

    public MainFrame() {
        super("Sonar Battleship");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout(12, 12));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 14, 12, 14));

        add(header(), BorderLayout.NORTH);
        JPanel boards = new JPanel(new GridLayout(1, 2, 14, 0));
        boards.setOpaque(false);
        boards.add(left);
        boards.add(right);
        add(boards, BorderLayout.CENTER);
        add(sidePanel(), BorderLayout.EAST);
        add(bottomBar(), BorderLayout.SOUTH);
        setJMenuBar(menu());
        setGlassPane(curtain);

        left.setListener(this);
        right.setListener(this);
        keyBindings();
        // fit the window to the screen (the bottom bar must stay visible on laptops)
        java.awt.Rectangle screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setMinimumSize(new Dimension(Math.min(900, screen.width), Math.min(560, screen.height)));
        setSize(Math.min(1320, screen.width), Math.min(820, screen.height));
        setLocationRelativeTo(null);
        if (screen.width < 1320 || screen.height < 820) setExtendedState(MAXIMIZED_BOTH);
        showIdle();
    }

    // ================================================================== layout

    private JComponent header() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        JLabel title = new JLabel("SONAR BATTLESHIP");
        title.setFont(Theme.TITLE);
        title.setForeground(Theme.TEXT);
        status.setFont(Theme.H2);
        status.setForeground(Theme.ACCENT);
        status.setHorizontalAlignment(JLabel.CENTER);
        turnLabel.setFont(Theme.BODY);
        turnLabel.setForeground(Theme.MUTED);
        p.add(title, BorderLayout.WEST);
        p.add(status, BorderLayout.CENTER);
        p.add(turnLabel, BorderLayout.EAST);
        return p;
    }

    private JComponent sidePanel() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(330, 100));
        JPanel infos = new JPanel();
        infos.setLayout(new BoxLayout(infos, BoxLayout.Y_AXIS));
        infos.setBackground(Theme.PANEL);
        infos.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.PANEL_BORDER), BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        for (JLabel l : info) {
            l.setFont(Theme.BODY);
            l.setForeground(Theme.TEXT);
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            infos.add(l);
            infos.add(Box.createVerticalStrut(8));
        }
        infos.add(legend());
        p.add(infos, BorderLayout.NORTH);

        log.setFont(Theme.MONO);
        log.setBackground(Theme.PANEL);
        log.setForeground(Theme.TEXT);
        log.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, false, false);
                l.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
                l.setOpaque(false);
                String s = String.valueOf(value);
                l.setForeground(s.startsWith("\u2605") ? Theme.ACCENT : s.contains("HIT") || s.contains("SUNK") ? new Color(0xFF8A8A)
                        : Theme.TEXT);
                return l;
            }
        });
        JScrollPane sp = new JScrollPane(log, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Theme.PANEL_BORDER), "Log",
                0, 0, Theme.BODY, Theme.MUTED));
        sp.getViewport().setBackground(Theme.PANEL);
        sp.setBackground(Theme.PANEL);
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    private JComponent legend() {
        JLabel l = new JLabel("<html><div style='color:#8DA2BD;font-size:10px'>"
                + "<b style='color:#E8EEF6'>Legend</b><br>"
                + "<span style='color:#FFFFFF'>\u25cb</span> rocket missed &nbsp; "
                + "<span style='color:#EF4444'>\u25cf</span> hit<br>"
                + "<span style='color:#34D399'>\u25cf</span> sonar contact &nbsp; "
                + "<span style='color:#34D399'>\u00b7</span> sonar: empty<br>"
                + "<span style='color:#B98AF0'>\u25a0</span> your sonar boat &nbsp; "
                + "<span style='color:#FF3EC8'>\u25a0</span> your sonar boat hit<br>"
                + "faded = may be outdated"
                + "</div></html>");
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JComponent bottomBar() {
        bar.setOpaque(false);
        JPanel actions = flow();
        ButtonGroup group = new ButtonGroup();
        for (JToggleButton b : new JToggleButton[]{fireB, sonarB, moveB}) {
            group.add(b);
            actions.add(b);
        }
        fireB.addActionListener(e -> setTool(Tool.FIRE));
        sonarB.addActionListener(e -> setTool(Tool.SONAR));
        moveB.addActionListener(e -> setTool(Tool.MOVE));

        JPanel place = flow();
        place.add(rotateB);
        place.add(randomB);
        place.add(undoB);
        place.add(readyB);
        rotateB.addActionListener(e -> rotate());
        randomB.addActionListener(e -> randomPlacement());
        undoB.addActionListener(e -> undoPlacement());
        readyB.addActionListener(e -> placementDone());

        JPanel ai = flow();
        ai.add(pauseB);
        ai.add(stepB);
        pauseB.addActionListener(e -> togglePause());
        stepB.addActionListener(e -> step());

        bar.add(actions, "actions");
        bar.add(place, "place");
        bar.add(ai, "ai");
        bar.add(flow(), "none");

        hint.setFont(Theme.BODY);
        hint.setForeground(Theme.TEXT);
        JPanel p = new JPanel(new BorderLayout(14, 0));
        p.setOpaque(false);
        p.add(bar, BorderLayout.WEST);
        p.add(hint, BorderLayout.CENTER);
        return p;
    }

    private static JPanel flow() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.setOpaque(false);
        return p;
    }

    private static JToggleButton toolButton(String s) {
        JToggleButton b = new JToggleButton(s);
        b.setFont(Theme.BODY.deriveFont(Font.BOLD));
        b.setFocusable(false);
        return b;
    }

    private static JButton button(String s) {
        JButton b = new JButton(s);
        b.setFont(Theme.BODY.deriveFont(Font.BOLD));
        b.setFocusable(false);
        return b;
    }

    private JMenuBar menu() {
        JMenuBar mb = new JMenuBar();
        JMenu gm = new JMenu("Game");
        JMenuItem ng = new JMenuItem("New game\u2026");
        ng.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK));
        ng.addActionListener(e -> newGameDialog());
        JMenuItem again = new JMenuItem("Play again (same settings)");
        again.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK));
        again.addActionListener(e -> newGame(settings));
        JMenuItem quit = new JMenuItem("Quit");
        quit.addActionListener(e -> System.exit(0));
        gm.add(ng);
        gm.add(again);
        gm.addSeparator();
        gm.add(quit);

        JMenu vm = new JMenu("View");
        revealItem.addActionListener(e -> refresh());
        vm.add(revealItem);
        beliefItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK));
        beliefItem.addActionListener(e -> refresh());
        vm.add(beliefItem);
        vm.addSeparator();
        ButtonGroup sg = new ButtonGroup();
        int[] delays = {50, 300, 700, 1500};
        String[] labels = {"AI speed: very fast", "AI speed: fast", "AI speed: normal", "AI speed: slow"};
        for (int i = 0; i < delays.length; i++) {
            final int d = delays[i];
            JRadioButtonMenuItem it = new JRadioButtonMenuItem(labels[i], d == aiDelay);
            it.addActionListener(e -> aiDelay = d);
            sg.add(it);
            vm.add(it);
        }

        JMenu hm = new JMenu("Help");
        JMenuItem rules = new JMenuItem("Rules");
        rules.addActionListener(e -> showRules());
        hm.add(rules);
        mb.add(gm);
        mb.add(vm);
        mb.add(hm);
        return mb;
    }

    private void keyBindings() {
        JComponent root = getRootPane();
        bind(root, "F", () -> { if (humanTurn()) setTool(Tool.FIRE); });
        bind(root, "S", () -> { if (humanTurn()) setTool(Tool.SONAR); });
        bind(root, "M", () -> { if (humanTurn()) setTool(Tool.MOVE); });
        bind(root, "R", () -> { if (phase == Phase.PLACING) rotate(); });
        bind(root, "SPACE", () -> { if (godView && phase == Phase.PLAYING) togglePause(); });
        bind(root, "ENTER", () -> {
            if (phase == Phase.PLACING && placingLength() == 0) placementDone();
        });
        bind(root, "ESCAPE", () -> {
            selectedShip = -1;
            left.setSelection(-1, null);
        });
    }

    private static void bind(JComponent c, String key, Runnable r) {
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), key);
        c.getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { r.run(); }
        });
    }

    // ================================================================== game set-up

    private void showIdle() {
        phase = Phase.IDLE;
        status.setText("Game \u2192 New game (Ctrl+N) to start");
        left.setTitle("Your grid", "");
        right.setTitle("Enemy waters", "");
        barCards.show(bar, "none");
        hint.setText("Choose the players and the rules in the New game dialog.");
        refreshInfo();
    }

    public void newGameDialog() {
        NewGameDialog.Settings s = new NewGameDialog(this, settings).showDialog();
        if (s != null) {
            settings = s;
            newGame(s);
        }
    }

    /** Starts a game with default rules without showing the dialog (used for quick tests). */
    public void startDirect(String player1, String player2) {
        startDirect(player1, player2, new Config());
    }

    /** Same, with the given rules. */
    public void startDirect(String player1, String player2, Config rules) {
        NewGameDialog.Settings s = new NewGameDialog.Settings();
        s.config = rules.copy();
        s.players = new String[]{player1, player2};
        s.names = new String[]{player1.equals(Players.HUMAN) ? "You" : player1, player2};
        settings = s;
        newGame(s);
    }

    private void newGame(NewGameDialog.Settings s) {
        generation++;
        if (aiTimer != null) aiTimer.stop();
        aiThinking = false;
        paused = false;
        pauseB.setText("Pause");
        cfg = s.config.copy();
        humans = 0;
        for (int i = 0; i < 2; i++) {
            players[i] = Players.create(s.players[i]);
            names[i] = s.names[i];
            if (players[i] == null) humans++;
            placements[i] = players[i] == null ? null : players[i].placeShips(cfg.copy(), new Random()); // each player its own Random and Config
        }
        if (names[0].equals(names[1])) {
            names[0] += " #1";
            names[1] += " #2";
        }
        godView = humans == 0;
        game = null;
        logModel.clear();
        trackers[0] = trackers[1] = null;
        left.setConfig(cfg);
        right.setConfig(cfg);
        left.setHighlight(null);
        right.setHighlight(null);
        left.setDimmed(false, null);
        right.setDimmed(false, null);
        addLog("\u2605 New game: " + names[0] + " vs " + names[1]);
        addLog("  " + cfg);
        nextPlacementOrPlay();
    }

    private void nextPlacementOrPlay() {
        for (int i = 0; i < 2; i++)
            if (placements[i] == null) {
                startPlacement(i);
                return;
            }
        startPlay();
    }

    // ================================================================== placement

    private void startPlacement(int p) {
        phase = Phase.PLACING;
        placingPlayer = p;
        viewer = p;
        placedFleet.clear();
        placedSonar = null;
        horizontal = true;
        left.setKind(BoardPanel.Kind.OWN);
        left.setIncoming(null);
        left.clearInteraction();
        right.setKind(BoardPanel.Kind.ENEMY);
        right.setKnowledge(null);
        right.setBoard(null);
        right.setReveal(false);
        right.clearInteraction();
        right.setDimmed(true, "Place your ships on the left grid");
        left.setTitle(names[p] + " \u2014 your grid", "placement");
        right.setTitle("Enemy waters", "");
        barCards.show(bar, "place");
        status.setText(names[p] + ": place your ships");
        turnLabel.setText(" ");
        updatePlacementView();
        refreshInfo();
        if (humans == 2) showCurtain(names[p] + ": place your ships (the other player looks away)", null);
    }

    private int placingLength() {
        return placedFleet.size() < cfg.fleet.length ? cfg.fleet[placedFleet.size()] : placedSonar == null ? cfg.sonarBoatLength : 0;
    }

    private boolean placingSonarBoat() {
        return placedFleet.size() == cfg.fleet.length && placedSonar == null;
    }

    private boolean candidateValid(Placement.Spot s) {
        boolean[][] used = new boolean[cfg.size][cfg.size];
        for (int k = 0; k < placedFleet.size(); k++) Placement.mark(used, placedFleet.get(k), cfg.fleet[k]);
        return Placement.fits(cfg, used, s, placingLength());
    }

    private void updatePlacementView() {
        left.setBoard(Board.preview(cfg, placedFleet, placedSonar));
        int len = placingLength();
        if (len > 0 && left.hoverRow() >= 0) {
            Placement.Spot s = new Placement.Spot(left.hoverRow(), left.hoverCol(), horizontal);
            left.setPreview(BoardPanel.cells(s.row, s.col, len, horizontal), candidateValid(s), placingSonarBoat());
        } else {
            left.setPreview(null, false, false);
        }
        readyB.setEnabled(len == 0);
        readyB.setBackground(len == 0 ? Theme.ACCENT : null);
        if (placedFleet.size() < cfg.fleet.length) {
            status.setText(names[placingPlayer] + " \u2014 step 1/3: place your fleet (" + (placedFleet.size() + 1) + "/"
                    + cfg.fleet.length + ")");
            right.setDimmed(true, "Step 1/3: place your ships on the left grid");
        } else if (placedSonar == null) {
            status.setText(names[placingPlayer] + " \u2014 step 2/3: place your sonar boat (purple)");
            right.setDimmed(true, "Step 2/3: place your sonar boat (purple)");
        } else {
            status.setText(names[placingPlayer] + " \u2014 step 3/3: click Ready (or press Enter)");
            right.setDimmed(true, "Click here or press Enter to start the game");
        }
        undoB.setEnabled(!placedFleet.isEmpty());
        if (placedFleet.size() < cfg.fleet.length) {
            String name = new Ship(len, 0, 0, true, false).name();
            hint.setText("Place your " + name + " (length " + len + "): click on your grid. "
                    + "Right-click or R to rotate (" + (horizontal ? "horizontal" : "vertical") + ").");
        } else if (placedSonar == null) {
            hint.setText("<html>Now place <b>your sonar boat</b> (length " + cfg.sonarBoatLength
                    + "): your sonar works until it is sunk.</html>");
        } else {
            hint.setText("Placement complete. Click Ready.");
        }
    }

    private void rotate() {
        if (phase != Phase.PLACING) return;
        horizontal = !horizontal;
        updatePlacementView();
    }

    private void undoPlacement() {
        if (placedSonar != null) placedSonar = null;
        else if (!placedFleet.isEmpty()) placedFleet.remove(placedFleet.size() - 1);
        updatePlacementView();
    }

    private void randomPlacement() {
        Placement p = Placement.random(cfg, rnd);
        placedFleet.clear();
        placedFleet.addAll(p.fleet);
        placedSonar = p.sonarBoat;
        updatePlacementView();
    }

    private void placementDone() {
        Placement p = new Placement(placedFleet, placedSonar);
        String err = p.check(cfg);
        if (err != null) {
            hint.setText("Invalid placement: " + err);
            return;
        }
        placements[placingPlayer] = p;
        left.setPreview(null, false, false);
        nextPlacementOrPlay();
    }

    // ================================================================== playing

    private void startPlay() {
        int first = settings.first < 0 ? rnd.nextInt(2) : settings.first;
        game = new Game(cfg, placements[0], placements[1], first);
        phase = Phase.PLAYING;
        right.setDimmed(false, null);
        left.clearInteraction();
        right.clearInteraction();
        if (godView) viewer = 0;
        else if (humans == 1) viewer = players[0] == null ? 0 : 1;
        addLog("\u2605 " + names[first] + " starts");
        nextTurn();
    }

    private boolean humanTurn() {
        return phase == Phase.PLAYING && game != null && !game.isOver() && players[game.currentPlayer()] == null && !curtainUp;
    }

    private void nextTurn() {
        if (game.isOver()) {
            endGame();
            return;
        }
        int p = game.currentPlayer();
        if (players[p] == null) {
            if (humans == 2 && viewer != p) {
                viewer = p;
                refresh();
                showCurtain(names[p] + ": your turn (click to continue)", null);
            }
            barCards.show(bar, "actions");
            status.setText(names[p] + ": your turn");
            setTool(Tool.FIRE);
            refresh();
        } else {
            barCards.show(bar, godView ? "ai" : "none");
            status.setText(names[p] + " is thinking\u2026");
            hint.setText(godView ? (paused ? "Paused: Step to play one action, Resume (space) to continue." : " ")
                    : "Wait for your opponent.");
            left.clearInteraction();
            right.clearInteraction();
            refresh();
            if (!paused) scheduleAI(aiDelay);
        }
    }

    private void scheduleAI(int delay) {
        if (aiTimer != null) aiTimer.stop();
        final int gen = generation;
        aiTimer = new Timer(delay, e -> { if (gen == generation) runAI(); });
        aiTimer.setRepeats(false);
        aiTimer.start();
    }

    private void runAI() {
        if (aiThinking || game == null || game.isOver()) return;
        final int gen = generation;
        final int p = game.currentPlayer();
        final Player pl = players[p];
        if (pl == null) return;
        final GameView view = game.viewFor(p);
        aiThinking = true;
        new SwingWorker<Action, Void>() {
            private long time;

            @Override
            protected Action doInBackground() {
                long t0 = System.nanoTime();
                Action a = pl.chooseAction(view);
                time = System.nanoTime() - t0;
                return a;
            }

            @Override
            protected void done() {
                aiThinking = false;
                if (gen != generation) return;
                Action a;
                try {
                    a = get();
                } catch (Exception ex) {
                    addLog("\u2605 " + names[p] + " crashed: " + ex.getCause());
                    a = null;
                }
                if (a == null || game.illegalReason(a) != null) {
                    addLog("\u2605 illegal action from " + names[p] + " (" + a + "): random action played");
                    List<Action> legal = game.legalActions();
                    a = legal.get(rnd.nextInt(legal.size()));
                }
                if (time > 1_000_000_000L) addLog(String.format("  (%s thought %.1f s)", names[p], time / 1e9));
                apply(a);
            }
        }.execute();
    }

    private void togglePause() {
        paused = !paused;
        pauseB.setText(paused ? "Resume" : "Pause");
        if (!paused && phase == Phase.PLAYING && players[game.currentPlayer()] != null) scheduleAI(50);
        if (paused && aiTimer != null) aiTimer.stop();
        if (phase == Phase.PLAYING && godView)
            hint.setText(paused ? "Paused: Step to play one action, Resume (space) to continue." : " ");
    }

    private void step() {
        if (!paused) togglePause();
        if (phase == Phase.PLAYING && players[game.currentPlayer()] != null) runAI();
    }

    private void act(Action a) {
        if (!humanTurn()) return;
        String err = game.illegalReason(a);
        if (err != null) {
            hint.setText("<html><span style='color:#FF8A8A'>Not allowed: " + err + "</span></html>");
            return;
        }
        apply(a);
    }

    private void apply(Action a) {
        Event e = game.play(a);
        addLog(logLine(e));
        selectedShip = -1;
        plannedMove = null;
        left.setSelection(-1, null);
        left.setPreview(null, true, false);
        nextTurn();
    }

    private void endGame() {
        phase = Phase.OVER;
        if (aiTimer != null) aiTimer.stop();
        barCards.show(bar, "none");
        String msg;
        if (game.winner() < 0) msg = "Draw (" + game.endReason() + ")";
        else msg = names[game.winner()] + " wins \u2014 " + game.endReason();
        status.setText(msg);
        hint.setText("Game over after " + game.turn() + " actions. Game \u2192 Play again (Ctrl+A) or New game (Ctrl+N).");
        addLog("\u2605 " + msg);
        for (int i = 0; i < 2; i++)
            if (players[i] instanceof battleship.players.AIPlayer) {
                battleship.players.TimeStats t = ((battleship.players.AIPlayer) players[i]).timeStats();
                addLog(String.format("  %s: time per action: mean %.1f ms, std. dev. %.1f ms, max %.0f ms", names[i],
                        t.mean(), t.stdDev(), t.max()));
                int over = t.countOver(battleship.players.AIPlayer.TIME_LIMIT_MS);
                if (over > 0) addLog(String.format("  %s: %d action(s) over %d ms", names[i], over,
                        battleship.players.AIPlayer.TIME_LIMIT_MS));
            }
        left.clearInteraction();
        right.clearInteraction();
        refresh();
        right.setReveal(true);
    }

    // ================================================================== tools

    private void setTool(Tool t) {
        tool = t;
        if (t == Tool.MOVE && plannedMove != null) { // choose the move again
            plannedMove = null;
            left.setPreview(null, true, false);
        }
        fireB.setSelected(t == Tool.FIRE);
        sonarB.setSelected(t == Tool.SONAR);
        moveB.setSelected(t == Tool.MOVE);
        selectedShip = -1;
        left.setSelection(-1, null);
        right.setHover(t == Tool.FIRE ? BoardPanel.Hover.CELL : t == Tool.SONAR ? BoardPanel.Hover.SONAR : BoardPanel.Hover.NONE);
        int p = game.currentPlayer();
        sonarB.setEnabled(game.sonarAvailable(p));
        switch (t) {
            case FIRE:
                hint.setText("Click a square of the enemy grid to fire a rocket.");
                break;
            case SONAR:
                hint.setText(game.sonarAvailable(p) ? "Click a square of the enemy grid: the sonar shows which squares around it hold a ship."
                        : "Your sonar boat is sunk: no more sonar.");
                break;
            case MOVE:
                hint.setText("Optional silent move: click one of your undamaged boats, then a highlighted"
                        + " square. You will then fire or launch a sonar.");
                break;
            default:
                break;
        }
    }

    // ================================================================== mouse

    @Override
    public void clicked(BoardPanel src, int r, int c, boolean rightButton) {
        if (phase == Phase.PLACING && src == right && placingLength() == 0) {
            placementDone();
            return;
        }
        if (r < 0 || c < 0) return;
        if (phase == Phase.PLACING && src == left) {
            if (rightButton) {
                rotate();
                return;
            }
            int len = placingLength();
            if (len == 0) return;
            Placement.Spot s = new Placement.Spot(r, c, horizontal);
            if (!candidateValid(s)) {
                hint.setText("<html><span style='color:#FF8A8A'>Cannot place it there.</span></html>");
                return;
            }
            if (placingSonarBoat()) placedSonar = s; else placedFleet.add(s);
            updatePlacementView();
            return;
        }
        if (!humanTurn()) return;
        Board mine = game.board(game.currentPlayer());
        if (rightButton && src == left && plannedMove != null) { // cancel the planned silent move
            setTool(Tool.MOVE);
            return;
        }
        switch (tool) {
            case FIRE:
                if (src == right) act(withPlannedMove(Action.fire(r, c)));
                break;
            case SONAR:
                if (src == right) act(withPlannedMove(Action.sonar(r, c)));
                break;
            case MOVE:
                if (src != left) break;
                if (selectedShip >= 0) {
                    for (int d = -1; d <= 1; d += 2) {
                        if (!mine.canMove(selectedShip, d)) continue;
                        int[] t = mine.moveTarget(selectedShip, d);
                        if (t[0] == r && t[1] == c) {
                            planMove(mine, selectedShip, d);
                            return;
                        }
                    }
                }
                selectShip(mine, r, c);
                break;
            default:
                break;
        }
    }

    private Action withPlannedMove(Action a) {
        return plannedMove == null ? a : a.withMove(plannedMove[0], plannedMove[1]);
    }

    /** Silent-move rule: remembers the move, shows the ship's new position, and asks for the attack. */
    private void planMove(Board mine, int ship, int d) {
        Ship s = mine.boat(ship);
        setTool(Tool.FIRE);
        plannedMove = new int[]{ship, d};
        List<int[]> cells = new ArrayList<>();
        for (int k = 0; k < s.length(); k++)
            cells.add(new int[]{s.cellRow(k) + (s.isHorizontal() ? 0 : d), s.cellCol(k) + (s.isHorizontal() ? d : 0)});
        left.setPreview(cells, true, false);
        hint.setText("<html>Planned silent move: " + s.name() + (d > 0 ? " forward" : " backward")
                + " (green). Now <b>fire</b> or launch a <b>sonar</b> to end your turn. Press M (or right-click your grid)"
                + " to change the move.</html>");
    }

    private void selectShip(Board mine, int r, int c) {
        selectedShip = -1;
        List<int[]> targets = new ArrayList<>();
        for (int i = 0; i < mine.boatCount(); i++)
            if (mine.boat(i).occupies(r, c)) {
                Ship s = mine.boat(i);
                if (!s.isIntact()) {
                    hint.setText("<html><span style='color:#FF8A8A'>A damaged boat cannot move.</span></html>");
                    left.setSelection(-1, null);
                    return;
                }
                selectedShip = i;
                for (int d = -1; d <= 1; d += 2) if (mine.canMove(i, d)) targets.add(mine.moveTarget(i, d));
                hint.setText(targets.isEmpty() ? "This boat is blocked." : "Click a highlighted square to move the "
                        + s.name() + " one square.");
            }
        left.setSelection(selectedShip, targets);
    }

    @Override
    public void hovered(BoardPanel src, int r, int c) {
        if (phase == Phase.PLACING && src == left) updatePlacementView();
    }

    // ================================================================== display

    private void refresh() {
        if (game == null) {
            refreshInfo();
            return;
        }
        int t = game.turn();
        left.setCurrentTurn(t);
        right.setCurrentTurn(t);
        turnLabel.setText("Action " + t + " / " + cfg.maxTurns);
        if (godView) {
            for (int k = 0; k < 2; k++) {
                BoardPanel bp = k == 0 ? left : right;
                bp.setKind(BoardPanel.Kind.OWN);
                bp.setBoard(game.board(k));
                bp.setIncoming(eventsBy(1 - k, game.history()));
                bp.setTitle(names[k] + "'s grid", sub(k));
            }
        } else {
            int v = viewer;
            List<Event> h = game.historyFor(v);
            left.setKind(BoardPanel.Kind.OWN);
            left.setBoard(game.board(v));
            left.setIncoming(eventsBy(1 - v, h));
            left.setTitle(names[v] + " \u2014 your grid", sub(v));
            right.setKind(BoardPanel.Kind.ENEMY);
            right.setKnowledge(new Knowledge(cfg, v, h));
            right.setBoard(game.board(1 - v));
            right.setReveal(phase == Phase.OVER || (revealItem.isSelected() && humans < 2));
            right.setTitle("Enemy waters \u2014 " + names[1 - v], game.sonarAvailable(v) ? "your sonar: available"
                    : "your sonar: LOST");
        }
        refreshBelief();
        // highlight the target of the last visible action
        left.setHighlight(null);
        right.setHighlight(null);
        List<Event> hist = game.history();
        if (!hist.isEmpty()) {
            Event e = hist.get(hist.size() - 1);
            {
                BoardPanel target = godView ? (e.player == 0 ? right : left) : (e.player == viewer ? right : left);
                target.setHighlight(new int[]{e.action.row, e.action.col});
            }
        }
        refreshInfo();
    }

    /** Belief-state overlay: probabilities computed from what the attacking player knows. */
    private void refreshBelief() {
        left.setBelief(null, null);
        right.setBelief(null, null);
        if (!beliefItem.isSelected() || game == null) return;
        for (int target = 0; target < 2; target++) {
            BoardPanel bp = godView ? (target == 0 ? left : right) : (target == viewer ? null : right);
            if (bp == null) continue;
            int attacker = 1 - target;
            if (humans == 2 && attacker != viewer) continue;
            if (trackers[attacker] == null) trackers[attacker] = new battleship.belief.BeliefTracker(cfg, attacker);
            battleship.belief.BeliefState b = trackers[attacker].update(game.historyFor(attacker));
            if (b.isWon() || b.getNbrOfStates() == 0) continue;
            bp.setBelief(flat(b.getBoatProbabilities()), flat(b.getSonarBoatProbabilities()));
            String nStates = b.getNbrOfStates() >= 1e6 ? String.format("%.1fM", b.getNbrOfStates() / 1e6)
                    : String.format("%,.0f", b.getNbrOfStates());
            bp.setTitle(godView ? names[target] + "'s grid" : "Enemy waters \u2014 " + names[target],
                    (players[attacker] == null && !godView ? "your" : names[attacker] + "'s") + " belief: " + nStates
                            + " states");
        }
    }

    /** A 7x7 array p[row][col] as an array indexed by row * 7 + col (used by the display). */
    private static double[] flat(double[][] g) {
        int n = g.length;
        double[] f = new double[n * n];
        for (int r = 0; r < n; r++) System.arraycopy(g[r], 0, f, r * n, n);
        return f;
    }

    private String sub(int p) {
        return "sonar " + (game.sonarAvailable(p) ? "OK" : "LOST");
    }

    private static List<Event> eventsBy(int p, List<Event> h) {
        List<Event> l = new ArrayList<>();
        for (Event e : h) if (e.player == p) l.add(e);
        return l;
    }

    private void refreshInfo() {
        for (int i = 0; i < 2; i++) {
            if (cfg == null || names[i] == null) {
                info[i].setText(" ");
                continue;
            }
            String type = players[i] == null ? "human" : players[i].name();
            StringBuilder sb = new StringBuilder("<html><b>" + names[i] + "</b> <span style='color:#8DA2BD'>(" + type + ")</span>");
            if (game != null) {
                Board b = game.board(i);
                sb.append("<br><span style='color:#8DA2BD'>boats afloat:</span> ").append(b.boatsAfloat()).append(" / ")
                        .append(cfg.boats);
                sb.append(" &nbsp; <span style='color:#8DA2BD'>sonar:</span> ")
                        .append(game.sonarAvailable(i) ? "OK" : "<span style='color:#FF8A8A'>lost</span>");
                sb.append(" &nbsp; <span style='color:#8DA2BD'>score:</span> ").append(game.score(i));
                if (game.currentPlayer() == i && phase == Phase.PLAYING) sb.append(" &nbsp; <span style='color:#F5A524'>\u25c0 to play</span>");
            }
            info[i].setText(sb.append("</html>").toString());
        }
    }

    private String logLine(Event full) {
        Event e = full;
        boolean showFull = godView || (humans == 1 && players[full.player] == null);
        if (!showFull) e = game.historyFor(1 - full.player).get(full.turn);
        String who = names[e.player];
        if (who.length() > 11) who = who.substring(0, 11);
        String line = String.format("%3d %-11s %s", e.turn + 1, who, e.describe());
        if (e.fireResult == FireResult.SONAR_BOAT_SUNK) line = "\u2605" + line.substring(1);
        return line;
    }

    private void addLog(String s) {
        logModel.addElement(s);
        int last = logModel.size() - 1;
        SwingUtilities.invokeLater(() -> log.ensureIndexIsVisible(last));
    }

    private void showRules() {
        Config c = cfg != null ? cfg : settings.config;
        String txt = "<html><body style='width:520px;font-family:sans-serif'>"
                + "<h2>Sonar Battleship</h2>"
                + "<p>Each player hides on his " + c.size + "\u00d7" + c.size + " grid a fleet (lengths 4, 3 and 2) and his"
                + " <b>sonar boat</b> (length " + c.sonarBoatLength + "). Boats never share a square.</p>"
                + "<p>On his turn a player:</p><ol>"
                + "<li><b>may</b> move one of his undamaged boats (the sonar boat included) one square forward or backward"
                + " along its axis, onto free squares. This move is <b>silent</b>: the opponent never learns whether a boat moved;</li>"
                + "<li><b>must</b> then <b>fire</b> a rocket at a square of the enemy grid (MISS, HIT or SUNK), or launch a"
                + " <b>sonar</b> centred on a square of the enemy grid: it shows which squares of the 3\u00d73 area hold a boat"
                + " (type and damage are not shown).</li></ol>"
                + "<p>A damaged boat can no longer move. A sunk boat is announced and its squares are revealed. A player can use"
                + " his sonar as long as his sonar boat is afloat.</p>"
                + "<p><b>You win by sinking all the enemy boats, the sonar boat included.</b> After " + c.maxTurns
                + " actions, the higher score wins: 1 point per square hit, plus, for each boat sunk, a bonus equal to its"
                + " length (+2 for the sonar boat).</p>"
                + "<p>You see where the enemy rockets and sonars land on your grid, never his moves.</p></body></html>";
        JOptionPane.showMessageDialog(this, txt, "Rules", JOptionPane.INFORMATION_MESSAGE);
    }

    // ================================================================== curtain

    private void showCurtain(String message, Runnable after) {
        curtainUp = true;
        curtain.message = message;
        curtain.after = after;
        curtain.setVisible(true);
        curtain.repaint();
    }

    /** Hides the screen between the turns of two human players. */
    private final class Curtain extends JPanel {
        String message = "";
        Runnable after;

        Curtain() {
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    setVisible(false);
                    curtainUp = false;
                    if (after != null) after.run();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Theme.BG);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setFont(Theme.TITLE);
            g.setColor(Theme.TEXT);
            int w = g.getFontMetrics().stringWidth(message);
            g.drawString(message, (getWidth() - w) / 2, getHeight() / 2);
            g.setFont(Theme.BODY);
            g.setColor(Theme.MUTED);
            String s = "click anywhere to continue";
            g.drawString(s, (getWidth() - g.getFontMetrics().stringWidth(s)) / 2, getHeight() / 2 + 30);
        }
    }
}
