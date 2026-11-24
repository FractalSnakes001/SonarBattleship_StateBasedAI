package battleship.belief;

import java.util.Arrays;

/** Minimal open-addressing hash map from long keys to int values (no boxing). */
final class LongIntMap {
    private long[] keys;
    private int[] vals;
    private boolean[] used;
    private int size, mask;

    LongIntMap(int expected) {
        int cap = Integer.highestOneBit(Math.max(16, expected * 2 - 1)) << 1;
        keys = new long[cap];
        vals = new int[cap];
        used = new boolean[cap];
        mask = cap - 1;
    }

    private static int hash(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        return (int) k;
    }

    /** Value of key, or -1. */
    int get(long k) {
        int i = hash(k) & mask;
        while (used[i]) {
            if (keys[i] == k) return vals[i];
            i = (i + 1) & mask;
        }
        return -1;
    }

    /** Value of key if present; otherwise inserts value v and returns -1. */
    int putIfAbsent(long k, int v) {
        int i = hash(k) & mask;
        while (used[i]) {
            if (keys[i] == k) return vals[i];
            i = (i + 1) & mask;
        }
        used[i] = true;
        keys[i] = k;
        vals[i] = v;
        if (++size * 2 > keys.length) grow();
        return -1;
    }

    private void grow() {
        long[] ok = keys;
        int[] ov = vals;
        boolean[] ou = used;
        int cap = keys.length * 2;
        keys = new long[cap];
        vals = new int[cap];
        used = new boolean[cap];
        mask = cap - 1;
        size = 0;
        for (int i = 0; i < ok.length; i++) if (ou[i]) putIfAbsent(ok[i], ov[i]);
    }

    int size() { return size; }

    void clear() {
        Arrays.fill(used, false);
        size = 0;
    }
}
