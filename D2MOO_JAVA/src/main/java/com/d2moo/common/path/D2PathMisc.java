package com.d2moo.common.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure direction and line helpers ported from D2Common {@code Path/PathMisc.cpp}. */
public final class D2PathMisc {
    public static final int DIRECTION_COUNT = 8;

    private static final int[][] DIRECTION_OFFSETS = {
            {1, 0}, {1, 1}, {0, 1}, {-1, 1},
            {-1, 0}, {-1, -1}, {0, -1}, {1, -1}
    };

    /** Native {@code stru_6FDD2158}: preferred, clockwise alternative, counterclockwise. */
    private static final int[][] DIRECTIONS = {
            {5, 4, 6}, {4, 5, 6}, {4, 3, 5}, {4, 3, 2}, {3, 4, 2},
            {6, 5, 4}, {5, 4, 6}, {4, 3, 5}, {3, 4, 2}, {2, 3, 4},
            {6, 7, 5}, {6, 7, 5}, {6, 7, 5}, {2, 1, 3}, {2, 1, 3},
            {6, 7, 0}, {7, 0, 6}, {0, 1, 7}, {1, 0, 2}, {2, 1, 0},
            {7, 0, 6}, {0, 7, 6}, {0, 1, 7}, {0, 1, 2}, {1, 0, 2}
    };

    /** Native cardinal-only alternatives from {@code byte_6FDD2288}. */
    private static final int[][] CARDINAL_DIRECTIONS = {
            {4, 6, -1}, {4, 6, -1}, {4, 2, 6}, {4, 2, -1}, {4, 2, -1},
            {6, 4, -1}, {4, 6, -1}, {4, 2, 6}, {4, 2, -1}, {2, 4, -1},
            {6, 0, 4}, {6, 0, 4}, {6, 0, 4}, {2, 0, 4}, {2, 0, 4},
            {6, 0, -1}, {0, 6, -1}, {0, 2, 6}, {2, 0, -1}, {2, 0, -1},
            {0, 6, -1}, {0, 6, -1}, {0, 2, 6}, {0, 2, -1}, {0, 2, -1}
    };

    private static final int[] SHORT_DISTANCE = {
            -1, -1, -1, 0, 2, 4, 6, 8,
            -1, -1, 0, 1, 2, 4, 6, 8,
            -1, 0, 0, 2, 3, 5, 7, 8,
            0, 1, 2, 2, 4, 5, 7, 8,
            2, 2, 3, 4, 5, 6, 7, 9,
            4, 4, 5, 5, 6, 7, 8, 9,
            6, 6, 7, 7, 7, 8, 10, 10,
            8, 8, 8, 8, 9, 9, 10, 11
    };

    private D2PathMisc() {}

    public static int directionOffsetX(int direction) {
        checkDirection(direction);
        return DIRECTION_OFFSETS[direction][0];
    }

    public static int directionOffsetY(int direction) {
        checkDirection(direction);
        return DIRECTION_OFFSETS[direction][1];
    }

    /** Preferred D2 cardinal/diagonal direction toward the target. */
    public static int primaryDirection(D2Path.Point from, D2Path.Point to) {
        requirePoints(from, to);
        return DIRECTIONS[directionTableIndex(from.x, from.y, to.x, to.y)][0];
    }

    /** Returns the three native collision-test directions. */
    public static int[] directions(D2Path.Point from, D2Path.Point to) {
        requirePoints(from, to);
        return DIRECTIONS[directionTableIndex(from.x, from.y, to.x, to.y)].clone();
    }

    /** Returns the cardinal-only test directions; unavailable alternatives are {@code -1}. */
    public static int[] cardinalDirections(D2Path.Point from, D2Path.Point to) {
        requirePoints(from, to);
        return CARDINAL_DIRECTIONS[
                directionTableIndex(from.x, from.y, to.x, to.y)].clone();
    }

    /** Exact port of D2Common {@code sub_6FDAB610}; result indexes the native 5x5 table. */
    public static int directionTableIndex(int x1, int y1, int x2, int y2) {
        int differenceX = x2 - x1;
        int differenceY = y2 - y1;
        int absoluteX = Math.abs(differenceX);
        int absoluteY = Math.abs(differenceY);

        if (absoluteX < 2 * absoluteY) {
            if (absoluteY >= 2 * absoluteX) {
                if (differenceX < 0) {
                    if (differenceY < -1) return 5;
                    if (differenceY > 1) differenceY = 2;
                    return differenceY + 7;
                }
                differenceX &= 1;
            }
        } else {
            differenceY = differenceY >= 0 ? differenceY & 1 : -1;
        }

        if (differenceX < -1) {
            differenceX = -2;
        } else if (differenceX > 1) {
            differenceX = 2;
        }
        if (differenceY < -1) return 5 * differenceX + 10;
        if (differenceY > 1) differenceY = 2;
        return differenceY + 5 * differenceX + 12;
    }

    /** Native short-range lookup, falling back to the A* diagonal metric at distance eight. */
    public static int approximateDistance(D2Path.Point first, D2Path.Point second) {
        requirePoints(first, second);
        int differenceX = Math.abs(first.x - second.x);
        int differenceY = Math.abs(first.y - second.y);
        if (differenceX >= 8 || differenceY >= 8) {
            return differenceX <= differenceY
                    ? differenceX + 2 * differenceY
                    : differenceY + 2 * differenceX;
        }
        int result = SHORT_DISTANCE[differenceX + 8 * differenceY];
        return result >= 0 ? result + 1 : 0;
    }

    /**
     * Corrected 1.13c {@code PATH_SimplifyToLines}: emits segment endpoints and excludes start.
     */
    public static List<D2Path.Point> simplifyToLines(
            D2Path.Point start, List<D2Path.Point> input) {
        if (start == null || input == null) {
            throw new IllegalArgumentException("start and input are required");
        }
        if (input.isEmpty()) return Collections.emptyList();
        if (input.size() == 1) {
            return Collections.singletonList(input.get(0));
        }

        List<D2Path.Point> output = new ArrayList<>();
        int previousDeltaX = input.get(0).x - start.x;
        int previousDeltaY = input.get(0).y - start.y;
        int pointsInLine = 0;
        int currentIndex;
        for (currentIndex = 0; currentIndex < input.size() - 1; currentIndex++) {
            D2Path.Point current = input.get(currentIndex);
            D2Path.Point next = input.get(currentIndex + 1);
            int deltaX = next.x - current.x;
            int deltaY = next.y - current.y;
            if (deltaX == previousDeltaX && deltaY == previousDeltaY) {
                pointsInLine++;
            } else if (pointsInLine <= 0
                    && previousDeltaX != deltaX
                    && previousDeltaY != deltaY) {
                // Force a new line on the following point; native unit steps cannot have -2.
                deltaX = -2;
                pointsInLine = 1;
            } else {
                output.add(current);
                pointsInLine = 0;
            }
            previousDeltaX = deltaX;
            previousDeltaY = deltaY;
        }
        output.add(input.get(currentIndex));
        return Collections.unmodifiableList(output);
    }

    /** Native Bresenham path, excluding start and bounded by {@code maximumDistance - 1}. */
    public static LineResult bresenhamLine(
            D2Path.Point start, D2Path.Point target, int maximumDistance) {
        requirePoints(start, target);
        int maximumSteps = maximumDistance - 1;
        int differenceX = target.x - start.x;
        int differenceY = target.y - start.y;
        int stepX = differenceX < 0 ? -1 : 1;
        int stepY = differenceY < 0 ? -1 : 1;
        int absoluteX = Math.abs(differenceX);
        int absoluteY = Math.abs(differenceY);

        if (absoluteX == 0 && absoluteY == 0) {
            return new LineResult(Collections.<D2Path.Point>emptyList(), 0);
        }
        int majorDirection;
        int majorSteps;
        if (absoluteX < absoluteY) {
            majorDirection = stepY <= 0 ? 0 : 2;
            majorSteps = absoluteY;
        } else {
            majorDirection = stepX <= 0 ? 3 : 1;
            majorSteps = absoluteX;
        }
        if (majorSteps > maximumSteps) {
            return new LineResult(Collections.<D2Path.Point>emptyList(), majorDirection);
        }

        List<D2Path.Point> points = new ArrayList<>(majorSteps);
        int x = start.x;
        int y = start.y;
        int deviation = 0;
        if (absoluteX < absoluteY) {
            for (int i = 0; i < absoluteY; i++) {
                deviation += absoluteX;
                y += stepY;
                if (deviation >= absoluteY) {
                    deviation -= absoluteY;
                    x += stepX;
                }
                points.add(new D2Path.Point(x, y));
            }
        } else {
            for (int i = 0; i < absoluteX; i++) {
                deviation += absoluteY;
                x += stepX;
                if (deviation >= absoluteX) {
                    deviation -= absoluteX;
                    y += stepY;
                }
                points.add(new D2Path.Point(x, y));
            }
        }
        return new LineResult(points, majorDirection);
    }

    private static void checkDirection(int direction) {
        if (direction < 0 || direction >= DIRECTION_COUNT) {
            throw new IndexOutOfBoundsException("direction: " + direction);
        }
    }

    private static void requirePoints(D2Path.Point first, D2Path.Point second) {
        if (first == null || second == null) {
            throw new IllegalArgumentException("points are required");
        }
    }

    public static final class LineResult {
        private final List<D2Path.Point> points;
        private final int majorDirection;

        LineResult(List<D2Path.Point> points, int majorDirection) {
            this.points = Collections.unmodifiableList(new ArrayList<>(points));
            this.majorDirection = majorDirection;
        }

        public List<D2Path.Point> points() {
            return points;
        }

        /** Native major direction: north=0, east=1, south=2, west=3. */
        public int majorDirection() {
            return majorDirection;
        }
    }
}
