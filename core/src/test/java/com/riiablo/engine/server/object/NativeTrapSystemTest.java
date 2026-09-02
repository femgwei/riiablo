package com.riiablo.engine.server.object;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.NativeTrapFire;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.event.NativeTrapInteractionEvent;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;

import net.mostlyoriginal.api.event.common.EventSystem;

class NativeTrapSystemTest {
  @Test
  void mapsNativeTrapCallbackTypesToTrapMonsters() {
    assertEquals(MonsterType.TRAP_LIGHTNING, NativeTrapSystem.monsterForTrapType(1));
    assertEquals(MonsterType.TRAP_FIREBOLT, NativeTrapSystem.monsterForTrapType(2));
    assertEquals(MonsterType.TRAP_POISONCLOUD, NativeTrapSystem.monsterForTrapType(3));
    assertEquals(MonsterType.TRAP_NOVA, NativeTrapSystem.monsterForTrapType(4));
    assertEquals(-1, NativeTrapSystem.monsterForTrapType(5));
    assertEquals(MonsterType.TRAP_FIREBOLT, NativeTrapSystem.monsterForTrapType(6));
    assertEquals(-1, NativeTrapSystem.monsterForTrapType(7));
    assertEquals(MonsterType.TRAP_FIREBOLT, NativeTrapSystem.monsterForTrapType(8));
    assertEquals(MonsterType.TRAP_FIREBOLT, NativeTrapSystem.monsterForTrapType(9));
  }

  @Test
  void appliesNativeLevelCompatibilityRouting() {
    assertEquals(2, NativeTrapSystem.nativeHandlerType(1, 2));
    assertEquals(2, NativeTrapSystem.nativeHandlerType(3, 2));
    assertEquals(3, NativeTrapSystem.nativeHandlerType(3, 25));
    assertEquals(2, NativeTrapSystem.nativeHandlerType(4, 39));
    assertEquals(4, NativeTrapSystem.nativeHandlerType(4, 40));
    assertEquals(2, NativeTrapSystem.nativeHandlerType(8, 75));
    assertEquals(8, NativeTrapSystem.nativeHandlerType(8, 74));
    assertEquals(1, NativeTrapSystem.nativeHandlerType(1, -1));
  }

  @Test
  void regionTrapTypesSpawnNativeCountOfRewardlessTrapMonsters() {
    Harness harness = new Harness(true);
    try {
      int objectId = harness.objectAt(12, 34);
      NativeTrapInteractionEvent event = NativeTrapInteractionEvent.obtain(
          7, objectId, 5, 4, 100, 8, true);
      int expected = NativeTrapSystem.monsterCount(
          8, NativeTrapSystem.trapSeed(event,
              harness.world.getMapper(Position.class).get(objectId)));
      harness.events.dispatch(event);

      assertEquals(expected, harness.factory.monstersCreated);
      assertEquals(MonsterType.TRAP_FIREBOLT, harness.factory.lastMonsterId);
      assertEquals(12f, harness.factory.firstX);
      assertEquals(34f, harness.factory.firstY);
      assertEquals(expected, harness.factory.nativeFlagApplications);
      assertEquals(NativeUnitFlags.NEST_SUMMON, harness.factory.lastNativeFlags);
    } finally {
      harness.dispose();
    }
  }

  @Test
  void repeatedActivationIsIgnoredAndFireCallbacksCreateExpiringObjects() {
    Harness harness = new Harness(true);
    try {
      int objectId = harness.objectAt(4, 5);
      harness.events.dispatch(NativeTrapInteractionEvent.obtain(
          7, objectId, 5, 4, 100, 1, false));
      harness.events.dispatch(NativeTrapInteractionEvent.obtain(
          7, objectId, 5, 4, 100, 5, true));
      harness.events.dispatch(NativeTrapInteractionEvent.obtain(
          7, objectId, 5, 4, 100, 7, true));

      assertEquals(0, harness.factory.monstersCreated);
      assertEquals(0, harness.factory.nativeFlagApplications);
      assertEquals(4, harness.factory.staticObjectsCreated);
      assertTrue(harness.world.getMapper(NativeTrapFire.class)
          .has(harness.factory.lastStaticObject));
    } finally {
      harness.dispose();
    }
  }

  @Test
  void explodingBarrelWithoutInteractTypeDoesNotBecomeLightningTrap() {
    Harness harness = new Harness(true);
    try {
      int objectId = harness.objectAt(4, 5);
      harness.events.dispatch(NativeTrapInteractionEvent.obtain(
          7, objectId, 11, 7, 100, 0, true));
      assertEquals(0, harness.factory.monstersCreated);
    } finally {
      harness.dispose();
    }
  }

  @Test
  void missingFactoryOrObjectPositionIsIgnoredSafely() {
    Harness withoutFactory = new Harness(false);
    try {
      withoutFactory.events.dispatch(NativeTrapInteractionEvent.obtain(
          7, withoutFactory.world.create(), 5, 4, 100, 1, true));
    } finally {
      withoutFactory.dispose();
    }

    Harness withoutPosition = new Harness(true);
    try {
      withoutPosition.events.dispatch(NativeTrapInteractionEvent.obtain(
          7, withoutPosition.world.create(), 5, 4, 100, 1, true));
      assertEquals(0, withoutPosition.factory.monstersCreated);
    } finally {
      withoutPosition.dispose();
    }
  }

  private static final class Harness {
    final TestFactory factory;
    final NativeTrapSystem traps = new NativeTrapSystem();
    final World world;
    final EventSystem events;

    Harness(boolean withFactory) {
      factory = withFactory ? new TestFactory() : null;
      WorldConfigurationBuilder builder = new WorldConfigurationBuilder()
          .with(new EventSystem(), traps);
      if (factory != null) builder.with(factory);
      com.artemis.WorldConfiguration configuration = builder.build()
          .register("map", new Map(0, 0));
      if (factory != null) configuration.register("factory", factory);
      world = new World(configuration);
      if (factory != null) factory.world = world;
      events = world.getSystem(EventSystem.class);
      world.process();
    }

    int objectAt(float x, float y) {
      int id = world.create();
      world.getMapper(Position.class).create(id).position.set(x, y);
      return id;
    }

    void dispose() {
      world.dispose();
    }
  }

  private static final class TestFactory extends EntityFactory {
    World world;
    int monstersCreated;
    int lastMonsterId = -1;
    float firstX;
    float firstY;
    int nativeFlagApplications;
    int lastNativeFlags;
    int staticObjectsCreated;
    int lastStaticObject = Engine.INVALID_ENTITY;

    @Override public int createPlayer(CharData data, Vector2 position) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createDynamicObject(int act, int preset, float x, float y) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createStaticObject(int act, int object, float x, float y) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createStaticObjectByClassId(int object, float x, float y) {
      staticObjectsCreated++;
      int id = world.create();
      world.getMapper(Position.class).create(id).position.set(x, y);
      world.getMapper(MapWrapper.class).create(id).set(map, map.getZone(x, y));
      com.riiablo.codec.excel.Objects.Entry base = new com.riiablo.codec.excel.Objects.Entry();
      base.Id = object;
      base.Damage = 100;
      world.getMapper(com.riiablo.engine.server.component.Object.class).create(id).base = base;
      lastStaticObject = id;
      return id;
    }

    @Override public int createMonster(int monsterId, float x, float y) {
      monstersCreated++;
      lastMonsterId = monsterId;
      if (monstersCreated == 1) {
        firstX = x;
        firstY = y;
      }
      return 1000 + monstersCreated;
    }

    @Override public void applyNativeUnitFlags(int entityId, int flags) {
      nativeFlagApplications++;
      lastNativeFlags = flags;
    }

    @Override public int createWarp(int index, float x, float y) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createItem(Item item, float x, float y) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createMissile(int missileId, Vector2 angle, Vector2 position) {
      return Engine.INVALID_ENTITY;
    }
  }
}
