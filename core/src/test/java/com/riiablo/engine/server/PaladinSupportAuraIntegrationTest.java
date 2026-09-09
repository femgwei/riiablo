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
import com.riiablo.attributes.NativeStatResolver;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.engine.server.skill.AuraManager;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Authoritative native integration for Defiance, Aim, Vigor, Fanaticism and Thorns. */
class PaladinSupportAuraIntegrationTest extends RiiabloTest {
  @Test
  void allFiveSupportAurasReplaceTheirLegacyDefinitions() {
    AuraManager manager = new AuraManager();
    int[] ids = {SkillId.DEFIANCE, SkillId.BLESSED_AIM, SkillId.VIGOR,
        SkillId.FANATICISM, SkillId.THORNS};
    for (int id : ids) {
      AuraManager.AuraDefinition definition = manager.getAuraDefinition(id);
      assertNotNull(definition, "missing aura id=" + id);
      assertNotNull(definition.nativeSkill, "legacy fallback survived id=" + id);
      assertEquals(65, definition.nativeSkill.srvdofunc);
      assertEquals(50, definition.perDelayFrames);
    }
    assertEquals(Stat.skill_armor_percent,
        manager.getAuraDefinition(SkillId.DEFIANCE).statIds[0]);
    assertEquals(Stat.thorns_percent,
        manager.getAuraDefinition(SkillId.THORNS).statIds[0]);
  }

  @Test
  void defianceAndVigorPublishEveryNativePartyStat() {
    try (Harness test = new Harness()) {
      int caster = player(test.world, "paladin", (byte) Riiablo.PALADIN, 0, 0);
      int ally = player(test.world, "amazon", (byte) Riiablo.AMAZON, 3, 0);
      party(test.parties, caster, ally);
      CharData data = test.world.getMapper(Player.class).get(caster).data;

      data.setSkillLevel(SkillId.DEFIANCE, 1);
      assertTrue(test.auras.selectAura(caster, SkillId.DEFIANCE));
      test.tick();
      UnitState defiance = states(test.world, ally).getState(StateId.DEFIANCE);
      assertNotNull(defiance);
      assertEquals(70, defiance.getStatContributionValue(Stat.skill_armor_percent));

      data.setSkillLevel(SkillId.VIGOR, 1);
      Velocity velocity = test.world.getMapper(Velocity.class).create(ally);
      assertTrue(test.auras.selectAura(caster, SkillId.VIGOR));
      test.tick();
      UnitState vigor = states(test.world, ally).getState(StateId.STAMINA);
      assertNotNull(vigor);
      assertEquals(50, vigor.getStatContributionValue(Stat.staminarecoverybonus));
      assertEquals(50, vigor.getStatContributionValue(Stat.skill_staminapercent));
      assertEquals(13, vigor.getStatContributionValue(Stat.velocitypercent));
      assertEquals(1.13f, velocity.stateSpeedMultiplier, 0.001f);

      test.auras.clearAura(caster);
      test.ticks(51);
      assertFalse(states(test.world, ally).hasState(StateId.STAMINA));
      assertEquals(1f, velocity.stateSpeedMultiplier, 0.001f);
    }
  }

  @Test
  void blessedAimKeepsItsHardPointPassiveSeparateFromTheSelectedAura() {
    try (Harness test = new Harness()) {
      int caster = player(test.world, "paladin", (byte) Riiablo.PALADIN, 0, 0);
      int ally = player(test.world, "amazon", (byte) Riiablo.AMAZON, 3, 0);
      party(test.parties, caster, ally);
      CharData data = test.world.getMapper(Player.class).get(caster).data;
      data.setSkillLevel(SkillId.BLESSED_AIM, 4);
      UnitState itemSkills = states(test.world, caster)
          .addState(StateId.SHRINE_SKILL, 0, 1, caster);
      itemSkills.setStatContribution(
          Stat.item_allskills, 0, NativeStatResolver.Operation.ADD, 3);

      assertTrue(test.auras.selectAura(caster, SkillId.BLESSED_AIM));
      test.tick();

      UnitState allyAura = states(test.world, ally).getState(StateId.BLESSEDAIM);
      UnitState ownerPassive = states(test.world, caster).getState(StateId.PENETRATE);
      assertNotNull(allyAura);
      assertNotNull(ownerPassive);
      assertEquals(165, allyAura.getStatContributionValue(Stat.item_tohit_percent),
          "the selected aura uses effective level seven");
      assertEquals(4, ownerPassive.level);
      assertEquals(20, ownerPassive.getStatContributionValue(Stat.item_tohit_percent),
          "the permanent penetrate list uses four hard points only");

      data.setSkillLevel(SkillId.BLESSED_AIM, 0);
      test.tick();
      assertFalse(states(test.world, caster).hasState(StateId.PENETRATE));
    }
  }

  @Test
  void fanaticismOwnerFullDamageOverwritesTheHalfStrengthPartyValue() {
    try (Harness test = new Harness()) {
      int caster = player(test.world, "paladin", (byte) Riiablo.PALADIN, 0, 0);
      int ally = player(test.world, "amazon", (byte) Riiablo.AMAZON, 3, 0);
      party(test.parties, caster, ally);
      test.world.getMapper(Player.class).get(caster).data
          .setSkillLevel(SkillId.FANATICISM, 1);

      assertTrue(test.auras.selectAura(caster, SkillId.FANATICISM));
      test.tick();

      UnitState self = states(test.world, caster).getState(StateId.FANATICISM);
      UnitState party = states(test.world, ally).getState(StateId.FANATICISM);
      assertNotNull(self);
      assertNotNull(party);
      assertEquals(14, self.getStatContributionValue(Stat.attackrate));
      assertEquals(40, self.getStatContributionValue(Stat.item_tohit_percent));
      assertEquals(50, self.getStatContributionValue(Stat.damagepercent));
      assertEquals(25, party.getStatContributionValue(Stat.damagepercent));
      assertEquals(14, party.getStatContributionValue(Stat.attackrate));
      assertEquals(40, party.getStatContributionValue(Stat.item_tohit_percent));
    }
  }

  @Test
  void thornsReflectsMeleeWithNativePlayerRoundingButNeverMissiles() {
    try (Harness test = new Harness()) {
      int defender = player(test.world, "paladin", (byte) Riiablo.PALADIN, 0, 0);
      int monster = monster(test.world, 1, 0);
      test.world.getMapper(Player.class).get(defender).data.setSkillLevel(SkillId.THORNS, 1);
      assertTrue(test.auras.selectAura(defender, SkillId.THORNS));
      test.tick();

      test.events.dispatch(DamageEvent.obtainMelee(monster, defender, 10, 10));
      assertEquals(75f, life(test.world, monster), 0.001f);

      int player = player(test.world, "amazon", (byte) Riiablo.AMAZON, 1, 0);
      test.events.dispatch(DamageEvent.obtainMelee(player, defender, 100, 100));
      assertEquals(95f, life(test.world, player), 0.001f,
          "native reduced thorns percent then passes through the 17% PvP physical scalar");

      int mercenary = monster(test.world, 1, 0);
      test.world.getMapper(Mercenary.class).create(mercenary).ownerId = player;
      test.events.dispatch(DamageEvent.obtainMelee(mercenary, defender, 100, 100));
      assertEquals(95f, life(test.world, mercenary), 0.001f);

      float beforeMissile = life(test.world, monster);
      test.events.dispatch(DamageEvent.obtainMissile(monster, defender, 10, 10, null));
      test.events.dispatch(DamageEvent.obtainReactive(defender, monster, 10, 10));
      assertEquals(beforeMissile, life(test.world, monster), 0.001f);
    }
  }

  private static final class Harness implements AutoCloseable {
    final PartyManager parties = new PartyManager();
    final AuraEcsSystem auras = new AuraEcsSystem();
    final EventSystem events = new EventSystem();
    final NoopFactory factory = new NoopFactory();
    final World world = new World(new WorldConfigurationBuilder()
        .with(events, new StateUpdater(), auras, factory).build()
        .register("factory", factory)
        .register("map", new Map(0, 0))
        .register("partyManager", parties));

    Harness() { world.setDelta(1f / 25f); }
    void tick() { world.process(); }
    void ticks(int count) { for (int i = 0; i < count; i++) tick(); }
    @Override public void close() { world.dispose(); }
  }

  private static int player(World world, String name, byte classId, float x, float y) {
    int id = unit(world, x, y);
    world.getMapper(Player.class).create(id).data = CharData.createRemote(name, classId);
    return id;
  }

  private static int monster(World world, float x, float y) {
    int id = unit(world, x, y);
    world.getMapper(Monster.class).create(id);
    return id;
  }

  private static int unit(World world, float x, float y) {
    int id = world.create();
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(UnitStates.class).create(id).init(id);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes();
    return id;
  }

  private static void party(PartyManager parties, int leader, int member) {
    short id = parties.createParty(leader);
    assertTrue(id >= 0);
    assertTrue(parties.joinParty(id, member));
  }

  private static StateList states(World world, int id) {
    return world.getMapper(UnitStates.class).get(id).stateList;
  }

  private static float life(World world, int id) {
    return world.getMapper(AttributesWrapper.class).get(id).attrs
        .get(Stat.hitpoints).asFixed();
  }

  private static Attributes attributes() {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.level, 10);
    attrs.base().put(Stat.hitpoints, 100);
    attrs.base().put(Stat.maxhp, 100);
    attrs.base().put(Stat.mana, 100);
    attrs.base().put(Stat.maxmana, 100);
    attrs.base().put(Stat.damageresist, 0);
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
