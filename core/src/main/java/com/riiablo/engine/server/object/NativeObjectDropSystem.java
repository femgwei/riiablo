package com.riiablo.engine.server.object;

import java.util.List;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Levels;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.NativeObjectState;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.ObjectInteractionEvent;
import com.riiablo.engine.server.object.NativeObjectOperateTable.Lifecycle;
import com.riiablo.engine.server.quest.Act1MalusQuest;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.Quality;
import com.riiablo.item.TreasureClassResolver;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.Map;

import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/** Creates authoritative ground-item entities for first-time native container opens. */
public class NativeObjectDropSystem extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(NativeObjectDropSystem.class);
  private static final int[][] DROP_OFFSETS = {
      {2, 3}, {-2, 3}, {2, -3}, {-2, -3},
      {0, 2}, {2, 0}, {0, -2}, {-2, 0}, {0, 0}
  };

  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<NativeObjectState> mNativeObjectState;
  protected ComponentMapper<Player> mPlayer;

  @Wire(name = "factory")
  protected EntityFactory factory;
  protected ItemGenerator itemGenerator;

  private NativeObjectDropAdapter adapter;
  private EntitySubscription players;
  private final Vector2 dropPosition = new Vector2();

  @Override
  protected void initialize() {
    players = world.getAspectSubscriptionManager().get(Aspect.all(Player.class));
  }

  @Subscribe
  public void onObjectInteraction(ObjectInteractionEvent event) {
    if (event == null || !event.firstActivation()) return;
    boolean malus = event.objectClassId == NativeQuestObjectResolver.HORADRIC_MALUS
        && event.lifecycle == Lifecycle.QUEST_OBJECT;
    if (!malus && !isContainer(event.lifecycle)) return;
    if (factory == null || itemGenerator == null || Riiablo.files == null) {
      log.warn("[OBJECT_DROP] item creation unavailable: object={}, factory={}, generator={}",
          event.entityId, factory != null, itemGenerator != null);
      return;
    }

    Position position = mPosition.get(event.entityId);
    MapWrapper wrapper = mMapWrapper.get(event.entityId);
    Levels.Entry level = wrapper == null || wrapper.zone == null ? null : wrapper.zone.level;
    if (position == null || level == null) {
      log.warn("[OBJECT_DROP] object has no position/level: entity={}, object={}",
          event.entityId, event.objectClassId);
      return;
    }

    int difficulty = difficulty(event.playerId);
    int itemLevel = NativeObjectDropAdapter.areaLevel(level, difficulty);
    if (malus) {
      Vector2 target = findDropPosition(wrapper.map, position.position, 0);
      createQuestItem(Act1MalusQuest.MALUS_CODE, difficulty, itemLevel,
          target.x, target.y);
      log.info("[A1Q3] Horadric Malus dropped: object={}, player={}, level={}",
          event.entityId, event.playerId, level.Id);
      return;
    }
    RandomXS128 random = new RandomXS128(objectSeed(event, position));
    if (adapter == null) adapter = new NativeObjectDropAdapter(Riiablo.files);
    TreasureClassResolver.PlayerContext playerContext = playerContext(level.Id);
    List<NativeObjectDropAdapter.Drop> drops = adapter.rollChest(
        level, difficulty, random::nextInt, playerContext);

    int created = 0;
    for (int i = 0; i < drops.size(); i++) {
      NativeObjectDropAdapter.Drop drop = drops.get(i);
      Vector2 target = findDropPosition(wrapper.map, position.position, i);
      int entityId = createItem(drop, itemLevel, target.x, target.y, random);
      if (entityId >= 0) created++;
    }
    log.info("[OBJECT_DROP] opened entity={}, object={}, level={}, difficulty={}, "
            + "tier={}, players={}, sameLevelPlayers={}, effectivePlayers={}, rolled={}, created={}",
        event.entityId, event.objectClassId, level.Id, difficulty,
        adapter.chestTier(level, difficulty), playerContext.totalPlayers,
        playerContext.partyMembersInLevel,
        playerContext.effectivePlayerCount(), drops.size(), created);
  }

  private void createQuestItem(String code, int difficulty, int itemLevel,
      float x, float y) {
    try {
      Item item = itemGenerator.generate(code);
      item.ilvl = (byte) MathUtils.clamp(itemLevel, 1, 99);
      item.version = Item.VERSION_110;
      item.quality = Quality.NORMAL;
      item.flags |= Item.ITEMFLAG_IDENTIFIED;
      item.attrs.base().put(Stat.questitemdifficulty, difficulty);
      item.attrs.reset();
      int entityId = factory.createItem(item, x, y);
      item.id = entityId;
    } catch (Throwable t) {
      log.error("[A1Q3] Horadric Malus item creation failed: code={}", code, t);
    }
  }

  private int createItem(NativeObjectDropAdapter.Drop drop, int itemLevel,
      float x, float y, RandomXS128 random) {
    try {
      Item item = itemGenerator.generate(drop.code);
      item.ilvl = (byte) MathUtils.clamp(itemLevel, 1, 99);
      item.quality = safeQuality(drop);
      item.flags |= Item.ITEMFLAG_IDENTIFIED;
      if (drop.isGold()) {
        int baseGold = itemLevel + random.nextInt(Math.max(1, 5 * itemLevel));
        long adjusted = (long) baseGold * drop.goldMultiplier >> 8;
        item.attrs.base().put(Stat.quantity, (int) Math.max(1L, Math.min(adjusted, Integer.MAX_VALUE)));
      }
      int entityId = factory.createItem(item, x, y);
      item.id = entityId;
      return entityId;
    } catch (Throwable t) {
      log.error("[OBJECT_DROP] item creation failed: token={}, code={}, ilvl={}",
          drop.sourceToken, drop.code, itemLevel, t);
      return -1;
    }
  }

  private Quality safeQuality(NativeObjectDropAdapter.Drop drop) {
    if (drop.forcedQuality == Quality.UNIQUE || drop.forcedQuality == Quality.SET) {
      // Unique/set property application is a later migration stage. MAGIC is
      // serializable today and avoids emitting structurally incomplete items.
      log.warn("[OBJECT_DROP] {} properties unavailable; downgrade token {} to MAGIC",
          drop.forcedQuality, drop.sourceToken);
      return Quality.MAGIC;
    }
    return Quality.NORMAL;
  }

  private int difficulty(int playerId) {
    Player player = mPlayer.get(playerId);
    return player == null || player.data == null ? Riiablo.NORMAL
        : Math.max(0, Math.min(player.data.diff, 2));
  }

  /**
   * Builds the native NoDrop context for the object's level.
   *
   * <p>D2Game uses all connected players for the total count, but only living
   * party members in the object's level for the local-party component. The
   * party manager is not currently registered in the client world, so the
   * authoritative ECS level membership is used as the conservative fallback;
   * this fixes the previous hard-coded value of one.</p>
   */
  private TreasureClassResolver.PlayerContext playerContext(int levelId) {
    int total = players == null ? 1 : players.getEntities().size();
    int sameLevel = 0;
    if (players != null) {
      int[] ids = players.getEntities().getData();
      for (int i = 0, s = players.getEntities().size(); i < s; i++) {
        MapWrapper wrapper = mMapWrapper.get(ids[i]);
        if (wrapper != null && wrapper.zone != null && wrapper.zone.level != null
            && wrapper.zone.level.Id == levelId) {
          sameLevel++;
        }
      }
    }
    return playerContextForCounts(total, sameLevel);
  }

  /** Testable normalization for native total/same-level player counts. */
  static TreasureClassResolver.PlayerContext playerContextForCounts(
      int totalPlayers, int sameLevelPlayers) {
    return new TreasureClassResolver.PlayerContext(totalPlayers,
        Math.max(1, sameLevelPlayers));
  }

  private long objectSeed(ObjectInteractionEvent event, Position position) {
    NativeObjectState state = mNativeObjectState.get(event.entityId);
    long seed = 0x9E3779B97F4A7C15L;
    seed = 31 * seed + event.objectClassId;
    seed = 31 * seed + (state == null ? 0 : state.presetIndex);
    seed = 31 * seed + Float.floatToRawIntBits(position.position.x);
    seed = 31 * seed + Float.floatToRawIntBits(position.position.y);
    return seed;
  }

  private Vector2 findDropPosition(Map map, Vector2 origin, int index) {
    for (int i = 0; i < DROP_OFFSETS.length; i++) {
      int[] offset = DROP_OFFSETS[(index + i) % DROP_OFFSETS.length];
      dropPosition.set(origin.x + offset[0], origin.y + offset[1]);
      if (map == null || map.flags(dropPosition) == 0) return dropPosition;
    }
    return dropPosition.set(origin);
  }

  private static boolean isContainer(Lifecycle lifecycle) {
    return lifecycle == Lifecycle.ANIMATED_CONTAINER
        || lifecycle == Lifecycle.INSTANT_CONTAINER;
  }
}
