package com.riiablo.engine.server;

import com.d2moo.common.drlg.D2ObjectIds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EnvironmentObjectSystemTest {
  @Test
  public void recognizesNativeRogueBonfireByObjectAndLevelId() {
    assertTrue(EnvironmentObjectSystem.isRogueBonfire(
        D2ObjectIds.OBJECT_ROGUEBONFIRE, EnvironmentObjectSystem.ROGUE_ENCAMPMENT));
  }

  @Test
  public void doesNotTreatOtherObjectsOrLevelsAsRogueBonfire() {
    assertFalse(EnvironmentObjectSystem.isRogueBonfire(10,
        EnvironmentObjectSystem.ROGUE_ENCAMPMENT));
    assertFalse(EnvironmentObjectSystem.isRogueBonfire(
        D2ObjectIds.OBJECT_ROGUEBONFIRE, 2));
  }
}
