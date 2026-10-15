package com.riiablo.engine.server;

import com.artemis.World;
import com.d2moo.common.path.D2Path;
import com.d2moo.common.path.D2PathUnits;
import com.riiablo.engine.server.component.NativePathIdentity;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Object;
import com.riiablo.engine.server.component.Size;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.codec.excel.Objects;
import com.riiablo.codec.excel.Missiles;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativePathUnitMetadataTest {
  private D2Path.Point p(int x, int y) { return new D2Path.Point(x, y); }
  private int create(World w, Class.Type type) {
    int id = w.create();
    w.getMapper(Class.class).create(id).type = type;
    w.process();
    return id;
  }
  private void check(World w, int id, int type, int x, int y) {
    NativePathUnitMetadata m = NativePathUnitMetadata.read(w, id);
    assertEquals(type, m.type); assertEquals(x, m.sizeX); assertEquals(y, m.sizeY);
  }
  @Test void mapsPlayerAndItemWithoutUsingHostEnumOrdinals() {
    World w = new World();
    try { check(w, create(w, Class.Type.PLR), 0, 2, 2);
      check(w, create(w, Class.Type.ITM), 4, 1, 1);
    } finally { w.dispose(); }
  }
  @Test void monsterUsesIndependentTableDimensionsAndSnapshot() {
    World w = new World();
    try {
      int id = create(w, Class.Type.MON);
      Monster m = w.getMapper(Monster.class).create(id);
      m.monstats2 = new MonStats2.Entry(); m.monstats2.SizeX = 4; m.monstats2.SizeY = 7;
      w.getMapper(Size.class).create(id);
      NativePathUnitMetadata old = NativePathUnitMetadata.read(w, id);
      check(w, id, 1, 4, 7);
      m.monstats2.SizeX = 0;
      assertEquals(4, old.sizeX); check(w, id, 1, 0, 7);
    } finally { w.dispose(); }
  }
  @Test void objectPreservesFullIntegerDimensions() {
    World w = new World();
    try {
      int id = create(w, Class.Type.OBJ);
      Object o = w.getMapper(Object.class).create(id); o.base = new Objects.Entry();
      o.base.SizeX = 300; o.base.SizeY = 513;
      check(w, id, 2, 300, 513);
    } finally { w.dispose(); }
  }
  @Test void missileUsesTableSizeForBothAxes() {
    World w = new World();
    try {
      int id = create(w, Class.Type.MIS);
      Missile m = w.getMapper(Missile.class).create(id); m.missile = new Missiles.Entry();
      m.missile.Size = 5; check(w, id, 3, 5, 5);
    } finally { w.dispose(); }
  }
  @Test void rejectsUnknownRowsClassesWarpsAndDeletedEntities() {
    World w = new World();
    try {
      for (Class.Type type : new Class.Type[] {Class.Type.MON, Class.Type.OBJ, Class.Type.MIS, Class.Type.WRP}) {
        int id = create(w, type);
        assertThrows(IllegalArgumentException.class, () -> NativePathUnitMetadata.read(w, id));
      }
      int id = create(w, Class.Type.PLR);
      w.getMapper(Class.class).get(id).type = null;
      assertThrows(IllegalArgumentException.class, () -> NativePathUnitMetadata.read(w, id));
      w.delete(id); w.process();
      assertThrows(IllegalArgumentException.class, () -> NativePathUnitMetadata.read(w, id));
      assertThrows(IllegalArgumentException.class, () -> NativePathUnitMetadata.read(w, -1));
    } finally { w.dispose(); }
  }

  @Test void doorOrientationIsExactOneAndOffsetsBothSides() {
    World w = new World();
    try {
      int id = create(w, Class.Type.OBJ);
      Object o = w.getMapper(Object.class).create(id); o.base = new Objects.Entry();
      o.base.IsDoor = true; o.base.SizeX = 2;
      for (int orientation : new int[] {0, 1, 2, 255}) {
        o.base.Orientation = orientation;
        NativePathUnitMetadata m = NativePathUnitMetadata.read(w, id);
        assertTrue(m.door); assertEquals(orientation == 1, m.orientationIsOne);
        D2PathUnits.Unit u = m.staticUnit(p(20, 30));
        assertEquals(orientation == 1 ? p(20, 32) : p(22, 30),
            D2PathUnits.prepare(p(40, 40), p(1, 1), u, 0).target);
        assertEquals(orientation == 1 ? p(20, 28) : p(18, 30),
            D2PathUnits.prepare(p(10, 10), p(1, 1), u, 0).target);
      }
    } finally { w.dispose(); }
  }

  @Test void ordinaryObjectsNeverUseDoorOffsetAndMetadataIsFrozen() {
    World w = new World();
    try {
      int id = create(w, Class.Type.OBJ);
      Object o = w.getMapper(Object.class).create(id); o.base = new Objects.Entry();
      o.base.Orientation = 1;
      NativePathUnitMetadata old = NativePathUnitMetadata.read(w, id);
      o.base.IsDoor = true; o.base.Orientation = 2;
      assertFalse(old.door); assertTrue(old.orientationIsOne);
      assertEquals(p(20, 30), D2PathUnits.prepare(p(40, 40), p(1, 1),
          old.staticUnit(p(20, 30)), 0).target);
      assertEquals(p(22, 30), D2PathUnits.prepare(p(40, 40), p(1, 1),
          NativePathUnitMetadata.read(w, id).staticUnit(p(20, 30)), 0).target);
    } finally { w.dispose(); }
  }

  @Test void staticCompositionRetainsRawCoordinatesAndRequiresExplicitLeadDirection() {
    World w = new World();
    try {
      int id = create(w, Class.Type.OBJ);
      Object o = w.getMapper(Object.class).create(id); o.base = new Objects.Entry(); o.base.IsDoor = true;
      NativePathUnitMetadata m = NativePathUnitMetadata.read(w, id);
      D2PathUnits.Unit u = m.staticUnit(p(65540, -3));
      assertEquals(p(65540, -3), D2PathUnits.coordinates(u));
      assertEquals(p(4, 65533), D2PathUnits.sample(u, 0));
      assertThrows(IllegalArgumentException.class, () -> D2PathUnits.sample(u, 1));
      assertEquals(D2PathUnits.sample(D2PathUnits.Unit.door(0, p(65540, -3), false, 19), 3),
          D2PathUnits.sample(m.staticUnit(p(65540, -3), 19), 3));
      assertThrows(IllegalArgumentException.class, () -> m.staticUnit(p(1, 1), 256));
      assertThrows(IllegalArgumentException.class, () -> m.dynamicUnit(null));
    } finally { w.dispose(); }
  }

  @Test void dynamicCompositionPreservesPrecisionAndMissingPathSemantics() {
    World w = new World();
    try {
      NativePathUnitMetadata m = NativePathUnitMetadata.read(w, create(w, Class.Type.PLR));
      D2PathUnits.DynamicPath raw = new D2PathUnits.DynamicPath(0xfffe1234, 0x8001abcd, 255);
      assertEquals(p(65534, 32769), D2PathUnits.coordinates(m.dynamicUnit(raw)));
      assertEquals(p(0, 0), D2PathUnits.coordinates(m.dynamicUnit(null)));
      assertThrows(IllegalArgumentException.class, () -> m.staticUnit(p(1, 1)));
    } finally { w.dispose(); }
  }

  @Test void invalidOrientationIsNotSilentlyNarrowed() {
    World w = new World();
    try {
      int id = create(w, Class.Type.OBJ);
      Object o = w.getMapper(Object.class).create(id); o.base = new Objects.Entry();
      for (int orientation : new int[] {-1, 256, 257}) {
        o.base.Orientation = orientation;
        assertThrows(IllegalArgumentException.class, () -> NativePathUnitMetadata.read(w, id));
      }
    } finally { w.dispose(); }
  }

  @Test void resolvedMetadataComposesInsideLifetimeBoundReader() {
    World w = new World();
    try {
      int owner = create(w, Class.Type.PLR), target = create(w, Class.Type.OBJ);
      w.getMapper(NativePathIdentity.class).create(owner).allocated(w, owner);
      w.getMapper(NativePathIdentity.class).create(target).allocated(w, target);
      Object o = w.getMapper(Object.class).create(target); o.base = new Objects.Entry();
      o.base.IsDoor = true; o.base.Orientation = 1; o.base.SizeX = 3;
      NativePathUnitBinding binding = new NativePathUnitBinding(w, owner, target, (world, id) -> {
        NativePathUnitMetadata m = NativePathUnitMetadata.read(world, id);
        return id == owner ? m.dynamicUnit(new D2PathUnits.DynamicPath(10 << 16, 10 << 16, 0))
            : m.staticUnit(p(20, 30));
      });
      D2PathUnits.Observation observation = binding.observe(0);
      assertEquals(3, observation.target.sizeX);
      assertEquals(p(20, 28), D2PathUnits.prepare(p(10, 10), p(1, 1), observation.target, 0).target);
      w.delete(target); w.process();
      assertThrows(IllegalStateException.class, observation::coordinates);
    } finally { w.dispose(); }
  }
}
