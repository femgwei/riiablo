package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.CharacterClass;
import com.riiablo.codec.excel.Skills;
import com.badlogic.gdx.math.Vector2;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.attributes.Attributes;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Native-data inventory for the Assassin skill tree before runtime porting. */
class AssassinSkillSpecializationTest extends RiiabloTest {
  @Test
  void auditNativeAssassinRows() {
    int rows = 0;
    for (int id = CharacterClass.ASSASSIN.firstSpell; id < CharacterClass.ASSASSIN.lastSpell; id++) {
      Skills.Entry skill = Riiablo.files.skills.get(id);
      assertNotNull(skill, "assassin skill id=" + id);
      rows++;
      System.out.println("[ASSASSIN_SKILL] id=" + skill.Id + " name=" + skill.skill
          + " srvSt=" + skill.srvstfunc + " srvDo=" + skill.srvdofunc
          + " cltDo=" + skill.cltdofunc + " srvA=" + skill.srvmissilea
          + " srvB=" + skill.srvmissileb + " cltA=" + skill.cltmissilea
          + " summon=" + skill.summon + " petType=" + skill.pettype
          + " petMax=" + skill.petmax + " auraLen=" + skill.auralencalc
          + " params=" + java.util.Arrays.toString(skill.Param));
    }
    System.out.println("[ASSASSIN_SKILL_SUMMARY] rows=" + rows);
  }

  @Test
  void shadowWarriorUsesNativeSrvDo049AndOwnedPetState() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      Skills.Entry shadow = Riiablo.files.skills.get("Shadow Warrior");
      assertNotNull(shadow);
      data.setSkillLevel(shadow.Id, 5);
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(2, 3);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = Attributes.obtainStandard();

      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, shadow.Id, Engine.INVALID_ENTITY, new Vector2(5, 3), shadow.srvdofunc, 0));

      assertEquals(1, factory.created);
      assertEquals("shadowwarrior", factory.petType);
      assertTrue(factory.skillLevel == 5);
      assertTrue(world.getMapper(UnitStates.class).get(factory.entityId).stateList
          .hasState(StateId.SHADOWWARRIOR));
    } finally {
      world.dispose();
    }
  }

  private static final class RecordingFactory extends EntityFactory {
    int created;
    int entityId = Engine.INVALID_ENTITY;
    int skillLevel;
    String petType;

    @Override
    public int createSummonedPet(int ownerId, com.riiablo.codec.excel.MonStats.Entry summon,
        String petType, int skillId, int skillLevel, int petMax, boolean passive,
        int durationFrames, float x, float y) {
      created++;
      this.petType = petType;
      this.skillLevel = skillLevel;
      entityId = world.create();
      world.getMapper(Monster.class).create(entityId).monstats = summon;
      world.getMapper(Position.class).create(entityId).position.set(x, y);
      world.getMapper(UnitStates.class).create(entityId).init(entityId);
      return entityId;
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
