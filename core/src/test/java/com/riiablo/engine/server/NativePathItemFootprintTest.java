package com.riiablo.engine.server;

import com.artemis.World;
import com.d2moo.common.collision.D2CommonCollision;
import com.d2moo.common.drlg.D2ActiveRoom;
import com.d2moo.common.path.*;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.NativePathIdentity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativePathItemFootprintTest {
  private static D2Path.Point p(int x, int y) { return new D2Path.Point(x, y); }
  private static int create(World world, Class.Type type) {
    int id = world.create(); world.getMapper(Class.class).create(id).type = type;
    world.getMapper(NativePathIdentity.class).create(id).allocated(world, id);
    world.process(); return id;
  }
  private static D2ActiveRoom room() {
    D2ActiveRoom room = new D2ActiveRoom();
    room.getCoords().setNSubtileWidth(50); room.getCoords().setNSubtileHeight(50);
    D2CommonCollision.allocRoomCollisionGrid(room); return room;
  }

  @Test void rawStaticCoordinatesStayUnnarrowedAndDirectionRemainsExplicit() {
    World world = new World();
    try {
      int id = create(world, Class.Type.ITM);
      D2PathFootprint.Unit item = NativePathItemFootprint.read(world, id, p(65540, -3), null);
      assertEquals(4, item.coordinates.type); assertEquals(1, item.coordinates.sizeX);
      assertNull(item.room); assertEquals(0x200, D2PathFootprint.collisionMask(item));
      assertEquals(p(65540, -3), D2PathUnits.coordinates(item.coordinates));
      assertEquals(p(4, 65533), D2PathUnits.sample(item.coordinates, 0));
      assertThrows(IllegalArgumentException.class, () -> D2PathUnits.sample(item.coordinates, 1));
      D2PathFootprint.Unit directed = NativePathItemFootprint.read(world, id, p(10, 10), null, 19);
      assertEquals(D2PathUnits.sample(D2PathUnits.Unit.stationary(4, 1, p(10, 10), 19), 3),
          D2PathUnits.sample(directed.coordinates, 3));
      // Request target preparation for items uses its own no-lead/tolerance-2 rule.
      D2PathUnits.Prepared prepared = D2PathUnits.prepare(p(1, 1), p(2, 2), item.coordinates, 3);
      assertEquals(p(4, 65533), prepared.target); assertEquals(2, prepared.tolerance);
    } finally { world.dispose(); }
  }

  @Test void unresolvedOrWrongClassAndInvalidRawDirectionAreRejected() {
    World world = new World();
    try {
      for (Class.Type type : new Class.Type[] {Class.Type.PLR, Class.Type.WRP}) {
        int id = create(world, type);
        assertThrows(IllegalArgumentException.class,
            () -> NativePathItemFootprint.read(world, id, p(1, 1), null));
      }
      int id = create(world, Class.Type.ITM);
      assertThrows(IllegalArgumentException.class,
          () -> NativePathItemFootprint.read(world, id, p(1, 1), null, 256));
      assertThrows(NullPointerException.class, () -> NativePathItemFootprint.read(world, id, null, null));
      world.delete(id); world.process();
      assertThrows(IllegalArgumentException.class,
          () -> NativePathItemFootprint.read(world, id, p(1, 1), null));
    } finally { world.dispose(); }
  }

  @Test void itemUsesOneCellAndPreservesOtherCollisionBits() {
    World world = new World();
    try {
      int id = create(world, Class.Type.ITM); D2ActiveRoom room = room();
      D2CommonCollision.setMask(room, 20, 10, 0x4000);
      int[] before = room.getPCollisionGrid().getPCellsFlags().clone();
      D2PathFootprint.Unit item = NativePathItemFootprint.read(world, id, p(20, 10), room);
      D2PathFootprint.add(item);
      assertEquals(0x4200, D2CommonCollision.checkMask(room, 20, 10, 0xffff));
      assertEquals(0, D2CommonCollision.checkMask(room, 19, 10, 0xffff));
      assertTrue(D2PathFootprint.remove(item, false));
      assertArrayEquals(before, room.getPCollisionGrid().getPCellsFlags());
    } finally { world.dispose(); }
  }

  @Test void boundItemTargetIsRemovedDuringSearchRestoredAndRejectsReplacement() {
    World world = new World();
    try {
      int owner = create(world, Class.Type.PLR), target = create(world, Class.Type.ITM);
      world.getMapper(CofReference.class).create(owner).mode = 1;
      D2ActiveRoom room = room();
      NativePathUnitBinding binding = new NativePathUnitBinding(world, owner, target,
          (w, id) -> { fail("coordinate reader must not run"); return null; });
      NativePathUnitBinding.FootprintObservation pair = binding.observeFootprints(0, (w, id) ->
          id == owner ? NativePathDynamicFootprint.read(w, id,
              new D2PathUnits.DynamicPath(10 << 16, 10 << 16, 0), room, 1, 0x80)
              : NativePathItemFootprint.read(w, id, p(20, 10), room));
      assertSame(pair.target.coordinates, pair.observation.target);
      D2PathFootprint.add(pair.owner); D2PathFootprint.add(pair.target);
      int[] before = room.getPCollisionGrid().getPCellsFlags().clone();
      D2PathContinuation.State path = new D2PathContinuation.State(0);
      path.currentPoint = p(10, 10); path.targetPoint = p(20, 10); path.targetUnit = pair.observation;
      path.pathType = 2; path.pathFlags = 0x800; path.setNewDistance(30);
      D2PathResult.State result = new D2PathResult.State(path);
      result.precisionX = 10 << 16; result.precisionY = 10 << 16; result.velocity = 256;
      result.room = new D2PathResult.Room(0, 0, 50, 50);
      D2PathRequest.State request = new D2PathRequest.State(result, pair.observation.target,
          new D2PathRequest.Room(result.room, false), false, 0, 2, 1, 1, 70);
      D2PathFootprint.RequestEffects effects = new D2PathFootprint.RequestEffects(request, pair.owner, pair.target) {
        public int search(D2PathRequest.Search search) {
          assertEquals(0, D2CommonCollision.checkMask(room, 10, 10, 0x80));
          assertEquals(0, D2CommonCollision.checkMask(room, 20, 10, 0x200));
          assertEquals(2, search.tolerance);
          return D2PathDispatch.compute(search, (x, y) -> blocked(request, x, y));
        }
      };
      assertTrue(D2PathRequest.compute(request, false, effects) > 0);
      assertArrayEquals(before, room.getPCollisionGrid().getPCellsFlags());
      world.getMapper(NativePathIdentity.class).get(target).allocated(world, target);
      request.collidedMask = 123;
      assertThrows(IllegalStateException.class, () -> D2PathRequest.compute(request, false, effects));
      assertEquals(123, request.collidedMask);
      assertArrayEquals(before, room.getPCollisionGrid().getPCellsFlags());
    } finally { world.dispose(); }
  }
}
