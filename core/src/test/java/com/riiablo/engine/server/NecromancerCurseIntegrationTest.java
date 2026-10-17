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
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.NativeAiTargetOverride;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.skill.NecromancerSkills;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import org.junit.jupiter.api.Test;

/** ECS contract for target-centred, server-authoritative Necromancer curses. */
class NecromancerCurseIntegrationTest extends RiiabloTest {
  @Test void ironMaidenUsesPacketAgainstOneLifeVictim() { ironOverkill(1f, 40.5f); }
  @Test void ironMaidenUsesPacketAgainstFractionalLifeVictim() { ironOverkill(.5f, 40.25f); }
  @Test void ironMaidenAndLifeTapKeepTheirIndependentBases() { ironOverkill(100f, 50f); }

  private static void ironOverkill(float victimLife, float expectedLife) {
    NoopFactory factory = new NoopFactory();
    IronProbe probe = new IronProbe();
    EventSystem events = new EventSystem();
    World world = new World(new WorldConfigurationBuilder()
        .with(events, probe, new StateUpdater(), factory).build()
        .register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int attacker = monster(world, 0, 0, 0);
      int defender = monster(world, 1, 0, 0);
      Attributes attackerAttrs = world.getMapper(AttributesWrapper.class).get(attacker).attrs;
      Attributes defenderAttrs = world.getMapper(AttributesWrapper.class).get(defender).attrs;
      attackerAttrs.get(Stat.hitpoints).set(50f);
      defenderAttrs.get(Stat.hitpoints).set(victimLife);
      UnitStates attackerStates = world.getMapper(UnitStates.class).create(attacker).init(attacker);
      UnitStates defenderStates = world.getMapper(UnitStates.class).create(defender).init(defender);
      NecromancerSkills.applyCurse(attackerStates.stateList, Riiablo.files.States,
          Riiablo.files.skills.get(SkillId.IRON_MAIDEN), 1, defender,
          Riiablo.files.DifficultyLevels.get(0), attackerAttrs, false);
      NecromancerSkills.applyCurse(defenderStates.stateList, Riiablo.files.States,
          Riiablo.files.skills.get(SkillId.LIFE_TAP), 1, attacker,
          Riiablo.files.DifficultyLevels.get(0), defenderAttrs, false);
      DamageEvent hit = DamageEvent.obtainMelee(attacker, defender, 20, 20);
      events.dispatch(hit);
      assertEquals(1, probe.reactive);
      assertEquals(10f, probe.physical);
      assertEquals(expectedLife, attackerAttrs.get(Stat.hitpoints).asFixed(), .0001f);
      assertEquals(20f, hit.damage);
      assertEquals(20f, hit.physicalDamage);
      assertEquals(0, probe.deaths);
    } finally { world.dispose(); }
  }

  @Test void ironMaidenSelectsFormulaForBothCombatants() {
    for (int attackerType = 0; attackerType < 3; attackerType++) {
      for (boolean defenderMonster : new boolean[] {false, true}) {
        NoopFactory factory = new NoopFactory();
        IronProbe probe = new IronProbe();
        EventSystem events = new EventSystem();
        World world = new World(new WorldConfigurationBuilder()
            .with(events, probe, new StateUpdater(), factory).build()
            .register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
        try {
          int attacker = attackerType == 0 ? player(world, "attacker", 0, 0)
              : monster(world, 0, 0, 0);
          if (attackerType == 2) world.getMapper(Mercenary.class).create(attacker);
          int defender = defenderMonster ? monster(world, 1, 0, 0)
              : player(world, "defender", 1, 0);
          UnitStates curse = world.getMapper(UnitStates.class).has(attacker)
              ? world.getMapper(UnitStates.class).get(attacker)
              : world.getMapper(UnitStates.class).create(attacker).init(attacker);
          com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(SkillId.IRON_MAIDEN);
          NecromancerSkills.applyCurse(curse.stateList, Riiablo.files.States, skill,
              1, defender, Riiablo.files.DifficultyLevels.get(0),
              world.getMapper(AttributesWrapper.class).get(attacker).attrs, attackerType != 1);
          String formula = attackerType == 0 ? skill.calc1 : defenderMonster ? skill.calc2 : skill.calc3;
          int percent = SkillFormula.evaluate(formula, skill, 1);
          int expected = 20 * percent / 100;
          if (attackerType == 0 && !defenderMonster) expected = expected * 17 / 100;
          System.out.println("IronMaiden attackerType=" + attackerType + " defenderMonster="
              + defenderMonster + " formula=" + formula + " percent=" + percent);
          events.dispatch(DamageEvent.obtainMelee(attacker, defender, 20, 20));
          assertEquals(expected, probe.physical, .0001f);
          assertEquals(expected > 0 ? 1 : 0, probe.reactive);
          assertEquals(100f - expected, world.getMapper(AttributesWrapper.class)
              .get(attacker).attrs.get(Stat.hitpoints).asFixed(), .0001f);
        } finally { world.dispose(); }
      }
    }
  }
  @Test void ironMaidenFractionalRemainderDies() { ironBoundary(10f + 255f / 256, 0f, false, false); }
  @Test void ironMaidenExactlyOneLifeSurvives() { ironBoundary(11f, 1f, false, false); }
  @Test void ironMaidenAboveOneLifeSurvives() { ironBoundary(11f + 1f / 256, 1f + 1f / 256, false, false); }
  @Test void cancelledIronMaidenPreservesFractionalLife() { ironBoundary(.5f, .5f, true, false); }
  @Test void immuneIronMaidenPreservesFractionalLife() { ironBoundary(.5f, .5f, false, true); }

  private static void ironBoundary(float initial, float expected, boolean cancel, boolean immune) {
    NoopFactory factory = new NoopFactory();
    IronProbe probe = new IronProbe();
    EventSystem events = new EventSystem();
    World world = new World(new WorldConfigurationBuilder()
        .with(events, probe, new StateUpdater(), factory).build()
        .register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int defender = monster(world, 1, 0, 0);
      int attacker = monster(world, 2, 0, immune ? 100 : 0);
      UnitStates curse = world.getMapper(UnitStates.class).create(attacker).init(attacker);
      NecromancerSkills.applyCurse(curse.stateList, Riiablo.files.States,
          Riiablo.files.skills.get(SkillId.IRON_MAIDEN), 1, defender,
          Riiablo.files.DifficultyLevels.get(0),
          world.getMapper(AttributesWrapper.class).get(attacker).attrs, false);
      world.getMapper(AttributesWrapper.class).get(attacker).attrs
          .get(Stat.hitpoints).set(initial);
      probe.cancel = cancel;
      events.dispatch(DamageEvent.obtainMelee(attacker, defender, 20, 20));
      assertEquals(expected, world.getMapper(AttributesWrapper.class).get(attacker).attrs
          .get(Stat.hitpoints).asFixed(), .0001f);
      assertEquals(immune ? 0 : 1, probe.reactive);
      assertEquals(expected == 0f ? 1 : 0, probe.deaths);
      if (!immune) assertEquals(10f, probe.physical);
      if (expected == 0f) {
        assertEquals(defender, probe.killer);
        assertEquals(attacker, probe.victim);
      }
    } finally {
      world.dispose();
    }
  }

  private static final class IronProbe extends PassiveSystem {
    int reactive, deaths, killer, victim;
    float physical;
    boolean cancel;
    @Subscribe public void damage(DamageEvent event) {
      if (event.kind != DamageEvent.REACTIVE) return;
      reactive++;
      physical = event.physicalDamage;
      if (cancel) event.damage = 0f;
    }
    @Subscribe public void death(DeathEvent event) {
      deaths++;
      killer = event.killer;
      victim = event.victim;
    }
  }

  @Test void castIronMaidenReflectsFromCursedAttacker() { ironCastDirection(true, false); }
  @Test void cursedVictimDoesNotReflectIronMaiden() { ironCastDirection(false, false); }
  @Test void snapshotAttackerDoesNotRepeatIronMaiden() { ironCastDirection(true, true); }

  private static void ironCastDirection(boolean curseAttacker, boolean snapshot) {
    NoopFactory factory = new NoopFactory();
    IronProbe probe = new IronProbe();
    EventSystem events = new EventSystem();
    World world = new World(new WorldConfigurationBuilder()
        .with(events, probe, new StateUpdater(), new ServerSkillSystem(true), factory).build()
        .register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int owner = player(world, "necromancer", 0, 0);
      int attacker = monster(world, 10, 0, 0);
      int defender = monster(world, 50, 0, 0);
      com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(SkillId.IRON_MAIDEN);
      world.getMapper(Player.class).get(owner).data.setSkillLevel(skill.Id, 1);
      int cursed = curseAttacker ? attacker : defender;
      events.dispatch(SkillDoEvent.obtain(owner, skill.Id, cursed,
          world.getMapper(Position.class).get(cursed).position, skill.srvdofunc, 0));
      assertTrue(states(world, cursed).hasState(StateId.IRONMAIDEN));
      world.getMapper(UnitStates.class).get(cursed).snapshotOnly = snapshot;
      // Explicit melee event: tests cast/state/reaction, not range or hit rolls.
      events.dispatch(DamageEvent.obtainMelee(attacker, defender, 20, 20));
      boolean reflects = curseAttacker && !snapshot;
      assertEquals(reflects ? 90f : 100f, world.getMapper(AttributesWrapper.class)
          .get(attacker).attrs.get(Stat.hitpoints).asFixed());
      assertEquals(reflects ? 1 : 0, probe.reactive);
      events.dispatch(DamageEvent.obtainMissile(attacker, defender, 20, 20, null));
      assertEquals(reflects ? 1 : 0, probe.reactive);
    } finally {
      world.dispose();
    }
  }

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
      CombatSystem.CombatResult cursed = new CombatSystem().calculateAttack(
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
      int nearby = monster(world, 1, 0, 0);
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
      NativeAiTargetOverride redirected = world.getMapper(NativeAiTargetOverride.class)
          .get(nearby);
      assertNotNull(redirected, "Attract must redirect nearby switch-AI monsters");
      assertEquals(NativeAiTargetOverride.ATTRACT, redirected.mode);
      assertEquals(target, redirected.targetId);
      assertFalse(world.getMapper(NativeAiTargetOverride.class).has(target),
          "the attracted monster must not be redirected to attack itself");
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, confuse.Id, target, new Vector2(10, 10), confuse.srvdofunc, 0));
      assertTrue(states(world, target).hasState(StateId.CONFUSE));
      assertEquals(NativeAiTargetOverride.CONFUSE,
          world.getMapper(NativeAiTargetOverride.class).get(target).mode);
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

  @Test
  void ironMaidenReflectsOnlyMeleePhysicalDamageAndLifeTapRestoresAttacker() {
    NoopFactory factory = new NoopFactory();
    EventSystem eventSystem = new EventSystem();
    World world = new World(new WorldConfigurationBuilder()
        .with(eventSystem, new StateUpdater(), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int attacker = player(world, "attacker", 0, 0);
      int defender = monster(world, 1, 0, 0);
      Attributes attackerAttrs = world.getMapper(AttributesWrapper.class).get(attacker).attrs;
      attackerAttrs.base().put(Stat.hitpoints, 100);
      attackerAttrs.base().put(Stat.maxhp, 100);
      attackerAttrs.reset();
      UnitStates defenderStates = world.getMapper(UnitStates.class).create(defender).init(defender);
      com.riiablo.codec.excel.Skills.Entry lifeTap = Riiablo.files.skills.get(SkillId.LIFE_TAP);
      NecromancerSkills.applyCurse(defenderStates.stateList, Riiablo.files.States,
          lifeTap, 1, defender, Riiablo.files.DifficultyLevels.get(0),
          world.getMapper(AttributesWrapper.class).get(defender).attrs, false);
      world.getMapper(AttributesWrapper.class).get(attacker).attrs.base().put(Stat.hitpoints, 20);
      world.getMapper(AttributesWrapper.class).get(attacker).attrs.reset();
      defenderStates.snapshotOnly = true;
      eventSystem.dispatch(DamageEvent.obtainMelee(attacker, defender, 20, 20));
      assertEquals(20, world.getMapper(AttributesWrapper.class).get(attacker).attrs
          .get(Stat.hitpoints).asInt(), "snapshot-only client must not repeat Life Tap healing");
      defenderStates.snapshotOnly = false;
      eventSystem.dispatch(DamageEvent.obtainMissile(attacker, defender, 20, 20, null));
      assertEquals(30, world.getMapper(AttributesWrapper.class).get(attacker).attrs
          .get(Stat.hitpoints).asInt(), "Life Tap must restore 50% of physical attack damage");

      int reflectedTarget = monster(world, 2, 0, 0);
      int monsterAttacker = monster(world, 3, 0, 0);
      UnitStates ironStates = world.getMapper(UnitStates.class).create(monsterAttacker).init(monsterAttacker);
      com.riiablo.codec.excel.Skills.Entry iron = Riiablo.files.skills.get(SkillId.IRON_MAIDEN);
      NecromancerSkills.applyCurse(ironStates.stateList, Riiablo.files.States,
          iron, 1, reflectedTarget, Riiablo.files.DifficultyLevels.get(0),
          world.getMapper(AttributesWrapper.class).get(monsterAttacker).attrs, false);
      world.getMapper(AttributesWrapper.class).get(monsterAttacker).attrs.base().put(Stat.hitpoints, 100);
      world.getMapper(AttributesWrapper.class).get(monsterAttacker).attrs.base().put(Stat.maxhp, 100);
      world.getMapper(AttributesWrapper.class).get(monsterAttacker).attrs.reset();
      eventSystem.dispatch(DamageEvent.obtainMelee(monsterAttacker, reflectedTarget, 20, 20));
      assertEquals(90, world.getMapper(AttributesWrapper.class).get(monsterAttacker).attrs
          .get(Stat.hitpoints).asInt(), "monster attacker uses calc2=50% physical damage");
      eventSystem.dispatch(DamageEvent.obtainMissile(
          monsterAttacker, reflectedTarget, 20, 20, null));
      assertEquals(90, world.getMapper(AttributesWrapper.class).get(monsterAttacker).attrs
          .get(Stat.hitpoints).asInt(), "Iron Maiden must not reflect missile damage");

      attackerAttrs.base().put(Stat.hitpoints, 100);
      attackerAttrs.reset();
      NecromancerSkills.applyCurse(states(world, attacker), Riiablo.files.States,
          iron, 1, reflectedTarget, Riiablo.files.DifficultyLevels.get(0), attackerAttrs, false);
      eventSystem.dispatch(DamageEvent.obtainMelee(attacker, reflectedTarget, 20, 20));
      assertEquals(60, attackerAttrs.get(Stat.hitpoints).asInt(),
          "player attacker must use the native calc1 branch");
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
