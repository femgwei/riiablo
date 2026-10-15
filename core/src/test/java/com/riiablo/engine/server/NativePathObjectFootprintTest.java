package com.riiablo.engine.server;

import com.artemis.World;
import com.d2moo.common.path.*;
import com.d2moo.common.drlg.*;
import com.d2moo.common.collision.D2CommonCollision;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Object;
import com.riiablo.codec.excel.Objects;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativePathObjectFootprintTest {
  private static D2Path.Point p(int x, int y) { return new D2Path.Point(x, y); }
  private static class Fixture implements AutoCloseable {
    final World world = new World();
    final int id = world.create();
    final Object object = world.getMapper(Object.class).create(id);
    final Objects.Entry row = new Objects.Entry();
    Fixture() {
      world.getMapper(Class.class).create(id).type = Class.Type.OBJ;
      object.base = row; row.SizeX = 2; row.SizeY = 3; row.HasCollision = new boolean[8];
      world.process();
    }
    D2PathFootprint.Unit read() { return NativePathObjectFootprint.read(world, id, p(10, 10), null); }
    public void close() { world.dispose(); }
  }
  @Test void realTablePredicatesSelectNativeMaskPriority() {
    try (Fixture f = new Fixture()) {
      assertEquals(0x400, D2PathFootprint.collisionMask(f.read()));
      f.row.BlockMissile = true;
      assertEquals(0x404, D2PathFootprint.collisionMask(f.read()));
      f.row.SubClass = 4;
      assertEquals(0x8000, D2PathFootprint.collisionMask(f.read()));
      f.row.IsDoor = true;
      assertEquals(0x804, D2PathFootprint.collisionMask(f.read()));
      f.row.BlocksVis = true;
      assertEquals(0x806, D2PathFootprint.collisionMask(f.read()));
      f.row.BlockMissile = false; f.row.BlocksVis = false;
      assertEquals(0x400, D2PathFootprint.collisionMask(f.read()));
    }
  }
  @Test void authoritativeObjectModeSelectsAllEightCollisionEntries() {
    try (Fixture f = new Fixture()) {
      for (int i = 0; i < 8; i++) f.row.HasCollision[i] = (i & 1) != 0;
      for (int i = 0; i < 8; i++) {
        f.object.mode = (byte) i;
        assertEquals((i & 1) != 0, D2PathFootprint.hasCollision(f.read()));
      }
    }
  }
  @Test void snapshotFreezesRowModeAndDimensions() {
    try (Fixture f = new Fixture()) {
      f.row.HasCollision[0] = true;
      D2PathFootprint.Unit old = f.read();
      f.row.HasCollision[0] = false; f.row.BlockMissile = true;
      f.row.SizeY = 9; f.object.mode = 7;
      assertTrue(D2PathFootprint.hasCollision(old)); assertEquals(3, old.sizeY);
      assertEquals(0x400, D2PathFootprint.collisionMask(old));
      assertEquals(0, old.mode); assertEquals(9, f.read().sizeY);
    }
  }
  @Test void invalidOrUnresolvedDataDoesNotFallBackToDefaults() {
    try (Fixture f = new Fixture()) {
      for (int mode : new int[] {8, 255}) {
        f.object.mode = (byte) mode;
        assertThrows(IllegalArgumentException.class, f::read);
      }
      f.object.mode = 0;
      f.row.HasCollision = null; assertThrows(IllegalArgumentException.class, f::read);
      f.row.HasCollision = new boolean[7]; assertThrows(IllegalArgumentException.class, f::read);
      f.row.HasCollision = new boolean[8]; f.row.SubClass = 256;
      assertThrows(IllegalArgumentException.class, f::read);
      f.row.SubClass = -1; assertThrows(IllegalArgumentException.class, f::read);
      f.world.getMapper(Class.class).get(f.id).type = Class.Type.PLR;
      assertThrows(IllegalArgumentException.class, f::read);
    }
  }
  @Test void coordinatesRemainRawAndDirectionIsExplicit() {
    try (Fixture f = new Fixture()) {
      D2PathFootprint.Unit u = NativePathObjectFootprint.read(f.world, f.id, p(65537, -2), null);
      assertEquals(p(65537, -2), D2PathUnits.coordinates(u.coordinates));
      assertThrows(IllegalArgumentException.class, () -> D2PathUnits.sample(u.coordinates, 2));
      D2PathFootprint.Unit directed = NativePathObjectFootprint.read(f.world, f.id, p(10, 10), null, 19);
      assertNotNull(D2PathUnits.sample(directed.coordinates, 2));
    }
  }
  @Test void actualRoomGridRoundTripHonorsHasCollisionAndForce() {
    try (Fixture f = new Fixture()) {
      D2ActiveRoom room = new D2ActiveRoom();
      D2DrlgCoords c = new D2DrlgCoords(); c.setNSubtileWidth(24); c.setNSubtileHeight(24);
      room.setCoords(c); D2CommonCollision.allocRoomCollisionGrid(room);
      D2PathFootprint.Unit u = NativePathObjectFootprint.read(f.world, f.id, p(10, 10), room);
      assertSame(room, u.room);
      D2PathFootprint.add(u);
      assertEquals(0x400, D2CommonCollision.checkMask(room, 10, 10, 0xffff));
      assertFalse(D2PathFootprint.remove(u, false));
      assertEquals(0x400, D2CommonCollision.checkMask(room, 10, 10, 0xffff));
      assertTrue(D2PathFootprint.remove(u, true));
      assertEquals(0, D2CommonCollision.checkMask(room, 10, 10, 0xffff));
    }
  }
}
