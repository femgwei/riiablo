package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.skill.AssassinSkills;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class AssassinNecromancerPassiveIntegrationTest extends RiiabloTest {
  @Test void masteryUsesRetailFormulasAndOnlyMatchesClawAttacks() {
    StateList states = new StateList(7);
    Skills.Entry row = Riiablo.files.skills.get(252);
    UnitState state = AssassinSkills.applyPassiveState(states, row, 2, 7, true);
    assertEquals(40, state.masteryAttackRatingModifier);
    assertEquals(39, state.masteryDamageModifier);
    StateList.WeaponMasteryBonus bonus = new StateList.WeaponMasteryBonus();
    states.getWeaponMastery(weapon("ktr"), false, bonus);
    assertEquals(40, bonus.attackRatingPercent);
    assertEquals(39, bonus.damagePercent);
    assertEquals(SkillFormula.evaluate(row.passivecalc[2], row, 2), bonus.criticalChance);
    states.getWeaponMastery(weapon("ssd"), false, bonus);
    assertTrue(bonus.isEmpty());
    states.getWeaponMastery(weapon("ktr"), true, bonus);
    assertTrue(bonus.isEmpty());
  }

  @Test void nativeWeaponBlockStateActuallyBlocksProductionCombatAndSkipsMovement() {
    Attributes attacker = attributes(20, 100), defender = attributes(20, 100);
    StateList states = new StateList(2);
    UnitState block = states.addState(StateId.WEAPONBLOCK, 0, 1, 2);
    block.setStatContribution(Stat.passive_weaponblock, 0,
        com.riiablo.attributes.NativeStatResolver.Operation.ADD, 50);
    CombatSystem combat = new CombatSystem(bound -> 0);
    CombatSystem.CombatResult result = combat.calculateAttack(attacker, defender,
        true, true, false, 10, 10, 100, true,
        null, null, 0, 0, null, states, false, null);
    assertTrue(result.blocked);
    assertEquals(0, result.totalDamage);
    result = combat.calculateAttack(attacker, defender,
        true, true, false, 10, 10, 100, true,
        null, null, 0, 0, null, states, true, null);
    assertFalse(result.blocked);
    assertTrue(result.totalDamage > 0);
  }

  @Test void psychicHammerDamagesOnlyItsUnitTargetAndMindBlastUsesGroundArea() {
    NoopFactory factory = new NoopFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new StateUpdater(), new ServerSkillSystem(true), factory).build()
        .register("factory", factory).register("map", new Map(0, 0)));
    try {
      int source = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      data.setSkillLevel(253, 1); data.setSkillLevel(273, 1);
      world.getMapper(Player.class).create(source).data = data;
      world.getMapper(Position.class).create(source).position.set(0, 0);
      world.getMapper(AttributesWrapper.class).create(source).attrs = attributes(20, 100);
      int target = target(world, 5, 0), other = target(world, 5, 1), far = target(world, 20, 0);
      Skills.Entry hammer = Riiablo.files.skills.get(253), blast = Riiablo.files.skills.get(273);
      world.process();
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(source,
          hammer.Id, target, null, hammer.srvdofunc, hammer.cltdofunc));
      assertTrue(hp(world, target) < 100);
      assertEquals(100f, hp(world, other));
      float before = hp(world, target);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(source,
          hammer.Id, -1, new Vector2(5, 0), hammer.srvdofunc, hammer.cltdofunc));
      assertEquals(before, hp(world, target), "Psychic Hammer cannot fall back to coordinates");
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(source,
          blast.Id, -1, new Vector2(5, 0), blast.srvdofunc, blast.cltdofunc));
      assertTrue(hp(world, target) < before);
      assertTrue(hp(world, other) < 100);
      assertEquals(100f, hp(world, far));
      assertNull(world.getMapper(UnitStates.class).get(other).stateList.getState(StateId.STUNNED),
          "native bosses cannot be stunned");
    } finally { world.dispose(); }
  }

  @Test void mindBlastConversionSkipsDamageAndRestoresItsLifeAndLevelOnRemoval() {
    NoopFactory factory = new NoopFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), factory).build()
        .register("factory", factory).register("map", new Map(0, 0)));
    try {
      int source = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      data.setSkillLevel(273, 1);
      world.getMapper(Player.class).create(source).data = data;
      world.getMapper(Position.class).create(source).position.set(0, 0);
      world.getMapper(AttributesWrapper.class).create(source).attrs = attributes(10, 100);
      int target = target(world, 5, 0);
      Monster monster = world.getMapper(Monster.class).get(target);
      monster.monstats = Riiablo.files.monstats.get("fallen1");
      monster.monstats2 = Riiablo.files.monstats2.get(monster.monstats.MonStatsEx);
      Skills.Entry blast = Riiablo.files.skills.get(273);
      world.process();
      // Choose a native pSeed whose second draw passes the retail conversion roll.
      NativeUnitRandom rng = NativeUnitRandom.unit(world, Riiablo.gameSeed, source);
      NativeUnitRandom.Snapshot chosen = null;
      int chance = com.riiablo.engine.server.skill.NecromancerSkills
          .nativeDiminishingPercent(blast, 1, 4, 5);
      for (int low = 1; low < 10000 && chosen == null; low++) {
        NativeUnitRandom.Snapshot candidate = new NativeUnitRandom.Snapshot(low, 666);
        rng.restore(candidate); rng.nextInt(10);
        if (rng.nextInt(100) <= chance) chosen = candidate;
      }
      assertNotNull(chosen); rng.restore(chosen);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(source,
          blast.Id, -1, new Vector2(5, 0), blast.srvdofunc, blast.cltdofunc));
      assertTrue(monster.converted);
      assertEquals(source, monster.conversionOwnerId);
      Attributes attrs = world.getMapper(AttributesWrapper.class).get(target).attrs;
      assertEquals(10, attrs.get(Stat.level).asInt());
      assertEquals(50f, attrs.get(Stat.hitpoints).asFixed());
      StateList states = world.getMapper(UnitStates.class).get(target).stateList;
      UnitState conversion = states.getState(StateId.CONVERSION);
      assertNotNull(conversion); assertTrue(conversion.duration >= 150 && conversion.duration < 250);
      states.removeState(StateId.CONVERSION);
      assertFalse(monster.converted);
      assertEquals(20, attrs.get(Stat.level).asInt());
      assertEquals(100f, attrs.get(Stat.hitpoints).asFixed());
      assertNull(states.getState(StateId.CONVERSION_SAVE));
    } finally { world.dispose(); }
  }

  @Test void summonCreationCopiesNativeOwnerResistanceAndShadowLevelLifeAndStats() {
    NoopFactory factory = new NoopFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new StateUpdater(), new ServerSkillSystem(true), factory).build()
        .register("factory", factory).register("map", new Map(0, 0)));
    try {
      int source = world.create();
      CharData data = CharData.createRemote("necro", (byte) Riiablo.NECROMANCER);
      data.setSkillLevel(89, 1); data.setSkillLevel(75, 1);
      world.getMapper(Player.class).create(source).data = data;
      world.getMapper(Position.class).create(source).position.set(0, 0);
      world.getMapper(AttributesWrapper.class).create(source).attrs = attributes(20, 100);
      world.getMapper(UnitStates.class).create(source).init(source);
      world.process();
      Skills.Entry golem = Riiablo.files.skills.get(75);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(source,
          golem.Id, -1, new Vector2(5, 0), golem.srvdofunc, golem.cltdofunc));
      Attributes pet = world.getMapper(AttributesWrapper.class).get(factory.lastPet).attrs;
      assertEquals(28, pet.get(Stat.fireresist).asInt());
      assertEquals(28, pet.get(Stat.coldresist).asInt());
      assertEquals(28, pet.get(Stat.lightresist).asInt());
      assertEquals(28, pet.get(Stat.poisonresist).asInt());
      data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      data.setSkillLevel(268, 3);
      world.getMapper(Player.class).get(source).data = data;
      Skills.Entry shadow = Riiablo.files.skills.get(268);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(source,
          shadow.Id, -1, new Vector2(5, 0), shadow.srvdofunc, shadow.cltdofunc));
      pet = world.getMapper(AttributesWrapper.class).get(factory.lastPet).attrs;
      assertEquals(20, pet.get(Stat.level).asInt());
      assertEquals(13f, pet.get(Stat.maxhp).asFixed());
      assertEquals(SkillFormula.evaluate(shadow.passivecalc[1], shadow, 3), pet.get(Stat.fireresist).asInt());
      assertTrue(pet.get(Stat.tohit).asInt()
          >= SkillFormula.evaluate(shadow.aurastatcalc[1], shadow, 3),
          "MonEquip weapon affixes can add AR after the native shadow stat list");
    } finally { world.dispose(); }
  }

  private static int target(World world, float x, float y) {
    int id = world.create();
    // A boss cannot be converted, so this test deterministically exercises the damage branch.
    Monster monster = world.getMapper(Monster.class).create(id);
    monster.monstats = Riiablo.files.monstats.get("diablo");
    monster.monstats2 = Riiablo.files.monstats2.get(monster.monstats.MonStatsEx);
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(20, 100);
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static float hp(World world, int id) {
    return world.getMapper(AttributesWrapper.class).get(id).attrs.get(Stat.hitpoints).asFixed();
  }

  private static Attributes attributes(int level, float hp) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.level, level);
    attrs.base().put(Stat.hitpoints, hp); attrs.base().put(Stat.maxhp, hp);
    attrs.reset(); return attrs;
  }

  @Test void passiveRefreshTracksLearningEquipmentAndBattleCommandWithoutCompounding() {
    NoopFactory factory = new NoopFactory();
    StateUpdater updater = new StateUpdater();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), updater, factory).build()
        .register("factory", factory).register("map", new Map(0, 0)));
    try {
      int id = world.create();
      CharData data = CharData.createRemote("assassin", (byte) Riiablo.ASSASSIN);
      data.setSkillLevel(252, 2);
      data.setSkillLevel(263, 2);
      world.getMapper(Player.class).create(id).data = data;
      StateList states = world.getMapper(UnitStates.class).create(id).init(id).stateList;
      world.process();
      assertNotNull(states.getState(StateId.CLAWMASTERY));
      assertNull(states.getState(StateId.WEAPONBLOCK));
      Item claw = weapon("ktr");
      data.getItems().equipItem(BodyLoc.RARM, data.getItems().add(claw));
      updater.refreshAfterSkillMove(id);
      int expected = SkillFormula.evaluate(Riiablo.files.skills.get(263).passivecalc[0],
          Riiablo.files.skills.get(263), 2);
      assertEquals(expected, states.getTotalStatContribution(Stat.passive_weaponblock));
      updater.refreshAfterSkillMove(id);
      assertEquals(expected, states.getTotalStatContribution(Stat.passive_weaponblock));
      data.getItems().unequipItem(BodyLoc.RARM);
      data.getItems().equipItem(BodyLoc.RARM, data.getItems().add(weapon("ssd")));
      updater.refreshAfterSkillMove(id);
      assertNull(states.getState(StateId.WEAPONBLOCK));
      UnitState command = states.addState(StateId.BATTLECOMMAND, 30, 1, id);
      command.skillModifier = 1;
      updater.refreshAfterSkillMove(id);
      assertEquals(3, states.getState(StateId.CLAWMASTERY).level);
      assertEquals(30, command.duration);
      data.setSkillLevel(252, 0);
      updater.refreshAfterSkillMove(id);
      assertNull(states.getState(StateId.CLAWMASTERY));
    } finally { world.dispose(); }
  }

  @Test void summonResistIsNativeDiminishingOwnerStatAndRemovesWithTheLearnedSkill() {
    NoopFactory factory = new NoopFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new StateUpdater(), factory).build()
        .register("factory", factory).register("map", new Map(0, 0)));
    try {
      int id = world.create();
      CharData data = CharData.createRemote("necro", (byte) Riiablo.NECROMANCER);
      data.setSkillLevel(89, 1);
      world.getMapper(Player.class).create(id).data = data;
      StateList states = world.getMapper(UnitStates.class).create(id).init(id).stateList;
      world.process();
      assertEquals(28, states.getTotalStatContribution(Stat.passive_summon_resist));
      world.process();
      assertEquals(28, states.getTotalStatContribution(Stat.passive_summon_resist));
      data.setSkillLevel(89, 0);
      world.process();
      assertNull(states.getState(StateId.SUMMONRESIST));
    } finally { world.dispose(); }
  }

  @Test void cloakHasSeparateSelfAndTargetStatsAndCannotRefreshWhileActive() {
    Skills.Entry row = Riiablo.files.skills.get("Cloak of Shadows");
    StateList states = new StateList(7);
    UnitState self = AssassinSkills.applyCloakSelf(states, row, 2, 7);
    assertNotNull(self);
    assertEquals(13, self.getStatContributionValue(Stat.item_armor_percent));
    int duration = self.duration;
    assertNull(AssassinSkills.applyCloakSelf(states, row, 10, 7));
    assertEquals(duration, self.duration);
    UnitState target = new StateList(8).addState(StateId.CLOAKED, duration, 2, 7);
    AssassinSkills.applyCloakTargetStats(target, row, 2);
    assertEquals(-18, target.getStatContributionValue(Stat.skill_armor_percent));
    assertEquals(0, target.getStatContributionValue(Stat.item_armor_percent));
  }

  private static Item weapon(String code) {
    Item item = new Item(); item.reset();
    item.setBase(Riiablo.files.weapons.get(code)); item.attrs.reset(); return item;
  }

  private static final class NoopFactory extends EntityFactory {
    int lastPet = -1;
    @Override public int createSummonedPet(int owner, com.riiablo.codec.excel.MonStats.Entry row,
        String petType, int skill, int level, int maximum, boolean passive, int duration,
        float x, float y) {
      int id = world.create();
      world.getMapper(Position.class).create(id).position.set(x, y);
      world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(1, 10);
      world.getMapper(UnitStates.class).create(id).init(id);
      return lastPet = id;
    }
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
