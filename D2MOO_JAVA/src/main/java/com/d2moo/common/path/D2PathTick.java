package com.d2moo.common.path;

/** Non-missile #10226 tick, PathMisc.cpp, and Step.cpp cursor/distance writes.
 * Collision relocation and active-room refresh are supplied by the host. */
public final class D2PathTick {
    private D2PathTick() {}

    public interface Effects extends D2PathContinuation.Effects {
        boolean tryMove(int oldX, int oldY, int newX, int newY, boolean unchecked);
        void recache(D2PathResult.State state);
    }

    public static final class State {
        public final D2PathResult.State result;
        public int acceleration, maximumVelocity, accelerationCounter;
        public int crossedCells;
        public boolean blocked;
        public State(D2PathResult.State result) { this.result = java.util.Objects.requireNonNull(result); }
    }

    public static boolean advance(State tick, int baseVelocity, Effects effects) {
        D2PathResult.State r = tick.result;
        D2PathContinuation.State p = r.path;
        if ((p.pathFlags & 0x40000) != 0) throw new IllegalArgumentException("missile tick required");
        p.pathFlags &= ~8;
        tick.crossedCells = 0;
        tick.blocked = false;
        if ((p.pathFlags & D2PathResult.ACTIVE) != 0 && p.pathPointCount > 0 && r.velocity != 0
                && D2PathContinuation.checkAndResume(p, effects) != 0 && p.hasRemainingPoints()) {
            if (tick.acceleration != 0 && ++tick.accelerationCounter >= 5) {
                r.velocity += tick.acceleration;
                if (r.velocity > tick.maximumVelocity) {
                    r.velocity = tick.maximumVelocity;
                    tick.acceleration = 0;
                } else if (r.velocity < 0) r.velocity = 0;
                tick.accelerationCounter = 0;
            }
            int magnitude = ((baseVelocity <= 0 ? 1024 : baseVelocity) * r.velocity) >> 6;
            int vx = (magnitude * r.directionVectorX) >> 12;
            int vy = (magnitude * r.directionVectorY) >> 12;
            r.velocityVectorX = vx; r.velocityVectorY = vy;
            if (vx != 0 || vy != 0) {
                D2Path.Point target = r.points[p.currentPointIndex];
                int tx = D2PathStep.toFp16Center(target.x), ty = D2PathStep.toFp16Center(target.y);
                // Step.cpp decrements ONCE per attempted cell-crossing tick, including rejection.
                long maximum = Math.max(Math.abs((long) vx), Math.abs((long) vy));
                if (Math.abs((long) (tx - r.precisionX)) <= maximum
                        && Math.abs((long) (ty - r.precisionY)) <= maximum) {
                    vx = tx - r.precisionX; vy = ty - r.precisionY;
                }
                if ((r.precisionX >>> 16) != ((r.precisionX + vx) >>> 16)
                        || (r.precisionY >>> 16) != ((r.precisionY + vy) >>> 16)) {
                    if (p.nDist() != 0 && p.pathType != 8 && p.pathType != 11) p.setDist(p.nDist() - 1);
                }
                D2PathStep.StepResult step = D2PathStep.stepUnit(r.precisionX, r.precisionY,
                        vx, vy, tx, ty, (x,y,nx,ny) -> effects.tryMove(x,y,nx,ny,(p.pathFlags & 4) != 0));
                tick.crossedCells = step.crossedCells;
                tick.blocked = step.blocked;
                if (step.crossedCells > 0 && !step.blocked) p.pathFlags |= 8;
                if (step.blocked) {
                    if (p.ownerType == 1 && (p.pathFlags & 0x10) != 0)
                        D2PathContinuation.resume(p, p.targetUnit != null, effects);
                    else p.currentPointIndex = p.pathPointCount;
                } else if (step.reachedTarget) p.currentPointIndex++;
                r.precisionX = step.x; r.precisionY = step.y;
                p.currentPoint = new D2Path.Point(step.x >>> 16, step.y >>> 16);
                effects.recache(r);
                if (p.hasRemainingPoints()) D2PathResult.normalizeVectors(r, true, true);
                if (p.hasRemainingPoints()) return true;
            }
        }
        reset(r, effects);
        return false;
    }

    public static void reset(D2PathResult.State r, Effects effects) {
        r.precisionX = D2PathStep.fitToFp16Center(r.precisionX);
        r.precisionY = D2PathStep.fitToFp16Center(r.precisionY);
        r.path.currentPoint = new D2Path.Point(r.precisionX >>> 16, r.precisionY >>> 16);
        effects.recache(r);
        r.path.pathFlags &= ~D2PathResult.ACTIVE;
        r.path.pathPointCount = r.path.currentPointIndex = 0;
        r.velocityVectorX = r.velocityVectorY = 0;
    }
}
