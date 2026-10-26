package com.riiablo.engine.server.combat;

import com.artemis.EntityAllocatorCheckpoint;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.riiablo.engine.replay.CompanionRuntimeCheckpoint;
import com.riiablo.engine.server.Actioneer;
import com.riiablo.engine.server.MercenaryFollowSystem;
import com.riiablo.engine.server.NativePlayerTargetSlots;
import com.riiablo.engine.server.component.*;
import com.riiablo.map.Map;
import java.lang.reflect.*;
import java.lang.Object;
import java.nio.ByteBuffer;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CombatPositionCheckpointTest {
  @Test void sparseOutOfOrderRetiredAndEmptyFramesPreserveEverySlotAndContainer() {
    try (Fixture a = new Fixture(4, false); Fixture b = new Fixture(4, false)) {
      a.history.capture(a.world, 1);
      a.world.getMapper(Position.class).get(0).position.set(-.5f, 2.5f);
      a.world.getMapper(Size.class).create(0).size = 0;
      a.world.getMapper(MapWrapper.class).get(0).roomId = 37;
      a.history.capture(a.world, 9); // replaces 1, but leaves all other slots untouched
      a.history.capture(a.world, 3);
      a.world.delete(1); EntityAllocatorCheckpoint.flushStructure(a.world);
      a.history.capture(a.world, 2); // retired ID 1 remains in 3 and 9
      a.world.delete(0); EntityAllocatorCheckpoint.flushStructure(a.world);
      a.history.capture(a.world, 4); // captured empty frame != uninitialised slot
      long[] ticks = b.history.ticks; Object frames = b.history.frames; Object slot = b.history.frames[0];
      CombatPositionCheckpoint image = a.image(); image.restore(b.history);
      assertSame(ticks, b.history.ticks); assertSame(frames, b.history.frames); assertSame(slot, b.history.frames[0]);
      assertArrayEquals(image.bytes(), b.image().bytes()); assertEquals(9, b.history.latestTick());
      assertEquals(4, b.history.ticks[0]); assertEquals(0, b.history.frames[0].size);
      assertNotNull(b.history.snapshot(1, 3)); assertNull(b.history.snapshot(1, 2));
      assertEquals(0, b.history.snapshot(0, 9).x); assertEquals(3, b.history.snapshot(0, 9).y);
      assertEquals(37, b.history.snapshot(0, 9).roomId); assertEquals(0, b.history.snapshot(0, 9).size);
      assertSame(b.zone, b.history.recentSnapshot(0, 0).zone);
      assertNotSame(a.zone, b.history.recentSnapshot(0, 0).zone);
      // latestTick is a high-water mark even if an earlier tick overwrites its slot.
      a.history.capture(a.world, 5); a.image().restore(b.history);
      assertEquals(9, b.history.latestTick()); assertNull(b.history.recentSnapshot(0, 0));
      assertArrayEquals(a.image().bytes(), b.image().bytes());
    }
  }

  @Test void emptyImageClearsOnlyDestinationHistoryAndNeverSamplesCurrentPositions() {
    try (Fixture a = new Fixture(4, false); Fixture b = new Fixture(4, false); Fixture c = new Fixture(4, false)) {
      b.history.capture(b.world, 5); c.history.capture(c.world, 7);
      a.image().restore(b.history); assertEquals(0, b.history.latestTick());
      assertNull(b.history.snapshot(0, 5)); assertEquals(7, c.history.latestTick());
      assertArrayEquals(a.image().bytes(), b.image().bytes());
    }
  }

  @Test void historicalMeleeReadsReboundZonesAndNeverCurrentCoordinates() {
    try (Fixture a = new Fixture(4, false); Fixture b = new Fixture(4, false)) {
      a.history.capture(a.world, 11); a.image().restore(b.history);
      b.world.getMapper(Position.class).get(1).position.set(500, 500);
      assertEquals(CombatPositionHistory.RangeResult.IN_RANGE, b.history.meleeRange(0, 1, 10, 0, 11));
      assertSame(b.zone, b.history.snapshot(1, 11).zone);
      b.history.capture(b.world, 12);
      assertEquals(CombatPositionHistory.RangeResult.OUT_OF_RANGE, b.history.meleeRange(0, 1, 10, 0, 12));
    }
  }

  @Test void malformedImagesRejectAllTruncationsTrailingCountsOrderAndBooleans() {
    World world = new World();
    try {
      for (int i = 0; i < 2; i++) world.getMapper(Position.class).create(world.create());
      EntityAllocatorCheckpoint.flushStructure(world);
      CombatPositionHistory h = new CombatPositionHistory(null, 1); h.capture(world, 1);
      byte[] bytes = CombatPositionCheckpoint.capture(h).bytes();
      for (int n = 0; n < bytes.length; n++) {
        byte[] truncated = Arrays.copyOf(bytes, n);
        assertThrows(IllegalArgumentException.class, () -> CombatPositionCheckpoint.fromBytes(truncated));
      }
      assertThrows(IllegalArgumentException.class, () -> CombatPositionCheckpoint.fromBytes(Arrays.copyOf(bytes, bytes.length + 1)));
      for (int offset : new int[] {16, 49}) {
        byte[] bad = bytes.clone(); bad[offset] = 2;
        assertThrows(IllegalArgumentException.class, () -> CombatPositionCheckpoint.fromBytes(bad));
      }
      for (int[] mutation : new int[][] {{0, 99}, {4, 0}, {4, Integer.MAX_VALUE}, {25, -1}, {25, Integer.MAX_VALUE},
          {29, -1}, {41, -1}, {50, 0}}) {
        byte[] bad = bytes.clone(); ByteBuffer.wrap(bad).putInt(mutation[0], mutation[1]);
        assertThrows(IllegalArgumentException.class, () -> CombatPositionCheckpoint.fromBytes(bad));
      }
      for (long tick : new long[] {-1, 0, 2}) {
        byte[] bad = bytes.clone(); ByteBuffer.wrap(bad).putLong(17, tick);
        assertThrows(IllegalArgumentException.class, () -> CombatPositionCheckpoint.fromBytes(bad));
      }
      byte[] input = bytes.clone(); CombatPositionCheckpoint image = CombatPositionCheckpoint.fromBytes(input);
      input[3] = 99; image.bytes()[3] = 99; assertArrayEquals(bytes, image.bytes());
    } finally { world.dispose(); }
  }

  @Test void laterZoneFailureAndAmbiguityLeaveAllDestinationSlotsUntouched() {
    try (Fixture a = new Fixture(4, false); Fixture b = new Fixture(4, false)) {
      a.history.capture(a.world, 1); b.history.capture(b.world, 2);
      byte[] before = b.image().bytes(); CombatPositionCheckpoint image = a.image();
      when(b.map.getZones()).thenReturn(new Array<>());
      assertThrows(IllegalStateException.class, () -> image.restore(b.history));
      when(b.map.getZones()).thenReturn(new Array<>(new Map.Zone[] {b.zone}));
      assertArrayEquals(before, b.image().bytes());
      when(b.map.getZones()).thenReturn(new Array<>(new Map.Zone[] {b.zone, b.zone}));
      assertThrows(IllegalStateException.class, () -> image.restore(b.history));
      when(b.map.getZones()).thenReturn(new Array<>(new Map.Zone[] {b.zone}));
      assertArrayEquals(before, b.image().bytes());
      assertThrows(IllegalStateException.class, () -> image.restore(new CombatPositionHistory(b.map, 8)));
      assertThrows(IllegalStateException.class, () -> image.restore(new CombatPositionHistory(null, 4)));
    }
  }

  @Test void foreignZonesAreRejectedInsteadOfImportingSourceObjects() {
    try (Fixture f = new Fixture(4, false)) {
      f.world.getMapper(MapWrapper.class).get(1).zone = new Map.Zone();
      f.history.capture(f.world, 1);
      assertThrows(IllegalStateException.class, f::image);
    }
  }

  @Test void zonedWireAndCompanionTailAreStrictAndFieldCoverageIsExplicit() {
    Set<String> fields = new HashSet<>();
    for (Field field : CombatPositionHistory.class.getDeclaredFields()) {
      if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) fields.add(field.getName());
    }
    assertEquals(new HashSet<>(Arrays.asList("capacity", "ticks", "frames", "map", "latestTick")), fields);
    try (Fixture f = new Fixture(4, true)) {
      f.world.process(); byte[] bytes = f.image().bytes();
      for (int i = 0; i < bytes.length; i++) {
        byte[] truncated = Arrays.copyOf(bytes, i);
        assertThrows(IllegalArgumentException.class, () -> CombatPositionCheckpoint.fromBytes(truncated));
      }
      CompanionRuntimeCheckpoint current = f.companions(); byte[] companion = current.bytes();
      int tail = companion.length - (1 + 4 + current.positionHistory().bytes().length + 1 + 8);
      for (int offset : new int[] {tail, companion.length - 9}) {
        byte[] bad = companion.clone(); bad[offset] = 2;
        assertThrows(IllegalArgumentException.class, () -> CompanionRuntimeCheckpoint.fromBytes(bad));
      }
      byte[] badLength = companion.clone(); ByteBuffer.wrap(badLength).putInt(tail + 1, Integer.MAX_VALUE);
      assertThrows(IllegalArgumentException.class, () -> CompanionRuntimeCheckpoint.fromBytes(badLength));
      byte[] badClock = companion.clone(); ByteBuffer.wrap(badClock).putLong(companion.length - 8, -1L);
      assertThrows(IllegalArgumentException.class, () -> CompanionRuntimeCheckpoint.fromBytes(badClock));
      f.capture.restoreCheckpointTick(Long.MAX_VALUE);
      byte[] before = f.image().bytes(); assertThrows(IllegalStateException.class, f.world::process);
      assertEquals(Long.MAX_VALUE, f.capture.checkpointTick()); assertArrayEquals(before, f.image().bytes());
    }
  }

  @Test void coveredAbsentHistoryDiffersFromLegacyUncoveredAndTopologyRejectionIsAtomic() {
    try (Fixture f = new Fixture(4, false)) {
      World bare = new World(new WorldConfigurationBuilder().with(new NativePlayerTargetSlots(), new MercenaryFollowSystem()).build());
      try {
        for (int i = 0; i < 2; i++) bare.create(); EntityAllocatorCheckpoint.flushStructure(bare);
        CompanionRuntimeCheckpoint absent = CompanionRuntimeCheckpoint.capture(bare, new int[] {0, 1});
        assertTrue(absent.hasPositionHistoryCoverage()); assertNull(absent.positionHistory());
        f.history.capture(f.world, 9); byte[] before = f.companions().bytes();
        assertThrows(IllegalStateException.class, () -> absent.restore(f.world));
        assertArrayEquals(before, f.companions().bytes());
        byte[] bareBefore = CompanionRuntimeCheckpoint.capture(bare, new int[] {0, 1}).bytes();
        assertThrows(IllegalStateException.class, () -> f.companions().restore(bare));
        assertArrayEquals(bareBefore, CompanionRuntimeCheckpoint.capture(bare, new int[] {0, 1}).bytes());
      } finally { bare.dispose(); }
    }
  }

  @Test void captureClockAndHistoryResumeTogetherWithoutRunningASystemDuringRestore() {
    try (Fixture a = new Fixture(4, true); Fixture b = new Fixture(4, true)) {
      for (int tick = 0; tick < 9; tick++) a.world.process();
      CompanionRuntimeCheckpoint image = a.companions(); image.restore(b.world);
      assertEquals(9, b.capture.checkpointTick()); assertEquals(9, b.history.latestTick());
      for (int tick = 0; tick < 20; tick++) {
        a.world.process(); b.world.process();
        assertArrayEquals(a.companions().bytes(), b.companions().bytes());
      }
      assertEquals(29, b.capture.checkpointTick());
      assertThrows(IllegalArgumentException.class, () -> b.capture.restoreCheckpointTick(-1));
    }
  }

  @Test void legacyCompanionV2DoesNotClearUncoveredHistoryOrCaptureClock() {
    try (Fixture a = new Fixture(4, true); Fixture b = new Fixture(4, true)) {
      a.world.process(); for (int i = 0; i < 5; i++) b.world.process();
      CompanionRuntimeCheckpoint current = a.companions();
      int tail = 1 + 4 + current.positionHistory().bytes().length + 1 + 8;
      byte[] old = Arrays.copyOf(current.bytes(), current.bytes().length - tail);
      ByteBuffer.wrap(old).putInt(2); CompanionRuntimeCheckpoint legacy = CompanionRuntimeCheckpoint.fromBytes(old);
      assertFalse(legacy.hasPositionHistoryCoverage()); assertNull(legacy.positionHistory());
      byte[] before = b.image().bytes(); legacy.restore(b.world);
      assertArrayEquals(before, b.image().bytes()); assertEquals(5, b.capture.checkpointTick());
      current.restore(b.world); assertEquals(1, b.capture.checkpointTick());
    }
  }

  @Test void systemAndOwnerBindingMismatchRejectBeforeRewindingWorldQuota() {
    try (Fixture a = new Fixture(4, true); Fixture b = new Fixture(4, false)) {
      a.world.process(); b.world.getSystem(NativePlayerTargetSlots.class).nextPetSpawnOrder();
      byte[] before = b.companions().bytes();
      assertThrows(IllegalStateException.class, () -> a.companions().restore(b.world));
      assertArrayEquals(before, b.companions().bytes());
    }
    try (Fixture f = new Fixture(4, true)) {
      f.capture.history = new CombatPositionHistory(f.map, 4);
      assertThrows(IllegalStateException.class, f::companions);
    }
  }

  @Test void realHirelingTrailConsumerChoosesTheSameHistoricalLandingAfterRestore() throws Exception {
    try (Fixture a = new Fixture(32, false); Fixture b = new Fixture(32, false)) {
      a.world.getMapper(Position.class).get(0).position.set(30, 8); a.history.capture(a.world, 10);
      a.world.getMapper(Position.class).get(0).position.set(1, 0); a.history.capture(a.world, 11);
      a.companions().restore(b.world);
      Method trail = MercenaryFollowSystem.class.getDeclaredMethod("tryOwnerTrail", int.class, int.class,
          Map.Zone.class, Vector2.class, int.class, boolean.class); trail.setAccessible(true);
      for (Fixture f : new Fixture[] {a, b}) {
        Actioneer actions = mock(Actioneer.class);
        Field field = MercenaryFollowSystem.class.getDeclaredField("actioneer"); field.setAccessible(true); field.set(f.follow, actions);
        when(actions.tryRunTo(eq(1), any(Vector2.class), eq(60))).thenReturn(true);
        assertEquals(true, trail.invoke(f.follow, 1, 0, f.zone, new Vector2(0, 0), 1, true));
        verify(actions).tryRunTo(1, new Vector2(30, 8), 60); // newest <=5 cells is skipped
      }
    }
  }

  private static final class Fixture implements AutoCloseable {
    final Map map = mock(Map.class);
    final Map.Zone zone = new Map.Zone();
    final CombatPositionHistory history;
    final CombatPositionCaptureSystem capture = new CombatPositionCaptureSystem();
    final MercenaryFollowSystem follow = new MercenaryFollowSystem();
    final World world;
    Fixture(int capacity, boolean automatic) {
      when(map.getZones()).thenReturn(new Array<>(new Map.Zone[] {zone}));
      history = new CombatPositionHistory(map, capacity);
      WorldConfigurationBuilder builder = new WorldConfigurationBuilder().with(new NativePlayerTargetSlots(), follow);
      if (automatic) builder.with(capture);
      world = new World(builder.build().register("map", map).register("combatPositionHistory", history));
      for (int i = 0; i < 2; i++) {
        int id = world.create(); world.getMapper(Position.class).create(id).position.set(i * 5, 0);
        world.getMapper(MapWrapper.class).create(id).set(map, zone);
      }
      EntityAllocatorCheckpoint.flushStructure(world);
    }
    CombatPositionCheckpoint image() { return CombatPositionCheckpoint.capture(history); }
    CompanionRuntimeCheckpoint companions() { return CompanionRuntimeCheckpoint.capture(world, new int[] {0, 1}); }
    @Override public void close() { world.dispose(); }
  }
}
