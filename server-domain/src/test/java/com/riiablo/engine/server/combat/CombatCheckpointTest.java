package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CombatCheckpointTest {
  @Test
  void canonicalSummaryAndDigestAreStable() {
    CombatCheckpoint first = CombatCheckpoint.of(24, 117, 119, 36, 256000, 227354, 2);
    CombatCheckpoint replay = CombatCheckpoint.of(24, 117, 119, 36, 256000, 227354, 2);
    assertEquals("24|117|119|36|256000|227354|2", first.canonical());
    assertEquals(first.digest(), replay.digest());
    assertEquals(64, first.digest().length());
  }

  @Test
  void anyAuthoritativeFieldChangeChangesDigest() {
    CombatCheckpoint first = CombatCheckpoint.of(24, 117, 119, 36, 256000, 227354, 2);
    CombatCheckpoint changed = CombatCheckpoint.of(25, 117, 119, 36, 256000, 227354, 2);
    assertNotEquals(first.canonical(), changed.canonical());
    assertNotEquals(first.digest(), changed.digest());
  }

  @Test
  void rejectsInvalidSummaryFields() {
    assertThrows(IllegalArgumentException.class,
        () -> CombatCheckpoint.of(-1, 117, 119, 36, 1, 1, 1));
    assertThrows(IllegalArgumentException.class,
        () -> CombatCheckpoint.of(1, 117, 119, 36, 1, 1, -1));
  }

  @Test
  void continuationCarriesTheRestoredLifeBoundary() {
    CombatCheckpoint archived = CombatCheckpoint.of(24, 117, 119, 36, 256000, 227354, 2);
    CombatCheckpoint resumed = CombatCheckpoint.of(25, 117, 119, 36, 227354, 210000, 1);
    CombatCheckpoint.Continuation result = archived.verifyContinuation(resumed);
    assertTrue(result.accepted());
  }

  @Test
  void continuationReportsIdentityAndBoundaryDrift() {
    CombatCheckpoint archived = CombatCheckpoint.of(24, 117, 119, 36, 256000, 227354, 2);
    CombatCheckpoint resumed = CombatCheckpoint.of(24, 118, 119, 47, 256001, 210000, 1);
    CombatCheckpoint.Continuation result = archived.verifyContinuation(resumed);
    assertTrue(!result.accepted());
    assertEquals(java.util.Arrays.asList("tick", "casterId", "skillId", "lifeBeforeFixed"),
        result.mismatches());
  }
}
