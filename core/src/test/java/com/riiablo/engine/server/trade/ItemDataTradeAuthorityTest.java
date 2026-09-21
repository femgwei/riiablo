package com.riiablo.engine.server.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.item.VendorPricing;
import com.riiablo.save.CharData;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ItemDataTradeAuthorityTest {
  @Test
  void atomicallyTransfersCarriedGoldAfterPreflight() {
    CharData first = CharData.obtain().set(Riiablo.NORMAL, false, "trade-a", Riiablo.AMAZON);
    CharData second = CharData.obtain().set(Riiablo.NORMAL, false, "trade-b", Riiablo.BARBARIAN);
    Map<Integer, CharData> players = new HashMap<>();
    players.put(10, first);
    players.put(20, second);

    TradeSession session = new TradeSession(7, 10, 20);
    session.setState(TradeState.TRADING);
    session.setGold(10, 0);
    session.setGold(20, 0);
    session.confirm(10);
    session.confirm(20);
    ItemDataTradeAuthority authority = new ItemDataTradeAuthority(players::get);

    assertEquals(TradeState.RESULT_SUCCESS, authority.validate(session));
    assertTrue(authority.commit(session));
    assertEquals(0, VendorPricing.carriedGold(first));
    assertEquals(0, VendorPricing.carriedGold(second));
  }

  @Test
  void rejectsGoldOfferThatExceedsOwnerWalletWithoutMutation() {
    CharData first = CharData.obtain().set(Riiablo.NORMAL, false, "trade-c", Riiablo.AMAZON);
    CharData second = CharData.obtain().set(Riiablo.NORMAL, false, "trade-d", Riiablo.BARBARIAN);
    Map<Integer, CharData> players = new HashMap<>();
    players.put(1, first);
    players.put(2, second);

    TradeSession session = new TradeSession(8, 1, 2);
    session.setState(TradeState.TRADING);
    session.setGold(1, 1);
    session.confirm(1);
    session.confirm(2);
    ItemDataTradeAuthority authority = new ItemDataTradeAuthority(players::get);

    assertEquals(TradeState.RESULT_NO_GOLD, authority.validate(session));
    assertEquals(0, VendorPricing.carriedGold(first));
    assertEquals(0, VendorPricing.carriedGold(second));
  }
}
