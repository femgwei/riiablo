package com.riiablo.engine.server.pet;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;

import com.riiablo.engine.Engine;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 雇佣兵管理器 - 基于 D2MOD PlayerPets.cpp 和 SUnitNpc.cpp 移植
 * 
 * <p>管理雇佣兵的完整生命周期：
 * <ul>
 *   <li>雇佣兵招募和解雇</li>
 *   <li>雇佣兵属性和装备</li>
 *   <li>雇佣兵复活</li>
 *   <li>雇佣兵 AI 行为</li>
 *   <li>雇佣兵经验和等级</li>
 * </ul>
 * 
 * <p>参考：
 * <ul>
 *   <li>D2MOD/source/D2Game/src/PLAYER/PlayerPets.cpp</li>
 *   <li>D2MOD/source/D2Game/src/UNIT/SUnitNpc.cpp</li>
 * </ul>
 * 
 * @author riiablo team
 */
public class MercenaryManager {
  private static final Logger log = LogManager.getLogger(MercenaryManager.class);

  //==========================================================================
  // 常量 - 雇佣兵类型
  //==========================================================================

  /** 第一幕：弓箭手（Rogue Scout） */
  public static final int MERC_TYPE_ROGUE = 0;

  /** 第二幕：沙漠佣兵（Desert Mercenary） */
  public static final int MERC_TYPE_DESERT = 1;

  /** 第三幕：铁狼（Iron Wolf） */
  public static final int MERC_TYPE_IRON_WOLF = 2;

  /** 第五幕：野蛮人（Barbarian） */
  public static final int MERC_TYPE_BARBARIAN = 3;

  //==========================================================================
  // 常量 - 雇佣兵状态
  //==========================================================================

  /** 待命（未被雇佣） */
  public static final int STATE_AVAILABLE = 0;

  /** 已被雇佣 */
  public static final int STATE_HIRED = 1;

  /** 已死亡 */
  public static final int STATE_DEAD = 2;

  /** D2 unit/save flag persisted for a dead hireling. */
  public static final int FLAG_DEAD = 0x00010000;

  //==========================================================================
  // 常量 - 雇佣兵 NPC
  //==========================================================================

  /** 卡夏（第一幕雇佣兵 NPC） */
  public static final int NPC_KASHYA = 150;

  /** 格瑞兹（第二幕雇佣兵 NPC） */
  public static final int NPC_GREIZ = 198;

  /** 阿舍拉（第三幕雇佣兵 NPC） */
  public static final int NPC_ASHEARA = 199;

  /** 夸坎克（第五幕雇佣兵 NPC） */
  public static final int NPC_QUAL_KEHK = 511;

  //==========================================================================
  // 内部类
  //==========================================================================

  /**
   * 雇佣兵定义
   */
  public static class MercenaryDefinition {
    /** 雇佣兵类型 */
    public int mercType;

    /** 名字 ID */
    public int nameId;

    /** 技能列表 */
    public int[] skills;

    /** 基础属性 */
    public int baseStrength;
    public int baseDexterity;
    public int baseLife;
    public int baseDefense;
    public int baseAttackRating;
    public int baseDamageMin;
    public int baseDamageMax;

    /** 每级增长 */
    public int strengthPerLevel;
    public int dexterityPerLevel;
    public int lifePerLevel;
    public int defensePerLevel;
    public int attackRatingPerLevel;
    public int damageMinPerLevel;
    public int damageMaxPerLevel;

    /** 抗性（火/冰/电/毒） */
    public int[] baseResistances = new int[4];

    /** 雇佣花费系数 */
    public int hireCostMultiplier;

    /** 复活花费系数 */
    public int resurrectCostMultiplier;
  }

  /**
   * 可用雇佣兵（NPC 展示列表）
   */
  public static class AvailableMercenary {
    /** 雇佣兵定义 */
    public MercenaryDefinition definition;

    /** 随机种子（用于外观变化） */
    public int seed;

    /** 名字 ID */
    public int nameId;

    /** 是否已被雇佣 */
    public boolean hired;

    /** 等级 */
    public int level;
  }

  /**
   * 活跃雇佣兵（玩家已雇佣的） 
   */
  public static class ActiveMercenary {
    /** 所属玩家 ID */
    public int ownerId;

    /** 雇佣兵实体 ID */
    public int entityId;

    /** 雇佣兵定义 */
    public MercenaryDefinition definition;

    /** 名字 ID */
    public int nameId;

    /** 随机种子 */
    public int seed;

    /** 当前状态 */
    public int state;

    /** 等级 */
    public int level;

    /** 经验值 */
    public long experience;

    /** 当前生命值 */
    public int currentLife;

    /** 最大生命值 */
    public int maxLife;

    /** 装备槽（头盔、护甲、武器） */
    public int[] equipment = new int[3];

    /** 雇佣时间 */
    public long hireTime;
  }

  /**
   * NPC 雇佣兵列表
   */
  public static class NpcMercenaryList {
    /** NPC 类型 ID */
    public int npcId;

    /** 雇佣兵类型 */
    public int mercType;

    /** 可用雇佣兵列表 */
    public Array<AvailableMercenary> available = new Array<>();

    /** 上次刷新时间 */
    public long lastRefreshTime;
  }

  //==========================================================================
  // 回调接口
  //==========================================================================

  /**
   * 雇佣兵事件回调
   */
  public interface MercenaryCallback {
    /**
     * 雇佣兵被雇佣
     */
    void onMercenaryHired(int playerId, ActiveMercenary merc);

    /**
     * 雇佣兵被解雇
     */
    void onMercenaryDismissed(int playerId, ActiveMercenary merc);

    /**
     * 雇佣兵死亡
     */
    void onMercenaryDeath(int playerId, ActiveMercenary merc);

    /**
     * 雇佣兵复活
     */
    void onMercenaryResurrected(int playerId, ActiveMercenary merc);

    /**
     * 雇佣兵升级
     */
    void onMercenaryLevelUp(int playerId, ActiveMercenary merc, int oldLevel, int newLevel);

    /**
     * 创建雇佣兵实体
     */
    int createMercenaryEntity(int playerId, MercenaryDefinition def, int level, int seed, int nameId);

    /**
     * 移除雇佣兵实体
     */
    void removeMercenaryEntity(int entityId);

    /** Restores the existing dead hireling entity in place. */
    boolean resurrectMercenaryEntity(int entityId, int playerId);

    /**
     * 获取玩家金币
     */
    int getPlayerGold(int playerId);

    /**
     * 扣除玩家金币
     */
    boolean deductPlayerGold(int playerId, int amount);

    /**
     * 获取玩家等级
     */
    int getPlayerLevel(int playerId);

    /**
     * 获取游戏难度
     */
    int getDifficulty();
  }

  //==========================================================================
  // 字段
  //==========================================================================

  /** 雇佣兵定义表（类型 -> 定义列表） */
  private final IntMap<Array<MercenaryDefinition>> mercDefinitions = new IntMap<>();

  /** NPC 雇佣兵列表（NPC ID -> 列表） */
  private final IntMap<NpcMercenaryList> npcLists = new IntMap<>();

  /** 玩家雇佣兵（玩家 ID -> 雇佣兵） */
  private final IntMap<ActiveMercenary> playerMercs = new IntMap<>();

  /** 经验表 */
  private final long[] expTable = new long[99];

  /** 回调 */
  private MercenaryCallback callback;

  /** 雇佣兵列表刷新间隔（毫秒） */
  private static final long REFRESH_INTERVAL = 600000; // 10 分钟

  //==========================================================================
  // 构造函数
  //==========================================================================

  public MercenaryManager() {
    initExpTable();
    registerDefaultMercenaries();
  }

  //==========================================================================
  // 核心方法 - 雇佣兵招募
  //==========================================================================

  /**
   * 获取 NPC 的雇佣兵列表
   * 
   * @param npcId NPC 类型 ID
   * @param playerLevel 玩家等级
   * @return 可用雇佣兵列表
   */
  public Array<AvailableMercenary> getAvailableMercenaries(int npcId, int playerLevel) {
    NpcMercenaryList list = npcLists.get(npcId);
    if (list == null) {
      list = initNpcMercenaryList(npcId);
      if (list == null) {
        return new Array<>();
      }
    }

    // 检查是否需要刷新
    long now = System.currentTimeMillis();
    if (now - list.lastRefreshTime > REFRESH_INTERVAL) {
      refreshMercenaryList(list, playerLevel);
    }

    return list.available;
  }

  /**
   * 雇佣一个雇佣兵
   * 
   * @param playerId 玩家 ID
   * @param npcId NPC ID
   * @param mercIndex 雇佣兵索引
   * @return true 如果成功雇佣
   */
  public boolean hireMercenary(int playerId, int npcId, int mercIndex) {
    // 检查玩家是否已有雇佣兵
    if (playerMercs.containsKey(playerId)) {
      log.debug("Player {} already has a mercenary", playerId);
      return false;
    }

    // 获取雇佣兵列表
    NpcMercenaryList list = npcLists.get(npcId);
    if (list == null || mercIndex < 0 || mercIndex >= list.available.size) {
      log.debug("Invalid mercenary index {} for NPC {}", mercIndex, npcId);
      return false;
    }

    AvailableMercenary available = list.available.get(mercIndex);
    if (available.hired) {
      log.debug("Mercenary {} already hired", mercIndex);
      return false;
    }

    // 计算雇佣费用
    int cost = calculateHireCost(available.definition, available.level);
    if (callback != null && callback.getPlayerGold(playerId) < cost) {
      log.debug("Player {} cannot afford mercenary (cost: {})", playerId, cost);
      return false;
    }

    // 创建活跃雇佣兵
    ActiveMercenary merc = new ActiveMercenary();
    merc.ownerId = playerId;
    merc.definition = available.definition;
    merc.nameId = available.nameId;
    merc.seed = available.seed;
    merc.level = available.level;
    merc.experience = getExpForLevel(available.level);
    merc.state = STATE_HIRED;
    merc.hireTime = System.currentTimeMillis();

    // 计算属性
    merc.maxLife = calculateMercLife(available.definition, available.level);
    merc.currentLife = merc.maxLife;

    // 创建实体
    if (callback != null) {
      merc.entityId = callback.createMercenaryEntity(playerId, available.definition, 
          available.level, available.seed, available.nameId);
      if (merc.entityId == Engine.INVALID_ENTITY) {
        log.warn("Mercenary entity creation failed before payment: player={}", playerId);
        return false;
      }
      if (!callback.deductPlayerGold(playerId, cost)) {
        callback.removeMercenaryEntity(merc.entityId);
        log.warn("Mercenary payment failed after entity reservation: player={}", playerId);
        return false;
      }
    }

    playerMercs.put(playerId, merc);
    available.hired = true;

    log.debug("Player {} hired mercenary (type: {}, level: {})", 
        playerId, available.definition.mercType, available.level);

    if (callback != null) {
      callback.onMercenaryHired(playerId, merc);
    }

    return true;
  }

  /**
   * Grants Kashya's Blood Raven reward without charging the player.
   *
   * <p>The reward is transactional: a logical mercenary is recorded only
   * after the owning runtime has created a real entity. This prevents the
   * quest from acknowledging the reward when entity creation is unavailable
   * or fails.
   *
   * @param playerId owner entity id
   * @param playerLevel owner level used to build Kashya's available list
   * @return {@code true} only when the Rogue entity and manager record exist
   */
  public boolean grantFreeRogue(int playerId, int playerLevel) {
    if (playerMercs.containsKey(playerId)) {
      log.debug("Player {} already has a mercenary; free Rogue not consumed", playerId);
      return false;
    }
    if (callback == null) {
      log.warn("Cannot grant free Rogue without an entity callback: player={}", playerId);
      return false;
    }

    Array<AvailableMercenary> available =
        getAvailableMercenaries(NPC_KASHYA, Math.max(1, playerLevel));
    AvailableMercenary rogue = firstAvailable(available, MERC_TYPE_ROGUE);
    if (rogue == null) {
      // Native NPC mercenary pools are replenished after their available
      // names are exhausted. Do the same here so multiplayer quest rewards
      // cannot become permanently blocked by earlier players.
      NpcMercenaryList kashya = npcLists.get(NPC_KASHYA);
      refreshMercenaryList(kashya, Math.max(1, playerLevel));
      available = kashya.available;
      rogue = firstAvailable(available, MERC_TYPE_ROGUE);
    }
    if (rogue == null) {
      log.warn("Kashya has no available Rogue for quest reward: player={}", playerId);
      return false;
    }

    int entityId = callback.createMercenaryEntity(playerId, rogue.definition,
        rogue.level, rogue.seed, rogue.nameId);
    if (entityId == Engine.INVALID_ENTITY) {
      log.warn("Free Rogue entity creation failed; reward remains pending: player={}", playerId);
      return false;
    }

    ActiveMercenary merc = createActiveMercenary(playerId, rogue, entityId);
    playerMercs.put(playerId, merc);
    rogue.hired = true;
    callback.onMercenaryHired(playerId, merc);
    log.info("Granted free Rogue: player={} entity={} level={} name={}",
        playerId, entityId, merc.level, merc.nameId);
    return true;
  }

  /**
   * Reconstructs the runtime record represented by a D2S mercenary header.
   *
   * <p>This deliberately does not invoke {@link MercenaryCallback#onMercenaryHired};
   * loading an existing hireling must not rewrite its persistent identity or
   * charge the player. The caller remains responsible for applying persisted
   * equipment attributes and the native dead-unit presentation after this
   * transaction succeeds.</p>
   */
  public boolean restoreMercenary(int playerId, int mercType, int level,
      long experience, int seed, int nameId, boolean dead) {
    if (playerMercs.containsKey(playerId) || callback == null || seed == 0) return false;
    MercenaryDefinition definition = definitionForSavedMercenary(mercType, nameId);
    if (definition == null) {
      log.warn("Cannot restore unknown hireling type: player={} type={}", playerId, mercType);
      return false;
    }

    int safeLevel = Math.max(1, Math.min(98, level));
    int entityId = callback.createMercenaryEntity(
        playerId, definition, safeLevel, seed, nameId);
    if (entityId == Engine.INVALID_ENTITY) {
      log.warn("Persisted mercenary entity creation failed: player={} type={} level={}",
          playerId, mercType, safeLevel);
      return false;
    }

    ActiveMercenary merc = new ActiveMercenary();
    merc.ownerId = playerId;
    merc.entityId = entityId;
    merc.definition = definition;
    merc.nameId = nameId;
    merc.seed = seed;
    merc.level = safeLevel;
    merc.experience = Math.max(0L, Math.min(0xFFFFFFFFL, experience));
    merc.state = dead ? STATE_DEAD : STATE_HIRED;
    merc.hireTime = System.currentTimeMillis();
    merc.maxLife = calculateMercLife(definition, safeLevel);
    merc.currentLife = dead ? 0 : merc.maxLife;
    playerMercs.put(playerId, merc);
    log.info("Restored persisted mercenary: player={} entity={} type={} level={} dead={}",
        playerId, entityId, mercType, safeLevel, dead);
    return true;
  }

  /** Removes only the runtime pet record during logout; persistent data is retained. */
  public void unloadMercenary(int playerId) {
    ActiveMercenary merc = playerMercs.remove(playerId);
    if (merc != null && callback != null) callback.removeMercenaryEntity(merc.entityId);
  }

  private MercenaryDefinition definitionForSavedMercenary(int mercType, int nameId) {
    Array<MercenaryDefinition> definitions = mercDefinitions.get(mercType);
    if (definitions == null || definitions.size == 0) return null;
    MercenaryDefinition selected = definitions.first();
    for (MercenaryDefinition candidate : definitions) {
      if (candidate.nameId <= nameId && candidate.nameId >= selected.nameId) {
        selected = candidate;
      }
    }
    return selected;
  }

  private ActiveMercenary createActiveMercenary(int playerId,
      AvailableMercenary available, int entityId) {
    ActiveMercenary merc = new ActiveMercenary();
    merc.ownerId = playerId;
    merc.entityId = entityId;
    merc.definition = available.definition;
    merc.nameId = available.nameId;
    merc.seed = available.seed;
    merc.level = available.level;
    merc.experience = getExpForLevel(available.level);
    merc.state = STATE_HIRED;
    merc.hireTime = System.currentTimeMillis();
    merc.maxLife = calculateMercLife(available.definition, available.level);
    merc.currentLife = merc.maxLife;
    return merc;
  }

  private static AvailableMercenary firstAvailable(
      Array<AvailableMercenary> available, int mercType) {
    for (AvailableMercenary candidate : available) {
      if (!candidate.hired && candidate.definition != null
          && candidate.definition.mercType == mercType) {
        return candidate;
      }
    }
    return null;
  }

  /**
   * 解雇雇佣兵
   * 
   * @param playerId 玩家 ID
   */
  public void dismissMercenary(int playerId) {
    ActiveMercenary merc = playerMercs.remove(playerId);
    if (merc == null) {
      return;
    }

    if (callback != null) {
      callback.removeMercenaryEntity(merc.entityId);
      callback.onMercenaryDismissed(playerId, merc);
    }

    log.debug("Player {} dismissed mercenary", playerId);
  }

  //==========================================================================
  // 核心方法 - 雇佣兵复活
  //==========================================================================

  /**
   * 复活雇佣兵
   * 
   * @param playerId 玩家 ID
   * @return true 如果成功复活
   */
  public boolean resurrectMercenary(int playerId) {
    ActiveMercenary merc = playerMercs.get(playerId);
    if (merc == null) {
      log.debug("Player {} has no mercenary to resurrect", playerId);
      return false;
    }

    if (merc.state != STATE_DEAD) {
      log.debug("Mercenary is not dead");
      return false;
    }

    // D2Common MONSTERS_GetHirelingResurrectionCost uses only hireling level.
    int cost = calculateResurrectCost(merc.definition, merc.level);
    if (callback == null || callback.getPlayerGold(playerId) < cost) {
      log.debug("Player {} cannot afford resurrection (cost: {})", playerId, cost);
      return false;
    }

    // Native D2 keeps the dead hireling entity/pet record and revives it in
    // place. Validate that transition before charging; gold mutation is then
    // deterministic on the same authoritative server thread.
    if (!callback.resurrectMercenaryEntity(merc.entityId, playerId)) {
      log.warn("Player {} mercenary entity could not be resurrected: entity={}",
          playerId, merc.entityId);
      return false;
    }
    if (!callback.deductPlayerGold(playerId, cost)) {
      // getPlayerGold and deduction execute on one server thread, so this is
      // an invariant failure rather than an ordinary insufficient-gold path.
      log.error("Player {} resurrection charge failed after affordability check: cost={}",
          playerId, cost);
      return false;
    }

    merc.state = STATE_HIRED;
    merc.currentLife = merc.maxLife;
    callback.onMercenaryResurrected(playerId, merc);

    log.debug("Player {} resurrected mercenary", playerId);

    return true;
  }

  /**
   * 雇佣兵死亡
   * 
   * @param playerId 玩家 ID
   */
  public void onMercenaryDeath(int playerId) {
    ActiveMercenary merc = playerMercs.get(playerId);
    if (merc == null || merc.state == STATE_DEAD) {
      return;
    }

    merc.state = STATE_DEAD;
    merc.currentLife = 0;

    log.debug("Mercenary of player {} died", playerId);

    if (callback != null) {
      callback.onMercenaryDeath(playerId, merc);
    }
  }

  //==========================================================================
  // 核心方法 - 经验和升级
  //==========================================================================

  /**
   * 给雇佣兵增加经验值
   * 
   * @param playerId 玩家 ID
   * @param exp 经验值
   */
  public void addExperience(int playerId, long exp) {
    ActiveMercenary merc = playerMercs.get(playerId);
    if (merc == null || merc.state == STATE_DEAD) {
      return;
    }

    merc.experience += exp;

    // 检查升级
    while (merc.level < 98) {
      long expNeeded = getExpForLevel(merc.level + 1);
      if (merc.experience >= expNeeded) {
        int oldLevel = merc.level;
        merc.level++;
        merc.maxLife = calculateMercLife(merc.definition, merc.level);
        merc.currentLife = merc.maxLife;

        log.debug("Mercenary of player {} leveled up: {} -> {}", playerId, oldLevel, merc.level);

        if (callback != null) {
          callback.onMercenaryLevelUp(playerId, merc, oldLevel, merc.level);
        }
      } else {
        break;
      }
    }
  }

  //==========================================================================
  // 属性计算
  //==========================================================================

  /**
   * 计算雇佣费用
   */
  public int calculateHireCost(MercenaryDefinition def, int level) {
    int baseCost = 100 + level * 50;
    return baseCost * def.hireCostMultiplier / 100;
  }

  /**
   * 计算复活费用
   */
  public int calculateResurrectCost(MercenaryDefinition def, int level) {
    return nativeResurrectionCost(level);
  }

  /** Mirrors D2Common #11083 MONSTERS_GetHirelingResurrectionCost. */
  public static int nativeResurrectionCost(int level) {
    long safeLevel = Math.max(0, level);
    return (int) Math.min(50_000L, 15L * safeLevel * safeLevel / 2L);
  }

  /**
   * 计算雇佣兵生命值
   */
  private int calculateMercLife(MercenaryDefinition def, int level) {
    return def.baseLife + (level - 1) * def.lifePerLevel;
  }

  /**
   * 计算雇佣兵攻击力
   */
  public int[] calculateMercDamage(MercenaryDefinition def, int level) {
    int min = def.baseDamageMin + (level - 1) * def.damageMinPerLevel;
    int max = def.baseDamageMax + (level - 1) * def.damageMaxPerLevel;
    return new int[]{min, max};
  }

  /**
   * 计算雇佣兵攻击准确率
   */
  public int calculateMercAttackRating(MercenaryDefinition def, int level) {
    return def.baseAttackRating + (level - 1) * def.attackRatingPerLevel;
  }

  /**
   * 计算雇佣兵防御
   */
  public int calculateMercDefense(MercenaryDefinition def, int level) {
    return def.baseDefense + (level - 1) * def.defensePerLevel;
  }

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 初始化 NPC 雇佣兵列表
   */
  private NpcMercenaryList initNpcMercenaryList(int npcId) {
    int mercType = getMercTypeForNpc(npcId);
    if (mercType < 0) {
      return null;
    }

    NpcMercenaryList list = new NpcMercenaryList();
    list.npcId = npcId;
    list.mercType = mercType;
    list.lastRefreshTime = 0;
    npcLists.put(npcId, list);

    return list;
  }

  /**
   * 刷新雇佣兵列表
   */
  private void refreshMercenaryList(NpcMercenaryList list, int playerLevel) {
    list.available.clear();

    Array<MercenaryDefinition> defs = mercDefinitions.get(list.mercType);
    if (defs == null) {
      return;
    }

    // 生成 3-6 个可用雇佣兵
    int count = 3 + (int)(Math.random() * 4);
    for (int i = 0; i < count; i++) {
      MercenaryDefinition def = defs.get((int)(Math.random() * defs.size));

      AvailableMercenary available = new AvailableMercenary();
      available.definition = def;
      available.seed = 1 + (int) (Math.random() * (Integer.MAX_VALUE - 1));
      available.nameId = def.nameId + (int)(Math.random() * 10);
      available.hired = false;
      available.level = Math.max(1, playerLevel - 5 + (int)(Math.random() * 10));

      list.available.add(available);
    }

    list.lastRefreshTime = System.currentTimeMillis();
    log.debug("Refreshed mercenary list for NPC {}: {} mercs available", 
        list.npcId, list.available.size);
  }

  /**
   * 获取 NPC 对应的雇佣兵类型
   */
  private int getMercTypeForNpc(int npcId) {
    switch (npcId) {
      case NPC_KASHYA: return MERC_TYPE_ROGUE;
      case NPC_GREIZ: return MERC_TYPE_DESERT;
      case NPC_ASHEARA: return MERC_TYPE_IRON_WOLF;
      case NPC_QUAL_KEHK: return MERC_TYPE_BARBARIAN;
      default: return -1;
    }
  }

  /**
   * 初始化经验表
   */
  private void initExpTable() {
    // 简化的经验表
    expTable[0] = 0;
    for (int i = 1; i < 99; i++) {
      expTable[i] = (long)(expTable[i - 1] + 100 * Math.pow(i, 2.5));
    }
  }

  /**
   * 获取等级所需经验
   */
  private long getExpForLevel(int level) {
    if (level < 1) return 0;
    if (level >= 99) return expTable[98];
    return expTable[level - 1];
  }

  /**
   * 注册默认雇佣兵
   */
  private void registerDefaultMercenaries() {
    // 第一幕弓箭手
    registerMercType(MERC_TYPE_ROGUE, createRogueMercs());

    // 第二幕沙漠佣兵
    registerMercType(MERC_TYPE_DESERT, createDesertMercs());

    // 第三幕铁狼
    registerMercType(MERC_TYPE_IRON_WOLF, createIronWolfMercs());

    // 第五幕野蛮人
    registerMercType(MERC_TYPE_BARBARIAN, createBarbarianMercs());

    log.debug("Registered default mercenary types");
  }

  private Array<MercenaryDefinition> createRogueMercs() {
    Array<MercenaryDefinition> list = new Array<>();

    // 火焰弓手
    MercenaryDefinition fire = new MercenaryDefinition();
    fire.mercType = MERC_TYPE_ROGUE;
    fire.nameId = 0;
    fire.baseStrength = 40;
    fire.baseDexterity = 52;
    fire.baseLife = 50;
    fire.baseDefense = 10;
    fire.baseAttackRating = 30;
    fire.baseDamageMin = 2;
    fire.baseDamageMax = 6;
    fire.strengthPerLevel = 1;
    fire.dexterityPerLevel = 2;
    fire.lifePerLevel = 6;
    fire.defensePerLevel = 3;
    fire.attackRatingPerLevel = 8;
    fire.damageMinPerLevel = 1;
    fire.damageMaxPerLevel = 2;
    fire.baseResistances = new int[]{30, 0, 0, 0};
    fire.hireCostMultiplier = 100;
    fire.resurrectCostMultiplier = 100;
    list.add(fire);

    // 冰霜弓手
    MercenaryDefinition cold = new MercenaryDefinition();
    cold.mercType = MERC_TYPE_ROGUE;
    cold.nameId = 10;
    cold.baseStrength = 40;
    cold.baseDexterity = 52;
    cold.baseLife = 50;
    cold.baseDefense = 10;
    cold.baseAttackRating = 30;
    cold.baseDamageMin = 2;
    cold.baseDamageMax = 6;
    cold.strengthPerLevel = 1;
    cold.dexterityPerLevel = 2;
    cold.lifePerLevel = 6;
    cold.defensePerLevel = 3;
    cold.attackRatingPerLevel = 8;
    cold.damageMinPerLevel = 1;
    cold.damageMaxPerLevel = 2;
    cold.baseResistances = new int[]{0, 30, 0, 0};
    cold.hireCostMultiplier = 100;
    cold.resurrectCostMultiplier = 100;
    list.add(cold);

    return list;
  }

  private Array<MercenaryDefinition> createDesertMercs() {
    Array<MercenaryDefinition> list = new Array<>();

    // 战斗佣兵（进攻光环）
    MercenaryDefinition combat = new MercenaryDefinition();
    combat.mercType = MERC_TYPE_DESERT;
    combat.nameId = 20;
    combat.baseStrength = 58;
    combat.baseDexterity = 45;
    combat.baseLife = 68;
    combat.baseDefense = 20;
    combat.baseAttackRating = 35;
    combat.baseDamageMin = 4;
    combat.baseDamageMax = 12;
    combat.strengthPerLevel = 2;
    combat.dexterityPerLevel = 1;
    combat.lifePerLevel = 8;
    combat.defensePerLevel = 5;
    combat.attackRatingPerLevel = 10;
    combat.damageMinPerLevel = 2;
    combat.damageMaxPerLevel = 3;
    combat.baseResistances = new int[]{15, 15, 15, 15};
    combat.hireCostMultiplier = 120;
    combat.resurrectCostMultiplier = 110;
    list.add(combat);

    // 防御佣兵（防御光环）
    MercenaryDefinition defense = new MercenaryDefinition();
    defense.mercType = MERC_TYPE_DESERT;
    defense.nameId = 30;
    defense.baseStrength = 55;
    defense.baseDexterity = 42;
    defense.baseLife = 80;
    defense.baseDefense = 30;
    defense.baseAttackRating = 30;
    defense.baseDamageMin = 3;
    defense.baseDamageMax = 10;
    defense.strengthPerLevel = 2;
    defense.dexterityPerLevel = 1;
    defense.lifePerLevel = 10;
    defense.defensePerLevel = 7;
    defense.attackRatingPerLevel = 8;
    defense.damageMinPerLevel = 1;
    defense.damageMaxPerLevel = 2;
    defense.baseResistances = new int[]{20, 20, 20, 20};
    defense.hireCostMultiplier = 110;
    defense.resurrectCostMultiplier = 100;
    list.add(defense);

    return list;
  }

  private Array<MercenaryDefinition> createIronWolfMercs() {
    Array<MercenaryDefinition> list = new Array<>();

    // 火焰铁狼
    MercenaryDefinition fire = new MercenaryDefinition();
    fire.mercType = MERC_TYPE_IRON_WOLF;
    fire.nameId = 40;
    fire.baseStrength = 35;
    fire.baseDexterity = 40;
    fire.baseLife = 45;
    fire.baseDefense = 15;
    fire.baseAttackRating = 25;
    fire.baseDamageMin = 1;
    fire.baseDamageMax = 4;
    fire.strengthPerLevel = 1;
    fire.dexterityPerLevel = 1;
    fire.lifePerLevel = 5;
    fire.defensePerLevel = 2;
    fire.attackRatingPerLevel = 5;
    fire.damageMinPerLevel = 1;
    fire.damageMaxPerLevel = 1;
    fire.baseResistances = new int[]{40, 0, 0, 0};
    fire.hireCostMultiplier = 130;
    fire.resurrectCostMultiplier = 120;
    list.add(fire);

    // 冰霜铁狼
    MercenaryDefinition cold = new MercenaryDefinition();
    cold.mercType = MERC_TYPE_IRON_WOLF;
    cold.nameId = 50;
    cold.baseStrength = 35;
    cold.baseDexterity = 40;
    cold.baseLife = 45;
    cold.baseDefense = 15;
    cold.baseAttackRating = 25;
    cold.baseDamageMin = 1;
    cold.baseDamageMax = 4;
    cold.strengthPerLevel = 1;
    cold.dexterityPerLevel = 1;
    cold.lifePerLevel = 5;
    cold.defensePerLevel = 2;
    cold.attackRatingPerLevel = 5;
    cold.damageMinPerLevel = 1;
    cold.damageMaxPerLevel = 1;
    cold.baseResistances = new int[]{0, 40, 0, 0};
    cold.hireCostMultiplier = 130;
    cold.resurrectCostMultiplier = 120;
    list.add(cold);

    // 闪电铁狼
    MercenaryDefinition lightning = new MercenaryDefinition();
    lightning.mercType = MERC_TYPE_IRON_WOLF;
    lightning.nameId = 60;
    lightning.baseStrength = 35;
    lightning.baseDexterity = 40;
    lightning.baseLife = 45;
    lightning.baseDefense = 15;
    lightning.baseAttackRating = 25;
    lightning.baseDamageMin = 1;
    lightning.baseDamageMax = 4;
    lightning.strengthPerLevel = 1;
    lightning.dexterityPerLevel = 1;
    lightning.lifePerLevel = 5;
    lightning.defensePerLevel = 2;
    lightning.attackRatingPerLevel = 5;
    lightning.damageMinPerLevel = 1;
    lightning.damageMaxPerLevel = 1;
    lightning.baseResistances = new int[]{0, 0, 40, 0};
    lightning.hireCostMultiplier = 130;
    lightning.resurrectCostMultiplier = 120;
    list.add(lightning);

    return list;
  }

  private Array<MercenaryDefinition> createBarbarianMercs() {
    Array<MercenaryDefinition> list = new Array<>();

    // 野蛮人战士
    MercenaryDefinition barb = new MercenaryDefinition();
    barb.mercType = MERC_TYPE_BARBARIAN;
    barb.nameId = 70;
    barb.baseStrength = 75;
    barb.baseDexterity = 35;
    barb.baseLife = 100;
    barb.baseDefense = 25;
    barb.baseAttackRating = 40;
    barb.baseDamageMin = 5;
    barb.baseDamageMax = 15;
    barb.strengthPerLevel = 3;
    barb.dexterityPerLevel = 1;
    barb.lifePerLevel = 12;
    barb.defensePerLevel = 5;
    barb.attackRatingPerLevel = 12;
    barb.damageMinPerLevel = 2;
    barb.damageMaxPerLevel = 4;
    barb.baseResistances = new int[]{10, 10, 10, 10};
    barb.hireCostMultiplier = 150;
    barb.resurrectCostMultiplier = 130;
    list.add(barb);

    return list;
  }

  private void registerMercType(int mercType, Array<MercenaryDefinition> defs) {
    mercDefinitions.put(mercType, defs);
  }

  //==========================================================================
  // 访问器
  //==========================================================================

  public void setCallback(MercenaryCallback callback) {
    this.callback = callback;
  }

  /**
   * 获取玩家的雇佣兵
   */
  public ActiveMercenary getPlayerMercenary(int playerId) {
    return playerMercs.get(playerId);
  }

  /**
   * 检查玩家是否有雇佣兵
   */
  public boolean hasMercenary(int playerId) {
    return playerMercs.containsKey(playerId);
  }

  /**
   * 玩家离开时清理
   */
  public void onPlayerLeave(int playerId) {
    ActiveMercenary merc = playerMercs.remove(playerId);
    if (merc != null && callback != null) {
      callback.removeMercenaryEntity(merc.entityId);
    }
  }
}
