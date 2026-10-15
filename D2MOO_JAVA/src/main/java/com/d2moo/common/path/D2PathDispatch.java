package com.d2moo.common.path;

import java.util.List;
import java.util.Objects;

/** Stateful non-missile entry subset: all non-null native table types (0/16 require Search rooms,
 * 16 also requires the live owner seed). Does not replace
 * production A*, implement every native table entry, or consume movement ticks.
 * Stable request metadata and the native room/pattern/mask policy remain adapter responsibilities. */
public final class D2PathDispatch {
    private D2PathDispatch() {}

    /** Dispatch the snapshotted search type, NOT a type inferred from owner/target identity.
     * Live nDirOffset is used as-is; this must not call PATH_SetType or reapply its table. */
    public static int compute(D2PathRequest.Search search, D2Toward.Collision collision) {
        Objects.requireNonNull(search);
        if (search.type == 0 || search.type == 16) {
            validate(search.state.result,search.start,search.target,search.maximumDistance,search.tolerance,collision);
            D2IDAStar.Bounds bounds = D2IDAStar.nativeRoomBounds(search.state.startRoom,search.targetRoom);
            List<D2Path.Point> points = (search.type == 16
                    ? D2IDAStar.findNativeStreamPath(search.start,search.target,bounds,
                        collision::blocked,search.state.initialFScore,(search.tolerance*2)&255,search.state.ownerSeed)
                    : D2IDAStar.findNativePath(search.start,search.target,bounds,
                        collision::blocked,search.state.initialFScore,(search.tolerance*2)&255)).path();
            writePrefix(search.state.result,points);
            return points.size(); // as A*: only the outer Request assigns live count/index/vectors
        }
        return compute(search.state.result, search.start, search.target, search.type,
                search.maximumDistance, search.tolerance, collision);
    }

    /** Direct projection without room/seed metadata: types0/16 require the Search overload. */
    public static int compute(D2PathResult.State state, D2Path.Point start, D2Path.Point target,
            int type, int maximumDistance, int tolerance, D2Toward.Collision collision) {
        Objects.requireNonNull(state);
        if (type == 0 || type == 16) throw new UnsupportedOperationException("native type0/16 require the room-aware Search overload");
        D2PathTypes.Entry entry = D2PathTypes.entry(type);
        if (entry == D2PathTypes.Entry.NONE) throw new IllegalStateException("native null search entry assertion");
        if (entry != D2PathTypes.Entry.TOWARD && entry != D2PathTypes.Entry.TOWARD_ALIAS
                && entry != D2PathTypes.Entry.WALL_FOLLOW && entry != D2PathTypes.Entry.A_STAR
                && entry != D2PathTypes.Entry.STRAIGHT && entry != D2PathTypes.Entry.KNOCKBACK_SERVER
                && entry != D2PathTypes.Entry.KNOCKBACK_CLIENT && entry != D2PathTypes.Entry.LEAP
                && entry != D2PathTypes.Entry.BACKUP_TURN) {
            throw new UnsupportedOperationException("native entry not ported here: " + entry);
        }
        validate(state,start,target,maximumDistance,tolerance,collision);
        D2PathContinuation.State path = state.path;
        if (entry == D2PathTypes.Entry.BACKUP_TURN) {
            path.currentPointIndex = 0;
            path.directionOffset = -4;
            int first = appendLocal(state,start,target,maximumDistance,collision);
            if (first == 0) return 0;
            path.directionOffset = -2;
            int count = appendLocal(state,start,target,maximumDistance,collision);
            if (count == first) {
                path.directionOffset = 2;
                count = appendLocal(state,start,target,maximumDistance,collision);
            }
            path.directionOffset = -4;
            return count;
        }
        if (entry == D2PathTypes.Entry.LEAP) {
            path.currentPointIndex = 0;
            state.points[0] = target;
            if (path.pathType == 4 || target.x == 0 || target.y == 0)
                throw new IllegalStateException("native collision probe assertion");
            if (state.velocity != 0) D2PathResult.normalizeSearchProbe(state);
            D2Path.Point end = state.velocity == 0 ? start : D2Toward.trace(start,target,collision).endpoint;
            if (end.equals(start)) return 0; // local destination differs from client slot-zero alias
            state.points[0] = end;
            path.pathPointCount = 1;
            return 1;
        }
        if (entry == D2PathTypes.Entry.KNOCKBACK_SERVER) {
            D2Path.Point end = target;
            if (path.targetUnit != null) {
                D2Path.Point raw = Objects.requireNonNull(path.targetUnit.coordinates());
                int dx = start.x - raw.x, dy = start.y - raw.y;
                int divisor = Math.max(Math.abs(dx), Math.abs(dy));
                if (divisor < 0) throw new IllegalArgumentException("unsafe native knockback division");
                if (divisor != 0) {
                    int half = (path.nDist() >> 1) + 1;
                    dx = dx * half / divisor;
                    dy = dy * half / divisor;
                }
                end = new D2Path.Point((start.x + dx) & 65535, (start.y + dy) & 65535);
            }
            state.points[0] = end;
            end = D2Toward.trace(start,end,collision).endpoint;
            // #10170 writes target AND clears target unit even on a zero-length result.
            path.targetPoint = end;
            path.targetUnit = null;
            if (end.equals(start)) return 0;
            state.points[0] = end;
            path.pathPointCount = 1;
            return 1; // server does not reset index or normalize vectors
        }
        if (entry == D2PathTypes.Entry.KNOCKBACK_CLIENT) {
            path.currentPointIndex = 0;
            state.points[0] = target;
            if (path.pathType == 4 || target.x == 0 || target.y == 0)
                throw new IllegalStateException("native collision probe assertion");
            if (state.velocity != 0) D2PathResult.normalizeSearchProbe(state);
            state.points[0] = state.velocity == 0 ? start : D2Toward.trace(start,target,collision).endpoint;
            return 1; // live count and target identity remain caller-owned
        }
        if (entry == D2PathTypes.Entry.A_STAR) return aStar(state, start, target, collision);
        if (entry == D2PathTypes.Entry.STRAIGHT) {
            int count = toward(state, start, target, maximumDistance, tolerance, collision);
            if (count != 0) {
                D2Path.Point last = state.points[count - 1];
                if (D2PathMisc.approximateDistance(last, target) <= tolerance && !last.equals(start)) return count;
            }
            int dx = Math.abs(start.x - target.x), dy = Math.abs(start.y - target.y);
            // 0x6FDAB1A9..B1B9: signed int32 imul/add/cmp, NOT a long squared radius.
            if (dx * dx + dy * dy <= 18 * 18) {
                int alternative = aStar(state, start, target, collision);
                if (alternative != 0) return alternative;
                if (count != 0) count = toward(state, start, target, maximumDistance, tolerance, collision);
            }
            return count;
        }
        if (entry == D2PathTypes.Entry.WALL_FOLLOW) {
            // Native type15 writes buffer/returns count; caller assigns count. No initial vectors or index reset.
            List<D2Path.Point> points = D2WallFollow.compute(start, target,
                    path.nDistMax(), path.targetUnit != null, collision::blocked).points();
            writePrefix(state, points);
            return points.size();
        }
        return toward(state, start, target, maximumDistance, tolerance, collision);
    }

    /** A720 appends at live count, but every pass starts geometrically at the search start.
     * Count is committed only at normal return; prefix writes survive callback exceptions. */
    private static int appendLocal(D2PathResult.State state, D2Path.Point start, D2Path.Point target,
            int maximumDistance, D2Toward.Collision collision) {
        int count = state.path.pathPointCount;
        if (count < 0 || count > state.points.length) throw new IllegalArgumentException("native count 0..78 required");
        int previous = -1, distance = 0;
        boolean changed = false;
        D2Path.Point current = start;
        while (distance < maximumDistance) {
            if (current.equals(target)) break;
            changed = false;
            // A8DF..A8ED narrows collision candidates as well as accepted steps.
            int selected = D2Toward.firstFreeDirection(current,target,state.path.directionOffset,collision);
            if (selected < 0 || ((selected + 4) & 7) == previous) break;
            if (selected != previous) {
                count = appendPoint(state,count,current);
                changed = true;
            }
            current = new D2Path.Point((current.x + D2PathMisc.directionOffsetX(selected)) & 65535,
                    (current.y + D2PathMisc.directionOffsetY(selected)) & 65535);
            previous = selected;
            distance++;
        }
        if (!changed && distance != 0) count = appendPoint(state,count,current);
        state.path.pathPointCount = count;
        return count;
    }

    private static int appendPoint(D2PathResult.State state, int count, D2Path.Point point) {
        if (count >= state.points.length) throw new IllegalArgumentException("native 78-point capacity exceeded");
        state.points[count] = point;
        return count + 1;
    }

    private static void validate(D2PathResult.State state, D2Path.Point start, D2Path.Point target,
            int maximumDistance, int tolerance, D2Toward.Collision collision) {
        D2PathContinuation.State path = state.path;
        if ((path.pathFlags & 0x40000) != 0 || path.ownerType != 0 && path.ownerType != 1) {
            throw new IllegalArgumentException("resolved non-missile player/monster required");
        }
        if (!uint16(start) || !uint16(target) || !start.equals(path.currentPoint)
                || (state.precisionX >>> 16) != start.x || (state.precisionY >>> 16) != start.y
                || maximumDistance < 0 || maximumDistance > 77 || tolerance < 0 || tolerance > 255) {
            throw new IllegalArgumentException("synchronized uint16 coordinates and native distance bytes required");
        }
        Objects.requireNonNull(collision);
    }

    private static int aStar(D2PathResult.State state, D2Path.Point start, D2Path.Point target,
            D2Toward.Collision collision) {
        List<D2Path.Point> points = D2AStar.findNativePath(start, target, collision::blocked,
                state.path.targetUnit != null).path();
        // #69E0/#78A0 write only an accepted prefix; index/count/vectors are caller-owned.
        writePrefix(state, points);
        return points.size();
    }

    private static int toward(D2PathResult.State state, D2Path.Point start, D2Path.Point target,
            int maximumDistance, int tolerance, D2Toward.Collision collision) {
        D2PathContinuation.State path = state.path;
        path.currentPointIndex = path.pathPointCount = 0;
        if (path.directionOffset != 0) {
            state.storePoints(D2Toward.compute(start, target, maximumDistance, tolerance,
                    path.directionOffset, collision).points());
        } else {
            // 0x6FDAAA19 writes slot zero before 0x6FDAABF0 normalizes with live count STILL zero.
            state.points[0] = target;
            if (path.pathType == 4) throw new IllegalStateException("native ray live missile type assertion");
            if (target.x == 0 || target.y == 0) throw new IllegalStateException("native ray target zero-axis assertion");
            D2Toward.RayResult ray;
            if (state.velocity == 0) {
                ray = new D2Toward.RayResult(start, false); // vectors/facing untouched by the zero-velocity gate
            } else {
                D2PathResult.normalizeSearchProbe(state);
                ray = state.velocity == 0 ? new D2Toward.RayResult(start, false)
                        : D2Toward.trace(start, target, collision);
            }
            state.storePoints(D2Toward.computeAfterRay(start, target, maximumDistance, tolerance, ray, collision).points());
        }
        return path.pathPointCount;
    }

    /** Concrete request + footprint + supported-entry composition. Creating it installs no live ECS backend. */
    public static final class Effects extends D2PathFootprint.RequestEffects {
        private final D2PathRequest.State request;
        public Effects(D2PathRequest.State request, D2PathFootprint.Unit owner, D2PathFootprint.Unit target) {
            super(request, owner, target);
            this.request = request;
        }
        @Override public int search(D2PathRequest.Search search) {
            if (search.state != request) throw new IllegalArgumentException("adapter bound to one request");
            return compute(search, (x, y) -> blocked(request, x, y));
        }
    }

    private static void writePrefix(D2PathResult.State state, List<D2Path.Point> points) {
        if (points.size() > state.points.length) throw new IllegalArgumentException("native 78-point capacity");
        for (int i = 0; i < points.size(); i++) state.points[i] = points.get(i);
    }

    private static boolean uint16(D2Path.Point point) {
        return point != null && point.x >= 0 && point.x <= 65535 && point.y >= 0 && point.y <= 65535;
    }
}
