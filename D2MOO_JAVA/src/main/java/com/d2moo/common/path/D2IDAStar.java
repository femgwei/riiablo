package com.d2moo.common.path;

import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Standalone port of D2Common {@code Path/IDAStar.cpp}.
 *
 * <p>The implementation intentionally stays independent of riiablo's runtime pathfinder.
 * The existing findPath API is a general-coordinate reference. findNativePath additionally
 * models the verified 1.10f type0 entry arithmetic/cache lifecycle. findNativeStreamPath
 * adds type16's live unit RNG stream; none installs a production movement backend.
 */
public final class D2IDAStar {
    public static final int MAX_ROOM_AREA = 50_000;
    public static final int MAX_NODES = 900;
    public static final int MAX_VISITS_PER_ITERATION = 10_000;
    public static final int F_SCORE_INCREMENT = 5;

    private static final int DEFAULT_F_SCORE_MARGIN = 100;

    // D2Common 1.10f 0x6FDD1CE0, including the non-periodic last two entries.
    private static final int[] RANDOM_OFFSETS = {
            -2,-1,0,1,2,-2,-1,0,1,2,-2,-1,0,1,2,-2,
            -1,0,1,2,-2,-1,0,1,2,-2,-1,0,1,2,-1,1
    };

    private static final int[][] COORD_OFFSETS = {
            {1, 0}, {1, 1}, {0, 1}, {-1, 1},
            {-1, 0}, {-1, -1}, {0, -1}, {1, -1}
    };

    private static final int[][] NEIGHBOR_SEQUENCES = {
            {0, 1, 6, 3, 4, 5, 2, 7},
            {0, 1, 6, 3, 1, 3, 6, 1},
            {0, 1, 1, 1, 1, 1, 2, 7},
            {1, 1, 1, 1, 1, 3, 6, 1},
            {6, 4, 3, 6, 1, 3, 2, 7},
            {7, 7, 7, 7, 7, 5, 2, 7},
            {0, 7, 7, 7, 7, 5, 2, 7},
            {0, 7, 2, 5, 7, 5, 2, 7}
    };

    private D2IDAStar() {}

    public static Result findPath(
            D2Path.Point start,
            D2Path.Point target,
            Bounds bounds,
            D2AStar.Collision collision) {
        int heuristic = requireInputs(start, target, bounds, collision);
        return findPath(
                start,
                target,
                bounds,
                collision,
                saturatedAdd(heuristic, DEFAULT_F_SCORE_MARGIN + 1),
                0);
    }

    /**
     * Runs the native iterative search.
     *
     * @param maximumFScoreExclusive native loop ceiling; the initial heuristic is always tried
     * @param targetDistanceThreshold stop after entering a node whose heuristic is below this
     *     value; use zero to require the exact target
     */
    public static Result findPath(
            D2Path.Point start,
            D2Path.Point target,
            Bounds bounds,
            D2AStar.Collision collision,
            int maximumFScoreExclusive,
            int targetDistanceThreshold) {
        int initialHeuristic = requireInputs(start, target, bounds, collision);
        if (targetDistanceThreshold < 0) {
            throw new IllegalArgumentException("targetDistanceThreshold must not be negative");
        }

        return search(start, target, bounds, collision, initialHeuristic,
                initialHeuristic, maximumFScoreExclusive, targetDistanceThreshold, false, null);
    }

    /** Deterministic type0 rooms-core projection (0x6FDA7A4E..81BA).
     * initialFScore is the PathInfo uint8 ceiling input; threshold is already doubled
     * and narrowed by the outer wrapper. No A* target-unit approach gate applies here.
     * Unsafe native cache-buffer overflow/word-wrapped room domains are rejected. */
    public static Result findNativePath(D2Path.Point start, D2Path.Point target, Bounds bounds,
            D2AStar.Collision collision, int initialFScore, int targetDistanceThreshold) {
        return nativePath(start,target,bounds,collision,initialFScore,targetDistanceThreshold,null);
    }

    /** Type16 rooms-core projection. ownerSeed must be the owner's live shared seed, not a copy.
     * Every direction advance consumes it, even on an unsuccessful search/flush. Root setup
     * does not roll it; contours and subsequent requests continue the same stream. */
    public static Result findNativeStreamPath(D2Path.Point start, D2Path.Point target, Bounds bounds,
            D2AStar.Collision collision, int initialFScore, int targetDistanceThreshold, D2Seed ownerSeed) {
        if (ownerSeed == null) throw new IllegalArgumentException("native type16 requires live owner seed");
        return nativePath(start,target,bounds,collision,initialFScore,targetDistanceThreshold,ownerSeed);
    }

    private static Result nativePath(D2Path.Point start, D2Path.Point target, Bounds bounds,
            D2AStar.Collision collision, int initialFScore, int targetDistanceThreshold, D2Seed ownerSeed) {
        if (!uint16(start) || !uint16(target) || bounds == null || collision == null
                || bounds.minX < 0 || bounds.minY < 0 || bounds.maxX > 65535 || bounds.maxY > 65535
                || initialFScore < 0 || initialFScore > 255
                || targetDistanceThreshold < 0 || targetDistanceThreshold > 255) {
            throw new IllegalArgumentException("native word coordinates/bounds and byte scores required");
        }
        int initial = (short) D2Path.aStarHeuristic(start.x,start.y,target.x,target.y);
        // #7BFB..7C2D: type16 narrows h+h/2 to int16, but root h/f stay unchanged.
        // Java integer division, like the native cdq/sub/sar sequence, truncates toward zero.
        int cutoff = ownerSeed == null ? initial : (short)(initial + initial / 2);
        return search(start,target,bounds,collision,initial,cutoff,Math.max(cutoff,initialFScore),
                targetDistanceThreshold,true,ownerSeed);
    }

    /** Outer #7970 room selection. Identity, not equal bounds, decides the same-room branch.
     * This intentionally follows the directional endpoint calculation, NOT a generic union.
     * Only the ordered, non-wrapping uint16 room projection is supported. */
    public static Bounds nativeRoomBounds(D2PathRequest.Room start, D2PathRequest.Room target) {
        if (start == null) throw new IllegalArgumentException("native IDA start room required");
        D2PathResult.Room a = start.bounds;
        long x = a.x, y = a.y, width = a.width, height = a.height;
        if (target != null && target != start) {
            D2PathResult.Room b = target.bounds;
            x = Math.min(a.x,b.x); y = Math.min(a.y,b.y);
            width = b.x >= a.x ? (long)b.x+b.width-a.x : (long)a.x+a.width-b.x;
            height = b.y >= a.y ? (long)b.y+b.height-a.y : (long)a.y+a.height-b.y;
        }
        if (width == a.width) { x -= 10; width += 20; }
        if (height == a.height) { y -= 10; height += 20; }
        if (x < 0 || y < 0 || width <= 0 || height <= 0 || x+width > 65535 || y+height > 65535) {
            throw new IllegalArgumentException("outside non-wrapping native IDA room domain");
        }
        Bounds result = new Bounds((int)x,(int)y,(int)(x+width),(int)(y+height));
        nativeCacheSize(result); // DLL asserts only the unpadded area; do not emulate its buffer overrun.
        return result;
    }

    private static boolean uint16(D2Path.Point p) {
        return p != null && p.x >= 0 && p.y >= 0 && p.x <= 65535 && p.y <= 65535;
    }

    private static int nativeCacheSize(Bounds bounds) {
        long size = ((long)bounds.maxX-bounds.minX+6)*((long)bounds.maxY-bounds.minY+6);
        if (size > MAX_ROOM_AREA) throw new IllegalArgumentException("outside native 50000-cell padded cache domain");
        return (int)size;
    }

    private static Result search(D2Path.Point start, D2Path.Point target, Bounds bounds,
            D2AStar.Collision collision, int initialHeuristic, int initialCutoff, int maximumFScoreExclusive,
            int targetDistanceThreshold, boolean nativeWords, D2Seed ownerSeed) {
        Context context = new Context(bounds, collision, nativeWords, ownerSeed);
        int cutoff = initialCutoff;
        int iterations = 0;
        int lastNodeCount = 0;
        int maximumAllocatedNodes = 0;
        Node validNode = null;

        do {
            iterations++;
            context.resetNodes(start, target, initialHeuristic);
            validNode = visitNodes(
                    context, target, cutoff, targetDistanceThreshold);
            lastNodeCount = context.nodeCount();
            maximumAllocatedNodes = Math.max(maximumAllocatedNodes, lastNodeCount);

            if (validNode != null || lastNodeCount == MAX_NODES) {
                break;
            }
            cutoff = saturatedAdd(cutoff, F_SCORE_INCREMENT);
        } while (cutoff < maximumFScoreExclusive);

        if (validNode == null) {
            return new Result(
                    Collections.<D2Path.Point>emptyList(),
                    false,
                    false,
                    lastNodeCount,
                    maximumAllocatedNodes,
                    iterations,
                    cutoff,
                    start);
        }

        boolean reachedTarget = validNode.point.equals(target);
        List<D2Path.Point> path = flushPath(validNode);
        return new Result(
                path,
                reachedTarget,
                true,
                lastNodeCount,
                maximumAllocatedNodes,
                iterations,
                cutoff,
                validNode.point);
    }

    private static int requireInputs(
            D2Path.Point start,
            D2Path.Point target,
            Bounds bounds,
            D2AStar.Collision collision) {
        if (start == null || target == null || bounds == null || collision == null) {
            throw new IllegalArgumentException("start, target, bounds and collision are required");
        }
        if (!bounds.contains(start.x, start.y)) {
            throw new IllegalArgumentException("start must be inside bounds");
        }
        return D2Path.aStarHeuristic(start.x, start.y, target.x, target.y);
    }

    private static Node visitNodes(
            Context context,
            D2Path.Point target,
            int cutoff,
            int targetDistanceThreshold) {
        Node current = context.root;
        int visits = 0;
        while (!current.point.equals(target)) {
            if (++visits > MAX_VISITS_PER_ITERATION) {
                return null;
            }
            if (!context.bounds.contains(current.point.x, current.point.y)) {
                break;
            }

            int[] offset = COORD_OFFSETS[current.nextNeighborIndex];
            D2Path.Point neighbor = new D2Path.Point(
                    context.coordinate(current.point.x + offset[0]), context.coordinate(current.point.y + offset[1]));
            boolean evaluateNextNeighbor = true;
            boolean mayEvaluateNode = true;

            Integer cachedDistance = context.cached(neighbor);
            if (cachedDistance != null && cachedDistance == 1) {
                mayEvaluateNode = false;
            } else if (cachedDistance == null
                    && context.collision.isBlocked(neighbor.x, neighbor.y)) {
                // One is lower than every legal move cost, so the cell is never tested again.
                context.cache(neighbor, 1);
                mayEvaluateNode = false;
            }

            if (mayEvaluateNode) {
                int newDistance = context.scoreAdd(
                        current.distanceFromStart,
                        D2Path.neighborCost(
                                current.point.x, current.point.y, neighbor.x, neighbor.y));
                cachedDistance = context.cached(neighbor);

                if (cachedDistance == null
                        || Integer.compareUnsigned(newDistance, cachedDistance) < 0
                        || context.nativeWords && newDistance == cachedDistance) {
                    context.cache(neighbor, newDistance);
                    int heuristic = context.word(D2Path.aStarHeuristic(
                            target.x, target.y, neighbor.x, neighbor.y));
                    int fScore = context.scoreAdd(heuristic, newDistance);
                    if (fScore <= cutoff) {
                        Node child = current.bestChild;
                        if (child == null) {
                            child = context.allocate();
                            current.bestChild = child;
                            if (child == null) {
                                return null;
                            }
                            child.parent = current;
                        }

                        Node parent = current;
                        current = child;
                        current.point = neighbor;
                        current.distanceFromStart = newDistance;
                        current.fScore = fScore;
                        current.heuristic = heuristic;
                        current.evaluationsCount = 0;
                        current.sequenceRow = (direction(neighbor, target)
                                - parent.nextNeighborIndex) & 7;
                        current.sequenceIndex = 0;
                        // #817E: reused children start from their PARENT's current direction.
                        if (context.nativeWords) current.nextNeighborIndex = parent.nextNeighborIndex;
                        advanceNeighbor(current, context);

                        if (current.heuristic < targetDistanceThreshold) {
                            return current;
                        }
                        evaluateNextNeighbor = false;
                    }
                }
            }

            if (evaluateNextNeighbor) {
                current = backtrack(current, context);
                if (current == null) {
                    return null;
                }
            }
        }
        return current;
    }

    private static Node backtrack(Node node, Context context) {
        if (node.evaluationsCount < 4) {
            node.sequenceIndex++;
            advanceNeighbor(node, context);
        }

        node.evaluationsCount++;
        if (node.evaluationsCount == 5) {
            while (node != context.root) {
                node = node.parent;
                node.sequenceIndex++;
                advanceNeighbor(node, context);
                if (++node.evaluationsCount != 5) {
                    return node;
                }
            }
            return null;
        }
        return node;
    }

    private static void advanceNeighbor(Node node, Context context) {
        // Native parent unwind advances the sequence BEFORE deciding root exhaustion.
        // Row0 index8 reads the adjacent row1[0], still within the verified 8x8 table.
        int row = node.sequenceRow + (context.nativeWords ? node.sequenceIndex / 8 : 0);
        int column = context.nativeWords ? node.sequenceIndex % 8 : node.sequenceIndex;
        int randomOffset = context.ownerSeed == null ? 0
                : RANDOM_OFFSETS[(int)Seed.rollRandomNumber(context.ownerSeed) & 31];
        node.nextNeighborIndex = (node.nextNeighborIndex
                + NEIGHBOR_SEQUENCES[row][column] + randomOffset) & 7;
    }

    private static int direction(D2Path.Point from, D2Path.Point to) {
        return D2PathMisc.primaryDirection(from, to);
    }

    /** Native flush: compressed straight paths and paths at the 78-point limit are rejected. */
    private static List<D2Path.Point> flushPath(Node end) {
        List<D2Path.Point> reverseTurns = new ArrayList<>();
        int previousDeltaX = -2;
        int previousDeltaY = -2;
        for (Node node = end;
                node != null && node.parent != null && reverseTurns.size() < D2Path.MAX_PATH_LENGTH;
                node = node.parent) {
            int deltaX = node.point.x - node.parent.point.x;
            int deltaY = node.point.y - node.parent.point.y;
            if (deltaX != previousDeltaX || deltaY != previousDeltaY) {
                reverseTurns.add(node.point);
                previousDeltaX = deltaX;
                previousDeltaY = deltaY;
            }
        }
        if (reverseTurns.size() <= 1 || reverseTurns.size() >= D2Path.MAX_PATH_LENGTH) {
            return Collections.emptyList();
        }
        Collections.reverse(reverseTurns);
        return reverseTurns;
    }

    private static int saturatedAdd(int first, int second) {
        long result = (long) first + second;
        return result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }

    private static final class Context {
        final Bounds bounds;
        final D2AStar.Collision collision;
        final Map<D2Path.Point, Integer> coordinateData = new HashMap<>();
        final boolean nativeWords;
        final D2Seed ownerSeed;
        final int[] nativeCache;
        final int stride;
        final List<Node> nodes = new ArrayList<>(MAX_NODES);
        Node root;

        Context(Bounds bounds, D2AStar.Collision collision, boolean nativeWords, D2Seed ownerSeed) {
            this.bounds = bounds;
            this.collision = collision;
            this.nativeWords = nativeWords;
            this.ownerSeed = ownerSeed;
            this.stride = bounds.maxX-bounds.minX+6;
            this.nativeCache = nativeWords ? new int[nativeCacheSize(bounds)] : null;
        }

        int coordinate(int value) { return nativeWords ? value & 0xffff : value; }
        int word(int value) { return nativeWords ? (short)value : value; }
        int scoreAdd(int a, int b) { return nativeWords ? (short)(a+b) : saturatedAdd(a,b); }
        int cacheIndex(D2Path.Point point) {
            long index = (long)point.x+3-bounds.minX
                    + (long)stride*(point.y+3-bounds.minY);
            if (index < 0 || index >= nativeCache.length) {
                throw new IllegalArgumentException("outside native IDA cache memory domain");
            }
            return (int)index;
        }
        Integer cached(D2Path.Point point) {
            if (!nativeWords) return coordinateData.get(point);
            int value = nativeCache[cacheIndex(point)];
            return value == 0 ? null : value;
        }
        void cache(D2Path.Point point, int value) {
            if (nativeWords) nativeCache[cacheIndex(point)] = value;
            else coordinateData.put(point,value);
        }

        void resetNodes(D2Path.Point start, D2Path.Point target, int heuristic) {
            nodes.clear();
            // #7A78 clears native cache ONCE; #7C31 resets only root/storage count per contour.
            if (!nativeWords) coordinateData.clear();
            root = allocate();
            root.point = start;
            root.distanceFromStart = 0;
            root.heuristic = heuristic;
            root.fScore = heuristic;
            root.evaluationsCount = -3;
            root.sequenceRow = 0;
            root.sequenceIndex = 0;
            root.nextNeighborIndex = direction(start, target) & 7;
        }

        Node allocate() {
            if (nodes.size() >= MAX_NODES) return null;
            Node node = new Node();
            nodes.add(node);
            return node;
        }

        int nodeCount() {
            return nodes.size();
        }
    }

    private static final class Node {
        D2Path.Point point;
        Node parent;
        Node bestChild;
        int fScore;
        int heuristic;
        int distanceFromStart;
        int evaluationsCount;
        int sequenceRow;
        int sequenceIndex;
        int nextNeighborIndex;
    }

    /** Inclusive native coordinate limits. */
    public static final class Bounds {
        public final int minX;
        public final int minY;
        public final int maxX;
        public final int maxY;

        public Bounds(int minX, int minY, int maxX, int maxY) {
            if (maxX <= minX || maxY <= minY) {
                throw new IllegalArgumentException("invalid bounds");
            }
            // Native room coordinates store origin + width/height as the inclusive search limit.
            long width = (long) maxX - minX;
            long height = (long) maxY - minY;
            if (width * height > MAX_ROOM_AREA) {
                throw new IllegalArgumentException("bounds exceed native room area limit");
            }
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }

        public boolean contains(int x, int y) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY;
        }
    }

    public static final class Result {
        private final List<D2Path.Point> path;
        private final boolean reachedTarget;
        private final boolean nativePathFound;
        private final int allocatedNodes;
        private final int maximumAllocatedNodes;
        private final int cutoffIterations;
        private final int finalCutoff;
        private final D2Path.Point bestPoint;

        Result(
                List<D2Path.Point> path,
                boolean reachedTarget,
                boolean nativePathFound,
                int allocatedNodes,
                int maximumAllocatedNodes,
                int cutoffIterations,
                int finalCutoff,
                D2Path.Point bestPoint) {
            this.path = Collections.unmodifiableList(new ArrayList<>(path));
            this.reachedTarget = reachedTarget;
            this.nativePathFound = nativePathFound;
            this.allocatedNodes = allocatedNodes;
            this.maximumAllocatedNodes = maximumAllocatedNodes;
            this.cutoffIterations = cutoffIterations;
            this.finalCutoff = finalCutoff;
            this.bestPoint = bestPoint;
        }

        public List<D2Path.Point> path() {
            return path;
        }

        public boolean reachedTarget() {
            return reachedTarget;
        }

        /** True when native search produced a terminal node, even if flush rejected its path. */
        public boolean nativePathFound() {
            return nativePathFound;
        }

        public int allocatedNodes() {
            return allocatedNodes;
        }

        public int maximumAllocatedNodes() {
            return maximumAllocatedNodes;
        }

        public int cutoffIterations() {
            return cutoffIterations;
        }

        public int finalCutoff() {
            return finalCutoff;
        }

        public D2Path.Point bestPoint() {
            return bestPoint;
        }
    }
}
