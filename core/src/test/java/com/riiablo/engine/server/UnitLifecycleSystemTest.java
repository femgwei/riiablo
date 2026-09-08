package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.attributes.NativeStatResolver;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.Target;
import com.riiablo.engine.server.component.UnitLifecycle;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Regression tests for the authoritative death boundary and reference cleanup. */
class UnitLifecycleSystemTest {
  @Test
  void spawnAdvancesThroughInsertedToActiveOnFixedTicks() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new UnitLifecycleSystem())
        .build());
    try {
      int entity = world.create();
      UnitLifecycle lifecycle = world.getMapper(UnitLifecycle.class).create(entity).reset();
      assertEquals(UnitLifecycle.Phase.SPAWN, lifecycle.phase);
      // Each fixed tick advances one lifecycle phase.
      world.process();
      assertEquals(UnitLifecycle.Phase.INSERTED,
          world.getMapper(UnitLifecycle.class).get(entity).phase);
      world.process();
      assertEquals(UnitLifecycle.Phase.ACTIVE,
          world.getMapper(UnitLifecycle.class).get(entity).phase);
    } finally {
      world.dispose();
    }
  }

  @Test
  void ownerDeathCleansOwnedMissilesPetsTargetsAndSourceStatesExactlyOnce() {
    EventSystem events = new EventSystem();
    World world = new World(new WorldConfigurationBuilder()
        .with(events, new UnitLifecycleSystem())
        .build());
    try {
      int owner = world.create();
      world.getMapper(Player.class).create(owner);

      int missile = world.create();
      world.getMapper(Missile.class).create(missile).ownerId = owner;
      int pet = world.create();
      world.getMapper(SummonedPet.class).create(pet).set(owner, "test", 1, 1, false, 0);
      int targeter = world.create();
      world.getMapper(Target.class).create(targeter).target = owner;
      int victim = world.create();
      UnitStates states = world.getMapper(UnitStates.class).create(victim).init(victim);
      UnitState curse = states.stateList.addStateLayer(
          StateId.AMPLIFYDAMAGE, 50, 1, owner, 99);
      curse.setStatContribution(Stat.damagepercent, 0,
          NativeStatResolver.Operation.ADD, 25);
      world.process(); // publish component insertions to subscriptions

      events.dispatch(DeathEvent.obtain(7, owner));
      world.process(); // flush deferred entity deletion

      assertFalse(world.getEntityManager().isActive(missile));
      assertFalse(world.getEntityManager().isActive(pet));
      assertEquals(com.riiablo.engine.Engine.INVALID_ENTITY,
          world.getMapper(Target.class).get(targeter).target);
      assertFalse(states.stateList.hasState(StateId.AMPLIFYDAMAGE));
      assertEquals(UnitLifecycle.Phase.DEATH,
          world.getMapper(UnitLifecycle.class).get(owner).phase);

      // A duplicate death packet must not delete unrelated entities or repeat cleanup.
      events.dispatch(DeathEvent.obtain(7, owner));
      assertTrue(world.getEntityManager().isActive(owner));
      assertFalse(world.getEntityManager().isActive(missile));
    } finally {
      world.dispose();
    }
  }
}
