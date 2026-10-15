package com.riiablo.engine.server;

import com.artemis.World;
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
}
