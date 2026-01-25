package com.riiablo.engine.server.item;

import com.badlogic.gdx.math.MathUtils;

import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 物品工具类 - 基于 D2MOO Items.cpp 移植
 * 
 * <p>提供物品相关的计算和工具方法：
 * <ul>
 *   <li>物品掉落计算</li>
 *   <li>品质判定</li>
 *   <li>耐久度计算</li>
 *   <li>物品等级计算</li>
 * </ul>
 * 
 * <p>参考：D2MOO/source/D2Game/src/ITEMS/Items.cpp
 * 
 * @author riiablo team
 */
public final class ItemUtil {
  private static final Logger log = LogManager.getLogger(ItemUtil.class);

  private ItemUtil() {} // 不可实例化

  //==========================================================================
  // 物品等级计算
  //==========================================================================

  /**
   * 计算物品等级
   * 
   * <p>物品等级影响可以出现的词缀和属性范围
   * 
   * @param monsterLevel 怪物等级
   * @param areaLevel 区域等级
   * @return 物品等级
   */
  public static int calculateItemLevel(int monsterLevel, int areaLevel) {
    // 物品等级 = max(怪物等级, 区域等级)
    return Math.max(monsterLevel, areaLevel);
  }

  /**
   * 根据怪物类型调整物品等级
   * 
   * @param baseLevel 基础等级
   * @param isChampion 是否是冠军怪物
   * @param isUnique 是否是唯一怪物
   * @param isSuperUnique 是否是超级唯一怪物
   * @return 调整后的物品等级
   */
  public static int adjustItemLevel(int baseLevel, boolean isChampion, boolean isUnique, boolean isSuperUnique) {
    int level = baseLevel;
    
    // 冠军怪物 +2
    if (isChampion) {
      level += 2;
    }
    
    // 唯一怪物 +3
    if (isUnique) {
      level += 3;
    }
    
    // 超级唯一怪物 +3
    if (isSuperUnique) {
      level += 3;
    }
    
    return Math.min(level, 99); // 最大99级
  }

  //==========================================================================
  // 品质判定
  //==========================================================================

  /**
   * 掷骰子判定物品品质
   * 
   * <p>基于 D2MOO ITEMS_RollItemQuality 函数
   * 
   * @param magicFind 魔法装备加成百分比
   * @param itemLevel 物品等级
   * @return 物品品质
   */
  public static int rollItemQuality(int magicFind, int itemLevel) {
    // 有效 MF = MF * 250 / (MF + 250)（收益递减）
    int effectiveMF = magicFind * 250 / (magicFind + 250);
    
    int roll = MathUtils.random(0, 999);
    
    // 暗金判定（约 1/400 基础概率）
    int uniqueChance = 400 - effectiveMF / 10;
    if (roll < 1000 / Math.max(uniqueChance, 1)) {
      return ItemQuality.UNIQUE;
    }
    
    // 套装判定（约 1/200 基础概率）
    int setChance = 200 - effectiveMF / 10;
    if (roll < 1000 / Math.max(setChance, 1) + 1000 / Math.max(uniqueChance, 1)) {
      return ItemQuality.SET;
    }
    
    // 稀有判定（约 1/100 基础概率）
    int rareChance = 100 - effectiveMF / 5;
    if (roll < 1000 / Math.max(rareChance, 1) + 1000 / Math.max(setChance, 1) + 1000 / Math.max(uniqueChance, 1)) {
      return ItemQuality.RARE;
    }
    
    // 魔法判定（约 1/10 基础概率）
    int magicChance = 10 - effectiveMF / 25;
    if (roll < 1000 / Math.max(magicChance, 1) + 1000 / Math.max(rareChance, 1) + 1000 / Math.max(setChance, 1) + 1000 / Math.max(uniqueChance, 1)) {
      return ItemQuality.MAGIC;
    }
    
    // 超强判定（约 1/20 概率）
    if (MathUtils.random(0, 19) == 0) {
      return ItemQuality.SUPERIOR;
    }
    
    // 劣质判定（约 1/50 概率）
    if (MathUtils.random(0, 49) == 0) {
      return ItemQuality.INFERIOR;
    }
    
    return ItemQuality.NORMAL;
  }

  //==========================================================================
  // 耐久度计算
  //==========================================================================

  /**
   * 计算物品初始耐久度
   * 
   * @param baseMaxDurability 基础最大耐久度
   * @param quality 物品品质
   * @return 初始耐久度
   */
  public static int calculateInitialDurability(int baseMaxDurability, int quality) {
    if (baseMaxDurability <= 0) {
      return 0;
    }
    
    int maxDur = baseMaxDurability;
    
    // 超强物品增加 10-15% 耐久度
    if (quality == ItemQuality.SUPERIOR) {
      int bonus = MathUtils.random(10, 15);
      maxDur = maxDur * (100 + bonus) / 100;
    }
    
    // 劣质物品减少 20-30% 耐久度
    if (quality == ItemQuality.INFERIOR) {
      int penalty = MathUtils.random(20, 30);
      maxDur = maxDur * (100 - penalty) / 100;
    }
    
    return Math.max(maxDur, 1);
  }

  /**
   * 计算耐久度损耗
   * 
   * <p>每次被击中或攻击时调用
   * 
   * @param currentDurability 当前耐久度
   * @param isEthereal 是否是幻化物品
   * @return 新的耐久度
   */
  public static int degradeDurability(int currentDurability, boolean isEthereal) {
    if (currentDurability <= 0) {
      return 0;
    }
    
    // 幻化物品无法修复
    // 普通物品有 1/10 概率损耗 1 点
    if (MathUtils.random(0, 9) == 0) {
      return currentDurability - 1;
    }
    
    return currentDurability;
  }

  //==========================================================================
  // 物品掉落相关
  //==========================================================================

  /**
   * 计算物品在地面上的存在时间
   * 
   * @param quality 物品品质
   * @param isGold 是否是金币
   * @return 存在时间（秒）
   */
  public static float getGroundDuration(int quality, boolean isGold) {
    if (isGold) {
      return 60.0f; // 金币 60 秒
    }
    
    switch (quality) {
      case ItemQuality.UNIQUE:
      case ItemQuality.SET:
        return 300.0f; // 暗金/套装 5 分钟
      case ItemQuality.RARE:
        return 180.0f; // 稀有 3 分钟
      case ItemQuality.MAGIC:
        return 120.0f; // 魔法 2 分钟
      default:
        return 60.0f; // 其他 1 分钟
    }
  }

  /**
   * 计算掉落位置偏移（避免重叠）
   * 
   * @param dropIndex 掉落索引（同时掉落多个物品时）
   * @return X 轴偏移
   */
  public static float getDropOffsetX(int dropIndex) {
    // 使用螺旋模式分布
    int ring = (int) Math.sqrt(dropIndex);
    int pos = dropIndex - ring * ring;
    return MathUtils.cosDeg(pos * 45) * ring * 0.5f;
  }

  /**
   * 计算掉落位置偏移（避免重叠）
   * 
   * @param dropIndex 掉落索引
   * @return Y 轴偏移
   */
  public static float getDropOffsetY(int dropIndex) {
    int ring = (int) Math.sqrt(dropIndex);
    int pos = dropIndex - ring * ring;
    return MathUtils.sinDeg(pos * 45) * ring * 0.5f;
  }

  //==========================================================================
  // 插槽相关
  //==========================================================================

  /**
   * 计算物品可以有的最大插槽数
   * 
   * @param itemLevel 物品等级
   * @param baseMaxSockets 基础最大插槽
   * @return 最大插槽数
   */
  public static int calculateMaxSockets(int itemLevel, int baseMaxSockets) {
    if (baseMaxSockets <= 0) {
      return 0;
    }
    
    // 物品等级限制插槽数
    int levelLimit;
    if (itemLevel < 25) {
      levelLimit = Math.max(1, itemLevel / 10);
    } else if (itemLevel < 40) {
      levelLimit = 4;
    } else {
      levelLimit = 6;
    }
    
    return Math.min(baseMaxSockets, levelLimit);
  }

  /**
   * 随机生成插槽数
   * 
   * @param maxSockets 最大插槽数
   * @return 随机插槽数
   */
  public static int rollSocketCount(int maxSockets) {
    if (maxSockets <= 0) {
      return 0;
    }
    return MathUtils.random(1, maxSockets);
  }

  //==========================================================================
  // 价值计算
  //==========================================================================

  /**
   * 计算物品出售价格
   * 
   * @param baseValue 基础价值
   * @param quality 物品品质
   * @param isIdentified 是否已鉴定
   * @return 出售价格
   */
  public static int calculateSellPrice(int baseValue, int quality, boolean isIdentified) {
    if (baseValue <= 0) {
      return 1;
    }
    
    int value = baseValue;
    
    // 品质加成
    switch (quality) {
      case ItemQuality.INFERIOR:
        value = value / 2;
        break;
      case ItemQuality.SUPERIOR:
        value = value * 2;
        break;
      case ItemQuality.MAGIC:
        value = value * 3;
        break;
      case ItemQuality.RARE:
        value = value * 5;
        break;
      case ItemQuality.SET:
      case ItemQuality.UNIQUE:
        value = value * 10;
        break;
    }
    
    // 未鉴定物品只卖基础价格
    if (!isIdentified && quality >= ItemQuality.MAGIC) {
      value = baseValue;
    }
    
    // 出售价格是购买价格的 1/4
    return Math.max(value / 4, 1);
  }

  //==========================================================================
  // 调试信息
  //==========================================================================

  /**
   * 生成物品信息字符串（调试用）
   * 
   * @param itemCode 物品代码
   * @param quality 品质
   * @param itemLevel 物品等级
   * @return 信息字符串
   */
  public static String formatItemInfo(String itemCode, int quality, int itemLevel) {
    return String.format("Item[%s, %s, iLvl=%d]", 
        itemCode, ItemQuality.getName(quality), itemLevel);
  }
}
