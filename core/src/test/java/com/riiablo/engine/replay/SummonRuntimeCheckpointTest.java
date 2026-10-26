package com.riiablo.engine.replay;

import com.artemis.EntityAllocatorCheckpoint;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.engine.server.NativePlayerTargetSlots;
import com.riiablo.engine.server.SummonedPetSystem;
import com.riiablo.engine.server.component.*;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SummonRuntimeCheckpointTest {
  @Test void everyFieldHasAnExplicitPolicyAndAllRawScalarsRoundTripInPlace() throws Exception {
    Set<String> expected = new HashSet<>(Arrays.asList("ownerId", "petType", "skillId", "skillLevel", "passive",
        "durationFrames", "elapsedFrames", "spawnOrder", "nativeTargetOrder", "nativeTargetNode", "unsummonPending",
        "unsummonReason", "networkReplica", "maxShots", "shotsFired", "attackCooldownFrames", "bladeSentinel",
        "hasTrapTarget", "trapTargetX", "trapTargetY", "bladeOriginX", "bladeOriginY", "bladeMovingToTarget",
        "bladeMissileId", "infernoChanneling", "infernoFrame", "infernoSequencePoint", "infernoEndFrame",
        "infernoRemainingFrames", "infernoPulseFrames", "infernoPulseCooldownFrames", "infernoTargetId",
        "deathLastCorpseId", "deathPending", "deadFrames", "sourceItem", "boneWall", "controllerId"));
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      SummonedPet a = source.pet(1), b = target.pet(1); Set<String> actual = new HashSet<>();
      for (Field field : SummonedPet.class.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
        actual.add(field.getName());
        if (!Modifier.isPublic(field.getModifiers())) continue;
        if (field.getType() == int.class) field.setInt(a, 37);
        else if (field.getType() == boolean.class) field.setBoolean(a, true);
        else if (field.getType() == float.class) field.setFloat(a, -0f);
        else if (field.getType() == String.class) field.set(a, "\u9aa8\u5899/\ud83d\udd25");
      }
      a.ownerId = 0; a.skillId = -1; a.spawnOrder = 1; a.infernoEndFrame = -1;
      // These are signed due counters, not nonnegative durations (Inferno tail can retain negatives).
      a.attackCooldownFrames = -2; a.infernoRemainingFrames = -15; a.infernoPulseCooldownFrames = -4;
      assertEquals(expected, actual);
      TargetRuntimeCheckpoint image = source.image(); NativeTargetNode node = b.checkpointTargetNode();
      image.restore(target.world);
      assertSame(b, target.pet(1)); assertSame(node, b.checkpointTargetNode());
      for (Field field : SummonedPet.class.getDeclaredFields()) if (Modifier.isPublic(field.getModifiers())
          && !Modifier.isStatic(field.getModifiers())) assertEquals(field.get(a), field.get(b), field.getName());
      assertArrayEquals(image.bytes(), target.image().bytes());
    }
  }

  @Test void missingAndExtraControllersRestorePresenceAndShareTheTargetNode() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      target.world.getMapper(SummonedPet.class).remove(1); EntityAllocatorCheckpoint.flushStructure(target.world);
      source.image().restore(target.world);
      assertSame(target.world.getMapper(NativeTargetNode.class).get(1), target.pet(1).checkpointTargetNode());
      assertArrayEquals(source.image().bytes(), target.image().bytes());
      source.world.getMapper(SummonedPet.class).remove(1); EntityAllocatorCheckpoint.flushStructure(source.world);
      source.image().restore(target.world); assertNull(target.pet(1));
      assertArrayEquals(source.image().bytes(), target.image().bytes());
    }
  }

  @Test void quotaIsWorldLocalRetainsDeletedHighWaterAndDoesNotConsumeTargetEventsOnRestore() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture(); Fixture other = new Fixture()) {
      long unrelated = other.slots.checkpointPetSpawnSequence();
      source.slots.nextPetSpawnOrder(); source.slots.nextPetSpawnOrder(); // retired quota entries
      long targetSequence = source.slots.captureCheckpoint().sequence;
      source.image().restore(target.world);
      assertEquals(targetSequence, target.slots.captureCheckpoint().sequence);
      assertEquals(2, target.world.create()); target.addPet(2);
      assertEquals(2, source.world.create()); source.addPet(2);
      EntityAllocatorCheckpoint.flushStructure(source.world); EntityAllocatorCheckpoint.flushStructure(target.world);
      assertEquals(4, target.pet(2).spawnOrder); assertEquals(4, source.pet(2).spawnOrder);
      assertEquals(unrelated, other.slots.checkpointPetSpawnSequence());
      source.world.getMapper(SummonedPet.class).remove(1);
      source.world.getMapper(SummonedPet.class).remove(2); EntityAllocatorCheckpoint.flushStructure(source.world);
      source.image().restore(target.world); assertNull(target.pet(1));
      assertEquals(5, target.slots.nextPetSpawnOrder());
    }
  }

  @Test void allocatorExhaustionDoesNotWrapOrAffectAnotherWorld() {
    try (Fixture source = new Fixture(); Fixture other = new Fixture()) {
      source.slots.restorePetSpawnSequence(Long.MAX_VALUE);
      source.image().restore(other.world);
      assertThrows(IllegalStateException.class, source.slots::nextPetSpawnOrder);
      assertThrows(IllegalStateException.class, other.slots::nextPetSpawnOrder);
      assertThrows(IllegalStateException.class, () -> source.pet(1).set(99, "overwrite", 7, 2, true, 4));
      assertEquals(0, source.pet(1).ownerId); assertEquals("assassintrap", source.pet(1).petType);
      assertEquals(1L, source.pet(1).spawnOrder);
      assertEquals(Long.MAX_VALUE, source.slots.checkpointPetSpawnSequence());
      assertThrows(IllegalArgumentException.class, () -> source.slots.restorePetSpawnSequence(-1));
    }
  }

  @Test void durationAndUnsummonDeathGraceContinueAtTheExactSameTick() {
    for (boolean dying : new boolean[] {false, true}) {
      try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
        SummonedPet p = source.pet(1); p.petType = "assassintrap";
        p.durationFrames = 7; p.elapsedFrames = 2.25f;
        p.unsummonPending = dying; p.deadFrames = 23.5f; p.unsummonReason = dying ? "quota" : null;
        source.image().restore(target.world);
        for (int tick = 1; tick <= 6; tick++) {
          source.tick(); target.tick();
          boolean active = source.world.getEntityManager().isActive(1);
          assertEquals(tick < (dying ? 2 : 5), active);
          assertEquals(active, target.world.getEntityManager().isActive(1));
          assertArrayEquals(source.image().bytes(), target.image().bytes());
        }
      }
    }
  }

  @Test void boneWallSegmentsKeepTheirControllerAndDieTogetherAfterRestore() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      for (Fixture f : new Fixture[] {source, target}) {
        assertEquals(2, f.world.create()); f.addPet(2);
        f.pet(1).boneWall = true; f.pet(1).controllerId = 1;
        f.pet(2).boneWall = true; f.pet(2).controllerId = 1;
        EntityAllocatorCheckpoint.flushStructure(f.world);
      }
      source.image().restore(target.world);
      source.world.delete(1); target.world.delete(1);
      EntityAllocatorCheckpoint.flushStructure(source.world); EntityAllocatorCheckpoint.flushStructure(target.world);
      source.tick(); target.tick();
      assertFalse(source.world.getEntityManager().isActive(2));
      assertFalse(target.world.getEntityManager().isActive(2));
      assertArrayEquals(source.image().bytes(), target.image().bytes());
    }
  }

  @Test void missingLaterHostAndUncoveredItemGraphFailBeforeAnyMutation() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      assertEquals(2, target.world.create()); target.addPet(2); EntityAllocatorCheckpoint.flushStructure(target.world);
      byte[] before = target.image().bytes(); TargetRuntimeCheckpoint image = source.image();
      assertThrows(IllegalStateException.class, () -> image.restore(target.world));
      assertArrayEquals(before, target.image().bytes());
    }
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      assertEquals(2, source.world.create()); source.addPet(2); EntityAllocatorCheckpoint.flushStructure(source.world);
      byte[] before = target.image().bytes(); TargetRuntimeCheckpoint image = source.image();
      assertThrows(IllegalStateException.class, () -> image.restore(target.world));
      assertArrayEquals(before, target.image().bytes());
      source.pet(1).sourceItem = new com.riiablo.item.Item();
      assertThrows(IllegalStateException.class, source::image);
    }
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      TargetRuntimeCheckpoint image = source.image(); target.pet(1).sourceItem = new com.riiablo.item.Item();
      long sequence = target.slots.nextOrder(), quota = target.slots.checkpointPetSpawnSequence();
      target.pet(1).shotsFired = 123;
      assertThrows(IllegalStateException.class, () -> image.restore(target.world));
      assertNotNull(target.pet(1).sourceItem); assertEquals(123, target.pet(1).shotsFired);
      assertEquals(sequence, target.slots.captureCheckpoint().sequence);
      assertEquals(quota, target.slots.checkpointPetSpawnSequence());
    }
  }

  @Test void invalidRawValuesDuplicateQuotaAndUncoveredLiveReferencesAreRejected() {
    try (Fixture f = new Fixture()) {
      for (float invalid : new float[] {Float.NaN, Float.POSITIVE_INFINITY, -1f}) {
        f.pet(1).elapsedFrames = invalid; assertThrows(IllegalArgumentException.class, f::image);
      }
      f.pet(1).elapsedFrames = 0;
      assertEquals(2, f.world.create()); EntityAllocatorCheckpoint.flushStructure(f.world);
      f.pet(1).bladeMissileId = 2;
      assertThrows(IllegalStateException.class, () -> SummonRuntimeCheckpoint.capture(f.world, new int[] {0, 1}));
      f.pet(1).bladeMissileId = -1; f.addPet(2); EntityAllocatorCheckpoint.flushStructure(f.world);
      f.pet(2).spawnOrder = f.pet(1).spawnOrder; assertThrows(IllegalArgumentException.class, f::image);
      f.pet(2).spawnOrder = Long.MAX_VALUE; assertThrows(IllegalArgumentException.class, f::image);
    }
  }

  @Test void allTruncationsTrailingBytesInvalidVersionsAndBooleansAreRejected() {
    try (Fixture f = new Fixture()) {
      byte[] bytes = f.image().companionRuntime().summonRuntime().bytes();
      for (int i = 0; i < bytes.length; i++) {
        byte[] truncated = Arrays.copyOf(bytes, i);
        assertThrows(IllegalArgumentException.class, () -> SummonRuntimeCheckpoint.fromBytes(truncated));
      }
      assertThrows(IllegalArgumentException.class, () -> SummonRuntimeCheckpoint.fromBytes(Arrays.copyOf(bytes, bytes.length + 1)));
      byte[] bad = bytes.clone(); bad[3] = 2; final byte[] versionImage = bad;
      assertThrows(IllegalArgumentException.class, () -> SummonRuntimeCheckpoint.fromBytes(versionImage));
      bad = bytes.clone(); bad[20] = 2; final byte[] booleanImage = bad;
      assertThrows(IllegalArgumentException.class, () -> SummonRuntimeCheckpoint.fromBytes(booleanImage));
      bad = bytes.clone(); ByteBuffer.wrap(bad).putInt(12, Integer.MAX_VALUE); final byte[] countImage = bad;
      assertThrows(IllegalArgumentException.class, () -> SummonRuntimeCheckpoint.fromBytes(countImage));
      byte[] own = f.image().companionRuntime().summonRuntime().bytes(); own[3] = 99;
      assertArrayEquals(bytes, f.image().companionRuntime().summonRuntime().bytes());
    }
  }

  @Test void oldCompanionV1LeavesSummonControllersAndQuotaExplicitlyUncovered() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      CompanionRuntimeCheckpoint current = source.image().companionRuntime();
      byte[] v1 = Arrays.copyOf(current.bytes(), current.bytes().length - 3 - 4 - current.summonRuntime().bytes().length);
      ByteBuffer.wrap(v1).putInt(1); CompanionRuntimeCheckpoint legacy = CompanionRuntimeCheckpoint.fromBytes(v1);
      assertNull(legacy.summonRuntime()); target.pet(1).shotsFired = 11; target.slots.nextPetSpawnOrder();
      legacy.restore(target.world);
      assertEquals(11, target.pet(1).shotsFired); assertEquals(2, target.slots.checkpointPetSpawnSequence());
      source.image().restore(target.world); assertEquals(0, target.pet(1).shotsFired);
      assertEquals(1, target.slots.checkpointPetSpawnSequence());
    }
  }

  private static final class Fixture implements AutoCloseable {
    final NativePlayerTargetSlots slots = new NativePlayerTargetSlots();
    final World world = new World(new WorldConfigurationBuilder().with(slots, new SummonedPetSystem()).build());
    Fixture() {
      assertEquals(0, world.create()); world.getMapper(Player.class).create(0);
      assertEquals(1, world.create()); addPet(1); EntityAllocatorCheckpoint.flushStructure(world);
    }
    void addPet(int id) {
      NativeTargetNode node = world.getMapper(NativeTargetNode.class).create(id); node.usePlayerSlots(slots);
      world.getMapper(SummonedPet.class).create(id).bindNativeTargetNode(node).set(0, "assassintrap", 272, 4, false, 0);
    }
    SummonedPet pet(int id) { return world.getMapper(SummonedPet.class).get(id); }
    TargetRuntimeCheckpoint image() { return TargetRuntimeCheckpoint.capture(world, EntityAllocatorCheckpoint.capture(world).activeIds()); }
    void tick() { world.setDelta(.04f); world.process(); }
    @Override public void close() { world.dispose(); }
  }
}
