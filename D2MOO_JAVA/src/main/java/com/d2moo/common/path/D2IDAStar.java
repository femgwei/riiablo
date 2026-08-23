package com.d2moo.common.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Standalone port of D2Common {@code Path/IDAStar.cpp}.
 *
 * <p>The implementation intentionally stays independent of riiablo's runtime pathfinder. It
 * preserves the native node and visit limits, direction-dependent neighbor order, five-point
 * F-score increments, coordinate-distance cache, and path flush behavior.
 */
public final class D2IDAStar {
    public static final int MAX_ROOM_AREA = 50_000;
    public static final int MAX_NODES = 900;
    public static final int MAX_VISITS_PER_ITERATION = 10_000;
    public static final int F_SCORE_INCREMENT = 5;

    private static final int DEFAULT_F_SCORE_MARGIN = 100;

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

        Context context = new Context(bounds, collision);
        int cutoff = initialHeuristic;
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
                    current.point.x + offset[0], current.point.y + offset[1]);
            boolean evaluateNextNeighbor = true;
            boolean mayEvaluateNode = true;

            Integer cachedDistance = context.coordinateData.get(neighbor);
            if (cachedDistance != null && cachedDistance == 1) {
                mayEvaluateNode = false;
            } else if (cachedDistance == null
                    && context.collision.isBlocked(neighbor.x, neighbor.y)) {
                // One is lower than every legal move cost, so the cell is never tested again.
                context.coordinateData.put(neighbor, 1);
                mayEvaluateNode = false;
            }

            if (mayEvaluateNode) {
                int newDistance = saturatedAdd(
                        current.distanceFromStart,
                        D2Path.neighborCost(
                                current.point.x, current.point.y, neighbor.x, neighbor.y));
                cachedDistance = context.coordinateData.get(neighbor);

                if (cachedDistance == null
                        || Integer.compareUnsigned(newDistance, cachedDistance) < 0) {
                    context.coordinateData.put(neighbor, newDistance);
                    int heuristic = D2Path.aStarHeuristic(
                            target.x, target.y, neighbor.x, neighbor.y);
                    int fScore = saturatedAdd(heuristic, newDistance);
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
                        advanceNeighbor(current);

                        if (current.heuristic < targetDistanceThreshold) {
                            return current;
                        }
                        evaluateNextNeighbor = false;
                    }
                }
            }

            if (evaluateNextNeighbor) {
                current = backtrack(current, context.root);
                if (current == null) {
                    return null;
                }
            }
        }
        return current;
    }

    private static Node backtrack(Node node, Node root) {
        if (node.evaluationsCount < 4) {
            node.sequenceIndex++;
            advanceNeighbor(node);
        }

        node.evaluationsCount++;
        if (node.evaluationsCount == 5) {
            while (node != root) {
                node = node.parent;
                node.sequenceIndex++;
                advanceNeighbor(node);
                if (++node.evaluationsCount != 5) {
                    return node;
                }
            }
            return null;
        }
        return node;
    }

    private static void advanceNeighbor(Node node) {
        node.nextNeighborIndex = (node.nextNeighborIndex
                + NEIGHBOR_SEQUENCES[node.sequenceRow][node.sequenceIndex]) & 7;
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
        final List<Node> nodes = new ArrayList<>(MAX_NODES);
        Node root;

        Context(Bounds bounds, D2AStar.Collision collision) {
            this.bounds = bounds;
            this.collision = collision;
        }

        void resetNodes(D2Path.Point start, D2Path.Point target, int heuristic) {
            nodes.clear();
            // Each larger IDA* threshold must be able to revisit the preceding contour.
            coordinateData.clear();
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
