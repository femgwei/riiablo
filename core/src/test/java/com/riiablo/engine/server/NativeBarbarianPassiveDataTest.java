package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.serializer.StateSerializer;
import com.riiablo.engine.server.skill.BarbarianSkills;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class NativeBarbarianPassiveDataTest extends RiiabloTest {
  @Test
  void nativePassiveColumnsAndDiminishingFormulasAreLoaded() {
    assertPassive("Increased Stamina", 141, "increasedstamina", "skill_passive_staminapercent", 30, 45);
    assertPassive("Iron Skin", 145, "ironskin", "skill_armor_percent", 30, 40);
    assertPassive("Increased Speed", 148, "increasedspeed", "velocitypercent", 13, 18);
    assertPassive("Natural Resistance", 153, "naturalresistance", "fireresist", 12, 22);
    assertEquals(45, BarbarianSkills.calculateIncreasedStaminaBonus(2));
    assertEquals(40, BarbarianSkills.calculateIronSkinDefenseBonus(2));
    assertEquals(18, BarbarianSkills.calculateIncreasedSpeedBonus(2));
    assertEquals(22, BarbarianSkills.calculateNaturalResistanceBonus(2));
  }

  @Test
  void nativeWeaponMasteryRowsCarryItemLayersAndAuthoritativeFormulas() {
    int[] ids = {127, 128, 129, 134, 135, 136};
    String[] states = {"swordmastery", "axemastery", "macemastery",
        "polearmmastery", "throwingmastery", "spearmastery"};
    String[] itemTypes = {"swor", "axe", "blun", "pole", "thro", "spea"};
    for (int i = 0; i < ids.length; i++) {
      int id = ids[i];
      Skills.Entry skill = Riiablo.files.skills.get(id);
      assertNotNull(skill, "skill " + id);
      assertTrue(skill.passive);
      assertEquals(states[i], skill.passivestate);
      assertEquals(itemTypes[i], skill.passiveitype);
      boolean throwing = id == 135;
      assertEquals(throwing ? "passive_mastery_throw_th" : "passive_mastery_melee_th",
          skill.passivestat[0]);
      assertEquals(throwing ? "passive_mastery_throw_dmg" : "passive_mastery_melee_dmg",
          skill.passivestat[1]);
      assertEquals(throwing ? "passive_mastery_throw_crit" : "passive_mastery_melee_crit",
          skill.passivestat[2]);
      assertEquals(i < 3 ? 28 : 30, SkillFormula.evaluate(skill.passivecalc[0], skill, 1));
      assertEquals(i < 3 ? 36 : 38, SkillFormula.evaluate(skill.passivecalc[0], skill, 2));
      assertEquals(28, SkillFormula.evaluate(skill.passivecalc[1], skill, 1));
      assertEquals(33, SkillFormula.evaluate(skill.passivecalc[1], skill, 2));
      assertEquals(5, SkillFormula.evaluate(skill.passivecalc[2], skill, 1));
      assertEquals(9, SkillFormula.evaluate(skill.passivecalc[2], skill, 2));
    }
    assertEquals(36, BarbarianSkills.calculateWeaponMasteryAttackRatingBonus(2));
    assertEquals(33, BarbarianSkills.calculateWeaponMasteryDamageBonus(2));
    assertEquals(9, BarbarianSkills.getWeaponMasteryCriticalChance(2));
  }

  @Test
  void weaponTypeAndThrowContextSelectOnlyTheHighestMatchingMastery() {
    StateList states = new StateList(7);
    BarbarianSkills.applyPassiveState(states, skill("Sword Mastery"), 2, 7);
    BarbarianSkills.applyPassiveState(states, skill("Axe Mastery"), 1, 7);
    BarbarianSkills.applyPassiveState(states, skill("Throwing Mastery"), 2, 7);

    StateList.WeaponMasteryBonus bonus = new StateList.WeaponMasteryBonus();
    states.getWeaponMastery(weapon("ssd"), false, bonus);
    assertEquals(36, bonus.attackRatingPercent);
    assertEquals(33, bonus.damagePercent);
    assertEquals(9, bonus.criticalChance);

    states.getWeaponMastery(weapon("hax"), false, bonus);
    assertEquals(28, bonus.attackRatingPercent);
    assertEquals(28, bonus.damagePercent);
    assertEquals(5, bonus.criticalChance);

    Item javelin = weapon("jav");
    states.getWeaponMastery(javelin, true, bonus);
    assertEquals(38, bonus.attackRatingPercent);
    assertEquals(33, bonus.damagePercent);
    assertEquals(9, bonus.criticalChance);
    states.getWeaponMastery(javelin, false, bonus);
    assertTrue(bonus.isEmpty(), "throw mastery must not leak into a melee jab");

    states.getWeaponMastery(weapon("sbw"), false, bonus);
    assertTrue(bonus.isEmpty(), "a non-matching weapon must receive no mastery stats");

    Skills.Entry frenzy = skill("Frenzy");
    Attributes attacker = combatAttributes(100, 0, 1, 100);
    Item sword = weapon("ssd");
    int[] withoutMastery = BarbarianSkills.calculateFrenzyWeaponDamage(
        frenzy, 1, attacker, sword, name -> 0);
    int[] withMastery = BarbarianSkills.calculateFrenzyWeaponDamage(
        frenzy, 1, attacker, sword, name -> 0, states);
    assertTrue(withMastery[0] > withoutMastery[0]);
    int skillPercent = frenzy.ToHit;
    assertEquals(100 * (100 + skillPercent + 36) / 100,
        BarbarianSkills.getWeaponMasteryAttackRating(
            frenzy, 1, attacker, true, sword, states));
  }

  @Test
  void ecsCreatesRefreshesAndRemovesAllSixMasteryStatLists() {
    NoopFactory factory = new NoopFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new StateUpdater(), factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
    try {
      CharData data = CharData.obtain().clear().set(
          Riiablo.NORMAL, false, "MasteryBarbarian", Riiablo.BARBARIAN);
      int[] skillIds = {127, 128, 129, 134, 135, 136};
      for (int skillId : skillIds) data.setSkillLevel(skillId, 2);
      int entityId = world.create();
      world.getMapper(Player.class).create(entityId).data = data;
      world.getMapper(AttributesWrapper.class).create(entityId).attrs = attributes();
      UnitStates component = world.getMapper(UnitStates.class).create(entityId).init(entityId);

      world.setDelta(1f / 25f);
      world.process();
      StateList states = component.stateList;
      assertEquals(6, states.size());
      UnitState sword = states.getState(StateId.SWORDMASTERY);
      assertNotNull(sword);
      assertEquals("swor", sword.masteryItemType);
      assertFalse(sword.throwingMastery);
      assertEquals(36, sword.masteryAttackRatingModifier);
      assertEquals(33, sword.masteryDamageModifier);
      assertEquals(9, sword.masteryCriticalChance);
      UnitState throwing = states.getState(StateId.THROWINGMASTERY);
      assertNotNull(throwing);
      assertTrue(throwing.throwingMastery);
      assertEquals("thro", throwing.masteryItemType);

      UnitState command = states.addState(StateId.BATTLECOMMAND, 100, 1, entityId);
      command.skillModifier = 1;
      world.process();
      assertSame(sword, states.getState(StateId.SWORDMASTERY));
      assertEquals(3, sword.level);
      assertEquals(SkillFormula.evaluate(
          skill("Sword Mastery").passivecalc[2], skill("Sword Mastery"), 3),
          sword.masteryCriticalChance);

      data.setSkillLevel(127, 0);
      world.process();
      assertFalse(states.hasState(StateId.SWORDMASTERY),
          "+allskills must not retain a mastery after its owned level reaches zero");
      assertEquals(6, states.size(), "five masteries plus Battle Command must remain");
    } finally {
      world.dispose();
    }
  }

  @Test
  void selectedMasteryChangesAuthoritativeDamageHitChanceAndCriticalRoll() {
    Attributes attacker = combatAttributes(100, 0, 100, 100);
    Attributes defender = combatAttributes(100, 100, 1, 1);
    StateList.WeaponMasteryBonus mastery = new StateList.WeaponMasteryBonus();
    mastery.attackRatingPercent = 36;
    mastery.damagePercent = 33;

    CombatSystem.CombatResult base = CombatSystem.INSTANCE.calculateAttack(
        attacker, defender, true, false, false, 100, 100, 100, true,
        null, null, 0, 0, null, null, false, null);
    CombatSystem.CombatResult enhanced = CombatSystem.INSTANCE.calculateAttack(
        attacker, defender, true, false, false, 100, 100, 100, true,
        null, null, 0, 0, null, null, false, mastery);
    assertEquals(100, base.physicalDamage);
    assertEquals(133, enhanced.physicalDamage);

    base = CombatSystem.INSTANCE.calculateAttack(
        attacker, defender, true, false, false, 100, 100, 100, false,
        null, null, 0, 0, null, null, false, null);
    enhanced = CombatSystem.INSTANCE.calculateAttack(
        attacker, defender, true, false, false, 100, 100, 100, false,
        null, null, 0, 0, null, null, false, mastery);
    assertTrue(enhanced.hitChance > base.hitChance);

    mastery.attackRatingPercent = 0;
    mastery.damagePercent = 0;
    mastery.criticalChance = 100;
    CombatSystem.CombatResult critical = CombatSystem.INSTANCE.calculateAttack(
        attacker, defender, true, false, false, 100, 100, 100, true,
        null, null, 0, 0, null, null, false, mastery);
    assertTrue(critical.critical);
    assertEquals(200, critical.physicalDamage);
  }

  @Test
  void passiveStateContainsAuthoritativeRuntimeModifiers() {
    StateList states = new StateList(7);
    UnitState stamina = BarbarianSkills.applyPassiveState(
        states, skill("Increased Stamina"), 2, 7);
    assertNotNull(stamina);
    assertEquals(StateId.INCREASEDSTAMINA, stamina.stateId);
    assertEquals(45, stamina.maxStaminaModifier);
    assertTrue(stamina.isPermanent());

    UnitState natural = BarbarianSkills.applyPassiveState(
        states, skill("Natural Resistance"), 2, 7);
    assertNotNull(natural);
    assertEquals(22, natural.fireResistModifier);
    assertEquals(22, natural.coldResistModifier);
    assertEquals(22, natural.lightResistModifier);
    assertEquals(22, natural.poisonResistModifier);
  }

  @Test
  void ecsRefreshesOwnedPassivesWithoutCompoundingOrGrantingUnlearnedSkills() {
    NoopFactory factory = new NoopFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new StateUpdater(), factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
    try {
      CharData data = CharData.obtain().clear().set(
          Riiablo.NORMAL, false, "PassiveBarbarian", Riiablo.BARBARIAN);
      int[] skillIds = {141, 145, 148, 153};
      for (int skillId : skillIds) data.setSkillLevel(skillId, 2);

      int entityId = world.create();
      world.getMapper(Player.class).create(entityId).data = data;
      Attributes attributes = attributes();
      world.getMapper(AttributesWrapper.class).create(entityId).attrs = attributes;
      UnitStates component = world.getMapper(UnitStates.class).create(entityId).init(entityId);
      Velocity velocity = world.getMapper(Velocity.class).create(entityId).set(6f, 9f);

      world.setDelta(1f / 25f);
      world.process();

      StateList states = component.stateList;
      UnitState stamina = states.getState(StateId.INCREASEDSTAMINA);
      assertNotNull(stamina);
      assertEquals(4, states.size());
      assertEquals(45, stamina.maxStaminaModifier);
      assertEquals(40, states.getTotalDefenseModifier());
      assertEquals(18, states.getTotalVelocityModifier());
      for (int resist = 0; resist < 4; resist++) {
        assertEquals(22, states.getTotalResistModifier(resist));
      }
      assertEquals(145f, attributes.get(Stat.maxstamina).asFixed(), 0.001f);
      assertEquals(1.18f, velocity.stateSpeedMultiplier, 0.001f);

      world.process();
      assertSame(stamina, states.getState(StateId.INCREASEDSTAMINA));
      assertEquals(4, states.size(), "unchanged passives must not be duplicated");
      assertEquals(145f, attributes.get(Stat.maxstamina).asFixed(), 0.001f,
          "the permanent percentage must not compound every tick");

      data.setSkillLevel(141, 1);
      world.process();
      assertSame(stamina, states.getState(StateId.INCREASEDSTAMINA));
      assertEquals(1, stamina.level, "a reduced skill level must refresh the stat list");
      assertEquals(30, stamina.maxStaminaModifier);
      assertEquals(130f, attributes.get(Stat.maxstamina).asFixed(), 0.001f);

      UnitState command = states.addState(StateId.BATTLECOMMAND, 100, 1, entityId);
      command.skillModifier = 1;
      world.process();
      assertEquals(2, stamina.level);
      assertEquals(45, stamina.maxStaminaModifier,
          "Battle Command must raise an already-owned passive exactly once");

      for (int skillId : skillIds) data.setSkillLevel(skillId, 0);
      world.process();
      assertFalse(states.hasState(StateId.INCREASEDSTAMINA));
      assertFalse(states.hasState(StateId.IRONSKIN));
      assertFalse(states.hasState(StateId.INCREASEDSPEED));
      assertFalse(states.hasState(StateId.NATURALRESISTANCE));
      assertTrue(states.hasState(StateId.BATTLECOMMAND));
      assertEquals(100f, attributes.get(Stat.maxstamina).asFixed(), 0.001f);
      assertEquals(1f, velocity.stateSpeedMultiplier, 0.001f);
    } finally {
      world.dispose();
    }
  }

  @Test
  void passiveStatesAffectCombatAndRoundTripForMultiplayerPresentation() {
    UnitStates source = new UnitStates().init(7);
    BarbarianSkills.applyPassiveState(source.stateList, skill("Increased Stamina"), 2, 7);
    BarbarianSkills.applyPassiveState(source.stateList, skill("Iron Skin"), 2, 7);
    BarbarianSkills.applyPassiveState(source.stateList, skill("Increased Speed"), 2, 7);
    BarbarianSkills.applyPassiveState(source.stateList, skill("Natural Resistance"), 2, 7);

    Attributes attacker = combatAttributes(100, 0, 20, 1000);
    Attributes defender = combatAttributes(100, 100, 1, 1);
    CombatSystem.CombatResult base = CombatSystem.INSTANCE.calculateAttack(
        attacker, defender, true, true, false, 1, 1, 1000, false,
        null, null, 0, 0, null, null);
    CombatSystem.CombatResult protectedResult = CombatSystem.INSTANCE.calculateAttack(
        attacker, defender, true, true, false, 1, 1, 1000, false,
        null, null, 0, 0, null, source.stateList);
    assertTrue(protectedResult.hitChance < base.hitChance,
        "Iron Skin must feed the authoritative defense calculation");

    int[] elementalMin = new int[CombatSystem.DAMAGE_TYPE_COUNT];
    int[] elementalMax = new int[CombatSystem.DAMAGE_TYPE_COUNT];
    elementalMin[CombatSystem.DAMAGE_FIRE] = 100;
    elementalMax[CombatSystem.DAMAGE_FIRE] = 100;
    CombatSystem.CombatResult resisted = CombatSystem.INSTANCE.calculateAttack(
        attacker, defender, true, true, false, 1, 1, 1000, true,
        elementalMin, elementalMax, 0, 0, null, source.stateList);
    assertEquals(78, resisted.elementalDamage[CombatSystem.DAMAGE_FIRE]);

    StateSerializer serializer = new StateSerializer();
    FlatBufferBuilder builder = new FlatBufferBuilder(256);
    int stateOffset = serializer.putData(builder, source);
    int typeOffset = EntitySync.createComponentTypeVector(
        builder, new byte[] {ComponentP.StateP});
    int componentOffset = EntitySync.createComponentVector(builder, new int[] {stateOffset});
    builder.finish(EntitySync.createEntitySync(
        builder, 7, 0, 0, typeOffset, componentOffset));

    EntitySync packet = EntitySync.getRootAsEntitySync(builder.dataBuffer());
    UnitStates client = new UnitStates().init(7);
    serializer.getData(packet, 0, client);
    assertEquals(4, client.stateList.size());
    assertEquals(2, client.stateList.getStateLevel(StateId.IRONSKIN));
    assertEquals(18,
        client.stateList.getState(StateId.INCREASEDSPEED).velocityModifier);
    assertEquals(45,
        client.stateList.getState(StateId.INCREASEDSTAMINA).maxStaminaModifier);
  }

  private static void assertPassive(String name, int id, String state, String stat,
                                    int level1, int level2) {
    Skills.Entry skill = skill(name);
    System.out.println("[BARBARIAN_PASSIVE_DATA] " + name + " state=" + skill.passivestate
        + " stats=" + java.util.Arrays.toString(skill.passivestat)
        + " calcs=" + java.util.Arrays.toString(skill.passivecalc));
    assertEquals(id, skill.Id);
    assertTrue(skill.passive);
    assertEquals(state, skill.passivestate);
    assertEquals(stat, skill.passivestat[0]);
    assertEquals(level1, com.riiablo.engine.server.skill.SkillFormula.evaluate(
        skill.passivecalc[0], skill, 1));
    assertEquals(level2, com.riiablo.engine.server.skill.SkillFormula.evaluate(
        skill.passivecalc[0], skill, 2));
  }

  private static Skills.Entry skill(String name) {
    Skills.Entry skill = Riiablo.files.skills.get(name);
    assertNotNull(skill, name);
    return skill;
  }

  private static Attributes attributes() {
    Attributes attributes = Attributes.obtainStandard();
    attributes.base().clear();
    attributes.base().put(Stat.level, 20);
    attributes.base().put(Stat.hitpoints, 100);
    attributes.base().put(Stat.maxhp, 100);
    attributes.base().put(Stat.mana, 100);
    attributes.base().put(Stat.maxmana, 100);
    attributes.base().put(Stat.stamina, 100);
    attributes.base().put(Stat.maxstamina, 100);
    attributes.base().put(Stat.armorclass, 100);
    attributes.reset();
    return attributes;
  }

  private static Attributes combatAttributes(
      int hitpoints, int defense, int damage, int attackRating) {
    Attributes attributes = attributes();
    attributes.base().put(Stat.hitpoints, hitpoints);
    attributes.base().put(Stat.maxhp, hitpoints);
    attributes.base().put(Stat.armorclass, defense);
    attributes.base().put(Stat.mindamage, damage);
    attributes.base().put(Stat.maxdamage, damage);
    attributes.base().put(Stat.tohit, attackRating);
    attributes.reset();
    return attributes;
  }

  private static Item weapon(String code) {
    Item item = new Item();
    item.reset();
    item.setBase(Riiablo.files.weapons.get(code));
    item.attrs.reset();
    return item;
  }

  private static final class NoopFactory extends EntityFactory {
    @Override public int createPlayer(CharData data, Vector2 position) { return -1; }
    @Override public int createDynamicObject(int act, int preset, float x, float y) { return -1; }
    @Override public int createStaticObject(int act, int object, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int object, float x, float y) { return -1; }
    @Override public int createMonster(int monster, float x, float y) { return -1; }
    @Override public int createWarp(int index, float x, float y) { return -1; }
    @Override public int createItem(Item item, float x, float y) { return -1; }
    @Override public int createMissile(int missile, Vector2 angle, Vector2 position) { return -1; }
  }
}
