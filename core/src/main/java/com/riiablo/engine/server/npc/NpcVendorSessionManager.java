package com.riiablo.engine.server.npc;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.riiablo.item.Item;
import com.riiablo.item.VendorGenerator;
import com.riiablo.item.VendorPricing;
import com.riiablo.save.CharData;
import com.riiablo.codec.excel.Npc;

/** Server-owned shared trade and player-private gamble inventories/revisions. */
public final class NpcVendorSessionManager {
  public static final class Session {
    public final int npcEntityId;
    public final String npcType;
    public final Array<Item> stock = new Array<>(true, 64, Item.class);
    public long revision = 1;
    private boolean gamble;
    public final Npc.Entry pricing;
    public final int difficulty;

    private Session(int npcEntityId, String npcType, boolean gamble, Npc.Entry pricing, int difficulty) {
      this.npcEntityId = npcEntityId;
      this.npcType = npcType;
      this.gamble = gamble;
      this.pricing = pricing;
      this.difficulty = difficulty;
    }

    public boolean isGamble() { return gamble; }
  }

  private final IntMap<Session> sessions = new IntMap<>();
  private final IntMap<IntMap<Session>> gambleSessions = new IntMap<>();

  /** Opens an existing session or creates the initial server inventory. */
  public synchronized Session open(int npcEntityId, String npcType,
                                    VendorGenerator generator, boolean gamble,
                                    Npc.Entry pricing, int difficulty) throws Exception {
    return open(npcEntityId, npcType, generator, gamble, pricing, difficulty, 0, false);
  }

  /**
   * Opens shared trade stock or player-private gamble stock. A new OPEN for an
   * existing gamble session replaces its page and advances the revision.
   */
  public synchronized Session open(int npcEntityId, String npcType,
                                    VendorGenerator generator, boolean gamble,
                                    Npc.Entry pricing, int difficulty,
                                    int playerEntityId, boolean refreshGamble) throws Exception {
    IntMap<Session> ownerSessions = null;
    Session session;
    if (gamble) {
      ownerSessions = gambleSessions.get(playerEntityId);
      if (ownerSessions == null) {
        ownerSessions = new IntMap<>();
        gambleSessions.put(playerEntityId, ownerSessions);
      }
      session = ownerSessions.get(npcEntityId);
    } else {
      session = sessions.get(npcEntityId);
    }
    if (session != null) {
      if (gamble && refreshGamble) {
        replaceStock(session, generator == null
            ? new Array<Item>(false, 0, Item.class)
            : generator.generateGamble());
      }
      return session;
    }
    session = new Session(npcEntityId, npcType, gamble, pricing, difficulty);
    if (generator != null) {
      Array<Item> generated = gamble ? generator.generateGamble() : generator.generate(npcType);
      session.stock.addAll(generated);
    }
    if (gamble) ownerSessions.put(npcEntityId, session);
    else sessions.put(npcEntityId, session);
    return session;
  }

  private static void replaceStock(Session session, Array<Item> stock) {
    session.stock.clear();
    if (stock != null) session.stock.addAll(stock);
    session.revision++;
  }

  public synchronized Session get(int npcEntityId) { return sessions.get(npcEntityId); }

  /** Returns the item only when it is still present in this server session. */
  public synchronized Item find(Session session, int itemId) {
    if (session == null) return null;
    for (Item item : session.stock) if (item != null && item.id == itemId) return item;
    return null;
  }

  /** Atomically buys and removes one stock item after all player checks pass. */
  public synchronized int buy(Session session, CharData player, int itemId) {
    Item item = find(session, itemId);
    if (item == null || player == null || !item.hasFlag2(Item.ITEMFLAG2_INSTORE)) return 0;
    int price = price(session, item, player);
    boolean purchased = session.isGamble()
        ? VendorPricing.gamble(player, item)
        : VendorPricing.buy(player, item, session.pricing);
    if (!purchased) return 0;
    session.stock.removeValue(item, true);
    session.revision++;
    return price;
  }

  public int price(Session session, Item item, CharData player) {
    if (session == null) return 0;
    return session.isGamble()
        ? VendorPricing.gamblePrice(item, player)
        : VendorPricing.buyPrice(item, session.pricing, player);
  }

  /** Atomically sells an owned item. Returns zero on validation failure. */
  public int sell(Session session, CharData player, int itemIndex) {
    if (player == null) return 0;
    com.riiablo.save.ItemData items = player.getItems();
    if (itemIndex < 0 || itemIndex >= items.getItems().size) return 0;
    Item item = items.getItem(itemIndex);
    int price = VendorPricing.sellPrice(item, session == null ? null : session.pricing,
        player, session == null ? 0 : session.difficulty);
    return VendorPricing.sell(player, itemIndex, session == null ? null : session.pricing,
        session == null ? 0 : session.difficulty) ? price : 0;
  }

  public synchronized void clear(int npcEntityId) {
    sessions.remove(npcEntityId);
    for (IntMap<Session> ownerSessions : gambleSessions.values()) {
      ownerSessions.remove(npcEntityId);
    }
  }

  public synchronized void clearPlayer(int playerEntityId) {
    gambleSessions.remove(playerEntityId);
  }

  public synchronized void clearAll() {
    sessions.clear();
    gambleSessions.clear();
  }
}
