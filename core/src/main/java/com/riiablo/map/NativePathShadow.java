package com.riiablo.map;

import com.badlogic.gdx.ai.pfa.GraphPath;
import com.badlogic.gdx.math.Vector2;
import com.d2moo.common.path.D2AStar;
import com.d2moo.common.path.D2Path;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.pfa.AStarPathFinder;
import com.riiablo.map.pfa.Point2;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

/** Opt-in, simulation-thread-only diagnostics. Never installs the reference path.
 * Both replays use one frozen effective-collision snapshot, not native pattern adaptation.
 * The native reference is coordinate-target A* only, even when targetId is present.
 */
public final class NativePathShadow {
  private static final Logger log = LogManager.getLogger(NativePathShadow.class);
  static final int MARGIN = 16;
  static final int MAX_SIDE = 128;
  private final int every, maxSamples;
  private final Consumer<Result> sink;
  private long requests;
  private int samples, errors;

  public NativePathShadow(int every, int maxSamples, Consumer<Result> sink) {
    if (every < 1 || maxSamples < 1 || maxSamples > 128 || sink == null) {
      throw new IllegalArgumentException("every >= 1, samples 1..128 and sink required");
    }
    this.every = every;
    this.maxSamples = maxSamples;
    this.sink = sink;
  }

  static NativePathShadow fromSystemProperties() {
    if (!Boolean.getBoolean("riiablo.path.shadow")) return null;
    // WARN is intentional: explicit opt-in must work with the default logger threshold.
    return new NativePathShadow(Math.max(1, Integer.getInteger("riiablo.path.shadow.every", 128)),
        Math.max(1, Math.min(128, Integer.getInteger("riiablo.path.shadow.maxSamples", 16))),
        result -> log.warn("[NATIVE_PATH_SHADOW] {}", result));
  }

  public int samples() { return samples; }
  public int errors() { return errors; }

  void observe(MapGraph graph, Vector2 src, Vector2 dst, int flags, int size,
      MapGraph.Obstacle obstacle, int moverId, int targetId, boolean canOpenDoors,
      boolean partial, boolean liveSuccess, GraphPath<Point2> livePath) {
    if (samples >= maxSamples || requests++ % every != 0) return;
    int sample = ++samples;
    try {
      D2Path.Point start = new D2Path.Point(Map.round(src.x), Map.round(src.y));
      D2Path.Point target = new D2Path.Point(Map.round(dst.x), Map.round(dst.y));
      List<D2Path.Point> live = copy(livePath);
      long captureStart = System.nanoTime();
      Snapshot snapshot = Snapshot.capture(start, target,
          // AStarPathFinder passes -1/-1 to its candidate checks. Preserve that
          // separately from the public entry's mover/target-aware goal check.
          (x, y) -> !graph.isWalkable(x, y, flags, size, obstacle, -1, -1));
      if (snapshot == null) {
        sink.accept(new Result(sample, flags, size, moverId, targetId, canOpenDoors, partial,
            start, target, Status.SNAPSHOT_LIMIT, liveSuccess, live, null, null, 0));
        return;
      }
      boolean goalFree = partial || graph.isWalkable(target.x, target.y, flags, size,
          obstacle, moverId, targetId);
      long captureNanos = System.nanoTime() - captureStart;
      Run runtime = replay(snapshot, start, target, partial, goalFree);
      long nativeStart = System.nanoTime();
      Query nativeQuery = new Query(snapshot);
      D2AStar.Result nativeResult = D2AStar.findPath(start, target, nativeQuery::blocked, false);
      // Native flush can reject a path (including an overfull turn buffer).
      // Reaching the search target alone does not mean usable output exists.
      Run reference = new Run(!nativeResult.path().isEmpty(), nativeResult.path(), nativeResult.path(),
          nativeQuery.calls, nativeQuery.outside, nativeResult.allocatedNodes(),
          System.nanoTime() - nativeStart);
      Status status = runtime.outside || reference.outside ? Status.OUTSIDE_SNAPSHOT
          : liveSuccess != runtime.success || !live.equals(runtime.path)
              ? Status.LIVE_REPLAY_DIFFERENCE : Status.COMPARED;
      sink.accept(new Result(sample, flags, size, moverId, targetId, canOpenDoors, partial,
          start, target, status, liveSuccess, live, runtime, reference, captureNanos));
    } catch (RuntimeException diagnosticFailure) {
      // A failed capture/replay/sink must not turn a successful walk into a failure.
      // Do not swallow VM errors. Bounds above keep normal diagnostic work finite.
      errors++;
      try {
        log.warn("[NATIVE_PATH_SHADOW] sample={} status=DIAGNOSTIC_ERROR type={}",
            sample, diagnosticFailure.getClass().getSimpleName());
      } catch (RuntimeException loggingFailure) { /* Diagnostics remain non-authoritative. */ }
    }
  }

  private static Run replay(Snapshot snapshot, D2Path.Point start, D2Path.Point target,
      boolean partial, boolean goalFree) {
    long begin = System.nanoTime();
    Query query = new Query(snapshot);
    ReplayGraph graph = new ReplayGraph(query);
    AStarPathFinder finder = new AStarPathFinder(graph, true);
    com.riiablo.map.pfa.GraphPath path = new com.riiablo.map.pfa.GraphPath();
    boolean success = false;
    if (goalFree) {
      Point2 source = graph.getOrCreate(start.x, start.y), goal = graph.getOrCreate(target.x, target.y);
      success = partial ? finder.searchPartial(source, goal, 0, 0, path, null)
          : finder.search(source, goal, 0, 0, path, null);
    }
    List<D2Path.Point> raw = copy(path);
    return new Run(success, raw, D2Path.compressAStarPath(raw), query.calls, query.outside,
        graph.points.size(), System.nanoTime() - begin);
  }

  private static List<D2Path.Point> copy(GraphPath<Point2> path) {
    List<D2Path.Point> result = new ArrayList<>(path.getCount());
    for (Point2 point : path) result.add(new D2Path.Point(point.x, point.y));
    return Collections.unmodifiableList(result);
  }

  private static long key(int x, int y) { return ((long) x << 32) ^ (y & 0xffffffffL); }

  /** No live map, entity, predicate or mutable array escapes capture. */
  static final class Snapshot {
    final int x, y, width, height;
    private final boolean[] blocked;

    private Snapshot(int x, int y, int width, int height, boolean[] blocked) {
      this.x = x; this.y = y; this.width = width; this.height = height;
      this.blocked = blocked;
    }

    static Snapshot capture(D2Path.Point start, D2Path.Point target, D2AStar.Collision source) {
      long left = (long) Math.min(start.x, target.x) - MARGIN;
      long bottom = (long) Math.min(start.y, target.y) - MARGIN;
      long width = Math.abs((long) target.x - start.x) + 2 * MARGIN + 1;
      long height = Math.abs((long) target.y - start.y) + 2 * MARGIN + 1;
      if (width > MAX_SIDE || height > MAX_SIDE || left < Integer.MIN_VALUE
          || bottom < Integer.MIN_VALUE || left + width - 1 > Integer.MAX_VALUE
          || bottom + height - 1 > Integer.MAX_VALUE) return null;
      boolean[] cells = new boolean[(int) (width * height)];
      for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
        cells[y * (int) width + x] = source.isBlocked((int) left + x, (int) bottom + y);
      }
      return new Snapshot((int) left, (int) bottom, (int) width, (int) height, cells);
    }

    boolean contains(int cellX, int cellY) {
      long dx = (long) cellX - x, dy = (long) cellY - y;
      return dx >= 0 && dx < width && dy >= 0 && dy < height;
    }

    boolean blocked(int cellX, int cellY) {
      return !contains(cellX, cellY) || blocked[(cellY - y) * width + cellX - x];
    }
  }

  private static final class Query {
    final Snapshot snapshot;
    int calls;
    boolean outside;
    Query(Snapshot snapshot) { this.snapshot = snapshot; }
    boolean blocked(int x, int y) {
      calls++;
      if (!snapshot.contains(x, y)) outside = true;
      return snapshot.blocked(x, y);
    }
  }

  /** Same production solver/neighbour implementation, separate node identity and heap.
   * No Point2.updateClearance or live-map read is permitted during replay. */
  private static final class ReplayGraph extends MapGraph {
    final java.util.Map<Long, Point2> points = new HashMap<>();
    final Query query;
    ReplayGraph(Query query) { super(null, null); this.query = query; }
    @Override public Point2 getOrCreate(int x, int y) {
      long key = key(x, y);
      Point2 point = points.get(key);
      if (point == null) { point = new Point2(x, y); points.put(key, point); }
      return point;
    }
    @Override public boolean isWalkable(int x, int y, int flags, int size,
        Obstacle obstacle, int moverId, int targetId) { return !query.blocked(x, y); }
  }

  public enum Status { COMPARED, SNAPSHOT_LIMIT, OUTSIDE_SNAPSHOT, LIVE_REPLAY_DIFFERENCE }
  public enum Outcome { EXACT, PARTIAL, FAILED }

  public static final class Run {
    public final boolean success, outside;
    public final List<D2Path.Point> path, turns;
    public final int collisionQueries, nodes;
    public final long nanos;
    private Run(boolean success, List<D2Path.Point> path, List<D2Path.Point> turns,
        int collisionQueries, boolean outside, int nodes, long nanos) {
      this.success = success;
      this.path = Collections.unmodifiableList(new ArrayList<>(path));
      this.turns = Collections.unmodifiableList(new ArrayList<>(turns));
      this.collisionQueries = collisionQueries; this.outside = outside;
      this.nodes = nodes; this.nanos = nanos;
    }
    public Outcome outcome(D2Path.Point target) {
      if (!success || path.isEmpty()) return Outcome.FAILED;
      return path.get(path.size() - 1).equals(target) ? Outcome.EXACT : Outcome.PARTIAL;
    }
  }

  /** Detached values only: later graph reuse or smoothing cannot alter a sample. */
  public static final class Result {
    public final int sample, flags, size, moverId, targetId;
    public final boolean canOpenDoors, partial, liveSuccess;
    public final D2Path.Point start, target;
    public final Status status;
    public final List<D2Path.Point> livePath;
    public final Run runtime, reference;
    public final long captureNanos;
    private Result(int sample, int flags, int size, int moverId, int targetId,
        boolean canOpenDoors, boolean partial, D2Path.Point start, D2Path.Point target,
        Status status, boolean liveSuccess, List<D2Path.Point> livePath,
        Run runtime, Run reference, long captureNanos) {
      this.sample = sample; this.flags = flags; this.size = size;
      this.moverId = moverId; this.targetId = targetId; this.canOpenDoors = canOpenDoors;
      this.partial = partial; this.start = start; this.target = target; this.status = status;
      this.liveSuccess = liveSuccess; this.livePath = livePath;
      this.runtime = runtime; this.reference = reference; this.captureNanos = captureNanos;
    }
    public boolean differs() {
      return status == Status.COMPARED && (runtime.outcome(target) != reference.outcome(target)
          || !runtime.turns.equals(reference.turns));
    }
    @Override public String toString() {
      String prefix = "sample=" + sample + " status=" + status + " start=" + start + " target=" + target
          + " flags=" + flags + " size=" + size + " mover=" + moverId + " targetId=" + targetId
          + " openDoors=" + canOpenDoors + " partial=" + partial + " targetMode=COORDINATE"
          + " liveSuccess=" + liveSuccess + " livePath=" + livePath;
      if (runtime == null) return prefix;
      return prefix + " differs=" + differs() + " runtime=" + runtime.outcome(target)
          + " reference=" + reference.outcome(target) + " runtimeTurns=" + runtime.turns
          + " referenceTurns=" + reference.turns + " runtimeQueries=" + runtime.collisionQueries
          + " referenceQueries=" + reference.collisionQueries + " runtimeNodes=" + runtime.nodes
          + " referenceNodes=" + reference.nodes + " captureNs=" + captureNanos
          + " runtimeNs=" + runtime.nanos + " referenceNs=" + reference.nanos;
    }
  }
}
