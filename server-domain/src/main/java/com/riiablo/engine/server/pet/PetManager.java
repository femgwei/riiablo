package com.riiablo.engine.server.pet;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.Pool;

import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 宠物管理器 - 基于 D2MOD PlayerPets.cpp 移植
 * 
 * <p>管理玩家的所有召唤物/宠物：
 * <ul>
 *   <li>宠物创建和销毁</li>
 *   <li>宠物数量限制</li>
 *   <li>宠物生命周期管理</li>
 *   <li>宠物属性计算</li>
 * </ul>
 * 
 * <p>参考：D2MOD/source/D2Game/src/PLAYER/PlayerPets.cpp
 * 
 * @author riiablo team
 */
public class PetManager {
  private static final Logger log = LogManager.getLogger(PetManager.class);

  //==========================================================================
  // 常量
  //==========================================================================

  /** 骷髅战士最大数量 */
  public static final int MAX_SKELETONS = 8;

  /** 骷髅法师最大数量 */
  public static final int MAX_SKELETON_MAGES = 8;

  /** 复活怪物最大数量 */
  public static final int MAX_REVIVES = 10;

  /** 乌鸦最大数量 */
  public static final int MAX_RAVENS = 5;

  /** 狼最大数量 */
  public static final int MAX_WOLVES = 5;

  /** 灰熊最大数量 */
  public static final int MAX_GRIZZLIES = 1;

  /** 九头蛇最大数量 */
  public static final int MAX_HYDRAS = 6;

  /** 陷阱最大数量 */
  public static final int MAX_TRAPS = 5;

  /** 影子最大数量 */
  public static final int MAX_SHADOWS = 1;

  /** 女武神最大数量 */
  public static final int MAX_VALKYRIES = 1;

  /** 石魔最大数量（同时只能有一个） */
  public static final int MAX_GOLEMS = 1;

  //==========================================================================
  // 字段
  //==========================================================================

  /** 下一个宠物 ID */
  private int nextPetId = 1;

  /** 所有宠物按 ID 索引 */
  private final IntMap<PetData> petsById = new IntMap<>();

  /** 所有宠物按所有者索引 */
  private final IntMap<Array<PetData>> petsByOwner = new IntMap<>();

  /** 宠物对象池 */
  private final Pool<PetData> petPool = new Pool<PetData>() {
    @Override
    protected PetData newObject() {
      return new PetData();
    }
  };

  /** 宠物创建回调 */
  private PetCreateCallback createCallback;

  /** 宠物销毁回调 */
  private PetDestroyCallback destroyCallback;

  //==========================================================================
  // 回调接口
  //==========================================================================

  /**
   * 宠物创建回调
   */
  public interface PetCreateCallback {
    /**
     * 宠物创建时调用
     * 
     * @param pet 宠物数据
     * @param posX 创建位置 X
     * @param posY 创建位置 Y
     * @return 创建的实体 ID
     */
    int onPetCreate(PetData pet, float posX, float posY);
  }

  /**
   * 宠物销毁回调
   */
  public interface PetDestroyCallback {
    /**
     * 宠物销毁时调用
     * 
     * @param pet 宠物数据
     */
    void onPetDestroy(PetData pet);
  }

  //==========================================================================
  // 构造函数
  //==========================================================================

  public PetManager() {}

  //==========================================================================
  // 核心方法
  //==========================================================================

  /**
   * 创建宠物
   * 
   * <p>参考 D2MOD PLAYERPETS_CreatePet
   * 
   * @param ownerId 所有者实体 ID
   * @param petType 宠物类型
   * @param skillId 召唤技能 ID
   * @param skillLevel 技能等级
   * @param posX 创建位置 X
   * @param posY 创建位置 Y
   * @return 创建的宠物数据，或 null 如果失败
   */
  public PetData createPet(int ownerId, int petType, int skillId, int skillLevel,
      float posX, float posY) {

    // 检查数量限制
    int currentCount = getPetCount(ownerId, petType);
    int maxCount = getMaxPetCount(petType);

    if (currentCount >= maxCount) {
      log.debug("Pet limit reached: type={}, count={}/{}", 
          PetType.getName(petType), currentCount, maxCount);

      // 如果是石魔或女武神等唯一召唤物，先销毁旧的
      if (maxCount == 1) {
        removeOldestPet(ownerId, petType);
      } else {
        return null;
      }
    }

    // 创建宠物数据
    PetData pet = petPool.obtain();
    pet.reset();

    pet.petId = nextPetId++;
    pet.petType = petType;
    pet.ownerId = ownerId;
    pet.skillId = skillId;
    pet.skillLevel = skillLevel;
    pet.alive = true;

    // 根据类型和等级初始化属性
    initializePetStats(pet, skillLevel);

    // 添加到管理列表
    petsById.put(pet.petId, pet);
    getOrCreateOwnerPets(ownerId).add(pet);

    // 回调创建实体
    if (createCallback != null) {
      pet.entityId = createCallback.onPetCreate(pet, posX, posY);
    }

    log.debug("Created pet: type={}, owner={}, id={}, hp={}", 
        PetType.getName(petType), ownerId, pet.petId, pet.maxHp);

    return pet;
  }

  /**
   * 销毁宠物
   * 
   * @param petId 宠物 ID
   */
  public void destroyPet(int petId) {
    PetData pet = petsById.remove(petId);
    if (pet == null) {
      return;
    }

    // 从所有者列表移除
    Array<PetData> ownerPets = petsByOwner.get(pet.ownerId);
    if (ownerPets != null) {
      ownerPets.removeValue(pet, true);
    }

    // 回调销毁
    if (destroyCallback != null) {
      destroyCallback.onPetDestroy(pet);
    }

    log.debug("Destroyed pet: type={}, id={}", PetType.getName(pet.petType), petId);

    // 回收对象
    petPool.free(pet);
  }

  /**
   * 销毁玩家的所有宠物
   * 
   * @param ownerId 所有者实体 ID
   */
  public void destroyAllPets(int ownerId) {
    Array<PetData> ownerPets = petsByOwner.get(ownerId);
    if (ownerPets == null || ownerPets.isEmpty()) {
      return;
    }

    // 复制列表以避免迭代时修改
    Array<PetData> toDestroy = new Array<>(ownerPets);
    for (PetData pet : toDestroy) {
      destroyPet(pet.petId);
    }

    log.debug("Destroyed all pets for owner {}", ownerId);
  }

  /**
   * 销毁指定类型的所有宠物
   * 
   * @param ownerId 所有者实体 ID
   * @param petType 宠物类型
   */
  public void destroyPetsByType(int ownerId, int petType) {
    Array<PetData> ownerPets = petsByOwner.get(ownerId);
    if (ownerPets == null) {
      return;
    }

    Array<PetData> toDestroy = new Array<>();
    for (PetData pet : ownerPets) {
      if (pet.petType == petType) {
        toDestroy.add(pet);
      }
    }

    for (PetData pet : toDestroy) {
      destroyPet(pet.petId);
    }
  }

  /**
   * 更新所有宠物
   * 
   * @param deltaFrames 经过的帧数
   */
  public void update(int deltaFrames) {
    // 检查过期宠物
    Array<PetData> toDestroy = new Array<>();

    for (PetData pet : petsById.values()) {
      // 更新持续时间
      if (pet.hasTimeLimit()) {
        pet.remainingDuration -= deltaFrames;
        if (pet.isExpired()) {
          toDestroy.add(pet);
        }
      }

      // 检查死亡
      if (!pet.alive) {
        toDestroy.add(pet);
      }
    }

    // 销毁过期/死亡的宠物
    for (PetData pet : toDestroy) {
      destroyPet(pet.petId);
    }
  }

  //==========================================================================
  // 查询方法
  //==========================================================================

  /**
   * 获取宠物数据
   */
  public PetData getPet(int petId) {
    return petsById.get(petId);
  }

  /**
   * 获取玩家的所有宠物
   */
  public Array<PetData> getOwnerPets(int ownerId) {
    Array<PetData> pets = petsByOwner.get(ownerId);
    return pets != null ? pets : new Array<>();
  }

  /**
   * 获取玩家指定类型的宠物
   */
  public Array<PetData> getOwnerPetsByType(int ownerId, int petType) {
    Array<PetData> result = new Array<>();
    Array<PetData> ownerPets = petsByOwner.get(ownerId);

    if (ownerPets != null) {
      for (PetData pet : ownerPets) {
        if (pet.petType == petType && pet.alive) {
          result.add(pet);
        }
      }
    }

    return result;
  }

  /**
   * 获取玩家指定类型的宠物数量
   */
  public int getPetCount(int ownerId, int petType) {
    int count = 0;
    Array<PetData> ownerPets = petsByOwner.get(ownerId);

    if (ownerPets != null) {
      for (PetData pet : ownerPets) {
        if (pet.petType == petType && pet.alive) {
          count++;
        }
      }
    }

    return count;
  }

  /**
   * 获取玩家的宠物总数
   */
  public int getTotalPetCount(int ownerId) {
    Array<PetData> ownerPets = petsByOwner.get(ownerId);
    return ownerPets != null ? ownerPets.size : 0;
  }

  /**
   * 根据实体 ID 查找宠物
   */
  public PetData getPetByEntityId(int entityId) {
    for (PetData pet : petsById.values()) {
      if (pet.entityId == entityId) {
        return pet;
      }
    }
    return null;
  }

  //==========================================================================
  // 属性初始化
  //==========================================================================

  /**
   * 初始化宠物属性
   * 
   * <p>参考 D2MOD PLAYERPETS_SetPetStats
   */
  private void initializePetStats(PetData pet, int skillLevel) {
    // 基础属性根据宠物类型和技能等级计算
    switch (pet.petType) {
      case PetType.SKELETON:
        initializeSkeletonStats(pet, skillLevel);
        break;

      case PetType.SKELETON_MAGE:
        initializeSkeletonMageStats(pet, skillLevel);
        break;

      case PetType.CLAY_GOLEM:
        initializeClayGolemStats(pet, skillLevel);
        break;

      case PetType.BLOOD_GOLEM:
        initializeBloodGolemStats(pet, skillLevel);
        break;

      case PetType.IRON_GOLEM:
        initializeIronGolemStats(pet, skillLevel);
        break;

      case PetType.FIRE_GOLEM:
        initializeFireGolemStats(pet, skillLevel);
        break;

      case PetType.REVIVE:
        initializeReviveStats(pet, skillLevel);
        break;

      case PetType.SPIRIT_WOLF:
        initializeSpiritWolfStats(pet, skillLevel);
        break;

      case PetType.DIRE_WOLF:
        initializeDireWolfStats(pet, skillLevel);
        break;

      case PetType.GRIZZLY:
        initializeGrizzlyStats(pet, skillLevel);
        break;

      case PetType.VALKYRIE:
        initializeValkyrieStats(pet, skillLevel);
        break;

      case PetType.SHADOW_WARRIOR:
      case PetType.SHADOW_MASTER:
        initializeShadowStats(pet, skillLevel);
        break;

      default:
        // 默认属性
        pet.maxHp = 50 + skillLevel * 10;
        pet.currentHp = pet.maxHp;
        pet.minDamage = 1 + skillLevel;
        pet.maxDamage = 3 + skillLevel * 2;
        pet.attackRating = 50 + skillLevel * 10;
        pet.defense = 20 + skillLevel * 5;
        break;
    }
  }

  private void initializeSkeletonStats(PetData pet, int skillLevel) {
    // 骷髅战士属性随技能等级增长
    pet.maxHp = 30 + skillLevel * 15;
    pet.currentHp = pet.maxHp;
    pet.minDamage = 2 + skillLevel;
    pet.maxDamage = 5 + skillLevel * 2;
    pet.attackRating = 40 + skillLevel * 15;
    pet.defense = 15 + skillLevel * 8;
    pet.moveSpeed = 7;
    pet.attackSpeed = 15;
  }

  private void initializeSkeletonMageStats(PetData pet, int skillLevel) {
    // 骷髅法师 - 较少生命，魔法伤害
    pet.maxHp = 25 + skillLevel * 10;
    pet.currentHp = pet.maxHp;
    pet.minDamage = 3 + skillLevel * 2;
    pet.maxDamage = 8 + skillLevel * 3;
    pet.attackRating = 30 + skillLevel * 10;
    pet.defense = 10 + skillLevel * 5;
    pet.moveSpeed = 6;
    pet.attackSpeed = 20;
  }

  private void initializeClayGolemStats(PetData pet, int skillLevel) {
    // 泥土石魔 - 高生命，减速敌人
    pet.maxHp = 200 + skillLevel * 50;
    pet.currentHp = pet.maxHp;
    pet.minDamage = 5 + skillLevel * 2;
    pet.maxDamage = 15 + skillLevel * 4;
    pet.attackRating = 60 + skillLevel * 20;
    pet.defense = 40 + skillLevel * 15;
    pet.moveSpeed = 5;
    pet.attackSpeed = 25;
  }

  private void initializeBloodGolemStats(PetData pet, int skillLevel) {
    // 血肉石魔 - 生命偷取，与主人共享生命
    pet.maxHp = 150 + skillLevel * 40;
    pet.currentHp = pet.maxHp;
    pet.minDamage = 8 + skillLevel * 3;
    pet.maxDamage = 20 + skillLevel * 5;
    pet.attackRating = 70 + skillLevel * 25;
    pet.defense = 35 + skillLevel * 12;
    pet.bloodGolemLifeLeech = 30 + skillLevel * 3;
    pet.moveSpeed = 6;
    pet.attackSpeed = 20;
  }

  private void initializeIronGolemStats(PetData pet, int skillLevel) {
    // 钢铁石魔 - 属性来自物品
    pet.maxHp = 250 + skillLevel * 60;
    pet.currentHp = pet.maxHp;
    pet.minDamage = 10 + skillLevel * 4;
    pet.maxDamage = 25 + skillLevel * 6;
    pet.attackRating = 80 + skillLevel * 30;
    pet.defense = 60 + skillLevel * 20;
    pet.moveSpeed = 5;
    pet.attackSpeed = 18;
  }

  private void initializeFireGolemStats(PetData pet, int skillLevel) {
    // 火焰石魔 - 火焰伤害，火焰光环
    pet.maxHp = 180 + skillLevel * 45;
    pet.currentHp = pet.maxHp;
    pet.minDamage = 15 + skillLevel * 5;
    pet.maxDamage = 35 + skillLevel * 8;
    pet.attackRating = 75 + skillLevel * 25;
    pet.defense = 45 + skillLevel * 15;
    pet.fireResist = 100; // 火焰免疫
    pet.moveSpeed = 6;
    pet.attackSpeed = 22;
  }

  private void initializeReviveStats(PetData pet, int skillLevel) {
    // 复活怪物 - 属性来自原怪物，有持续时间
    pet.maxHp = 100 + skillLevel * 30;
    pet.currentHp = pet.maxHp;
    pet.minDamage = 5 + skillLevel * 2;
    pet.maxDamage = 15 + skillLevel * 4;
    pet.attackRating = 50 + skillLevel * 15;
    pet.defense = 30 + skillLevel * 10;
    pet.duration = 180 * 25; // 180秒
    pet.remainingDuration = pet.duration;
    pet.moveSpeed = 7;
    pet.attackSpeed = 15;
  }

  private void initializeSpiritWolfStats(PetData pet, int skillLevel) {
    // 狼 - 快速攻击
    pet.maxHp = 80 + skillLevel * 25;
    pet.currentHp = pet.maxHp;
    pet.minDamage = 4 + skillLevel * 2;
    pet.maxDamage = 10 + skillLevel * 3;
    pet.attackRating = 60 + skillLevel * 20;
    pet.defense = 25 + skillLevel * 10;
    pet.moveSpeed = 9;
    pet.attackSpeed = 12;
  }

  private void initializeDireWolfStats(PetData pet, int skillLevel) {
    // 凶狼 - 更强的狼
    pet.maxHp = 120 + skillLevel * 35;
    pet.currentHp = pet.maxHp;
    pet.minDamage = 8 + skillLevel * 3;
    pet.maxDamage = 18 + skillLevel * 5;
    pet.attackRating = 80 + skillLevel * 25;
    pet.defense = 35 + skillLevel * 12;
    pet.moveSpeed = 9;
    pet.attackSpeed = 13;
  }

  private void initializeGrizzlyStats(PetData pet, int skillLevel) {
    // 灰熊 - 高伤害、高生命
    pet.maxHp = 350 + skillLevel * 80;
    pet.currentHp = pet.maxHp;
    pet.minDamage = 20 + skillLevel * 6;
    pet.maxDamage = 50 + skillLevel * 12;
    pet.attackRating = 100 + skillLevel * 35;
    pet.defense = 80 + skillLevel * 25;
    pet.moveSpeed = 7;
    pet.attackSpeed = 20;
  }

  private void initializeValkyrieStats(PetData pet, int skillLevel) {
    // 女武神 - 高等级时非常强
    pet.maxHp = 200 + skillLevel * 50;
    pet.currentHp = pet.maxHp;
    pet.minDamage = 15 + skillLevel * 4;
    pet.maxDamage = 35 + skillLevel * 8;
    pet.attackRating = 90 + skillLevel * 30;
    pet.defense = 70 + skillLevel * 20;
    pet.fireResist = 15 + skillLevel * 2;
    pet.coldResist = 15 + skillLevel * 2;
    pet.lightningResist = 15 + skillLevel * 2;
    pet.poisonResist = 15 + skillLevel * 2;
    pet.moveSpeed = 8;
    pet.attackSpeed = 15;
  }

  private void initializeShadowStats(PetData pet, int skillLevel) {
    // 影子战士/大师
    boolean isMaster = pet.petType == PetType.SHADOW_MASTER;
    int bonus = isMaster ? 20 : 0;

    pet.maxHp = 150 + skillLevel * 40 + bonus * 5;
    pet.currentHp = pet.maxHp;
    pet.minDamage = 10 + skillLevel * 3 + bonus;
    pet.maxDamage = 25 + skillLevel * 6 + bonus * 2;
    pet.attackRating = 70 + skillLevel * 25 + bonus * 3;
    pet.defense = 50 + skillLevel * 15 + bonus * 2;
    pet.moveSpeed = 8;
    pet.attackSpeed = 14;
  }

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 获取宠物类型的最大数量
   */
  private int getMaxPetCount(int petType) {
    switch (petType) {
      case PetType.SKELETON:
        return MAX_SKELETONS;
      case PetType.SKELETON_MAGE:
        return MAX_SKELETON_MAGES;
      case PetType.REVIVE:
        return MAX_REVIVES;
      case PetType.RAVEN:
        return MAX_RAVENS;
      case PetType.SPIRIT_WOLF:
      case PetType.DIRE_WOLF:
        return MAX_WOLVES;
      case PetType.GRIZZLY:
        return MAX_GRIZZLIES;
      case PetType.HYDRA:
        return MAX_HYDRAS;
      case PetType.VALKYRIE:
      case PetType.DECOY:
        return MAX_VALKYRIES;
      case PetType.SHADOW_WARRIOR:
      case PetType.SHADOW_MASTER:
        return MAX_SHADOWS;

      // 所有石魔共享一个槽位
      case PetType.CLAY_GOLEM:
      case PetType.BLOOD_GOLEM:
      case PetType.IRON_GOLEM:
      case PetType.FIRE_GOLEM:
        return MAX_GOLEMS;

      // 陷阱
      case PetType.LIGHTNING_SENTRY:
      case PetType.DEATH_SENTRY:
      case PetType.WAKE_OF_FIRE:
      case PetType.WAKE_OF_INFERNO:
      case PetType.CHARGED_BOLT_SENTRY:
      case PetType.BLADE_SENTINEL:
        return MAX_TRAPS;

      default:
        return 1;
    }
  }

  /**
   * 移除最老的宠物
   */
  private void removeOldestPet(int ownerId, int petType) {
    Array<PetData> ownerPets = petsByOwner.get(ownerId);
    if (ownerPets == null) {
      return;
    }

    // 如果是石魔，移除所有类型的石魔
    boolean isGolem = PetType.isGolem(petType);

    for (PetData pet : ownerPets) {
      boolean shouldRemove = isGolem ? PetType.isGolem(pet.petType) : pet.petType == petType;
      if (shouldRemove) {
        destroyPet(pet.petId);
        return;
      }
    }
  }

  private Array<PetData> getOrCreateOwnerPets(int ownerId) {
    Array<PetData> pets = petsByOwner.get(ownerId);
    if (pets == null) {
      pets = new Array<>();
      petsByOwner.put(ownerId, pets);
    }
    return pets;
  }

  //==========================================================================
  // 配置方法
  //==========================================================================

  public void setCreateCallback(PetCreateCallback callback) {
    this.createCallback = callback;
  }

  public void setDestroyCallback(PetDestroyCallback callback) {
    this.destroyCallback = callback;
  }

  /**
   * 清除所有数据
   */
  public void clear() {
    for (PetData pet : petsById.values()) {
      petPool.free(pet);
    }
    petsById.clear();
    petsByOwner.clear();
    log.debug("Cleared all pets");
  }
}
