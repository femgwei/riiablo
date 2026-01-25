package com.riiablo.engine.server.item;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.Pool;

import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 地面物品管理器 - 基于 D2MOO ItemMode.cpp 移植
 * 
 * <p>管理地面上的物品：
 * <ul>
 *   <li>物品掉落到地面</li>
 *   <li>物品过期消失</li>
 *   <li>物品拾取权限</li>
 *   <li>物品可见性</li>
 * </ul>
 * 
 * <p>在暗黑破坏神 II 中，掉落的物品有以下特性：
 * <ul>
 *   <li>物品在地面上有存在时限</li>
 *   <li>组队时物品有拾取权限分配</li>
 *   <li>不同品质物品显示不同颜色的名称</li>
 * </ul>
 * 
 * <p>参考：D2MOO/source/D2Game/src/ITEMS/ItemMode.cpp
 * 
 * @author riiablo team
 */
public class GroundItemManager {
  private static final Logger log = LogManager.getLogger(GroundItemManager.class);

  //==========================================================================
  // 常量
  //==========================================================================

  /** 物品在地面上的默认存在时间（秒） */
  public static final float DEFAULT_ITEM_LIFETIME = 600f; // 10 分钟

  /** 金币在地面上的存在时间（秒） */
  public static final float GOLD_LIFETIME = 600f;

  /** 玩家丢弃物品的拾取权限时间（秒） */
  public static final float OWNER_ONLY_DURATION = 10f;

  /** 队友可拾取权限时间（秒）- 从玩家专属时间之后 */
  public static final float PARTY_ONLY_DURATION = 10f;

  /** 物品开始淡出的时间（秒） */
  public static final float FADE_START_TIME = 30f;

  /** 物品淡出持续时间（秒） */
  public static final float FADE_DURATION = 5f;

  //==========================================================================
  // 内部类 - 地面物品数据
  //==========================================================================

  /**
   * 地面物品数据
   */
  public static class GroundItemData implements Pool.Poolable {
    /** 物品实体 ID */
    public int entityId = -1;

    /** 物品代码 */
    public String itemCode;

    /** 物品品质 */
    public int quality = ItemQuality.NORMAL;

    /** 物品等级 */
    public int itemLevel = 1;

    /** 位置 X */
    public float posX;

    /** 位置 Y */
    public float posY;

    /** 掉落者/所有者 ID（用于拾取权限） */
    public int ownerId = -1;

    /** 击杀者 ID（用于掉落权限） */
    public int killerId = -1;

    /** 队伍 ID（用于队伍拾取权限） */
    public int partyId = -1;

    /** 创建时间戳（游戏帧） */
    public long createdFrame = 0;

    /** 剩余存在时间（秒） */
    public float timeRemaining = DEFAULT_ITEM_LIFETIME;

    /** 是否正在淡出 */
    public boolean fading = false;

    /** 淡出时间 */
    public float fadeTime = 0;

    /** 是否是金币 */
    public boolean isGold = false;

    /** 金币数量（仅当 isGold=true 时有效） */
    public int goldAmount = 0;

    /** 是否已被拾取 */
    public boolean pickedUp = false;

    /** 是否已标记为删除 */
    public boolean markedForRemoval = false;

    @Override
    public void reset() {
      entityId = -1;
      itemCode = null;
      quality = ItemQuality.NORMAL;
      itemLevel = 1;
      posX = 0;
      posY = 0;
      ownerId = -1;
      killerId = -1;
      partyId = -1;
      createdFrame = 0;
      timeRemaining = DEFAULT_ITEM_LIFETIME;
      fading = false;
      fadeTime = 0;
      isGold = false;
      goldAmount = 0;
      pickedUp = false;
      markedForRemoval = false;
    }

    /**
     * 检查是否已过期
     */
    public boolean isExpired() {
      return timeRemaining <= 0;
    }

    /**
     * 获取当前透明度（0-1）
     */
    public float getAlpha() {
      if (!fading) {
        return 1.0f;
      }
      return Math.max(0, 1.0f - fadeTime / FADE_DURATION);
    }

    /**
     * 检查玩家是否可以拾取此物品
     * 
     * @param playerId 玩家实体 ID
     * @param playerPartyId 玩家队伍 ID
     * @param elapsedTime 物品掉落后经过的时间（秒）
     * @return true 如果可以拾取
     */
    public boolean canPickUp(int playerId, int playerPartyId, float elapsedTime) {
      // 已被拾取
      if (pickedUp) {
        return false;
      }

      // 玩家自己丢弃的物品，任何时候都可以拾取
      if (ownerId == playerId) {
        return true;
      }

      // 在所有者专属时间内，只有所有者可以拾取
      if (elapsedTime < OWNER_ONLY_DURATION && killerId >= 0 && killerId != playerId) {
        return false;
      }

      // 在队伍专属时间内，只有同队玩家可以拾取
      if (elapsedTime < OWNER_ONLY_DURATION + PARTY_ONLY_DURATION) {
        if (partyId >= 0 && partyId != playerPartyId) {
          return false;
        }
      }

      // 过了权限时间，所有人都可以拾取
      return true;
    }
  }

  //==========================================================================
  // 回调接口
  //==========================================================================

  /**
   * 物品事件回调
   */
  public interface GroundItemCallback {
    /**
     * 物品过期被移除
     */
    void onItemExpired(GroundItemData item);

    /**
     * 物品被拾取
     */
    void onItemPickedUp(GroundItemData item, int pickerId);
  }

  //==========================================================================
  // 字段
  //==========================================================================

  /** 所有地面物品（按实体 ID 索引） */
  private final IntMap<GroundItemData> groundItems = new IntMap<>();

  /** 活跃物品列表（用于遍历更新） */
  private final Array<GroundItemData> activeItems = new Array<>();

  /** 物品数据对象池 */
  private final Pool<GroundItemData> itemPool = new Pool<GroundItemData>() {
    @Override
    protected GroundItemData newObject() {
      return new GroundItemData();
    }
  };

  /** 回调 */
  private GroundItemCallback callback;

  /** 当前游戏帧 */
  private long currentFrame = 0;

  //==========================================================================
  // 构造函数
  //==========================================================================

  public GroundItemManager() {}

  //==========================================================================
  // 核心方法
  //==========================================================================

  /**
   * 添加物品到地面
   * 
   * @param entityId 物品实体 ID
   * @param itemCode 物品代码
   * @param quality 物品品质
   * @param itemLevel 物品等级
   * @param posX 位置 X
   * @param posY 位置 Y
   * @param killerId 击杀者 ID（决定拾取权限）
   * @param partyId 击杀者队伍 ID
   * @return 地面物品数据
   */
  public GroundItemData addItem(int entityId, String itemCode, int quality, int itemLevel,
      float posX, float posY, int killerId, int partyId) {

    GroundItemData item = itemPool.obtain();
    item.entityId = entityId;
    item.itemCode = itemCode;
    item.quality = quality;
    item.itemLevel = itemLevel;
    item.posX = posX;
    item.posY = posY;
    item.killerId = killerId;
    item.partyId = partyId;
    item.createdFrame = currentFrame;
    item.timeRemaining = DEFAULT_ITEM_LIFETIME;

    groundItems.put(entityId, item);
    activeItems.add(item);

    log.debug("Added ground item: entity={}, code={}, quality={} at ({},{})",
        entityId, itemCode, ItemQuality.getName(quality), posX, posY);

    return item;
  }

  /**
   * 添加金币到地面
   */
  public GroundItemData addGold(int entityId, int amount, float posX, float posY,
      int killerId, int partyId) {

    GroundItemData item = itemPool.obtain();
    item.entityId = entityId;
    item.isGold = true;
    item.goldAmount = amount;
    item.itemCode = "gld";
    item.posX = posX;
    item.posY = posY;
    item.killerId = killerId;
    item.partyId = partyId;
    item.createdFrame = currentFrame;
    item.timeRemaining = GOLD_LIFETIME;

    groundItems.put(entityId, item);
    activeItems.add(item);

    log.debug("Added gold: entity={}, amount={} at ({},{})",
        entityId, amount, posX, posY);

    return item;
  }

  /**
   * 玩家丢弃物品到地面
   */
  public GroundItemData dropItem(int entityId, String itemCode, int quality, int itemLevel,
      float posX, float posY, int ownerId) {

    GroundItemData item = addItem(entityId, itemCode, quality, itemLevel, posX, posY, -1, -1);
    item.ownerId = ownerId;

    log.debug("Player {} dropped item: entity={}", ownerId, entityId);

    return item;
  }

  /**
   * 更新所有地面物品
   * 
   * @param deltaTime 经过的时间（秒）
   */
  public void update(float deltaTime) {
    currentFrame++;

    // 从后向前遍历，方便删除
    for (int i = activeItems.size - 1; i >= 0; i--) {
      GroundItemData item = activeItems.get(i);

      // 已标记删除
      if (item.markedForRemoval || item.pickedUp) {
        removeItem(i, item);
        continue;
      }

      // 更新时间
      item.timeRemaining -= deltaTime;

      // 检查是否开始淡出
      if (!item.fading && item.timeRemaining <= FADE_START_TIME) {
        item.fading = true;
        log.debug("Item {} starting to fade", item.entityId);
      }

      // 更新淡出
      if (item.fading) {
        item.fadeTime += deltaTime;
      }

      // 检查是否过期
      if (item.isExpired()) {
        handleItemExpired(item);
        removeItem(i, item);
      }
    }
  }

  /**
   * 尝试拾取物品
   * 
   * @param entityId 物品实体 ID
   * @param pickerId 拾取者实体 ID
   * @param pickerPartyId 拾取者队伍 ID
   * @return true 如果拾取成功
   */
  public boolean pickUp(int entityId, int pickerId, int pickerPartyId) {
    GroundItemData item = groundItems.get(entityId);
    if (item == null) {
      return false;
    }

    // 计算经过的时间
    float elapsedTime = (currentFrame - item.createdFrame) / 25f; // 假设 25 FPS

    // 检查拾取权限
    if (!item.canPickUp(pickerId, pickerPartyId, elapsedTime)) {
      log.debug("Player {} cannot pick up item {} (permission denied)", pickerId, entityId);
      return false;
    }

    // 标记为已拾取
    item.pickedUp = true;

    if (callback != null) {
      callback.onItemPickedUp(item, pickerId);
    }

    log.debug("Player {} picked up item {}", pickerId, entityId);

    return true;
  }

  /**
   * 移除物品（直接删除，不触发过期回调）
   */
  public void removeItemById(int entityId) {
    GroundItemData item = groundItems.get(entityId);
    if (item != null) {
      item.markedForRemoval = true;
    }
  }

  //==========================================================================
  // 事件处理
  //==========================================================================

  private void handleItemExpired(GroundItemData item) {
    log.debug("Item {} expired at ({},{})", item.entityId, item.posX, item.posY);

    if (callback != null) {
      callback.onItemExpired(item);
    }
  }

  private void removeItem(int index, GroundItemData item) {
    activeItems.removeIndex(index);
    groundItems.remove(item.entityId);
    itemPool.free(item);
  }

  //==========================================================================
  // 查询方法
  //==========================================================================

  /**
   * 获取地面物品数据
   */
  public GroundItemData getItem(int entityId) {
    return groundItems.get(entityId);
  }

  /**
   * 获取地面物品数量
   */
  public int getItemCount() {
    return activeItems.size;
  }

  /**
   * 获取所有地面物品（只读）
   */
  public Array<GroundItemData> getActiveItems() {
    return activeItems;
  }

  /**
   * 获取指定区域内的物品
   */
  public Array<GroundItemData> getItemsInRadius(float centerX, float centerY, float radius) {
    Array<GroundItemData> result = new Array<>();
    float radiusSq = radius * radius;

    for (GroundItemData item : activeItems) {
      float dx = item.posX - centerX;
      float dy = item.posY - centerY;
      if (dx * dx + dy * dy <= radiusSq) {
        result.add(item);
      }
    }

    return result;
  }

  /**
   * 清除所有物品
   */
  public void clear() {
    for (GroundItemData item : activeItems) {
      itemPool.free(item);
    }
    activeItems.clear();
    groundItems.clear();
    log.debug("Cleared all ground items");
  }

  //==========================================================================
  // 配置方法
  //==========================================================================

  public void setCallback(GroundItemCallback callback) {
    this.callback = callback;
  }
}
