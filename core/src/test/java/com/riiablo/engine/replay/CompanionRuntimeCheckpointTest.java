package com.riiablo.engine.replay;

import com.artemis.EntityAllocatorCheckpoint;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.*;
import com.riiablo.engine.server.component.*;
import com.riiablo.map.Map;
import java.lang.reflect.*;
import java.lang.Object;
import java.nio.ByteBuffer;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanionRuntimeCheckpointTest {
  @Test void everyHirelingFieldHasAnExplicitPolicyAndRawScalarsRoundTrip() throws Exception {
    Set<String> expected = new HashSet<>(Arrays.asList("ownerId", "nativeTargetOrder", "nativeTargetNode",
        "mercType", "hirelingId", "level", "seed", "nameId", "experience", "flags", "progressionRevision",
        "equipmentRevision", "skills", "skillLevels", "aiRngState", "aiChanceParam", "targetId"));
    try (Fixture source = new Fixture(false); Fixture target = new Fixture(false)) {
      Mercenary a = source.merc(), b = target.merc(); Set<String> actual = new HashSet<>();
      int value = 10;
      for (Field field : Mercenary.class.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
        actual.add(field.getName());
        if (!Modifier.isPublic(field.getModifiers())) continue;
        if (field.getType() == int.class) field.setInt(a, value++);
        else if (field.getType() == long.class) field.setLong(a, 9007199254740001L);
      }
      a.ownerId = 0; a.targetId = 777; a.hirelingId = -1; a.aiRngState = 0; a.aiChanceParam = -37;
      for (int i = 0; i < 6; i++) { a.skills[i] = i == 0 ? -1 : 100 + i; a.skillLevels[i] = i * 3; }
      assertEquals(expected, actual);
      NativeTargetNode targetNode = b.checkpointTargetNode(); int[] skills = b.skills, levels = b.skillLevels;
      TargetRuntimeCheckpoint image = source.image(); image.restore(target.world);
      assertSame(b, target.merc()); assertSame(targetNode, b.checkpointTargetNode());
      assertSame(skills, b.skills); assertSame(levels, b.skillLevels);
      for (Field field : Mercenary.class.getDeclaredFields()) {
        if (Modifier.isPublic(field.getModifiers()) && !Modifier.isStatic(field.getModifiers())) {
          if (field.getType() == int[].class) assertArrayEquals((int[]) field.get(a), (int[]) field.get(b), field.getName());
          else assertEquals(field.get(a), field.get(b), field.getName());
        }
      }
      assertArrayEquals(image.bytes(), target.image().bytes());
      verifyNoInteractions(source.actions, target.actions);
    }
  }

  @Test void timerPresenceZeroBitsAndWorldIsolationArePreserved() {
    try (Fixture source = new Fixture(true); Fixture target = new Fixture(true); Fixture other = new Fixture(true)) {
      source.follow.restoreRepathCooldowns(Collections.singletonMap(1, -0f));
      source.skills.restoreSkillCooldowns(Collections.singletonMap(1, .137f));
      target.follow.restoreRepathCooldowns(Collections.singletonMap(1, .5f));
      target.skills.restoreSkillCooldowns(Collections.singletonMap(1, .4f));
      other.follow.restoreRepathCooldowns(Collections.singletonMap(1, .3f));
      TargetRuntimeCheckpoint image = source.image(); image.restore(target.world);
      assertEquals(Float.floatToRawIntBits(-0f), Float.floatToRawIntBits(target.follow.checkpointRepathCooldowns().get(1)));
      assertEquals(.137f, target.skills.checkpointSkillCooldowns().get(1));
      assertEquals(.3f, other.follow.checkpointRepathCooldowns().get(1));
      assertArrayEquals(image.bytes(), target.image().bytes());
      source.follow.restoreRepathCooldowns(Collections.emptyMap()); source.skills.restoreSkillCooldowns(Collections.emptyMap());
      source.image().restore(target.world);
      assertTrue(target.follow.checkpointRepathCooldowns().isEmpty());
      assertTrue(target.skills.checkpointSkillCooldowns().isEmpty());
      assertThrows(UnsupportedOperationException.class, () -> source.follow.checkpointRepathCooldowns().put(1, .1f));
    }
  }

  @Test void networkProjectionUsesEffectiveHirelingIdButNeverAcceptsANegativeWireFallback() throws Exception {
    try (Fixture f = new Fixture(false)) {
      Mercenary projection = new Mercenary();
      for (Field field : Mercenary.class.getDeclaredFields()) {
        if (!Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers())
            && field.getType().isPrimitive()) field.set(projection, field.get(f.merc()));
      }
      projection.hirelingId = f.merc().hirelingId();
      java.util.Map<Integer, Mercenary> projections = new TreeMap<>();
      projections.put(0, null); projections.put(1, projection);
      CompanionRuntimeCheckpoint image = f.image().companionRuntime(); image.validateMercenaryProjections(projections);
      projection.hirelingId = -1;
      assertThrows(IllegalArgumentException.class, () -> image.validateMercenaryProjections(projections));
      projection.hirelingId = f.merc().hirelingId(); projection.flags ^= 1;
      assertThrows(IllegalArgumentException.class, () -> image.validateMercenaryProjections(projections));
      projections.put(1, null);
      assertThrows(IllegalArgumentException.class, () -> image.validateMercenaryProjections(projections));
    }
  }

  @Test void hirelingPresenceAndAbsenceRestoreWithoutSetOrNewTargetEvents() {
    try (Fixture source = new Fixture(false); Fixture target = new Fixture(false)) {
      target.world.getMapper(Mercenary.class).remove(1); EntityAllocatorCheckpoint.flushStructure(target.world);
      source.image().restore(target.world);
      assertEquals(0, target.merc().ownerId);
      assertSame(target.world.getMapper(NativeTargetNode.class).get(1), target.merc().checkpointTargetNode());
      assertArrayEquals(source.image().bytes(), target.image().bytes());
      source.world.getMapper(Mercenary.class).remove(1); EntityAllocatorCheckpoint.flushStructure(source.world);
      source.image().restore(target.world);
      assertNull(target.merc()); assertArrayEquals(source.image().bytes(), target.image().bytes());
    }
  }

  @Test void missingLaterHostAndSystemTopologyMismatchDoNotRewindAnyLiveState() {
    try (Fixture source = new Fixture(true); Fixture target = new Fixture(false)) {
      long before = target.slots.nextOrder(); target.merc().aiRngState = 987;
      assertThrows(IllegalStateException.class, () -> source.image().restore(target.world));
      assertEquals(before, target.slots.captureCheckpoint().sequence); assertEquals(987, target.merc().aiRngState);
    }
    try (Fixture source = new Fixture(true); Fixture target = new Fixture(true)) {
      source.world.create(); EntityAllocatorCheckpoint.flushStructure(source.world);
      source.skills.restoreSkillCooldowns(Collections.singletonMap(1, .2f));
      byte[] before = target.image().bytes();
      TargetRuntimeCheckpoint image = TargetRuntimeCheckpoint.capture(source.world, new int[] {0, 1, 2});
      assertThrows(IllegalStateException.class, () -> image.restore(target.world));
      assertArrayEquals(before, target.image().bytes());
    }
  }

  @Test void invalidTimerMapsFailBeforeClearingExistingClocks() {
    try (Fixture f = new Fixture(true)) {
      f.skills.restoreSkillCooldowns(Collections.singletonMap(1, .23f));
      for (float invalid : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -.01f}) {
        assertThrows(IllegalArgumentException.class, () -> f.skills.restoreSkillCooldowns(Collections.singletonMap(1, invalid)));
        assertEquals(.23f, f.skills.checkpointSkillCooldowns().get(1));
      }
      assertThrows(IllegalArgumentException.class, () -> f.follow.restoreRepathCooldowns(Collections.singletonMap(-1, .2f)));
      assertThrows(IllegalArgumentException.class, () -> f.pets.restoreRegroupCooldowns(Collections.singletonMap(1, null)));
    }
  }

  @Test void uncoveredTimerHostsOwnerAndDetachedMembershipAreRejected() {
    try (Fixture f = new Fixture(true)) {
      f.skills.restoreSkillCooldowns(Collections.singletonMap(77, .1f));
      assertThrows(IllegalArgumentException.class, f::image);
      f.skills.restoreSkillCooldowns(Collections.emptyMap()); f.merc().ownerId = 77;
      assertThrows(IllegalArgumentException.class, f::image);
      f.merc().ownerId = 0; f.merc().bindNativeTargetNode(null);
      assertThrows(IllegalStateException.class, f::image);
    }
  }

  @Test void missingTimerHostShapeFailsBeforeTargetSequenceOrMercenaryMutation() {
    try (Fixture source = new Fixture(true); Fixture target = new Fixture(true)) {
      source.skills.restoreSkillCooldowns(Collections.singletonMap(1, .2f));
      target.world.getMapper(Monster.class).remove(1); target.merc().aiRngState = 999;
      EntityAllocatorCheckpoint.flushStructure(target.world); long before = target.slots.nextOrder();
      TargetRuntimeCheckpoint image = source.image();
      assertThrows(IllegalStateException.class, () -> image.restore(target.world));
      assertEquals(999, target.merc().aiRngState); assertEquals(before, target.slots.captureCheckpoint().sequence);
    }
  }

  @Test void malformedCompanionImagesRejectTruncationTrailingBooleanCountsAndVersions() {
    try (Fixture f = new Fixture(false)) {
      byte[] bytes = f.image().companionRuntime().bytes();
      for (int i = 0; i < bytes.length; i++) {
        byte[] truncated = Arrays.copyOf(bytes, i);
        assertThrows(IllegalArgumentException.class, () -> CompanionRuntimeCheckpoint.fromBytes(truncated));
      }
      assertThrows(IllegalArgumentException.class, () -> CompanionRuntimeCheckpoint.fromBytes(Arrays.copyOf(bytes, bytes.length + 1)));
      for (int offset : new int[] {4, 5, 6, 15}) {
        byte[] bad = bytes.clone(); bad[offset] = 2;
        assertThrows(IllegalArgumentException.class, () -> CompanionRuntimeCheckpoint.fromBytes(bad), "offset=" + offset);
      }
      byte[] bad = bytes.clone(); ByteBuffer.wrap(bad).putInt(7, Integer.MAX_VALUE);
      assertThrows(IllegalArgumentException.class, () -> CompanionRuntimeCheckpoint.fromBytes(bad));
      byte[] owned = f.image().companionRuntime().bytes(); owned[3] = 99;
      assertArrayEquals(bytes, f.image().companionRuntime().bytes());
    }
  }

  @Test void legacyV2LeavesCompanionsExplicitlyUncovered() {
    try (Fixture source = new Fixture(true); Fixture target = new Fixture(true)) {
      source.skills.restoreSkillCooldowns(Collections.singletonMap(1, .23f));
      TargetRuntimeCheckpoint current = source.image();
      byte[] v2 = Arrays.copyOf(current.bytes(), current.bytes().length - 4 - current.companionRuntime().bytes().length);
      ByteBuffer.wrap(v2).putInt(2); TargetRuntimeCheckpoint legacy = TargetRuntimeCheckpoint.fromBytes(v2);
      assertNull(legacy.companionRuntime()); assertTrue(legacy.hasAiControlRuntime());
      target.merc().aiRngState = 876; target.skills.restoreSkillCooldowns(Collections.singletonMap(1, .1f));
      legacy.restore(target.world);
      assertEquals(876, target.merc().aiRngState); assertEquals(.1f, target.skills.checkpointSkillCooldowns().get(1));
      current.restore(target.world); assertEquals(.23f, target.skills.checkpointSkillCooldowns().get(1));
    }
  }

  @Test void malformedTimerWireRejectsNonfiniteNegativeUncoveredAndDuplicateEntries() {
    try (Fixture f = new Fixture(true)) {
      f.follow.restoreRepathCooldowns(Collections.singletonMap(1, .2f));
      byte[] bytes = f.image().companionRuntime().bytes();
      for (float invalid : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -.1f}) {
        byte[] bad = bytes.clone(); ByteBuffer.wrap(bad).putFloat(13, invalid);
        assertThrows(IllegalArgumentException.class, () -> CompanionRuntimeCheckpoint.fromBytes(bad));
      }
      for (int invalid : new int[] {-1, 77}) {
        byte[] bad = bytes.clone(); ByteBuffer.wrap(bad).putInt(9, invalid);
        assertThrows(IllegalArgumentException.class, () -> CompanionRuntimeCheckpoint.fromBytes(bad));
      }
      byte[] duplicate = new byte[bytes.length + 8];
      System.arraycopy(bytes, 0, duplicate, 0, 17);
      System.arraycopy(bytes, 17, duplicate, 25, bytes.length - 17);
      ByteBuffer.wrap(duplicate).putInt(5, 2).putInt(17, 1).putFloat(21, .3f);
      assertThrows(IllegalArgumentException.class, () -> CompanionRuntimeCheckpoint.fromBytes(duplicate));
      assertEquals(.2f, f.follow.checkpointRepathCooldowns().get(1));
    }
  }

  @Test void removalClearsSkillRetryAndRecycledIdDoesNotInheritIt() {
    try (Fixture f = new Fixture(true)) {
      f.skills.restoreSkillCooldowns(Collections.singletonMap(1, .4f));
      f.world.delete(1); EntityAllocatorCheckpoint.flushStructure(f.world);
      assertTrue(f.skills.checkpointSkillCooldowns().isEmpty());
      assertEquals(1, f.world.create()); assertTrue(f.skills.checkpointSkillCooldowns().isEmpty());
    }
  }

  @Test void followRetryContinuesAtTheSameTickAfterRestore() throws Exception {
    try (Fixture source = new Fixture(true); Fixture target = new Fixture(true)) {
      for (Fixture f : new Fixture[] {source, target}) {
        f.world.getMapper(Position.class).get(0).position.set(40, 0);
        f.skills.setEnabled(false);
        when(f.actions.canInterrupt(1)).thenReturn(true);
        when(f.actions.tryRunTo(eq(1), any(Vector2.class), eq(60))).thenReturn(true);
      }
      source.follow.restoreRepathCooldowns(Collections.singletonMap(1, .13f));
      source.image().restore(target.world);
      for (int tick = 0; tick < 30; tick++) {
        source.world.setDelta(.04f); target.world.setDelta(.04f); source.world.process(); target.world.process();
        assertEquals(source.follow.followCount(), target.follow.followCount());
        assertArrayEquals(source.image().bytes(), target.image().bytes());
        if (tick < 3) assertEquals(0, target.follow.followCount());
        if (tick == 3) assertEquals(1, target.follow.followCount());
      }
      assertTrue(target.follow.followCount() > 1);
    }
  }

  @Test void deletionBeforeCompanionAspectInsertionStillClearsRestoredClocks() {
    try (Fixture f = new Fixture(true)) {
      f.world.getMapper(Mercenary.class).remove(1); EntityAllocatorCheckpoint.flushStructure(f.world);
      // The entity is already published, but the restored controller is not in either aspect yet.
      f.world.getMapper(Mercenary.class).create(1);
      f.world.getMapper(SummonedPet.class).create(1);
      f.skills.restoreSkillCooldowns(Collections.singletonMap(1, .4f));
      f.follow.restoreRepathCooldowns(Collections.singletonMap(1, .3f));
      f.pets.restoreRegroupCooldowns(Collections.singletonMap(1, .2f));
      f.world.delete(1); EntityAllocatorCheckpoint.flushStructure(f.world);
      assertTrue(f.skills.checkpointSkillCooldowns().isEmpty());
      assertTrue(f.follow.checkpointRepathCooldowns().isEmpty());
      assertTrue(f.pets.checkpointRegroupCooldowns().isEmpty());
      assertEquals(1, f.world.create());
    }
  }

  @Test void skillRetryAndZeroStateRngContinueForOneHundredTicks() throws Exception {
    try (Fixture source = new Fixture(true); Fixture target = new Fixture(true)) {
      for (Fixture f : new Fixture[] {source, target}) {
        f.follow.setEnabled(false);
        f.merc().mercType = 1; f.merc().hirelingId = 9; f.merc().aiRngState = 0;
        f.world.getMapper(Position.class).get(1).position.set(0, 0);
        int enemy = f.world.create(); assertEquals(2, enemy);
        f.world.getMapper(Position.class).create(enemy).position.set(1, 0);
        f.world.getMapper(AttributesWrapper.class).create(enemy);
        MonStats.Entry row = new MonStats.Entry(); row.killable = true;
        f.world.getMapper(Monster.class).create(enemy).monstats = row;
        when(f.actions.isInMeleeRange(1, 2, 0)).thenReturn(true);
        int[] ids = {47, -1, -1, -1, -1, -1}, levels = {1, 0, 0, 0, 0, 0};
        NativeHirelingExperienceTable table = new NativeHirelingExperienceTable().add(
            new NativeHirelingExperienceTable.Row(9, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, ids, new int[6], levels, new int[6])
                .withChances(new int[] {100, 0, 0, 0, 0, 0}, new int[6]).withDefaultChance(0));
        set(f.skills, "table", table); EntityAllocatorCheckpoint.flushStructure(f.world);
      }
      source.skills.restoreSkillCooldowns(Collections.singletonMap(1, .13f));
      TargetRuntimeCheckpoint.capture(source.world, new int[] {0, 1, 2}).restore(target.world);
      for (int tick = 0; tick < 100; tick++) {
        source.world.setDelta(.04f); target.world.setDelta(.04f); source.world.process(); target.world.process();
        assertEquals(source.merc().aiRngState, target.merc().aiRngState);
        assertEquals(source.skills.castCount(), target.skills.castCount());
        assertArrayEquals(TargetRuntimeCheckpoint.capture(source.world, new int[] {0, 1, 2}).bytes(),
            TargetRuntimeCheckpoint.capture(target.world, new int[] {0, 1, 2}).bytes());
        if (tick < 3) assertEquals(0, target.skills.castCount());
      }
      assertTrue(target.skills.castCount() > 5);
    }
  }

  @Test void summonRegroupRetryRestoresTheControllerBeforeItsClocks() throws Exception {
    try (Fixture source = new Fixture(true); Fixture target = new Fixture(true)) {
      for (Fixture f : new Fixture[] {source, target}) {
        SummonedPet pet = f.world.getMapper(SummonedPet.class).create(1);
        pet.bindNativeTargetNode(f.world.getMapper(NativeTargetNode.class).get(1));
      }
      source.pets.restoreRegroupCooldowns(Collections.singletonMap(1, .13f));
      TargetRuntimeCheckpoint image = source.image(); image.restore(target.world);
      Method ready = SummonedPetSystem.class.getDeclaredMethod("readyToRegroup", int.class, boolean.class);
      ready.setAccessible(true);
      for (int tick = 0; tick < 5; tick++) {
        source.world.setDelta(.04f); target.world.setDelta(.04f);
        Object actual = ready.invoke(target.pets, 1, false);
        assertEquals(ready.invoke(source.pets, 1, false), actual);
        assertEquals(tick >= 3, actual);
        assertEquals(source.pets.checkpointRegroupCooldowns(), target.pets.checkpointRegroupCooldowns());
      }
      target.world.getMapper(SummonedPet.class).remove(1); EntityAllocatorCheckpoint.flushStructure(target.world);
      image.restore(target.world);
      assertNotNull(target.world.getMapper(SummonedPet.class).get(1));
      assertEquals(.13f, target.pets.checkpointRegroupCooldowns().get(1));
    }
  }

  private static void set(Object host, String name, Object value) throws Exception {
    Field field = host.getClass().getDeclaredField(name); field.setAccessible(true); field.set(host, value);
  }
  private static final class Fixture implements AutoCloseable {
    final NativePlayerTargetSlots slots = new NativePlayerTargetSlots();
    final MercenaryFollowSystem follow = new MercenaryFollowSystem();
    final MercenarySkillSystem skills = new MercenarySkillSystem();
    final SummonedPetSystem pets = new SummonedPetSystem();
    final Actioneer actions = mock(Actioneer.class);
    final World world;
    Fixture(boolean timers) {
      WorldConfigurationBuilder builder = new WorldConfigurationBuilder().with(slots);
      if (timers) builder.with(follow, skills, pets);
      world = new World(builder.build());
      try { if (timers) { set(follow, "actioneer", actions); set(skills, "actioneer", actions); } }
      catch (Exception failure) { throw new AssertionError(failure); }
      assertEquals(0, world.create()); world.getMapper(Player.class).create(0);
      assertEquals(1, world.create());
      Map map = mock(Map.class); Map.Zone zone = new Map.Zone();
      when(map.getZone(any(Vector2.class))).thenReturn(zone);
      when(map.getZone(anyFloat(), anyFloat())).thenReturn(zone);
      when(map.getZone(anyInt(), anyInt())).thenReturn(zone);
      for (int id : new int[] {0, 1}) {
        world.getMapper(Position.class).create(id).position.set(id * 5, 0);
        world.getMapper(MapWrapper.class).create(id).set(map, zone);
      }
      NativeTargetNode node = world.getMapper(NativeTargetNode.class).create(1); node.usePlayerSlots(slots);
      Mercenary m = world.getMapper(Mercenary.class).create(1); m.bindNativeTargetNode(node);
      m.set(0, 0, 10, 123, 7);
      MonStats.Entry row = new MonStats.Entry(); row.Align = 1;
      world.getMapper(Monster.class).create(1).monstats = row;
      EntityAllocatorCheckpoint.flushStructure(world);
    }
    Mercenary merc() { return world.getMapper(Mercenary.class).get(1); }
    TargetRuntimeCheckpoint image() { return TargetRuntimeCheckpoint.capture(world, new int[] {0, 1}); }
    @Override public void close() { world.dispose(); }
  }
}
