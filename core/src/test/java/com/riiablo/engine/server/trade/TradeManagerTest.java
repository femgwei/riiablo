package com.riiablo.engine.server.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TradeManagerTest {
  @Test
  void confirmationCannotCompleteWithoutAuthoritativeCommitter() {
    TradeManager manager = tradingManager();
    assertEquals(TradeState.RESULT_SUCCESS, manager.confirmTrade(10));
    assertEquals(TradeState.RESULT_ERROR, manager.confirmTrade(20));

    TradeSession retained = manager.getPlayerSession(10);
    assertNotNull(retained);
    assertEquals(TradeState.TRADING, retained.getState());
    assertFalse(retained.isBothConfirmed());
  }

  @Test
  void validationOrCommitFailureKeepsSessionRetryable() {
    TradeManager manager = tradingManager();
    StubAuthority authority = new StubAuthority();
    authority.validation = TradeState.RESULT_NO_GOLD;
    manager.setAuthority(authority);
    assertEquals(TradeState.RESULT_SUCCESS, manager.confirmTrade(10));
    assertEquals(TradeState.RESULT_NO_GOLD, manager.confirmTrade(20));
    assertNotNull(manager.getPlayerSession(10));
    assertFalse(manager.getPlayerSession(10).isBothConfirmed());
    assertEquals(TradeState.TRADING, manager.getPlayerSession(10).getState());
    assertEquals(0, authority.commitCalls);

    authority.validation = TradeState.RESULT_SUCCESS;
    authority.commitResult = false;
    assertEquals(TradeState.RESULT_SUCCESS, manager.confirmTrade(10));
    assertEquals(TradeState.RESULT_ERROR, manager.confirmTrade(20));
    assertNotNull(manager.getPlayerSession(10));
    assertEquals(1, authority.commitCalls);
  }

  @Test
  void successfulAtomicCommitIsRequiredBeforeCleanup() {
    TradeManager manager = tradingManager();
    StubAuthority authority = new StubAuthority();
    authority.validation = TradeState.RESULT_SUCCESS;
    authority.commitResult = true;
    manager.setAuthority(authority);

    assertEquals(TradeState.RESULT_SUCCESS, manager.confirmTrade(10));
    assertEquals(TradeState.RESULT_SUCCESS, manager.confirmTrade(20));
    assertEquals(1, authority.commitCalls);
    assertNull(manager.getPlayerSession(10));
    assertFalse(manager.isTrading(20));
  }

  private static TradeManager tradingManager() {
    TradeManager manager = new TradeManager();
    assertEquals(TradeState.RESULT_SUCCESS, manager.requestTrade(10, 20));
    assertEquals(TradeState.RESULT_SUCCESS, manager.acceptTrade(20));
    return manager;
  }

  private static final class StubAuthority implements TradeManager.TradeAuthority {
    int validation;
    boolean commitResult;
    int commitCalls;

    @Override public int validate(TradeSession session) {
      return validation;
    }

    @Override public boolean commit(TradeSession session) {
      commitCalls++;
      return commitResult;
    }
  }
}
