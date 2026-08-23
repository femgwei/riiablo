package com.d2moo.common.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure path primitives ported from D2Common {@code Path/AStar.cpp}. */
public final class D2Path {
    private D2Path() {}

    public static final int MAX_PATH_LENGTH = 78;

    /** Native child exploration order: diagonals first, then cardinal neighbors. */
    private static final int[][] NEIGHBOR_OFFSETS = {
            {-1, -1}, {-1, 1}, {1, -1}, {1, 1},
            {-1, 0}, {0, -1}, {1, 0}, {0, 1}
    };

    /** Native diagonal-distance heuristic, with cardinal cost 2 and diagonal cost 3. */
    public static int aStarHeuristic(int x1, int y1, int x2, int y2) {
        int diffX = absDifference(x1, x2);
        int diffY = absDifference(y1, y2);
        return diffX < diffY ? diffX + 2 * diffY : diffY + 2 * diffX;
    }

    public static int neighborCost(int x1, int y1, int x2, int y2) {
        return x1 == x2 || y1 == y2 ? 2 : 3;
    }

    public static int neighborOffsetX(int index) {
        checkNeighborIndex(index);
        return NEIGHBOR_OFFSETS[index][0];
    }

    public static int neighborOffsetY(int index) {
        checkNeighborIndex(index);
        return NEIGHBOR_OFFSETS[index][1];
    }

    public static int neighborCount() {
        return NEIGHBOR_OFFSETS.length;
    }

    /**
     * Matches {@code PATH_AStar_FlushNodeToDynamicPath}: removes intermediate points while
     * direction remains unchanged, excludes the start point, and rejects native overflow.
     */
    public static List<Point> compressAStarPath(List<Point> pathFromStart) {
        if (pathFromStart == null || pathFromStart.size() < 2) {
            return Collections.emptyList();
        }

        List<Point> reverseTurns = new ArrayList<>();
        int previousDeltaX = -2;
        int previousDeltaY = -2;
        for (int i = pathFromStart.size() - 1; i > 0; i--) {
            Point point = pathFromStart.get(i);
            Point parent = pathFromStart.get(i - 1);
            int deltaX = point.x - parent.x;
            int deltaY = point.y - parent.y;
            if (deltaX != previousDeltaX || deltaY != previousDeltaY) {
                reverseTurns.add(point);
                if (reverseTurns.size() >= MAX_PATH_LENGTH) {
                    return Collections.emptyList();
                }
                previousDeltaX = deltaX;
                previousDeltaY = deltaY;
            }
        }

        Collections.reverse(reverseTurns);
        return reverseTurns;
    }

    private static int absDifference(int a, int b) {
        long difference = (long) a - b;
        long absolute = Math.abs(difference);
        return absolute > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) absolute;
    }

    private static void checkNeighborIndex(int index) {
        if (index < 0 || index >= NEIGHBOR_OFFSETS.length) {
            throw new IndexOutOfBoundsException("neighbor index: " + index);
        }
    }

    public static final class Point {
        public final int x;
        public final int y;

        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Point)) return false;
            Point other = (Point) obj;
            return x == other.x && y == other.y;
        }

        @Override
        public int hashCode() {
            return 31 * x + y;
        }

        @Override
        public String toString() {
            return "(" + x + "," + y + ")";
        }
    }
}
