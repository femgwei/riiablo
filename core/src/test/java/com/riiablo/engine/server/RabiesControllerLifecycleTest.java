package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import java.util.ArrayList;
import java.util.List;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Real SrvDo30 ECS path; targets mark infection without manufacturing poison damage. */
class RabiesControllerLifecycleTest extends RiiabloTest {
  @ParameterizedTest @CsvSource({"10,false", "11,true", "51,true"})
  void onlyAtLeastTenRemainingFramesCanCreateAnotherInfection(int beforeTick, boolean spreads) {
    try (Fixture f = new Fixture(beforeTick)) {
      f.world.process();
      UnitState infected = f.states(f.target).stateList.getState(StateId.RABIES);
      assertEquals(spreads, infected != null);
      if (spreads) {
        assertEquals(beforeTick - 1, infected.duration);
        assertEquals(238, infected.skillId);
        assertEquals(f.source, infected.sourceEntityId);
        assertEquals(2, f.spawned.size(), "one null-cel contagion and one attached controller");
        Missile child = f.spawned.stream().filter(m -> m.rabiesController).findFirst().orElseThrow();
        assertEquals(infected.duration, child.remainingFrames);
        assertEquals(f.target, child.attachedEntityId);
      } else assertTrue(f.spawned.isEmpty(), "a nine-frame tail cannot be extended to ten");
    }
  }

  @Test void deathOfAttachedHostStopsSpreadEvenIfItsRabiesStateHasNotYetBeenRemoved() {
    try (Fixture f = new Fixture(51)) {
      f.world.getMapper(AttributesWrapper.class).get(f.host).attrs.get(Stat.hitpoints).set(0f);
      assertTrue(f.states(f.host).stateList.hasState(StateId.RABIES));
      f.world.process(); f.world.process();
      assertFalse(f.states(f.target).stateList.hasState(StateId.RABIES));
      assertTrue(f.spawned.isEmpty());
      assertFalse(f.world.getEntityManager().isActive(f.controllerId));
    }
  }

  @Test void removedStateDeletesControllerButRepeatPulsesDoNotRefreshAnExistingInfection() {
    try (Fixture f = new Fixture(51)) {
      f.world.process();
      UnitState infected = f.states(f.target).stateList.getState(StateId.RABIES);
      assertNotNull(infected);
      infected.duration = 17;
      for (int i = 0; i < 6; i++) f.world.process();
      assertEquals(17, infected.duration, "this fixture has no StateUpdater; pulses must not refresh it");
      f.states(f.host).stateList.removeState(StateId.RABIES);
      f.world.process(); f.world.process();
      assertFalse(f.world.getEntityManager().isActive(f.controllerId));
      assertNotNull(f.states(f.target).stateList.getState(StateId.RABIES),
          "removing one host's controller must not erase another infected target's state");
    }
  }

  private static final class Fixture implements AutoCloseable {
    final EntityFactory factory = mock(EntityFactory.class);
    final List<Missile> spawned = new ArrayList<>();
    final World world;
    final int source, host, target, controllerId;
    Fixture(int remaining) {
      world = new World(new WorldConfigurationBuilder().with(new EventSystem(),
          factory, new MissileCollisionSystem()).build().register("factory", factory)
          .register("map", new com.riiablo.map.Map(0, 0)));
      when(factory.createMissile(any(Missiles.Entry.class), any(Vector2.class), any(Vector2.class),
          anyInt(), anyInt())).thenAnswer(call -> {
        int id = world.create();
        Missiles.Entry row = call.getArgument(0);
        Vector2 origin = call.getArgument(2);
        Missile m = world.getMapper(Missile.class).create(id).set(row, origin, row.Range)
            .setOwner(call.getArgument(3));
        world.getMapper(Position.class).create(id).position.set(origin);
        world.getMapper(Velocity.class).create(id);
        spawned.add(m); return id;
      });
      source = unit(0, 0, true); host = unit(10, 10, false); target = unit(11, 10, false);
      states(host).stateList.addState(StateId.RABIES, remaining, 1, source).skillId = 238;
      controllerId = world.create();
      Missiles.Entry row = Riiablo.files.Missiles.get("rabiesplague");
      Missile controller = world.getMapper(Missile.class).create(controllerId)
          .set(row, new Vector2(10, 10), remaining).setOwner(source);
      controller.rabiesController = true; controller.attached = true;
      controller.attachedEntityId = host; controller.rabiesSourceId = source;
      controller.skillId = 238; controller.damageLevel = 1; controller.remainingFrames = remaining;
      world.getMapper(Position.class).create(controllerId).position.set(10, 10);
      world.getMapper(Velocity.class).create(controllerId);
      world.setDelta(1f/25f);
    }
    int unit(float x, float y, boolean player) {
      int id = world.create();
      if (player) world.getMapper(Player.class).create(id);
      else world.getMapper(Monster.class).create(id);
      world.getMapper(Position.class).create(id).position.set(x,y);
      Attributes attrs = Attributes.obtainStandard();
      attrs.base().put(Stat.level, 20); attrs.base().put(Stat.hitpoints, 100f);
      attrs.base().put(Stat.maxhp, 100f); attrs.reset();
      world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
      world.getMapper(UnitStates.class).create(id).init(id);
      return id;
    }
    UnitStates states(int id) { return world.getMapper(UnitStates.class).get(id); }
    @Override public void close() { world.dispose(); }
  }
}
