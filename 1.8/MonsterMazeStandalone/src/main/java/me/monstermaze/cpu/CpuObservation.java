package me.monstermaze.cpu;

/**
 * Fixed-size reusable production observation buffer.
 *
 * <p>Schema v2 mirrors the 96-feature policy contract in MonsterMazeAI.
 */
public final class CpuObservation {
    public static final int VERSION = 2;
    public static final int FEATURE_COUNT = 96;

    private final float[] values = new float[FEATURE_COUNT];

    public float[] values() {
        return values;
    }

    public float get(int index) {
        return values[index];
    }

    public void set(int index, float value) {
        values[index] = finite(value);
    }

    public void clear() {
        for (int i = 0; i < values.length; i++) values[i] = 0.0f;
    }

    private static float finite(float value) {
        return Float.isNaN(value) || Float.isInfinite(value) ? 0.0f : value;
    }
}
