package battleship.gui;

import battleship.engine.Config;
import battleship.players.Players;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/** Dialog to choose the players of a new game (the rules are fixed). */
@SuppressWarnings("serial")
final class NewGameDialog extends JDialog {

    /** Result of the dialog. */
    static final class Settings {
        String[] players = {Players.HUMAN, "AI"};
        String[] names = {"Player 1", "Player 2"};
        /** 0, 1 or -1 for random. */
        int first = 0;
        Config config = new Config();
    }

    private Settings result;

    private final JComboBox<String>[] playerBox;
    private final JTextField[] nameField = {new JTextField(12), new JTextField(12)};
    private final JComboBox<String> firstBox = new JComboBox<>(new String[]{"Player 1", "Player 2", "Random"});

    @SuppressWarnings({"unchecked", "rawtypes"})
    NewGameDialog(JFrame owner, Settings previous) {
        super(owner, "New game", true);
        playerBox = new JComboBox[]{new JComboBox<>(Players.BUILT_IN), new JComboBox<>(Players.BUILT_IN)};
        Settings s = previous == null ? new Settings() : previous;
        for (int i = 0; i < 2; i++) {
            playerBox[i].setEditable(true);
            playerBox[i].setSelectedItem(s.players[i]);
            playerBox[i].setToolTipText("Human, AI (yours), BasicAI, RandomAI, or the full name of a class implementing battleship.players.Player");
            nameField[i].setText(s.names[i]);
        }
        firstBox.setSelectedIndex(s.first < 0 ? 2 : s.first);

        JPanel players = new JPanel(new GridBagLayout());
        players.setBorder(BorderFactory.createTitledBorder("Players"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 6, 3, 6);
        g.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < 2; i++) {
            g.gridy = i;
            g.gridx = 0;
            players.add(new JLabel("Player " + (i + 1)), g);
            g.gridx = 1;
            players.add(playerBox[i], g);
            g.gridx = 2;
            players.add(new JLabel("name"), g);
            g.gridx = 3;
            players.add(nameField[i], g);
        }
        g.gridy = 2;
        g.gridx = 0;
        players.add(new JLabel("First to play"), g);
        g.gridx = 1;
        players.add(firstBox, g);

        JButton ok = new JButton("Start");
        JButton cancel = new JButton("Cancel");
        ok.addActionListener(e -> accept());
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(ok);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
        content.add(players, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(ok);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Shows the dialog and returns the settings, or null if cancelled. */
    Settings showDialog() {
        setVisible(true);
        return result;
    }

    private void accept() {
        Settings s = new Settings();
        try {
            for (int i = 0; i < 2; i++) {
                s.players[i] = String.valueOf(playerBox[i].getSelectedItem()).trim();
                Players.create(s.players[i]); // check that the class can be created
                String n = nameField[i].getText().trim();
                s.names[i] = n.isEmpty() ? "Player " + (i + 1) : n;
            }
            s.first = firstBox.getSelectedIndex() == 2 ? -1 : firstBox.getSelectedIndex();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid settings", JOptionPane.ERROR_MESSAGE);
            return;
        }
        result = s;
        dispose();
    }
}
