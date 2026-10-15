package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.d2moo.common.path.D2Path;
import com.riiablo.engine.server.NativeGroundPathProfile;
import com.riiablo.map.pfa.AStarPathFinder;
import com.riiablo.map.pfa.GraphPath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NativePathShadowTest {
  private static final int FLAGS = NativeGroundPathProfile.PLAYER.terrainFlags;

  @Test void disabledPropertyCreatesNoObserver() {
    String previous = System.getProperty("riiablo.path.shadow");
    try {
      System.clearProperty("riiablo.path.shadow");
      assertNull(NativePathShadow.fromSystemProperties());
    } finally { restore("riiablo.path.shadow", previous); }
  }

  @Test void explicitOptInCreatesObserverButReplayGraphsNeverAutoEnableIt() {
    String previous = System.getProperty("riiablo.path.shadow");
    try {
      System.setProperty("riiablo.path.shadow", "true");
      assertNotNull(NativePathShadow.fromSystemProperties());
      Map map = map(32);
      List<NativePathShadow.Result> rows = new ArrayList<>();
      map.setPathShadow(new NativePathShadow(1, 1, rows::add));
      assertTrue(map.findPath(new Vector2(10, 10), new Vector2(20, 10), FLAGS, 1, new GraphPath()));
      assertEquals(NativePathShadow.Status.COMPARED, only(rows).status);
    } finally { restore("riiablo.path.shadow", previous); }
  }

  @Test void optInDoesNotChangeInstalledPathMetricsOrRandomStream() {
    Map map = map(48);
    MapGraph graph = new MapGraph(map, null);
    AStarPathFinder finder = new AStarPathFinder(graph, true);
    GraphPath before = new GraphPath(), after = new GraphPath();
    Vector2 src = new Vector2(10, 10), dst = new Vector2(20, 17);
    assertTrue(graph.searchNodePath(finder, src, dst, FLAGS, 2, before));
    int visited = finder.metrics.visitedNodes, additions = finder.metrics.openListAdditions;
    List<NativePathShadow.Result> rows = new ArrayList<>();
    NativePathShadow shadow = new NativePathShadow(1, 2, rows::add);
    graph.setPathShadow(shadow);
    Random previous = MathUtils.random;
    try {
      MathUtils.random = new Random(731);
      assertTrue(graph.searchNodePath(finder, src, dst, FLAGS, 2, after));
      assertEquals(new Random(731).nextLong(), MathUtils.random.nextLong());
    } finally { MathUtils.random = previous; }
    assertEquals(before.toString(), after.toString());
    assertEquals(visited, finder.metrics.visitedNodes);
    assertEquals(additions, finder.metrics.openListAdditions);
    NativePathShadow.Result row = only(rows);
    assertEquals(NativePathShadow.Status.COMPARED, row.status);
    assertEquals(NativePathShadow.Outcome.EXACT, row.runtime.outcome(row.target));
    assertEquals(NativePathShadow.Outcome.EXACT, row.reference.outcome(row.target));
    assertEquals(NativePathShadow.Difference.TURNS, row.difference());
    assertTrue(row.reference.nodes <= 200);
    assertTrue(row.runtime.collisionQueries > 0);
    assertTrue(row.reference.collisionQueries > 0);
    assertEquals(0, shadow.errors());
    System.out.println("[NATIVE_PATH_SHADOW_FIXTURE] open " + row);
  }

  @Test void blockedCoordinateGoalRecordsContractDifferenceWithoutInstallingPartial() {
    Map map = map(32);
    map.zones.first().or(20, 20, DT1.Tile.FLAG_BLOCK_WALK);
    List<NativePathShadow.Result> rows = new ArrayList<>();
    map.setPathShadow(new NativePathShadow(1, 1, rows::add));
    GraphPath path = new GraphPath();
    assertFalse(map.findPath(new Vector2(5, 5), new Vector2(20, 20), FLAGS, 2, path));
    assertTrue(path.isEmpty());
    NativePathShadow.Result row = only(rows);
    assertEquals(NativePathShadow.Status.COMPARED, row.status);
    assertEquals(NativePathShadow.Outcome.FAILED, row.runtime.outcome(row.target));
    assertEquals(NativePathShadow.Outcome.PARTIAL, row.reference.outcome(row.target));
    assertTrue(row.differs());
    assertEquals(NativePathShadow.Difference.OUTCOME, row.difference());
    System.out.println("[NATIVE_PATH_SHADOW_FIXTURE] blocked-exact " + row);
  }

  @Test void partialEntryRetainsItsActualResultAndIsLabelledSeparately() {
    Map map = map(32);
    map.zones.first().or(20, 20, DT1.Tile.FLAG_BLOCK_WALK);
    List<NativePathShadow.Result> rows = new ArrayList<>();
    map.setPathShadow(new NativePathShadow(1, 1, rows::add));
    GraphPath path = new GraphPath();
    assertTrue(map.findPartialPath(map.zones.first(), new Vector2(5, 5), new Vector2(20, 20),
        FLAGS, 2, path, null, false));
    NativePathShadow.Result row = only(rows);
    assertEquals(NativePathShadow.Status.COMPARED, row.status);
    assertTrue(row.partial);
    assertEquals(NativePathShadow.Outcome.PARTIAL, row.runtime.outcome(row.target));
    assertEquals(NativePathShadow.Outcome.PARTIAL, row.reference.outcome(row.target));
    assertEquals(path.toString().replace(" ", ""), row.livePath.toString().replace(" ", ""));
    System.out.println("[NATIVE_PATH_SHADOW_FIXTURE] blocked-partial " + row);
  }

  @Test void snapshotReadsEachCellOnceAndDoesNotRetainLivePredicate() {
    boolean[] live = {false};
    AtomicInteger reads = new AtomicInteger();
    NativePathShadow.Snapshot snapshot = NativePathShadow.Snapshot.capture(
        point(10, 10), point(11, 11), (x, y) -> { reads.incrementAndGet(); return live[0]; });
    assertNotNull(snapshot);
    int captured = reads.get();
    assertEquals(snapshot.width * snapshot.height, captured);
    live[0] = true;
    for (int i = 0; i < 10; i++) assertFalse(snapshot.blocked(10, 10));
    assertEquals(captured, reads.get());
    assertTrue(snapshot.blocked(snapshot.x - 1, snapshot.y));
  }

  @Test void changedLiveInputIsInconclusiveNotAnAlgorithmDifference() {
    Map map = map(48);
    AtomicInteger reads = new AtomicInteger();
    MapGraph.Obstacle changes = (mover, target, x, y, size) -> reads.incrementAndGet() <= 2;
    List<NativePathShadow.Result> rows = new ArrayList<>();
    map.setPathShadow(new NativePathShadow(1, 1, rows::add));
    // First read admits the exact goal; start==goal needs no neighbours.
    GraphPath path = new GraphPath();
    assertTrue(map.findPath(new Vector2(20, 20), new Vector2(20, 20), FLAGS, 1, path, changes));
    NativePathShadow.Result row = only(rows);
    assertEquals(NativePathShadow.Status.LIVE_REPLAY_DIFFERENCE, row.status);
    assertEquals(NativePathShadow.Difference.INCONCLUSIVE, row.difference());
    assertFalse(row.differs());
    assertEquals(1, path.getCount());
  }

  @Test void captureAndSinkFailuresDoNotFailMovement() {
    Map map = map(48);
    NativePathShadow captureFailure = new NativePathShadow(1, 1, row -> fail("capture should fail"));
    map.setPathShadow(captureFailure);
    MapGraph.Obstacle obstacle = (mover, target, x, y, size) -> {
      if (x == 1 && y == 1) throw new IllegalStateException("diagnostic-only cell");
      return true;
    };
    GraphPath path = new GraphPath();
    assertTrue(map.findPath(new Vector2(10, 10), new Vector2(20, 10), FLAGS, 1, path, obstacle));
    assertEquals(1, captureFailure.errors());
    String expected = path.toString();
    NativePathShadow sinkFailure = new NativePathShadow(1, 1, row -> { throw new IllegalStateException(); });
    map.setPathShadow(sinkFailure);
    assertTrue(map.findPath(new Vector2(10, 10), new Vector2(20, 10), FLAGS, 1, path));
    assertEquals(expected, path.toString());
    assertEquals(1, sinkFailure.errors());
  }

  @Test void samplingCapsWorkAndDetachingRestoresOrdinaryQueries() {
    Map map = map(32);
    List<NativePathShadow.Result> rows = new ArrayList<>();
    NativePathShadow shadow = new NativePathShadow(2, 2, rows::add);
    map.setPathShadow(shadow);
    AtomicInteger calls = new AtomicInteger();
    MapGraph.Obstacle obstacle = (m, t, x, y, s) -> { calls.incrementAndGet(); return true; };
    for (int i = 0; i < 8; i++) {
      assertTrue(map.findPath(new Vector2(10, 10), new Vector2(10, 10), FLAGS, 1, new GraphPath(), obstacle));
    }
    assertEquals(2, rows.size());
    assertEquals(2, shadow.samples());
    int previous = calls.get();
    map.setPathShadow(null);
    assertTrue(map.findPath(new Vector2(10, 10), new Vector2(10, 10), FLAGS, 1, new GraphPath(), obstacle));
    assertEquals(previous + 1, calls.get(), "disabled mode performs no capture queries");
  }

  @Test void longRequestsSkipCaptureAndOverflowIsRejected() {
    AtomicInteger reads = new AtomicInteger();
    assertNull(NativePathShadow.Snapshot.capture(point(Integer.MIN_VALUE, 0), point(0, 0),
        (x, y) -> { reads.incrementAndGet(); return false; }));
    assertNull(NativePathShadow.Snapshot.capture(point(Integer.MAX_VALUE, 0), point(Integer.MAX_VALUE, 0),
        (x, y) -> { reads.incrementAndGet(); return false; }));
    assertEquals(0, reads.get());
    Map map = map(32);
    List<NativePathShadow.Result> rows = new ArrayList<>();
    map.setPathShadow(new NativePathShadow(1, 1, rows::add));
    assertFalse(map.findPath(new Vector2(5, 5), new Vector2(2000, 5), FLAGS, 1, new GraphPath()));
    NativePathShadow.Result row = only(rows);
    assertEquals(NativePathShadow.Status.SNAPSHOT_LIMIT, row.status);
    assertNull(row.runtime);
    assertFalse(row.differs());
    assertEquals(NativePathShadow.Difference.INCONCLUSIVE, row.difference());
  }

  @Test void sourceLevelFilterUsesOwningZoneAndDoesNotConsumeQuotaForOtherLevels() {
    Map map = map(32);
    Map.Zone original = map.zones.first(), detached = new Map.Zone();
    original.level = new com.riiablo.codec.excel.Levels.Entry();
    original.level.Id = 1;
    detached.map = map;
    detached.level = new com.riiablo.codec.excel.Levels.Entry();
    detached.level.Id = 63;
    detached.width = detached.height = 32;
    detached.flags = new byte[32 * 32];
    detached.addRoomEx(0, 0, 16, 32);
    detached.addRoomEx(16, 0, 16, 32);
    map.zones.add(detached);
    List<NativePathShadow.Result> rows = new ArrayList<>();
    NativePathShadow observer = new NativePathShadow(2, 2, 63, rows::add);
    map.setPathShadow(observer);
    GraphPath path = new GraphPath();
    Vector2 src = new Vector2(10, 10), dst = new Vector2(20, 10);
    for (int i = 0; i < 5; i++) assertTrue(map.findPath(src, dst, FLAGS, 1, path));
    assertEquals(0, observer.samples());
    for (int i = 0; i < 4; i++) {
      assertTrue(map.findPath(detached, src, dst, FLAGS, 1, path, (m, t, x, y, s) -> true,
          31, 37, false));
    }
    assertEquals(2, rows.size());
    NativePathShadow.Result row = rows.get(0);
    assertEquals(NativePathShadow.Status.COMPARED, row.status);
    assertEquals(63, row.context.sourceLevel);
    assertEquals(63, row.context.targetLevel);
    assertEquals(0, row.context.sourceRoom);
    assertEquals(1, row.context.targetRoom);
    assertTrue(row.context.dynamicObstacle);
    detached.level.Id = 64;
    detached.roomsEx.clear();
    assertEquals(63, row.context.sourceLevel, "metadata must not retain mutable level/room references");
    assertTrue(row.toString().contains("stage=MAP_SEARCH_RAW"));
    assertEquals(1, map.getZone(10, 10).levelId(), "scope restored after capture");
  }

  @Test void roomMetadataDoesNotInvalidateAnOuterRoomIterator() {
    Map map = map(32);
    Map.Zone zone = map.zones.first();
    zone.addRoomEx(0, 0, 16, 32);
    zone.addRoomEx(16, 0, 16, 32);
    List<NativePathShadow.Result> rows = new ArrayList<>();
    map.setPathShadow(new NativePathShadow(1, 2, rows::add));
    int visited = 0;
    for (Map.RoomEx room : zone.roomsEx) {
      assertTrue(map.findPath(new Vector2(10, 10), new Vector2(20, 10), FLAGS, 1, new GraphPath()));
      visited++;
    }
    assertEquals(2, visited);
    assertEquals(2, rows.size());
    assertEquals(0, rows.get(0).context.sourceRoom);
    assertEquals(1, rows.get(0).context.targetRoom);
  }

  @Test void successiveDynamicTargetSnapshotsStayDetachedWithoutInstallingNativePath() {
    Map map = map(48);
    com.riiablo.engine.server.UnitCollisionGrid grid = new com.riiablo.engine.server.UnitCollisionGrid();
    grid.put(31, 10, 10, 2);
    grid.put(37, 20, 10, 2);
    List<NativePathShadow.Result> rows = new ArrayList<>();
    map.setPathShadow(new NativePathShadow(1, 2, rows::add));
    MapGraph.Obstacle obstacle = (m, t, x, y, size) -> grid.isFree(31, 37, x, y, size);
    GraphPath path = new GraphPath();
    assertTrue(map.findPath(new Vector2(10, 10), new Vector2(20, 10), FLAGS, 2,
        path, obstacle, 31, 37, false));
    String first = rows.get(0).toString();
    grid.put(37, 22, 14, 2);
    grid.put(38, 16, 10, 2);
    assertTrue(map.findPath(new Vector2(10, 10), new Vector2(22, 14), FLAGS, 2,
        path, obstacle, 31, 37, false));
    assertEquals(first, rows.get(0).toString());
    assertEquals(point(20, 10), rows.get(0).target);
    assertEquals(point(22, 14), rows.get(1).target);
    assertEquals(NativePathShadow.Status.COMPARED, rows.get(1).status);
    assertTrue(rows.get(1).context.dynamicObstacle);
    assertEquals(path.getCount(), rows.get(1).livePath.size());
    System.out.println("[NATIVE_PATH_SHADOW_FIXTURE] target-moved " + rows.get(1));
  }

  @Test void partialResultsWithDifferentEndpointsAreNotLabelledOnlyAsTurns() {
    Map map = map(110);
    map.zones.first().or(95, 95, DT1.Tile.FLAG_BLOCK_WALK);
    List<NativePathShadow.Result> rows = new ArrayList<>();
    map.setPathShadow(new NativePathShadow(1, 1, rows::add));
    assertTrue(map.findPartialPath(map.zones.first(), new Vector2(5, 5), new Vector2(95, 95),
        FLAGS, 1, new GraphPath(), null, false));
    NativePathShadow.Result row = only(rows);
    assertEquals(NativePathShadow.Status.COMPARED, row.status);
    assertEquals(NativePathShadow.Outcome.PARTIAL, row.runtime.outcome(row.target));
    assertEquals(NativePathShadow.Outcome.PARTIAL, row.reference.outcome(row.target));
    assertEquals(NativePathShadow.Difference.ENDPOINT, row.difference());
    assertTrue(row.differs());
    System.out.println("[NATIVE_PATH_SHADOW_FIXTURE] partial-endpoints " + row);
  }

  @Test void aRouteLeavingTheSnapshotIsNotReportedAsAParityFailure() {
    Map map = map(64);
    Map.Zone zone = map.zones.first();
    Arrays.fill(zone.flags, (byte) DT1.Tile.FLAG_BLOCK_WALK);
    for (int y = 20; y <= 50; y++) {
      zone.flags[y * zone.width + 20] = zone.flags[y * zone.width + 22] = 0;
    }
    zone.flags[50 * zone.width + 21] = 0;
    List<NativePathShadow.Result> rows = new ArrayList<>();
    map.setPathShadow(new NativePathShadow(1, 1, rows::add));
    GraphPath path = new GraphPath();
    assertTrue(map.findPath(new Vector2(20, 20), new Vector2(22, 20), FLAGS, 1, path));
    NativePathShadow.Result row = only(rows);
    assertEquals(NativePathShadow.Status.OUTSIDE_SNAPSHOT, row.status);
    assertFalse(row.differs());
    assertTrue(path.getCount() > 50, "authoritative path can leave the diagnostic bounds");
  }

  @Test void rowsAreDetachedFromPooledNodesAndCannotBeMutated() {
    Map map = map(32);
    List<NativePathShadow.Result> rows = new ArrayList<>();
    map.setPathShadow(new NativePathShadow(1, 1, rows::add));
    GraphPath path = new GraphPath();
    assertTrue(map.findPath(new Vector2(10, 10), new Vector2(20, 10), FLAGS, 1, path));
    NativePathShadow.Result row = only(rows);
    String captured = row.livePath.toString();
    path.get(0).set(999, 999);
    path.clear();
    assertEquals(captured, row.livePath.toString());
    assertThrows(UnsupportedOperationException.class, () -> row.livePath.clear());
    assertThrows(UnsupportedOperationException.class, () -> row.runtime.path.clear());
    assertThrows(UnsupportedOperationException.class, () -> row.reference.turns.clear());
  }

  @Test void doorScopeAndOrdinaryObjectOverlapAreCapturedWithoutRelaxingCollision() {
    Map map = map(32);
    Map.Zone zone = map.zones.first();
    for (int y = 0; y < 32; y++) zone.or(16, y, DT1.Tile.FLAG_BLOCK_WALK);
    zone.flags[16 * 32 + 16] = 0;
    zone.objectBlockWalkRefs = new short[32 * 32];
    zone.objectDoorRefs = new short[32 * 32];
    zone.objectBlockWalkRefs[16 * 32 + 16] = zone.objectDoorRefs[16 * 32 + 16] = 1;
    List<NativePathShadow.Result> rows = new ArrayList<>();
    map.setPathShadow(new NativePathShadow(1, 3, rows::add));
    GraphPath path = new GraphPath();
    assertTrue(map.findPath(new Vector2(10, 16), new Vector2(22, 16), FLAGS, 1,
        path, null, 31, -1, true));
    NativePathShadow.Result row = only(rows);
    assertEquals(NativePathShadow.Status.COMPARED, row.status);
    assertEquals(NativePathShadow.Outcome.EXACT, row.reference.outcome(row.target));
    assertTrue(row.canOpenDoors);
    assertTrue(map.isBlockedForPath(16, 16, FLAGS, 1, false), "door filter restored after observation");
    zone.objectBlockWalkRefs[16 * 32 + 16]++;
    assertFalse(map.findPath(new Vector2(10, 16), new Vector2(22, 16), FLAGS, 1,
        path, null, 31, -1, true));
    assertEquals(NativePathShadow.Status.COMPARED, rows.get(1).status);
    assertEquals(NativePathShadow.Outcome.PARTIAL, rows.get(1).reference.outcome(rows.get(1).target));
    System.out.println("[NATIVE_PATH_SHADOW_FIXTURE] door " + row);
    System.out.println("[NATIVE_PATH_SHADOW_FIXTURE] door-object-overlap " + rows.get(1));
  }

  @Test void dynamicGridOwnerAndTargetIgnorePredicateIsPreserved() {
    Map map = map(32);
    com.riiablo.engine.server.UnitCollisionGrid grid = new com.riiablo.engine.server.UnitCollisionGrid();
    grid.put(31, 10, 10, 2);
    grid.put(37, 20, 10, 2);
    List<NativePathShadow.Result> rows = new ArrayList<>();
    map.setPathShadow(new NativePathShadow(1, 2, rows::add));
    MapGraph.Obstacle ignoreTarget = (m, t, x, y, size) -> grid.isFree(31, 37, x, y, size);
    GraphPath path = new GraphPath();
    assertTrue(map.findPath(new Vector2(10, 10), new Vector2(20, 10), FLAGS, 2,
        path, ignoreTarget, 31, 37, false));
    NativePathShadow.Result row = only(rows);
    assertEquals(NativePathShadow.Status.COMPARED, row.status);
    assertEquals(31, row.moverId);
    assertEquals(37, row.targetId);
    assertEquals(NativePathShadow.Outcome.EXACT, row.reference.outcome(row.target));
    assertFalse(map.findPath(new Vector2(10, 10), new Vector2(20, 10), FLAGS, 2,
        path, (m, t, x, y, size) -> grid.isFree(31, -1, x, y, size), 31, 37, false));
    assertEquals(NativePathShadow.Status.COMPARED, rows.get(1).status);
    assertEquals(NativePathShadow.Outcome.PARTIAL, rows.get(1).reference.outcome(rows.get(1).target));
  }

  @Test void candidateAndGoalIdentityArgumentsKeepTheirExistingContracts() {
    Map map = map(32);
    List<NativePathShadow.Result> rows = new ArrayList<>();
    map.setPathShadow(new NativePathShadow(1, 1, rows::add));
    // Goal entry accepts a specific mover. Candidate checks still pass -1/-1.
    MapGraph.Obstacle obstacle = (m, t, x, y, s) -> x != 20 || y != 10 || m == 31;
    assertFalse(map.findPath(new Vector2(10, 10), new Vector2(20, 10), FLAGS, 1,
        new GraphPath(), obstacle, 31, 37, false));
    assertEquals(NativePathShadow.Status.COMPARED, only(rows).status);
    assertEquals(NativePathShadow.Outcome.PARTIAL, only(rows).reference.outcome(only(rows).target));
  }

  @Test void aDensePresenceFixtureKeepsTheAuthoritativeRoute() {
    Map map = map(48);
    com.riiablo.engine.server.UnitCollisionGrid grid = new com.riiablo.engine.server.UnitCollisionGrid();
    int id = 1;
    for (int y = 9; y <= 24; y += 3) for (int x = 9; x <= 24; x += 3) grid.put(id++, x, y, 2);
    MapGraph.Obstacle obstacle = (m, t, x, y, size) -> grid.isFree(1000, -1, x, y, size);
    Vector2 start = new Vector2(5, 5), target = new Vector2(30, 30);
    GraphPath before = new GraphPath(), after = new GraphPath();
    boolean expected = map.findPath(start, target, FLAGS, 2, before, obstacle);
    List<NativePathShadow.Result> rows = new ArrayList<>();
    map.setPathShadow(new NativePathShadow(1, 1, rows::add));
    assertEquals(expected, map.findPath(start, target, FLAGS, 2, after, obstacle));
    assertEquals(before.toString(), after.toString());
    NativePathShadow.Result row = only(rows);
    assertEquals(NativePathShadow.Status.COMPARED, row.status);
    assertTrue(row.reference.nodes <= 200);
    System.out.println("[NATIVE_PATH_SHADOW_FIXTURE] dense-36 " + row);
  }

  @Test void alreadyAtTargetDoesNotMistakeEmptyNativeFlushForAnInstalledPath() {
    Map map = map(32);
    List<NativePathShadow.Result> rows = new ArrayList<>();
    map.setPathShadow(new NativePathShadow(1, 1, rows::add));
    assertTrue(map.findPath(new Vector2(10, 10), new Vector2(10, 10), FLAGS, 1, new GraphPath()));
    NativePathShadow.Result row = only(rows);
    assertEquals(NativePathShadow.Status.COMPARED, row.status);
    assertEquals(NativePathShadow.Outcome.EXACT, row.runtime.outcome(row.target));
    assertEquals(NativePathShadow.Outcome.FAILED, row.reference.outcome(row.target));
    assertTrue(row.reference.path.isEmpty());
    assertTrue(row.differs(), "output contract difference, not a gameplay movement bug");
  }

  @Test void flyingTerrainMaskIsCapturedInsteadOfAssumingGroundWalk() {
    Map map = map(32);
    for (int y = 0; y < 32; y++) map.zones.first().or(16, y, DT1.Tile.FLAG_BLOCK_WALK);
    List<NativePathShadow.Result> rows = new ArrayList<>();
    map.setPathShadow(new NativePathShadow(1, 1, rows::add));
    int flying = NativeGroundPathProfile.FLYING.terrainFlags;
    assertTrue(map.findPath(new Vector2(10, 10), new Vector2(20, 10), flying, 2, new GraphPath()));
    NativePathShadow.Result row = only(rows);
    assertEquals(NativePathShadow.Status.COMPARED, row.status);
    assertEquals(flying, row.flags);
    assertEquals(NativePathShadow.Outcome.EXACT, row.reference.outcome(row.target));
    assertFalse(row.differs());
    assertEquals(NativePathShadow.Difference.NONE, row.difference());
  }

  @Test void scopedOverlappingLevelCannotBorrowTerrainFromTheFirstLevel() {
    Map map = map(32);
    Map.Zone original = map.zones.first(), detached = new Map.Zone();
    detached.map = map;
    detached.width = detached.height = 32;
    detached.flags = new byte[32 * 32];
    Arrays.fill(original.flags, (byte) DT1.Tile.FLAG_BLOCK_WALK);
    map.zones.add(detached);
    List<NativePathShadow.Result> rows = new ArrayList<>();
    map.setPathShadow(new NativePathShadow(1, 1, rows::add));
    assertTrue(map.findPath(detached, new Vector2(10, 10), new Vector2(20, 10), FLAGS, 2,
        new GraphPath(), null, 31, -1, false));
    NativePathShadow.Result row = only(rows);
    assertEquals(NativePathShadow.Status.COMPARED, row.status);
    assertEquals(NativePathShadow.Outcome.EXACT, row.reference.outcome(row.target));
    assertTrue(map.isBlockedForPath(15, 10, FLAGS, 2, false), "owning-zone scope restored");
  }

  @Test void configurationRejectsUnboundedOrMissingConsumers() {
    assertThrows(IllegalArgumentException.class, () -> new NativePathShadow(0, 1, row -> {}));
    assertThrows(IllegalArgumentException.class, () -> new NativePathShadow(1, 0, row -> {}));
    assertThrows(IllegalArgumentException.class, () -> new NativePathShadow(1, 129, row -> {}));
    assertThrows(IllegalArgumentException.class, () -> new NativePathShadow(1, 1, null));
    assertThrows(IllegalArgumentException.class, () -> new NativePathShadow(1, 1, -2, row -> {}));
  }

  @Test void customSolversAndNullGoalsAreNotMislabelledAsRuntimeAStar() {
    Map map = map(32);
    List<NativePathShadow.Result> rows = new ArrayList<>();
    MapGraph graph = new MapGraph(map, new NativePathShadow(1, 2, rows::add));
    com.riiablo.map.pfa.PathFinder custom = (src, dst, flags, size, path) -> false;
    assertFalse(graph.searchNodePath(custom, new Vector2(10, 10), new Vector2(12, 10),
        FLAGS, 1, new GraphPath()));
    assertFalse(graph.searchNodePath(new AStarPathFinder(graph), new Vector2(10, 10), null,
        FLAGS, 1, new GraphPath()));
    assertTrue(rows.isEmpty());
  }

  private static NativePathShadow.Result only(List<NativePathShadow.Result> rows) {
    assertEquals(1, rows.size());
    return rows.get(0);
  }

  private static D2Path.Point point(int x, int y) { return new D2Path.Point(x, y); }

  private static Map map(int side) {
    Map map = new Map(0, 0);
    map.setPathShadow(null);
    Map.Zone zone = new Map.Zone();
    zone.map = map;
    zone.width = zone.height = side;
    zone.tilesX = zone.tilesY = (side + 4) / 5;
    zone.flags = new byte[side * side];
    map.zones.add(zone);
    return map;
  }

  private static void restore(String property, String value) {
    if (value == null) System.clearProperty(property); else System.setProperty(property, value);
  }
}
