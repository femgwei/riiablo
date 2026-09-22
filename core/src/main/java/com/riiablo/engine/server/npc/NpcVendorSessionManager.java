package com.riiablo.engine.server.npc;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.riiablo.codec.excel.ItemEntry;
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
    /** Base items permanently supplied by this vendor at session creation. */
    private final ObjectSet<ItemEntry> permanentStockBases = new ObjectSet<>();
    public long revision = 1;
    private boolean gamble;
    public final Npc.Entry pricing;
    public final int difficulty;
    private final VendorGenerator generator;

    private Session(int npcEntityId, String npcType, boolean gamble, Npc.Entry pricing,
                    int difficulty, VendorGenerator generator) {
      this.npcEntityId = npcEntityId;
      this.npcType = npcType;
      this.gamble = gamble;
      this.pricing = pricing;
      this.difficulty = difficulty;
      this.generator = generator;
    }

    public boolean isGamble() { return gamble; }
  }

  private final IntMap<Session> sessions = new IntMap<>();
  /** Gamble sessions are keyed by character identity, not a transient ECS id. */
  private final ObjectMap<String, IntMap<Session>> gambleSessions = new ObjectMap<>();

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
    return open(npcEntityId, npcType, generator, gamble, pricing, difficulty,
        playerEntityId, Integer.toString(playerEntityId), refreshGamble);
  }

  /**
   * Opens a session using a stable character identity.  The entity id is kept
   * only for the NPC key; it is deliberately not used to own gamble stock so
   * a reconnect with a new ECS entity cannot inherit another character's page.
   */
  public synchronized Session open(int npcEntityId, String npcType,
                                    VendorGenerator generator, boolean gamble,
                                    Npc.Entry pricing, int difficulty,
                                    int playerEntityId, String playerKey,
                                    boolean refreshGamble) throws Exception {
    IntMap<Session> ownerSessions = null;
    Session session;
    if (gamble) {
      String key = stablePlayerKey(playerKey, playerEntityId);
      ownerSessions = gambleSessions.get(key);
      if (ownerSessions == null) {
        ownerSessions = new IntMap<>();
        gambleSessions.put(key, ownerSessions);
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
    session = new Session(npcEntityId, npcType, gamble, pricing, difficulty, generator);
    if (generator != null) {
      Array<Item> generated = gamble ? generator.generateGamble() : generator.generate(npcType);
      session.stock.addAll(generated);
    }
    rememberPermanentStock(session);
    if (gamble) ownerSessions.put(npcEntityId, session);
    else sessions.put(npcEntityId, session);
    return session;
  }

  private static void replaceStock(Session session, Array<Item> stock) {
    session.stock.clear();
    session.permanentStockBases.clear();
    if (stock != null) session.stock.addAll(stock);
    rememberPermanentStock(session);
    session.revision++;
  }

  private static void rememberPermanentStock(Session session) {
    if (session == null || session.gamble) return;
    for (Item item : session.stock) {
      if (VendorPricing.isPermanentStoreItem(item)) session.permanentStockBases.add(item.base);
    }
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
    return buy(session, player, itemId, false);
  }

  public synchronized int buy(Session session, CharData player, int itemId, boolean toCursor) {
    Item item = find(session, itemId);
    if (item == null || player == null || !item.hasFlag2(Item.ITEMFLAG2_INSTORE)) return 0;
    Item replacement = !session.isGamble() && VendorPricing.isInfiniteStockItem(item)
        && session.generator != null ? session.generator.restock(item) : null;
    int price = price(session, item, player);
    boolean purchased = session.isGamble()
        ? (toCursor ? VendorPricing.gambleToCursor(player, item)
            : VendorPricing.gamble(player, item))
        : (toCursor ? VendorPricing.buyToCursor(player, item, session.pricing)
            : VendorPricing.buy(player, item, session.pricing));
    if (!purchased) return 0;
    int stockIndex = session.stock.indexOf(item, true);
    if (replacement != null && stockIndex >= 0) session.stock.set(stockIndex, replacement);
    else session.stock.removeValue(item, true);
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
  public synchronized int sell(Session session, CharData player, int itemIndex) {
    if (player == null) return 0;
    com.riiablo.save.ItemData items = player.getItems();
    if (itemIndex < 0 || itemIndex >= items.getItems().size) return 0;
    Item item = items.getItem(itemIndex);
    int price = VendorPricing.sellPrice(item, session == null ? null : session.pricing,
        player, session == null ? 0 : session.difficulty);
    if (!VendorPricing.sell(player, itemIndex, session == null ? null : session.pricing,
        session == null ? 0 : session.difficulty)) return 0;

    // Native D2 places a duplicate of a sold item into the NPC's shared
    // inventory.  The item is no longer owned by the player, but retaining
    // this object gives the session a stable id for the subsequent buyback
    // request and preserves its affixes/quality.
    if (session != null && !session.isGamble()) {
      item.flags2 |= Item.ITEMFLAG2_INSTORE;
      item.location = com.riiablo.item.Location.STORED;
      item.storeLoc = com.riiablo.item.StoreLoc.NONE;
      item.gridX = 0;
      item.gridY = 0;
      item.vendorPrice = -1;
      if (!VendorPricing.isQuiver(item)
          && (item.base == null || !session.permanentStockBases.contains(item.base))
          && find(session, item.id) == null) {
        session.stock.insert(0, item);
        session.revision++;
      }
    }
    return price;
  }

  public synchronized void clear(int npcEntityId) {
    sessions.remove(npcEntityId);
    for (IntMap<Session> ownerSessions : gambleSessions.values()) {
      ownerSessions.remove(npcEntityId);
    }
  }

  public synchronized void clearPlayer(int playerEntityId) {
    gambleSessions.remove(Integer.toString(playerEntityId));
  }

  /** Clears a reconnecting character's private gamble sessions by stable name. */
  public synchronized void clearPlayer(int playerEntityId, String playerKey) {
    gambleSessions.remove(stablePlayerKey(playerKey, playerEntityId));
  }

  public synchronized void clearAll() {
    sessions.clear();
    gambleSessions.clear();
  }

  private static String stablePlayerKey(String playerKey, int playerEntityId) {
    return playerKey == null || playerKey.trim().isEmpty()
        ? Integer.toString(playerEntityId) : playerKey;
  }
}
