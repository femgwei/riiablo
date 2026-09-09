package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Authoritative SrvDo060/SrvDo062 unit creation coverage. */
class NecromancerBoneWallIntegrationTest extends RiiabloTest {
  @Test
  void boneWallCreatesCentralAndEightPerpendicularSegments() {
    RecordingFactory factory = new RecordingFactory();
    World world = world(factory);
    try {
      int owner = player(world, SkillId.BONE_WALL);
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.BONE_WALL);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, skill.Id, Engine.INVALID_ENTITY, new Vector2(4, 0),
          skill.srvdofunc, skill.cltdofunc));

      assertEquals(9, pets(world).size());
      int controller = Engine.INVALID_ENTITY;
      for (int i = 0; i < pets(world).size(); i++) {
        int id = pets(world).get(i);
        SummonedPet pet = world.getMapper(SummonedPet.class).get(id);
        assertTrue(pet.boneWall);
        assertEquals(600, pet.durationFrames);
        assertEquals(4f, world.getMapper(Position.class).get(id).position.x, 0.001f);
        if (pet.controllerId == id) controller = id;
      }
      assertTrue(controller >= 0);
      for (int i = 0; i < pets(world).size(); i++) {
        assertEquals(controller,
            world.getMapper(SummonedPet.class).get(pets(world).get(i)).controllerId);
      }
    } finally {
      world.dispose();
    }
  }

  @Test
  void bonePrisonCreatesNativeTwelveSegmentRingAndSkipsTownTarget() {
    RecordingFactory factory = new RecordingFactory();
    World world = world(factory);
    try {
      int owner = player(world, SkillId.BONE_PRISON);
      int target = monster(world, 20, 20);
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.BONE_PRISON);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, skill.Id, target, new Vector2(20, 20), skill.srvdofunc, skill.cltdofunc));
      assertEquals(12, pets(world).size());

      int townTarget = monster(world, 40, 40);
      world.getMapper(com.riiablo.engine.server.component.MapWrapper.class)
          .create(townTarget).zone = new com.riiablo.map.Map.Zone() {
            @Override public boolean isTown() { return true; }
          };
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, skill.Id, townTarget, new Vector2(40, 40), skill.srvdofunc, skill.cltdofunc));
      assertEquals(12, pets(world).size());
    } finally {
      world.dispose();
    }
  }

  @Test
  void wallSegmentsUseSkillLifePercentAndControllerDeathRemovesChildren() {
    RecordingFactory factory = new RecordingFactory();
    World world = world(factory, true);
    try {
      int owner = player(world, SkillId.BONE_WALL);
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.BONE_WALL);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, skill.Id, Engine.INVALID_ENTITY, new Vector2(4, 0),
          skill.srvdofunc, skill.cltdofunc));
      world.process();
      IntBag created = pets(world);
      int controller = Engine.INVALID_ENTITY;
      for (int i = 0; i < created.size(); i++) {
        int id = created.get(i);
        assertEquals(125f, hp(world, id), 0.001f);
        if (world.getMapper(SummonedPet.class).get(id).controllerId == id) controller = id;
      }
      assertTrue(controller >= 0, "first native segment must be the wall controller; ids=" + describe(world, created));
      world.getMapper(AttributesWrapper.class).get(controller).attrs
          .base().put(Stat.hitpoints, 0);
      world.getMapper(AttributesWrapper.class).get(controller).attrs.reset();
      world.process();
      world.process();
      assertEquals(1, pets(world).size(), "only the dead controller remains for its death sequence");
      assertTrue(world.getEntityManager().isActive(controller));
    } finally {
      world.dispose();
    }
  }

  private static World world(RecordingFactory factory) {
    return world(factory, false);
  }

  private static World world(RecordingFactory factory, boolean lifecycle) {
    WorldConfigurationBuilder builder = new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true));
    if (lifecycle) builder.with(new SummonedPetSystem());
    return new World(builder.with(factory).build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
  }

  private static int player(World world, int skillId) {
    int id = world.create();
    CharData data = CharData.createRemote("necromancer", (byte) Riiablo.NECROMANCER);
    data.setSkillLevel(skillId, 2);
    world.getMapper(Player.class).create(id).data = data;
    world.getMapper(Position.class).create(id).position.set(0, 0);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(100);
    return id;
  }

  private static int monster(World world, float x, float y) {
    int id = world.create();
    MonStats.Entry row = Riiablo.files.monstats.get("fallen1");
    world.getMapper(Monster.class).create(id).monstats = row;
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(100);
    return id;
  }

  private static Attributes attributes(float hp) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.level, 10);
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.base().put(Stat.mana, 100);
    attrs.base().put(Stat.maxmana, 100);
    attrs.reset();
    return attrs;
  }

  private static float hp(World world, int id) {
    return world.getMapper(AttributesWrapper.class).get(id).attrs
        .get(Stat.hitpoints).asFixed();
  }

  private static IntBag pets(World world) {
    return world.getAspectSubscriptionManager().get(Aspect.all(SummonedPet.class)).getEntities();
  }

  private static String describe(World world, IntBag ids) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < ids.size(); i++) {
      int id = ids.get(i);
      SummonedPet pet = world.getMapper(SummonedPet.class).get(id);
      result.append(id).append(':').append(pet.controllerId).append('/').append(pet.boneWall).append(' ');
    }
    return result.toString();
  }

  private static final class RecordingFactory extends EntityFactory {
    @Override public int createSummonedPet(int ownerId, MonStats.Entry summon, String petType,
        int skillId, int skillLevel, int petMax, boolean passive, int durationFrames,
        float x, float y) {
      int id = world.create();
      Monster monster = world.getMapper(Monster.class).create(id);
      monster.monstats = summon;
      monster.monstats2 = Riiablo.files.monstats2.get(summon.MonStatsEx);
      world.getMapper(Position.class).create(id).position.set(x, y);
      world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(100);
      world.getMapper(SummonedPet.class).create(id)
          .set(ownerId, petType, skillId, skillLevel, passive, durationFrames);
      return id;
    }

    @Override public int createPlayer(CharData data, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMonster(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(com.riiablo.item.Item item, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMissile(int id, Vector2 angle, Vector2 position) { return Engine.INVALID_ENTITY; }
  }
}
