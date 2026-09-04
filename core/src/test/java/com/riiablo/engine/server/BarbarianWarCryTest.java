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
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.monster.MonsterRank;
import com.riiablo.engine.server.skill.BarbarianSkills;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Pure native-data and special-state regression coverage for barbarian war cries. */
class BarbarianWarCryTest extends RiiabloTest {
  @Test
  void howlAndWarCryApplyNativeRuntimeValues() {
    Skills.Entry howl = skill("Howl");
    StateList states = new StateList(10);
    UnitState terror = BarbarianSkills.applyHowlState(states, howl, 1, 2, 3, 7, true);
    assertNotNull(terror);
    assertEquals(StateId.TERROR, terror.stateId);
    assertEquals(75, terror.duration);
    assertEquals(24, terror.runtimeValue);
    assertFalse(BarbarianSkills.canHowlTarget(howl, 1, 1, 3));

    Skills.Entry shout = skill("Shout");
    states = new StateList(11);
    UnitState shoutState = BarbarianSkills.applyWarCryState(
        states, shout, 2, 7, false, name -> 0);
    assertNotNull(shoutState);
    assertEquals(StateId.SHOUT, shoutState.stateId);
    assertEquals(750, shoutState.duration);
    assertEquals(110, shoutState.defenseModifier);

    Skills.Entry taunt = skill("Taunt");
    states = new StateList(12);
    UnitState tauntState = BarbarianSkills.applyWarCryState(
        states, taunt, 3, 7, true, name -> 0);
    assertNotNull(tauntState);
    assertEquals(0, tauntState.duration, "Taunt has an empty AuraLenCalc in native Skills.txt");
    assertEquals(-9, tauntState.attackModifier);
    assertEquals(-9, tauntState.damageModifier);

    Skills.Entry battleCry = skill("Battle Cry");
    states = new StateList(13);
    UnitState battleState = BarbarianSkills.applyWarCryState(
        states, battleCry, 3, 7, true, name -> 0);
    assertNotNull(battleState);
    assertEquals(StateId.BATTLECRY, battleState.stateId);
    assertEquals(420, battleState.duration);
    assertEquals(-54, battleState.defenseModifier);
    assertEquals(-27, battleState.damageModifier);
  }

  @Test
  void canSwitchAiMatchesNativeWalkSwitchAndRankGates() {
    Monster monster = new Monster().set(new MonStats.Entry(), new MonStats2.Entry());
    monster.monstats.switchai = true;
    monster.monstats.boss = false;
    monster.monstats2.mMode = new boolean[16];
    monster.monstats2.mMode[Engine.Monster.MODE_WL] = true;
    assertTrue(BarbarianSkills.canSwitchWarCryAi(monster, new StateList(1)));

    monster.monstats2.mMode[Engine.Monster.MODE_WL] = false;
    assertFalse(BarbarianSkills.canSwitchWarCryAi(monster, null));
    monster.monstats2.mMode[Engine.Monster.MODE_WL] = true;
    monster.rank = MonsterRank.SUPER_UNIQUE;
    assertFalse(BarbarianSkills.canSwitchWarCryAi(monster, null));
    monster.rank = MonsterRank.NORMAL;
    monster.monstats.boss = true;
    assertFalse(BarbarianSkills.canSwitchWarCryAi(monster, null));
    monster.monstats.boss = false;
    StateList blocked = new StateList(1);
    blocked.addState(StateId.UNINTERRUPTABLE, 20);
    assertFalse(BarbarianSkills.canSwitchWarCryAi(monster, blocked));
  }

  @Test
  void howlMissileAppliesTerrorWithoutOrdinaryDamage() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new MissileCollisionSystem()).build());
    try {
      int barbarian = world.create();
      world.getMapper(Player.class).create(barbarian);
      world.getMapper(Position.class).create(barbarian).position.set(0, 0);
      world.getMapper(AttributesWrapper.class).create(barbarian).attrs = attributes(2, 100);

      MonStats.Entry fallen = Riiablo.files.monstats.get("fallen1");
      assertNotNull(fallen);
      MonStats2.Entry fallen2 = Riiablo.files.monstats2.get(fallen.MonStatsEx);
      assertNotNull(fallen2);
      assertTrue(fallen.switchai);
      assertTrue(fallen2.mMode[Engine.Monster.MODE_WL]);
      int target = world.create();
      world.getMapper(Monster.class).create(target).set(fallen, fallen2);
      world.getMapper(Position.class).create(target).position.set(1, 0);
      world.getMapper(AttributesWrapper.class).create(target).attrs = attributes(3, 100);
      world.getMapper(UnitStates.class).create(target).init(target);

      Skills.Entry howl = skill("Howl");
      Missiles.Entry row = Riiablo.files.Missiles.get(howl.srvmissilea);
      assertNotNull(row);
      int missileId = world.create();
      Missile missile = world.getMapper(Missile.class).create(missileId)
          .set(row, new Vector2(1, 0), row.Range).setOwner(barbarian);
      missile.skillId = howl.Id;
      missile.damageLevel = 1;
      world.getMapper(Position.class).create(missileId).position.set(1, 0);
      world.getMapper(Velocity.class).create(missileId).velocity.setZero();

      world.setDelta(1f / 25f);
      world.process();

      UnitState terror = world.getMapper(UnitStates.class).get(target)
          .stateList.getState(StateId.TERROR);
      assertNotNull(terror);
      assertEquals(75, terror.duration);
      assertEquals(24, terror.runtimeValue);
      assertEquals(100f, world.getMapper(AttributesWrapper.class).get(target)
          .attrs.get(Stat.hitpoints).asFixed(), 0.001f);
    } finally {
      world.dispose();
    }
  }

  private static Attributes attributes(int level, float hp) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.level, level);
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.reset();
    return attrs;
  }

  private static Skills.Entry skill(String name) {
    Skills.Entry skill = Riiablo.files.skills.get(name);
    assertNotNull(skill, name);
    return skill;
  }
}
