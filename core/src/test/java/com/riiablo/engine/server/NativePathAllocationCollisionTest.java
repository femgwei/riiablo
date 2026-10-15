package com.riiablo.engine.server;

import com.artemis.World;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.codec.excel.Missiles;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativePathAllocationCollisionTest {
  @Test void playerAndMissileAllocationMasksUseNativeConstants() {
    World w = new World();
    try {
      int id = w.create(); w.getMapper(Class.class).create(id).type = Class.Type.PLR; w.process();
      NativePathAllocationCollision player = NativePathAllocationCollision.read(w, id, false);
      assertEquals(1, player.pattern); assertEquals(0x80, player.footprintMask); assertEquals(0x1c09, player.moveMask);
      w.getMapper(Class.class).get(id).type = Class.Type.MIS;
      Missile m = w.getMapper(Missile.class).create(id); m.missile = new Missiles.Entry(); m.missile.Size = 3;
      NativePathAllocationCollision missile = NativePathAllocationCollision.read(w, id, false);
      assertEquals(2, missile.pattern); assertEquals(0, missile.footprintMask); assertEquals(0, missile.moveMask);
    } finally { w.dispose(); }
  }
  @Test void monsterPriorityIsWraithThenFlyingThenDoorOpeningAndSnapshotsStayFrozen() {
    World w = new World();
    try {
      int id = w.create(); w.getMapper(Class.class).create(id).type = Class.Type.MON;
      Monster m = w.getMapper(Monster.class).create(id);
      m.monstats = new MonStats.Entry(); m.monstats.BaseId = "fallen1";
      m.monstats2 = new MonStats2.Entry(); m.monstats2.SizeX = 3; w.process();
      NativePathAllocationCollision ordinary = NativePathAllocationCollision.read(w, id, false);
      assertEquals(0x3c01, ordinary.moveMask); assertEquals(0x100, ordinary.footprintMask);
      m.monstats.opendoors = true;
      assertEquals(0x3401, NativePathAllocationCollision.read(w, id, false).moveMask);
      m.monstats.flying = true;
      assertEquals(0x1804, NativePathAllocationCollision.read(w, id, false).moveMask);
      m.monstats.BaseId = "wraith1"; m.monstats.inTown = true;
      NativePathAllocationCollision ghost = NativePathAllocationCollision.read(w, id, false);
      assertEquals(5, ghost.pattern); assertEquals(0x804, ghost.moveMask);
      assertEquals(2, ordinary.pattern); assertEquals(0x3c01, ordinary.moveMask);
    } finally { w.dispose(); }
  }
  @Test void tablePetPatternAndMissingBaseMetadataAreHandledExplicitly() {
    World w = new World();
    try {
      int id = w.create(); w.getMapper(Class.class).create(id).type = Class.Type.MON;
      Monster m = w.getMapper(Monster.class).create(id);
      m.monstats = new MonStats.Entry(); m.monstats.BaseId = "fallen1"; m.monstats.npc = true;
      m.monstats2 = new MonStats2.Entry(); m.monstats2.SizeX = 2; w.process();
      assertEquals(3, NativePathAllocationCollision.read(w, id, false).pattern);
      m.monstats.interact = true;
      assertEquals(1, NativePathAllocationCollision.read(w, id, false).pattern);
      m.monstats.BaseId = null;
      assertThrows(IllegalArgumentException.class, () -> NativePathAllocationCollision.read(w, id, false));
      w.getMapper(Class.class).get(id).type = Class.Type.ITM;
      assertThrows(IllegalArgumentException.class, () -> NativePathAllocationCollision.read(w, id, false));
    } finally { w.dispose(); }
  }
}
