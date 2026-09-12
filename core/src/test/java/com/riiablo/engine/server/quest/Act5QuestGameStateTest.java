package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Act5QuestGameStateTest {
  @Test
  void roomSnapshotRestoresWaveOriginAndPortalTransitions() {
    Act5QuestGameState original = new Act5QuestGameState();
    original.setBaalOrigin(123.5f, 456.25f);
    assertTrue(original.baalWaves.start());
    original.baalWaves.tick(true);
    assertTrue(original.baalPortals.openWorldstoneChamber());

    Act5QuestGameState restored = new Act5QuestGameState();
    restored.restore(original.snapshot());

    assertTrue(restored.hasBaalOrigin());
    assertEquals(123.5f, restored.baalOriginX());
    assertEquals(456.25f, restored.baalOriginY());
    assertEquals(original.baalWaves.wave(), restored.baalWaves.wave());
    assertEquals(original.baalWaves.delayTicks(), restored.baalWaves.delayTicks());
    assertTrue(restored.baalPortals.isWorldstoneChamberOpen());
    assertFalse(restored.baalPortals.isLastPortalCreated());
    assertFalse(restored.baalPortals.openWorldstoneChamber());
  }
}
