package com.riiablo.engine.server.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Verifies the ownership window copied to ItemP is identical to the server policy. */
class GroundDropOwnershipMetadataTest {
  @Test
  void appliesOwnerPartyAndGoldSharingMetadata() {
    com.riiablo.engine.server.component.Item component =
        new com.riiablo.engine.server.component.Item();
    GroundDropOwnership.applyMetadata(component, 41, 7, 10_000L, 10_000L, true);

    long now = System.currentTimeMillis();
    assertEquals(41, component.dropOwnerId);
    assertEquals(7, component.dropPartyId);
    assertTrue(component.dropOwnerUntilMillis >= now + 9_900L);
    assertTrue(component.dropOwnerUntilMillis <= now + 10_100L);
    assertTrue(component.dropPartyUntilMillis >= component.dropOwnerUntilMillis + 9_900L);
    assertTrue(component.partyShareGold);
  }

  @Test
  void resetClearsClientVisibleMetadata() {
    com.riiablo.engine.server.component.Item component =
        new com.riiablo.engine.server.component.Item();
    GroundDropOwnership.applyMetadata(component, 41, 7, 1L, 2L, true);
    component = new com.riiablo.engine.server.component.Item();

    assertEquals(-1, component.dropOwnerId);
    assertEquals(-1, component.dropPartyId);
    assertEquals(0L, component.dropOwnerUntilMillis);
    assertEquals(0L, component.dropPartyUntilMillis);
    assertFalse(component.partyShareGold);
  }
}
