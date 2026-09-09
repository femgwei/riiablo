package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** ECS contract for target-centred, server-authoritative Necromancer curses. */
class NecromancerCurseIntegrationTest extends RiiabloTest {
  @Test
  void amplifyDamageUsesTargetCentreAndFeedsPhysicalResistance() {
    NoopFactory factory = new NoopFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      com.riiablo.codec.excel.Skills.Entry skill =
          Riiablo.files.skills.get(SkillId.AMPLIFY_DAMAGE);
      assertNotNull(skill);
      int owner = player(world, "necromancer", 0, 0);
      world.getMapper(Player.class).get(owner).data.setSkillLevel(skill.Id, 1);
      int range = SkillFormula.evaluate(skill.aurarangecalc, skill, 1);
      int selected = monster(world, 20, 20, 0);
      int nearby = monster(world, 20 + Math.max(1, range - 1), 20, 0);
      int outside = monster(world, 20 + range + 1, 20, 0);

      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, skill.Id, selected, new Vector2(20, 20), skill.srvdofunc, 0));

      int expected = SkillFormula.evaluate(skill.aurastatcalc[0], skill, 1);
      com.riiablo.engine.server.state.StateList selectedStates = states(world, selected);
      assertEquals(expected, selectedStates.getTotalPhysicalResistModifier());
      assertEquals(expected, states(world, nearby).getTotalPhysicalResistModifier());
      assertFalse(world.getMapper(UnitStates.class).has(outside),
          "a target outside the selected-point radius must remain untouched");

      Attributes attacker = attributes(100);
      CombatSystem.CombatResult cursed = CombatSystem.INSTANCE.calculateAttack(
          attacker, world.getMapper(AttributesWrapper.class).get(selected).attrs,
          true, false, false, 100, 100, 1000, true,
          null, null, 0, 0, null, selectedStates);
      assertEquals(100 * (100 - expected) / 100, cursed.physicalDamage,
          "negative damageresist must amplify physical damage");
    } finally {
      world.dispose();
    }
  }

  @Test
  void lowerResistUsesOneFifthPenaltyAgainstImmuneMonster() {
    com.riiablo.codec.excel.Skills.Entry skill =
        Riiablo.files.skills.get(SkillId.LOWER_RESIST);
    Attributes immune = attributes(100);
    immune.base().put(Stat.fireresist, 120);
    immune.reset();
    com.riiablo.engine.server.state.StateList states =
        new com.riiablo.engine.server.state.StateList(7);
    int fire = SkillFormula.evaluate(skill.aurastatcalc[0], skill, 1);
    int cold = SkillFormula.evaluate(skill.aurastatcalc[1], skill, 1);
    int lightning = SkillFormula.evaluate(skill.aurastatcalc[2], skill, 1);
    int poison = SkillFormula.evaluate(skill.aurastatcalc[3], skill, 1);
    com.riiablo.engine.server.skill.NecromancerSkills.applyCurse(
        states, Riiablo.files.States, skill, 1, 1,
        Riiablo.files.DifficultyLevels.get(0), immune, false);
    assertEquals(fire / 5, states.getTotalResistModifier(0));
    assertEquals(cold, states.getTotalResistModifier(1));
    assertEquals(lightning, states.getTotalResistModifier(2));
    assertEquals(poison, states.getTotalResistModifier(3));
  }

  @Test
  void attractAndConfuseUseTheirDedicatedServerFunctions() {
    NoopFactory factory = new NoopFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 1)));
    try {
      int owner = player(world, "necromancer", 0, 0);
      int target = monster(world, 10, 10, 0);
      CharData data = world.getMapper(Player.class).get(owner).data;
      data.diff = 1;
      com.riiablo.codec.excel.Skills.Entry attract =
          Riiablo.files.skills.get(SkillId.ATTRACT);
      com.riiablo.codec.excel.Skills.Entry confuse =
          Riiablo.files.skills.get(SkillId.CONFUSE);
      data.setSkillLevel(attract.Id, 1);
      data.setSkillLevel(confuse.Id, 1);

      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, attract.Id, target, new Vector2(10, 10), attract.srvdofunc, 0));
      assertTrue(states(world, target).hasState(StateId.ATTRACT));
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, confuse.Id, target, new Vector2(10, 10), confuse.srvdofunc, 0));
      assertTrue(states(world, target).hasState(StateId.CONFUSE));
      assertEquals(SkillFormula.evaluate(confuse.auralencalc, confuse, 1)
              / Riiablo.files.DifficultyLevels.get(1).AiCurseDivisor,
          states(world, target).getState(StateId.CONFUSE).duration);
    } finally {
      world.dispose();
    }
  }

  @Test
  void curseRejectsFriendlyPlayerCorpseAndOwnedSummon() {
    NoopFactory factory = new NoopFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      com.riiablo.codec.excel.Skills.Entry skill =
          Riiablo.files.skills.get(SkillId.AMPLIFY_DAMAGE);
      int owner = player(world, "necromancer", 10, 10);
      world.getMapper(Player.class).get(owner).data.setSkillLevel(skill.Id, 1);
      int selected = monster(world, 12, 10, 0);
      int friendly = player(world, "friendly", 12, 11);
      int corpse = monster(world, 12, 9, 0);
      world.getMapper(Corpse.class).create(corpse).reset(Corpse.DEFAULT_DURATION, true);
      int pet = monster(world, 11, 10, 0);
      world.getMapper(SummonedPet.class).create(pet)
          .set(owner, "golem", 0, 1, false, 0);

      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, skill.Id, selected, new Vector2(12, 10), skill.srvdofunc, 0));

      assertTrue(states(world, selected).hasState(StateId.AMPLIFYDAMAGE));
      assertFalse(states(world, friendly).hasState(StateId.AMPLIFYDAMAGE));
      assertFalse(world.getMapper(UnitStates.class).has(corpse));
      assertFalse(world.getMapper(UnitStates.class).has(pet));
    } finally {
      world.dispose();
    }
  }

  @Test
  void monsterCasterUsesTheSameDataDrivenCursePath() {
    NoopFactory factory = new NoopFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      com.riiablo.codec.excel.Skills.Entry skill =
          Riiablo.files.skills.get(SkillId.AMPLIFY_DAMAGE);
      int caster = monster(world, 0, 0, 0);
      int target = player(world, "target", 10, 10);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          caster, skill.Id, target, new Vector2(10, 10), skill.srvdofunc, 0));
      assertEquals(SkillFormula.evaluate(skill.aurastatcalc[0], skill, 1),
          states(world, target).getTotalPhysicalResistModifier());
    } finally {
      world.dispose();
    }
  }

  private static int player(World world, String name, float x, float y) {
    int id = world.create();
    world.getMapper(Player.class).create(id).data =
        CharData.createRemote(name, (byte) Riiablo.NECROMANCER);
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(100);
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static int monster(World world, float x, float y, int physicalResist) {
    int id = world.create();
    com.riiablo.codec.excel.MonStats.Entry row = Riiablo.files.monstats.get("fallen1");
    world.getMapper(Monster.class).create(id).monstats = row;
    world.getMapper(Position.class).create(id).position.set(x, y);
    Attributes attrs = attributes(100);
    attrs.base().put(Stat.damageresist, physicalResist);
    attrs.reset();
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
    return id;
  }

  private static com.riiablo.engine.server.state.StateList states(World world, int id) {
    UnitStates states = world.getMapper(UnitStates.class).get(id);
    assertNotNull(states);
    return states.stateList;
  }

  private static Attributes attributes(float hp) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.level, 10);
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.reset();
    return attrs;
  }

  private static final class NoopFactory extends EntityFactory {
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
