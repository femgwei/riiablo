package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.pet.PetType;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;

/** Direct authoritative ServerEntityFactory coverage for native PetMax rules. */
class ServerEntityFactorySummonQuotaTest extends RiiabloTest {
  @Test
  void skeletonPetMaxEvictsOldestMatchingPetThroughNativeDismissal() {
    Fixture fixture = new Fixture();
    World world = fixture.world;
    try {
      int owner = fixture.owner();
      MonStats.Entry summon = summon(SkillId.RAISE_SKELETON);
      int first = fixture.factory.createSummonedPet(
          owner, summon, "skeleton", SkillId.RAISE_SKELETON, 5, 2, false, 0, 10, 10);
      world.process();
      int second = fixture.factory.createSummonedPet(
          owner, summon, "skeleton", SkillId.RAISE_SKELETON, 5, 2, false, 0, 11, 10);
      world.process();
      int third = fixture.factory.createSummonedPet(
          owner, summon, "skeleton", SkillId.RAISE_SKELETON, 5, 2, false, 0, 12, 10);

      assertTrue(first >= 0 && second >= 0 && third >= 0);
      assertTrue(world.getMapper(SummonedPet.class).get(first).unsummonPending,
          "PetMax replacement must dismiss the oldest matching pet");
      assertTrue(!world.getMapper(SummonedPet.class).get(second).unsummonPending);
      assertTrue(!world.getMapper(SummonedPet.class).get(third).unsummonPending);
      assertEquals("skeleton", world.getMapper(SummonedPet.class).get(first).petType);
      assertTrue(world.getMapper(SummonedPet.class).get(first).spawnOrder
          < world.getMapper(SummonedPet.class).get(second).spawnOrder);
    } finally {
      world.dispose();
    }
  }

  @Test
  void allGolemRowsShareOneNativePetTypeQuota() {
    Fixture fixture = new Fixture();
    World world = fixture.world;
    try {
      int owner = fixture.owner();
      int clay = fixture.factory.createSummonedPet(owner, summon(SkillId.CLAY_GOLEM),
          "Clay Golem", SkillId.CLAY_GOLEM, 5, 1, false, 0, 10, 10);
      world.process();
      int fire = fixture.factory.createSummonedPet(owner, summon(SkillId.FIRE_GOLEM),
          "Fire Golem", SkillId.FIRE_GOLEM, 5, 1, false, 0, 11, 10);

      assertEquals("golem", world.getMapper(SummonedPet.class).get(clay).petType);
      assertEquals("golem", world.getMapper(SummonedPet.class).get(fire).petType);
      assertTrue(world.getMapper(SummonedPet.class).get(clay).unsummonPending,
          "a new golem must replace the previous golem regardless of row name");
      assertTrue(!world.getMapper(SummonedPet.class).get(fire).unsummonPending);
    } finally {
      world.dispose();
    }
  }

  private static MonStats.Entry summon(int skillId) {
    com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(skillId);
    assertNotNull(skill);
    MonStats.Entry summon = Riiablo.files.monstats.get(skill.summon);
    if (summon == null) {
      for (MonStats.Entry candidate : Riiablo.files.monstats) {
        if (candidate != null && candidate.Id != null
            && candidate.Id.equalsIgnoreCase(skill.summon)) {
          summon = candidate;
          break;
        }
      }
    }
    assertNotNull(summon, "missing summon row " + skill.summon);
    return summon;
  }

  private static final class Fixture {
    final TestFactory factory = new TestFactory();
    final World world = new World(new WorldConfigurationBuilder()
        .with(new net.mostlyoriginal.api.event.common.EventSystem(), new ItemManager(),
            new ObjectInteractor(), new WarpInteractor(), new ItemInteractor(),
            factory, new SummonedPetSystem())
        .build()
        .register("factory", factory)
        .register("map", new Map(0, 0)));

    int owner() {
      int id = world.create();
      world.getMapper(Player.class).create(id).data =
          CharData.createRemote("necromancer", (byte) Riiablo.NECROMANCER);
      world.getMapper(Position.class).create(id).position.set(10, 10);
      world.getMapper(MapWrapper.class).create(id).set(null, new OpenZone());
      return id;
    }
  }

  private static final class TestFactory extends ServerEntityFactory {
    @Override public int createMonster(int monsterId, float x, float y) {
      int id = world.create();
      mNativeUnitFlags.create(id).reset();
      return id;
    }

    @Override public int createPlayer(CharData data, Vector2 position) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createDynamicObject(int act, int id, float x, float y) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createStaticObject(int act, int id, float x, float y) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createStaticObjectByClassId(int id, float x, float y) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createWarp(int index, float x, float y) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createItem(com.riiablo.item.Item item, float x, float y) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createMissile(int id, Vector2 angle, Vector2 position) {
      return Engine.INVALID_ENTITY;
    }
  }

  private static final class OpenZone extends Map.Zone {
    @Override public boolean findFreeCoordinates(Vector2 coordinates, int unitSize,
        int maxDistance, boolean allowNeighborRooms, Vector2 result) {
      result.set(coordinates);
      return true;
    }
  }
}
