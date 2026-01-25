package com.riiablo.engine.server.object;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.Pool;

import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 场景对象管理器 - 基于 D2MOO Objects.cpp 移植
 * 
 * <p>管理游戏中所有场景对象：
 * <ul>
 *   <li>对象创建和销毁</li>
 *   <li>对象交互处理</li>
 *   <li>神殿效果应用</li>
 *   <li>容器物品掉落</li>
 * </ul>
 * 
 * <p>参考：D2MOO/source/D2Game/src/OBJECTS/Objects.cpp
 * 
 * @author riiablo team
 */
public class ObjectManager {
  private static final Logger log = LogManager.getLogger(ObjectManager.class);

  //==========================================================================
  // 常量
  //==========================================================================

  /** 最大对象数量 */
  public static final int MAX_OBJECTS = 512;

  /** 传送门默认持续时间（帧） */
  public static final int PORTAL_DURATION = 600 * 25; // 10分钟

  //==========================================================================
  // 字段
  //==========================================================================

  /** 下一个对象 ID */
  private int nextObjectId = 1;

  /** 所有对象按 ID 索引 */
  private final IntMap<ObjectData> objectsById = new IntMap<>();

  /** 对象按场景索引 */
  private final IntMap<Array<ObjectData>> objectsByArea = new IntMap<>();

  /** 对象对象池 */
  private final Pool<ObjectData> objectPool = new Pool<ObjectData>() {
    @Override
    protected ObjectData newObject() {
      return new ObjectData();
    }
  };

  /** 对象交互回调 */
  private ObjectInteractCallback interactCallback;

  /** 神殿效果回调 */
  private ShrineEffectCallback shrineCallback;

  /** 物品掉落回调 */
  private ItemDropCallback dropCallback;

  //==========================================================================
  // 回调接口
  //==========================================================================

  /**
   * 对象交互回调
   */
  public interface ObjectInteractCallback {
    /**
     * 对象被交互时调用
     * 
     * @param obj 对象数据
     * @param playerId 交互的玩家 ID
     */
    void onObjectInteract(ObjectData obj, int playerId);
  }

  /**
   * 神殿效果回调
   */
  public interface ShrineEffectCallback {
    /**
     * 应用神殿效果
     * 
     * @param shrineType 神殿类型
     * @param playerId 玩家 ID
     * @param duration 效果持续时间（帧）
     */
    void onShrineEffect(int shrineType, int playerId, int duration);
  }

  /**
   * 物品掉落回调
   */
  public interface ItemDropCallback {
    /**
     * 容器掉落物品
     * 
     * @param obj 容器对象
     * @param quality 品质等级
     * @param count 掉落数量
     */
    void onItemDrop(ObjectData obj, int quality, int count);
  }

  //==========================================================================
  // 构造函数
  //==========================================================================

  public ObjectManager() {}

  //==========================================================================
  // 核心方法
  //==========================================================================

  /**
   * 创建场景对象
   * 
   * @param objectType 对象类型
   * @param classIndex 对象类索引
   * @param areaId 场景 ID
   * @param posX 位置 X
   * @param posY 位置 Y
   * @return 创建的对象数据
   */
  public ObjectData createObject(int objectType, int classIndex, int areaId, int posX, int posY) {
    if (objectsById.size >= MAX_OBJECTS) {
      log.warn("Maximum object count reached: {}", MAX_OBJECTS);
      return null;
    }

    ObjectData obj = objectPool.obtain();
    obj.reset();

    obj.objectId = nextObjectId++;
    obj.objectType = objectType;
    obj.classIndex = classIndex;
    obj.areaId = areaId;
    obj.posX = posX;
    obj.posY = posY;

    // 根据类型初始化特殊属性
    initializeObjectByType(obj);

    // 添加到管理列表
    objectsById.put(obj.objectId, obj);
    getOrCreateAreaObjects(areaId).add(obj);

    log.debug("Created object: type={}, pos=({},{}), area={}", 
        ObjectType.getName(objectType), posX, posY, areaId);

    return obj;
  }

  /**
   * 创建神殿
   */
  public ObjectData createShrine(int shrineType, int areaId, int posX, int posY) {
    ObjectData obj = createObject(ObjectType.SHRINE, 0, areaId, posX, posY);
    if (obj != null) {
      obj.shrineType = shrineType;
    }
    return obj;
  }

  /**
   * 创建玩家城镇传送门
   */
  public ObjectData createTownPortal(int ownerId, int areaId, int posX, int posY, int targetArea) {
    ObjectData obj = createObject(ObjectType.PORTAL, 0, areaId, posX, posY);
    if (obj != null) {
      obj.portalOwnerId = ownerId;
      obj.portalTargetArea = targetArea;
      obj.portalDuration = PORTAL_DURATION;
    }
    return obj;
  }

  /**
   * 创建传送点
   */
  public ObjectData createWaypoint(int areaId, int posX, int posY, boolean active) {
    ObjectData obj = createObject(ObjectType.WAYPOINT, 0, areaId, posX, posY);
    if (obj != null) {
      obj.waypointActive = active;
    }
    return obj;
  }

  /**
   * 销毁对象
   */
  public void destroyObject(int objectId) {
    ObjectData obj = objectsById.remove(objectId);
    if (obj == null) {
      return;
    }

    // 从场景列表移除
    Array<ObjectData> areaObjects = objectsByArea.get(obj.areaId);
    if (areaObjects != null) {
      areaObjects.removeValue(obj, true);
    }

    log.debug("Destroyed object: type={}, id={}", ObjectType.getName(obj.objectType), objectId);

    // 回收对象
    objectPool.free(obj);
  }

  /**
   * 更新所有对象
   * 
   * @param currentFrame 当前游戏帧
   */
  public void update(long currentFrame) {
    Array<ObjectData> toDestroy = new Array<>();

    for (ObjectData obj : objectsById.values()) {
      // 更新传送门持续时间
      if (obj.objectType == ObjectType.PORTAL && obj.portalOwnerId >= 0) {
        obj.portalDuration--;
        if (obj.portalDuration <= 0) {
          toDestroy.add(obj);
        }
      }
    }

    for (ObjectData obj : toDestroy) {
      destroyObject(obj.objectId);
    }
  }

  //==========================================================================
  // 交互处理
  //==========================================================================

  /**
   * 玩家与对象交互
   * 
   * @param objectId 对象 ID
   * @param playerId 玩家 ID
   * @return 是否交互成功
   */
  public boolean interact(int objectId, int playerId) {
    ObjectData obj = objectsById.get(objectId);
    if (obj == null) {
      log.debug("Object not found: {}", objectId);
      return false;
    }

    if (!obj.canInteract()) {
      log.debug("Object cannot be interacted: {}", objectId);
      return false;
    }

    // 检查锁定
    if (obj.locked) {
      log.debug("Object is locked: {}", objectId);
      // TODO: 检查玩家是否有钥匙
      return false;
    }

    // 根据类型处理交互
    switch (obj.objectType) {
      case ObjectType.CHEST:
      case ObjectType.BARREL:
      case ObjectType.URN:
      case ObjectType.SARCOPHAGUS:
        return interactContainer(obj, playerId);

      case ObjectType.SHRINE:
      case ObjectType.GEM_SHRINE:
      case ObjectType.WELL:
        return interactShrine(obj, playerId);

      case ObjectType.DOOR:
        return interactDoor(obj, playerId);

      case ObjectType.PORTAL:
        return interactPortal(obj, playerId);

      case ObjectType.WAYPOINT:
        return interactWaypoint(obj, playerId);

      default:
        // 通用回调
        if (interactCallback != null) {
          interactCallback.onObjectInteract(obj, playerId);
        }
        return true;
    }
  }

  /**
   * 与容器交互
   */
  private boolean interactContainer(ObjectData obj, int playerId) {
    if (obj.opened) {
      return false;
    }

    // 检查陷阱
    if (obj.trapped && obj.trapDamage > 0) {
      log.debug("Trap triggered: damage={}", obj.trapDamage);
      // TODO: 对玩家造成伤害
    }

    // 打开容器
    obj.open();

    // 掉落物品
    if (dropCallback != null && !obj.looted) {
      int dropCount = calculateDropCount(obj);
      dropCallback.onItemDrop(obj, obj.containerQuality, dropCount);
      obj.looted = true;
    }

    if (interactCallback != null) {
      interactCallback.onObjectInteract(obj, playerId);
    }

    log.debug("Opened container: id={}", obj.objectId);
    return true;
  }

  /**
   * 与神殿交互
   */
  private boolean interactShrine(ObjectData obj, int playerId) {
    if (obj.shrineActive) {
      return false;
    }

    // 激活神殿
    obj.activateShrine(0);

    // 应用神殿效果
    if (shrineCallback != null) {
      int duration = ShrineType.getDurationFrames(obj.shrineType);
      shrineCallback.onShrineEffect(obj.shrineType, playerId, duration);
    }

    if (interactCallback != null) {
      interactCallback.onObjectInteract(obj, playerId);
    }

    log.debug("Used shrine: type={}, player={}", 
        ShrineType.getName(obj.shrineType), playerId);
    return true;
  }

  /**
   * 与门交互
   */
  private boolean interactDoor(ObjectData obj, int playerId) {
    if (obj.doorOpen) {
      obj.close();
    } else {
      obj.open();
    }

    if (interactCallback != null) {
      interactCallback.onObjectInteract(obj, playerId);
    }

    log.debug("Door toggled: id={}, open={}", obj.objectId, obj.doorOpen);
    return true;
  }

  /**
   * 与传送门交互
   */
  private boolean interactPortal(ObjectData obj, int playerId) {
    // 传送门只有所有者或队友可以使用
    // TODO: 检查队伍关系

    if (interactCallback != null) {
      interactCallback.onObjectInteract(obj, playerId);
    }

    log.debug("Portal used: target={}, player={}", obj.portalTargetArea, playerId);
    return true;
  }

  /**
   * 与传送点交互
   */
  private boolean interactWaypoint(ObjectData obj, int playerId) {
    if (!obj.waypointActive) {
      obj.waypointActive = true;
      log.debug("Waypoint activated: area={}", obj.areaId);
    }

    if (interactCallback != null) {
      interactCallback.onObjectInteract(obj, playerId);
    }

    return true;
  }

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 根据类型初始化对象属性
   */
  private void initializeObjectByType(ObjectData obj) {
    switch (obj.objectType) {
      case ObjectType.CHEST:
        // 箱子可能有陷阱
        obj.trapped = MathUtils.random(99) < 10; // 10%几率有陷阱
        if (obj.trapped) {
          obj.trapDamage = MathUtils.random(10, 30);
        }
        break;

      case ObjectType.DOOR:
        obj.doorOpen = false;
        break;

      case ObjectType.BARREL:
      case ObjectType.URN:
        // 可破坏容器
        obj.containerQuality = 0;
        break;

      default:
        break;
    }
  }

  /**
   * 计算容器掉落数量
   */
  private int calculateDropCount(ObjectData obj) {
    switch (obj.objectType) {
      case ObjectType.CHEST:
        return MathUtils.random(1, 4);
      case ObjectType.BARREL:
      case ObjectType.URN:
        return MathUtils.random(99) < 30 ? 1 : 0;
      case ObjectType.SARCOPHAGUS:
        return MathUtils.random(2, 5);
      default:
        return 1;
    }
  }

  private Array<ObjectData> getOrCreateAreaObjects(int areaId) {
    Array<ObjectData> objects = objectsByArea.get(areaId);
    if (objects == null) {
      objects = new Array<>();
      objectsByArea.put(areaId, objects);
    }
    return objects;
  }

  //==========================================================================
  // 查询方法
  //==========================================================================

  /**
   * 获取对象
   */
  public ObjectData getObject(int objectId) {
    return objectsById.get(objectId);
  }

  /**
   * 获取场景中的所有对象
   */
  public Array<ObjectData> getAreaObjects(int areaId) {
    Array<ObjectData> objects = objectsByArea.get(areaId);
    return objects != null ? objects : new Array<>();
  }

  /**
   * 获取指定位置的对象
   */
  public ObjectData getObjectAt(int areaId, int posX, int posY) {
    Array<ObjectData> areaObjects = objectsByArea.get(areaId);
    if (areaObjects == null) {
      return null;
    }

    for (ObjectData obj : areaObjects) {
      if (obj.posX == posX && obj.posY == posY) {
        return obj;
      }
    }

    return null;
  }

  /**
   * 获取指定类型的对象
   */
  public Array<ObjectData> getObjectsByType(int areaId, int objectType) {
    Array<ObjectData> result = new Array<>();
    Array<ObjectData> areaObjects = objectsByArea.get(areaId);

    if (areaObjects != null) {
      for (ObjectData obj : areaObjects) {
        if (obj.objectType == objectType) {
          result.add(obj);
        }
      }
    }

    return result;
  }

  //==========================================================================
  // 配置方法
  //==========================================================================

  public void setInteractCallback(ObjectInteractCallback callback) {
    this.interactCallback = callback;
  }

  public void setShrineCallback(ShrineEffectCallback callback) {
    this.shrineCallback = callback;
  }

  public void setDropCallback(ItemDropCallback callback) {
    this.dropCallback = callback;
  }

  /**
   * 清除所有数据
   */
  public void clear() {
    for (ObjectData obj : objectsById.values()) {
      objectPool.free(obj);
    }
    objectsById.clear();
    objectsByArea.clear();
    log.debug("Cleared all objects");
  }
}
