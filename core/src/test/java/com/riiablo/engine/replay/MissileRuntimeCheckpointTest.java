package com.riiablo.engine.replay;

import static org.junit.jupiter.api.Assertions.*;
import com.artemis.World;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntSet;
import com.d2moo.common.drlg.D2Seed;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.server.combat.NativeMissilePhysicalDamage;
import com.riiablo.engine.server.combat.NativeMissileElementalDamage;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.NativePathIdentity;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import org.junit.jupiter.api.Test;

class MissileRuntimeCheckpointTest extends RiiabloTest {
  @Test void schemaCoversEveryMissileFieldExplicitly() {
    Set<String> fields = new HashSet<>();
    for (Field field : Missile.class.getDeclaredFields()) {
      if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) fields.add(field.getName());
    }
    assertEquals(fields, MissileRuntimeCheckpoint.coveredFields());
  }

  @Test void everyScalarVectorPacketAndEncodedStatRoundTripsWithoutResourcesDuringDecode() throws Exception {
    try (Fixture source = new Fixture(1); Fixture target = new Fixture(1)) {
      Missile missile = source.get(0);
      int number = 17;
      for (Field field : Missile.class.getDeclaredFields()) {
        if (!Modifier.isPublic(field.getModifiers()) || Modifier.isStatic(field.getModifiers())) continue;
        if (field.getType() == int.class) field.setInt(missile, number++);
        else if (field.getType() == float.class) field.setFloat(missile, number++ + 0.25f);
        else if (field.getType() == boolean.class) field.setBoolean(missile, true);
        else if (field.getType() == String.class) field.set(missile, field.getName() + "-runtime");
        else if (field.getType() == Vector2.class) ((Vector2) field.get(missile)).set(number++, number++);
      }
      missile.ownerId = -1;
      missile.acceleration.restore(4, -64, 15);
      missile.nativeSeed = new D2Seed(missile.rngState, -999);
      missile.nativePhysical = new NativeMissilePhysicalDamage(123, 456, 71, 19, 29, true)
          .withCreationCritical(true).withOwnerCritical(false);
      missile.nativeElemental = NativeMissileElementalDamage.restore(0,
          new int[] {0, 123, 234, 345, 456, 567}, new int[] {0, 234, 345, 456, 567, 678}, 17, 31, 2);
      missile.damage.base().putEncoded(Stat.mindamage, 0, 123);
      missile.damage.aggregate().putEncoded(Stat.mindamage, 0, 999);
      missile.damage.remaining().putEncoded(Stat.maxdamage, 0, 456);
      missile.nextHitFrame.put(8, 91);
      missile.nextHitFrame.put(3, 31);
      missile.hitTargets.add(8); missile.hitTargets.add(3);
      Map<Integer, MissileRuntimeCheckpoint> snapshots = source.capture();
      byte[] bytes = snapshots.get(0).bytes();
      com.riiablo.Files saved = Riiablo.files;
      try {
        Riiablo.files = null;
        assertArrayEquals(bytes, MissileRuntimeCheckpoint.fromBytes(bytes).bytes());
      } finally { Riiablo.files = saved; }
      MissileRuntimeCheckpoint.restore(target.world, snapshots);
      assertArrayEquals(bytes, target.capture().get(0).bytes());
      for (Field field : Missile.class.getDeclaredFields()) {
        if (!Modifier.isPublic(field.getModifiers()) || Modifier.isStatic(field.getModifiers())) continue;
        Class<?> type = field.getType();
        if (type == int.class || type == float.class || type == boolean.class
            || type == String.class || type == Vector2.class) {
          assertEquals(field.get(missile), field.get(target.get(0)), field.getName());
        }
      }
      assertEquals(-999, target.get(0).nativeSeed.getNHighSeed());
      assertEquals(missile.nativeSeed.getNLowSeed(), target.get(0).nativeSeed.getNLowSeed());
      assertTrue(target.get(0).nativePhysical.creationCritical);
      assertTrue(target.get(0).nativePhysical.ownerCriticalCaptured);
      assertFalse(target.get(0).nativePhysical.ownerCritical);
      assertEquals(123, target.get(0).nativePhysical.minimum);
      assertEquals(456, target.get(0).nativePhysical.maximum);
      assertEquals(71, target.get(0).nativePhysical.damagePercent);
      for (int i = 0; i < 6; i++) {
        assertEquals(missile.nativeElemental.minimum(i), target.get(0).nativeElemental.minimum(i));
        assertEquals(missile.nativeElemental.maximum(i), target.get(0).nativeElemental.maximum(i));
      }
      assertEquals(123, target.get(0).damage.base().get(Stat.mindamage).encodedValues());
      assertEquals(456, target.get(0).damage.remaining().get(Stat.maxdamage).encodedValues());
      assertEquals(999, target.get(0).damage.get(Stat.mindamage).encodedValues());
      Vector2 first = new Vector2(3, 0), second = new Vector2(3, 0);
      missile.acceleration.advance(first);
      target.get(0).acceleration.advance(second);
      assertEquals(first, second);
      assertTrue(first.x < 3f); // the fifth-step acceleration phase survived
      byte[] detached = snapshots.get(0).bytes(); detached[0] ^= 1;
      assertArrayEquals(bytes, snapshots.get(0).bytes());
    }
  }

  @Test void sharedCastSetsAndAliasesToOtherMissilesHitSetsKeepTheirIdentity() {
    try (Fixture source = new Fixture(3); Fixture target = new Fixture(3)) {
      IntSet common = new IntSet(); common.add(17);
      source.get(0).sharedHitTargets = common;
      source.get(1).sharedHitTargets = source.get(0).hitTargets;
      source.get(0).hitTargets.add(91);
      source.get(2).sharedHitTargets = common;
      Map<Integer, MissileRuntimeCheckpoint> snapshots = source.capture();
      MissileRuntimeCheckpoint.restore(target.world, snapshots);
      assertSame(target.get(0).sharedHitTargets, target.get(2).sharedHitTargets);
      assertSame(target.get(0).hitTargets, target.get(1).sharedHitTargets);
      target.get(2).sharedHitTargets.add(18);
      assertTrue(target.get(0).sharedHitTargets.contains(18));
      assertFalse(source.get(0).sharedHitTargets.contains(18));
      target.get(0).sharedHitTargets.remove(18);
      for (int i = 0; i < 3; i++) assertArrayEquals(snapshots.get(i).bytes(), target.capture().get(i).bytes());
    }
  }

  @Test void currentAndRetiredOwnerLifetimeReferencesDoNotBindToTheWrongIncarnation() {
    try (Fixture source = new Fixture(2); Fixture target = new Fixture(2)) {
      int sourceOwner = source.world.create(), targetOwner = target.world.create();
      NativePathIdentity first = source.world.getMapper(NativePathIdentity.class).create(sourceOwner)
          .allocated(source.world, sourceOwner);
      source.get(0).ownerId = sourceOwner;
      source.get(0).nativeBaalOwnerIdentity = first.token();
      first.allocated(source.world, sourceOwner); // id 0 retains a stale token
      source.get(1).ownerId = sourceOwner;
      source.get(1).nativeBaalOwnerIdentity = first.token();
      target.world.getMapper(NativePathIdentity.class).create(targetOwner).allocated(target.world, targetOwner);
      Map<Integer, MissileRuntimeCheckpoint> snapshots = source.capture();
      MissileRuntimeCheckpoint.restore(target.world, snapshots);
      assertFalse(target.get(0).nativeBaalOwnerIdentity.isCurrent(target.world, targetOwner));
      assertTrue(target.get(1).nativeBaalOwnerIdentity.isCurrent(target.world, targetOwner));
      assertArrayEquals(snapshots.get(0).bytes(), target.capture().get(0).bytes());
    }
  }

  @Test void malformedBooleansFloatsVersionsTruncationAndTrailingBytesAreRejected() {
    try (Fixture source = new Fixture(1)) {
      byte[] bytes = source.capture().get(0).bytes();
      byte[] invalid = bytes.clone(); invalid[28] = 2;
      assertThrows(IllegalArgumentException.class, () -> MissileRuntimeCheckpoint.fromBytes(invalid));
      byte[] nan = bytes.clone(); java.nio.ByteBuffer.wrap(nan).putFloat(8, Float.NaN);
      assertThrows(IllegalArgumentException.class, () -> MissileRuntimeCheckpoint.fromBytes(nan));
      byte[] version = bytes.clone(); version[3] = 2;
      assertThrows(IllegalArgumentException.class, () -> MissileRuntimeCheckpoint.fromBytes(version));
      assertThrows(IllegalArgumentException.class, () -> MissileRuntimeCheckpoint.fromBytes(Arrays.copyOf(bytes, bytes.length - 1)));
      assertThrows(IllegalArgumentException.class, () -> MissileRuntimeCheckpoint.fromBytes(Arrays.copyOf(bytes, bytes.length + 1)));
      source.get(0).damage.buildList();
      assertThrows(IllegalStateException.class, source::capture);
    }
  }

  @Test void conflictingSharedGroupsAndMissingCurrentOwnerAreRejectedBeforeMutation() {
    try (Fixture source = new Fixture(2); Fixture target = new Fixture(2)) {
      IntSet common = new IntSet(); common.add(91);
      source.get(0).sharedHitTargets = source.get(1).sharedHitTargets = common;
      Map<Integer, MissileRuntimeCheckpoint> first = source.capture();
      common.add(92);
      Map<Integer, MissileRuntimeCheckpoint> conflicting = new TreeMap<>(first);
      conflicting.put(1, source.capture().get(1));
      assertThrows(IllegalArgumentException.class, () -> MissileRuntimeCheckpoint.restore(target.world, conflicting));
      assertEquals(0, target.get(0).hitTargets.size);
      int owner = source.world.create();
      source.get(1).ownerId = owner;
      source.get(1).nativeBaalOwnerIdentity = source.world.getMapper(NativePathIdentity.class)
          .create(owner).allocated(source.world, owner).token();
      Map<Integer, MissileRuntimeCheckpoint> missingOwner = source.capture();
      assertThrows(IllegalStateException.class, () -> MissileRuntimeCheckpoint.restore(target.world, missingOwner));
      assertNull(target.get(0).sharedHitTargets);
    }
  }

  private static final class Fixture implements AutoCloseable {
    final World world = new World();
    final int[] ids;
    Fixture(int count) {
      ids = new int[count];
      for (int i = 0; i < count; i++) {
        ids[i] = world.create();
        Missile missile = world.getMapper(Missile.class).create(ids[i]);
        missile.missile = new Missiles.Entry(); missile.missile.Id = 77;
      }
    }
    Missile get(int id) { return world.getMapper(Missile.class).get(id); }
    Map<Integer, MissileRuntimeCheckpoint> capture() { return MissileRuntimeCheckpoint.capture(world, ids); }
    @Override public void close() { world.dispose(); }
  }
}
