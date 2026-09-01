package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

class MercenaryFollowSystemTest {
  @Test
  void mirrorsNativeHirelingDistanceBands() {
    assertEquals(MercenaryFollowSystem.MOTION_SETTLE,
        MercenaryFollowSystem.motion(true, 16f, false));
    assertEquals(MercenaryFollowSystem.MOTION_NONE,
        MercenaryFollowSystem.motion(true, 20f, false));
    assertEquals(MercenaryFollowSystem.MOTION_FOLLOW,
        MercenaryFollowSystem.motion(true, 24.01f, false));
    assertEquals(MercenaryFollowSystem.MOTION_TELEPORT,
        MercenaryFollowSystem.motion(true, 100.01f, false));
    assertEquals(MercenaryFollowSystem.MOTION_TELEPORT,
        MercenaryFollowSystem.motion(false, 2f, false));
  }

  @Test
  void deadHirelingOnlyMovesAcrossZoneBoundary() {
    assertEquals(MercenaryFollowSystem.MOTION_NONE,
        MercenaryFollowSystem.motion(true, 80f, true));
    assertEquals(MercenaryFollowSystem.MOTION_TELEPORT,
        MercenaryFollowSystem.motion(false, 2f, true));
  }

  @Test
  void landingSearchSkipsOwnerAndBlockedCandidates() {
    Vector2 result = new Vector2();
    boolean found = MercenaryFollowSystem.findLanding(new Vector2(10, 10), result,
        (x, y) -> x == 12 && y == 10);

    assertTrue(found);
    assertEquals(new Vector2(12, 10), result);
    assertFalse(result.epsilonEquals(10, 10));
  }
}
