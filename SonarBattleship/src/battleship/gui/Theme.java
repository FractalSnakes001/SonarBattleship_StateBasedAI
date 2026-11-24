package battleship.gui;

import java.awt.Color;
import java.awt.Font;

/** Colours and fonts of the GUI. */
final class Theme {
    private Theme() {}

    static final Color BG = new Color(0x0E1726);
    static final Color PANEL = new Color(0x16233A);
    static final Color PANEL_BORDER = new Color(0x26395A);
    static final Color TEXT = new Color(0xE8EEF6);
    static final Color MUTED = new Color(0x8DA2BD);
    static final Color ACCENT = new Color(0xF5A524);

    static final Color OCEAN_TOP = new Color(0x1F5A92);
    static final Color OCEAN_BOTTOM = new Color(0x123D69);
    static final Color FOG = new Color(0x0B1A2E);
    static final Color GRID = new Color(255, 255, 255, 38);

    static final Color SHIP = new Color(0xB4BFCB);
    static final Color SHIP_EDGE = new Color(0x5F6E7E);
    static final Color WRECK = new Color(0x3B434D);
    static final Color HIT = new Color(0xEF4444);
    static final Color FLAME = new Color(0xFFB020);
    static final Color MISS = new Color(0xFFFFFF);
    static final Color SONAR = new Color(0x34D399);
    static final Color SONAR_BOAT = new Color(0xB98AF0);
    /** Square of a sonar boat hit by its own owner. */
    static final Color OWN_BOAT_HIT = new Color(0xFF3EC8);
    static final Color VALID = new Color(0x34D399);
    static final Color INVALID = new Color(0xEF4444);

    static final Font TITLE = new Font("SansSerif", Font.BOLD, 22);
    static final Font H2 = new Font("SansSerif", Font.BOLD, 15);
    static final Font BODY = new Font("SansSerif", Font.PLAIN, 13);
    static final Font SMALL = new Font("SansSerif", Font.PLAIN, 11);
    static final Font MONO = new Font("Monospaced", Font.PLAIN, 12);

    static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }
}
