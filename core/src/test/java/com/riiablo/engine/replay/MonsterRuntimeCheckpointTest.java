package com.riiablo.engine.replay;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.utils.Array;
import com.d2moo.common.drlg.D2Seed;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.engine.server.NativeUnitRandom;
import com.riiablo.engine.server.combat.NativeMonsterModeElements;
import com.riiablo.engine.server.component.*;
import com.riiablo.map.Map;
import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MonsterRuntimeCheckpointTest {
  static final Set<Integer> IDS = new HashSet<>(Arrays.asList(0, 1, 2));

  @Test void schemaAndEveryScalarRequireAnExplicitDecisionAndRestoreExactly() throws Exception {
    Set<String> fields = new HashSet<>(Arrays.asList(
        "monstats", "monstats2", "componentVariants", "rank", "championType", "affixes",
        "chargedBoltsCooldown", "fireDeathExplosionScheduled", "coldDeathNovaScheduled", "lightningEnchantedLastFrame", "uniqueId",
        "baalWaveIndex", "baalWaveSuperUniqueId", "baalWaveRoomId", "baalWaveLeader", "minionOwnerId",
        "nativeSpawnClassId", "nativeOwnerId", "nativeOwnerIdentity", "nativeRequestedMountId", "nativeRequestedMountIdentity",
        "nativeSelfResurrections", "nativeSelfResurrect", "nativeChargedBolts", "baalLinkedOwnerId", "baalClonesCreated",
        "rngState", "nativeSeed", "modeElements", "nativePackId", "nativePackLeader",
        "attack2MinDamage", "attack2MaxDamage", "attack2ToHit", "resurrected", "resurrectedBy", "playerRevive",
        "shatteredAtDeath", "frogNoPresence", "vultureAirborne", "nativeCorpseCollision", "nativeRestoredCollisionPattern",
        "frogSmallPresence", "frogSpawnMarkerInitialized", "frogSpawnMarkerMoved", "frogSpawnMarkerX", "frogSpawnMarkerY",
        "spawnZone", "spawnX", "spawnY", "hasSpawnAnchor", "converted", "conversionOwnerId"));
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      Set<String> actual = new HashSet<>(); int value = 100;
      for (Field field : Monster.class.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
        actual.add(field.getName());
        if (field.getType() == int.class) field.setInt(source.monster, value++);
        else if (field.getType() == long.class) field.setLong(source.monster, 0x98765432fedcba98L);
        else if (field.getType() == float.class) field.setFloat(source.monster, value++ + 0.00390625f);
        else if (field.getType() == double.class) field.setDouble(source.monster, 9007199254740001d);
        else if (field.getType() == boolean.class) field.setBoolean(source.monster, true);
      }
      assertEquals(fields, actual);
      assertEquals(16, source.monster.componentVariants.length, "wire layout requires an explicit version decision");
      for (int i = 0; i < 16; i++) source.monster.componentVariants[i] = i * 7;
      source.monster.nativeSeed = new D2Seed(source.monster.rngState, -877);
      source.monster.modeElements = elements();
      assertEquals(6, source.monster.modeElements.minimum.length);
      assertEquals(6, source.monster.modeElements.maximum.length);
      assertEquals(3, source.monster.modeElements.drainMinimum.length);
      assertEquals(3, source.monster.modeElements.drainMaximum.length);
      Set<String> elementFields = new HashSet<>();
      for (Field f : NativeMonsterModeElements.class.getDeclaredFields()) if (!Modifier.isStatic(f.getModifiers()) && !f.isSynthetic()) elementFields.add(f.getName());
      assertEquals(new HashSet<>(Arrays.asList("minimum", "maximum", "coldLength", "poisonLength", "matchedRows",
          "unsupportedType", "drainMinimum", "drainMaximum")), elementFields);
      MonsterRuntimeCheckpoint image = source.capture();
      image.restore(target.world, 0);
      for (Field field : Monster.class.getDeclaredFields()) if (!Modifier.isStatic(field.getModifiers()) && field.getType().isPrimitive()) {
        assertEquals(field.get(source.monster), field.get(target.monster), field.getName());
      }
      assertArrayEquals(source.monster.componentVariants, target.monster.componentVariants);
      assertNotSame(source.monster.modeElements, target.monster.modeElements);
      assertArrayEquals(source.monster.modeElements.drainMaximum, target.monster.modeElements.drainMaximum);
      assertArrayEquals(image.bytes(), target.capture().bytes());
      assertTrue(image.matchesSeed(source.monster.rngState, -877));
      assertNotSame(source.monster.nativeSeed, target.monster.nativeSeed);
    }
  }

  @Test void frozenElementsAndBothSeedWordsContinueWithoutReselectingOrDetachingTheLiveSeed() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      source.monster.modeElements = elements();
      source.monster.nativeSeed = new D2Seed(77, 12345); source.monster.rngState = 77;
      NativeUnitRandom retained = NativeUnitRandom.monster(target.monster, 1, 0);
      source.capture().restore(target.world, 0);
      assertEquals(77, retained.state()); assertEquals(12345, retained.high());
      assertEquals(source.monster.modeElements.matchedRows, target.monster.modeElements.matchedRows);
      NativeUnitRandom expected = NativeUnitRandom.monster(source.monster, 1, 0);
      for (int i = 0; i < 20; i++) assertEquals(expected.nextUnsigned(), retained.nextUnsigned());
      assertArrayEquals(source.capture().bytes(), target.capture().bytes());
      source.monster.modeElements = null; source.monster.nativeSeed = null; source.monster.rngState = 0;
      source.capture().restore(target.world, 0);
      assertNull(target.monster.modeElements); assertNull(target.monster.nativeSeed);
    }
  }

  @Test void ownerAndMountNullCurrentStaleLifetimesSurviveAndStillExpireOnReuse() {
    for (int owner = 0; owner < 3; owner++) for (int mount = 0; mount < 3; mount++) {
      try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
        source.monster.nativeOwnerId = 1; source.monster.nativeRequestedMountId = 2;
        source.monster.nativeOwnerIdentity = owner == 0 ? null : source.identity(1).token();
        source.monster.nativeRequestedMountIdentity = mount == 0 ? null : source.identity(2).token();
        if (owner == 2) source.identity(1).allocated(source.world, 1);
        if (mount == 2) source.identity(2).allocated(source.world, 2);
        MonsterRuntimeCheckpoint saved = source.capture(); saved.restore(target.world, 0);
        assertArrayEquals(saved.bytes(), target.capture().bytes());
        if (owner == 1) assertSame(target.identity(1).token(), target.monster.nativeOwnerIdentity);
        if (mount == 1) assertSame(target.identity(2).token(), target.monster.nativeRequestedMountIdentity);
        if (owner == 0) assertNull(target.monster.nativeOwnerIdentity);
        if (mount == 0) assertNull(target.monster.nativeRequestedMountIdentity);
        target.identity(1).allocated(target.world, 1); target.identity(2).allocated(target.world, 2);
        if (owner != 0) assertFalse(target.monster.nativeOwnerIdentity.isCurrent(target.world, 1));
        if (mount != 0) assertFalse(target.monster.nativeRequestedMountIdentity.isCurrent(target.world, 2));
      }
    }
  }

  @Test void actualMapOwnerAndUniqueZoneIdentityAreRequiredWithoutCoordinateGuessing() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      Map.Zone original = source.zone(), replacement = target.zone();
      source.monster.spawnZone = original; source.monster.spawnX = -99; source.monster.spawnY = 9999;
      source.monster.hasSpawnAnchor = false; // Raw anchor flag is independent from the retained zone.
      MonsterRuntimeCheckpoint saved = source.capture(); saved.restore(target.world, 0);
      assertSame(replacement, target.monster.spawnZone); assertNotSame(original, target.monster.spawnZone);
      assertEquals(-99, target.monster.spawnX); assertFalse(target.monster.hasSpawnAnchor);
      target.zones.clear(); target.monster.nativeSelfResurrections = 42;
      assertThrows(IllegalStateException.class, () -> saved.restore(target.world, 0));
      assertEquals(42, target.monster.nativeSelfResurrections);
      target.zones.add(replacement); target.zone();
      assertThrows(IllegalStateException.class, () -> saved.restore(target.world, 0));
      assertThrows(IllegalStateException.class, target::capture);
      source.world.getMapper(MapWrapper.class).get(0).map = mock(Map.class);
      assertThrows(IllegalStateException.class, source::capture);
      source.monster.spawnZone = mock(Map.Zone.class);
      source.world.getMapper(MapWrapper.class).get(0).map = source.map;
      assertThrows(IllegalStateException.class, source::capture);
    }
  }

  @Test void allUnitRecordsPreflightBeforeMutationAndLegacyV2LeavesMonsterUncovered() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      source.monster.nativeOwnerId = 1; source.monster.nativeOwnerIdentity = source.identity(1).token();
      source.monster.nativeSelfResurrections = 9;
      java.util.Map<Integer, UnitStateRuntimeCheckpoint> image = UnitStateRuntimeCheckpoint.capture(source.world, new int[] {0, 1, 2});
      assertTrue(image.get(0).hasMonsterRuntime()); assertNotNull(image.get(0).monsterRuntime());
      assertTrue(image.get(1).hasMonsterRuntime()); assertNull(image.get(1).monsterRuntime());
      target.world.getMapper(NativePathIdentity.class).remove(1);
      assertThrows(IllegalStateException.class, () -> UnitStateRuntimeCheckpoint.restore(target.world, image));
      assertEquals(0, target.monster.nativeSelfResurrections);
      java.util.Map<Integer, UnitStateRuntimeCheckpoint> legacy = new HashMap<>();
      legacy.put(0, UnitStateRuntimeCheckpoint.fromBytes(new byte[] {0, 0, 0, 2, 0}));
      assertFalse(legacy.get(0).hasMonsterRuntime());
      UnitStateRuntimeCheckpoint.restore(target.world, legacy);
      assertEquals(0, target.monster.nativeSelfResurrections);
      assertThrows(IllegalArgumentException.class, () -> source.capture().validateCoverage(Collections.singleton(0)));
      assertThrows(IllegalStateException.class, () -> UnitStateRuntimeCheckpoint.capture(source.world, new int[] {1, 2}));
      assertThrows(IllegalArgumentException.class, () -> UnitStateRuntimeCheckpoint.capture(source.world, new int[] {0, 1, 3}));
      assertThrows(IllegalArgumentException.class, () -> UnitStateRuntimeCheckpoint.capture(source.world, new int[] {0, 1, 2, 2}));
    }
  }

  @Test void legacyV2ConversionOverlayRestoresWithoutOverwritingUndeclaredMonsterFields() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      source.world.getMapper(UnitStates.class).create(0).init(0);
      source.monster.converted = true; source.monster.conversionOwnerId = 2;
      source.monster.nativeSelfResurrections = 9;
      UnitStateRuntimeCheckpoint full = UnitStateRuntimeCheckpoint.capture(source.world, new int[] {0, 1, 2}).get(0);
      byte[] legacy = Arrays.copyOf(full.bytes(), full.bytes().length
          - 4 - full.pathRuntime().bytes().length
          - 4 - full.actionRuntime().bytes().length
          - 14 - full.monsterRuntime().bytes().length);
      legacy[3] = 2;
      UnitStateRuntimeCheckpoint record = UnitStateRuntimeCheckpoint.fromBytes(legacy);
      assertFalse(record.hasMonsterRuntime()); assertNull(record.monsterRuntime());
      target.monster.nativeSelfResurrections = 4;
      UnitStateRuntimeCheckpoint.restore(target.world, Collections.singletonMap(0, record));
      assertTrue(target.monster.converted); assertEquals(2, target.monster.conversionOwnerId);
      assertEquals(4, target.monster.nativeSelfResurrections);
    }
  }

  @Test void malformedAndResourceIdentityMismatchImagesFailWithoutChangingTheHost() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      byte[] bytes = source.capture().bytes();
      for (int i = 0; i < bytes.length; i++) {
        byte[] truncated = Arrays.copyOf(bytes, i);
        assertThrows(IllegalArgumentException.class, () -> MonsterRuntimeCheckpoint.fromBytes(truncated));
      }
      assertThrows(IllegalArgumentException.class, () -> MonsterRuntimeCheckpoint.fromBytes(Arrays.copyOf(bytes, bytes.length + 1)));
      byte[] bad = bytes.clone(); bad[3] = 2;
      byte[] badVersion = bad;
      assertThrows(IllegalArgumentException.class, () -> MonsterRuntimeCheckpoint.fromBytes(badVersion));
      bad = bytes.clone(); bad[bytes.length - 3] = 3; byte[] badLifetime = bad;
      assertThrows(IllegalArgumentException.class, () -> MonsterRuntimeCheckpoint.fromBytes(badLifetime));
      target.monster.monstats2.Id = "different";
      assertThrows(IllegalStateException.class, () -> source.capture().restore(target.world, 0));
      assertEquals(0, target.monster.nativeSelfResurrections);
      source.monster.spawnX = Float.NaN; assertThrows(IllegalArgumentException.class, source::capture);
      source.monster.spawnX = 0; source.monster.lightningEnchantedLastFrame = Double.POSITIVE_INFINITY;
      assertThrows(IllegalArgumentException.class, source::capture);
    }
  }

  private static NativeMonsterModeElements elements() {
    NativeMonsterModeElements e = new NativeMonsterModeElements();
    for (int i = 0; i < e.minimum.length; i++) { e.minimum[i] = i * 333 - 999; e.maximum[i] = i * 111; }
    for (int i = 0; i < 3; i++) { e.drainMinimum[i] = i + 9; e.drainMaximum[i] = i + 19; }
    e.coldLength = 8; e.poisonLength = 37; e.matchedRows = 3; e.unsupportedType = true; return e;
  }

  static final class Fixture implements AutoCloseable {
    final Map map = mock(Map.class);
    final Array<Map.Zone> zones = new Array<>();
    final World world = new World(new WorldConfigurationBuilder().build().register("map", map));
    final Monster monster;
    Fixture() {
      when(map.getZones()).thenReturn(zones);
      for (int id = 0; id < 3; id++) { assertEquals(id, world.create()); identity(id).allocated(world, id); }
      MonStats.Entry row = new MonStats.Entry(); row.hcIdx = 17; row.Id = "monster";
      MonStats2.Entry row2 = new MonStats2.Entry(); row2.Id = "monster2";
      monster = world.getMapper(Monster.class).create(0).set(row, row2);
      world.getMapper(MapWrapper.class).create(0).set(map, null); world.process();
    }
    NativePathIdentity identity(int id) { return world.getMapper(NativePathIdentity.class).create(id); }
    MonsterRuntimeCheckpoint capture() { return MonsterRuntimeCheckpoint.capture(world, 0, IDS); }
    Map.Zone zone() {
      Map.Zone z = mock(Map.Zone.class); when(z.levelId()).thenReturn(8); when(z.levelSeed()).thenReturn(97);
      when(z.x()).thenReturn(100); when(z.y()).thenReturn(10); when(z.width()).thenReturn(128); when(z.height()).thenReturn(64);
      zones.add(z); return z;
    }
    @Override public void close() { world.dispose(); }
  }
}
