package com.riiablo.engine.server.item;

import com.badlogic.gdx.math.MathUtils;

import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.save.ItemData;

/**
 * 物品耐久度管理器 - 基于 D2MOO 移植
 * 
 * <p>管理物品耐久度的消耗和修理：
 * <ul>
 *   <li>攻击时武器耐久度消耗</li>
 *   <li>被攻击时护甲耐久度消耗</li>
 *   <li>物品修理费用计算</li>
 *   <li>无形物品处理</li>
 *   <li>不破坏物品处理</li>
 * </ul>
 * 
 * <p>参考：D2MOO/source/D2Game/src/UNIT/SUnitDmg.cpp - SUNITDMG_DrainItemDurability
 * 
 * @author riiablo team
 */
public class ItemDurabilityManager {
  private static final Logger log = LogManager.getLogger(ItemDurabilityManager.class);

  /** 单例实例 */
  public static final ItemDurabilityManager INSTANCE = new ItemDurabilityManager();

  private ItemDurabilityManager() {}

  //==========================================================================
  // 耐久度消耗权重（身体部位）
  //==========================================================================

  /** 耐久度消耗权重结构 */
  private static class DurabilityLossWeight {
    final BodyLoc bodyLoc;
    final int weight;
    
    DurabilityLossWeight(BodyLoc bodyLoc, int weight) {
      this.bodyLoc = bodyLoc;
      this.weight = weight;
    }
  }

  /** 
   * 护甲部位耐久度消耗权重
   * 参考 D2MOO sgDurabilityLossWeights
   */
  private static final DurabilityLossWeight[] ARMOR_DURABILITY_WEIGHTS = {
    new DurabilityLossWeight(BodyLoc.HEAD, 3),   // 头盔
    new DurabilityLossWeight(BodyLoc.TORS, 5),   // 胸甲（权重最高）
    new DurabilityLossWeight(BodyLoc.RARM, 4),   // 右手
    new DurabilityLossWeight(BodyLoc.LARM, 4),   // 左手/盾牌
    new DurabilityLossWeight(BodyLoc.BELT, 2),   // 腰带
    new DurabilityLossWeight(BodyLoc.FEET, 2),   // 鞋子
    new DurabilityLossWeight(BodyLoc.GLOV, 2),   // 手套
  };

  //==========================================================================
  // 耐久度消耗常量
  //==========================================================================

  /** 近战攻击消耗耐久度的概率（1/X） */
  public static final int MELEE_DURABILITY_CHANCE = 10;
  
  /** 远程攻击消耗耐久度的概率（1/X） */
  public static final int RANGED_DURABILITY_CHANCE = 20;
  
  /** 被击中时消耗护甲耐久度的概率（1/X） */
  public static final int ARMOR_DURABILITY_CHANCE = 8;

  //==========================================================================
  // 核心方法
  //==========================================================================

  /**
   * 消耗武器耐久度（攻击时调用）
   * 
   * <p>参考 D2MOO ITEMS_UpdateDurability
   * 
   * @param item 武器物品
   * @param isMelee 是否为近战攻击
   * @return true 如果物品损坏（耐久度为0）
   */
  public boolean drainWeaponDurability(Item item, boolean isMelee) {
    if (item == null) {
      return false;
    }

    // 检查是否是不可破坏的物品
    if (isIndestructible(item)) {
      return false;
    }

    // 概率检查
    int chance = isMelee ? MELEE_DURABILITY_CHANCE : RANGED_DURABILITY_CHANCE;
    if (MathUtils.random(chance - 1) != 0) {
      return false;
    }

    return drainDurability(item, 1);
  }

  /**
   * 消耗护甲耐久度（被攻击时调用）
   * 
   * <p>参考 D2MOO SUNITDMG_DrainItemDurability
   * 
   * @param itemData 玩家物品数据
   * @return 损坏的物品（如果有）
   */
  public Item drainArmorDurability(ItemData itemData) {
    if (itemData == null) {
      return null;
    }

    // 概率检查
    if (MathUtils.random(ARMOR_DURABILITY_CHANCE - 1) != 0) {
      return null;
    }

    // 收集所有装备的护甲及其权重
    Item[] armorItems = new Item[ARMOR_DURABILITY_WEIGHTS.length];
    int totalWeight = 0;
    
    for (int i = 0; i < ARMOR_DURABILITY_WEIGHTS.length; i++) {
      DurabilityLossWeight slot = ARMOR_DURABILITY_WEIGHTS[i];
      Item item = itemData.getEquipped(slot.bodyLoc);
      
      if (item != null && isArmor(item) && !isIndestructible(item)) {
        armorItems[i] = item;
        totalWeight += slot.weight;
      }
    }

    if (totalWeight <= 0) {
      return null;
    }

    // 根据权重随机选择一件护甲消耗耐久度
    int randomWeight = MathUtils.random(totalWeight - 1);
    int index = MathUtils.random(ARMOR_DURABILITY_WEIGHTS.length - 1);
    
    for (int i = 0; i < ARMOR_DURABILITY_WEIGHTS.length; i++) {
      int currentIndex = (index + i) % ARMOR_DURABILITY_WEIGHTS.length;
      if (armorItems[currentIndex] != null) {
        if (randomWeight < ARMOR_DURABILITY_WEIGHTS[currentIndex].weight) {
          if (drainDurability(armorItems[currentIndex], 1)) {
            return armorItems[currentIndex]; // 物品损坏
          }
          return null;
        }
        randomWeight -= ARMOR_DURABILITY_WEIGHTS[currentIndex].weight;
      }
    }

    return null;
  }

  /**
   * 消耗物品耐久度
   * 
   * @param item 物品
   * @param amount 消耗量
   * @return true 如果物品损坏（耐久度降为0）
   */
  public boolean drainDurability(Item item, int amount) {
    if (item == null || amount <= 0) {
      return false;
    }

    // 检查是否不可破坏
    if (isIndestructible(item)) {
      return false;
    }

    // 获取当前耐久度
    int currentDur = getCurrentDurability(item);
    if (currentDur <= 0) {
      return true; // 已经损坏
    }

    // 消耗耐久度
    int newDur = Math.max(0, currentDur - amount);
    setCurrentDurability(item, newDur);

    log.debug("Item {} durability: {} -> {}", item.getNameString(), currentDur, newDur);

    if (newDur <= 0) {
      onItemBroken(item);
      return true;
    }

    return false;
  }

  //==========================================================================
  // 修理系统
  //==========================================================================

  /**
   * 计算物品修理费用
   * 
   * <p>修理费用公式：
   * <pre>
   * 费用 = 基础修理费 * (最大耐久度 - 当前耐久度) * 等级系数
   * </pre>
   * 
   * @param item 物品
   * @return 修理费用（金币）
   */
  public int calculateRepairCost(Item item) {
    if (item == null) {
      return 0;
    }

    // 不可破坏物品无需修理
    if (isIndestructible(item)) {
      return 0;
    }

    int currentDur = getCurrentDurability(item);
    int maxDur = getMaxDurability(item);
    
    // 已经满耐久度
    if (currentDur >= maxDur) {
      return 0;
    }

    // 计算损坏程度
    int damageLost = maxDur - currentDur;

    // 获取基础修理费（从物品表）
    int baseCost = getBaseRepairCost(item);

    // 计算总费用
    int totalCost = baseCost * damageLost;

    // 魔法物品修理费更高
    if (item.quality != null && item.quality.ordinal() > 1) {
      totalCost = totalCost * (item.quality.ordinal() + 1) / 2;
    }

    return Math.max(1, totalCost);
  }

  /**
   * 修理物品
   * 
   * @param item 物品
   * @param goldAvailable 可用金币
   * @return 消耗的金币（0表示无需修理或金币不足）
   */
  public int repairItem(Item item, int goldAvailable) {
    if (item == null) {
      return 0;
    }

    int cost = calculateRepairCost(item);
    if (cost <= 0) {
      return 0; // 无需修理
    }

    if (goldAvailable < cost) {
      return 0; // 金币不足
    }

    // 恢复满耐久度
    int maxDur = getMaxDurability(item);
    setCurrentDurability(item, maxDur);

    log.debug("Repaired item {} for {} gold", item.getNameString(), cost);

    return cost;
  }

  /**
   * 修理所有装备
   * 
   * @param itemData 物品数据
   * @param goldAvailable 可用金币
   * @return 总消耗金币
   */
  public int repairAllEquipment(ItemData itemData, int goldAvailable) {
    if (itemData == null) {
      return 0;
    }

    int totalCost = 0;
    int remainingGold = goldAvailable;

    // 遍历所有装备槽位
    for (BodyLoc loc : BodyLoc.values()) {
      Item item = itemData.getEquipped(loc);
      if (item != null) {
        int cost = repairItem(item, remainingGold);
        totalCost += cost;
        remainingGold -= cost;
        
        if (remainingGold <= 0) {
          break;
        }
      }
    }

    return totalCost;
  }

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 检查物品是否不可破坏
   */
  private boolean isIndestructible(Item item) {
    // 检查物品标志
    if (item.base != null && item.base.nodurability) {
      return true;
    }
    
    // 检查属性：不可破坏
    // TODO: 检查 STAT_ITEM_INDESTRUCTIBLE
    
    // 无形物品也可以认为是一种特殊情况
    // if (isEthereal(item)) return false; // 无形物品会损坏
    
    return false;
  }

  /**
   * 检查物品是否是护甲类型
   */
  private boolean isArmor(Item item) {
    if (item == null || item.type == null) {
      return false;
    }
    return item.type.is(com.riiablo.item.Type.ARMO);
  }

  /**
   * 获取当前耐久度
   */
  private int getCurrentDurability(Item item) {
    if (item == null || item.attrs == null) {
      return 0;
    }
    StatRef ref = item.attrs.get(Stat.durability);
    return ref != null ? ref.asInt() : 0;
  }

  /**
   * 设置当前耐久度
   */
  private void setCurrentDurability(Item item, int value) {
    if (item == null || item.attrs == null) {
      return;
    }
    item.attrs.base().put(Stat.durability, value);
  }

  /**
   * 获取最大耐久度
   */
  private int getMaxDurability(Item item) {
    if (item == null || item.attrs == null) {
      return 0;
    }
    StatRef ref = item.attrs.get(Stat.maxdurability);
    return ref != null ? ref.asInt() : 0;
  }

  /**
   * 获取基础修理费用
   */
  private int getBaseRepairCost(Item item) {
    // TODO: 从物品表读取实际修理费用
    // 暂时使用简化值
    if (item == null) {
      return 1;
    }
    
    // 根据物品等级估算基础修理费
    int itemLevel = item.attrs != null ? 
        (item.attrs.get(Stat.level) != null ? item.attrs.get(Stat.level).asInt() : 1) : 1;
    
    return Math.max(1, itemLevel / 2);
  }

  /**
   * 物品损坏时的处理
   */
  private void onItemBroken(Item item) {
    if (item == null) {
      return;
    }

    log.info("Item {} is broken!", item.getNameString());

    // 无形物品损坏后消失
    if (isEthereal(item)) {
      // TODO: 从装备槽移除物品
      log.info("Ethereal item {} destroyed", item.getNameString());
    }

    // TODO: 播放物品损坏音效
    // TODO: 显示物品损坏提示
  }

  /**
   * 检查物品是否是无形的
   */
  private boolean isEthereal(Item item) {
    // TODO: 检查物品的无形标志
    return false;
  }

  //==========================================================================
  // 查询方法
  //==========================================================================

  /**
   * 检查物品是否需要修理
   */
  public boolean needsRepair(Item item) {
    if (item == null || isIndestructible(item)) {
      return false;
    }
    return getCurrentDurability(item) < getMaxDurability(item);
  }

  /**
   * 获取物品耐久度百分比
   */
  public float getDurabilityPercent(Item item) {
    if (item == null) {
      return 0;
    }
    int max = getMaxDurability(item);
    if (max <= 0) {
      return 1.0f; // 无耐久度物品视为100%
    }
    return (float) getCurrentDurability(item) / max;
  }

  /**
   * 检查物品是否损坏（耐久度为0）
   */
  public boolean isBroken(Item item) {
    if (item == null || isIndestructible(item)) {
      return false;
    }
    return getCurrentDurability(item) <= 0;
  }
}
