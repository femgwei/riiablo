package com.riiablo.engine.replay;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.utils.Array;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Networked;
import com.riiablo.map.Map;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.NativePathIdentity;
import com.riiablo.engine.Engine;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MapRuntimeCheckpointTest {
  @Test void deathHistoryRoundTripPreservesCursorAndReplacesFutureHistory() {
    try (DeathFixture source = new DeathFixture(); DeathFixture target = new DeathFixture()) {
      for (int i = 0; i < 4; i++) source.kill(i);
      target.kill(5);
      MapRuntimeCheckpoint image = MapRuntimeCheckpoint.capture(source.world, source.ids);
      image.restore(target.world, (a, b, c, d) -> new MapRuntimeCheckpoint.Binding(target.map, target.zone));
      assertArrayEquals(source.modes.captureRoomDeathHistory(source.room),
          target.modes.captureRoomDeathHistory(target.room));
      assertFalse(target.modes.isRecentRoomDeath(target.room, 5));
      assertArrayEquals(image.bytes(), MapRuntimeCheckpoint.capture(target.world, target.ids).bytes());
      target.kill(4);
      assertFalse(target.modes.isRecentRoomDeath(target.room, 0));
      assertTrue(target.modes.isRecentRoomDeath(target.room, 1));
    }
  }

  @Test void staleDeathLifetimeRemainsStaleAfterPortableRestore() {
    try (DeathFixture source = new DeathFixture(); DeathFixture target = new DeathFixture()) {
      source.kill(0);
      source.world.getMapper(NativePathIdentity.class).get(0).allocated(source.world, 0);
      MapRuntimeCheckpoint image = MapRuntimeCheckpoint.capture(source.world, source.ids);
      image.restore(target.world, (a, b, c, d) -> new MapRuntimeCheckpoint.Binding(target.map, target.zone));
      assertFalse(target.modes.isRecentRoomDeath(target.room, 0));
      assertArrayEquals(image.bytes(), MapRuntimeCheckpoint.capture(target.world, target.ids).bytes());
    }
  }

  @Test void partialCaptureDoesNotBindAnExcludedDeathToARecycledId() {
    try (DeathFixture source = new DeathFixture(); DeathFixture target = new DeathFixture()) {
      source.kill(0);
      MapRuntimeCheckpoint image = MapRuntimeCheckpoint.capture(source.world, new int[] {1});
      image.restore(target.world, (a, b, c, d) -> new MapRuntimeCheckpoint.Binding(target.map, target.zone));
      assertFalse(target.modes.isRecentRoomDeath(target.room, 0));
    }
  }

  @Test void missingCurrentLifetimeFailsBeforeChangingBindingsOrHistory() {
    try (DeathFixture source = new DeathFixture(); DeathFixture target = new DeathFixture()) {
      source.kill(0); target.kill(5);
      MapRuntimeCheckpoint image = MapRuntimeCheckpoint.capture(source.world, source.ids);
      target.world.getMapper(NativePathIdentity.class).remove(0);
      target.world.process();
      Map replacementMap = new Map(77, 1);
      assertThrows(IllegalStateException.class, () -> image.restore(target.world,
          (a, b, c, d) -> new MapRuntimeCheckpoint.Binding(replacementMap, target.zone)));
      assertSame(target.map, target.world.getMapper(MapWrapper.class).get(0).map);
      assertTrue(target.modes.isRecentRoomDeath(target.room, 5));
    }
  }

  @Test void emptyHistoryClearsFutureDeathsAndMalformedCursorIsRejected() {
    try (DeathFixture source = new DeathFixture(); DeathFixture target = new DeathFixture()) {
      MapRuntimeCheckpoint empty = MapRuntimeCheckpoint.capture(source.world, source.ids);
      target.kill(0);
      empty.restore(target.world, (a, b, c, d) -> new MapRuntimeCheckpoint.Binding(target.map, target.zone));
      assertFalse(target.modes.isRecentRoomDeath(target.room, 0));
      source.kill(0);
      byte[] corrupt = MapRuntimeCheckpoint.capture(source.world, source.ids).bytes();
      java.nio.ByteBuffer.wrap(corrupt).putInt(corrupt.length - 36, 4);
      assertThrows(IllegalArgumentException.class, () -> MapRuntimeCheckpoint.fromBytes(corrupt));
    }
  }

  @Test void legacyVersionOneRestoresWithEmptyDeathHistory() throws Exception {
    try (DeathFixture target = new DeathFixture()) {
      java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
      java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
      out.writeInt(1); out.writeInt(1); out.writeInt(0); out.writeBoolean(true);
      out.writeInt(77); out.writeInt(1); out.writeInt(target.zone.levelId());
      out.writeInt(target.zone.levelSeed()); out.writeInt(0); out.writeBoolean(false);
      target.kill(0);
      MapRuntimeCheckpoint.fromBytes(bytes.toByteArray()).restore(target.world,
          (a, b, c, d) -> new MapRuntimeCheckpoint.Binding(target.map, target.zone));
      assertFalse(target.modes.isRecentRoomDeath(target.room, 0));
    }
  }

  private static final class DeathFixture implements AutoCloseable {
    final World world = new World(new WorldConfigurationBuilder().with(new EventSystem(), new CofManager()).build());
    final Map map = new Map(77, 1);
    final Map.Zone zone = new Map.Zone();
    final Map.RoomEx room = zone.addRoomEx(0, 0, 100, 100);
    final CofManager modes = world.getSystem(CofManager.class);
    final int[] ids = new int[6];
    DeathFixture() {
      room.setAdjacentRoomIds(new int[0]);
      for (int i = 0; i < ids.length; i++) {
        int id = ids[i] = world.create();
        world.getMapper(Networked.class).create(id);
        world.getMapper(MapWrapper.class).create(id).set(map, zone).roomId = 0;
        world.getMapper(Monster.class).create(id).set(null, null);
        world.getMapper(CofReference.class).create(id).mode = Engine.Monster.MODE_NU;
        world.getMapper(Position.class).create(id).position.set(10 + i, 10);
        world.getMapper(NativePathIdentity.class).create(id).allocated(world, id);
      }
      world.process();
    }
    void kill(int id) { modes.setMode(id, Engine.Monster.MODE_NU); modes.setMode(id, Engine.Monster.MODE_DT); }
    @Override public void close() { world.dispose(); }
  }

  @Test void capturesStableMapZoneRoomIdentityAndRestoresThroughResolver() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      Map sourceMap = new Map(77, 1);
      Map.Zone sourceZone = zone(12, 345, 2);
      source.bind(sourceMap, sourceZone, 1, true);
      MapRuntimeCheckpoint image = MapRuntimeCheckpoint.capture(source.world, new int[] {0, 1});

      Map targetMap = new Map(77, 1);
      Map.Zone targetZone = zone(12, 345, 2);
      image.restore(target.world, (seed, difficulty, levelId, levelSeed) -> {
        assertEquals(77, seed); assertEquals(1, difficulty);
        assertEquals(12, levelId); assertEquals(345, levelSeed);
        return new MapRuntimeCheckpoint.Binding(targetMap, targetZone);
      });
      MapWrapper restored = target.world.getMapper(MapWrapper.class).get(0);
      assertSame(targetMap, restored.map);
      assertSame(targetZone, restored.zone);
      assertEquals(1, restored.roomId);
      assertTrue(restored.roomIdPinned);
      assertNull(target.world.getMapper(MapWrapper.class).get(1).map);
      assertArrayEquals(image.bytes(), MapRuntimeCheckpoint.capture(target.world, new int[] {0, 1}).bytes());
    }
  }

  @Test void resolverAndRoomCoverageAreValidatedBeforeMutation() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      source.bind(new Map(3, 0), zone(9, 99, 2), 1, false);
      MapRuntimeCheckpoint image = MapRuntimeCheckpoint.capture(source.world, new int[] {0, 1});
      MapWrapper before = target.world.getMapper(MapWrapper.class).get(0);
      assertThrows(IllegalStateException.class, () -> image.restore(target.world, (a, b, c, d) -> null));
      assertNull(before.map);
      assertThrows(IllegalArgumentException.class,
          () -> MapRuntimeCheckpoint.fromBytes(Arrays.copyOf(image.bytes(), image.bytes().length - 1)));
      byte[] trailing = Arrays.copyOf(image.bytes(), image.bytes().length + 1);
      assertThrows(IllegalArgumentException.class, () -> MapRuntimeCheckpoint.fromBytes(trailing));
    }
  }

  private static Map.Zone zone(int levelId, int levelSeed, int rooms) {
    Map.Zone zone = mock(Map.Zone.class);
    when(zone.levelId()).thenReturn(levelId);
    when(zone.levelSeed()).thenReturn(levelSeed);
    Array<Map.RoomEx> roomList = new Array<>();
    for (int i = 0; i < rooms; i++) roomList.add(mock(Map.RoomEx.class));
    when(zone.getRoomsEx()).thenReturn(roomList);
    when(zone.isRoomActivationTracking()).thenReturn(false);
    return zone;
  }

  private static final class Fixture implements AutoCloseable {
    final World world = new World(new WorldConfigurationBuilder().build());
    Fixture() {
      int a = world.create(); world.getMapper(Networked.class).create(a); world.getMapper(MapWrapper.class).create(a);
      int b = world.create(); world.getMapper(Networked.class).create(b); world.getMapper(MapWrapper.class).create(b);
      world.process();
    }
    void bind(Map map, Map.Zone zone, int room, boolean pinned) {
      MapWrapper wrapper = world.getMapper(MapWrapper.class).get(0);
      wrapper.set(map, zone); wrapper.roomId = room; wrapper.roomIdPinned = pinned;
    }
    @Override public void close() { world.dispose(); }
  }
}
