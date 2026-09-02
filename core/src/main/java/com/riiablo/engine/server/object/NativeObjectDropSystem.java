package com.riiablo.engine.server.object;

import java.util.List;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Levels;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.NativeObjectState;
import com.riiablo.engine.server.component.Object;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.NativeCainQuestEvent;
import com.riiablo.engine.server.event.ObjectInteractionEvent;
import com.riiablo.engine.server.object.NativeObjectOperateTable.Lifecycle;
import com.riiablo.engine.server.quest.Act1CainQuest;
import com.riiablo.engine.server.quest.Act1MalusQuest;
import com.riiablo.engine.Engine;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.Quality;
import com.riiablo.item.TreasureClassResolver;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.Map;

import net.mostlyoriginal.api.event.common.Subscribe;

/** Creates authoritative ground-item entities for first-time native container opens. */
public class NativeObjectDropSystem extends BaseSystem {
  private static final Logger log = LogManager.getLogger(NativeObjectDropSystem.class);
  private static final int CAIN_START_POSITION = 385;
  private static final float CAIN_TRANSFER_DELAY = 1f;
  private static final int[][] DROP_OFFSETS = {
      {2, 3}, {-2, 3}, {2, -3}, {-2, -3},
      {0, 2}, {2, 0}, {0, -2}, {-2, 0}, {0, 0}
  };

  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<NativeObjectState> mNativeObjectState;
  protected ComponentMapper<Object> mObject;
  protected ComponentMapper<Player> mPlayer;

  @Wire(name = "factory")
  protected EntityFactory factory;
  protected ItemGenerator itemGenerator;

  private NativeObjectDropAdapter adapter;
  private EntitySubscription players;
  private EntitySubscription monsters;
  private EntitySubscription objects;
  private final Vector2 dropPosition = new Vector2();
  private int tristramCain = Engine.INVALID_ENTITY;
  private int townCain = Engine.INVALID_ENTITY;
  private float cainTransferTimer;

  @Override
  protected void initialize() {
    players = world.getAspectSubscriptionManager().get(Aspect.all(Player.class));
    monsters = world.getAspectSubscriptionManager().get(
        Aspect.all(Monster.class, MapWrapper.class));
    objects = world.getAspectSubscriptionManager().get(
        Aspect.all(Object.class, Position.class, MapWrapper.class));
  }

  @Override
  protected void processSystem() {
    if (tristramCain == Engine.INVALID_ENTITY || townCain != Engine.INVALID_ENTITY) return;
    cainTransferTimer -= world.getDelta();
    if (cainTransferTimer > 0f) return;
    townCain = findTownCain();
    if (townCain == Engine.INVALID_ENTITY) townCain = spawnTownCain();
    if (townCain == Engine.INVALID_ENTITY) {
      cainTransferTimer = CAIN_TRANSFER_DELAY;
      return;
    }
    if (world.getEntityManager().isActive(tristramCain)) world.delete(tristramCain);
    log.info("[A1Q4] Cain transferred to Rogue Encampment: tristramCain={}, townCain={}",
        tristramCain, townCain);
    tristramCain = Engine.INVALID_ENTITY;
  }

  @Subscribe
  public void onCainQuestObject(NativeCainQuestEvent event) {
    if (event == null || !mPosition.has(event.objectEntityId)) return;
    if (event.action == NativeCainQuestEvent.PORTAL_TO_TRISTRAM) {
      createCainPortal(event);
      return;
    }
    if (event.action == NativeCainQuestEvent.CAIN_GIBBET) {
      releaseCain(event);
      return;
    }
    if (event.action != NativeCainQuestEvent.INIFUSS_TREE) return;
    Position position = mPosition.get(event.objectEntityId);
    int difficulty = difficulty(event.playerId);
    int itemLevel = 1;
    MapWrapper wrapper = mMapWrapper.get(event.objectEntityId);
    if (wrapper != null && wrapper.zone != null && wrapper.zone.level != null) {
      itemLevel = NativeObjectDropAdapter.areaLevel(wrapper.zone.level, difficulty);
    }
    Vector2 target = findDropPosition(wrapper == null ? null : wrapper.map,
        position.position, 0);
    if (createQuestItem(Act1CainQuest.BARK_SCROLL_CODE, difficulty, itemLevel,
        target.x, target.y)) {
      event.accept();
      log.info("[A1Q4] Scroll of Inifuss dropped: object={}, player={}",
          event.objectEntityId, event.playerId);
    }
  }

  private void releaseCain(NativeCainQuestEvent event) {
    if (factory == null) return;
    Position gibbet = mPosition.get(event.objectEntityId);
    MapWrapper wrapper = mMapWrapper.get(event.objectEntityId);
    if (gibbet == null || wrapper == null || wrapper.zone == null
        || wrapper.zone.level == null
        || wrapper.zone.level.Id != com.d2moo.common.drlg.D2LevelIds.LEVEL_TRISTRAM) {
      return;
    }
    Vector2 spawn = findDropPosition(wrapper.map, gibbet.position, 0).add(3f, 3f);
    int cain = factory.createMonster(
        com.riiablo.engine.server.monster.MonsterType.DECKARDCAIN, spawn.x, spawn.y);
    if (cain == Engine.INVALID_ENTITY) {
      log.error("[A1Q4] Cain creation failed: gibbet={}, player={}, position={}",
          event.objectEntityId, event.playerId, spawn);
      return;
    }
    tristramCain = cain;
    cainTransferTimer = CAIN_TRANSFER_DELAY;
    event.accept();
    log.info("[A1Q4] Cain released in Tristram: entity={}, gibbet={}, player={}, position={}",
        cain, event.objectEntityId, event.playerId, spawn);
  }

  private int findTownCain() {
    if (monsters == null) return Engine.INVALID_ENTITY;
    IntBag entities = monsters.getEntities();
    int[] ids = entities.getData();
    for (int i = 0, size = entities.size(); i < size; i++) {
      int entityId = ids[i];
      Monster monster = mMonster.get(entityId);
      MapWrapper wrapper = mMapWrapper.get(entityId);
      if (monster != null && monster.monstats != null
          && monster.monstats.hcIdx
              == com.riiablo.engine.server.monster.MonsterType.DECKARDCAIN_TOWN
          && isLevel(wrapper, com.d2moo.common.drlg.D2LevelIds.LEVEL_ROGUEENCAMPMENT)) {
        return entityId;
      }
    }
    return Engine.INVALID_ENTITY;
  }

  private int spawnTownCain() {
    if (factory == null || objects == null) return Engine.INVALID_ENTITY;
    IntBag entities = objects.getEntities();
    int[] ids = entities.getData();
    for (int i = 0, size = entities.size(); i < size; i++) {
      int entityId = ids[i];
      Object object = mObject.get(entityId);
      MapWrapper wrapper = mMapWrapper.get(entityId);
      if (object == null || object.base == null || object.base.Id != CAIN_START_POSITION
          || !isLevel(wrapper,
              com.d2moo.common.drlg.D2LevelIds.LEVEL_ROGUEENCAMPMENT)) continue;
      Vector2 position = mPosition.get(entityId).position;
      int cain = factory.createMonster(
          com.riiablo.engine.server.monster.MonsterType.DECKARDCAIN_TOWN,
          position.x, position.y);
      if (cain != Engine.INVALID_ENTITY) {
        log.info("[A1Q4] town Cain spawned: entity={}, marker={}, position={}",
            cain, entityId, position);
      }
      return cain;
    }
    log.warn("[A1Q4] Cain start-position object {} is not loaded; transfer deferred",
        CAIN_START_POSITION);
    return Engine.INVALID_ENTITY;
  }

  private static boolean isLevel(MapWrapper wrapper, int levelId) {
    return wrapper != null && wrapper.zone != null && wrapper.zone.level != null
        && wrapper.zone.level.Id == levelId;
  }

  private void createCainPortal(NativeCainQuestEvent event) {
    if (factory == null || event.destinationLevelId <= 0) return;
    Position stone = mPosition.get(event.objectEntityId);
    MapWrapper wrapper = mMapWrapper.get(event.objectEntityId);
    if (stone == null || wrapper == null || wrapper.map == null || wrapper.zone == null) return;

    Vector2 portalPosition = findPortalPosition(wrapper.map, stone.position);
    int visual = factory.createStaticObjectByClassId(60, portalPosition.x, portalPosition.y);
    int warp = factory.createQuestWarp(
        event.destinationLevelId, portalPosition.x, portalPosition.y);
    if (warp == Engine.INVALID_ENTITY) {
      if (visual != Engine.INVALID_ENTITY) world.delete(visual);
      log.error("[A1Q4] Tristram portal creation failed: stone={}, player={}, position={}",
          event.objectEntityId, event.playerId, portalPosition);
      return;
    }
    wrapper.zone.addWarp(warp);
    event.accept();
    log.info("[A1Q4] permanent Tristram portal created: visual={}, warp={}, stone={}, "
            + "player={}, destination={}, position={}",
        visual, warp, event.objectEntityId, event.playerId,
        event.destinationLevelId, portalPosition);
  }

  private Vector2 findPortalPosition(Map map, Vector2 stone) {
    dropPosition.set(stone).add(4f, 4f);
    if (map.flags(dropPosition) == 0) return dropPosition;
    for (int radius = 1; radius <= 8; radius++) {
      for (int dx = -radius; dx <= radius; dx++) {
        dropPosition.set(stone.x + 4 + dx, stone.y + 4 - radius);
        if (map.flags(dropPosition) == 0) return dropPosition;
        dropPosition.set(stone.x + 4 + dx, stone.y + 4 + radius);
        if (map.flags(dropPosition) == 0) return dropPosition;
      }
    }
    return dropPosition.set(stone).add(4f, 4f);
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
      if (createQuestItem(Act1MalusQuest.MALUS_CODE, difficulty, itemLevel,
          target.x, target.y)) {
        log.info("[A1Q3] Horadric Malus dropped: object={}, player={}, level={}",
            event.entityId, event.playerId, level.Id);
      }
      return;
    }
    RandomXS128 random = new RandomXS128(objectSeed(event, position));
    NativeObjectState state = mNativeObjectState.get(event.entityId);
    int interactType = state == null ? 0 : state.interactType;
    boolean locked = NativeObjectInteractTypeResolver.locked(interactType);
    boolean specialChest = state != null
        && state.kind == com.riiablo.map.NativePresetObjectResolver.Kind.SPECIAL_CHEST;

    // D2Game's handlers do not all use the chest routine.  In particular,
    // urns/jars roll only 21% of the time, while an ordinary chest has a 25%
    // no-drop branch. Locked and spark chests bypass that branch.
    if (!shouldDropContainer(event.operateFn, locked, specialChest,
        random.nextInt(100))) {
      log.info("[OBJECT_DROP] container opened without drop: entity={}, object={}, "
              + "operateFn={}, locked={}, sparkChest={}",
          event.entityId, event.objectClassId, event.operateFn, locked, specialChest);
      return;
    }

    Quality forcedContainerQuality = Quality.NONE;
    if (specialChest) {
      // OBJECTS_SpawnSpecialChest marks a spark chest; the opening handler
      // then chooses 5% RARE and otherwise MAGIC. The spawn RNG stream is not
      // exported, so use the stable per-object stream while preserving the
      // native quality distribution.
      forcedContainerQuality = sparkChestQuality(random.nextInt(100));
    }
    if (adapter == null) adapter = new NativeObjectDropAdapter(Riiablo.files);
    TreasureClassResolver.PlayerContext playerContext = playerContext(level.Id);
    List<NativeObjectDropAdapter.Drop> drops = adapter.rollChest(
        level, difficulty, random::nextInt, playerContext);

    int created = 0;
    for (int i = 0; i < drops.size(); i++) {
      NativeObjectDropAdapter.Drop drop = drops.get(i);
      Vector2 target = findDropPosition(wrapper.map, position.position, i);
      int entityId = createItem(drop, itemLevel, target.x, target.y, random,
          forcedContainerQuality);
      if (entityId >= 0) created++;
    }
    log.info("[OBJECT_DROP] opened entity={}, object={}, level={}, difficulty={}, "
            + "tier={}, players={}, sameLevelPlayers={}, effectivePlayers={}, rolled={}, created={}",
        event.entityId, event.objectClassId, level.Id, difficulty,
        adapter.chestTier(level, difficulty), playerContext.totalPlayers,
        playerContext.partyMembersInLevel,
        playerContext.effectivePlayerCount(), drops.size(), created);
  }

  /** Testable projection of D2Game's per-OperateFn container roll gate. */
  static boolean shouldDropContainer(int operateFn, boolean locked,
      boolean sparkChest, int percentRoll) {
    int roll = Math.max(0, Math.min(percentRoll, 99));
    if (operateFn == 3) {
      // OBJECTS_OperateFunction03_Urn_Basket_Jar: %100 <= 20.
      return roll <= 20;
    }
    if (operateFn == 4 && !locked && !sparkChest) {
      // OBJECTS_OperateFunction04_Chest: %100 >= 25 enters the drop branch.
      return roll >= 25;
    }
    return true;
  }

  /** Testable native spark-chest quality roll (5% rare, 95% magic). */
  static Quality sparkChestQuality(int percentRoll) {
    return Math.max(0, Math.min(percentRoll, 99)) < 5
        ? Quality.RARE : Quality.MAGIC;
  }

  private boolean createQuestItem(String code, int difficulty, int itemLevel,
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
      return entityId >= 0;
    } catch (Throwable t) {
      log.error("[QUEST_ITEM] creation failed: code={}", code, t);
      return false;
    }
  }

  private int createItem(NativeObjectDropAdapter.Drop drop, int itemLevel,
      float x, float y, RandomXS128 random, Quality forcedContainerQuality) {
    try {
      Item item = itemGenerator.generate(drop.code);
      item.ilvl = (byte) MathUtils.clamp(itemLevel, 1, 99);
      item.quality = forcedContainerQuality != Quality.NONE
          ? forcedContainerQuality : safeQuality(drop);
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
