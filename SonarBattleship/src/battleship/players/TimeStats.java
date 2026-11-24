package battleship.players;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Statistics about the time taken by an AI to choose its actions: number of measures, mean, variance, standard
 * deviation, minimum, median, maximum, and number of actions over a time limit.
 */
public final class TimeStats {
    private final List<Double> ms = new ArrayList<>();
    private double sum, sumSq;

    /** Adds one measure, in nanoseconds. */
    public void add(long nanos) {
        double m = nanos / 1e6;
        ms.add(m);
        sum += m;
        sumSq += m * m;
    }

    /** Adds all the measures of another object. */
    public void addAll(TimeStats o) {
        for (double m : o.ms) add((long) (m * 1e6));
    }

    public int count() { return ms.size(); }

    /** Mean time per action, in milliseconds. */
    public double mean() { return ms.isEmpty() ? 0 : sum / ms.size(); }

    /** Variance of the time per action (unbiased estimator), in milliseconds squared. */
    public double variance() {
        int n = ms.size();
        if (n < 2) return 0;
        return Math.max(0, (sumSq - sum * sum / n) / (n - 1));
    }

    /** Standard deviation of the time per action, in milliseconds. */
    public double stdDev() { return Math.sqrt(variance()); }

    public double min() { return ms.isEmpty() ? 0 : Collections.min(ms); }
    public double max() { return ms.isEmpty() ? 0 : Collections.max(ms); }

    /** Median time per action, in milliseconds. */
    public double median() {
        if (ms.isEmpty()) return 0;
        List<Double> s = new ArrayList<>(ms);
        Collections.sort(s);
        int n = s.size();
        return n % 2 == 1 ? s.get(n / 2) : (s.get(n / 2 - 1) + s.get(n / 2)) / 2;
    }

    /** Number of actions that took more than the given time (milliseconds). */
    public int countOver(double limitMs) {
        int k = 0;
        for (double m : ms) if (m > limitMs) k++;
        return k;
    }

    /** All the measures, in milliseconds, in the order they were added. */
    public List<Double> values() { return Collections.unmodifiableList(ms); }

    @Override
    public String toString() {
        return String.format("%d actions: mean %.1f ms, std. dev. %.1f ms, median %.1f ms, min %.1f ms, max %.1f ms",
                count(), mean(), stdDev(), median(), min(), max());
    }
}
