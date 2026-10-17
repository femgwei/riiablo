package com.riiablo.engine.server;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.component.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImpMountCollisionIntegrationTest extends RiiabloTest {
  @Test void mountingKeepsBothFootprintsWithoutRelocatingAndDismountMovesImpMarker() throws Exception {
    try (DiabPrisonIntegrationTest.Fixture f = new DiabPrisonIntegrationTest.Fixture("diablo")) {
      int imp = f.factory.createMonster(Riiablo.files.monstats.get("imp1").hcIdx, 170, 170);
      int mount = f.factory.createMonster(Riiablo.files.monstats.get("siegebeast1").hcIdx, 180, 180);
      f.world.process(); f.collisions.refreshNow();
      teleport(f, imp, mount, null);
      assertEquals(new Vector2(180, 180), f.pos(imp));
      assertTrue(f.collisions.hasPresence(imp));
      assertFalse(f.world.getMapper(NativeUnitFlags.class).get(imp).has(NativeUnitFlags.MONSTER_TARGET));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 170, 170, 1, 0x100));
      // Exclude the mount: the rider must still supply MONSTER and NO_PATH.
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(mount, f.zone, 180, 180, 1, 0x100));
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(mount, f.zone, 180, 180, 1, 0x1000));
      f.collisions.rebuildNow();
      assertEquals(new Vector2(180, 180), f.pos(imp));
      assertEquals(new Vector2(180, 180), f.pos(mount));
      teleport(f, imp, -1, new Vector2(170, 170));
      assertEquals(-1, f.world.getMapper(Monster.class).get(imp).nativeOwnerId);
      assertTrue(f.world.getMapper(NativeUnitFlags.class).get(imp).has(NativeUnitFlags.MONSTER_TARGET));
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 170, 170, 1, 0x100));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(mount, f.zone, 180, 180, 1, 0x100));
    }
  }

  @Test void deadMountIsRejectedBeforeMovingImpOrChangingOwner() throws Exception {
    try (DiabPrisonIntegrationTest.Fixture f = new DiabPrisonIntegrationTest.Fixture("diablo")) {
      int imp = f.factory.createMonster(Riiablo.files.monstats.get("imp1").hcIdx, 170, 170);
      int mount = f.factory.createMonster(Riiablo.files.monstats.get("siegebeast1").hcIdx, 180, 180);
      f.world.getMapper(UnitLifecycle.class).create(mount).transition(UnitLifecycle.Phase.DEATH);
      teleport(f, imp, mount, null);
      assertEquals(new Vector2(170, 170), f.pos(imp));
      assertEquals(-1, f.world.getMapper(Monster.class).get(imp).nativeOwnerId);
      assertEquals(-1, f.world.getMapper(Monster.class).get(mount).nativeOwnerId);
    }
  }

  private static void teleport(DiabPrisonIntegrationTest.Fixture f, int imp, int target,
      Vector2 position) throws Exception {
    java.lang.reflect.Method method = Actioneer.class.getDeclaredMethod("resolveImpTeleport",
        int.class, int.class, Vector2.class);
    method.setAccessible(true);
    method.invoke(f.world.getSystem(Actioneer.class), imp, target, position);
  }
}
