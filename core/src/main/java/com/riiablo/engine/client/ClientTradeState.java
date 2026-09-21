package com.riiablo.engine.client;

import com.badlogic.gdx.utils.Array;
import com.riiablo.net.packet.d2gs.TradeItemSnapshot;
import com.riiablo.net.packet.d2gs.TradeResult;

/** Client-side copy of the authoritative player trade session snapshot. */
public final class ClientTradeState {
  private final Array<Item> sourceItems = new Array<>(false, 4, Item.class);
  private final Array<Item> targetItems = new Array<>(false, 4, Item.class);
  private long revision;
  private long lastRequestId;
  private boolean lastSuccess;
  private String lastReason = "";
  private byte lastOperation;
  private int sessionId;
  private int sourceEntityId = -1;
  private int targetEntityId = -1;
  private int state;
  private long sourceGold;
  private long targetGold;
  private boolean active;

  public void apply(TradeResult result) {
    sourceItems.clear();
    targetItems.clear();
    for (int i = 0; i < result.sourceItemsLength(); i++) {
      sourceItems.add(Item.from(result.sourceItems(i)));
    }
    for (int i = 0; i < result.targetItemsLength(); i++) {
      targetItems.add(Item.from(result.targetItems(i)));
    }
    if (result.requestId() != 0) {
      lastRequestId = result.requestId();
      lastSuccess = result.success();
      lastReason = result.reason() == null ? "" : result.reason();
      lastOperation = result.operation();
    }
    sessionId = result.sessionId();
    sourceEntityId = result.sourceEntityId();
    targetEntityId = result.targetEntityId();
    state = result.state();
    sourceGold = result.sourceGold();
    targetGold = result.targetGold();
    active = state == com.riiablo.engine.server.trade.TradeState.PENDING
        || state == com.riiablo.engine.server.trade.TradeState.INVITED
        || state == com.riiablo.engine.server.trade.TradeState.TRADING
        || state == com.riiablo.engine.server.trade.TradeState.CONFIRMED;
    revision++;
  }

  public Array<Item> sourceItems() { return sourceItems; }
  public Array<Item> targetItems() { return targetItems; }
  public long revision() { return revision; }
  public long lastRequestId() { return lastRequestId; }
  public boolean lastSuccess() { return lastSuccess; }
  public String lastReason() { return lastReason; }
  public byte lastOperation() { return lastOperation; }
  public int sessionId() { return sessionId; }
  public int sourceEntityId() { return sourceEntityId; }
  public int targetEntityId() { return targetEntityId; }
  public int state() { return state; }
  public long sourceGold() { return sourceGold; }
  public long targetGold() { return targetGold; }
  public boolean active() { return active; }

  public static final class Item {
    public int itemId;
    public int x;
    public int y;
    public int width;
    public int height;
    public long quantity;
    public boolean identified;

    static Item from(TradeItemSnapshot wire) {
      Item item = new Item();
      item.itemId = wire.itemId();
      item.x = wire.x();
      item.y = wire.y();
      item.width = wire.width();
      item.height = wire.height();
      item.quantity = wire.quantity();
      item.identified = wire.identified();
      return item;
    }
  }
}
