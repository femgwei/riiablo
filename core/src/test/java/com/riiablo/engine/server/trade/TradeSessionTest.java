package com.riiablo.engine.server.trade;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TradeSessionTest {
  @Test
  void rejectsDuplicateItemIdentityAndInvalidGeometry() {
    TradeSession session = new TradeSession(1, 10, 20);
    session.setState(TradeState.TRADING);

    TradeSlot first = new TradeSlot(77, 0, 0);
    assertTrue(session.addItem(10, first));

    TradeSlot duplicate = new TradeSlot(77, 1, 0);
    assertFalse(session.addItem(10, duplicate));

    TradeSlot zeroWidth = new TradeSlot(78, 2, 0);
    zeroWidth.setPosition(2, 0, 0, 1);
    assertFalse(session.addItem(10, zeroWidth));

    TradeSlot oversized = new TradeSlot(79, 0, 0);
    oversized.setPosition(0, 0, 5, 1);
    assertFalse(session.addItem(10, oversized));
  }

  @Test
  void duplicateRejectedAfterConfirmationWithoutChangingExistingOffer() {
    TradeSession session = new TradeSession(2, 10, 20);
    session.setState(TradeState.TRADING);
    assertTrue(session.addItem(10, new TradeSlot(80, 0, 0)));
    assertTrue(session.confirm(10));
    assertTrue(session.confirm(20));
    assertTrue(session.isBothConfirmed());

    // The session is confirmed and therefore immutable until the manager
    // completes/cancels it; the repeated item must not be appended.
    assertFalse(session.addItem(10, new TradeSlot(80, 1, 0)));
  }
}
