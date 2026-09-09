package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.pet.PetType;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** ECS regression for atomic corpse consumption and owned skeleton creation. */
class NecromancerSummonIntegrationTest extends RiiabloTest {
  @Test
  void raiseSkeletonConsumesCorpseOnceAndCreatesOwnedPet() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("necromancer", (byte) Riiablo.NECROMANCER);
      data.setSkillLevel(SkillId.RAISE_SKELETON, 5);
      world.getMapper(Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(10, 10);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(20, 100);

      int corpse = world.create();
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.RAISE_SKELETON);
      Monster corpseMonster = world.getMapper(Monster.class).create(corpse);
      corpseMonster.monstats = Riiablo.files.monstats.get(skill.summon);
      corpseMonster.monstats2 = corpseMonster.monstats == null ? null
          : Riiablo.files.monstats2.get(corpseMonster.monstats.MonStatsEx);
      world.getMapper(Position.class).create(corpse).position.set(12, 10);
      world.getMapper(AttributesWrapper.class).create(corpse).attrs = attributes(1, 0);
      world.getMapper(UnitStates.class).create(corpse).init(corpse);
      world.getMapper(Corpse.class).create(corpse).reset(Corpse.DEFAULT_DURATION, true);

      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, SkillId.RAISE_SKELETON, corpse, new Vector2(12, 10), 31, 0));

      assertFalse(world.getMapper(Corpse.class).has(corpse), "consumed corpse must be removed");
      assertTrue(factory.created > 0, "native summon must create one pet");
      assertEquals(owner, world.getMapper(SummonedPet.class).get(factory.lastEntity).ownerId);
      assertEquals(PetType.canonical(skill.pettype), factory.lastPetType);
      assertTrue(world.getMapper(SummonedPet.class).get(factory.lastEntity).petType.length() > 0);

      // A repeated keyframe cannot consume or create from the same corpse.
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, SkillId.RAISE_SKELETON, corpse, new Vector2(12, 10), 31, 0));
      assertEquals(1, factory.created);
    } finally {
      world.dispose();
    }
  }

  private static Attributes attributes(int level, float hp) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.level, level);
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, Math.max(1f, hp));
    attrs.reset();
    return attrs;
  }

  private static final class RecordingFactory extends EntityFactory {
    int created;
    int lastEntity = Engine.INVALID_ENTITY;
    String lastPetType;

    @Override public int createSummonedPet(int ownerId,
        com.riiablo.codec.excel.MonStats.Entry summon, String petType, int skillId,
        int skillLevel, int petMax, boolean passive, int durationFrames, float x, float y) {
      created++;
      lastPetType = PetType.canonical(petType);
      lastEntity = world.create();
      world.getMapper(Monster.class).create(lastEntity).monstats = summon;
      world.getMapper(Position.class).create(lastEntity).position.set(x, y);
      world.getMapper(AttributesWrapper.class).create(lastEntity).attrs = attributes(1, 10);
      world.getMapper(UnitStates.class).create(lastEntity).init(lastEntity);
      world.getMapper(SummonedPet.class).create(lastEntity)
          .set(ownerId, lastPetType, skillId, skillLevel, passive, durationFrames);
      return lastEntity;
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
