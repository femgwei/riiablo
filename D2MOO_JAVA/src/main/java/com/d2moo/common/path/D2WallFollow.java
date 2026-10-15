package com.d2moo.common.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Prepared-coordinate type-15 point generation: 1.10f D2Common 0x6FDAC270/0x6FDABAC0.
 * Based on D2MOO PathWF.cpp, with the empty-cache distance read checked against 1.10f.
 * Requires stable collision queries; the caller owns pattern, mask and room policy.
 * This is not target-unit preparation, a dynamic-path state machine or a production backend.
 * Native query-skip and simplification quirks are retained: returned segments are NOT
 * a collision-safety certificate for installation into a different movement system.
 */
public final class D2WallFollow {
    @FunctionalInterface
    public interface Collision { boolean blocked(int x, int y); }

    // WallFollow uses north=0, clockwise, unlike PathMisc/Toward's east=0.
    private static final int[] DX = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final int[] DY = {-1, -1, 0, 1, 1, 1, 0, -1};
    private static final int[] CELL_DIRECTION = {7, 0, 1, 6, -1, 2, 5, 4, 3};
    private static final int[] PREVIOUS_1 = {6, 6, 0, 0, 2, 2, 4, 4};
    private static final int[] PREVIOUS_2 = {2, 2, 4, 4, 6, 6, 0, 0};
    private static final int[] NEXT_1 = {2, 2, 4, 4, 6, 6, 0, 0};
    private static final int[] NEXT_2 = {6, 0, 0, 2, 2, 4, 4, 6};
    private static final D2Path.Point ZERO = new D2Path.Point(0, 0);

    private D2WallFollow() {}

    /**
     * hasTargetUnit only reproduces the native minimum-40 distance rule; it does not
     * resolve or track a unit. Raw points can be partial and are not an exact-arrival promise.
     */
    public static Result compute(D2Path.Point start, D2Path.Point target,
            int maximumDistance, boolean hasTargetUnit, Collision collision) {
        if (!isUint16(start) || !isUint16(target) || collision == null
                || maximumDistance < 0 || maximumDistance >= D2Path.MAX_PATH_LENGTH) {
            throw new IllegalArgumentException("uint16 points, collision and distance 0..77 required");
        }
        int distance = hasTargetUnit ? Math.max(40, maximumDistance) : maximumDistance;
        D2PathMisc.LineResult line = D2PathMisc.bresenhamLine(start, target, distance);
        // The native entry does not collision-test or emit lines of length zero, one or two.
        if (line.points().size() <= 2) return new Result(start, target, Collections.emptyList(), 0);

        Path path = new Path(line.points());
        D2Path.Point subpathStart = start;
        int detours = 0;
        for (path.index = 0; path.index < path.length; path.index++) {
            D2Path.Point point = path.points[path.index];
            if (collision.blocked(point.x, point.y)) {
                detours++;
                int count = findSubpath(start, target, subpathStart, path,
                        distance - path.index, line.majorDirection(), collision);
                if (count == 0) return new Result(start, target, path.prefix(path.index), detours);
            }
            // Intentionally use the updated index, THEN increment the loop index.
            // A rejoin skips the next collision query; do not normalize this into a new search.
            subpathStart = path.points[path.index];
        }
        return new Result(start, target, path.prefix(path.length), detours);
    }

    private static int findSubpath(D2Path.Point start, D2Path.Point target,
            D2Path.Point subpathStart, Path path, int maximumLength,
            int majorDirection, Collision collision) {
        D2Path.Point blocked = path.points[path.index];
        int dx = blocked.x - subpathStart.x, dy = blocked.y - subpathStart.y;
        if (Math.abs(dx) > 1 || Math.abs(dy) > 1) {
            throw new IllegalStateException("native subpath must start at an adjacent cell");
        }
        int direction = CELL_DIRECTION[4 + 3 * dy + dx];
        Cache first = new Cache(subpathStart, direction + 1, PREVIOUS_1, NEXT_1);
        Cache second = new Cache(subpathStart, direction + 7, PREVIOUS_2, NEXT_2);
        Cache current = first, other = second;
        // Either side finishing ends the search, not both sides.
        while (!(first.finished || second.finished)) {
            D2Path.Point candidate = current.nextFree(collision);
            if (candidate == null) {
                current.finished = true;
            } else {
                if (!current.points.isEmpty()) {
                    int progress;
                    switch (majorDirection) {
                        case 0: progress = subpathStart.y - candidate.y; break;
                        case 1: progress = candidate.x - subpathStart.x; break;
                        case 2: progress = candidate.y - subpathStart.y; break;
                        case 3: progress = subpathStart.x - candidate.x; break;
                        default: throw new AssertionError(majorDirection);
                    }
                    int rejoinIndex = progress - 1 + path.index;
                    if (progress > 0 && rejoinIndex < path.length
                            && candidate.equals(path.points[rejoinIndex])) {
                        current.points.add(candidate);
                        int difference = path.index - rejoinIndex + current.points.size() - 1;
                        int newLast = rejoinIndex + difference;
                        System.arraycopy(path.points, rejoinIndex + 1, path.points, newLast + 1,
                                path.length - rejoinIndex - 1);
                        current.copyTo(path.points, path.index);
                        path.length += difference;
                        path.index = newLast + 1;
                        return current.points.size();
                    }
                }
                if (other.points.size() > 1) {
                    if (current.lastSyncIndex > 0) {
                        if (candidate.equals(other.points.get(current.lastSyncIndex - 2))) {
                            path.terminate(path.index);
                            return 0;
                        }
                        current.lastSyncIndex = 0;
                    }
                    if (candidate.equals(other.position)) current.lastSyncIndex = other.points.size();
                }
                current.points.add(candidate);
                current.position = candidate;
                current.direction = current.previous[current.direction & 7];
                if (maximumLength - path.length - 1 <= current.points.size()) current.finished = true;
            }
            if (current.lastSyncIndex == 0) {
                Cache swap = current;
                current = other;
                other = swap;
            }
        }

        // The native >80 guard is unreachable for this API's distance range (0..77).
        int distance1 = squaredDistance(first.distancePoint(), target);
        int distance2 = squaredDistance(second.distancePoint(), target);
        Cache best = distance1 < distance2 ? first : second; // ties choose cache2
        if (squaredDistance(start, target) < (distance1 < distance2 ? distance1 : distance2)) return 0;
        best.copyTo(path.points, path.index);
        path.index += best.points.size();
        path.terminate(path.index);
        // This is the current cache count, NOT necessarily the selected cache count.
        return current.points.size();
    }

    private static int squaredDistance(D2Path.Point first, D2Path.Point second) {
        int dx = first.x - second.x, dy = first.y - second.y;
        // Native imul/add and signed comparisons: deliberately retain int32 wraparound.
        return dx * dx + dy * dy;
    }

    private static boolean isUint16(D2Path.Point point) {
        return point != null && point.x >= 0 && point.x <= 0xffff
                && point.y >= 0 && point.y <= 0xffff;
    }

    private static final class Cache {
        final List<D2Path.Point> points = new ArrayList<>();
        final int[] previous, next;
        D2Path.Point position;
        int direction, lastSyncIndex;
        boolean finished;

        Cache(D2Path.Point position, int direction, int[] previous, int[] next) {
            this.position = position;
            this.direction = direction;
            this.previous = previous;
            this.next = next;
        }

        D2Path.Point nextFree(Collision collision) {
            for (int attempt = 0; attempt < 4; attempt++) {
                D2Path.Point candidate = new D2Path.Point(
                        (position.x + DX[direction & 7]) & 0xffff,
                        (position.y + DY[direction & 7]) & 0xffff);
                if (!collision.blocked(candidate.x, candidate.y)) return candidate;
                if (attempt < 3) direction = next[direction & 7];
            }
            return null;
        }

        D2Path.Point distancePoint() {
            // DLL BFF3/C044 reads points[nPoints-1], i.e. packed bPathFinished at
            // cache+0x20 when empty. BB19/BB5D initialize it; BECD sets it to 1.
            // Model (finished ? 1 : 0, 0) safely, NOT D2MOO's non-original INT_MAX.
            return points.isEmpty() ? new D2Path.Point(finished ? 1 : 0, 0)
                    : points.get(points.size() - 1);
        }

        void copyTo(D2Path.Point[] destination, int index) {
            for (D2Path.Point point : points) destination[index++] = point;
        }
    }

    private static final class Path {
        final D2Path.Point[] points = new D2Path.Point[D2Path.MAX_PATH_LENGTH];
        int index, length;

        Path(List<D2Path.Point> line) {
            java.util.Arrays.fill(points, ZERO);
            length = line.size();
            for (int i = 0; i < length; i++) points[i] = line.get(i);
        }

        void terminate(int length) {
            this.length = length;
            points[length] = new D2Path.Point(0, points[length].y); // only X is cleared
        }

        List<D2Path.Point> prefix(int count) {
            List<D2Path.Point> result = new ArrayList<>(count);
            for (int i = 0; i < count; i++) result.add(points[i]);
            return result;
        }
    }

    public static final class Result {
        private final D2Path.Point target;
        private final List<D2Path.Point> rawPoints, points;
        public final int detours;

        private Result(D2Path.Point start, D2Path.Point target,
                List<D2Path.Point> rawPoints, int detours) {
            this.target = target;
            this.rawPoints = Collections.unmodifiableList(new ArrayList<>(rawPoints));
            this.points = D2PathMisc.simplifyToLines(start, this.rawPoints);
            this.detours = detours;
        }

        /** Unsimplified prefix selected by the native return path (no sentinel). */
        public List<D2Path.Point> rawPoints() { return rawPoints; }
        /** Native PATH_SimplifyToLines output, not an extra collision-checked smoothing pass. */
        public List<D2Path.Point> points() { return points; }
        /** Emitted coordinate equality only, not target-unit arrival or dynamic-path readiness. */
        public boolean reachedTarget() {
            return !points.isEmpty() && points.get(points.size() - 1).equals(target);
        }
    }
}
