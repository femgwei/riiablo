package com.d2moo.common.path;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Non-missile #10142 result phase (1.10f D2Common 0x6FDA8CB6..8E09),
 * initial waypoint/vector normalization (0x6FDAC790), and room/facing helpers.
 * This is an independent state projection, NOT request validation, collision
 * footprint removal/restoration, a search dispatcher, or a production path installer.
 */
public final class D2PathResult {
    public static final int ACTIVE = 0x20;
    public static final int LEAVES_ROOM = 1;
    private static final int MISSILE_MASK = 0x40000;

    private D2PathResult() {}

    /** Raw native int32 subtile bounds; upper-bound additions intentionally wrap. */
    public static final class Room {
        public final int x, y, width, height;
        public Room(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    /**
     * Additional fields alongside the shared continuation projection. Adapters
     * must keep path.currentPoint synchronized with precisionX/Y's high words.
     * Defaults are projection defaults, not a port of native allocation. The fixed
     * buffer retains stale entries after a shorter result or a rejected request.
     */
    public static final class State {
        public final D2PathContinuation.State path;
        public final D2Path.Point[] points = new D2Path.Point[D2Path.MAX_PATH_LENGTH];
        public Room room;
        public int precisionX, precisionY;
        public int velocity;
        public int previousVelocity; // +0x80, independent of the current velocity
        public int directionVectorX, directionVectorY;
        public int velocityVectorX, velocityVectorY;
        public int direction, newDirection, diffDirection;
        public int pendingUpdate; // native unk0x38; no guessed semantic name beyond pending state

        public State(D2PathContinuation.State path) {
            this.path = Objects.requireNonNull(path);
            requirePoint(path.currentPoint);
            precisionX = D2PathStep.toFp16Center(path.currentPoint.x);
            precisionY = D2PathStep.toFp16Center(path.currentPoint.y);
            Arrays.fill(points, new D2Path.Point(0, 0));
        }

        /** Adapter convenience: write a validated prefix/count without clearing stale buffer slots or index. */
        public void storePoints(List<D2Path.Point> result) {
            Objects.requireNonNull(result);
            if (result.size() > points.length) throw new IllegalArgumentException("native 78-point capacity");
            for (D2Path.Point point : result) requirePoint(point);
            for (int i = 0; i < result.size(); i++) points[i] = result.get(i);
            path.pathPointCount = result.size();
        }
    }

    /** After target preparation: clear index/count and write final even when preparation disallows searching. */
    public static void beginSearch(State state, D2Path.Point searchTarget) {
        requireState(state);
        requirePoint(searchTarget);
        state.path.currentPointIndex = 0;
        state.path.pathPointCount = 0;
        state.path.finalTargetPoint = searchTarget;
    }

    /**
     * Call ONLY after restoring any removed target footprint and then the owner
     * footprint. A zero count only clears ACTIVE; exhausting all points during
     * normalization additionally clears index/count and zeroes vectors/velocity.
     */
    public static int finishSearch(State state) {
        requireState(state);
        requireCount(state);
        D2PathContinuation.State path = state.path;
        if (path.pathPointCount == 0) {
            path.pathFlags &= ~ACTIVE;
            return 0;
        }
        normalizeVectors(state, true, true);
        if (path.currentPointIndex >= path.pathPointCount) return reject(state);
        updateRoomFlag(state);
        path.previousTargetPoint = path.targetPoint;
        state.pendingUpdate = 0;
        if ((path.pathFlags & 0x10) == 0 && path.targetUnit == null) {
            path.targetPoint = state.points[path.pathPointCount - 1];
        }
        path.pathFlags |= ACTIVE;
        return path.pathPointCount; // total count, NOT remaining count
    }

    /** Common 0x6FDA8DFE rejection only; some earlier failures bypass this cleanup. No velocity/buffer reset. */
    public static int reject(State state) {
        requireState(state);
        state.path.currentPointIndex = 0;
        state.path.pathPointCount = 0;
        state.path.pathFlags &= ~ACTIVE;
        return 0;
    }

    /**
     * 0x6FDAC790: skip only points whose CENTER exactly equals current precision,
     * derive vectors with native int32 multiply/shift, and optionally update facing.
     * Uses the existing D2PathStep lookup in its non-overflow native domain: unsigned
     * precision deltas fit int32 and 127*minorDelta fits int32. Unsafe native table
     * indexing outside that domain is rejected, not silently replaced by long math.
     */
    public static void normalizeVectors(State state, boolean normalizeIfSamePoint, boolean forceFacing) {
        normalizeVectors(state, normalizeIfSamePoint, forceFacing, false);
    }

    /** Toward's 0x6FDAABF0 probe reads slot zero with index/count BOTH zero (0x6FDAC7A6).
     * The caller has already written the temporary target. Do not fake a live count of one:
     * an exact-center probe must keep count/index zero while zeroing velocity/vectors. */
    static void normalizeSearchProbe(State state) {
        normalizeVectors(state, true, true, true);
    }

    private static void normalizeVectors(State state, boolean normalizeIfSamePoint,
            boolean forceFacing, boolean emptyProbe) {
        requireState(state);
        requireCount(state);
        D2PathContinuation.State path = state.path;
        if (path.currentPointIndex < 0 || path.currentPointIndex >= path.pathPointCount
                && !(emptyProbe && path.currentPointIndex == 0 && path.pathPointCount == 0)) {
            throw new IllegalArgumentException("normalization requires a current waypoint");
        }
        int x, y;
        while (true) {
            D2Path.Point point = state.points[path.currentPointIndex];
            requirePoint(point);
            x = D2PathStep.toFp16Center(point.x);
            y = D2PathStep.toFp16Center(point.y);
            if (x != state.precisionX || y != state.precisionY) break;
            if (path.currentPointIndex >= path.pathPointCount - 1) {
                state.directionVectorX = state.directionVectorY = 0;
                state.velocityVectorX = state.velocityVectorY = 0;
                state.velocity = 0;
                path.currentPointIndex = path.pathPointCount;
                return;
            }
            path.currentPointIndex++;
        }
        long dx = Math.abs(Integer.toUnsignedLong(x) - Integer.toUnsignedLong(state.precisionX));
        long dy = Math.abs(Integer.toUnsignedLong(y) - Integer.toUnsignedLong(state.precisionY));
        if (Math.max(dx, dy) > Integer.MAX_VALUE || 127L * Math.min(dx, dy) > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("outside native non-overflow direction lookup domain");
        }
        D2PathStep.DirectionVector vector = D2PathStep.directionVector(state.precisionX, state.precisionY, x, y);
        state.directionVectorX = vector.x;
        state.directionVectorY = vector.y;
        state.velocityVectorX = (vector.x * state.velocity) >> 8;
        state.velocityVectorY = (vector.y * state.velocity) >> 8;
        int direction = (path.pathFlags & 0x200) == 0 ? vector.direction : (vector.direction - 32) & 63;
        boolean samePoint = (x >>> 16) == ((state.precisionX + state.velocityVectorX) >>> 16)
                && (y >>> 16) == ((state.precisionY + state.velocityVectorY) >>> 16);
        if (forceFacing || samePoint && normalizeIfSamePoint) updateFacing(state, direction);
    }

    /** 0x6FDA9720: scheduled player/monster turning, direct object/item facing, missile flag0x40 gate. */
    public static void updateFacing(State state, int facing) {
        Objects.requireNonNull(state);
        int direction = facing & 63;
        switch (state.path.ownerType) {
            case 2:
            case 4:
                state.direction = direction;
                break;
            case 3:
                if ((state.path.pathFlags & 0x40) == 0) state.direction = direction;
                break;
            default:
                if (direction != state.newDirection) {
                    state.newDirection = direction;
                    int delta = (direction - state.direction) & 63;
                    int step = delta == 0 ? 0 : delta <= 8 ? 1 : delta <= 16 ? 4 : delta <= 31 ? 8
                            : delta <= 47 ? -8 : delta <= 55 ? -4 : -1;
                    state.diffDirection = step & 255;
                }
                break;
        }
    }

    /**
     * 0x6FDA8220: scan ALL stored live points, including consumed prefixes, against
     * half-open room bounds. DLL sets LEAVES_ROOM for a nonempty path with null room,
     * unlike the upstream source's outer if(room) which only clears it in that case.
     */
    public static void updateRoomFlag(State state) {
        requireState(state);
        requireCount(state);
        state.path.pathFlags &= ~LEAVES_ROOM;
        for (int i = 0; i < state.path.pathPointCount; i++) {
            D2Path.Point point = state.points[i];
            requirePoint(point);
            Room room = state.room;
            if (room == null || point.x < room.x || point.x >= room.x + room.width
                    || point.y < room.y || point.y >= room.y + room.height) {
                state.path.pathFlags |= LEAVES_ROOM;
                break;
            }
        }
    }

    private static void requireState(State state) {
        Objects.requireNonNull(state);
        if ((state.path.pathFlags & MISSILE_MASK) != 0) {
            throw new IllegalArgumentException("non-missile result phase required");
        }
    }

    private static void requireCount(State state) {
        if (state.path.pathPointCount < 0 || state.path.pathPointCount > state.points.length) {
            throw new IllegalArgumentException("native live point count 0..78 required");
        }
    }

    private static void requirePoint(D2Path.Point point) {
        if (point == null || point.x < 0 || point.x > 65535 || point.y < 0 || point.y > 65535) {
            throw new IllegalArgumentException("uint16 waypoint required");
        }
    }
}
