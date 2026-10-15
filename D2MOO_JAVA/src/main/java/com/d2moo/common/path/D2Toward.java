package com.d2moo.common.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Point generation from 1.10f PathMisc.cpp PATH_Toward_6FDAA9F0 and sub_6FDAA720.
 * Requires a prepared coordinate target, velocity remaining nonzero after the
 * initial wrapper normalization, and stable collision queries.
 * The caller supplies the native pattern/mask/room policy through Collision.
 * Does not implement target-unit preparation, distance consumption, velocity normalization,
 * type dispatch or wall-follow fallback, and does not install an ECS movement path.
 */
public final class D2Toward {
    @FunctionalInterface
    public interface Collision { boolean blocked(int x, int y); }

    private D2Toward() {}

    /** Raw native points, including duplicate/source points and budget-boundary quirks. */
    public static Result compute(D2Path.Point start, D2Path.Point target,
            int maximumDistance, int targetTolerance, int directionOffset, Collision collision) {
        requireInputs(start, target, collision);
        if (maximumDistance < 0 || maximumDistance >= D2Path.MAX_PATH_LENGTH
                || targetTolerance < 0 || targetTolerance > 255) {
            throw new IllegalArgumentException("distance must be 0..77 and tolerance 0..255");
        }
        List<D2Path.Point> points = new ArrayList<>();
        if (directionOffset != 0) {
            // Nonzero offset bypasses the initial ray and tolerance test entirely.
            localSteps(start, target, start, maximumDistance, directionOffset, true, points, collision);
        } else {
            return computeAfterRay(start, target, maximumDistance, targetTolerance,
                    trace(start, target, collision), collision);
        }
        return new Result(start, target, points);
    }

    /** Shared zero-offset tail after the stateful entry has performed 0x6FDAABF0 exactly once. */
    static Result computeAfterRay(D2Path.Point start, D2Path.Point target,
            int maximumDistance, int targetTolerance, RayResult ray, Collision collision) {
        List<D2Path.Point> points = new ArrayList<>();
        if (ray.clear || D2PathMisc.approximateDistance(ray.endpoint, target) <= targetTolerance) {
            points.add(ray.endpoint);
        } else {
            if (!ray.endpoint.equals(start)) points.add(ray.endpoint);
            localSteps(start, target, ray.endpoint, maximumDistance, 0, false, points, collision);
        }
        return new Result(start, target, points);
    }

    private static void localSteps(D2Path.Point start, D2Path.Point target, D2Path.Point current,
            int maximumDistance, int offset, boolean includeSourceOnTurn,
            List<D2Path.Point> points, Collision collision) {
        int previousDirection = -1;
        int distance = 0;
        boolean changedDirection = false;
        boolean blockedOrReversed = false;
        while (distance < maximumDistance) {
            // Keep the previous iteration's changedDirection on this exit.
            if (current.equals(target)) break;
            changedDirection = false;
            int direction = firstFreeDirection(current, target, offset, collision);
            if (direction == -1 || ((direction + 4) & 7) == previousDirection) {
                blockedOrReversed = true;
                break;
            }
            if (direction != previousDirection) {
                if (includeSourceOnTurn || !current.equals(start)) points.add(current);
                changedDirection = true;
            }
            // D2PathPointStrc fields are uint16_t, including rotated steps at the edge.
            current = new D2Path.Point(
                    (current.x + D2PathMisc.directionOffsetX(direction)) & 0xffff,
                    (current.y + D2PathMisc.directionOffsetY(direction)) & 0xffff);
            previousDirection = direction;
            distance++;
        }
        // PATH_Toward's LABEL_20 appends after a failed/reversing step regardless
        // of the previous iteration. On a normal exit LABEL_22 omits a final
        // newly-turned step, even if it would have reached the target.
        if (distance != 0 && (blockedOrReversed || !changedDirection)) points.add(current);
    }

    /** Native ordered three-candidate collision test; -1 means all three blocked. */
    public static int firstFreeDirection(D2Path.Point from, D2Path.Point target,
            int offset, Collision collision) {
        requireInputs(from, target, collision);
        int[] directions = D2PathMisc.directions(from, target);
        for (int rawDirection : directions) {
            int direction = (rawDirection + offset) & 7;
            int x = (from.x + D2PathMisc.directionOffsetX(direction)) & 0xffff;
            int y = (from.y + D2PathMisc.directionOffsetY(direction)) & 0xffff;
            if (!collision.blocked(x, y)) return direction;
        }
        return -1;
    }

    /** PATH_RayTrace (0x6FDAAD10): rollback is to the last completed iteration. */
    public static RayResult trace(D2Path.Point start, D2Path.Point target, Collision collision) {
        requireInputs(start, target, collision);
        int x = start.x, y = start.y, lastX = x, lastY = y;
        int nx = Math.abs(target.x - x) + 1, ny = Math.abs(target.y - y) + 1;
        int stepX = target.x >= x ? 1 : -1, stepY = target.y >= y ? 1 : -1;
        if (nx == ny) {
            while (x != target.x) {
                x += stepX;
                y += stepY;
                if (collision.blocked(x, y)) return ray(lastX, lastY, false);
                lastX = x;
                lastY = y;
            }
        } else if (nx > ny) {
            int offset = ny;
            while (x != target.x) {
                x += stepX;
                if (collision.blocked(x, y)) return ray(lastX, lastY, false);
                offset += ny;
                if (offset >= nx) {
                    offset -= nx;
                    y += stepY;
                    if (offset > 0 && collision.blocked(x, y)) return ray(lastX, lastY, false);
                }
                lastX = x;
                lastY = y;
            }
        } else {
            int offset = nx;
            while (y != target.y) {
                y += stepY;
                if (collision.blocked(x, y)) return ray(lastX, lastY, false);
                offset += nx;
                if (offset >= ny) {
                    offset -= ny;
                    x += stepX;
                    if (offset > 0 && collision.blocked(x, y)) return ray(lastX, lastY, false);
                }
                lastX = x;
                lastY = y;
            }
        }
        return ray(target.x, target.y, true);
    }

    private static RayResult ray(int x, int y, boolean clear) {
        return new RayResult(new D2Path.Point(x, y), clear);
    }

    private static void requireInputs(D2Path.Point start, D2Path.Point target, Collision collision) {
        if (start == null || target == null || collision == null
                || start.x < 0 || start.x > 0xffff || start.y < 0 || start.y > 0xffff
                || target.x < 0 || target.x > 0xffff || target.y < 0 || target.y > 0xffff) {
            throw new IllegalArgumentException("uint16 coordinates and collision are required");
        }
    }

    public static final class RayResult {
        public final D2Path.Point endpoint;
        public final boolean clear;
        RayResult(D2Path.Point endpoint, boolean clear) {
            this.endpoint = endpoint;
            this.clear = clear;
        }
    }

    public static final class Result {
        private final D2Path.Point start, target;
        private final List<D2Path.Point> points;
        private Result(D2Path.Point start, D2Path.Point target, List<D2Path.Point> points) {
            this.start = start;
            this.target = target;
            this.points = Collections.unmodifiableList(new ArrayList<>(points));
        }
        public List<D2Path.Point> points() { return points; }
        /** Excludes a source-only result; not a full dynamic-path wrapper normalization. */
        public boolean hasMovementPoints() {
            for (D2Path.Point point : points) if (!point.equals(start)) return true;
            return false;
        }
        /** Whether the emitted last point is the requested coordinate, not target-unit arrival. */
        public boolean reachedTarget() {
            return !points.isEmpty() && points.get(points.size() - 1).equals(target);
        }
    }
}
