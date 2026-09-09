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
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
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

/** Native SrvDo066/SrvDo081 special Paladin aura integration coverage. */
class PaladinSpecialAuraIntegrationTest extends RiiabloTest {
  @Test
  void holyShockPublishesOnlyItsSelfDamageListAndPulsesLightning() {
    try (Harness test = new Harness()) {
      int caster = player(test.world, 0, 0);
      int target = monster(test.world, monsterRow(false, false, -50), 3, 0);

      assertTrue(test.auras.manager().activateAura(caster, SkillId.HOLY_SHOCK, 1));
      test.tick();

      UnitState self = states(test.world, caster).getState(StateId.HOLYSHOCK);
      assertNotNull(self);
      assertTrue(self.getStatContributionValue(Stat.lightmaxdam) > 0);
      assertEquals(0, self.getStatContributionValue(Stat.fireresist));
      assertTrue(life(test.world, target) < 100f);
    }
  }

  @Test
  void holyFreezeSlowsOnlyNegativeColdEffectTargetsAndMaintainsShatterRoll() {
    try (Harness test = new Harness()) {
      int caster = player(test.world, 0, 0);
      int susceptible = monster(test.world, monsterRow(false, false, -50), 3, 0);
      int immune = monster(test.world, monsterRow(false, false, 0), 4, 0);

      assertTrue(test.auras.manager().activateAura(caster, SkillId.HOLY_FREEZE, 1));
      test.tick();

      UnitState self = states(test.world, caster).getState(StateId.HOLYWIND);
      assertNotNull(self);
      assertTrue(self.getStatContributionValue(Stat.coldmaxdam) > 0);
      assertEquals(0, self.getStatContributionValue(Stat.velocitypercent),
          "SrvDo081 keeps owner PassiveStat separate from target AuraStat");
      UnitState slow = states(test.world, susceptible).getState(StateId.HOLYWINDCOLD);
      assertNotNull(slow);
      assertTrue(slow.getStatContributionValue(Stat.velocitypercent) < 0);
      assertTrue(slow.getStatContributionValue(Stat.attackrate) < 0);
      assertTrue(slow.getStatContributionValue(Stat.other_animrate) < 0);
      assertTrue(life(test.world, susceptible) < 100f);
      assertFalse(states(test.world, immune).hasState(StateId.HOLYWINDCOLD));
      assertEquals(100f, life(test.world, immune), 0.001f);

      boolean sawShatter = states(test.world, susceptible).hasState(StateId.SHATTER);
      boolean sawClear = !sawShatter;
      for (int i = 0; i < 100 && !(sawShatter && sawClear); i++) {
        test.auras.updateHolyFreezeShatter(
            caster, susceptible, SkillId.HOLY_FREEZE, 1, 51);
        sawShatter |= states(test.world, susceptible).hasState(StateId.SHATTER);
        sawClear |= !states(test.world, susceptible).hasState(StateId.SHATTER);
      }
      assertTrue(sawShatter, "the native deterministic stream must exercise the 20% shatter state");
      assertTrue(sawClear, "a later failed roll must clear the source-owned shatter state");
    }
  }

  @Test
  void sanctuaryFiltersLivingAndBossTargetsAndBypassesUndeadPhysicalResistance() {
    try (Harness test = new Harness()) {
      int caster = player(test.world, 0, 0);
      int undead = monster(test.world, monsterRow(true, false, -50), 3, 0);
      int living = monster(test.world, monsterRow(false, false, -50), 4, 0);
      int boss = monster(test.world, monsterRow(true, true, -50), 5, 0);

      assertTrue(test.auras.manager().activateAura(caster, SkillId.SANCTUARY, 1));
      test.tick();

      StateList casterStates = states(test.world, caster);
      UnitState self = casterStates.getState(StateId.SANCTUARY);
      assertNotNull(self);
      assertTrue(self.getStatContributionValue(Stat.item_undeaddamage_percent) > 0);
      assertTrue(self.getStatContributionValue(Stat.item_undead_tohit) > 0);
      assertEquals(1, self.getStatContributionValue(Stat.skill_bypass_undead));
      assertTrue(life(test.world, undead) < 100f);
      assertEquals(100f, life(test.world, living), 0.001f);
      assertEquals(100f, life(test.world, boss), 0.001f);

      Attributes attacker = attributes();
      attacker.base().put(Stat.mindamage, 10);
      attacker.base().put(Stat.maxdamage, 10);
      attacker.reset();
      Attributes defender = attributes();
      defender.base().put(Stat.damageresist, 100);
      defender.reset();
      CombatSystem.CombatResult without = CombatSystem.INSTANCE.calculateAttackAtDifficulty(
          attacker, defender, true, false, true,
          10, 10, 1000, true, null, null, 0, 0,
          null, null, false, null, 0, 0, false, true);
      CombatSystem.CombatResult with = CombatSystem.INSTANCE.calculateAttackAtDifficulty(
          attacker, defender, true, false, true,
          10, 10, 1000, true, null, null, 0, 0,
          casterStates, null, false, null, 0, 0, false, true);
      assertEquals(0, without.physicalDamage);
      assertTrue(with.physicalDamage > 10,
          "Sanctuary must add undead damage and bypass the undead physical immunity");
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

    Harness() { world.setDelta(1f / 25f); }
    void tick() { world.process(); }
    @Override public void close() { world.dispose(); }
  }

  private static int player(World world, float x, float y) {
    int id = unit(world, x, y);
    world.getMapper(Player.class).create(id);
    return id;
  }

  private static int monster(World world, MonStats.Entry row, float x, float y) {
    int id = unit(world, x, y);
    world.getMapper(Monster.class).create(id).monstats = row;
    return id;
  }

  private static int unit(World world, float x, float y) {
    int id = world.create();
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(UnitStates.class).create(id).init(id);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes();
    return id;
  }

  private static MonStats.Entry monsterRow(boolean undead, boolean boss, int coldEffect) {
    MonStats.Entry row = new MonStats.Entry();
    row.lUndead = undead;
    row.boss = boss;
    row.coldeffect = new int[] {coldEffect, coldEffect, coldEffect};
    return row;
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
    attrs.base().put(Stat.hitpoints, 100);
    attrs.base().put(Stat.maxhp, 100);
    attrs.base().put(Stat.mana, 100);
    attrs.base().put(Stat.maxmana, 100);
    attrs.base().put(Stat.tohit, 1000);
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
