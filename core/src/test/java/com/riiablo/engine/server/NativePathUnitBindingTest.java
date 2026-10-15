package com.riiablo.engine.server;

import com.artemis.World;
import com.d2moo.common.collision.D2CommonCollision;
import com.d2moo.common.drlg.D2ActiveRoom;
import com.d2moo.common.path.*;
import com.riiablo.engine.server.component.NativePathIdentity;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativePathUnitBindingTest {
  private static NativePathUnitBinding.FootprintObservation ecsFootprints(Fixture f, D2ActiveRoom room) {
    f.world.getMapper(com.riiablo.engine.server.component.Class.class).create(f.owner).type =
        com.riiablo.engine.server.component.Class.Type.PLR;
    f.world.getMapper(com.riiablo.engine.server.component.CofReference.class).create(f.owner).mode = 1;
    f.world.getMapper(com.riiablo.engine.server.component.Class.class).create(f.target).type =
        com.riiablo.engine.server.component.Class.Type.OBJ;
    com.riiablo.engine.server.component.Object object =
        f.world.getMapper(com.riiablo.engine.server.component.Object.class).create(f.target);
    object.base = new com.riiablo.codec.excel.Objects.Entry();
    object.base.SizeX = 2; object.base.SizeY = 3;
    object.base.IsDoor = true; object.base.Orientation = 1;
    object.base.HasCollision = new boolean[8]; object.base.HasCollision[1] = true; object.mode = 1;
    NativePathUnitBinding binding = new NativePathUnitBinding(f.world, f.owner, f.target,
        (w, id) -> { fail("coordinate reader must not run"); return null; });
    return binding.observeFootprints(0, (w, id) -> {
      f.reads.incrementAndGet();
      return id == f.owner ? NativePathDynamicFootprint.read(w, id,
          new D2PathUnits.DynamicPath(10 << 16, 10 << 16, 0), room, 1, 0x80)
          : NativePathObjectFootprint.read(w, id, p(20, 10), room);
    });
  }

  private static D2ActiveRoom ecsRoom() {
    D2ActiveRoom room = new D2ActiveRoom();
    room.getCoords().setNSubtileWidth(50); room.getCoords().setNSubtileHeight(50);
    D2CommonCollision.allocRoomCollisionGrid(room); return room;
  }

  @Test void realEcsReadersComposeWithDoorTargetRequestAndFootprintRestoration() {
    try (Fixture f = new Fixture()) {
      D2ActiveRoom room = ecsRoom();
      NativePathUnitBinding.FootprintObservation pair = ecsFootprints(f, room);
      assertSame(pair.owner.coordinates, pair.observation.owner);
      assertSame(pair.target.coordinates, pair.observation.target);
      D2PathFootprint.add(pair.owner); D2PathFootprint.add(pair.target);
      int[] before = room.getPCollisionGrid().getPCellsFlags().clone();
      D2PathRequest.State state = request(pair.observation); state.result.path.pathFlags |= 0x800;
      assertTrue(D2PathRequest.compute(state, false,
          new D2PathDispatch.Effects(state, pair.owner, pair.target)) > 0);
      assertEquals(p(20, 12), state.result.path.targetPoint);
      assertArrayEquals(before, room.getPCollisionGrid().getPCellsFlags());
      assertEquals(2, f.reads.get());
    }
  }

  @Test void realEcsReaderRequestRejectsReplacementBeforePathAndGridWrites() {
    try (Fixture f = new Fixture()) {
      D2ActiveRoom room = ecsRoom();
      NativePathUnitBinding.FootprintObservation pair = ecsFootprints(f, room);
      D2PathRequest.State state = request(pair.observation);
      D2PathDispatch.Effects effects = new D2PathDispatch.Effects(state, pair.owner, pair.target);
      D2PathFootprint.add(pair.owner); D2PathFootprint.add(pair.target);
      int[] before = room.getPCollisionGrid().getPCellsFlags().clone();
      state.collidedMask = 123; state.result.path.currentPointIndex = 7;
      f.birth(f.target);
      assertThrows(IllegalStateException.class, () -> D2PathRequest.compute(state, false, effects));
      assertEquals(123, state.collidedMask); assertEquals(7, state.result.path.currentPointIndex);
      assertArrayEquals(before, room.getPCollisionGrid().getPCellsFlags());
    }
  }
  private static D2PathFootprint.Unit footprint(D2PathUnits.Unit coordinates) {
    return D2PathFootprint.Unit.dynamic(coordinates, null, 1, 1, 0x80);
  }

  @Test void footprintObservationSharesExactCoordinateObjectsWithoutSecondRead() {
    try (Fixture f = new Fixture()) {
      NativePathUnitBinding.FootprintObservation pair = f.binding().observeFootprints(3, (w, id) -> {
        f.reads.incrementAndGet();
        return footprint(id == f.owner ? f.ownerData : f.targetData);
      });
      assertEquals(2, f.reads.get());
      assertSame(pair.owner.coordinates, pair.observation.owner);
      assertSame(pair.target.coordinates, pair.observation.target);
      assertEquals(3, pair.observation.speed);
      f.targetData = dynamic(1, 30, 10);
      assertEquals(p(20, 10), pair.observation.coordinates());
      pair.requireCurrent();
    }
  }

  @Test void footprintSelfTargetCapturesOnlyOnce() {
    try (Fixture f = new Fixture()) {
      NativePathUnitBinding binding = new NativePathUnitBinding(f.world, f.owner, f.owner,
          (w, id) -> { fail("coordinate reader must not run"); return null; });
      NativePathUnitBinding.FootprintObservation pair = binding.observeFootprints(0, (w, id) -> {
        f.reads.incrementAndGet(); return footprint(f.ownerData);
      });
      assertSame(pair.owner, pair.target);
      assertSame(pair.observation.owner, pair.observation.target);
      assertEquals(1, f.reads.get());
    }
  }

  @Test void footprintCaptureChecksLifetimeBeforeAndAfterReads() {
    try (Fixture f = new Fixture()) {
      NativePathUnitBinding binding = f.binding();
      assertThrows(IllegalStateException.class, () -> binding.observeFootprints(0, (w, id) -> {
        f.birth(f.target); return footprint(f.ownerData);
      }));
      assertThrows(IllegalStateException.class, () -> binding.observeFootprints(0, (w, id) -> {
        fail("stale binding must not read"); return null;
      }));
    }
  }

  @Test void footprintObservationRejectsInvalidInputAndLaterReplacement() {
    try (Fixture f = new Fixture()) {
      NativePathUnitBinding binding = f.binding();
      assertThrows(IllegalArgumentException.class, () -> binding.observeFootprints(256, (w, id) -> {
        fail("invalid speed must not read"); return null;
      }));
      assertThrows(NullPointerException.class, () -> binding.observeFootprints(0, (w, id) -> null));
      NativePathUnitBinding.FootprintObservation pair = binding.observeFootprints(0,
          (w, id) -> footprint(id == f.owner ? f.ownerData : f.targetData));
      f.birth(f.target);
      assertThrows(IllegalStateException.class, pair::requireCurrent);
      assertThrows(IllegalStateException.class, pair.observation::coordinates);
    }
  }
  private static D2Path.Point p(int x, int y) { return new D2Path.Point(x, y); }
  private static D2PathUnits.Unit dynamic(int type, int x, int y) {
    return D2PathUnits.Unit.dynamic(type, 2,
        new D2PathUnits.DynamicPath(D2PathStep.toFp16Center(x), D2PathStep.toFp16Center(y), 19));
  }
  private static final class Fixture implements AutoCloseable {
    final World world = new World();
    final int owner = world.create(), target = world.create();
    final AtomicInteger reads = new AtomicInteger();
    D2PathUnits.Unit ownerData = dynamic(0, 10, 10), targetData = dynamic(1, 20, 10);
    Fixture() {
      birth(owner); birth(target); world.process();
    }
    void birth(int id) { world.getMapper(NativePathIdentity.class).create(id).allocated(world, id); }
    NativePathUnitBinding binding() {
      return new NativePathUnitBinding(world, owner, target, (w, id) -> {
        assertSame(world, w);
        reads.incrementAndGet();
        return id == owner ? ownerData : targetData;
      });
    }
    @Override public void close() { world.dispose(); }
  }

  @Test void capturesOnePairPerOperationAndRecapturesAfterMovement() {
    try (Fixture f = new Fixture()) {
      NativePathUnitBinding binding = f.binding();
      assertEquals(0, f.reads.get());
      D2PathUnits.Observation first = binding.observe(3);
      assertSame(f.ownerData, first.owner);
      assertSame(f.targetData, first.target);
      assertEquals(p(20, 10), first.coordinates());
      assertEquals(D2PathUnits.sample(f.targetData, 3), first.samplePoint());
      assertEquals(D2PathUnits.distance(f.targetData, f.ownerData), first.distanceToOwner());
      f.targetData = dynamic(1, 25, 12);
      assertEquals(p(20, 10), first.coordinates(), "a request observation never mixes in new data");
      D2PathUnits.Observation next = binding.observe(3);
      assertEquals(p(25, 12), next.coordinates());
      assertEquals(4, f.reads.get(), "sampling immutable data does not reread ECS");
    }
  }

  @Test void selfTargetReadsOnlyOneSnapshot() {
    try (Fixture f = new Fixture()) {
      NativePathUnitBinding binding = new NativePathUnitBinding(f.world, f.owner, f.owner,
          (w, id) -> { f.reads.incrementAndGet(); return f.ownerData; });
      D2PathUnits.Observation observation = binding.observe(0);
      assertSame(observation.owner, observation.target);
      assertEquals(1, f.reads.get());
    }
  }

  @Test void rawStaticCoordinatesAndDynamicHighWordsAreNotGuessedFromEcsFloats() {
    try (Fixture f = new Fixture()) {
      f.ownerData = D2PathUnits.Unit.dynamic(0, 2, new D2PathUnits.DynamicPath(0xffffffff, 0x8000ffff, 0));
      f.targetData = D2PathUnits.Unit.stationary(2, 1, p(65540, -5), 19);
      D2PathUnits.Observation observation = f.binding().observe(0);
      assertEquals(p(65535, 32768), D2PathUnits.coordinates(observation.owner));
      assertEquals(p(65540, -5), observation.coordinates());
      assertEquals(p(4, 65531), observation.samplePoint());
    }
  }

  @Test void unknownIdentityIsRejectedWithoutAllocatingOne() {
    try (Fixture f = new Fixture()) {
      int unknown = f.world.create();
      assertThrows(IllegalArgumentException.class, () -> new NativePathUnitBinding(f.world,
          f.owner, unknown, (w, id) -> { fail("must not resolve data"); return null; }));
      assertFalse(f.world.getMapper(NativePathIdentity.class).has(unknown));
      assertThrows(IllegalArgumentException.class, () -> new NativePathUnitBinding(f.world,
          -1, f.target, (w, id) -> null));
    }
  }

  @Test void changedOwnerOrTargetRejectsBeforeAnyNativeDataRead() {
    for (boolean replaceOwner : new boolean[] {false, true}) {
      try (Fixture f = new Fixture()) {
        NativePathUnitBinding binding = f.binding();
        f.birth(replaceOwner ? f.owner : f.target);
        assertThrows(IllegalStateException.class, () -> binding.observe(0));
        assertEquals(0, f.reads.get());
      }
    }
  }

  @Test void readerCannotReturnAnObservationAfterReplacingTheTargetLifetime() {
    try (Fixture f = new Fixture()) {
      NativePathUnitBinding binding = new NativePathUnitBinding(f.world, f.owner, f.target, (w, id) -> {
        if (id == f.target) f.birth(f.target);
        return id == f.owner ? f.ownerData : f.targetData;
      });
      assertThrows(IllegalStateException.class, () -> binding.observe(0));
    }
  }

  @Test void capturedObservationRejectsAllReadsAfterLifetimeChanges() {
    try (Fixture f = new Fixture()) {
      D2PathUnits.Observation observation = f.binding().observe(0);
      f.birth(f.target);
      assertThrows(IllegalStateException.class, observation::unitType);
      assertThrows(IllegalStateException.class, observation::coordinates);
      assertThrows(IllegalStateException.class, observation::samplePoint);
      assertThrows(IllegalStateException.class, observation::distanceToOwner);
      assertEquals(2, f.reads.get());
    }
  }

  @Test void deletedAndRecycledIdNeverRebindsTheOldReader() {
    try (Fixture f = new Fixture()) {
      NativePathUnitBinding binding = f.binding();
      f.world.delete(f.target); f.world.process();
      int replacement = f.world.create();
      assertEquals(f.target, replacement);
      f.birth(replacement); f.world.process();
      assertThrows(IllegalStateException.class, () -> binding.observe(0));
      assertEquals(0, f.reads.get());
      assertDoesNotThrow(() -> f.binding().observe(0));
    }
  }

  @Test void savedSelectionCannotBindAgainUsingNewTokensAtTheSameId() {
    try (Fixture f = new Fixture()) {
      NativePathIdentity.Token owner = f.world.getMapper(NativePathIdentity.class).get(f.owner).token();
      NativePathIdentity.Token target = f.world.getMapper(NativePathIdentity.class).get(f.target).token();
      f.birth(f.target);
      assertThrows(IllegalStateException.class, () -> new NativePathUnitBinding(f.world,
          f.owner, owner, f.target, target, (w, id) -> { fail("must not resolve replacement"); return null; }));
      assertEquals(0, f.reads.get());
    }
  }

  @Test void stopDistanceRemainsLazyAndDoesNotInventMissingStaticPathDirection() {
    try (Fixture f = new Fixture()) {
      f.targetData = D2PathUnits.Unit.stationary(4, 1, p(10, 10));
      D2PathUnits.Observation observation = f.binding().observe(1);
      D2PathContinuation.State path = new D2PathContinuation.State(0);
      path.currentPoint = p(10, 10);
      path.targetUnit = observation;
      path.setStepNum(255);
      assertEquals(D2PathContinuation.Decision.STOP, D2PathContinuation.decide(path));
      assertThrows(IllegalArgumentException.class, observation::samplePoint);
    }
  }

  @Test void invalidSpeedAndMissingDataFailExplicitly() {
    try (Fixture f = new Fixture()) {
      NativePathUnitBinding binding = f.binding();
      assertThrows(IllegalArgumentException.class, () -> binding.observe(-1));
      assertThrows(IllegalArgumentException.class, () -> binding.observe(256));
      assertEquals(0, f.reads.get());
      f.targetData = null;
      assertThrows(NullPointerException.class, () -> binding.observe(0));
    }
  }

  private static D2PathRequest.State request(D2PathUnits.Observation observation) {
    D2PathContinuation.State path = new D2PathContinuation.State(observation.owner.type);
    path.currentPoint = D2PathUnits.coordinates(observation.owner);
    path.targetPoint = observation.coordinates();
    path.targetUnit = observation;
    path.pathType = 2;
    path.setNewDistance(30);
    D2PathResult.State result = new D2PathResult.State(path);
    D2PathUnits.DynamicPath raw = D2PathUnits.dynamicPath(observation.owner);
    if (raw != null) { result.precisionX = raw.precisionX; result.precisionY = raw.precisionY; }
    result.velocity = 256;
    result.room = new D2PathResult.Room(0, 0, 50, 50);
    return new D2PathRequest.State(result, observation.target,
        new D2PathRequest.Room(result.room, false), false, observation.speed, 2, 1, 1, 70);
  }

  @Test void staleRequestIsRejectedBeforeCollisionOrPathStateWrites() {
    try (Fixture f = new Fixture()) {
      D2PathRequest.State state = request(f.binding().observe(0));
      state.collidedMask = 1234;
      state.result.path.currentPointIndex = 7;
      f.birth(f.owner);
      assertThrows(IllegalStateException.class, () -> D2PathRequest.compute(state, false, null));
      assertEquals(1234, state.collidedMask);
      assertEquals(7, state.result.path.currentPointIndex);
    }
  }

  @Test void boundObservationComposesWithRealFootprintsAndDispatch() {
    try (Fixture f = new Fixture()) {
      D2PathUnits.Observation observation = f.binding().observe(0);
      D2PathRequest.State state = request(observation);
      state.result.path.pathFlags |= 0x800;
      D2ActiveRoom room = new D2ActiveRoom();
      room.getCoords().setNSubtileWidth(50);
      room.getCoords().setNSubtileHeight(50);
      D2CommonCollision.allocRoomCollisionGrid(room);
      D2PathFootprint.Unit owner = D2PathFootprint.Unit.dynamic(observation.owner, room, 1, 1, 0x80);
      D2PathFootprint.Unit target = D2PathFootprint.Unit.dynamic(observation.target, room, 1, 1, 0x100);
      D2PathFootprint.add(owner); D2PathFootprint.add(target);
      assertEquals(1, D2PathRequest.compute(state, false, new D2PathDispatch.Effects(state, owner, target)));
      assertEquals(p(20, 10), state.result.points[state.result.path.pathPointCount - 1]);
      assertEquals(0x80, D2CommonCollision.checkMask(room, 10, 10, 0x80));
      assertEquals(0x100, D2CommonCollision.checkMask(room, 20, 10, 0x100));
      assertEquals(2, f.reads.get());
    }
  }
}
