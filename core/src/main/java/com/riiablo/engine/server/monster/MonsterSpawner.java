package com.riiablo.engine.server.monster;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;

import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 怪物生成器 - 基于 D2MOD MonsterSpawn.cpp 移植
 * 
 * <p>管理怪物的生成逻辑：
 * <ul>
 *   <li>普通怪物群生成</li>
 *   <li>冠军怪生成</li>
 *   <li>暗金怪生成（带词缀）</li>
 *   <li>BOSS 生成</li>
 * </ul>
 * 
 * <p>参考：D2MOD/source/D2Game/src/MONSTER/MonsterSpawn.cpp
 * 
 * @author riiablo team
 */
public class MonsterSpawner {
  private static final Logger log = LogManager.getLogger(MonsterSpawner.class);

  //==========================================================================
  // 常量
  //==========================================================================

  /** 普通怪物群最小数量 */
  public static final int MIN_PACK_SIZE = 2;

  /** 普通怪物群最大数量 */
  public static final int MAX_PACK_SIZE = 6;

  /** 冠军怪群最小数量 */
  public static final int MIN_CHAMPION_PACK = 2;

  /** 冠军怪群最大数量 */
  public static final int MAX_CHAMPION_PACK = 4;

  /** 暗金怪随从最小数量 */
  public static final int MIN_UNIQUE_MINIONS = 3;

  /** 暗金怪随从最大数量 */
  public static final int MAX_UNIQUE_MINIONS = 5;

  /** 精英怪出现几率（百分比） */
  public static final int ELITE_SPAWN_CHANCE = 8;

  /** 冠军怪占精英怪的比例（百分比） */
  public static final int CHAMPION_RATIO = 60;

  //==========================================================================
  // 字段
  //==========================================================================

  /** 怪物创建回调 */
  private MonsterCreateCallback createCallback;

  /** 当前难度 */
  private int difficulty = 0; // 0=普通, 1=噩梦, 2=地狱

  //==========================================================================
  // 回调接口
  //==========================================================================

  /**
   * 怪物创建回调
   */
  public interface MonsterCreateCallback {
    /**
     * 创建怪物
     * 
     * @param monsterId 怪物类型 ID
     * @param posX 位置 X
     * @param posY 位置 Y
     * @param rank 怪物等级
     * @param affixes 词缀（暗金怪）
     * @param championType 冠军类型（冠军怪）
     * @param uniqueId 暗金怪主人 ID（随从用）
     * @return 创建的实体 ID
     */
    int onMonsterCreate(int monsterId, float posX, float posY, 
        int rank, long affixes, int championType, int uniqueId);
  }

  //==========================================================================
  // 构造函数
  //==========================================================================

  public MonsterSpawner() {}

  //==========================================================================
  // 核心方法
  //==========================================================================

  /**
   * 生成怪物群
   * 
   * <p>参考 D2MOD MONSTERSPAWN_SpawnMonsterPack
   * 
   * @param monsterId 怪物类型 ID
   * @param centerX 中心位置 X
   * @param centerY 中心位置 Y
   * @param areaLevel 区域等级
   * @return 生成的怪物实体 ID 列表
   */
  public Array<Integer> spawnPack(int monsterId, float centerX, float centerY, int areaLevel) {
    Array<Integer> spawned = new Array<>();

    // 决定生成什么类型的怪物群
    boolean isElite = MathUtils.random(99) < ELITE_SPAWN_CHANCE;

    if (isElite) {
      boolean isChampion = MathUtils.random(99) < CHAMPION_RATIO;
      if (isChampion) {
        spawned.addAll(spawnChampionPack(monsterId, centerX, centerY, areaLevel));
      } else {
        spawned.addAll(spawnUniquePack(monsterId, centerX, centerY, areaLevel));
      }
    } else {
      spawned.addAll(spawnNormalPack(monsterId, centerX, centerY, areaLevel));
    }

    return spawned;
  }

  /**
   * 生成普通怪物群
   */
  public Array<Integer> spawnNormalPack(int monsterId, float centerX, float centerY, int areaLevel) {
    Array<Integer> spawned = new Array<>();

    int packSize = MathUtils.random(MIN_PACK_SIZE, MAX_PACK_SIZE);

    for (int i = 0; i < packSize; i++) {
      float offsetX = MathUtils.random(-3f, 3f);
      float offsetY = MathUtils.random(-3f, 3f);

      int entityId = createMonster(monsterId, centerX + offsetX, centerY + offsetY,
          MonsterRank.NORMAL, MonsterAffix.NONE, -1, -1);

      if (entityId >= 0) {
        spawned.add(entityId);
      }
    }

    log.debug("Spawned normal pack: monster={}, count={}", monsterId, spawned.size);
    return spawned;
  }

  /**
   * 生成冠军怪群
   */
  public Array<Integer> spawnChampionPack(int monsterId, float centerX, float centerY, int areaLevel) {
    Array<Integer> spawned = new Array<>();

    int packSize = MathUtils.random(MIN_CHAMPION_PACK, MAX_CHAMPION_PACK);
    int championType = MathUtils.random(0, 4); // 随机冠军类型

    for (int i = 0; i < packSize; i++) {
      float offsetX = MathUtils.random(-2f, 2f);
      float offsetY = MathUtils.random(-2f, 2f);

      int entityId = createMonster(monsterId, centerX + offsetX, centerY + offsetY,
          MonsterRank.CHAMPION, MonsterAffix.NONE, championType, -1);

      if (entityId >= 0) {
        spawned.add(entityId);
      }
    }

    log.debug("Spawned champion pack: monster={}, type={}, count={}", 
        monsterId, MonsterRank.getChampionTypeName(championType), spawned.size);
    return spawned;
  }

  /**
   * 生成暗金怪及其随从
   */
  public Array<Integer> spawnUniquePack(int monsterId, float centerX, float centerY, int areaLevel) {
    Array<Integer> spawned = new Array<>();

    // 生成随机词缀
    long affixes = generateRandomAffixes(areaLevel);

    // 生成暗金怪主体
    int uniqueEntityId = createMonster(monsterId, centerX, centerY,
        MonsterRank.UNIQUE, affixes, -1, -1);

    if (uniqueEntityId < 0) {
      return spawned;
    }
    spawned.add(uniqueEntityId);

    // 生成随从
    int minionCount = MathUtils.random(MIN_UNIQUE_MINIONS, MAX_UNIQUE_MINIONS);

    for (int i = 0; i < minionCount; i++) {
      float offsetX = MathUtils.random(-3f, 3f);
      float offsetY = MathUtils.random(-3f, 3f);

      int entityId = createMonster(monsterId, centerX + offsetX, centerY + offsetY,
          MonsterRank.MINION, MonsterAffix.NONE, -1, uniqueEntityId);

      if (entityId >= 0) {
        spawned.add(entityId);
      }
    }

    log.debug("Spawned unique pack: monster={}, affixes=[{}], minions={}", 
        monsterId, MonsterAffix.affixesToString(affixes), minionCount);
    return spawned;
  }

  /**
   * 生成 BOSS
   */
  public int spawnBoss(int monsterId, float posX, float posY, long affixes) {
    int entityId = createMonster(monsterId, posX, posY,
        MonsterRank.BOSS, affixes, -1, -1);

    log.debug("Spawned boss: monster={}, affixes=[{}]", 
        monsterId, MonsterAffix.affixesToString(affixes));

    return entityId;
  }

  /**
   * 生成超级暗金怪
   */
  public int spawnSuperUnique(int monsterId, float posX, float posY, long affixes) {
    int entityId = createMonster(monsterId, posX, posY,
        MonsterRank.SUPER_UNIQUE, affixes, -1, -1);

    log.debug("Spawned super unique: monster={}, affixes=[{}]", 
        monsterId, MonsterAffix.affixesToString(affixes));

    return entityId;
  }

  //==========================================================================
  // 词缀生成
  //==========================================================================

  /**
   * 生成随机词缀
   * 
   * <p>参考 D2MOD MONSTERUNIQUE_RollAffixes
   */
  public long generateRandomAffixes(int areaLevel) {
    long[] availableAffixes;
    int affixCount;

    switch (difficulty) {
      case 0: // 普通
        availableAffixes = MonsterAffix.NORMAL_AFFIXES;
        affixCount = MonsterAffix.NORMAL_AFFIX_COUNT;
        break;
      case 1: // 噩梦
        availableAffixes = MonsterAffix.NIGHTMARE_AFFIXES;
        affixCount = MonsterAffix.NIGHTMARE_AFFIX_COUNT;
        break;
      case 2: // 地狱
      default:
        availableAffixes = MonsterAffix.HELL_AFFIXES;
        affixCount = MonsterAffix.HELL_AFFIX_COUNT;
        break;
    }

    long result = MonsterAffix.NONE;
    int attempts = 0;
    int maxAttempts = 100;

    while (countAffixes(result) < affixCount && attempts < maxAttempts) {
      attempts++;

      // 随机选择一个词缀
      int index = MathUtils.random(availableAffixes.length - 1);
      long affix = availableAffixes[index];

      // 检查是否已有该词缀
      if (MonsterAffix.hasAffix(result, affix)) {
        continue;
      }

      // 检查冲突（例如不能同时有火焰强化和火焰免疫）
      if (!isAffixCompatible(result, affix)) {
        continue;
      }

      result = MonsterAffix.addAffix(result, affix);
    }

    return result;
  }

  /**
   * 统计词缀数量
   */
  private int countAffixes(long affixes) {
    int count = 0;
    long temp = affixes;
    while (temp != 0) {
      count += (int) (temp & 1);
      temp >>>= 1;
    }
    return count;
  }

  /**
   * 检查词缀兼容性
   */
  private boolean isAffixCompatible(long existing, long newAffix) {
    // 不能同时有元素强化和对应的免疫
    if (newAffix == MonsterAffix.FIRE_IMMUNE && 
        MonsterAffix.hasAffix(existing, MonsterAffix.FIRE_ENCHANTED)) {
      return false;
    }
    if (newAffix == MonsterAffix.COLD_IMMUNE && 
        MonsterAffix.hasAffix(existing, MonsterAffix.COLD_ENCHANTED)) {
      return false;
    }
    if (newAffix == MonsterAffix.LIGHTNING_IMMUNE && 
        MonsterAffix.hasAffix(existing, MonsterAffix.LIGHTNING_ENCHANTED)) {
      return false;
    }
    if (newAffix == MonsterAffix.POISON_IMMUNE && 
        MonsterAffix.hasAffix(existing, MonsterAffix.POISON_ENCHANTED)) {
      return false;
    }

    // 不能有多个相同类型的免疫
    if (MonsterAffix.isImmuneAffix(newAffix)) {
      int immuneCount = 0;
      if (MonsterAffix.hasAffix(existing, MonsterAffix.FIRE_IMMUNE)) immuneCount++;
      if (MonsterAffix.hasAffix(existing, MonsterAffix.COLD_IMMUNE)) immuneCount++;
      if (MonsterAffix.hasAffix(existing, MonsterAffix.LIGHTNING_IMMUNE)) immuneCount++;
      if (MonsterAffix.hasAffix(existing, MonsterAffix.POISON_IMMUNE)) immuneCount++;
      if (MonsterAffix.hasAffix(existing, MonsterAffix.PHYSICAL_IMMUNE)) immuneCount++;
      if (immuneCount >= 2) {
        return false; // 最多2个免疫
      }
    }

    return true;
  }

  //==========================================================================
  // 辅助方法
  //==========================================================================

  private int createMonster(int monsterId, float posX, float posY,
      int rank, long affixes, int championType, int uniqueId) {
    if (createCallback == null) {
      log.warn("No create callback set");
      return -1;
    }

    return createCallback.onMonsterCreate(monsterId, posX, posY, 
        rank, affixes, championType, uniqueId);
  }

  //==========================================================================
  // 配置方法
  //==========================================================================

  public void setCreateCallback(MonsterCreateCallback callback) {
    this.createCallback = callback;
  }

  public void setDifficulty(int difficulty) {
    this.difficulty = Math.max(0, Math.min(2, difficulty));
  }

  public int getDifficulty() {
    return difficulty;
  }
}
