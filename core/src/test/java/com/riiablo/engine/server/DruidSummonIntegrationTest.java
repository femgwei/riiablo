package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
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

/** ECS wiring for authoritative Druid SrvDo114/115/119 summon creation. */
class DruidSummonIntegrationTest extends RiiabloTest {
  @Test
  void allNativeDruidSummonFunctionsCreateOwnedEntities() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("druid", (byte) Riiablo.DRUID);
      world.getMapper(Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(10, 10);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(20, 100);

      int[] ids = {SkillId.RAVEN, SkillId.POISON_CREEPER, SkillId.OAK_SAGE,
          SkillId.SUMMON_SPIRIT_WOLF, SkillId.CARRION_VINE,
          SkillId.HEART_OF_WOLVERINE, SkillId.SUMMON_DIRE_WOLF,
          SkillId.SOLAR_CREEPER, SkillId.SPIRIT_OF_BARBS, SkillId.SUMMON_GRIZZLY};
      for (int id : ids) {
        com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(id);
        data.setSkillLevel(id, 8);
        world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
            owner, id, Engine.INVALID_ENTITY, new Vector2(12, 10), skill.srvdofunc, 0));
        assertEquals(owner, world.getMapper(SummonedPet.class).get(factory.lastEntity).ownerId);
        assertEquals(PetType.canonical(skill.pettype), factory.lastPetType);
      }
      assertEquals(ids.length, factory.created);
      assertTrue(factory.lastMaximum >= 1);
    } finally {
      world.dispose();
    }
  }

  private static Attributes attributes(int level, float hp) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.level, level);
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.reset();
    return attrs;
  }

  private static final class RecordingFactory extends EntityFactory {
    int created;
    int lastEntity = Engine.INVALID_ENTITY;
    int lastMaximum;
    String lastPetType;

    @Override public int createSummonedPet(int ownerId,
        com.riiablo.codec.excel.MonStats.Entry summon, String petType, int skillId,
        int skillLevel, int petMax, boolean passive, int durationFrames, float x, float y) {
      created++;
      lastMaximum = petMax;
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
