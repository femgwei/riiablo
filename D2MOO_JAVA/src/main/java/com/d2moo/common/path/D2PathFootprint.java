package com.d2moo.common.path;

import com.d2moo.common.collision.D2Collision;
import com.d2moo.common.collision.D2CommonCollision;
import com.d2moo.common.drlg.D2ActiveRoom;
import java.util.Objects;

/**
 * #10222/#10223 unit footprint selection and #10338/#10410 mask/eligibility,
 * checked against 1.10f D2Common 0x6FDA8320..85FB and 0x6FDBDE10/FB40.
 * Writes through the existing in-memory room-grid primitives. This does NOT
 * resolve entity lifetime/tables, port recursive box/grid splitting, install
 * production collision, or implement a path-type dispatcher.
 */
public final class D2PathFootprint {
    private D2PathFootprint() {}

    /** Resolved Objects.txt fields; raw bytes are not normalized to 0/1.
     * The eight mode entries are copied; unsupported modes are rejected rather
     * than reading an adjacent native record byte as if it were valid metadata. */
    public static final class ObjectData {
        public final int isDoor, blocksVis, blockMissile, subClass;
        private final int[] hasCollision;

        public ObjectData(int isDoor, int blocksVis, int blockMissile, int subClass, int[] hasCollision) {
            this.isDoor = unsignedByte(isDoor);
            this.blocksVis = unsignedByte(blocksVis);
            this.blockMissile = unsignedByte(blockMissile);
            this.subClass = unsignedByte(subClass);
            if (hasCollision == null || hasCollision.length != 8) {
                throw new IllegalArgumentException("eight Objects.txt collision-mode bytes required");
            }
            this.hasCollision = hasCollision.clone();
            for (int value : this.hasCollision) unsignedByte(value);
        }
    }

    /** Stable per-operation metadata, not an ECS unit or a snapshot to retain across movement.
     * sizeX comes from coordinates.sizeX (already resolved by UNITS_GetUnitSizeX).
     * room is UNITS_GetRoom, NOT necessarily the owner's search start room. */
    public static final class Unit {
        public final D2PathUnits.Unit coordinates;
        public final D2ActiveRoom room;
        public final int mode, pattern, nativeFootprintMask, sizeY;
        private final ObjectData object;

        private Unit(D2PathUnits.Unit coordinates, D2ActiveRoom room, int mode,
                int pattern, int nativeFootprintMask, int sizeY, ObjectData object) {
            this.coordinates = Objects.requireNonNull(coordinates);
            this.room = room;
            this.mode = mode;
            this.pattern = pattern;
            this.nativeFootprintMask = nativeFootprintMask;
            this.sizeY = sizeY;
            this.object = object;
        }

        /** Player/monster/missile. Missing dynamic paths are retained, not silently allocated. */
        public static Unit dynamic(D2PathUnits.Unit coordinates, D2ActiveRoom room,
                int mode, int pattern, int nativeFootprintMask) {
            int type = Objects.requireNonNull(coordinates).type;
            if (type != 0 && type != 1 && type != 3) throw new IllegalArgumentException("dynamic unit required");
            return new Unit(coordinates, room, mode, pattern, nativeFootprintMask, 0, null);
        }

        /** Object with resolved sizeY and the matching Objects.txt record. */
        public static Unit object(D2PathUnits.Unit coordinates, D2ActiveRoom room,
                int mode, int sizeY, ObjectData object) {
            if (Objects.requireNonNull(coordinates).type != 2 || sizeY < 0 || mode < 0 || mode >= 8) {
                throw new IllegalArgumentException("object, nonnegative sizeY and mode 0..7 required");
            }
            return new Unit(coordinates, room, mode, 0, 0, sizeY, Objects.requireNonNull(object));
        }

        /** Item/tile: size shape, not the dynamic pattern and not the object's rectangular shape. */
        public static Unit stationary(D2PathUnits.Unit coordinates, D2ActiveRoom room) {
            int type = Objects.requireNonNull(coordinates).type;
            if (type != 4 && type != 5) throw new IllegalArgumentException("item or tile required");
            return new Unit(coordinates, room, 0, 0, 0, 0, null);
        }
    }

    /** Raw #10338 result: dynamic footprint field is uint32; narrowing is at the grid-call ABI. */
    public static int collisionMask(Unit unit) {
        Objects.requireNonNull(unit);
        switch (unit.coordinates.type) {
            case 2:
                ObjectData object = unit.object;
                if (object.isDoor != 0) {
                    if (object.blocksVis != 0) return 0x806;
                    return object.blockMissile != 0 ? 0x804 : 0x400;
                }
                if ((object.subClass & 4) != 0) return D2Collision.COLLIDE_CORPSE;
                return object.blockMissile != 0 ? 0x404 : 0x400;
            case 4: return D2Collision.COLLIDE_ITEM;
            case 5: return D2Collision.COLLIDE_WALL;
            default:
                // #10177 at 0x6FDA9FC0: null path returns 0xFFFF, NOT zero.
                return D2PathUnits.hasDynamicPath(unit.coordinates) ? unit.nativeFootprintMask : 0xffff;
        }
    }

    /** #10410 checks the current object's table byte, not its footprint mask or animating state. */
    public static boolean hasCollision(Unit unit) {
        Objects.requireNonNull(unit);
        return unit.coordinates.type != 2 || unit.object.hasCollision[unit.mode] != 0;
    }

    /** #10222 never tests death modes or object HasCollision before adding. */
    public static void add(Unit unit) { alter(Objects.requireNonNull(unit), true); }

    /** #10223's boolean is branch eligibility, NOT evidence that any grid bit changed. */
    public static boolean remove(Unit unit, boolean force) {
        Objects.requireNonNull(unit);
        switch (unit.coordinates.type) {
            case 0:
                if (!force && (unit.mode == 0 || unit.mode == 17)) return false;
                break;
            case 1:
                if (!force && (unit.mode == 0 || unit.mode == 12)) return false;
                break;
            case 2:
                if (!hasCollision(unit) && !force) return false;
                break;
            default: break;
        }
        alter(unit, false);
        return true;
    }

    private static void alter(Unit unit, boolean add) {
        int type = unit.coordinates.type;
        D2Path.Point point = D2PathUnits.coordinates(unit.coordinates);
        int mask = collisionMask(unit) & 0xffff;
        if (type == 0 || type == 1) {
            // Native reads pattern from pDynamicPath even before asking for the mask/room.
            if (!D2PathUnits.hasDynamicPath(unit.coordinates)) {
                throw new IllegalStateException("player/monster footprint requires an allocated dynamic path");
            }
            if (add) D2CommonCollision.setMaskWithPattern(unit.room, point.x, point.y, unit.pattern, mask);
            else D2CommonCollision.resetMaskWithPattern(unit.room, point.x, point.y, unit.pattern, mask);
        } else if (type == 2) {
            // Native add builds even a degenerate box, unlike remove's positive-size guard.
            // Existing grid helpers don't model that case; don't pretend a no-op is an oracle.
            if (add && (unit.coordinates.sizeX == 0 || unit.sizeY == 0)) {
                throw new IllegalArgumentException("degenerate native object add box is outside this grid adapter");
            }
            if (add) D2CommonCollision.setMaskWithSizeXY(unit.room, point.x, point.y,
                    unit.coordinates.sizeX, unit.sizeY, mask);
            else D2CommonCollision.resetMaskWithSizeXY(unit.room, point.x, point.y,
                    unit.coordinates.sizeX, unit.sizeY, mask);
        } else {
            if (add) D2CommonCollision.setMaskWithSize(unit.room, point.x, point.y, unit.coordinates.sizeX, mask);
            else D2CommonCollision.resetMaskWithSize(unit.room, point.x, point.y, unit.coordinates.sizeX, mask);
        }
    }

    /** Actual footprint and query adapter for the preceding request layer.
     * Search remains explicit: subclasses supply the correct native type implementation.
     * Room graphs must agree; only owner bounds/metadata and target snapshot identity are
     * checked here. No global live-grid mutation is installed by creating this adapter. */
    public abstract static class RequestEffects implements D2PathRequest.Effects {
        private final D2PathRequest.State request;
        private final Unit owner, target;

        protected RequestEffects(D2PathRequest.State request, Unit owner, Unit target) {
            this.request = Objects.requireNonNull(request);
            this.owner = Objects.requireNonNull(owner);
            this.target = target;
            if (owner.coordinates.type != request.result.path.ownerType
                    || !D2PathUnits.coordinates(owner.coordinates).equals(request.result.path.currentPoint)
                    || owner.coordinates.sizeX != request.unitSize
                    || (target == null ? request.target != null : target.coordinates != request.target)
                    || ((owner.coordinates.type == 0 || owner.coordinates.type == 1)
                        && owner.pattern != request.collisionPattern)
                    || !sameBounds(owner.room, request.result.room)) {
                throw new IllegalArgumentException("request and footprint snapshots must describe the same units/room");
            }
        }

        @Override public final boolean removeOwner(D2PathRequest.State state) { check(state); return remove(owner, false); }
        @Override public final boolean removeTarget(D2PathRequest.State state) { check(state); return remove(target, false); }
        @Override public final void addTarget(D2PathRequest.State state) { check(state); add(target); }
        @Override public final void addOwner(D2PathRequest.State state) { check(state); add(owner); }
        @Override public final boolean blocked(D2PathRequest.State state, int x, int y) {
            check(state);
            return D2CommonCollision.checkAnyCollisionWithPattern(owner.room, x, y,
                    request.collisionPattern, request.moveMask & 0xffff) != 0;
        }

        private void check(D2PathRequest.State state) {
            if (state != request) throw new IllegalArgumentException("adapter is bound to one stable request snapshot");
        }
    }

    private static boolean sameBounds(D2ActiveRoom room, D2PathResult.Room bounds) {
        if (room == null || bounds == null) return room == null && bounds == null;
        return room.getCoords().getNSubtileX() == bounds.x && room.getCoords().getNSubtileY() == bounds.y
                && room.getCoords().getNSubtileWidth() == bounds.width && room.getCoords().getNSubtileHeight() == bounds.height;
    }

    private static int unsignedByte(int value) {
        if (value < 0 || value > 255) throw new IllegalArgumentException("uint8 metadata required");
        return value;
    }
}
