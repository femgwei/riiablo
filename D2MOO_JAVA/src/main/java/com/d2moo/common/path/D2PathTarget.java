package com.d2moo.common.path;

/**
 * Non-missile search-target preparation from 1.10f D2Common 0x6FDA8A75..8CC9.
 * Requires an already resolved target, the native +/-100 coordinate window, and a
 * stable collision view with the caller's footprint, pattern, mask and room policy.
 * This phase alone does not validate a movement request or install a dynamic path.
 */
public final class D2PathTarget {
    public static final int ADJUST_BLOCKED_TARGET = 0x1000;

    @FunctionalInterface
    public interface Collision { boolean blocked(int x, int y); }

    // 1.10f .rdata 0x6FDD1DF8: used directly AND transposed, not a nearest-free flood fill.
    private static final int[][] SNAP = {
            {-1, 0, 0, 0, 0, 0, 0, 0, 1},
            {-1, -1, 0, 0, 0, 0, 0, 1, 1},
            {-1, -1, -1, 0, 0, 0, 1, 1, 1},
            {-1, -1, -1, -1, 0, 1, 1, 1, 1},
            {-1, -1, -1, -1, 0, 1, 1, 1, 1},
            {-1, -1, -1, -1, 0, 1, 1, 1, 1},
            {-1, -1, -1, 0, 0, 0, 1, 1, 1},
            {-1, -1, 0, 0, 0, 0, 0, 1, 1},
            {-1, 0, 0, 0, 0, 0, 0, 0, 1}
    };

    private D2PathTarget() {}

    /**
     * The returned target is the search/final coordinate, NOT the dynamic path's
     * unit target or previous-target snapshot. canSearch only describes this phase.
     */
    public static Result prepare(D2Path.Point start, D2Path.Point target,
            int pathFlags, boolean player, Collision collision) {
        requirePoint(start);
        requirePoint(target);
        if ((pathFlags & 0x40000) != 0) {
            throw new IllegalArgumentException("missile requests bypass native target preparation");
        }
        if (collision == null || Math.abs(target.x - start.x) > 100
                || Math.abs(target.y - start.y) > 100) {
            throw new IllegalArgumentException("stable collision and native +/-100 window required");
        }
        if ((pathFlags & ADJUST_BLOCKED_TARGET) == 0 || !collision.blocked(target.x, target.y)) {
            return new Result(target, true);
        }

        D2Path.Point[] candidates = {target, target, target};
        int[] directions = D2PathMisc.cardinalDirections(target, start);
        while (!candidates[0].equals(start)) {
            for (int i = 0; i < 3; i++) {
                if (directions[i] == -1) continue;
                candidates[i] = step(candidates[i], directions[i]);
                if (!collision.blocked(candidates[i].x, candidates[i].y)) {
                    D2Path.Point selected = candidates[i];
                    // DLL 8C75..8C90 compares SELECTED to start, unlike the upstream
                    // source's second comparison against candidates[0]. The target
                    // assignment happens even if this selected point is the source.
                    if (selected.equals(start)) return new Result(selected, false);
                    return new Result(player ? extendPlayerTarget(start, selected, collision) : selected, true);
                }
            }
            // Only the primary direction is recalculated. The other two candidates
            // accumulate independently in their initial directions (uint16 fields).
            directions[0] = D2PathMisc.cardinalDirections(candidates[0], start)[0];
        }
        // No candidate was selected: the original search target remains unchanged.
        return new Result(target, false);
    }

    /** PATH_AdvanceToDoor (0x6FDA90C0): tolerance is 2; no collision query here. */
    public static D2Path.Point doorTarget(D2Path.Point start, D2Path.Point door, boolean orientationIsOne) {
        requirePoint(start);
        requirePoint(door);
        if (orientationIsOne) {
            return new D2Path.Point(door.x, (door.y + (start.y >= door.y ? 2 : -2)) & 0xffff);
        }
        return new D2Path.Point((door.x + (start.x >= door.x ? 2 : -2)) & 0xffff, door.y);
    }

    private static D2Path.Point extendPlayerTarget(D2Path.Point start,
            D2Path.Point target, Collision collision) {
        int dx = target.x - start.x, dy = target.y - start.y;
        int distance = Math.max(Math.abs(dx), Math.abs(dy));
        if (distance == 0 || distance >= 5) return target;
        int offsetX = SNAP[dy + 4][dx + 4], offsetY = SNAP[dx + 4][dy + 4];
        int x = target.x, y = target.y;
        D2Path.Point result = target;
        while (distance < 5) {
            x += offsetX;
            y += offsetY;
            // DLL 907B..90AC retains int32 query coordinates, even outside uint16.
            // It only narrows the accepted target stores, not the next query registers.
            if (collision.blocked(x, y)) break;
            result = new D2Path.Point(x & 0xffff, y & 0xffff);
            distance++;
        }
        return result;
    }

    private static D2Path.Point step(D2Path.Point point, int direction) {
        return new D2Path.Point((point.x + D2PathMisc.directionOffsetX(direction)) & 0xffff,
                (point.y + D2PathMisc.directionOffsetY(direction)) & 0xffff);
    }

    private static void requirePoint(D2Path.Point point) {
        if (point == null || point.x < 0 || point.x > 0xffff || point.y < 0 || point.y > 0xffff) {
            throw new IllegalArgumentException("uint16 coordinates required");
        }
    }

    public static final class Result {
        public final D2Path.Point target;
        public final boolean canSearch;

        private Result(D2Path.Point target, boolean canSearch) {
            this.target = target;
            this.canSearch = canSearch;
        }
    }
}
