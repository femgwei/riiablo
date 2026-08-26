package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QuestWarpTest {
  @Test
  void roundTripsDestinationWithoutCollidingWithOrdinaryTileIndices() {
    int encoded = QuestWarp.encode(38);
    assertTrue(QuestWarp.isQuestWarp(encoded));
    assertEquals(38, QuestWarp.destinationLevelId(encoded));
    assertFalse(QuestWarp.isQuestWarp(0x00010203));
    assertEquals(0, QuestWarp.destinationLevelId(0x00010203));
  }

  @Test
  void rejectsInvalidDestination() {
    assertThrows(IllegalArgumentException.class, () -> QuestWarp.encode(0));
    assertThrows(IllegalArgumentException.class, () -> QuestWarp.encode(0x10000));
  }
}
