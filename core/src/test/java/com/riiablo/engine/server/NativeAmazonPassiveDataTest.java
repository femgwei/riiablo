package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.skill.AmazonSkills;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.save.CharData;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Native 1.10f data and ECS coverage for Amazon permanent passives. */
class NativeAmazonPassiveDataTest extends RiiabloTest {
  @Test
  void skillIdsAndPassiveRowsMatchNativeSkillsTxt() {
    assertEquals(9, SkillId.CRITICAL_STRIKE);
    assertEquals(13, SkillId.DODGE);
    assertEquals(18, SkillId.AVOID);
    assertEquals(23, SkillId.PENETRATE);
    assertEquals(29, SkillId.EVADE);
    assertEquals(33, SkillId.PIERCE);

    assertPassiveRow("Critical Strike", 9, StateId.CRITICALSTRIKE, "criticalstrike",
        "passive_critical_strike", "dm12", 5, 80);
    assertPassiveRow("Dodge", 13, StateId.DODGE, "dodge",
        "passive_dodge", "dm12", 10, 65);
    assertPassiveRow("Avoid", 18, StateId.AVOID, "avoid",
        "passive_avoid", "dm12", 15, 75);
    assertPassiveRow("Penetrate", 23, StateId.PENETRATE, "penetrate",
        "item_tohit_percent", "ln12", 35, 10);
    assertPassiveRow("Evade", 29, StateId.EVADE, "evade",
        "passive_evade", "dm12", 10, 65);
    assertPassiveRow("Pierce", 33, StateId.PIERCE, "pierce",
        "skill_pierce", "dm12", 10, 100);
  }

  @Test
  void nativePassiveFormulasMatchAtRepresentativeLevels() {
    int[] levels = {1, 5, 10, 20};
    int[] critical = {16, 42, 56, 68};
    int[] dodge = {18, 37, 47, 56};
    int[] avoid = {24, 45, 56, 65};
    int[] penetrate = {35, 75, 125, 225};
    int[] pierce = {24, 55, 71, 86};
    for (int i = 0; i < levels.length; i++) {
      int level = levels[i];
      assertEquals(critical[i], AmazonSkills.getCriticalStrikeChance(level));
      assertEquals(dodge[i], AmazonSkills.getDodgeChance(level));
      assertEquals(avoid[i], AmazonSkills.getAvoidChance(level));
      assertEquals(penetrate[i], AmazonSkills.calculatePenetrateBonus(level));
      assertEquals(dodge[i], AmazonSkills.getEvadeChance(level));
      assertEquals(pierce[i], AmazonSkills.getPierceChance(level));
    }
  }

  @Test
  void passiveStateBridgeUsesNativeStatsAndStateIds() {
    StateList states = new StateList(7);
    UnitState critical = AmazonSkills.applyPassiveState(
        states, skill("Critical Strike"), 10, 7);
    UnitState avoid = AmazonSkills.applyPassiveState(
        states, skill("Avoid"), 10, 7);
    UnitState penetrate = AmazonSkills.applyPassiveState(
        states, skill("Penetrate"), 10, 7);
    UnitState pierce = AmazonSkills.applyPassiveState(
        states, skill("Pierce"), 10, 7);

    assertEquals(StateId.CRITICALSTRIKE, critical.stateId);
    assertEquals(56, critical.getStatContributionValue(Stat.passive_critical_strike));
    assertEquals(56, avoid.getStatContributionValue(Stat.passive_avoid));
    assertEquals(125, penetrate.getStatContributionValue(Stat.item_tohit_percent));
    assertEquals(71, pierce.getStatContributionValue(Stat.skill_pierce));
    assertEquals(56, states.getTotalStatContribution(Stat.passive_critical_strike));
    assertEquals(56, states.getTotalStatContribution(Stat.passive_avoid));
  }

  @Test
  void stateUpdaterRefreshesLearnedAmazonPassives() {
    NoopFactory factory = new NoopFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new StateUpdater(), factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
    try {
      int amazon = world.create();
      CharData data = CharData.createRemote("amazon", (byte) Riiablo.AMAZON);
      data.setSkillLevel(SkillId.AVOID, 10);
      world.getMapper(Player.class).create(amazon).data = data;
      world.getMapper(UnitStates.class).create(amazon).init(amazon);

      world.process();

      UnitState avoid = world.getMapper(UnitStates.class).get(amazon)
          .stateList.getState(StateId.AVOID);
      assertNotNull(avoid);
      assertEquals(10, avoid.level);
      assertEquals(56, avoid.getStatContributionValue(Stat.passive_avoid));
    } finally {
      world.dispose();
    }
  }

  private static Skills.Entry skill(String name) {
    Skills.Entry skill = Riiablo.files.skills.get(name);
    assertNotNull(skill, name);
    return skill;
  }

  private static void assertPassiveRow(String name, int id, int stateId, String stateName,
      String stat, String formula, int param1, int param2) {
    Skills.Entry skill = skill(name);
    assertEquals(id, skill.Id, name);
    assertEquals(true, skill.passive, name);
    assertEquals(stateName, skill.passivestate, name);
    assertEquals(stat, skill.passivestat[0], name);
    assertEquals(formula, skill.passivecalc[0], name);
    assertEquals(param1, skill.Param[0], name);
    assertEquals(param2, skill.Param[1], name);
    assertEquals(stateId, AmazonSkills.getPassiveStateId(skill), name);
  }

  private static final class NoopFactory extends EntityFactory {
    @Override public int createPlayer(CharData data, Vector2 position) { return -1; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return -1; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return -1; }
    @Override public int createMonster(int id, float x, float y) { return -1; }
    @Override public int createWarp(int index, float x, float y) { return -1; }
    @Override public int createItem(Item item, float x, float y) { return -1; }
    @Override public int createMissile(int id, Vector2 angle, Vector2 position) { return -1; }
  }
}
