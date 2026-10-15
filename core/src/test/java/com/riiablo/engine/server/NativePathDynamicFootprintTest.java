package com.riiablo.engine.server;

import com.artemis.World;
import com.d2moo.common.path.*;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativePathDynamicFootprintTest {
  @Test void logicalModeSurvivesVisualOverrideAndRawFieldsStayUnnarrowed() {
    World w = new World();
    try {
      int id = w.create(); w.getMapper(Class.class).create(id).type = Class.Type.PLR;
      CofReference cof = w.getMapper(CofReference.class).create(id);
      cof.mode = (byte) 255; cof.visualType = Class.Type.MON; w.process();
      D2PathFootprint.Unit unit = NativePathDynamicFootprint.read(w, id,
          new D2PathUnits.DynamicPath(0xffff1234, 0x8001abcd, 19), null, 0x12345678, 0x87654321);
      assertEquals(0, unit.coordinates.type); assertEquals(2, unit.coordinates.sizeX);
      assertEquals(255, unit.mode); assertEquals(0x12345678, unit.pattern);
      assertEquals(0x87654321, D2PathFootprint.collisionMask(unit));
      assertEquals(new D2Path.Point(65535, 32769), D2PathUnits.coordinates(unit.coordinates));
      cof.mode = 1; assertEquals(255, unit.mode);
    } finally { w.dispose(); }
  }

  @Test void missingPathIsRetainedAndDeathModeRemovalUsesLogicalPlayerMode() {
    World w = new World();
    try {
      int id = w.create(); w.getMapper(Class.class).create(id).type = Class.Type.PLR;
      w.getMapper(CofReference.class).create(id).mode = 17; w.process();
      D2PathFootprint.Unit unit = NativePathDynamicFootprint.read(w, id, null, null, 1, 0x80);
      assertEquals(0xffff, D2PathFootprint.collisionMask(unit));
      assertFalse(D2PathFootprint.remove(unit, false));
      assertThrows(IllegalStateException.class, () -> D2PathFootprint.add(unit));
    } finally { w.dispose(); }
  }

  @Test void unknownModeAndStaticUnitsAreRejected() {
    World w = new World();
    try {
      int id = w.create(); w.getMapper(Class.class).create(id).type = Class.Type.PLR; w.process();
      assertThrows(IllegalArgumentException.class,
          () -> NativePathDynamicFootprint.read(w, id, null, null, 1, 0x80));
      w.getMapper(CofReference.class).create(id);
      w.getMapper(Class.class).get(id).type = Class.Type.ITM;
      assertThrows(IllegalArgumentException.class,
          () -> NativePathDynamicFootprint.read(w, id, null, null, 1, 0x80));
    } finally { w.dispose(); }
  }
}
