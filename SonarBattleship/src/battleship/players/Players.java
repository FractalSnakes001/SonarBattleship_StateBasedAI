package battleship.players;

/** Creates players from a short name or a fully qualified class name. */
public final class Players {
    public static final String HUMAN = "Human";
    public static final String[] BUILT_IN = {HUMAN, "AI", "BasicAI", "RandomAI"};

    private Players() {}

    /** Returns null for a human player. */
    public static Player create(String spec) {
        String s = spec.trim();
        if (s.equalsIgnoreCase(HUMAN)) return null;
        if (s.equalsIgnoreCase("AI")) return new AIPlayer();
        if (s.equalsIgnoreCase("BasicAI")) return new BasicAI();
        if (s.equalsIgnoreCase("RandomAI")) return new RandomAI();
        try {
            Class<?> cl = Class.forName(s.contains(".") ? s : "battleship.players." + s);
            Object o = cl.getDeclaredConstructor().newInstance();
            if (!(o instanceof Player)) throw new IllegalArgumentException(s + " does not implement Player");
            return (Player) o;
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Cannot create player '" + s + "': " + e);
        }
    }
}
