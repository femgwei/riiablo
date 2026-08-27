package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;
import net.mostlyoriginal.api.event.common.EventSystem;

/** Headless authoritative ECS coverage for aura targeting and lifecycle. */
class AuraEcsScenarioTest extends RiiabloTest {
  @Test
  void mightBuffsPartyButNotEnemiesAndSwitchesToConviction() {
    AuraEcsSystem auraSystem = new AuraEcsSystem();
    World world = new World(new WorldConfigurationBuilder().with(auraSystem).build());
    try {
      int caster = player(world, 0, 0);
      int ally = player(world, 3, 0);
      int enemy = monster(world, 5, 0);
      world.setDelta(1f / 25f);

      assertTrue(auraSystem.manager().activateAura(caster, SkillId.MIGHT, 1));
      world.process();
      assertTrue(states(world, caster).hasState(StateId.MIGHT));
      assertTrue(states(world, ally).hasState(StateId.MIGHT));
      assertFalse(states(world, enemy).hasState(StateId.MIGHT));
      assertEquals(40, states(world, ally).getTotalDamageModifier());
      assertTrue(auraSystem.manager().activateAura(caster, SkillId.MIGHT, 1),
          "replayed selection must be idempotent");
      world.process();
      assertEquals(40, states(world, ally).getTotalDamageModifier(),
          "replayed selection must not stack the same aura");
      System.out.println("[AURA_ECS] aura=MIGHT caster=true allyDamage=40 enemy=false status=PASS");

      assertTrue(auraSystem.manager().activateAura(caster, SkillId.CONVICTION, 1));
      world.process();
      assertFalse(states(world, caster).hasState(StateId.MIGHT));
      assertFalse(states(world, ally).hasState(StateId.MIGHT));
      assertTrue(states(world, enemy).hasState(StateId.CONVICTION));
      assertEquals(-30, states(world, enemy).getTotalResistModifier(0));
      assertEquals(-30, states(world, enemy).getTotalResistModifier(1));
      assertEquals(-30, states(world, enemy).getTotalResistModifier(2));
      assertEquals(-50, states(world, enemy).getTotalDefenseModifier());
      System.out.println("[AURA_ECS] aura=CONVICTION enemyFire=-30 enemyDefense=-50"
          + " oldAuraRemoved=true status=PASS");

      world.getMapper(AttributesWrapper.class).get(caster).attrs.get(Stat.hitpoints).set(0);
      world.process();
      assertFalse(states(world, enemy).hasState(StateId.CONVICTION));
      assertFalse(auraSystem.manager().hasActiveAura(caster));
      System.out.println("[AURA_ECS] phase=caster_dead effectsRemoved=true status=PASS");
    } finally {
      world.dispose();
    }
  }

  @Test
  void leavingRangeRemovesAuraAndFanaticismSpeedIsNotFrenzyStacks() {
    AuraEcsSystem auraSystem = new AuraEcsSystem();
    NoopFactory factory = new NoopFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), auraSystem, new StateUpdater(), factory).build()
        .register("factory", factory).register("map", new Map(0, 0)));
    try {
      int caster = player(world, 0, 0);
      int ally = player(world, 3, 0);
      com.riiablo.engine.server.component.Velocity velocity = world
          .getMapper(com.riiablo.engine.server.component.Velocity.class).create(ally);
      world.setDelta(1f / 25f);

      assertTrue(auraSystem.manager().activateAura(caster, SkillId.FANATICISM, 1));
      world.process();
      assertTrue(states(world, ally).hasState(StateId.FANATICISM));
      assertEquals(15, states(world, ally).getTotalVelocityModifier());
      assertEquals(1.15f, velocity.stateSpeedMultiplier, 0.001f,
          "Fanaticism 15 must be 15%, not fifteen Frenzy stacks");

      world.getMapper(Position.class).get(ally).position.set(500, 0);
      world.process();
      assertFalse(states(world, ally).hasState(StateId.FANATICISM));
      // StateUpdater executes after AuraEcsSystem and observes the removal.
      assertEquals(1f, velocity.stateSpeedMultiplier, 0.001f);
      System.out.println("[AURA_ECS] aura=FANATICISM velocity=1.15 leftRange=true"
          + " restored=1.0 status=PASS");
    } finally {
      world.dispose();
    }
  }

  private static int player(World world, float x, float y) {
    int id = world.create();
    world.getMapper(Player.class).create(id);
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(UnitStates.class).create(id).init(id);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes();
    return id;
  }

  private static int monster(World world, float x, float y) {
    int id = world.create();
    world.getMapper(Monster.class).create(id);
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(UnitStates.class).create(id).init(id);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes();
    return id;
  }

  private static com.riiablo.engine.server.state.StateList states(World world, int id) {
    return world.getMapper(UnitStates.class).get(id).stateList;
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
    @Override public int createPlayer(CharData data, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMonster(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(Item item, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMissile(int id, Vector2 angle, Vector2 position) { return Engine.INVALID_ENTITY; }
  }
}
