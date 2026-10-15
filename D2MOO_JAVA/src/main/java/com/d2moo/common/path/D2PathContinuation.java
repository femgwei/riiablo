package com.d2moo.common.path;

import java.util.Objects;

/**
 * Non-missile continuation policy from 1.10f D2Common 0x6FDAC8F0 (#10236)
 * and 0x6FDACA41..CB2C (inlined sub_6FD5DB70). This is a state projection,
 * not a complete dynamic path, search wrapper (#10142), or movement tick (#10226).
 * Callers supply resolved uint16 coordinates and stable, read-only target observations.
 * Search/refresh callbacks deliberately operate on the same mutable state; failed
 * searches can have native side effects which must survive the type-15 fallback.
 */
public final class D2PathContinuation {
    public static final int UNKNOWN_FLAG_0X10 = 0x10;
    public static final int UNIT_DOUPDATE = 1;
    private static final int MISSILE_MASK = 0x40000;

    private D2PathContinuation() {}

    public enum Decision { KEEP, STOP, REPLAN_COORDINATE, REPLAN_UNIT }

    /**
     * Unit identity, D2Common_10399 distance, and sub_6FDAB940 sampling belong to
     * the adapter. In particular, samplePoint must include native FOG lead when
     * applicable; this class must not substitute Euclidean distance or live position.
     */
    public interface Target {
        int unitType();
        int distanceToOwner();
        D2Path.Point samplePoint();
    }

    public interface Effects {
        /** UNITROOM_RefreshUnit, before DOUPDATE and budget consumption. */
        void refresh(State state);

        /** D2Common_10142(path, owner, 0), including its state writes and raw result. */
        int search(State state);
    }

    /**
     * Relevant native fields only. Point values must be uint16 coordinates, indices
     * and counts native signed int32. Defaults are zeroed projection values, not a
     * port of native allocation/initialization. Callbacks must synchronize their
     * external path representation with this state; no automatic rollback is done.
     */
    public static final class State {
        public final int ownerType;
        public int ownerFlags;
        public int pathFlags;
        public int pathType;
        public int previousPathType; // +0x40, saved only by PATH_SetType's flag gate
        public int directionOffset;
        public int currentPointIndex;
        public int pathPointCount;
        public D2Path.Point currentPoint = new D2Path.Point(0, 0);
        public D2Path.Point targetPoint = currentPoint;
        public D2Path.Point previousTargetPoint = currentPoint;
        public D2Path.Point finalTargetPoint = currentPoint;
        public Target targetUnit;

        private int dist;       // +0x90 nDist: movement-attempt counter, NOT the replan budget
        private int distMax;    // +0x91 nDistMax: search distance
        private int stepNum;    // +0x93 nStepNum: stop distance to target unit
        private int distance;   // +0x94 nDistance: continuation budget

        public State(int ownerType) { this.ownerType = ownerType; }

        public int nDist() { return dist; }
        public int nDistMax() { return distMax; }
        public int nStepNum() { return stepNum; }
        public int nDistance() { return distance; }

        /** #10190 at 0x6FDAA250: low byte only; does not set nDistMax/nDistance. */
        public void setDist(int value) { dist = value & 0xff; }

        /** 0x6FDAA280: truncate FIRST, then cap at 77; nDistance is untouched. */
        public void setNewDistance(int value) { dist = distMax = Math.min(value & 0xff, 77); }

        /** Adapter assignment of an already resolved native unsigned byte. */
        public void setStepNum(int value) { stepNum = unsignedByte(value); }

        /** PATH_SetDistance at 0x6FDAA4E0 asserts 0..255, unlike setDist. */
        public void setDistance(int value) { distance = unsignedByte(value); }

        /** 0x6FDAA530: int32 addition wraps BEFORE the signed 0..255 clamp. */
        public void addToDistance(int amount) { distance = Math.max(0, Math.min(255, distance + amount)); }

        /** Only the index/count test, NOT #10226's active/velocity/movement gates. */
        public boolean hasRemainingPoints() { return currentPointIndex < pathPointCount; }
    }

    /**
     * Pure policy apart from lazy read-only target observations. KEEP does not mean
     * that a waypoint exists or movement is active. No target sampling occurs when
     * an exhausted circle or the unit stop-distance check has already decided.
     */
    public static Decision decide(State state) {
        requireNonMissile(state);
        if ((state.pathType == 5 || state.pathType == 6) && !state.hasRemainingPoints()) {
            return Decision.KEEP;
        }
        Target target = state.targetUnit;
        if (target == null) {
            return !state.hasRemainingPoints() && !state.currentPoint.equals(state.finalTargetPoint)
                    ? Decision.REPLAN_COORDINATE : Decision.KEEP;
        }
        if (target.distanceToOwner() <= state.nStepNum()) return Decision.STOP;

        // DLL saves current XY before sub_6FDAB940; target sampling also happens for
        // objects/items/etc, although only player/monster displacement triggers here.
        D2Path.Point current = state.currentPoint;
        D2Path.Point sampled = target.samplePoint();
        int targetType = target.unitType();
        if ((targetType == 0 || targetType == 1)
                && (Math.abs(state.previousTargetPoint.x - sampled.x) > 5
                || Math.abs(state.previousTargetPoint.y - sampled.y) > 5)) {
            return Decision.REPLAN_UNIT;
        }
        return state.hasRemainingPoints() || state.finalTargetPoint.equals(current)
                ? Decision.KEEP : Decision.REPLAN_UNIT;
    }

    /**
     * Inlined sub_6FD5DB70: KEEP=1, STOP=0, replan returns #10236's raw result.
     * The caller still needs #10226's post-search index/count test and tick gates.
     */
    public static int checkAndResume(State state, Effects effects) {
        switch (decide(state)) {
            case STOP: return 0;
            case REPLAN_COORDINATE: return resume(state, false, effects);
            case REPLAN_UNIT: return resume(state, true, effects);
            default: return 1;
        }
    }

    /**
     * #10236 only. towardUnit is native a2, not inferred from targetUnit presence.
     * Type writes are direct: do NOT call PATH_SetType/reapply flags or dirOffset.
     */
    public static int resume(State state, boolean towardUnit, Effects effects) {
        if (state == null) return 0;
        requireNonMissile(state);
        if ((state.pathFlags & UNKNOWN_FLAG_0X10) == 0) {
            if (state.ownerType == 1 && state.nDistance() == 0) return 0;
            Objects.requireNonNull(effects, "refresh/search callbacks").refresh(state);
            state.ownerFlags |= UNIT_DOUPDATE;
            state.addToDistance(-state.currentPointIndex);
        }
        Objects.requireNonNull(effects, "refresh/search callbacks");
        if (state.pathType != 2 && state.pathType != 13 && state.pathType != 15) {
            return effects.search(state);
        }
        if (towardUnit) {
            state.pathType = 13;
        } else {
            state.pathType = 2;
            state.targetPoint = state.finalTargetPoint;
        }
        int result = effects.search(state);
        if (result == 0) {
            state.pathType = 15;
            result = effects.search(state);
        }
        return result;
    }

    private static int unsignedByte(int value) {
        if (value < 0 || value > 255) throw new IllegalArgumentException("uint8 value required");
        return value;
    }

    private static void requireNonMissile(State state) {
        Objects.requireNonNull(state, "path state");
        if ((state.pathFlags & MISSILE_MASK) != 0) {
            throw new IllegalArgumentException("missiles bypass this continuation policy");
        }
    }
}
