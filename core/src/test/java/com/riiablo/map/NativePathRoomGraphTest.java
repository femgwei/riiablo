package com.riiablo.map;

import com.d2moo.common.drlg.D2DrlgLevel;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.d2moo.common.path.D2PathRequest;
import com.riiablo.codec.excel.Levels;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativePathRoomGraphTest {
  private static Map.Zone zone(int id) {
    Map.Zone zone = new Map.Zone();
    zone.level = new Levels.Entry();
    zone.level.Id = id;
    return zone;
  }

  private static D2DrlgRoom nativeRoom(int levelId) {
    D2DrlgLevel level = new D2DrlgLevel();
    level.setLevelId(levelId);
    D2DrlgRoom room = new D2DrlgRoom();
    room.setLevel(level);
    level.setFirstRoomEx(room);
    return room;
  }

  @Test void orderedCrossLevelSelfDuplicateAndNullSlots() {
    Map.Zone a = zone(1), b = zone(2);
    Map.RoomEx ar = a.addRoomEx(0, 0, 10, 10), br = b.addRoomEx(10, 0, 10, 10);
    D2DrlgRoom an = nativeRoom(1), bn = nativeRoom(2);
    an.setPpRoomsNear(new D2DrlgRoom[] {bn, null, an, bn});
    an.setNRoomsNear(4);
    ar.setNativePopulationMetadata(an);
    br.setNativePopulationMetadata(bn);
    NativePathRoomGraph graph = new NativePathRoomGraph(Arrays.asList(a, b));
    D2PathRequest.Room room = graph.room(ar);
    assertEquals(Arrays.asList(graph.room(br), null, room, graph.room(br)), room.nearby);
    assertSame(graph.room(br), D2PathRequest.findRoom(room, 10, 0));
    assertSame(room, D2PathRequest.findRoom(room, 9, 9));
    assertNull(D2PathRequest.findRoom(room, 20, 0));
    assertThrows(UnsupportedOperationException.class, () -> room.nearby.clear());
  }

  @Test void missingZoneRemainsNullAndSnapshotIsStable() {
    Map.Zone a = zone(1);
    Map.RoomEx ar = a.addRoomEx(-10, -10, 10, 10);
    D2DrlgRoom an = nativeRoom(1), bn = nativeRoom(2);
    an.setPpRoomsNear(new D2DrlgRoom[] {bn});
    an.setNRoomsNear(1);
    ar.setNativePopulationMetadata(an);
    NativePathRoomGraph graph = new NativePathRoomGraph(Arrays.asList(a));
    assertEquals(Arrays.asList((D2PathRequest.Room) null), graph.room(ar).nearby);
    ar.nativePathNeighborKeys()[0] = 0L;
    assertEquals(Long.valueOf(2L << 32), ar.nativePathNeighborKeys()[0]);
    an.setNRoomsNear(0);
    ar.setNativePopulationMetadata(an);
    assertEquals(1, graph.room(ar).nearby.size());
    assertTrue(new NativePathRoomGraph(Arrays.asList(a)).room(ar).nearby.isEmpty());
    assertEquals(-10, graph.room(ar).bounds.x);
  }

  @Test void rejectsLegacyTopologyAndDuplicateLevelKeys() {
    Map.Zone a = zone(1), b = zone(1);
    Map.RoomEx ar = a.addRoomEx(0, 0, 10, 10), br = b.addRoomEx(10, 0, 10, 10);
    ar.setAdjacentRoomIds(new int[0]);
    assertThrows(IllegalArgumentException.class, () -> new NativePathRoomGraph(Arrays.asList(a)));
    ar.setNativePopulationMetadata(nativeRoom(1));
    br.setNativePopulationMetadata(nativeRoom(1));
    assertThrows(IllegalArgumentException.class, () -> new NativePathRoomGraph(Arrays.asList(a, b)));
    assertNull(new NativePathRoomGraph(Arrays.asList(a)).room(br));
  }

  @Test void doesNotExpandTransitivelyOrInferGeometricNeighbors() {
    Map.Zone a = zone(1), b = zone(2), c = zone(3);
    Map.RoomEx ar = a.addRoomEx(0, 0, 10, 10);
    Map.RoomEx br = b.addRoomEx(10, 0, 10, 10);
    Map.RoomEx cr = c.addRoomEx(20, 0, 10, 10);
    D2DrlgRoom an = nativeRoom(1), bn = nativeRoom(2), cn = nativeRoom(3);
    an.setPpRoomsNear(new D2DrlgRoom[] {bn, cn});
    an.setNRoomsNear(1); // unused array tail must not become a link
    bn.setPpRoomsNear(new D2DrlgRoom[] {cn});
    bn.setNRoomsNear(1);
    ar.setNativePopulationMetadata(an);
    br.setNativePopulationMetadata(bn);
    cr.setNativePopulationMetadata(cn);
    NativePathRoomGraph graph = new NativePathRoomGraph(Arrays.asList(a, b, c));
    assertNull(D2PathRequest.findRoom(graph.room(ar), 20, 0));
    assertSame(graph.room(cr), D2PathRequest.findRoom(graph.room(br), 20, 0));
    assertNull(D2PathRequest.findRoom(graph.room(cr), 19, 0));
  }
}
