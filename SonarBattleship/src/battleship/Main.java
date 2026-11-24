package battleship;

import battleship.gui.MainFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Launches the graphical game. */
public final class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {
                // default look and feel
            }
            MainFrame f = new MainFrame();
            f.setVisible(true);
            if (args.length == 0 || !args[0].equals("--no-dialog")) f.newGameDialog();
        });
    }
}
