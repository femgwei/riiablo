package com.riiablo.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.artemis.BaseSystem;
import com.artemis.WorldConfiguration;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.utils.Bag;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** Tests assembly parameters without starting a World, GL or loading MPQ data. */
class SimulationModeTest {
  @ParameterizedTest
  @CsvSource({
      "LOCAL, true, true, true",
      "DEDICATED_SERVER, true, false, true",
      "REMOTE_CLIENT, false, true, false"
  })
  void preservesExistingAuthorityCollisionAndMovementPolicy(
      SimulationMode mode, boolean authority, boolean localInput, boolean networkedModes)
      throws Exception {
    assertEquals(authority, mode.isAuthoritative());
    // This is the system's footprint-index switch, not BaseSystem's enabled flag.
    assertEquals(authority, field(mode.createDynamicUnitCollisionSystem(), "enabled"));
    BaseSystem movement = mode.createVelocityModeChanger();
    assertEquals(localInput, field(movement, "applyLocalRunInput"));
    assertEquals(networkedModes, field(movement, "updateNetworkedModes"));
  }

  @ParameterizedTest
  @CsvSource({"LOCAL, true", "DEDICATED_SERVER, false"})
  void preservesExistingLocalSkillAdapter(SimulationMode mode, boolean monstersOnly) throws Exception {
    assertEquals(monstersOnly, field(mode.createServerSkillSystem(), "monstersOnly"));
    assertNotSame(mode.createServerSkillSystem(), mode.createServerSkillSystem());
  }

  @Test
  void refusesAuthoritativeSkillsOnRemoteClients() {
    assertThrows(IllegalStateException.class, SimulationMode.REMOTE_CLIENT::createServerSkillSystem);
  }

  @ParameterizedTest
  @EnumSource(SimulationMode.class)
  void neverCachesMutableSystemsInTheMode(SimulationMode mode) {
    assertNotSame(mode.createDynamicUnitCollisionSystem(), mode.createDynamicUnitCollisionSystem());
    assertNotSame(mode.createVelocityModeChanger(), mode.createVelocityModeChanger());
  }

  @ParameterizedTest
  @EnumSource(SimulationMode.class)
  void factoriesInstallInlineAtHostSelectedPositions(SimulationMode mode) throws Exception {
    BaseSystem collision = mode.createDynamicUnitCollisionSystem();
    BaseSystem host = new HostSystem();
    BaseSystem movement = mode.createVelocityModeChanger();
    WorldConfiguration config = new WorldConfigurationBuilder()
        .with(collision).with(host).with(movement).build();
    @SuppressWarnings("unchecked")
    Bag<BaseSystem> installed = (Bag<BaseSystem>) field(config, "systems");
    List<BaseSystem> systems = new ArrayList<>();
    for (int i = 0; i < installed.size(); i++) systems.add(installed.get(i));
    int start = systems.indexOf(collision);
    assertSame(host, systems.get(start + 1));
    assertSame(movement, systems.get(start + 2));
  }

  private static Object field(Object owner, String name) throws Exception {
    Field field = owner.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(owner);
  }

  private static class HostSystem extends BaseSystem {
    @Override protected void processSystem() {}
  }
}
