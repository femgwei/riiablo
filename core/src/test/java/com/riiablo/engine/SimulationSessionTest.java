package com.riiablo.engine;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.annotations.Wire;
import com.riiablo.engine.server.EnvironmentObjectSystem;
import com.riiablo.engine.server.EnvironmentSystem;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.map.EnvironmentCycle;
import com.riiablo.map.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SimulationSessionTest {
  @ParameterizedTest
  @EnumSource(SimulationMode.class)
  void retainsHostBindingsWithoutCopyingResources(SimulationMode mode) {
    Map map = new Map(1, 0);
    EnvironmentCycle environment = new EnvironmentCycle();
    PartyManager parties = new PartyManager();
    SimulationSession session = new SimulationSession(mode, map, environment, parties);
    assertSame(mode, session.mode());
    assertSame(map, session.map());
    assertSame(environment, session.environment());
    assertSame(parties, session.partyManager());
  }

  @ParameterizedTest
  @EnumSource(SimulationMode.class)
  void namedConsumersReceiveTheSameObjectsAsExplicitConsumers(SimulationMode mode) {
    SimulationSession session = new SimulationSession(
        mode, new Map(1, 0), new EnvironmentCycle(), new PartyManager());
    NamedConsumer consumer = new NamedConsumer();
    WorldConfiguration config = new WorldConfigurationBuilder().with(consumer).build();
    assertSame(config, session.registerServices(config));
    World world = new World(config);
    try {
      assertSame(session, consumer.session);
      assertSame(session.map(), consumer.map);
      assertSame(session.environment(), consumer.environment);
      assertSame(session.partyManager(), consumer.parties);
    } finally {
      world.dispose();
    }
  }

  @Test
  void separateHostsDoNotGetCachedSessionBindings() {
    SimulationSession first = new SimulationSession(
        SimulationMode.LOCAL, new Map(1, 0), new EnvironmentCycle(), new PartyManager());
    SimulationSession second = new SimulationSession(
        SimulationMode.LOCAL, new Map(2, 0), new EnvironmentCycle(), new PartyManager());
    assertNotSame(first, second);
    assertNotSame(first.map(), second.map());
    assertNotSame(first.environment(), second.environment());
    assertNotSame(first.partyManager(), second.partyManager());
  }

  @Test
  void rejectsMissingSessionBindingsAtConstruction() {
    Map map = new Map(1, 0);
    EnvironmentCycle environment = new EnvironmentCycle();
    PartyManager parties = new PartyManager();
    assertThrows(NullPointerException.class,
        () -> new SimulationSession(null, map, environment, parties));
    assertThrows(NullPointerException.class,
        () -> new SimulationSession(SimulationMode.LOCAL, null, environment, parties));
    assertThrows(NullPointerException.class,
        () -> new SimulationSession(SimulationMode.LOCAL, map, null, parties));
    assertThrows(NullPointerException.class,
        () -> new SimulationSession(SimulationMode.LOCAL, map, environment, null));
  }

  @Test
  void rejectsMissingConfiguration() {
    SimulationSession session = new SimulationSession(
        SimulationMode.LOCAL, new Map(1, 0), new EnvironmentCycle(), new PartyManager());
    assertThrows(NullPointerException.class, () -> session.registerServices(null));
  }

  @Test
  void migratedSystemsRejectMissingExplicitDependencies() {
    assertThrows(NullPointerException.class, () -> new EnvironmentSystem(null, new EnvironmentCycle()));
    assertThrows(NullPointerException.class, () -> new EnvironmentSystem(new Map(1, 0), null));
    assertThrows(NullPointerException.class, () -> new EnvironmentObjectSystem(null));
    assertThrows(NullPointerException.class, () -> new com.riiablo.attributes.ExperienceManager(null));
    assertThrows(NullPointerException.class, () -> new com.riiablo.engine.server.DeathRewardSystem(null));
    assertThrows(NullPointerException.class,
        () -> new com.riiablo.engine.server.party.PartyMemberSyncSystem(null));
  }

  static class NamedConsumer extends BaseSystem {
    @Wire(name = "simulationSession") SimulationSession session;
    @Wire(name = "map") Map map;
    @Wire(name = "environment") EnvironmentCycle environment;
    @Wire(name = "partyManager") PartyManager parties;
    @Override protected void processSystem() {}
  }
}
