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
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Objects;
import com.riiablo.codec.excel.Missiles;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativePathUnitMetadataTest {
  @Test void capturedRuntimeReviveFlagDrivesTownAndPetPatternWithoutProvenanceGuesses() {
    World w = new World();
    try {
      int id = create(w, Class.Type.MON);
      Monster monster = w.getMapper(Monster.class).create(id);
      monster.monstats = new MonStats.Entry(); monster.monstats2 = new MonStats2.Entry();
      com.riiablo.engine.server.component.NativeUnitFlags flags =
          w.getMapper(com.riiablo.engine.server.component.NativeUnitFlags.class).create(id).reset();
      monster.setResurrected(42, true); monster.monstats2.revive = true;
      flags.set(com.riiablo.engine.server.component.NativeUnitFlags.IS_RESURRECT
          | com.riiablo.engine.server.component.NativeUnitFlags.MERCENARY);
      NativePathUnitMetadata ordinary = NativePathUnitMetadata.read(w, id);
      assertFalse(ordinary.ownerCanBeInTown()); assertEquals(1, ordinary.collisionPatternFromSize(2));
      flags.set(com.riiablo.engine.server.component.NativeUnitFlags.IS_REVIVE);
      NativePathUnitMetadata revived = NativePathUnitMetadata.read(w, id);
      assertTrue(revived.ownerCanBeInTown()); assertEquals(3, revived.collisionPatternFromSize(2));
      assertEquals(4, revived.collisionPatternFromSize(3));
      flags.clear(com.riiablo.engine.server.component.NativeUnitFlags.IS_REVIVE);
      assertTrue(revived.ownerCanBeInTown()); assertFalse(ordinary.ownerCanBeInTown());
      assertFalse(NativePathUnitMetadata.read(w, id).ownerCanBeInTown());
    } finally { w.dispose(); }
  }

  @Test void runtimeFlagDoesNotReplaceMissingMonStatsAndMissingFlagsAreNotZero() {
    World w = new World();
    try {
      int id = create(w, Class.Type.MON);
      Monster monster = w.getMapper(Monster.class).create(id); monster.monstats2 = new MonStats2.Entry();
      monster.monstats = new MonStats.Entry();
      NativePathUnitMetadata unknown = NativePathUnitMetadata.read(w, id);
      assertThrows(IllegalArgumentException.class, unknown::ownerCanBeInTown);
      assertThrows(IllegalArgumentException.class, () -> unknown.collisionPatternFromSize(2));
      assertEquals(1, unknown.collisionPatternFromSize(255));
      // Explicit callers can still supply independently verified runtime metadata.
      assertTrue(unknown.ownerCanBeInTown(true));
      w.getMapper(com.riiablo.engine.server.component.NativeUnitFlags.class).create(id)
          .set(com.riiablo.engine.server.component.NativeUnitFlags.IS_REVIVE);
      monster.monstats = null;
      NativePathUnitMetadata missingTable = NativePathUnitMetadata.read(w, id);
      assertThrows(IllegalArgumentException.class, missingTable::ownerCanBeInTown);
      assertThrows(IllegalArgumentException.class, () -> missingTable.collisionPatternFromSize(2));
    } finally { w.dispose(); }
  }

  @Test void capturedTownFlagsAndInteractStillSelectRuntimePattern() {
    World w = new World();
    try {
      int id = create(w, Class.Type.MON);
      Monster monster = w.getMapper(Monster.class).create(id);
      monster.monstats = new MonStats.Entry(); monster.monstats2 = new MonStats2.Entry();
      w.getMapper(com.riiablo.engine.server.component.NativeUnitFlags.class).create(id).reset();
      monster.monstats.npc = true;
      assertTrue(NativePathUnitMetadata.read(w, id).ownerCanBeInTown());
      assertEquals(3, NativePathUnitMetadata.read(w, id).collisionPatternFromSize(2));
      monster.monstats.interact = true;
      assertEquals(1, NativePathUnitMetadata.read(w, id).collisionPatternFromSize(2));
      NativePathUnitMetadata player = NativePathUnitMetadata.read(w, create(w, Class.Type.PLR));
      assertTrue(player.ownerCanBeInTown()); assertEquals(1, player.collisionPatternFromSize(2));
    } finally { w.dispose(); }
  }

  @Test void populatedWarpRoutingDoesNotAuthorizeNativeTileOrObjectClassification() {
    World w = new World();
    try {
      int id = create(w, Class.Type.WRP);
      com.riiablo.engine.server.component.Warp warp =
          w.getMapper(com.riiablo.engine.server.component.Warp.class).create(id);
      warp.warp = new com.riiablo.codec.excel.LvlWarp.Entry();
      warp.dstLevel = new com.riiablo.codec.excel.Levels.Entry();
      for (int index : new int[] {0, 0x01020304,
          com.riiablo.engine.server.quest.QuestWarp.encode(1)}) {
        warp.index = index;
        assertThrows(IllegalArgumentException.class, () -> NativePathUnitMetadata.read(w, id));
        assertThrows(IllegalArgumentException.class,
            () -> NativePathAllocationCollision.read(w, id, false));
      }
      warp.townPortalOwner = 42; warp.linkedTownPortal = 43;
      // Even a resolved object row alongside routing data cannot override Class provenance.
      Object object = w.getMapper(Object.class).create(id);
      object.base = new Objects.Entry(); object.base.SizeX = 3; object.base.SizeY = 2;
      assertThrows(IllegalArgumentException.class, () -> NativePathUnitMetadata.read(w, id));
    } finally { w.dispose(); }
  }

  @Test void ambiguousWarpCannotEscapeThroughLifetimeBoundObservation() {
    World w = new World();
    try {
      int owner = create(w, Class.Type.PLR), target = create(w, Class.Type.WRP);
      w.getMapper(NativePathIdentity.class).create(owner).allocated(w, owner);
      w.getMapper(NativePathIdentity.class).create(target).allocated(w, target);
      w.getMapper(com.riiablo.engine.server.component.Warp.class).create(target)
          .index = com.riiablo.engine.server.quest.QuestWarp.encode(2);
      NativePathUnitBinding binding = new NativePathUnitBinding(w, owner, target, (world, id) -> {
        NativePathUnitMetadata metadata = NativePathUnitMetadata.read(world, id);
        return id == owner ? metadata.dynamicUnit(new D2PathUnits.DynamicPath(0, 0, 0))
            : metadata.staticUnit(p(20, 30));
      });
      assertThrows(IllegalArgumentException.class, () -> binding.observe(0));
      // Rejection leaves the valid owner usable; no guessed target snapshot is returned.
      assertEquals(0, NativePathUnitMetadata.read(w, owner).type);
    } finally { w.dispose(); }
  }

  @Test void monsterSizeBytesRejectEachInvalidAxisWithoutTruncation() {
    World w = new World();
    try {
      int id = create(w, Class.Type.MON);
      Monster m = w.getMapper(Monster.class).create(id); m.monstats2 = new MonStats2.Entry();
      for (int invalid : new int[] {-1, 256, 257, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
        m.monstats2.SizeX = invalid; m.monstats2.SizeY = 2;
        assertThrows(IllegalArgumentException.class, () -> NativePathUnitMetadata.read(w, id));
        m.monstats2.SizeX = 2; m.monstats2.SizeY = invalid;
        assertThrows(IllegalArgumentException.class, () -> NativePathUnitMetadata.read(w, id));
      }
      m.monstats2.SizeX = 0; m.monstats2.SizeY = 255; check(w, id, 1, 0, 255);
      m.monstats2.SizeX = 255; m.monstats2.SizeY = 0; check(w, id, 1, 255, 0);
    } finally { w.dispose(); }
  }
  @Test void missileSizeByteRejectsInvalidValuesAndPreservesEndpoints() {
    World w = new World();
    try {
      int id = create(w, Class.Type.MIS);
      Missile m = w.getMapper(Missile.class).create(id); m.missile = new Missiles.Entry();
      for (int invalid : new int[] {-1, 256, 257, Integer.MAX_VALUE}) {
        m.missile.Size = invalid;
        assertThrows(IllegalArgumentException.class, () -> NativePathUnitMetadata.read(w, id));
      }
      m.missile.Size = 0; check(w, id, 3, 0, 0);
      m.missile.Size = 255; check(w, id, 3, 255, 255);
    } finally { w.dispose(); }
  }
  @Test void oversizedButValidNativeSizeByteKeepsPatternFallbackSeparateFromMetadataValidation() {
    World w = new World();
    try {
      int id = create(w, Class.Type.MON);
      Monster m = w.getMapper(Monster.class).create(id); m.monstats2 = new MonStats2.Entry();
      m.monstats2.SizeX = 255;
      NativePathUnitMetadata metadata = NativePathUnitMetadata.read(w, id);
      assertEquals(255, metadata.sizeX);
      assertEquals(1, metadata.collisionPatternFromSize(metadata.sizeX, false));
    } finally { w.dispose(); }
  }
  @Test void realMonsterTableDrivesRuntimePatternDerivationWithoutResurrectionGuess() {
    World w = new World();
    try {
      int id = create(w, Class.Type.MON);
      Monster m = w.getMapper(Monster.class).create(id);
      m.monstats2 = new MonStats2.Entry(); m.monstats = new MonStats.Entry();
      m.monstats.npc = true;
      NativePathUnitMetadata old = NativePathUnitMetadata.read(w, id);
      assertEquals(3, old.collisionPatternFromSize(2, false));
      m.monstats.interact = true;
      assertEquals(2, NativePathUnitMetadata.read(w, id).collisionPatternFromSize(3, false));
      assertEquals(4, old.collisionPatternFromSize(3, false));
      m.monstats.npc = false; m.monstats.interact = false; m.setResurrected(42, true);
      assertEquals(1, NativePathUnitMetadata.read(w, id).collisionPatternFromSize(2, false));
      assertEquals(3, NativePathUnitMetadata.read(w, id).collisionPatternFromSize(2, true));
    } finally { w.dispose(); }
  }
  @Test void invalidSizePatternBypassesUnknownTownDataAsNativeEarlyReturnDoes() {
    World w = new World();
    try {
      int id = create(w, Class.Type.MON);
      w.getMapper(Monster.class).create(id).monstats2 = new MonStats2.Entry();
      NativePathUnitMetadata m = NativePathUnitMetadata.read(w, id);
      assertEquals(1, m.collisionPatternFromSize(-1, true));
      assertEquals(1, m.collisionPatternFromSize(4, true));
      assertThrows(IllegalArgumentException.class, () -> m.collisionPatternFromSize(2, true));
    } finally { w.dispose(); }
  }
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

  @Test void townEligibilityUsesTableFlagsAndExplicitRuntimeFlag() {
    World w = new World();
    try {
      int id = create(w, Class.Type.MON);
      Monster m = w.getMapper(Monster.class).create(id);
      m.monstats2 = new MonStats2.Entry(); m.monstats = new MonStats.Entry();
      for (boolean npc : new boolean[] {false, true}) {
        for (boolean town : new boolean[] {false, true}) {
          m.monstats.npc = npc; m.monstats.inTown = town;
          NativePathUnitMetadata metadata = NativePathUnitMetadata.read(w, id);
          assertEquals(npc || town, metadata.ownerCanBeInTown(false));
          assertTrue(metadata.ownerCanBeInTown(true));
        }
      }
    } finally { w.dispose(); }
  }

  @Test void resurrectionProvenanceDoesNotInventNativeReviveFlag() {
    World w = new World();
    try {
      int id = create(w, Class.Type.MON);
      Monster m = w.getMapper(Monster.class).create(id);
      m.monstats2 = new MonStats2.Entry(); m.monstats = new MonStats.Entry();
      m.setResurrected(42, true);
      assertFalse(NativePathUnitMetadata.read(w, id).ownerCanBeInTown(false));
      m.playerRevive = false;
      assertFalse(NativePathUnitMetadata.read(w, id).ownerCanBeInTown(false));
    } finally { w.dispose(); }
  }

  @Test void townMetadataIsFrozenAndMissingHostRowIsUnknown() {
    World w = new World();
    try {
      int id = create(w, Class.Type.MON);
      Monster m = w.getMapper(Monster.class).create(id); m.monstats2 = new MonStats2.Entry();
      NativePathUnitMetadata unknown = NativePathUnitMetadata.read(w, id);
      assertThrows(IllegalArgumentException.class, () -> unknown.ownerCanBeInTown(true));
      m.monstats = new MonStats.Entry(); m.monstats.npc = true;
      NativePathUnitMetadata old = NativePathUnitMetadata.read(w, id);
      m.monstats.npc = false;
      assertTrue(old.ownerCanBeInTown(false));
      assertFalse(NativePathUnitMetadata.read(w, id).ownerCanBeInTown(false));
      assertThrows(IllegalArgumentException.class, () -> unknown.ownerCanBeInTown(false));
    } finally { w.dispose(); }
  }

  @Test void nonMonsterOwnersHaveNoMonsterTownRestriction() {
    World w = new World();
    try {
      for (Class.Type type : new Class.Type[] {Class.Type.PLR, Class.Type.ITM}) {
        NativePathUnitMetadata m = NativePathUnitMetadata.read(w, create(w, type));
        assertTrue(m.ownerCanBeInTown(false));
      }
    } finally { w.dispose(); }
  }
}
