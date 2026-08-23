package com.d2moo.common.path;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Standalone port of the bounded D2Common A* search.
 *
 * <p>This class has no dependency on riiablo's runtime map or pathfinder. Callers provide a
 * collision predicate and can compare its result before choosing to integrate it.
 */
public final class D2AStar {
    public static final int MAX_NODES = 200;

    private static final int[][] TARGET_APPROACH_OFFSETS = {
            {-2, -2}, {-2, 2}, {2, -2}, {2, 2},
            {-2, 0}, {0, -2}, {2, 0}, {0, 2}
    };

    private D2AStar() {}

    @FunctionalInterface
    public interface Collision {
        boolean isBlocked(int x, int y);
    }

    public static Result findPath(
            D2Path.Point start, D2Path.Point target, Collision collision) {
        return findPath(start, target, collision, false);
    }

    /**
     * @param targetIsUnit when true, applies the native eight-point target clearance test
     */
    public static Result findPath(
            D2Path.Point start,
            D2Path.Point target,
            Collision collision,
            boolean targetIsUnit) {
        if (start == null || target == null || collision == null) {
            throw new IllegalArgumentException("start, target and collision are required");
        }
        if (targetIsUnit && !targetHasApproach(target, collision)) {
            return Result.empty(start);
        }

        Context context = new Context();
        Node startNode = context.allocate(start);
        startNode.distanceFromStart = 0;
        startNode.heuristic = D2Path.aStarHeuristic(target.x, target.y, start.x, start.y);
        startNode.fScore = startNode.heuristic;
        context.makeCandidate(startNode);

        Node best = null;
        boolean reachedTarget = false;
        Node current;
        while ((current = context.popBest()) != null) {
            if (best == null
                    || current.heuristic < best.heuristic
                    || (current.heuristic == best.heuristic
                        && current.distanceFromStart > best.distanceFromStart + 5)) {
                best = current;
            }

            if (current.heuristic == 0) {
                reachedTarget = true;
                break;
            }
            if (!exploreChildren(context, current, target, collision)) {
                break;
            }
        }

        if (best == null) {
            return Result.empty(start);
        }
        List<D2Path.Point> fullPath = buildFullPath(best);
        List<D2Path.Point> compressed = D2Path.compressAStarPath(fullPath);
        return new Result(
                compressed,
                reachedTarget,
                context.nodes.size(),
                best.point);
    }

    private static boolean targetHasApproach(D2Path.Point target, Collision collision) {
        for (int[] offset : TARGET_APPROACH_OFFSETS) {
            if (!collision.isBlocked(target.x + offset[0], target.y + offset[1])) {
                return true;
            }
        }
        return false;
    }

    private static boolean exploreChildren(
            Context context, Node current, D2Path.Point target, Collision collision) {
        for (int i = 0; i < D2Path.neighborCount(); i++) {
            D2Path.Point point = new D2Path.Point(
                    current.point.x + D2Path.neighborOffsetX(i),
                    current.point.y + D2Path.neighborOffsetY(i));
            if (!collision.isBlocked(point.x, point.y)
                    && !evaluateNeighbor(context, current, point, target)) {
                return false;
            }
        }
        return true;
    }

    private static boolean evaluateNeighbor(
            Context context, Node current, D2Path.Point point, D2Path.Point target) {
        int distance = current.distanceFromStart
                + D2Path.neighborCost(current.point.x, current.point.y, point.x, point.y);

        Node node = context.pending.get(point);
        if (node != null) {
            current.addChild(node);
            if (distance < node.distanceFromStart) {
                node.parent = current;
                node.distanceFromStart = distance;
                node.fScore = distance + node.heuristic;
            }
            return true;
        }

        node = context.visited.get(point);
        if (node != null) {
            current.addChild(node);
            if (distance < node.distanceFromStart) {
                node.parent = current;
                node.distanceFromStart = distance;
                node.fScore = distance + node.heuristic;
                context.propagateScore(node);
            }
            return true;
        }

        node = context.allocate(point);
        if (node == null) {
            return false;
        }
        current.addChild(node);
        node.parent = current;
        node.distanceFromStart = distance;
        node.heuristic = D2Path.aStarHeuristic(point.x, point.y, target.x, target.y);
        node.fScore = distance + node.heuristic;
        context.makeCandidate(node);
        return true;
    }

    private static List<D2Path.Point> buildFullPath(Node end) {
        List<D2Path.Point> reversed = new ArrayList<>();
        for (Node node = end; node != null; node = node.parent) {
            reversed.add(node.point);
        }
        Collections.reverse(reversed);
        return reversed;
    }

    private static final class Context {
        final List<Node> nodes = new ArrayList<>(MAX_NODES);
        final List<Node> sortedPending = new ArrayList<>();
        final Map<D2Path.Point, Node> pending = new HashMap<>();
        final Map<D2Path.Point, Node> visited = new HashMap<>();

        Node allocate(D2Path.Point point) {
            if (nodes.size() >= MAX_NODES) return null;
            Node node = new Node(point);
            nodes.add(node);
            return node;
        }

        void makeCandidate(Node node) {
            pending.put(node.point, node);
            int index = 0;
            while (index < sortedPending.size()
                    && sortedPending.get(index).fScore < node.fScore) {
                index++;
            }
            sortedPending.add(index, node);
        }

        Node popBest() {
            if (sortedPending.isEmpty()) return null;
            Node node = sortedPending.remove(0);
            pending.remove(node.point);
            visited.put(node.point, node);
            return node;
        }

        void propagateScore(Node start) {
            Deque<Node> stack = new ArrayDeque<>();
            stack.push(start);
            while (!stack.isEmpty()) {
                Node current = stack.pop();
                for (Node child : current.children) {
                    int distance = current.distanceFromStart
                            + D2Path.neighborCost(
                                current.point.x, current.point.y, child.point.x, child.point.y);
                    if (distance < child.distanceFromStart) {
                        child.parent = current;
                        child.distanceFromStart = distance;
                        child.fScore = distance + child.heuristic;
                        stack.push(child);
                    }
                }
            }
        }
    }

    private static final class Node {
        final D2Path.Point point;
        final List<Node> children = new ArrayList<>(8);
        Node parent;
        int fScore;
        int heuristic;
        int distanceFromStart;

        Node(D2Path.Point point) {
            this.point = point;
        }

        void addChild(Node child) {
            if (children.size() < 8) children.add(child);
        }
    }

    public static final class Result {
        private final List<D2Path.Point> path;
        private final boolean reachedTarget;
        private final int allocatedNodes;
        private final D2Path.Point bestPoint;

        Result(
                List<D2Path.Point> path,
                boolean reachedTarget,
                int allocatedNodes,
                D2Path.Point bestPoint) {
            this.path = Collections.unmodifiableList(new ArrayList<>(path));
            this.reachedTarget = reachedTarget;
            this.allocatedNodes = allocatedNodes;
            this.bestPoint = bestPoint;
        }

        static Result empty(D2Path.Point start) {
            return new Result(Collections.<D2Path.Point>emptyList(), false, 0, start);
        }

        public List<D2Path.Point> path() {
            return path;
        }

        public boolean reachedTarget() {
            return reachedTarget;
        }

        public int allocatedNodes() {
            return allocatedNodes;
        }

        public D2Path.Point bestPoint() {
            return bestPoint;
        }
    }
}
