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

    /**
     * Ordinary non-missile branch of Step.cpp's sub_6FDACEC0. Inputs are raw
     * fp16 velocity vectors, NOT world-units/second. The caller owns path
     * flags, room recaching, distance accounting and the waypoint cursor.
     * A collision callback must atomically relocate the footprint, restoring
     * the old footprint on rejection (COLLISION_TryTeleportUnitCollisionMask).
     */
    public static StepResult stepUnit(int x, int y, int velocityX, int velocityY,
            int targetX, int targetY, UnitStepCollision collision) {
        if (collision == null) throw new NullPointerException("collision");
        if (velocityX == 0 && velocityY == 0) {
            return new StepResult(fitToFp16Center(x), fitToFp16Center(y), false, false, 0);
        }
        int dx = targetX - x, dy = targetY - y;
        long maximum = Math.max(Math.abs((long) velocityX), Math.abs((long) velocityY));
        boolean reached = Math.abs((long) dx) <= maximum && Math.abs((long) dy) <= maximum;
        if (reached) { velocityX = dx; velocityY = dy; }
        int endX = x + velocityX, endY = y + velocityY;
        int endCellX = fromFp16(endX), endCellY = fromFp16(endY);
        if (fromFp16(x) == endCellX && fromFp16(y) == endCellY) {
            return new StepResult(endX, endY, reached, false, 0);
        }
        D2Path.Point delta = fitStepDelta(velocityX, velocityY);
        int steps = 0;
        while (fromFp16(x) != endCellX || fromFp16(y) != endCellY) {
            int nextX = x + delta.x, nextY = y + delta.y;
            int cellX = fromFp16(x), cellY = fromFp16(y);
            int nextCellX = fromFp16(nextX), nextCellY = fromFp16(nextY);
            if (cellX != nextCellX || cellY != nextCellY) {
                if (!collision.tryMove(cellX, cellY, nextCellX, nextCellY)) {
                    return new StepResult(fitToFp16Center(x), fitToFp16Center(y), false, true, steps);
                }
                // Native PATH_MAX_STEP_LEN is ten; the final precision still
                // comes from the requested endpoint, not the fitted vector.
                if (++steps >= 10) break;
            }
            x = nextX;
            y = nextY;
        }
        return new StepResult(endX, endY, reached, false, steps);
    }

    @FunctionalInterface
    public interface UnitStepCollision {
        boolean tryMove(int oldX, int oldY, int newX, int newY);
    }

    public static final class StepResult {
        public final int x, y, crossedCells;
        public final boolean reachedTarget, blocked;

        private StepResult(int x, int y, boolean reachedTarget, boolean blocked, int crossedCells) {
            this.x = x; this.y = y;
            this.reachedTarget = reachedTarget; this.blocked = blocked;
            this.crossedCells = crossedCells;
        }
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
