package com.riiablo.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.d2moo.common.drlg.D2ObjectIds;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.EnvironmentObjectSystem;
import com.riiablo.engine.server.EnvironmentSystem;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Object;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.map.EnvironmentCycle;
import com.riiablo.map.Map;
import java.lang.reflect.Proxy;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Real ECS ticks with resource-free maps, not a full GameScreen or D2GS lifecycle test. */
class SimulationSessionIntegrationTest {
  private Application previousApp;

  @BeforeEach
  void installLogSink() {
    previousApp = Gdx.app;
    Gdx.app = (Application) Proxy.newProxyInstance(getClass().getClassLoader(),
        new java.lang.Class<?>[] {Application.class}, (proxy, method, args) -> null);
  }

  @AfterEach
  void restoreApplication() {
    Gdx.app = previousApp;
  }

  @ParameterizedTest
  @EnumSource(SimulationMode.class)
  void interleavedWorldsKeepSeparateClocksAndReadTheirLiveMap(SimulationMode mode) {
    ActMap firstMap = new ActMap(3);
    ActMap secondMap = new ActMap(0);
    SimulationSession first = session(mode, firstMap);
    SimulationSession second = session(mode, secondMap);
    World firstWorld = clockWorld(first);
    World secondWorld = clockWorld(second);
    try {
      firstWorld.process();
      assertEquals(16, first.environment().ticks());
      assertEquals(0, second.environment().ticks());
      secondWorld.process();
      assertEquals(16, first.environment().ticks());
      assertEquals(1, second.environment().ticks());
      firstMap.act = 0;
      firstWorld.process();
      assertEquals(17, first.environment().ticks(), "act is read live, not captured at assembly");
      assertEquals(1, second.environment().ticks());
    } finally {
      firstWorld.dispose();
      secondWorld.dispose();
    }
  }

  @ParameterizedTest
  @EnumSource(SimulationMode.class)
  void teardownAndRecreationDoNotReuseTheOldClockOrDisposeHostMaps(SimulationMode mode) {
    ActMap oldMap = new ActMap(0);
    SimulationSession oldSession = session(mode, oldMap);
    World oldWorld = clockWorld(oldSession);
    try {
      for (int tick = 0; tick < 10; tick++) oldWorld.process();
    } finally {
      oldWorld.dispose();
    }
    assertEquals(0, oldMap.disposeCalls, "map disposal remains the host's responsibility");
    ActMap newMap = new ActMap(0);
    SimulationSession newSession = session(mode, newMap);
    World newWorld = clockWorld(newSession);
    try {
      assertEquals(0, newSession.environment().ticks());
      newWorld.process();
      assertEquals(1, newSession.environment().ticks());
      assertEquals(10, oldSession.environment().ticks());
    } finally {
      newWorld.dispose();
    }
    assertEquals(0, newMap.disposeCalls);
  }

  @Test
  void constructorBindingsCannotBeOverwrittenByUnrelatedNamedAliases() {
    SimulationSession explicit = session(SimulationMode.LOCAL, new ActMap(3));
    SimulationSession aliases = session(SimulationMode.LOCAL, new ActMap(0));
    WorldConfiguration config = aliases.registerServices(new WorldConfigurationBuilder()
        .with(new EnvironmentSystem(explicit.map(), explicit.environment())).build());
    World world = new World(config);
    try {
      world.process();
      assertEquals(16, explicit.environment().ticks());
      assertEquals(0, aliases.environment().ticks());
    } finally {
      world.dispose();
    }
  }

  @Test
  void clockTransitionReachesTheBonfireInTheSameTickAndOtherSessionsStayIsolated() {
    SimulationSession session = session(SimulationMode.LOCAL, new ActMap(0));
    session.environment().initialize(EnvironmentCycle.CYCLE_NOON,
        160 * EnvironmentCycle.NORMAL_TIME_RATE, false);
    SimulationSession other = session(SimulationMode.LOCAL, new ActMap(0));
    World world = new World(session.registerServices(new WorldConfigurationBuilder()
        .with(new EventSystem(), new CofManager())
        .with(new EnvironmentSystem(session.map(), session.environment()))
        .with(new EnvironmentObjectSystem(session.environment())).build()));
    try {
      int bonfire = bonfire(world, session.map());
      world.process();
      assertFalse(session.environment().isDay());
      assertTrue(world.getMapper(Sequence.class).has(bonfire));
      Sequence sequence = world.getMapper(Sequence.class).get(bonfire);
      assertEquals(Engine.Object.MODE_OP, sequence.mode1);
      assertEquals(Engine.Object.MODE_ON, sequence.mode2);
      assertTrue(other.environment().isDay());
      assertEquals(0, other.environment().ticks());

      world.getMapper(CofReference.class).get(bonfire).mode = Engine.Object.MODE_ON;
      session.environment().setPeriodOfDay(EnvironmentCycle.PERIOD_DAY);
      world.process();
      assertFalse(world.getMapper(Sequence.class).has(bonfire));
      assertEquals(Engine.Object.MODE_NU, world.getMapper(CofReference.class).get(bonfire).mode);
      assertEquals(Engine.Object.MODE_NU, world.getMapper(Object.class).get(bonfire).mode);
    } finally {
      world.dispose();
    }
  }

  private static SimulationSession session(SimulationMode mode, Map map) {
    return new SimulationSession(mode, map, new EnvironmentCycle());
  }

  private static World clockWorld(SimulationSession session) {
    return new World(session.registerServices(new WorldConfigurationBuilder()
        .with(new EnvironmentSystem(session.map(), session.environment())).build()));
  }

  private static int bonfire(World world, Map map) {
    Map.Zone town = new Map.Zone();
    town.level = new Levels.Entry();
    town.level.Id = 1;
    int entity = world.create();
    Objects.Entry base = new Objects.Entry();
    base.Id = D2ObjectIds.OBJECT_ROGUEBONFIRE;
    world.getMapper(Object.class).create(entity).base = base;
    world.getMapper(CofReference.class).create(entity).mode = Engine.Object.MODE_NU;
    world.getMapper(MapWrapper.class).create(entity).set(map, town);
    return entity;
  }

  /** No generation or native resources: only live Act and host-disposal observation. */
  private static class ActMap extends Map {
    int act;
    int disposeCalls;
    ActMap(int act) {
      super(1, 0);
      this.act = act;
    }
    @Override public int getAct() { return act; }
    @Override public void dispose() { disposeCalls++; }
  }
}
