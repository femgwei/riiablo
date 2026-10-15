package com.d2moo.common.path;

import com.d2moo.common.collision.D2CommonCollision;
import com.d2moo.common.drlg.D2ActiveRoom;
import com.d2moo.common.drlg.D2DrlgAct;
import com.d2moo.common.drlg.D2DrlgLevel;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.d2moo.common.dungeon.Dungeon;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class D2PathActiveRoomsTest {
  private static final class Fixture {
    final D2DrlgAct act = new D2DrlgAct();
    final D2DrlgRoom[] drlg = new D2DrlgRoom[3];
    final D2ActiveRoom[] live = new D2ActiveRoom[3];
    Fixture() {
      for (int i = 0; i < 3; i++) {
        D2DrlgLevel level = new D2DrlgLevel();
        level.setLevelId(i + 1); // town level 1, outdoors levels 2 and 3
        drlg[i] = new D2DrlgRoom();
        drlg[i].setLevel(level);
        level.setFirstRoomEx(drlg[i]);
        drlg[i].setNTileXPos(i * 2);
        drlg[i].setNTileWidth(2);
        drlg[i].setNTileHeight(2);
      }
      for (D2DrlgRoom room : drlg) {
        room.setPpRoomsNear(drlg);
        room.setNRoomsNear(3);
      }
      live[0] = allocate(0);
      live[1] = allocate(1);
    }
    D2ActiveRoom allocate(int i) {
      return live[i] = Dungeon.allocRoom(act, drlg[i], null, null, i + 17, 0);
    }
  }

  @Test void capturesLiveCompactedPrefixAndTownAcrossLevels() {
    Fixture f = new Fixture();
    D2PathActiveRooms graph = new D2PathActiveRooms(f.act);
    assertEquals(Arrays.asList(graph.room(f.live[0]), graph.room(f.live[1])), graph.room(f.live[0]).nearby);
    assertEquals(3, f.live[0].getPpRoomList().length);
    assertEquals(2, graph.room(f.live[0]).nearby.size());
    assertTrue(graph.room(f.live[0]).town);
    assertFalse(graph.room(f.live[1]).town);
    assertSame(graph.room(f.live[1]), D2PathRequest.findRoom(graph.room(f.live[0]), 10, 0));
    assertNull(D2PathRequest.findRoom(graph.room(f.live[0]), 20, 0));
    assertDoesNotThrow(graph::requireCurrent);
  }

  @Test void preservesActualListOrderNullSelfAndDuplicates() {
    Fixture f = new Fixture();
    D2ActiveRoom a = f.live[0], b = f.live[1];
    a.setPpRoomList(new D2ActiveRoom[] {b, null, a, b, a});
    a.setNNumRooms(4);
    D2PathActiveRooms graph = new D2PathActiveRooms(f.act);
    assertEquals(Arrays.asList(graph.room(b), null, graph.room(a), graph.room(b)), graph.room(a).nearby);
    assertThrows(UnsupportedOperationException.class, () -> graph.room(a).nearby.clear());
    a.getPpRoomList()[4] = null;
    assertDoesNotThrow(graph::requireCurrent);
    a.getPpRoomList()[0] = a;
    assertThrows(IllegalStateException.class, graph::requireCurrent);
  }

  @Test void allocationInvalidatesAndRecaptureAddsNewRoom() {
    Fixture f = new Fixture();
    D2PathActiveRooms before = new D2PathActiveRooms(f.act);
    f.allocate(2);
    assertThrows(IllegalStateException.class, before::requireCurrent);
    D2PathActiveRooms after = new D2PathActiveRooms(f.act);
    assertSame(after.room(f.live[2]), D2PathRequest.findRoom(after.room(f.live[0]), 20, 0));
    assertNotSame(before.room(f.live[0]).bounds, after.room(f.live[0]).bounds);
  }

  @Test void removalUsesLiveTailSwapAndRejectsOldIdentityOnReallocation() {
    Fixture f = new Fixture();
    f.allocate(2);
    D2ActiveRoom removed = f.live[0];
    D2PathActiveRooms before = new D2PathActiveRooms(f.act);
    Dungeon.removeRoomFromAct(f.act, removed);
    assertThrows(IllegalStateException.class, before::requireCurrent);
    D2PathActiveRooms after = new D2PathActiveRooms(f.act);
    assertEquals(Arrays.asList(after.room(f.live[2]), after.room(f.live[1])), after.room(f.live[1]).nearby,
        "native tail swap differs from simply filtering RoomEx order");
    assertThrows(IllegalArgumentException.class, () -> after.room(removed));
    D2ActiveRoom replacement = f.allocate(0);
    assertNotSame(removed, replacement);
    assertEquals(removed.getNRoomId(), replacement.getNRoomId());
    D2PathActiveRooms rebuilt = new D2PathActiveRooms(f.act);
    assertThrows(IllegalArgumentException.class, () -> rebuilt.room(removed));
    assertDoesNotThrow(rebuilt::requireCurrent);
  }

  @Test void rawSubtileBoundsAreCopiedWithoutTileConversion() {
    Fixture f = new Fixture();
    f.live[0].getCoords().setNSubtileX(-17);
    f.live[0].getCoords().setNSubtileWidth(7);
    D2PathActiveRooms graph = new D2PathActiveRooms(f.act);
    assertEquals(-17, graph.room(f.live[0]).bounds.x);
    assertEquals(7, graph.room(f.live[0]).bounds.width);
    f.live[0].getCoords().setNSubtileWidth(8);
    assertThrows(IllegalStateException.class, graph::requireCurrent);
    assertEquals(7, graph.room(f.live[0]).bounds.width);
  }

  @Test void detectsMetadataAndCollisionGridReplacementButNotBitsOrSeedChanges() {
    Fixture f = new Fixture();
    D2PathActiveRooms graph = new D2PathActiveRooms(f.act);
    D2CommonCollision.setMask(f.live[0], 2, 2, 0x80);
    f.live[0].getSeed().setSeeds(123, 666);
    assertDoesNotThrow(graph::requireCurrent);
    f.live[0].setPCollisionGrid(null);
    assertThrows(IllegalStateException.class, graph::requireCurrent);
    D2PathActiveRooms noGrid = new D2PathActiveRooms(f.act);
    f.drlg[0].getLevel().setLevelId(2);
    assertThrows(IllegalStateException.class, noGrid::requireCurrent);
  }

  @Test void rejectsForeignNearRoomAndBrokenOwnershipAndCycles() {
    Fixture f = new Fixture();
    f.live[0].setPpRoomList(new D2ActiveRoom[] {new D2ActiveRoom()});
    f.live[0].setNNumRooms(1);
    assertThrows(IllegalArgumentException.class, () -> new D2PathActiveRooms(f.act));
    f.live[0].setNNumRooms(0);
    f.live[0].setAct(new D2DrlgAct());
    assertThrows(IllegalArgumentException.class, () -> new D2PathActiveRooms(f.act));
    f.live[0].setAct(f.act);
    f.live[0].setRoomNext(f.live[1]);
    assertThrows(IllegalArgumentException.class, () -> new D2PathActiveRooms(f.act));
  }

  @Test void detectsDetachedBacklinkAndProjectionListReplacement() {
    Fixture f = new Fixture();
    D2PathActiveRooms graph = new D2PathActiveRooms(f.act);
    graph.room(f.live[0]).nearby = Collections.emptyList();
    assertThrows(IllegalStateException.class, graph::requireCurrent);
    D2PathActiveRooms fresh = new D2PathActiveRooms(f.act);
    f.drlg[0].setRoom(null);
    assertThrows(IllegalStateException.class, fresh::requireCurrent);
    assertThrows(IllegalArgumentException.class, () -> new D2PathActiveRooms(f.act));
  }

  private static D2PathFootprint.Unit owner(D2ActiveRoom room) {
    return D2PathFootprint.Unit.dynamic(D2PathUnits.Unit.dynamic(0, 1,
        new D2PathUnits.DynamicPath(D2PathStep.toFp16Center(2), D2PathStep.toFp16Center(2), 0)),
        room, 1, 1, 0x80);
  }
  private static D2PathRequest.State request(D2PathRequest.Room room) {
    D2PathContinuation.State path = new D2PathContinuation.State(0);
    path.currentPoint = new D2Path.Point(2, 2);
    D2PathResult.State result = new D2PathResult.State(path);
    result.room = room.bounds;
    return new D2PathRequest.State(result, null, room, false, 0, 1, 1, 1, 0);
  }
  private static D2PathFootprint.RequestEffects effects(D2PathRequest.State state,
      D2PathFootprint.Unit owner, D2PathActiveRooms graph) {
    return new D2PathFootprint.RequestEffects(state, owner, null, graph) {
      @Override public int search(D2PathRequest.Search search) { return 0; }
    };
  }

  @Test void effectsRequireExactRoomIdentityEvenWithIdenticalBounds() {
    Fixture f = new Fixture();
    D2PathActiveRooms graph = new D2PathActiveRooms(f.act);
    D2PathRequest.Room actual = graph.room(f.live[0]);
    D2PathRequest.Room impostor = new D2PathRequest.Room(actual.bounds, actual.town);
    assertThrows(IllegalArgumentException.class, () -> effects(request(impostor), owner(f.live[0]), graph));
    assertDoesNotThrow(() -> effects(request(actual), owner(f.live[0]), graph));
  }

  @Test void effectsRejectStaleTopologyBeforeFootprintMutation() {
    Fixture f = new Fixture();
    D2PathActiveRooms graph = new D2PathActiveRooms(f.act);
    D2PathRequest.State state = request(graph.room(f.live[0]));
    D2PathFootprint.Unit owner = owner(f.live[0]);
    D2PathFootprint.add(owner);
    assertEquals(0x80, D2CommonCollision.checkMask(f.live[0], 2, 2, 0x80));
    D2PathFootprint.RequestEffects effects = effects(state, owner, graph);
    f.allocate(2);
    assertThrows(IllegalStateException.class, () -> effects.removeOwner(state));
    assertEquals(0x80, D2CommonCollision.checkMask(f.live[0], 2, 2, 0x80));
    assertThrows(IllegalStateException.class, () -> effects(state, owner, graph));
  }

  @Test void emptyActAndNullRoomRemainValidUntilAllocation() {
    D2DrlgAct act = new D2DrlgAct();
    D2PathActiveRooms graph = new D2PathActiveRooms(act);
    assertNull(graph.room(null));
    assertDoesNotThrow(graph::requireCurrent);
    act.setRoom(new D2ActiveRoom());
    assertThrows(IllegalStateException.class, graph::requireCurrent);
  }

  @Test void composedRequestSearchesAcrossLiveSeamAndRestoresFootprint() {
    Fixture f = new Fixture();
    D2PathActiveRooms graph = new D2PathActiveRooms(f.act);
    D2PathRequest.State state = request(graph.room(f.live[0]));
    state.result.path.targetPoint = new D2Path.Point(12, 2);
    state.result.path.pathType = 2;
    state.result.path.setNewDistance(20);
    state.result.velocity = 256;
    D2PathFootprint.Unit owner = owner(f.live[0]);
    D2PathFootprint.add(owner);
    D2PathDispatch.Effects effects = new D2PathDispatch.Effects(state, owner, null, graph);
    assertEquals(1, D2PathRequest.compute(state, false, effects));
    assertEquals(new D2Path.Point(12, 2), state.result.points[state.result.path.pathPointCount - 1]);
    assertSame(graph.room(f.live[0]).bounds, state.result.room);
    assertEquals(0x80, D2CommonCollision.checkMask(f.live[0], 2, 2, 0x80));
    assertDoesNotThrow(graph::requireCurrent);
  }
}
