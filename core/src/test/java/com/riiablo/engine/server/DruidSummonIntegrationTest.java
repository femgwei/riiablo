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
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.skill.DruidSkills;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
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

  @Test
  void vinesInstallNativeSumSkillLevelAndVineBeastStateWithoutAuraProjection() {
    int[] vineSkills = {SkillId.POISON_CREEPER, SkillId.CARRION_VINE, SkillId.SOLAR_CREEPER};
    for (int id : vineSkills) {
      com.riiablo.codec.excel.Skills.Entry vine = Riiablo.files.skills.get(id);
      assertEquals(StateId.VINE_BEAST, DruidSkills.getSummonAuraState(vine));
      assertTrue(DruidSkills.getSummonAuraSkill(vine) == null,
          "Vines use the Vine Attack SumSkill, not a party aura row");
      assertEquals("Vine Attack", vine.sumskill[0]);
      com.riiablo.codec.excel.Skills.Entry attack = Riiablo.files.skills.get(vine.sumskill[0]);
      assertTrue(attack != null && attack.Id >= 0);
      assertEquals(8, DruidSkills.getSummonGrantedSkillLevel(vine, 8, attack.Id, name -> 0),
          "D2MOO SumSk1Calc=lvl must preserve the parent vine skill level");
    }
  }

  @Test
  void summonBaseLevelUsesNativeCalc2InsteadOfOwnerLevelHeuristic() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("druid-level", (byte) Riiablo.DRUID);
      data.setSkillLevel(SkillId.RAVEN, 8);
      world.getMapper(Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(10, 10);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(20, 100);

      com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(SkillId.RAVEN);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, SkillId.RAVEN, Engine.INVALID_ENTITY, new Vector2(10, 10),
          skill.srvdofunc, skill.cltdofunc));

      int expected = Math.max(1, SkillFormula.evaluate(skill.calc2, skill, 8,
          name -> 0, name -> Riiablo.files.skills.get(name)));
      int actual = world.getMapper(AttributesWrapper.class).get(factory.lastEntity)
          .attrs.get(Stat.level).asInt();
      assertEquals(expected, actual,
          "D2MOO SrvDo114 uses Skills.txt Calc2 for the summoned base level");
    } finally {
      world.dispose();
    }
  }

  @Test
  void spiritAuraProjectsToOwnerWithPetSourceIdentity() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new StateUpdater(), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("druid-aura", (byte) Riiablo.DRUID);
      data.setSkillLevel(SkillId.OAK_SAGE, 8);
      world.getMapper(Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(10, 10);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(20, 100);
      world.getMapper(UnitStates.class).create(owner).init(owner);

      com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(SkillId.OAK_SAGE);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, SkillId.OAK_SAGE, Engine.INVALID_ENTITY, new Vector2(10, 10),
          skill.srvdofunc, skill.cltdofunc));
      int source = factory.lastEntity;

      world.setDelta(1f / 25f);
      world.process();

      UnitState aura = world.getMapper(UnitStates.class).get(owner).stateList
          .getStateLayer(StateId.OAKSAGE, source, SkillId.OAK_SAGE);
      UnitState sourceAura = world.getMapper(UnitStates.class).get(source).stateList
          .getState(StateId.OAKSAGE);
      assertTrue(aura != null,
          "Oak Sage must project a source-owned aura layer to its owner");
      assertEquals(source, aura.sourceEntityId);
      assertEquals(SkillId.OAK_SAGE, aura.skillId);
      assertTrue(aura.maxLifeModifier > 0,
          "Oak Sage owner layer must carry its native max-life modifier source="
              + (sourceAura != null ? sourceAura.maxLifeModifier : -1)
              + " skillStats=" + skill.aurastat[0] + " calc=" + skill.aurastatcalc[0]
              + " auraState=" + skill.aurastate);
    } finally {
      world.dispose();
    }
  }

  @Test
  void spiritAurasUseLinkedNativeAuraRowsAndRevokeWhenOutOfRangeOrDismissed() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new StateUpdater(), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = world.create();
      CharData data = CharData.createRemote("druid-aura-values", (byte) Riiablo.DRUID);
      world.getMapper(Player.class).create(owner).data = data;
      world.getMapper(Position.class).create(owner).position.set(10, 10);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(20, 100);
      world.getMapper(UnitStates.class).create(owner).init(owner);

      int[] skills = {SkillId.OAK_SAGE, SkillId.HEART_OF_WOLVERINE, SkillId.SPIRIT_OF_BARBS};
      int[] sources = new int[skills.length];
      for (int i = 0; i < skills.length; i++) {
        data.setSkillLevel(skills[i], 8);
        com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(skills[i]);
        world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
            owner, skills[i], Engine.INVALID_ENTITY, new Vector2(10, 10),
            skill.srvdofunc, skill.cltdofunc));
        sources[i] = factory.lastEntity;
      }
      world.setDelta(1f / 25f);
      world.process();

      UnitStates ownerStates = world.getMapper(UnitStates.class).get(owner);
      UnitState oak = ownerStates.stateList.getStateLayer(
          StateId.OAKSAGE, sources[0], SkillId.OAK_SAGE);
      UnitState wolverine = ownerStates.stateList.getStateLayer(
          StateId.WOLVERINE, sources[1], SkillId.HEART_OF_WOLVERINE);
      UnitState barbs = ownerStates.stateList.getStateLayer(
          StateId.BARBS, sources[2], SkillId.SPIRIT_OF_BARBS);
      assertEquals(65, oak.maxLifeModifier,
          "Oak Sage must use linked Oak Sage Aura ln34");
      assertEquals(74, wolverine.resolvedAttackModifier(),
          "Wolverine must use linked aura attack ln34");
      assertEquals(69, wolverine.resolvedDamageModifier(),
          "Wolverine must use linked aura damage ln56");
      assertEquals(120, barbs.runtimeValue,
          "Spirit of Barbs must use linked aura thorns ln34");

      world.getMapper(Position.class).get(sources[1]).position.set(100, 100);
      world.process();
      assertTrue(ownerStates.stateList.getStateLayer(
          StateId.WOLVERINE, sources[1], SkillId.HEART_OF_WOLVERINE) == null,
          "moving the spirit outside its aura range must revoke its owner layer");

      world.delete(sources[2]);
      world.process();
      assertTrue(ownerStates.stateList.getStateLayer(
          StateId.BARBS, sources[2], SkillId.SPIRIT_OF_BARBS) == null,
          "removing the spirit must revoke its source-owned layer");
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
