package battleship.gui;

import battleship.engine.Action;
import battleship.engine.Board;
import battleship.engine.Config;
import battleship.engine.Event;
import battleship.engine.FireResult;
import battleship.engine.Knowledge;
import battleship.engine.Ship;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Draws one grid. Two kinds:
 * OWN   - the grid of a player as he sees it: his boats (fleet and sonar boat) and the opponent's shots;
 * ENEMY - what a player knows about the opponent's grid (optionally with the real ships revealed).
 */
@SuppressWarnings("serial")
final class BoardPanel extends JPanel {

    interface Listener {
        void clicked(BoardPanel source, int row, int col, boolean rightButton);
        void hovered(BoardPanel source, int row, int col);
    }

    enum Kind { OWN, ENEMY }
    enum Hover { NONE, CELL, SONAR }

    private Kind kind;
    private Listener listener;
    private Config cfg;
    private String title = "", subtitle = "";

    // data
    private Board board;
    private Knowledge knowledge;
    private List<Event> incoming = Collections.emptyList();
    private int currentTurn;
    private boolean reveal;
    private boolean dimmed;
    private String dimMessage;

    // interaction overlays
    private int hr = -1, hc = -1;
    private Hover hover = Hover.NONE;
    private List<int[]> preview;
    private boolean previewValid, previewIsSonarBoat;
    private int selectedShip = -1;
    private List<int[]> moveTargets = Collections.emptyList();
    private int[] highlight;

    // geometry
    private int cell, ox, oy;

    // belief-state overlay (null when hidden)
    private double[] beliefShip, beliefOwnBoat;

    BoardPanel(Kind kind) {
        this.kind = kind;
        setOpaque(false);
        setPreferredSize(new Dimension(470, 500));
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int[] rc = cellAt(e.getX(), e.getY());
                int r = rc == null ? -1 : rc[0], c = rc == null ? -1 : rc[1];
                if (r != hr || c != hc) {
                    hr = r;
                    hc = c;
                    if (listener != null) listener.hovered(BoardPanel.this, r, c);
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hr = hc = -1;
                if (listener != null) listener.hovered(BoardPanel.this, -1, -1);
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                int[] rc = cellAt(e.getX(), e.getY());
                if (listener != null) // (-1, -1) when the click is outside the grid
                    listener.clicked(BoardPanel.this, rc == null ? -1 : rc[0], rc == null ? -1 : rc[1],
                            SwingUtilities.isRightMouseButton(e));
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    // ------------------------------------------------------------------ setters

    void setListener(Listener l) { listener = l; }
    void setConfig(Config c) { cfg = c; repaint(); }
    void setTitle(String t, String sub) { title = t; subtitle = sub == null ? "" : sub; repaint(); }
    void setBoard(Board b) { board = b; repaint(); }
    void setKnowledge(Knowledge k) { knowledge = k; repaint(); }
    void setIncoming(List<Event> events) { incoming = events == null ? Collections.<Event>emptyList() : events; repaint(); }
    void setCurrentTurn(int t) { currentTurn = t; }
    void setReveal(boolean b) { reveal = b; repaint(); }
    void setDimmed(boolean b, String msg) { dimmed = b; dimMessage = msg; repaint(); }
    void setHover(Hover h) { hover = h; repaint(); }
    void setPreview(List<int[]> cells, boolean valid, boolean sonarBoat) {
        preview = cells;
        previewValid = valid;
        previewIsSonarBoat = sonarBoat;
        repaint();
    }
    void setSelection(int ship, List<int[]> targets) {
        selectedShip = ship;
        moveTargets = targets == null ? Collections.<int[]>emptyList() : targets;
        repaint();
    }
    void setHighlight(int[] rc) { highlight = rc; repaint(); }

    /** Probabilities to display (index row*size+col), or null to hide the overlay. */
    void setBelief(double[] ship, double[] ownBoat) {
        beliefShip = ship;
        beliefOwnBoat = ownBoat;
        repaint();
    }
    int hoverRow() { return hr; }
    int hoverCol() { return hc; }
    Kind kind() { return kind; }
    void setKind(Kind k) { kind = k; repaint(); }

    void clearInteraction() {
        hover = Hover.NONE;
        preview = null;
        selectedShip = -1;
        moveTargets = Collections.emptyList();
        repaint();
    }

    // ------------------------------------------------------------------ geometry

    private void computeGeometry() {
        int n = cfg.size;
        int top = 48, label = 22, pad = 14;
        int availW = getWidth() - 2 * pad - label;
        int availH = getHeight() - top - pad - label;
        cell = Math.max(10, Math.min(availW, availH) / n);
        int gw = cell * n;
        ox = pad + label + (availW - gw) / 2;
        oy = top + label + Math.max(0, (availH - gw) / 4);
    }

    private int[] cellAt(int x, int y) {
        if (cfg == null || cell == 0) return null;
        int c = Math.floorDiv(x - ox, cell), r = Math.floorDiv(y - oy, cell);
        return cfg.inside(r, c) ? new int[]{r, c} : null;
    }

    private double cx(int c) { return ox + (c + 0.5) * cell; }
    private double cy(int r) { return oy + (r + 0.5) * cell; }

    // ------------------------------------------------------------------ painting

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        g.setColor(Theme.PANEL);
        g.fill(new RoundRectangle2D.Double(1, 1, w - 2, h - 2, 18, 18));
        g.setColor(Theme.PANEL_BORDER);
        g.draw(new RoundRectangle2D.Double(1, 1, w - 2, h - 2, 18, 18));

        g.setFont(Theme.H2);
        g.setColor(Theme.TEXT);
        g.drawString(title, 18, 30);
        g.setFont(Theme.SMALL);
        g.setColor(Theme.MUTED);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(subtitle, w - 18 - fm.stringWidth(subtitle), 30);
        if (cfg == null) { g.dispose(); return; }

        computeGeometry();
        int n = cfg.size, gw = n * cell;

        // ocean
        g.setPaint(new GradientPaint(ox, oy, Theme.OCEAN_TOP, ox, oy + gw, Theme.OCEAN_BOTTOM));
        g.fillRect(ox, oy, gw, gw);
        if (kind == Kind.ENEMY && knowledge != null) {
            for (int r = 0; r < n; r++)
                for (int c = 0; c < n; c++) {
                    int st = knowledge.state(r, c);
                    if (st == Knowledge.UNKNOWN) g.setColor(Theme.alpha(Theme.FOG, 150));
                    else if (!knowledge.isFresh(r, c)) g.setColor(Theme.alpha(Theme.FOG, 90));
                    else continue;
                    g.fillRect(ox + c * cell, oy + r * cell, cell, cell);
                }
        }
        if (beliefShip != null) paintBeliefHeat(g);
        // grid + labels
        g.setColor(Theme.GRID);
        for (int i = 0; i <= n; i++) {
            g.drawLine(ox, oy + i * cell, ox + gw, oy + i * cell);
            g.drawLine(ox + i * cell, oy, ox + i * cell, oy + gw);
        }
        g.setFont(Theme.SMALL);
        g.setColor(Theme.MUTED);
        fm = g.getFontMetrics();
        for (int i = 0; i < n; i++) {
            String col = String.valueOf((char) ('A' + i)), row = String.valueOf(i + 1);
            g.drawString(col, (int) cx(i) - fm.stringWidth(col) / 2, oy - 7);
            g.drawString(row, ox - 8 - fm.stringWidth(row), (int) cy(i) + fm.getAscent() / 2 - 1);
        }

        if (kind == Kind.OWN) paintOwn(g); else paintEnemy(g);
        if (beliefShip != null) paintBeliefText(g);
        paintOverlays(g);

        if (dimmed) {
            g.setColor(new Color(8, 14, 24, 200));
            g.fill(new RoundRectangle2D.Double(1, 1, w - 2, h - 2, 18, 18));
            if (dimMessage != null) {
                g.setFont(Theme.H2);
                g.setColor(Theme.TEXT);
                fm = g.getFontMetrics();
                g.drawString(dimMessage, (w - fm.stringWidth(dimMessage)) / 2, h / 2);
            }
        }
        g.dispose();
    }

    private void paintOwn(Graphics2D g) {
        if (board != null) {
            for (int i = 0; i < board.fleet().size(); i++) drawShip(g, board.ship(i), i == selectedShip, 1f);
            if (board.sonarBoat() != null) drawShip(g, board.sonarBoat(), false, 1f);
        }
        // opponent's last visible sonar on my grid
        Event lastSonar = null;
        for (Event e : incoming) if (e.action.type == Action.Type.SONAR) lastSonar = e;
        if (lastSonar != null) {
            int age = currentTurn - lastSonar.turn;
            if (age <= 2) drawArea(g, lastSonar.action.row, lastSonar.action.col, Theme.SONAR, age <= 1 ? 55 : 25, true);
        }
        // opponent's rockets: latest one per square
        int n = cfg.size;
        Event[][] last = new Event[n][n];
        for (Event e : incoming) if (e.action.type == Action.Type.FIRE) last[e.action.row][e.action.col] = e;
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++) {
                Event e = last[r][c];
                if (e == null) continue;
                float a = currentTurn - e.turn <= 2 ? 1f : 0.55f;
                if (e.fireResult == FireResult.MISS) drawMiss(g, r, c, a);
                else {
                    Ship s = board == null ? null : board.shipAt(r, c);
                    boolean stillDamaged = s != null && s.isDamaged(s.indexOf(r, c));
                    if (!stillDamaged) drawRing(g, r, c, Theme.HIT, 0.30, a * 0.8f); // square hit earlier, no damaged ship there now
                }
            }
    }

    private void paintEnemy(Graphics2D g) {
        int n = cfg.size;
        if (knowledge != null) {
            int[] ls = knowledge.lastSonar();
            if (ls != null && currentTurn - ls[2] <= 2)
                drawArea(g, ls[0], ls[1], Theme.SONAR, currentTurn - ls[2] <= 1 ? 45 : 20, true);
            for (int r = 0; r < n; r++)
                for (int c = 0; c < n; c++) {
                    int st = knowledge.state(r, c);
                    float a = knowledge.isFresh(r, c) ? 1f : 0.45f;
                    switch (st) {
                        case Knowledge.EMPTY:
                            if (knowledge.source(r, c) == Knowledge.FROM_ROCKET) drawMiss(g, r, c, a);
                            else drawDot(g, r, c, Theme.SONAR, 0.07, a * 0.8f);
                            break;
                        case Knowledge.CONTACT:
                            drawDot(g, r, c, Theme.SONAR, 0.17, a);
                            drawRing(g, r, c, Theme.SONAR, 0.30, a * 0.6f);
                            break;
                        case Knowledge.HIT:
                            drawBurst(g, r, c, a);
                            break;
                        case Knowledge.SUNK:
                            drawWreckCell(g, r, c);
                            break;
                        default:
                            break;
                    }
                }
        }
        if (reveal && board != null) {
            Stroke old = g.getStroke();
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{5f, 5f}, 0f));
            for (Ship s : board.fleet()) outlineShip(g, s, Theme.alpha(Theme.ACCENT, 200));
            if (board.sonarBoat() != null) outlineShip(g, board.sonarBoat(), Theme.alpha(Theme.SONAR_BOAT, 220));
            g.setStroke(old);
        }
    }

    private void paintOverlays(Graphics2D g) {
        for (int[] t : moveTargets) {
            g.setColor(Theme.alpha(Theme.ACCENT, 70));
            g.fillRect(ox + t[1] * cell + 3, oy + t[0] * cell + 3, cell - 6, cell - 6);
            outlineCell(g, t[0], t[1], Theme.ACCENT, 2f);
        }
        if (preview != null) {
            Color col = !previewValid ? Theme.INVALID : previewIsSonarBoat ? Theme.SONAR_BOAT : Theme.VALID;
            for (int[] p : preview) {
                if (!cfg.inside(p[0], p[1])) continue;
                g.setColor(Theme.alpha(col, 110));
                g.fillRect(ox + p[1] * cell + 3, oy + p[0] * cell + 3, cell - 6, cell - 6);
                outlineCell(g, p[0], p[1], col, 2f);
            }
        }
        if (hr >= 0) {
            if (hover == Hover.SONAR) drawArea(g, hr, hc, Theme.ACCENT, 50, true);
            if (hover == Hover.CELL || hover == Hover.SONAR) outlineCell(g, hr, hc, Theme.ACCENT, 2.5f);
        }
        if (highlight != null && cfg.inside(highlight[0], highlight[1]))
            outlineCell(g, highlight[0], highlight[1], Theme.alpha(Color.WHITE, 220), 2f);
    }

    // ------------------------------------------------------------------ belief overlay

    private void paintBeliefHeat(Graphics2D g) {
        int n = cfg.size;
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++) {
                double p = beliefShip[r * n + c];
                if (p <= 0.0005) continue;
                g.setColor(Theme.alpha(Theme.FLAME, (int) (25 + 175 * p)));
                g.fillRect(ox + c * cell + 1, oy + r * cell + 1, cell - 1, cell - 1);
            }
    }

    private void paintBeliefText(Graphics2D g) {
        int n = cfg.size;
        g.setFont(Theme.SMALL.deriveFont(java.awt.Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++) {
                double p = beliefShip[r * n + c];
                int x = ox + c * cell, y = oy + r * cell;
                if (p > 0.0005) {
                    String s = percent(p);
                    g.setColor(new Color(0, 0, 0, 120));
                    g.fillRect(x + 2, y + 2, fm.stringWidth(s) + 4, fm.getHeight());
                    g.setColor(Color.WHITE);
                    g.drawString(s, x + 4, y + 1 + fm.getAscent());
                }
                double q = beliefOwnBoat == null ? 0 : beliefOwnBoat[r * n + c];
                if (q > 0.0005) {
                    String s = percent(q);
                    int w = fm.stringWidth(s);
                    g.setColor(new Color(0, 0, 0, 120));
                    g.fillRect(x + cell - w - 6, y + cell - fm.getHeight() - 2, w + 4, fm.getHeight());
                    g.setColor(Theme.SONAR_BOAT.brighter());
                    g.drawString(s, x + cell - w - 4, y + cell - 3 - fm.getDescent());
                }
            }
    }

    private static String percent(double p) {
        if (p >= 0.995) return "100%";
        if (p < 0.01) return "<1%";
        return Math.round(p * 100) + "%";
    }

    // ------------------------------------------------------------------ primitives

    private void drawShip(Graphics2D g, Ship s, boolean selected, float alpha) {
        int r0 = s.row(), c0 = s.col();
        int len = s.length();
        double x = ox + c0 * cell + 4, y = oy + r0 * cell + 4;
        double wd = (s.isHorizontal() ? len * cell : cell) - 8, ht = (s.isHorizontal() ? cell : len * cell) - 8;
        RoundRectangle2D hull = new RoundRectangle2D.Double(x, y, wd, ht, cell * 0.7, cell * 0.7);
        Composite oc = g.getComposite();
        g.setComposite(AlphaComposite.SrcOver.derive(alpha));
        if (selected) {
            g.setColor(Theme.alpha(Theme.ACCENT, 90));
            g.fill(new RoundRectangle2D.Double(x - 4, y - 4, wd + 8, ht + 8, cell * 0.8, cell * 0.8));
        }
        Color base = s.isSonarBoat() ? Theme.SONAR_BOAT : s.isSunk() ? Theme.WRECK : Theme.SHIP;
        Color edge = s.isSonarBoat() ? Theme.SONAR_BOAT.darker().darker() : Theme.SHIP_EDGE;
        g.setPaint(new GradientPaint((float) x, (float) y, base.brighter(), (float) (x + wd), (float) (y + ht), base.darker()));
        g.fill(hull);
        g.setColor(edge);
        g.setStroke(new BasicStroke(1.5f));
        g.draw(hull);
        for (int i = 0; i < len; i++) {
            int r = s.cellRow(i), c = s.cellCol(i);
            if (s.isSonarBoat() && !s.isDamaged(i)) {
                // small sonar waves
                g.setColor(Theme.alpha(Color.WHITE, 170));
                g.setStroke(new BasicStroke(1.3f));
                for (int k = 1; k <= 2; k++) {
                    double rad = cell * 0.09 * k;
                    g.draw(new java.awt.geom.Arc2D.Double(cx(c) - rad, cy(r) - rad, 2 * rad, 2 * rad, -50, 100, java.awt.geom.Arc2D.OPEN));
                }
                g.fill(new Ellipse2D.Double(cx(c) - 2.5, cy(r) - 2.5, 5, 5));
            } else if (!s.isDamaged(i)) {
                g.setColor(Theme.alpha(edge, 160));
                double rad = cell * 0.08;
                g.fill(new Ellipse2D.Double(cx(c) - rad, cy(r) - rad, 2 * rad, 2 * rad));
            }
            if (s.isDamaged(i)) {
                if (s.isSonarBoat()) drawOwnBoatHit(g, r, c);
                else if (s.isSunk()) drawCross(g, r, c, Theme.HIT);
                else drawBurst(g, r, c, 1f);
            }
        }
        g.setComposite(oc);
    }

    private void outlineShip(Graphics2D g, Ship s, Color col) {
        int len = s.length();
        double x = ox + s.col() * cell + 3, y = oy + s.row() * cell + 3;
        double wd = (s.isHorizontal() ? len * cell : cell) - 6, ht = (s.isHorizontal() ? cell : len * cell) - 6;
        g.setColor(col);
        g.draw(new RoundRectangle2D.Double(x, y, wd, ht, cell * 0.7, cell * 0.7));
    }

    private void drawArea(Graphics2D g, int cr, int cc, Color col, int alpha, boolean border) {
        int n = cfg.size;
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++)
                if (cfg.inSonarArea(cr, cc, r, c)) {
                    g.setColor(Theme.alpha(col, alpha));
                    g.fillRect(ox + c * cell + 1, oy + r * cell + 1, cell - 1, cell - 1);
                }
        if (border) {
            g.setColor(Theme.alpha(col, Math.min(255, alpha * 3)));
            g.setStroke(new BasicStroke(1.5f));
            for (int r = 0; r < n; r++)
                for (int c = 0; c < n; c++) {
                    if (!cfg.inSonarArea(cr, cc, r, c)) continue;
                    int x = ox + c * cell, y = oy + r * cell;
                    if (r == 0 || !cfg.inSonarArea(cr, cc, r - 1, c)) g.drawLine(x, y, x + cell, y);
                    if (r == n - 1 || !cfg.inSonarArea(cr, cc, r + 1, c)) g.drawLine(x, y + cell, x + cell, y + cell);
                    if (c == 0 || !cfg.inSonarArea(cr, cc, r, c - 1)) g.drawLine(x, y, x, y + cell);
                    if (c == n - 1 || !cfg.inSonarArea(cr, cc, r, c + 1)) g.drawLine(x + cell, y, x + cell, y + cell);
                }
        }
    }

    private void outlineCell(Graphics2D g, int r, int c, Color col, float width) {
        g.setColor(col);
        g.setStroke(new BasicStroke(width));
        g.draw(new RoundRectangle2D.Double(ox + c * cell + 2, oy + r * cell + 2, cell - 4, cell - 4, 8, 8));
    }

    private void drawMiss(Graphics2D g, int r, int c, float a) {
        drawRing(g, r, c, Theme.MISS, 0.14, a * 0.9f);
        drawRing(g, r, c, Theme.MISS, 0.25, a * 0.35f);
    }

    private void drawRing(Graphics2D g, int r, int c, Color col, double radius, float a) {
        double rad = cell * radius;
        g.setColor(Theme.alpha(col, (int) (255 * a)));
        g.setStroke(new BasicStroke(2f));
        g.draw(new Ellipse2D.Double(cx(c) - rad, cy(r) - rad, 2 * rad, 2 * rad));
    }

    private void drawDot(Graphics2D g, int r, int c, Color col, double radius, float a) {
        double rad = cell * radius;
        g.setColor(Theme.alpha(col, (int) (255 * a)));
        g.fill(new Ellipse2D.Double(cx(c) - rad, cy(r) - rad, 2 * rad, 2 * rad));
    }

    private void drawBurst(Graphics2D g, int r, int c, float a) {
        drawDot(g, r, c, Theme.HIT, 0.26, a);
        drawDot(g, r, c, Theme.FLAME, 0.13, a);
    }

    private void drawCross(Graphics2D g, int r, int c, Color col) {
        double d = cell * 0.22;
        g.setColor(col);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.Line2D.Double(cx(c) - d, cy(r) - d, cx(c) + d, cy(r) + d));
        g.draw(new java.awt.geom.Line2D.Double(cx(c) - d, cy(r) + d, cx(c) + d, cy(r) - d));
    }

    private void drawWreckCell(Graphics2D g, int r, int c) {
        g.setColor(Theme.WRECK);
        g.fill(new RoundRectangle2D.Double(ox + c * cell + 4, oy + r * cell + 4, cell - 8, cell - 8, 10, 10));
        drawCross(g, r, c, Theme.HIT);
    }

    /** A square of a sonar boat hit by its own owner: bright pink square with a white cross. */
    private void drawOwnBoatHit(Graphics2D g, int r, int c) {
        g.setColor(Theme.OWN_BOAT_HIT);
        g.fill(new RoundRectangle2D.Double(ox + c * cell + 3, oy + r * cell + 3, cell - 6, cell - 6, 10, 10));
        g.setColor(Theme.OWN_BOAT_HIT.darker());
        g.setStroke(new BasicStroke(2f));
        g.draw(new RoundRectangle2D.Double(ox + c * cell + 3, oy + r * cell + 3, cell - 6, cell - 6, 10, 10));
        drawCross(g, r, c, Color.WHITE);
    }

    /** Squares of a ship of length len placed at (r,c). */
    static List<int[]> cells(int r, int c, int len, boolean horizontal) {
        List<int[]> l = new ArrayList<>();
        for (int i = 0; i < len; i++) l.add(horizontal ? new int[]{r, c + i} : new int[]{r + i, c});
        return l;
    }
}
