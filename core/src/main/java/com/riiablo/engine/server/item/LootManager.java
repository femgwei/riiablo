package com.riiablo.engine.server.item;

import java.util.List;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;

import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.ItemEntry;
import com.riiablo.item.TreasureClassResolver;
import com.riiablo.item.NativeItemQualityResolver;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 掉落物品管理器 - 基于 D2MOD Items.cpp 移植
 * 
 * <p>管理怪物死亡时的物品掉落逻辑，包括：
 * <ul>
 *   <li>物品品质判定（普通/魔法/稀有/套装/暗金）</li>
 *   <li>MF（寻找魔法物品）影响</li>
 *   <li>GF（获取更多金钱）影响</li>
 *   <li>金币掉落数量</li>
 *   <li>物品数量判定</li>
 * </ul>
 * 
 * <p>参考：D2MOD/source/D2Game/src/ITEMS/Items.cpp
 * 
 * @author riiablo team
 */
public class LootManager {
  private static final Logger log = LogManager.getLogger(LootManager.class);

  //==========================================================================
  // 常量 - 物品掉落基础几率
  //==========================================================================

  /** 暗金物品基础分母（越大越难掉落） */
  public static final int BASE_UNIQUE_DIVISOR = 400;

  /** 套装物品基础分母 */
  public static final int BASE_SET_DIVISOR = 160;

  /** 稀有物品基础分母 */
  public static final int BASE_RARE_DIVISOR = 100;

  /** 魔法物品基础分母 */
  public static final int BASE_MAGIC_DIVISOR = 34;

  /** 超强物品几率（百分比） */
  public static final int SUPERIOR_CHANCE = 5;

  /** 劣质物品几率（百分比） */
  public static final int INFERIOR_CHANCE = 2;

  /** 物品基础掉落几率（怪物等级每级增加） */
  public static final float DROP_CHANCE_PER_LEVEL = 0.01f;

  /** 精英怪物掉落倍数 */
  public static final float ELITE_DROP_MULTIPLIER = 2.0f;

  /** BOSS 掉落倍数 */
  public static final float BOSS_DROP_MULTIPLIER = 5.0f;

  //==========================================================================
  // 常量 - 金币掉落
  //==========================================================================

  /** 金币掉落基础数量 */
  public static final int BASE_GOLD_MIN = 1;
  public static final int BASE_GOLD_MAX = 10;

  /** 金币掉落等级倍数 */
  public static final float GOLD_LEVEL_MULTIPLIER = 0.5f;

  //==========================================================================
  // 常量 - MF 收益递减
  //==========================================================================

  /** MF 影响暗金物品的效率因子 */
  public static final float MF_UNIQUE_FACTOR = 250f / 100f;

  /** MF 影响套装物品的效率因子 */
  public static final float MF_SET_FACTOR = 500f / 100f;

  /** MF 影响稀有物品的效率因子 */
  public static final float MF_RARE_FACTOR = 600f / 100f;

  //==========================================================================
  // 内部类 - 掉落结果
  //==========================================================================

  /**
   * 掉落结果数据
   */
  public static class LootResult {
    /** 物品代码列表 */
    public final Array<String> itemCodes = new Array<>();

    /** 物品品质列表 */
    public final Array<Integer> itemQualities = new Array<>();

    /** 物品等级列表 */
    public final Array<Integer> itemLevels = new Array<>();

    /** 掉落的金币数量 */
    public int goldAmount = 0;

    /** 重置结果 */
    public void reset() {
      itemCodes.clear();
      itemQualities.clear();
      itemLevels.clear();
      goldAmount = 0;
    }

    /** 添加物品 */
    public void addItem(String code, int quality, int itemLevel) {
      itemCodes.add(code);
      itemQualities.add(quality);
      itemLevels.add(itemLevel);
    }

    /** 获取物品数量 */
    public int getItemCount() {
      return itemCodes.size;
    }
  }

  /**
   * 掉落配置
   */
  public static class LootConfig {
    /** 怪物等级 */
    public int monsterLevel = 1;

    /** 区域等级 */
    public int areaLevel = 1;

    /** 难度（0=普通，1=噩梦，2=地狱） */
    public int difficulty = 0;

    /** 是否是精英怪（冠军/暗金） */
    public boolean isElite = false;

    /** 是否是 BOSS */
    public boolean isBoss = false;

    /** 是否是超级暗金怪 */
    public boolean isSuperUnique = false;

    /** 怪物类型/种类（用于宝藏类查询） */
    public int monsterType = 0;

    /** 击杀者的 MF（寻找魔法物品） */
    public int magicFind = 0;

    /** 击杀者的 GF（获取更多金钱） */
    public int goldFind = 0;

    /** 玩家数量（影响掉落） */
    public int playerCount = 1;

    /** Same-level party count used by the native NoDrop exponent. */
    public int partyMembersInLevel = 1;

    /** Native MonStats/SuperUniques TreasureClass name, when available. */
    public String treasureClass;

    /** 重置配置 */
    public void reset() {
      monsterLevel = 1;
      areaLevel = 1;
      difficulty = 0;
      isElite = false;
      isBoss = false;
      isSuperUnique = false;
      monsterType = 0;
      magicFind = 0;
      goldFind = 0;
      playerCount = 1;
      partyMembersInLevel = 1;
      treasureClass = null;
    }
  }

  //==========================================================================
  // 回调接口
  //==========================================================================

  /**
   * 物品创建回调
   */
  public interface LootCreateCallback {
    /**
     * 创建物品实体
     * 
     * @param itemCode 物品代码
     * @param quality 物品品质
     * @param itemLevel 物品等级
     * @param posX 位置 X
     * @param posY 位置 Y
     * @return 创建的物品实体 ID
     */
    int onItemCreate(String itemCode, int quality, int itemLevel, float posX, float posY);

    /**
     * 创建金币实体
     * 
     * @param amount 金币数量
     * @param posX 位置 X
     * @param posY 位置 Y
     * @return 创建的金币实体 ID
     */
    int onGoldCreate(int amount, float posX, float posY);
  }

  //==========================================================================
  // 字段
  //==========================================================================

  /** 物品创建回调 */
  private LootCreateCallback createCallback;

  /** 掉落结果缓存（避免频繁创建对象） */
  private final LootResult cachedResult = new LootResult();

  /** 掉落配置缓存 */
  private final LootConfig cachedConfig = new LootConfig();

  //==========================================================================
  // 构造函数
  //==========================================================================

  public LootManager() {}

  //==========================================================================
  // 核心方法
  //==========================================================================

  /**
   * 计算怪物死亡掉落
   * 
   * <p>参考 D2MOD ITEMS_DropItems
   * 
   * @param config 掉落配置
   * @return 掉落结果
   */
  public LootResult calculateLoot(LootConfig config) {
    cachedResult.reset();

    if (config == null) return cachedResult;

    // Monster drops use the native TC graph when a class is available. The
    // legacy procedural table remains a compatibility fallback for custom
    // monsters or test environments without Excel data.
    if (calculateTreasureClassDrop(config, cachedResult)) {
      log.debug("Calculated native TC loot: tc={}, items={}, gold={}",
          config.treasureClass, cachedResult.getItemCount(), cachedResult.goldAmount);
      return cachedResult;
    }

    // 计算金币掉落
    calculateGoldDrop(config, cachedResult);

    // 计算物品掉落数量
    int dropCount = calculateDropCount(config);

    // 为每个掉落位生成物品
    for (int i = 0; i < dropCount; i++) {
      generateDrop(config, cachedResult);
    }

    log.debug("Calculated loot: items={}, gold={}", 
        cachedResult.getItemCount(), cachedResult.goldAmount);

    return cachedResult;
  }

  private boolean calculateTreasureClassDrop(LootConfig config, LootResult result) {
    if (config.treasureClass == null || config.treasureClass.trim().isEmpty()
        || Riiablo.files == null || Riiablo.files.TreasureClassEx == null) return false;
    if (Riiablo.files.TreasureClassEx.index(config.treasureClass) < 0
        && Riiablo.files.TreasureClassEx.get(config.treasureClass) == null) return false;
    int itemLevel = calculateItemLevel(config);
    TreasureClassResolver.PlayerContext players = new TreasureClassResolver.PlayerContext(
        config.playerCount, config.partyMembersInLevel);
    List<TreasureClassResolver.Drop> drops;
    try {
      TreasureClassResolver resolver = new TreasureClassResolver(Riiablo.files.TreasureClassEx);
      drops = resolver.resolve(config.treasureClass, itemLevel,
          bound -> MathUtils.random(bound - 1), TreasureClassResolver.NATIVE_MAX_DROPS, players);
    } catch (RuntimeException ex) {
      log.warn("[LOOT_TC] failed to resolve {}: {}", config.treasureClass, ex.toString());
      return false;
    }
    if (drops.isEmpty()) return true;
    for (TreasureClassResolver.Drop drop : drops) {
      String token = TreasureClassResolver.baseToken(drop.token);
      if (token == null || token.isEmpty()) continue;
      if ("gld".equalsIgnoreCase(token)) {
        result.goldAmount += nativeGoldAmount(itemLevel, config, drop.token);
        continue;
      }
      String code = token;
      int quality = forcedTreasureQuality(token);
      if (quality == ItemQuality.NORMAL) {
        ItemEntry base = findBase(token);
        if (base == null) {
          log.debug("[LOOT_TC] unresolved leaf {}, skipping", drop.token);
          continue;
        }
        quality = rollTreasureQuality(drop, base, itemLevel, config);
      } else {
        if (quality == ItemQuality.UNIQUE) {
          com.riiablo.codec.excel.UniqueItems.Entry unique = Riiablo.files.UniqueItems.get(token);
          code = unique.code;
        } else if (quality == ItemQuality.SET) {
          com.riiablo.codec.excel.SetItems.Entry set = Riiablo.files.SetItems.get(token);
          code = set._item != null && !set._item.isEmpty() ? set._item : set.item;
        }
      }
      if (findBase(code) != null) result.addItem(code, quality, itemLevel);
    }
    return true;
  }

  private int forcedTreasureQuality(String token) {
    if (Riiablo.files.UniqueItems != null && Riiablo.files.UniqueItems.get(token) != null)
      return ItemQuality.UNIQUE;
    if (Riiablo.files.SetItems != null && Riiablo.files.SetItems.get(token) != null)
      return ItemQuality.SET;
    return ItemQuality.NORMAL;
  }

  private ItemEntry findBase(String code) {
    if (code == null || code.isEmpty()) return null;
    ItemEntry entry = Riiablo.files.armor.get(code);
    if (entry != null) return entry;
    entry = Riiablo.files.weapons.get(code);
    if (entry != null) return entry;
    return Riiablo.files.misc.get(code);
  }

  /** Applies ItemRatio, MF diminishing returns, and inherited TC modifiers. */
  private int rollTreasureQuality(TreasureClassResolver.Drop drop, ItemEntry base, int itemLevel,
                                  LootConfig config) {
    com.riiablo.codec.excel.ItemTypes.Entry type =
        base == null ? null : Riiablo.files.ItemTypes.get(base.type);
    com.riiablo.codec.excel.ItemRatio.Entry ratio = Riiablo.files.ItemRatio == null
        ? null : Riiablo.files.ItemRatio.get(base, type, 100);
    if (ratio == null) return rollItemQuality(config, itemLevel);
    return NativeItemQualityResolver.roll(ratio, base, type, itemLevel, config.magicFind,
        drop.Unique, drop.Set, drop.Rare, drop.Magic, drop.Superior, drop.Normal,
        bound -> MathUtils.random(bound - 1));
  }

  private int nativeGoldAmount(int itemLevel, LootConfig config, String token) {
    int min = Math.max(1, itemLevel / 2 + 1);
    int max = Math.max(min, itemLevel * 2 + 5);
    int amount = MathUtils.random(min, max);
    if (config.isBoss) amount *= 3;
    else if (config.isElite) amount *= 2;
    if (config.goldFind > 0) amount = amount * (100 + config.goldFind) / 100;
    int multiplier = com.riiablo.engine.server.object.NativeObjectDropAdapter.multiplier(token);
    return Math.max(1, (int) (((long) amount * multiplier) / 256L));
  }

  /**
   * 执行掉落（创建实体）
   * 
   * @param result 掉落结果
   * @param posX 掉落位置 X
   * @param posY 掉落位置 Y
   * @return 创建的实体 ID 列表
   */
  public Array<Integer> executeLoot(LootResult result, float posX, float posY) {
    Array<Integer> entities = new Array<>();

    if (createCallback == null) {
      log.warn("No create callback set");
      return entities;
    }

    // 创建金币
    if (result.goldAmount > 0) {
      // 金币散落到周围
      float goldX = posX + MathUtils.random(-1f, 1f);
      float goldY = posY + MathUtils.random(-1f, 1f);
      int entityId = createCallback.onGoldCreate(result.goldAmount, goldX, goldY);
      if (entityId >= 0) {
        entities.add(entityId);
      }
    }

    // 创建物品
    for (int i = 0; i < result.getItemCount(); i++) {
      String code = result.itemCodes.get(i);
      int quality = result.itemQualities.get(i);
      int itemLevel = result.itemLevels.get(i);

      // 物品散落到周围
      float itemX = posX + MathUtils.random(-2f, 2f);
      float itemY = posY + MathUtils.random(-2f, 2f);

      int entityId = createCallback.onItemCreate(code, quality, itemLevel, itemX, itemY);
      if (entityId >= 0) {
        entities.add(entityId);
      }
    }

    log.debug("Created {} loot entities at ({},{})", entities.size, posX, posY);

    return entities;
  }

  /**
   * 便捷方法：计算并执行掉落
   */
  public Array<Integer> dropLoot(LootConfig config, float posX, float posY) {
    LootResult result = calculateLoot(config);
    return executeLoot(result, posX, posY);
  }

  /**
   * 从玩家属性获取 MF 和 GF
   */
  public void applyPlayerBonuses(Attributes playerAttrs, LootConfig config) {
    if (playerAttrs == null) {
      return;
    }

    // 获取 MF（寻找魔法物品）
    config.magicFind = getStatValue(playerAttrs, Stat.item_magicbonus, 0);

    // 获取 GF（获取更多金钱）
    config.goldFind = getStatValue(playerAttrs, Stat.item_goldbonus, 0);

    log.debug("Applied player bonuses: MF={}, GF={}", config.magicFind, config.goldFind);
  }

  //==========================================================================
  // 金币掉落
  //==========================================================================

  /**
   * 计算金币掉落
   * 
   * <p>参考 D2MOD ITEMS_DropGold
   */
  private void calculateGoldDrop(LootConfig config, LootResult result) {
    // 金币掉落几率（大部分怪物都掉金币）
    if (MathUtils.random(99) < 70) {
      // 基础金币数量
      int minGold = BASE_GOLD_MIN + (int) (config.monsterLevel * GOLD_LEVEL_MULTIPLIER);
      int maxGold = BASE_GOLD_MAX + (int) (config.monsterLevel * GOLD_LEVEL_MULTIPLIER * 2);

      int goldAmount = MathUtils.random(minGold, maxGold);

      // 难度加成
      switch (config.difficulty) {
        case 1: // 噩梦
          goldAmount = (int) (goldAmount * 2.5f);
          break;
        case 2: // 地狱
          goldAmount = (int) (goldAmount * 5.0f);
          break;
      }

      // 精英/BOSS 加成
      if (config.isBoss) {
        goldAmount *= 3;
      } else if (config.isElite) {
        goldAmount *= 2;
      }

      // 玩家数量加成
      if (config.playerCount > 1) {
        goldAmount = (int) (goldAmount * (1 + 0.1f * (config.playerCount - 1)));
      }

      // GF（获取更多金钱）加成
      if (config.goldFind > 0) {
        goldAmount = goldAmount * (100 + config.goldFind) / 100;
      }

      result.goldAmount = goldAmount;
    }
  }

  //==========================================================================
  // 物品掉落
  //==========================================================================

  /**
   * 计算掉落数量
   */
  private int calculateDropCount(LootConfig config) {
    int baseCount = 0;

    // BOSS 掉落 4-6 件物品
    if (config.isBoss) {
      baseCount = MathUtils.random(4, 6);
    }
    // 超级暗金怪掉落 3-5 件
    else if (config.isSuperUnique) {
      baseCount = MathUtils.random(3, 5);
    }
    // 精英怪掉落 1-3 件
    else if (config.isElite) {
      baseCount = MathUtils.random(1, 3);
    }
    // 普通怪物
    else {
      // 普通怪物有 30% 几率掉落物品
      if (MathUtils.random(99) < 30) {
        baseCount = 1;
        // 10% 几率额外掉落一件
        if (MathUtils.random(99) < 10) {
          baseCount++;
        }
      }
    }

    // 玩家数量影响
    if (config.playerCount > 1 && baseCount > 0) {
      // 每多一个玩家增加 10% 掉落几率
      if (MathUtils.random(99) < 10 * (config.playerCount - 1)) {
        baseCount++;
      }
    }

    return baseCount;
  }

  /**
   * 生成单个掉落物品
   */
  private void generateDrop(LootConfig config, LootResult result) {
    // 确定物品等级
    int itemLevel = calculateItemLevel(config);

    // 确定物品品质
    int quality = rollItemQuality(config, itemLevel);

    // 根据品质选择物品代码（简化版本 - 实际应从 TreasureClass 查询）
    String itemCode = selectItemCode(config, quality, itemLevel);

    if (itemCode != null) {
      result.addItem(itemCode, quality, itemLevel);
    }
  }

  /**
   * 计算物品等级
   */
  private int calculateItemLevel(LootConfig config) {
    // 物品等级基于怪物等级和区域等级
    int baseLevel = Math.max(config.monsterLevel, config.areaLevel);

    // 精英怪物品等级 +3
    if (config.isElite) {
      baseLevel += 3;
    }

    // BOSS 物品等级更高
    if (config.isBoss) {
      baseLevel += 5;
    }

    // 限制在 1-99 范围
    return Math.max(1, Math.min(99, baseLevel));
  }

  /**
   * 判定物品品质
   * 
   * <p>参考 D2MOD 的品质判定逻辑：
   * <ol>
   *   <li>先判定暗金</li>
   *   <li>再判定套装</li>
   *   <li>再判定稀有</li>
   *   <li>再判定魔法</li>
   *   <li>否则判定普通/超强/劣质</li>
   * </ol>
   */
  private int rollItemQuality(LootConfig config, int itemLevel) {
    // 计算有效 MF（应用收益递减）
    int effectiveMF = config.magicFind;

    // 尝试暗金
    int uniqueChance = calculateUniqueChance(itemLevel, effectiveMF, config);
    if (MathUtils.random(999) < uniqueChance) {
      log.debug("Rolled UNIQUE quality: chance={}‰", uniqueChance);
      return ItemQuality.UNIQUE;
    }

    // 尝试套装
    int setChance = calculateSetChance(itemLevel, effectiveMF, config);
    if (MathUtils.random(999) < setChance) {
      log.debug("Rolled SET quality: chance={}‰", setChance);
      return ItemQuality.SET;
    }

    // 尝试稀有
    int rareChance = calculateRareChance(itemLevel, effectiveMF, config);
    if (MathUtils.random(999) < rareChance) {
      log.debug("Rolled RARE quality: chance={}‰", rareChance);
      return ItemQuality.RARE;
    }

    // 尝试魔法
    int magicChance = calculateMagicChance(itemLevel, effectiveMF, config);
    if (MathUtils.random(999) < magicChance) {
      log.debug("Rolled MAGIC quality: chance={}‰", magicChance);
      return ItemQuality.MAGIC;
    }

    // 普通品质判定
    return rollNormalQuality();
  }

  /**
   * 计算暗金物品几率
   * 
   * <p>暗金的 MF 效率公式：effectiveMF = MF * 250 / (MF + 250)
   */
  private int calculateUniqueChance(int itemLevel, int magicFind, LootConfig config) {
    // 基础几率
    int baseChance = 1000 / BASE_UNIQUE_DIVISOR; // 2.5‰

    // MF 加成（使用收益递减公式）
    int effectiveMF = calculateEffectiveMF(magicFind, MF_UNIQUE_FACTOR);
    baseChance = baseChance * (100 + effectiveMF) / 100;

    // 精英/BOSS 加成
    if (config.isBoss) {
      baseChance *= 3;
    } else if (config.isElite) {
      baseChance = (int) (baseChance * 1.5f);
    }

    // 物品等级影响
    baseChance += itemLevel / 10;

    return Math.min(baseChance, 50); // 最高 5%
  }

  /**
   * 计算套装物品几率
   * 
   * <p>套装的 MF 效率公式：effectiveMF = MF * 500 / (MF + 500)
   */
  private int calculateSetChance(int itemLevel, int magicFind, LootConfig config) {
    int baseChance = 1000 / BASE_SET_DIVISOR; // 6.25‰

    int effectiveMF = calculateEffectiveMF(magicFind, MF_SET_FACTOR);
    baseChance = baseChance * (100 + effectiveMF) / 100;

    if (config.isBoss) {
      baseChance *= 2;
    } else if (config.isElite) {
      baseChance = (int) (baseChance * 1.3f);
    }

    baseChance += itemLevel / 8;

    return Math.min(baseChance, 80); // 最高 8%
  }

  /**
   * 计算稀有物品几率
   * 
   * <p>稀有的 MF 效率公式：effectiveMF = MF * 600 / (MF + 600)
   */
  private int calculateRareChance(int itemLevel, int magicFind, LootConfig config) {
    int baseChance = 1000 / BASE_RARE_DIVISOR; // 10‰

    int effectiveMF = calculateEffectiveMF(magicFind, MF_RARE_FACTOR);
    baseChance = baseChance * (100 + effectiveMF) / 100;

    if (config.isBoss) {
      baseChance = (int) (baseChance * 1.5f);
    } else if (config.isElite) {
      baseChance = (int) (baseChance * 1.2f);
    }

    baseChance += itemLevel / 5;

    return Math.min(baseChance, 150); // 最高 15%
  }

  /**
   * 计算魔法物品几率
   */
  private int calculateMagicChance(int itemLevel, int magicFind, LootConfig config) {
    int baseChance = 1000 / BASE_MAGIC_DIVISOR; // 29.4‰

    // 魔法物品 MF 直接生效（无收益递减）
    baseChance = baseChance * (100 + magicFind) / 100;

    if (config.isElite || config.isBoss) {
      baseChance = (int) (baseChance * 1.2f);
    }

    baseChance += itemLevel / 3;

    return Math.min(baseChance, 400); // 最高 40%
  }

  /**
   * 计算有效 MF（应用收益递减）
   * 
   * <p>公式：effectiveMF = MF * factor / (MF + factor)
   * 其中 factor 取决于物品类型
   */
  private int calculateEffectiveMF(int magicFind, float factor) {
    if (magicFind <= 0) {
      return 0;
    }
    return (int) (magicFind * factor / (magicFind + factor * 100));
  }

  /**
   * 判定普通品质（劣质/普通/超强）
   */
  private int rollNormalQuality() {
    int roll = MathUtils.random(99);

    if (roll < INFERIOR_CHANCE) {
      return ItemQuality.INFERIOR;
    } else if (roll < INFERIOR_CHANCE + SUPERIOR_CHANCE) {
      return ItemQuality.SUPERIOR;
    } else {
      return ItemQuality.NORMAL;
    }
  }

  /**
   * 选择物品代码
   * 
   * <p>简化版本 - 实际应该从 TreasureClass 系统查询
   */
  private String selectItemCode(LootConfig config, int quality, int itemLevel) {
    // 先判断是否掉落宝石或符文
    int specialRoll = MathUtils.random(999);

    // 宝石掉落几率（约 5%）
    if (specialRoll < 50) {
      return selectGem(itemLevel, config.difficulty);
    }

    // 符文掉落几率（约 3%，高级符文更稀有）
    if (specialRoll < 80) {
      return selectRune(itemLevel, config.difficulty);
    }

    // 根据物品等级范围返回不同物品
    if (itemLevel >= 80) {
      return selectHighLevelItem(quality);
    } else if (itemLevel >= 50) {
      return selectMidLevelItem(quality);
    } else {
      return selectLowLevelItem(quality);
    }
  }

  /**
   * 选择掉落的宝石
   * 
   * <p>宝石等级与怪物等级和难度相关：
   * <ul>
   *   <li>碎裂宝石：怪物等级 1-15</li>
   *   <li>裂开宝石：怪物等级 10-30</li>
   *   <li>普通宝石：怪物等级 25-50</li>
   *   <li>无瑕宝石：怪物等级 40-70</li>
   *   <li>完美宝石：怪物等级 60+</li>
   * </ul>
   */
  private String selectGem(int itemLevel, int difficulty) {
    // 宝石颜色
    String[] gemColors = {"r", "g", "b", "w", "y", "v"}; // 红绿蓝白黄紫
    String color = gemColors[MathUtils.random(gemColors.length - 1)];

    // 根据等级选择宝石品质
    String prefix = selectGemPrefix(itemLevel, difficulty);

    return "g" + prefix + color;
  }

  /**
   * 选择宝石品质前缀
   */
  private String selectGemPrefix(int itemLevel, int difficulty) {
    // 调整后的掉落等级（难度增加掉落更好宝石的几率）
    int adjustedLevel = itemLevel + difficulty * 15;

    // 使用加权随机
    int roll = MathUtils.random(99);

    if (adjustedLevel >= 70) {
      // 高等级：完美>无瑕>普通
      if (roll < 15) return "p"; // 15% 完美
      if (roll < 45) return "l"; // 30% 无瑕
      if (roll < 75) return "s"; // 30% 普通
      if (roll < 90) return "f"; // 15% 裂开
      return "c"; // 10% 碎裂
    } else if (adjustedLevel >= 50) {
      // 中高等级：无瑕>普通
      if (roll < 5) return "p"; // 5% 完美
      if (roll < 25) return "l"; // 20% 无瑕
      if (roll < 60) return "s"; // 35% 普通
      if (roll < 85) return "f"; // 25% 裂开
      return "c"; // 15% 碎裂
    } else if (adjustedLevel >= 30) {
      // 中等级：普通>裂开
      if (roll < 10) return "l"; // 10% 无瑕
      if (roll < 40) return "s"; // 30% 普通
      if (roll < 75) return "f"; // 35% 裂开
      return "c"; // 25% 碎裂
    } else if (adjustedLevel >= 15) {
      // 低等级：裂开>碎裂
      if (roll < 5) return "s"; // 5% 普通
      if (roll < 35) return "f"; // 30% 裂开
      return "c"; // 65% 碎裂
    } else {
      // 极低等级：几乎都是碎裂
      if (roll < 10) return "f"; // 10% 裂开
      return "c"; // 90% 碎裂
    }
  }

  /**
   * 选择掉落的符文
   * 
   * <p>符文掉落率遵循暗黑2原版规则：
   * <ul>
   *   <li>低级符文（El-Thul, r01-r10）：相对常见</li>
   *   <li>中级符文（Amn-Fal, r11-r19）：较少见</li>
   *   <li>高级符文（Lem-Zod, r20-r33）：非常稀有</li>
   * </ul>
   * 
   * <p>高级符文的掉落几率约为 1:50000 到 1:5000000
   */
  private String selectRune(int itemLevel, int difficulty) {
    // 调整后的等级
    int adjustedLevel = itemLevel + difficulty * 10;

    // 最大可掉落符文编号
    int maxRune;
    if (adjustedLevel >= 85) {
      maxRune = 33; // 可以掉落到 Zod
    } else if (adjustedLevel >= 70) {
      maxRune = 28; // 可以掉落到 Lo
    } else if (adjustedLevel >= 55) {
      maxRune = 24; // 可以掉落到 Ist
    } else if (adjustedLevel >= 40) {
      maxRune = 19; // 可以掉落到 Fal
    } else if (adjustedLevel >= 25) {
      maxRune = 14; // 可以掉落到 Dol
    } else {
      maxRune = 10; // 只能掉落到 Thul
    }

    // 使用加权随机 - 低级符文更常见
    int runeNum = rollRuneNumber(maxRune);

    return String.format("r%02d", runeNum);
  }

  /**
   * 掷骰符文编号
   * 
   * <p>使用指数分布，低级符文更常见
   */
  private int rollRuneNumber(int maxRune) {
    // 每升一级，稀有度约翻倍
    // 使用多次随机来模拟指数分布

    int rune = 1;
    int attempts = 0;
    int maxAttempts = 20;

    while (rune < maxRune && attempts < maxAttempts) {
      // 约 50% 几率升级到下一个符文
      if (MathUtils.random(99) < 40) {
        rune++;
      } else {
        break;
      }
      attempts++;
    }

    // 高级符文额外稀有度检查
    if (rune >= 20) {
      // Lem 以上的符文需要额外检查
      int extraCheck = (rune - 19) * 15; // 每级多 15% 失败率
      if (MathUtils.random(99) < extraCheck) {
        rune = Math.max(1, rune - MathUtils.random(1, 5));
      }
    }

    return Math.min(rune, maxRune);
  }

  private String selectHighLevelItem(int quality) {
    String[] items = {"amu", "rin", "jew", "cm1", "cm2", "cm3"};
    return items[MathUtils.random(items.length - 1)];
  }

  private String selectMidLevelItem(int quality) {
    String[] items = {"cap", "skp", "hlm", "ltp", "buc", "lrg"};
    return items[MathUtils.random(items.length - 1)];
  }

  private String selectLowLevelItem(int quality) {
    String[] items = {"cap", "qui", "lea", "buc", "sst", "clb"};
    return items[MathUtils.random(items.length - 1)];
  }

  //==========================================================================
  // 宝石/符文检测辅助
  //==========================================================================

  /**
   * 检查物品代码是否是宝石
   */
  public boolean isGemCode(String code) {
    if (code == null || code.length() != 3) {
      return false;
    }

    char prefix = code.charAt(0);
    char type = code.charAt(1);

    if (prefix == 'g') {
      return (type == 'c' || type == 'f' || type == 's' || type == 'l' || type == 'p');
    }

    // 骷髅
    if (code.startsWith("sk")) {
      char level = code.charAt(2);
      return level == 'c' || level == 'f' || level == 'u' || level == 'l' || level == 'z';
    }

    return false;
  }

  /**
   * 检查物品代码是否是符文
   */
  public boolean isRuneCode(String code) {
    if (code == null || code.length() != 3 || !code.startsWith("r")) {
      return false;
    }

    try {
      int runeNum = Integer.parseInt(code.substring(1));
      return runeNum >= 1 && runeNum <= 33;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /**
   * 获取符文编号（1-33）
   */
  public int getRuneNumber(String code) {
    if (!isRuneCode(code)) {
      return -1;
    }
    return Integer.parseInt(code.substring(1));
  }

  /**
   * 获取符文名称
   */
  public String getRuneName(String code) {
    int num = getRuneNumber(code);
    if (num < 1 || num > 33) {
      return null;
    }

    String[] names = {
        null, "El", "Eld", "Tir", "Nef", "Eth", "Ith", "Tal", "Ral", "Ort", "Thul",
        "Amn", "Sol", "Shael", "Dol", "Hel", "Io", "Lum", "Ko", "Fal", "Lem",
        "Pul", "Um", "Mal", "Ist", "Gul", "Vex", "Ohm", "Lo", "Sur", "Ber",
        "Jah", "Cham", "Zod"
    };

    return names[num];
  }

  //==========================================================================
  // 辅助方法
  //==========================================================================

  private int getStatValue(Attributes attrs, short statId, int defaultValue) {
    try {
      return attrs.get(statId).asInt();
    } catch (Exception e) {
      return defaultValue;
    }
  }

  //==========================================================================
  // 配置方法
  //==========================================================================

  public void setCreateCallback(LootCreateCallback callback) {
    this.createCallback = callback;
  }

  /**
   * 获取缓存的配置对象（用于重复使用）
   */
  public LootConfig obtainConfig() {
    cachedConfig.reset();
    return cachedConfig;
  }
}
