package com.d2moo.common.path;

import com.d2moo.common.drlg.D2Seed;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Non-missile #10142 orchestration, 1.10f D2Common 0x6FDA86F1..8E09.
 * Resolved snapshots and effects only: NOT an entity adapter, native search-type
 * dispatcher, footprint implementation, or production movement backend.
 */
public final class D2PathRequest {
    private D2PathRequest() {}

    /** Ordered active-room neighborhood, as returned by DUNGEON_GetAdjacentRoomsList.
     * Keep the graph and bounds stable throughout a request; null entries are allowed. */
    public static final class Room {
        public final D2PathResult.Room bounds;
        public final boolean town;
        public List<Room> nearby = Collections.emptyList();

        public Room(D2PathResult.Room bounds, boolean town) {
            this.bounds = Objects.requireNonNull(bounds);
            this.town = town;
        }
    }

    /** Fields not present in the shared result/continuation projection.
     * target must describe exactly path.targetUnit, not a replacement identity.
     * ownerCanBeInTown must be resolved from the native metadata predicate below. */
    public static final class State {
        public final D2PathResult.State result;
        public final D2PathUnits.Unit target;
        public final Room startRoom;
        public final boolean ownerCanBeInTown;
        public final int speed, unitSize, collisionPattern, moveMask, initialFScore;
        /** Live owner unit +0x20 seed projection; required only for type16. Never cloned/reset. */
        public final D2Seed ownerSeed;
        private final D2PathUnits.Observation observation;
        public int collidedMask;

        /** Effects must share the standard observation's exact owner snapshot.
         * Legacy custom targets have no verifiable owner identity contract. */
        void requireOwnerObservation(D2PathUnits.Unit owner) {
            if (observation == null) return;
            if (result.path.targetUnit != observation || observation.owner != owner) {
                throw new IllegalArgumentException("effects owner/observation changed; rebuild the request");
            }
            observation.requireCurrent();
        }

        void requireObservationCurrent() {
            if (observation == null) return;
            if (result.path.targetUnit != observation) {
                throw new IllegalArgumentException("request observation changed; rebuild the request");
            }
            observation.requireCurrent();
        }

        public State(D2PathResult.State result, D2PathUnits.Unit target, Room startRoom,
                boolean ownerCanBeInTown, int speed, int unitSize, int collisionPattern,
                int moveMask, int initialFScore) {
            this(result,target,startRoom,ownerCanBeInTown,speed,unitSize,collisionPattern,
                    moveMask,initialFScore,null);
        }

        public State(D2PathResult.State result, D2PathUnits.Unit target, Room startRoom,
                boolean ownerCanBeInTown, int speed, int unitSize, int collisionPattern,
                int moveMask, int initialFScore, D2Seed ownerSeed) {
            this.result = Objects.requireNonNull(result);
            if ((target == null) != (result.path.targetUnit == null)) {
                throw new IllegalArgumentException("resolved target and path identity must agree");
            }
            if (result.path.ownerType < 0 || result.path.ownerType > 5) {
                throw new IllegalArgumentException("resolved owner required for footprint effects");
            }
            if (result.room != (startRoom == null ? null : startRoom.bounds)) {
                throw new IllegalArgumentException("room projections must share the native bounds");
            }
            if (speed < 0 || speed > 255 || initialFScore < 0 || initialFScore > 255 || unitSize < 0) {
                throw new IllegalArgumentException("resolved size and unsigned native bytes required");
            }
            observation = result.path.targetUnit instanceof D2PathUnits.Observation
                    ? (D2PathUnits.Observation) result.path.targetUnit : null;
            if (observation != null) {
                observation.requireCurrent();
                if (observation.target != target || observation.speed != speed) {
                    throw new IllegalArgumentException("request target/speed must match the unit observation");
                }
                if (observation.owner.type != result.path.ownerType || observation.owner.sizeX != unitSize) {
                    throw new IllegalArgumentException("request owner type/size must match the unit observation");
                }
            }
            this.target = target;
            this.startRoom = startRoom;
            this.ownerCanBeInTown = ownerCanBeInTown;
            this.speed = speed;
            this.unitSize = unitSize;
            this.collisionPattern = collisionPattern;
            this.moveMask = moveMask; // raw uint32 field; collision implementation narrows if its ABI requires
            this.initialFScore = initialFScore;
            this.ownerSeed = ownerSeed;
        }
    }

    /** Search-local values captured in native order, before footprint removal.
     * targetRoom is the INITIAL lookup; the town fallback does not replace it.
     * Search implementations write the point buffer and return the raw live count. */
    public static final class Search {
        public final State state;
        public final D2Path.Point start, target;
        public final Room targetRoom;
        public final int type, maximumDistance, tolerance;

        private Search(State state, D2Path.Point start, D2Path.Point target, Room targetRoom,
                int type, int maximumDistance, int tolerance) {
            this.state = state;
            this.start = start;
            this.target = target;
            this.targetRoom = targetRoom;
            this.type = type;
            this.maximumDistance = maximumDistance;
            this.tolerance = tolerance;
        }
    }

    public interface Effects {
        /** #10223(owner, FALSE); its return value is intentionally ignored. */
        boolean removeOwner(State state);
        /** #10223(target, FALSE); only a true result causes target restoration. */
        boolean removeTarget(State state);
        void addTarget(State state);
        /** #10222(owner) is unconditional after an attempted search, even if removal returned false. */
        void addOwner(State state);
        /** Stable native pattern/mask collision view, after footprint removals. */
        boolean blocked(State state, int x, int y);
        /** Explicit dispatcher boundary; must not mutate request identity, start, rooms or metadata. */
        int search(Search search);
    }

    /** MONSTERS_CanBeInTown, 0x6FDA56C0: even revived requires a valid MonStats record. */
    public static boolean monsterCanBeInTown(boolean hasMonStats, boolean npc,
            boolean inTown, boolean revived) {
        return hasMonStats && (npc || inTown || revived);
    }

    /** COLLISION_GetRoomBySubTileCoordinates, 0x6FD46620: self first, then ordered nearby rooms. */
    public static Room findRoom(Room start, int x, int y) {
        if (start == null) return null;
        if (contains(start.bounds, x, y)) return start;
        for (Room room : start.nearby) {
            if (room != null && contains(room.bounds, x, y)) return room;
        }
        return null;
    }

    /** Inlined town predicate: only ordinary monsters probe midpoint on an initial lookup miss.
     * This local fallback room does NOT become Search.targetRoom. */
    public static boolean destinationAllowed(int ownerType, boolean canBeInTown,
            Room startRoom, Room targetRoom, D2Path.Point start, D2Path.Point target) {
        if (ownerType != 1 || canBeInTown) return true;
        Room room = targetRoom;
        if (room == null) {
            Room halfway = findRoom(startRoom, start.x + (target.x - start.x) / 2,
                    start.y + (target.y - start.y) / 2);
            room = findRoom(halfway, target.x, target.y);
        }
        return room == null || !room.town;
    }

    /** Null path returns zero. Missiles are explicitly outside this projection.
     * Normal native completion restores target, then owner, BEFORE vector normalization.
     * Java callback exceptions also restore completed removals as a host-safety extension;
     * this is not a claim about native assertion/exception unwinding or state rollback. */
    public static int compute(State state, boolean allowInTown, Effects effects) {
        if (state == null) return 0;
        D2PathResult.State result = state.result;
        D2PathContinuation.State path = result.path;
        if ((path.pathFlags & 0x40000) != 0) throw new IllegalArgumentException("missile dispatcher required");
        state.requireObservationCurrent();
        state.collidedMask = 0;
        if (state.target != null) {
            D2Path.Point raw = D2PathUnits.coordinates(state.target);
            // Early raw zero-axis return preserves index/count/ACTIVE and old snapshots.
            if (raw.x == 0 || raw.y == 0) return 0;
        }
        D2Path.Point start = path.currentPoint;
        if (zero(start)) return D2PathResult.reject(result);
        int maximumDistance = path.nDistMax();
        D2PathUnits.Prepared prepared = D2PathUnits.prepare(start, path.targetPoint, state.target, state.speed);
        path.targetPoint = prepared.target;
        D2Path.Point target = prepared.target;
        if (zero(target) || start.equals(target) || Math.abs(start.x - target.x) > 100
                || Math.abs(start.y - target.y) > 100) return D2PathResult.reject(result);

        Room targetRoom = findRoom(state.startRoom, target.x, target.y);
        // The installed 1.10f DLL ASSERTS here, unlike upstream's outer if(startRoom).
        if (state.startRoom == null) throw new IllegalStateException("1.10f requires start room (0x6FDA8979)");
        if (!destinationAllowed(path.ownerType, state.ownerCanBeInTown,
                state.startRoom, targetRoom, start, target) && !allowInTown) {
            return D2PathResult.reject(result);
        }
        int type = path.pathType;
        Objects.requireNonNull(effects, "footprint/collision/search effects");
        boolean ownerCompleted = false, targetRemoved = false;
        try {
            effects.removeOwner(state);
            ownerCompleted = true;
            if (state.target != null && state.target.sizeX != 0 && (path.pathFlags & 0x800) != 0) {
                targetRemoved = effects.removeTarget(state);
            }
            D2PathTarget.Result adjusted = D2PathTarget.prepare(start, target, path.pathFlags,
                    path.ownerType == 0, (x, y) -> effects.blocked(state, x, y));
            D2PathResult.beginSearch(result, adjusted.target);
            if (adjusted.canSearch) {
                path.pathPointCount = effects.search(new Search(state, start, adjusted.target, targetRoom,
                        type, maximumDistance, prepared.tolerance));
            }
        } finally {
            try {
                if (targetRemoved) effects.addTarget(state);
            } finally {
                if (ownerCompleted) effects.addOwner(state);
            }
        }
        return D2PathResult.finishSearch(result);
    }

    private static boolean zero(D2Path.Point point) { return point.x == 0 && point.y == 0; }

    private static boolean contains(D2PathResult.Room room, int x, int y) {
        return x >= room.x && x < room.x + room.width && y >= room.y && y < room.y + room.height;
    }
}
