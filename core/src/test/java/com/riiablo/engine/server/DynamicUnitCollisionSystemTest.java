package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.codec.excel.MonStats2;
import com.riiablo.engine.server.component.Monster;
import org.junit.jupiter.api.Test;

class DynamicUnitCollisionSystemTest {
  @Test void overlappingDetachedLevelsDoNotShareUnitCollision() {
    DynamicUnitCollisionSystem collision = new DynamicUnitCollisionSystem();
    com.artemis.World world = new com.artemis.World(new com.artemis.WorldConfigurationBuilder()
        .with(collision).build());
    try {
      com.riiablo.map.Map map = new com.riiablo.map.Map(1, 0);
      com.riiablo.map.Map.Zone first = new com.riiablo.map.Map.Zone();
      com.riiablo.map.Map.Zone second = new com.riiablo.map.Map.Zone();
      int a = world.create(), b = world.create();
      for (int id : new int[] {a, b}) {
        world.getMapper(com.riiablo.engine.server.component.Position.class).create(id).position.set(10, 10);
        world.getMapper(com.riiablo.engine.server.component.Velocity.class).create(id);
        world.getMapper(com.riiablo.engine.server.component.Size.class).create(id).size = 2;
        world.getMapper(com.riiablo.engine.server.component.Class.class).create(id)
            .type = com.riiablo.engine.server.component.Class.Type.PLR;
        world.getMapper(com.riiablo.engine.server.component.MapWrapper.class).create(id)
            .set(map, id == a ? first : second);
      }
      world.process();
      collision.refreshNow();
      assertTrue(collision.isFreeForPath(a, -1, 10, 10, 2));
      assertFalse(collision.isFreeForPath(second, a, -1, 10, 10, 2),
          "A warp must query target-level monsters before updating the mover wrapper");
      assertTrue(collision.isFreeForLanding(a, 10, 10, 2));
      world.getMapper(com.riiablo.engine.server.component.MapWrapper.class).get(b).set(map, first);
      assertFalse(collision.isFreeForPath(a, -1, 10, 10, 2));
      assertFalse(collision.isFreeForLanding(a, 10, 10, 2));
    } finally { world.dispose(); }
  }
  @Test void newlyInsertedMonsterCannotTrapPlayerOnDeferredActivation() {
    DynamicUnitCollisionSystem collision = new DynamicUnitCollisionSystem();
    com.artemis.World world = new com.artemis.World(new com.artemis.WorldConfigurationBuilder()
        .with(collision).build());
    try {
      int player = world.create();
      world.getMapper(com.riiablo.engine.server.component.Position.class).create(player).position.set(10, 10);
      world.getMapper(com.riiablo.engine.server.component.Velocity.class).create(player);
      world.getMapper(com.riiablo.engine.server.component.Size.class).create(player).size = 2;
      world.getMapper(com.riiablo.engine.server.component.Class.class).create(player)
          .type = com.riiablo.engine.server.component.Class.Type.PLR;
      world.process();
      int monster = world.create();
      world.getMapper(com.riiablo.engine.server.component.Position.class).create(monster).position.set(10, 10);
      world.getMapper(com.riiablo.engine.server.component.Velocity.class).create(monster);
      world.getMapper(com.riiablo.engine.server.component.Size.class).create(monster).size = 2;
      world.getMapper(com.riiablo.engine.server.component.Class.class).create(monster)
          .type = com.riiablo.engine.server.component.Class.Type.MON;
      world.process();
      assertTrue(collision.isFreeForPath(player, -1, 10, 10, 2));
      com.badlogic.gdx.math.Vector2 placed = world.getMapper(com.riiablo.engine.server.component.Position.class).get(monster).position.cpy();
      world.process();
      assertTrue(placed.epsilonEquals(world.getMapper(com.riiablo.engine.server.component.Position.class).get(monster).position),
          "Spawn placement repair must not teleport established monsters every tick");
    } finally { world.dispose(); }
  }
  @Test
  void ambientCrittersAreNotDynamicPathObstacles() {
    Monster critter = new Monster().set(null, new MonStats2.Entry());
    critter.monstats2.critter = true;

    assertFalse(DynamicUnitCollisionSystem.isDynamicObstacle(critter));
  }

  @Test
  void ordinaryMonstersRemainDynamicPathObstacles() {
    Monster monster = new Monster().set(null, new MonStats2.Entry());

    assertTrue(DynamicUnitCollisionSystem.isDynamicObstacle(monster));
  }

  @Test
  void nativeCollisionUsesCanBeInTownAndExcludesInteractables() {
    assertTrue(DynamicUnitCollisionSystem.isPetPresence(true, true, false, false, false));
    assertTrue(DynamicUnitCollisionSystem.isPetPresence(true, false, true, false, false));
    assertTrue(DynamicUnitCollisionSystem.isPetPresence(true, false, false, true, false));
    assertFalse(DynamicUnitCollisionSystem.isPetPresence(true, false, false, false, false));
    assertFalse(DynamicUnitCollisionSystem.isPetPresence(true, true, false, false, true));
    assertFalse(DynamicUnitCollisionSystem.isPetPresence(false, true, true, true, false));
  }

  @Test
  void tableOnlyCollisionHelperKeepsReviveAndSmallParity() {
    assertTrue(DynamicUnitCollisionSystem.isTablePetPresence(true, true, false, false));
    assertTrue(DynamicUnitCollisionSystem.isTablePetPresence(true, false, true, false));
    assertFalse(DynamicUnitCollisionSystem.isTablePetPresence(true, false, false, false));
    assertFalse(DynamicUnitCollisionSystem.isTablePetPresence(true, true, true, true));
    assertFalse(DynamicUnitCollisionSystem.isTablePetPresence(false, true, true, false));
  }

  @Test
  void nativeNoPathMarkerPreventsOrdinaryMonstersFromOverlapping() {
    assertTrue(DynamicUnitCollisionSystem.blocksNativePath(true, false));
    assertTrue(DynamicUnitCollisionSystem.blocksNativePath(true, true));
    assertTrue(DynamicUnitCollisionSystem.blocksNativePath(false, false));
    assertFalse(DynamicUnitCollisionSystem.blocksNativePath(false, true));
  }

  @Test
  void nativeFlyingPathIgnoresPetPresenceButStillSeesNoPath() {
    assertFalse(DynamicUnitCollisionSystem.blocksNativePath(
        DynamicUnitCollisionSystem.NativePathProfile.FLYING, true));
    assertTrue(DynamicUnitCollisionSystem.blocksNativePath(
        DynamicUnitCollisionSystem.NativePathProfile.FLYING, false));
  }

  @Test
  void nativeDoorOpeningMonsterUsesOrdinaryDynamicUnitMask() {
    assertTrue(DynamicUnitCollisionSystem.blocksNativePath(
        DynamicUnitCollisionSystem.NativePathProfile.MONSTER_CAN_OPEN_DOORS, true));
    assertTrue(DynamicUnitCollisionSystem.blocksNativePath(
        DynamicUnitCollisionSystem.NativePathProfile.MONSTER_CAN_OPEN_DOORS, false));
  }
}
