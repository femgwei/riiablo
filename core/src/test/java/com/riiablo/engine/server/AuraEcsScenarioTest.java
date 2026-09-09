package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Headless authoritative ECS coverage for native aura targeting and lifecycle. */
class AuraEcsScenarioTest extends RiiabloTest {
  @Test
  void mightBuffsOnlyPartyAndConvictionTargetsHostiles() {
    try (Harness test = new Harness()) {
      int caster = player(test.world, 0, 0);
      int ally = player(test.world, 3, 0);
      int neutral = player(test.world, 4, 0);
      int hostilePlayer = player(test.world, 5, 0);
      int monster = monster(test.world, 6, 0);
      party(test.parties, caster, ally);
      assertTrue(test.parties.declareHostility(caster, hostilePlayer));

      assertTrue(test.auras.manager().activateAura(caster, SkillId.MIGHT, 1));
      test.tick();
      assertTrue(states(test.world, caster).hasState(StateId.MIGHT));
      assertTrue(states(test.world, ally).hasState(StateId.MIGHT));
      assertFalse(states(test.world, neutral).hasState(StateId.MIGHT));
      assertFalse(states(test.world, hostilePlayer).hasState(StateId.MIGHT));
      assertFalse(states(test.world, monster).hasState(StateId.MIGHT));
      assertEquals(40, states(test.world, ally).getTotalDamageModifier());

      assertTrue(test.auras.manager().activateAura(caster, SkillId.CONVICTION, 1));
      test.tick();
      assertTrue(states(test.world, hostilePlayer).hasState(StateId.CONVICTION));
      assertTrue(states(test.world, monster).hasState(StateId.CONVICTION));
      assertFalse(states(test.world, ally).hasState(StateId.CONVICTION));
      assertFalse(states(test.world, neutral).hasState(StateId.CONVICTION));
      assertEquals(-30, states(test.world, monster).getTotalResistModifier(0));
      assertEquals(-30, states(test.world, monster).getTotalResistModifier(1));
      assertEquals(-30, states(test.world, monster).getTotalResistModifier(2));
      assertEquals(-49, states(test.world, monster).getTotalDefenseModifier());

      test.ticks(51);
      assertFalse(states(test.world, caster).hasState(StateId.MIGHT));
      assertFalse(states(test.world, ally).hasState(StateId.MIGHT));

      life(test.world, caster, 0);
      test.tick();
      assertFalse(test.auras.manager().hasActiveAura(caster));
      test.ticks(51);
      assertFalse(states(test.world, monster).hasState(StateId.CONVICTION));
    }
  }

  @Test
  void strongestSameSkillAuraWinsAndWeakerReturnsOnItsNextPulse() {
    try (Harness test = new Harness()) {
      int weakCaster = player(test.world, 0, 0);
      int strongCaster = player(test.world, 1, 0);
      int ally = player(test.world, 3, 0);
      short party = test.parties.createParty(weakCaster);
      assertTrue(party >= 0);
      assertTrue(test.parties.joinParty(party, strongCaster));
      assertTrue(test.parties.joinParty(party, ally));

      assertTrue(test.auras.manager().activateAura(weakCaster, SkillId.MIGHT, 1));
      assertTrue(test.auras.manager().activateAura(strongCaster, SkillId.MIGHT, 5));
      test.tick();

      StateList allyStates = states(test.world, ally);
      UnitState winner = allyStates.getState(StateId.MIGHT);
      assertNotNull(winner);
      assertEquals(strongCaster, winner.sourceEntityId);
      assertEquals(SkillId.MIGHT, winner.skillId);
      assertEquals(5, winner.level);
      assertEquals(51, winner.duration,
          "the aura runs after state decay and publishes native perdelay+1");
      assertEquals(80, allyStates.getTotalDamageModifier());
      assertEquals(1, allyStates.size(), "the rejected weak aura must not leave a hidden layer");

      life(test.world, strongCaster, 0);
      test.tick();
      assertEquals(strongCaster, allyStates.getState(StateId.MIGHT).sourceEntityId,
          "the strong native layer lives until its short expiry");
      test.ticks(50);

      UnitState restored = allyStates.getState(StateId.MIGHT);
      assertNotNull(restored);
      assertEquals(weakCaster, restored.sourceEntityId);
      assertEquals(1, restored.level);
      assertEquals(40, allyStates.getTotalDamageModifier());
    }
  }

  @Test
  void rangeZoneMercenaryAndSummonUseAuthoritativeOwnerRelations() {
    try (Harness test = new Harness()) {
      int caster = player(test.world, 0, 0);
      int owner = player(test.world, 2, 0);
      int outsider = player(test.world, 3, 0);
      int mercenary = mercenary(test.world, owner, 4, 0);
      int summon = summon(test.world, owner, 5, 0);
      int outsiderSummon = summon(test.world, outsider, 6, 0);
      int noAuraMercenary = mercenary(test.world, owner, 7, 0);
      MonStats.Entry noAuraRow = new MonStats.Entry();
      noAuraRow.noAura = true;
      test.world.getMapper(Monster.class).get(noAuraMercenary).monstats = noAuraRow;
      party(test.parties, caster, owner);

      Map.Zone field = new Map.Zone();
      Map.Zone otherLevel = new Map.Zone();
      zone(test.world, caster, field);
      zone(test.world, owner, field);
      zone(test.world, mercenary, field);
      zone(test.world, summon, field);
      zone(test.world, outsider, field);
      zone(test.world, outsiderSummon, field);
      zone(test.world, noAuraMercenary, field);

      assertTrue(test.auras.manager().activateAura(caster, SkillId.MIGHT, 1));
      test.tick();
      assertTrue(states(test.world, mercenary).hasState(StateId.MIGHT));
      assertTrue(states(test.world, summon).hasState(StateId.MIGHT));
      assertFalse(states(test.world, outsiderSummon).hasState(StateId.MIGHT));
      assertFalse(states(test.world, noAuraMercenary).hasState(StateId.MIGHT));

      zone(test.world, summon, otherLevel);
      position(test.world, mercenary).set(100, 0);
      test.ticks(51);
      assertFalse(states(test.world, mercenary).hasState(StateId.MIGHT));
      assertFalse(states(test.world, summon).hasState(StateId.MIGHT));
    }
  }

  @Test
  void nativePartyAuraAppliesToSelfButSkipsTownRoomTargets() {
    try (Harness test = new Harness()) {
      int caster = player(test.world, 0, 0);
      int ally = player(test.world, 3, 0);
      party(test.parties, caster, ally);
      Map.Zone town = new Map.Zone() {
        @Override public boolean isTown() { return true; }
      };
      zone(test.world, caster, town);
      zone(test.world, ally, town);

      assertTrue(test.auras.manager().activateAura(caster, SkillId.MIGHT, 1));
      test.tick();
      assertTrue(states(test.world, caster).hasState(StateId.MIGHT));
      assertFalse(states(test.world, ally).hasState(StateId.MIGHT));
    }
  }

  @Test
  void prayerUsesNativePeriodAndClampsHealingToMaximumLife() {
    try (Harness test = new Harness()) {
      int caster = player(test.world, 0, 0);
      int ally = player(test.world, 3, 0);
      party(test.parties, caster, ally);
      life(test.world, ally, 97);

      assertTrue(test.auras.manager().activateAura(caster, SkillId.PRAYER, 1));
      test.tick();
      assertEquals(99f, life(test.world, ally), 0.001f);
      test.ticks(50);
      assertEquals(100f, life(test.world, ally), 0.001f,
          "level-one Prayer heals two life but must clamp at maxhp");
    }
  }

  @Test
  void holyFireDoesNotDamageInTownAndPulsesInTheField() {
    try (Harness test = new Harness()) {
      int caster = player(test.world, 0, 0);
      int target = monster(test.world, 3, 0);
      Map.Zone town = new Map.Zone() {
        @Override public boolean isTown() { return true; }
      };
      Map.Zone field = new Map.Zone();
      zone(test.world, caster, town);
      zone(test.world, target, town);

      assertTrue(test.auras.manager().activateAura(caster, SkillId.HOLY_FIRE, 1));
      test.tick();
      assertEquals(100f, life(test.world, target), 0.001f);
      assertEquals(6, states(test.world, caster).getTotalStatContribution(Stat.firemindam));
      assertEquals(18, states(test.world, caster).getTotalStatContribution(Stat.firemaxdam));

      zone(test.world, caster, field);
      zone(test.world, target, field);
      test.ticks(50);
      assertTrue(life(test.world, target) < 100f,
          "the native periodic fire packet must damage a hostile field monster");
      assertTrue(life(test.world, target) >= 97f,
          "level-one Holy Fire periodic damage is in the native 1..3 range");
    }
  }

  @Test
  void fanaticismExpiresAfterRangePulseInsteadOfStackingAsFrenzy() {
    try (Harness test = new Harness()) {
      int caster = player(test.world, 0, 0);
      int ally = player(test.world, 3, 0);
      party(test.parties, caster, ally);
      Velocity velocity = test.world.getMapper(Velocity.class).create(ally);

      assertTrue(test.auras.manager().activateAura(caster, SkillId.FANATICISM, 1));
      test.tick();
      assertTrue(states(test.world, ally).hasState(StateId.FANATICISM));
      assertEquals(15, states(test.world, ally).getTotalVelocityModifier());
      assertEquals(1.15f, velocity.stateSpeedMultiplier, 0.001f);

      position(test.world, ally).set(500, 0);
      test.ticks(51);
      assertFalse(states(test.world, ally).hasState(StateId.FANATICISM));
      assertEquals(1f, velocity.stateSpeedMultiplier, 0.001f);
    }
  }

  private static final class Harness implements AutoCloseable {
    final PartyManager parties = new PartyManager();
    final AuraEcsSystem auras = new AuraEcsSystem();
    final NoopFactory factory = new NoopFactory();
    final Map map = new Map(0, 0);
    final World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new StateUpdater(), auras, factory).build()
        .register("factory", factory)
        .register("map", map)
        .register("partyManager", parties));

    Harness() {
      world.setDelta(1f / 25f);
    }

    void tick() {
      world.process();
    }

    void ticks(int count) {
      for (int i = 0; i < count; i++) tick();
    }

    @Override public void close() {
      world.dispose();
    }
  }

  private static void party(PartyManager parties, int leader, int member) {
    short party = parties.createParty(leader);
    assertTrue(party >= 0);
    assertTrue(parties.joinParty(party, member));
  }

  private static int player(World world, float x, float y) {
    int id = unit(world, x, y);
    world.getMapper(Player.class).create(id);
    return id;
  }

  private static int monster(World world, float x, float y) {
    int id = unit(world, x, y);
    world.getMapper(Monster.class).create(id);
    return id;
  }

  private static int mercenary(World world, int owner, float x, float y) {
    int id = monster(world, x, y);
    world.getMapper(Mercenary.class).create(id).ownerId = owner;
    return id;
  }

  private static int summon(World world, int owner, float x, float y) {
    int id = monster(world, x, y);
    world.getMapper(SummonedPet.class).create(id).ownerId = owner;
    return id;
  }

  private static int unit(World world, float x, float y) {
    int id = world.create();
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(UnitStates.class).create(id).init(id);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes();
    return id;
  }

  private static void zone(World world, int entityId, Map.Zone zone) {
    world.getMapper(MapWrapper.class).create(entityId).zone = zone;
  }

  private static Vector2 position(World world, int entityId) {
    return world.getMapper(Position.class).get(entityId).position;
  }

  private static StateList states(World world, int id) {
    return world.getMapper(UnitStates.class).get(id).stateList;
  }

  private static void life(World world, int entityId, int value) {
    world.getMapper(AttributesWrapper.class).get(entityId).attrs.get(Stat.hitpoints).set(value);
  }

  private static float life(World world, int entityId) {
    return world.getMapper(AttributesWrapper.class).get(entityId)
        .attrs.get(Stat.hitpoints).asFixed();
  }

  private static Attributes attributes() {
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
    @Override public int createPlayer(CharData data, Vector2 position) {
      return Engine.INVALID_ENTITY;
    }
    @Override public int createDynamicObject(int act, int id, float x, float y) {
      return Engine.INVALID_ENTITY;
    }
    @Override public int createStaticObject(int act, int id, float x, float y) {
      return Engine.INVALID_ENTITY;
    }
    @Override public int createStaticObjectByClassId(int id, float x, float y) {
      return Engine.INVALID_ENTITY;
    }
    @Override public int createMonster(int id, float x, float y) {
      return Engine.INVALID_ENTITY;
    }
    @Override public int createWarp(int index, float x, float y) {
      return Engine.INVALID_ENTITY;
    }
    @Override public int createItem(Item item, float x, float y) {
      return Engine.INVALID_ENTITY;
    }
    @Override public int createMissile(int id, Vector2 angle, Vector2 position) {
      return Engine.INVALID_ENTITY;
    }
  }
}
