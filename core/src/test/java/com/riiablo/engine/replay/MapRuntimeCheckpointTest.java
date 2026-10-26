package com.riiablo.engine.replay;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.utils.Array;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Networked;
import com.riiablo.map.Map;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MapRuntimeCheckpointTest {
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
