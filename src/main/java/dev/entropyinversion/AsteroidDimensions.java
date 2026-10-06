package dev.entropyinversion;

public final class AsteroidDimensions {
    private static final int MIN_DIAMETER = 3;
    private static final int MAX_DIAMETER = 11;
    private static final int SIZE_STEPS = (MAX_DIAMETER - MIN_DIAMETER) / 2;

    private AsteroidDimensions() {
    }

    public static int diameterForRadius(int radius) {
        int boundedRadius = Math.max(1, Math.min(200, radius));
        int sizeStep = (int) Math.round((boundedRadius - 1) * SIZE_STEPS / 199.0D);
        return MIN_DIAMETER + sizeStep * 2;
    }

    public static int halfSizeForRadius(int radius) {
        return diameterForRadius(radius) / 2;
    }

    public static boolean isInsideAsteroid(int x, int y, int z, int halfSize) {
        int distanceSquared = x * x + y * y + z * z;
        return distanceSquared <= halfSize * (halfSize + 1);
    }
}
