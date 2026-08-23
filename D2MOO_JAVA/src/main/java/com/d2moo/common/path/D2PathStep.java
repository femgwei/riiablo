package com.d2moo.common.path;

/** Pure fixed-point and direction-vector helpers ported from D2Common {@code Path/Step.cpp}. */
public final class D2PathStep {
    public static final int PRECISE_ONE = 1 << 16;
    public static final int PRECISE_HALF = 1 << 15;
    public static final int PRECISE_VECTOR_LENGTH = 1 << 12;
    public static final int FACING_COUNT = 64;

    private static final int LOOKUP_SIZE = 128;
    private static final int[] VECTOR_X = new int[LOOKUP_SIZE];
    private static final int[] VECTOR_Y = new int[LOOKUP_SIZE];
    private static final int[] ANGLE = new int[LOOKUP_SIZE];

    static {
        for (int tangent = 0; tangent < LOOKUP_SIZE; tangent++) {
            double length = StrictMath.sqrt(127.0 * 127.0 + tangent * tangent);
            VECTOR_X[tangent] = (int) StrictMath.floor(
                    PRECISE_VECTOR_LENGTH * tangent / length);
            VECTOR_Y[tangent] = (int) StrictMath.floor(
                    PRECISE_VECTOR_LENGTH * 127.0 / length);
            ANGLE[tangent] = Math.min(7, (int) StrictMath.floor(
                    StrictMath.atan(tangent / 127.0) * 32.0 / StrictMath.PI));
        }
    }

    private D2PathStep() {}

    public static int toFp16Corner(int subtile) {
        return (subtile & 0xFFFF) << 16;
    }

    public static int toFp16Center(int subtile) {
        return toFp16Corner(subtile) + PRECISE_HALF;
    }

    public static int fromFp16(int precision) {
        return precision >>> 16;
    }

    public static int fitToFp16Center(int precision) {
        return (precision & 0xFFFF0000) + PRECISE_HALF;
    }

    public static int normalizeFacing(int direction) {
        return direction & (FACING_COUNT - 1);
    }

    public static int computeDirection(int startX, int startY, int targetX, int targetY) {
        return directionVector(
                toFp16Center(startX),
                toFp16Center(startY),
                toFp16Center(targetX),
                toFp16Center(targetY)).direction;
    }

    public static int computeDirectionFromPreciseCoords(
            int startPrecisionX,
            int startPrecisionY,
            int targetPrecisionX,
            int targetPrecisionY) {
        return directionVector(
                startPrecisionX,
                startPrecisionY,
                targetPrecisionX,
                targetPrecisionY).direction;
    }

    /** Native 64-facing direction and 12-bit normalized direction vector. */
    public static DirectionVector directionVector(
            int startPrecisionX,
            int startPrecisionY,
            int targetPrecisionX,
            int targetPrecisionY) {
        long startX = Integer.toUnsignedLong(startPrecisionX);
        long startY = Integer.toUnsignedLong(startPrecisionY);
        long targetX = Integer.toUnsignedLong(targetPrecisionX);
        long targetY = Integer.toUnsignedLong(targetPrecisionY);

        boolean startXLessThanTarget = startX <= targetX;
        long minimumX = Math.min(startX, targetX);
        long maximumX = Math.max(startX, targetX);
        boolean startYLessThanTarget = startY <= targetY;
        long minimumY = Math.min(startY, targetY);
        long maximumY = Math.max(startY, targetY);

        boolean deltaXGreaterThanY;
        if (maximumX - minimumX > maximumY - minimumY) {
            long swap = minimumX;
            minimumX = minimumY;
            minimumY = swap;
            swap = maximumX;
            maximumX = maximumY;
            maximumY = swap;
            deltaXGreaterThanY = true;
        } else {
            deltaXGreaterThanY = false;
        }

        int tangent = 0;
        long denominator = maximumY - minimumY;
        if (denominator != 0) {
            tangent = (int) (127L * (maximumX - minimumX) / denominator);
            tangent = Math.max(0, Math.min(LOOKUP_SIZE - 1, tangent));
        }

        int x;
        int y;
        int angle = ANGLE[tangent];
        if (deltaXGreaterThanY) {
            y = VECTOR_X[tangent];
            x = VECTOR_Y[tangent];
            angle = (-1 - angle) & 0xF;
        } else {
            x = VECTOR_X[tangent];
            y = VECTOR_Y[tangent];
        }
        if (!startYLessThanTarget) {
            y = -y;
            angle = (-1 - angle) & 0x1F;
        }
        if (startXLessThanTarget) {
            angle = (-1 - angle) & 0x3F;
        } else {
            x = -x;
        }
        return new DirectionVector(x, y, (angle + 8) & 0x3F);
    }

    /** Native step-delta fitting from {@code sub_6FDAB810}. */
    public static D2Path.Point fitStepDelta(int x, int y) {
        while (x < -PRECISE_ONE || x > PRECISE_ONE) {
            x >>= 1;
            y >>= 1;
        }
        while (y < -PRECISE_ONE || y > PRECISE_ONE) {
            x >>= 1;
            y >>= 1;
        }
        return new D2Path.Point(x, y);
    }

    public static final class DirectionVector {
        public final int x;
        public final int y;
        public final int direction;

        DirectionVector(int x, int y, int direction) {
            this.x = x;
            this.y = y;
            this.direction = direction;
        }
    }
}
