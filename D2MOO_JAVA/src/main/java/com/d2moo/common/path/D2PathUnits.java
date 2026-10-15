package com.d2moo.common.path;

import java.util.Objects;

/**
 * Resolved-unit coordinate/target helpers from 1.10f PathUtil.cpp, the target phase
 * of #10142, and Units.cpp #10399. Unit snapshots are NOT entity-ID lookup, table
 * size resolution, target lifetime management, or the full search/movement wrapper.
 */
public final class D2PathUnits {
    // Fog.dll 1.10f #10084, table VA 0x6FF742A8, every eighth float.
    // PathUtil's angle is always a multiple of eight. Keep the actual asymmetric
    // float bits, not an ideal sine or a mirrored quarter-wave approximation.
    private static final int[] SIN_BITS = {
            0x00000000, 0x3dc8bd36, 0x3e47c5c2, 0x3e94a032, 0x3ec3ef16, 0x3ef15aea, 0x3f0e39da, 0x3f226799,
            0x3f3504f3, 0x3f45e404, 0x3f54db32, 0x3f61c598, 0x3f6c835f, 0x3f74fa0b, 0x3f7b14bf, 0x3f7ec46d,
            0x3f800000, 0x3f7ec46d, 0x3f7b14be, 0x3f74fa0a, 0x3f6c835e, 0x3f61c597, 0x3f54db31, 0x3f45e403,
            0x3f3504f2, 0x3f226798, 0x3f0e39d9, 0x3ef15ae8, 0x3ec3ef13, 0x3e94a02f, 0x3e47c5bc, 0x3dc8bd2b,
            0xb3bbbd2e, 0xbdc8bd42, 0xbe47c5c8, 0xbe94a035, 0xbec3ef18, 0xbef15aed, 0xbf0e39db, 0xbf22679b,
            0xbf3504f4, 0xbf45e405, 0xbf54db32, 0xbf61c598, 0xbf6c835f, 0xbf74fa0b, 0xbf7b14bf, 0xbf7ec46d,
            0xbf800000, 0xbf7ec46d, 0xbf7b14be, 0xbf74fa0a, 0xbf6c835e, 0xbf61c596, 0xbf54db30, 0xbf45e402,
            0xbf3504f1, 0xbf226797, 0xbf0e39d8, 0xbef15ae5, 0xbec3ef10, 0xbe94a02c, 0xbe47c5b7, 0xbdc8bd1f
    };

    private D2PathUnits() {}

    /** Native unsigned fp16 coordinates and raw +0x64 direction byte. */
    public static final class DynamicPath {
        public final int precisionX, precisionY, direction;

        public DynamicPath(int precisionX, int precisionY, int direction) {
            this.precisionX = precisionX;
            this.precisionY = precisionY;
            this.direction = unsignedByte(direction);
        }
    }

    /** Immutable per-observation projection; resolved sizeX comes from UNITS_GetUnitSizeX. */
    public static final class Unit {
        public final int type, sizeX;
        private final DynamicPath dynamicPath;
        private final D2Path.Point staticPoint;
        private final Integer staticPathDirection;
        private final boolean door, orientationIsOne;

        private Unit(int type, int sizeX, DynamicPath dynamicPath, D2Path.Point staticPoint,
                Integer staticPathDirection, boolean door, boolean orientationIsOne) {
            if (type < 0 || type > 5 || sizeX < 0) {
                throw new IllegalArgumentException("unit type 0..5 and nonnegative resolved size required");
            }
            this.type = type;
            this.sizeX = sizeX;
            this.dynamicPath = dynamicPath;
            this.staticPoint = staticPoint;
            this.staticPathDirection = staticPathDirection;
            this.door = door;
            this.orientationIsOne = orientationIsOne;
        }

        /** Player/monster/missile; a null dynamic path resolves coordinates and direction to zero. */
        public static Unit dynamic(int type, int sizeX, DynamicPath path) {
            if (isStatic(type)) throw new IllegalArgumentException("static unit type");
            return new Unit(type, sizeX, path, null, null, false, false);
        }

        /** Object/item/tile with raw int32 static coordinates, not yet narrowed to path points. */
        public static Unit stationary(int type, int sizeX, D2Path.Point point) {
            if (!isStatic(type)) throw new IllegalArgumentException("dynamic unit type");
            return new Unit(type, sizeX, null, Objects.requireNonNull(point), null, false, false);
        }

        /**
         * Optional raw byte at target path +0x64. Native nonzero-speed sampling also
         * reads this offset for static targets; do not invent a zero-facing value.
         */
        public static Unit stationary(int type, int sizeX, D2Path.Point point, int pathDirection) {
            if (!isStatic(type)) throw new IllegalArgumentException("dynamic unit type");
            return new Unit(type, sizeX, null, Objects.requireNonNull(point),
                    unsignedByte(pathDirection), false, false);
        }

        public static Unit door(int sizeX, D2Path.Point point, boolean orientationIsOne) {
            return new Unit(2, sizeX, null, Objects.requireNonNull(point), null, true, orientationIsOne);
        }

        public static Unit door(int sizeX, D2Path.Point point, boolean orientationIsOne, int pathDirection) {
            return new Unit(2, sizeX, null, Objects.requireNonNull(point),
                    unsignedByte(pathDirection), true, orientationIsOne);
        }

        private int pathDirection() {
            if (!isStatic(type)) return dynamicPath == null ? 0 : dynamicPath.direction;
            if (staticPathDirection == null) {
                throw new IllegalArgumentException("static lead requires explicit target path +0x64 byte");
            }
            return staticPathDirection;
        }
    }

    /** UNITS_GetX/YPosition: static int32 coordinates, dynamic unsigned high fp16 words, or zero. */
    public static D2Path.Point coordinates(Unit unit) {
        Objects.requireNonNull(unit, "resolved unit");
        if (isStatic(unit.type)) return unit.staticPoint;
        return unit.dynamicPath == null ? new D2Path.Point(0, 0)
                : new D2Path.Point(unit.dynamicPath.precisionX >>> 16, unit.dynamicPath.precisionY >>> 16);
    }

    /**
     * PathUtil 0x6FDAB890: add the native FOG lead and store uint16. speed is the
     * owner's +0x68 unsigned byte, not movement velocity. Direction is the target's
     * raw +0x64 byte (normally 0..63); no isMoving/velocity gate is applied here.
     */
    public static D2Path.Point lead(D2Path.Point point, int speed, int direction) {
        Objects.requireNonNull(point, "coordinate");
        unsignedByte(speed);
        unsignedByte(direction);
        int angle = (direction - (speed << 5)) & 63;
        // x87 loads float operands, multiplies without a float store, then __ftol
        // truncates toward zero. float24 * byte8 fits EXACTLY in double53.
        int dx = (int) ((double) sineAtPathAngle(angle + 16) * speed);
        int dy = (int) ((double) sineAtPathAngle(angle) * speed);
        return new D2Path.Point((point.x + dx) & 0xffff, (point.y + dy) & 0xffff);
    }

    // Deliberately only the stride-eight subset consumed by these path helpers.
    static float sineAtPathAngle(int index) { return Float.intBitsToFloat(SIN_BITS[index & 63]); }

    /** sub_6FDAB940: narrow coordinates first, then apply lead to ANY target type when speed != 0. */
    public static D2Path.Point sample(Unit target, int speed) {
        unsignedByte(speed);
        D2Path.Point point = coordinates(target);
        point = new D2Path.Point(point.x & 0xffff, point.y & 0xffff);
        return speed == 0 ? point : lead(point, speed, target.pathDirection());
    }

    /**
     * #10142's inlined PATH_UpdateTargetUnit phase only. The earlier raw-coordinate
     * zero-axis gate and later coincidence/room/search gates are NOT performed here.
     * Result tolerance is path-info field_14, not the persistent nStepNum stop distance.
     */
    public static Prepared prepare(D2Path.Point start, D2Path.Point oldTarget, Unit target, int speed) {
        requireUint16(start);
        requireUint16(oldTarget);
        unsignedByte(speed);
        if (target == null) return new Prepared(oldTarget, 1);
        D2Path.Point point = coordinates(target);
        point = new D2Path.Point(point.x & 0xffff, point.y & 0xffff);
        if (point.x == 0 && point.y == 0) return new Prepared(point, 0);
        switch (target.type) {
            case 0:
            case 1:
                return new Prepared(speed == 0 ? point : lead(point, speed, target.pathDirection()), 1);
            case 2:
                return new Prepared(target.door ? D2PathTarget.doorTarget(start, point, target.orientationIsOne)
                        : point, 2);
            case 4: return new Prepared(point, 2);
            default: return new Prepared(point, 1);
        }
    }

    public static final class Prepared {
        public final D2Path.Point target;
        public final int tolerance;

        private Prepared(D2Path.Point target, int tolerance) {
            this.target = target;
            this.tolerance = tolerance;
        }
    }

    /** #10399 at 0x6FDC2CB0: size-aware native stopping distance, NOT Euclidean or path cost. */
    public static int distance(Unit first, Unit second) {
        return distance(coordinates(first), first.sizeX, coordinates(second), second.sizeX);
    }

    /**
     * Arithmetic stage of #10399. Requires nonnegative resolved sizes and coordinate
     * differences with a representable int32 absolute value; invalid native memory/
     * overflowed abs cannot safely index the short table and is explicitly rejected.
     */
    public static int distance(D2Path.Point first, int firstSize, D2Path.Point second, int secondSize) {
        Objects.requireNonNull(first);
        Objects.requireNonNull(second);
        if (firstSize < 0 || secondSize < 0) throw new IllegalArgumentException("negative unit size");
        long dx = Math.abs((long) first.x - second.x), dy = Math.abs((long) first.y - second.y);
        if (dx > Integer.MAX_VALUE || dy > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("native absolute coordinate difference out of range");
        }
        int x = (int) dx, y = (int) dy;
        if (x >= 8 || y >= 8 || firstSize >= 4 || secondSize >= 4) {
            int radius = (firstSize >>> 1) + (secondSize >>> 1);
            x = Math.max(0, x - radius);
            y = Math.max(0, y - radius);
            return x <= y ? x + 2 * y : y + 2 * x;
        }
        // Existing PathMisc helper uses this same native 8x8 table, returning
        // raw+1 for nonnegative entries, zero for the negative sentinel.
        int result = D2PathMisc.approximateDistance(first, second);
        if (result == 0) return 0;
        result--;
        if (firstSize == 3 || secondSize == 3) result = Math.max(0, result - 1);
        if (firstSize <= 1 || secondSize <= 1) result++;
        return result;
    }

    /** Stable snapshot adapter for the continuation layer; sampling remains lazy after the stop check. */
    public static D2PathContinuation.Target observation(Unit owner, Unit target, int speed) {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(target);
        unsignedByte(speed);
        return new D2PathContinuation.Target() {
            @Override public int unitType() { return target.type; }
            @Override public int distanceToOwner() { return distance(target, owner); }
            @Override public D2Path.Point samplePoint() { return sample(target, speed); }
        };
    }

    private static boolean isStatic(int type) { return type == 2 || type == 4 || type == 5; }

    private static int unsignedByte(int value) {
        if (value < 0 || value > 255) throw new IllegalArgumentException("uint8 value required");
        return value;
    }

    private static void requireUint16(D2Path.Point point) {
        if (point == null || point.x < 0 || point.x > 65535 || point.y < 0 || point.y > 65535) {
            throw new IllegalArgumentException("uint16 path point required");
        }
    }
}
