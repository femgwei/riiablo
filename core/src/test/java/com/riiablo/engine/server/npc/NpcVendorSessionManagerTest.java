package com.riiablo.engine.server.npc;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NpcVendorSessionManagerTest {
  @Test void tradeStockIsSharedButGambleStockIsPlayerPrivate() throws Exception {
    NpcVendorSessionManager manager = new NpcVendorSessionManager();
    NpcVendorSessionManager.Session tradeOne =
        manager.open(10, "gheed", null, false, null, 0, 1, false);
    NpcVendorSessionManager.Session tradeTwo =
        manager.open(10, "gheed", null, false, null, 0, 2, false);
    NpcVendorSessionManager.Session gambleOne =
        manager.open(10, "gheed", null, true, null, 0, 1, false);
    NpcVendorSessionManager.Session gambleTwo =
        manager.open(10, "gheed", null, true, null, 0, 2, false);

    assertSame(tradeOne, tradeTwo);
    assertNotSame(gambleOne, gambleTwo);
  }

  @Test void reopeningGambleAdvancesRevisionAndDisconnectClearsOnlyThatPlayer() throws Exception {
    NpcVendorSessionManager manager = new NpcVendorSessionManager();
    NpcVendorSessionManager.Session first =
        manager.open(10, "gheed", null, true, null, 0, 1, false);
    long revision = first.revision;
    assertSame(first, manager.open(10, "gheed", null, true, null, 0, 1, true));
    assertEquals(revision + 1, first.revision);

    NpcVendorSessionManager.Session other =
        manager.open(10, "gheed", null, true, null, 0, 2, false);
    manager.clearPlayer(1);
    assertNotSame(first, manager.open(10, "gheed", null, true, null, 0, 1, false));
    assertSame(other, manager.open(10, "gheed", null, true, null, 0, 2, false));
  }
}
