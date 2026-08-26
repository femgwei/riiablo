package com.riiablo.engine.server.object;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.riiablo.item.TreasureClassResolver;

class NativeObjectDropPlayerContextTest {
  @Test
  void preservesRemoteTotalButUsesSameLevelPartyForEffectiveCount() {
    TreasureClassResolver.PlayerContext context =
        NativeObjectDropSystem.playerContextForCounts(4, 2);
    assertEquals(4, context.totalPlayers);
    assertEquals(2, context.partyMembersInLevel);
    assertEquals(3, context.effectivePlayerCount());
  }

  @Test
  void fallsBackToOneWhenNoSameLevelPlayerWasReported() {
    TreasureClassResolver.PlayerContext context =
        NativeObjectDropSystem.playerContextForCounts(8, 0);
    assertEquals(8, context.totalPlayers);
    assertEquals(1, context.partyMembersInLevel);
    assertEquals(4, context.effectivePlayerCount());
  }
}
