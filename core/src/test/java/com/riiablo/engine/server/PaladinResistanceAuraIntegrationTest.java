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
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.engine.server.skill.AuraManager;
import com.riiablo.engine.server.skill.PaladinSkills;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Authoritative SrvDo065 resistance aura and hard-point passive coverage. */
class PaladinResistanceAuraIntegrationTest extends RiiabloTest {
  @Test
  void nativeDefinitionsReplaceTheLegacyResistanceFallbacks() {
    AuraManager manager = new AuraManager();
    int[] ids = {SkillId.RESIST_FIRE, SkillId.RESIST_COLD,
        SkillId.RESIST_LIGHTNING, SkillId.SALVATION};
    for (int id : ids) {
      AuraManager.AuraDefinition definition = manager.getAuraDefinition(id);
      assertNotNull(definition, "missing aura definition id=" + id);
      assertNotNull(definition.nativeSkill, "legacy fallback survived id=" + id);
      assertEquals(65, definition.nativeSkill.srvdofunc);
      assertEquals(50, definition.perDelayFrames);
    }
    AuraManager.AuraDefinition fire = manager.getAuraDefinition(SkillId.RESIST_FIRE);
    assertEquals(Stat.fireresist, fire.statIds[0]);
    assertEquals(Stat.maxfireresist, fire.statIds[1]);
    for (int statId : fire.passiveStatIds) assertEquals(-1, statId,
        "the separate hard-point passive must not be folded into the selected aura twice");
  }

  @Test
  void hardPointsCreatePermanentMaxResistanceAndTrackSkillChanges() {
    try (Harness test = new Harness()) {
      int paladin = player(test.world, "paladin", (byte) Riiablo.PALADIN, 0, 0);
      CharData data = test.world.getMapper(Player.class).get(paladin).data;
      data.setSkillLevel(SkillId.RESIST_FIRE, 4);
      test.tick();

      StateList states = states(test.world, paladin);
      assertTrue(states.hasState(StateId.PASSIVE_RESISTFIRE));
      assertEquals(2, states.getTotalStatContribution(Stat.maxfireresist));
      assertEquals(0, states.getTotalResistModifier(0));

      attributes(test.world, paladin).base().put(Stat.fireresist, 100);
      attributes(test.world, paladin).reset();
      CombatSystem.CombatResult capped = CombatSystem.INSTANCE.calculateFixedElementalDamage(
          attributes(test.world, paladin), true, false,
          CombatSystem.DAMAGE_FIRE, 100, 0, states, 0);
      assertEquals(23, capped.totalDamage,
          "four hard points add two points to the native 75% cap");

      data.setSkillLevel(SkillId.RESIST_FIRE, 0);
      test.tick();
      assertFalse(states.hasState(StateId.PASSIVE_RESISTFIRE));
      assertEquals(0, states.getTotalStatContribution(Stat.maxfireresist));
    }
  }

  @Test
  void selectedResistFireUsesNativeActiveStatsWithoutDoubleCountingPassive() {
    try (Harness test = new Harness()) {
      int caster = player(test.world, "paladin", (byte) Riiablo.PALADIN, 0, 0);
      int ally = player(test.world, "amazon", (byte) Riiablo.AMAZON, 3, 0);
      party(test.parties, caster, ally);
      test.world.getMapper(Player.class).get(caster).data
          .setSkillLevel(SkillId.RESIST_FIRE, 4);

      assertTrue(test.auras.selectAura(caster, SkillId.RESIST_FIRE));
      test.tick();
      StateList casterStates = states(test.world, caster);
      StateList allyStates = states(test.world, ally);
      assertTrue(casterStates.hasState(StateId.RESISTFIRE));
      assertTrue(allyStates.hasState(StateId.RESISTFIRE));
      assertEquals(85, allyStates.getTotalResistModifier(0));
      assertEquals(4, allyStates.getTotalStatContribution(Stat.maxfireresist));
      assertEquals(6, casterStates.getTotalStatContribution(Stat.maxfireresist),
          "selected +4 and permanent hard-point +2 are distinct native lists");

      attributes(test.world, ally).base().put(Stat.fireresist, 100);
      attributes(test.world, ally).reset();
      CombatSystem.CombatResult capped = CombatSystem.INSTANCE.calculateFixedElementalDamage(
          attributes(test.world, ally), true, false,
          CombatSystem.DAMAGE_FIRE, 100, 0, allyStates, 0);
      assertEquals(21, capped.totalDamage,
          "the selected level-four aura raises the ally cap from 75 to 79");

      test.auras.clearAura(caster);
      test.ticks(51);
      assertFalse(allyStates.hasState(StateId.RESISTFIRE));
      assertTrue(casterStates.hasState(StateId.PASSIVE_RESISTFIRE));
      assertEquals(2, casterStates.getTotalStatContribution(Stat.maxfireresist));
    }
  }

  @Test
  void salvationAppliesItsNativeThreeResistanceStatsToPartyMembers() {
    try (Harness test = new Harness()) {
      int caster = player(test.world, "paladin", (byte) Riiablo.PALADIN, 0, 0);
      int ally = player(test.world, "amazon", (byte) Riiablo.AMAZON, 3, 0);
      party(test.parties, caster, ally);
      test.world.getMapper(Player.class).get(caster).data
          .setSkillLevel(SkillId.SALVATION, 1);

      assertTrue(test.auras.selectAura(caster, SkillId.SALVATION));
      test.tick();
      StateList states = states(test.world, ally);
      assertTrue(states.hasState(StateId.RESISTALL));
      assertEquals(61, states.getTotalResistModifier(0));
      assertEquals(61, states.getTotalResistModifier(1));
      assertEquals(61, states.getTotalResistModifier(2));
    }
  }

  @Test
  void passiveFormulaUsesHardPointsInsteadOfItemSkillBonus() {
    Skills.Entry fire = Riiablo.files.skills.get(SkillId.RESIST_FIRE);
    StateList states = new StateList(1);
    assertNotNull(PaladinSkills.applyResistancePassiveState(states, fire, 5, 1));
    assertEquals(2, states.getTotalStatContribution(Stat.maxfireresist));
  }

  private static final class Harness implements AutoCloseable {
    final PartyManager parties = new PartyManager();
    final AuraEcsSystem auras = new AuraEcsSystem();
    final NoopFactory factory = new NoopFactory();
    final World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new StateUpdater(), auras, factory).build()
        .register("factory", factory)
        .register("map", new Map(0, 0))
        .register("partyManager", parties));

    Harness() { world.setDelta(1f / 25f); }
    void tick() { world.process(); }
    void ticks(int count) { for (int i = 0; i < count; i++) tick(); }
    @Override public void close() { world.dispose(); }
  }

  private static int player(
      World world, String name, byte classId, float x, float y) {
    int id = world.create();
    world.getMapper(Player.class).create(id).data = CharData.createRemote(name, classId);
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(UnitStates.class).create(id).init(id);
    world.getMapper(AttributesWrapper.class).create(id).attrs = baseAttributes();
    return id;
  }

  private static void party(PartyManager parties, int leader, int member) {
    short party = parties.createParty(leader);
    assertTrue(party >= 0);
    assertTrue(parties.joinParty(party, member));
  }

  private static StateList states(World world, int id) {
    return world.getMapper(UnitStates.class).get(id).stateList;
  }

  private static Attributes attributes(World world, int id) {
    return world.getMapper(AttributesWrapper.class).get(id).attrs;
  }

  private static Attributes baseAttributes() {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.hitpoints, 100);
    attrs.base().put(Stat.maxhp, 100);
    attrs.base().put(Stat.mana, 100);
    attrs.base().put(Stat.maxmana, 100);
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
    @Override public int createItem(Item item, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMissile(int id, Vector2 direction, Vector2 position) {
      return Engine.INVALID_ENTITY;
    }
  }
}
