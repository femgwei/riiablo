package com.riiablo.engine.server.combat;

import com.badlogic.gdx.math.MathUtils;

import com.riiablo.attributes.Stat;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 战斗系统 - 基于 D2MOD SUnitDmg.cpp 移植
 * 
 * <p>实现完整的暗黑破坏神 II 战斗机制：
 * <ul>
 *   <li>攻击命中率（AR）计算</li>
 *   <li>防御等级（Defense）计算</li>
 *   <li>命中判定公式</li>
 *   <li>伤害类型和减免</li>
 *   <li>格挡和闪避</li>
 *   <li>暴击（Deadly Strike / Critical Strike）</li>
 *   <li>吸血和法力偷取</li>
 * </ul>
 * 
 * <p>参考：D2MOD/source/D2Game/src/UNIT/SUnitDmg.cpp
 * 
 * @author riiablo team
 */
public class CombatSystem {
  private static final Logger log = LogManager.getLogger(CombatSystem.class);

  //==========================================================================
  // 常量 - 伤害类型
  //==========================================================================

  /** 物理伤害 */
  public static final int DAMAGE_PHYSICAL = 0;
  
  /** 火焰伤害 */
  public static final int DAMAGE_FIRE = 1;
  
  /** 闪电伤害 */
  public static final int DAMAGE_LIGHTNING = 2;
  
  /** 冰冷伤害 */
  public static final int DAMAGE_COLD = 3;
  
  /** 毒素伤害 */
  public static final int DAMAGE_POISON = 4;
  
  /** 魔法伤害 */
  public static final int DAMAGE_MAGIC = 5;

  /** 伤害类型数量 */
  public static final int DAMAGE_TYPE_COUNT = 6;

  //==========================================================================
  // 常量 - 命中公式参数
  //==========================================================================

  /** 基础命中率（5%） */
  public static final int BASE_TO_HIT_CHANCE = 5;

  /** 最低命中率（5%） */
  public static final int MIN_TO_HIT_CHANCE = 5;

  /** 最高命中率（95%） */
  public static final int MAX_TO_HIT_CHANCE = 95;

  /** PvP 命中率调整因子 */
  public static final int PVP_HIT_FACTOR = 2;

  /** 等级差异每级影响（用于命中计算） */
  public static final int LEVEL_DIFF_MODIFIER = 2;

  //==========================================================================
  // 常量 - 格挡参数
  //==========================================================================

  /** 基础格挡率 */
  public static final int BASE_BLOCK_CHANCE = 0;

  /** 最低格挡率 */
  public static final int MIN_BLOCK_CHANCE = 0;

  /** 最高格挡率 */
  public static final int MAX_BLOCK_CHANCE = 75;

  /** 格挡敏捷因子 */
  public static final int BLOCK_DEXTERITY_FACTOR = 2;

  //==========================================================================
  // 常量 - 暴击参数
  //==========================================================================

  /** 最高暴击率 */
  public static final int MAX_CRIT_CHANCE = 100;

  //==========================================================================
  // 内部类 - 战斗上下文
  //==========================================================================

  /**
   * 攻击者数据
   */
  public static class AttackerData {
    /** 实体 ID */
    public int entityId;

    /** 是否是玩家 */
    public boolean isPlayer;

    /** 等级 */
    public int level;

    /** 力量 */
    public int strength;

    /** 敏捷 */
    public int dexterity;

    /** 攻击等级（Attack Rating） */
    public int attackRating;

    /** 攻击等级百分比加成 */
    public int attackRatingPercent;

    /** 武器最小伤害 */
    public int minDamage;

    /** 武器最大伤害 */
    public int maxDamage;

    /** 增强伤害百分比 */
    public int enhancedDamagePercent;

    /** 各元素伤害（火/电/冰/毒/魔法） */
    public int[] elementalMinDamage = new int[DAMAGE_TYPE_COUNT];
    public int[] elementalMaxDamage = new int[DAMAGE_TYPE_COUNT];

    /** 对恶魔攻击等级加成 */
    public int demonToHit;

    /** 对亡灵攻击等级加成 */
    public int undeadToHit;

    /** 致命一击（Deadly Strike）几率 */
    public int deadlyStrike;

    /** 暴击（Critical Strike）几率 */
    public int criticalStrike;

    /** 压碎性打击（Crushing Blow）几率 */
    public int crushingBlow;

    /** 无视目标防御 */
    public boolean ignoreTargetDefense;

    /** 攻击是否是远程（投射物） */
    public boolean isMissile;

    /** 生命偷取百分比 */
    public int lifeLeech;

    /** 法力偷取百分比 */
    public int manaLeech;

    /** 击中后冰冻敌人时间（帧） */
    public int coldLength;

    /** 使用的技能 ID（-1 表示普通攻击） */
    public int skillId = -1;

    /** 技能等级 */
    public int skillLevel;
  }

  /**
   * 防御者数据
   */
  public static class DefenderData {
    /** 实体 ID */
    public int entityId;

    /** 是否是玩家 */
    public boolean isPlayer;

    /** 是否是怪物 */
    public boolean isMonster;

    /** 等级 */
    public int level;

    /** 敏捷 */
    public int dexterity;

    /** 防御等级（Defense） */
    public int defense;

    /** 对近战防御 */
    public int defenseVsMelee;

    /** 对远程防御 */
    public int defenseVsMissile;

    /** 生命值 */
    public int currentLife;
    public int maxLife;

    /** 格挡率 */
    public int blockChance;

    /** 是否正在格挡（持盾） */
    public boolean canBlock;

    /** 各元素抗性 */
    public int[] resistances = new int[DAMAGE_TYPE_COUNT];

    /** 物理伤害减免（Damage Reduced） */
    public int damageReduced;

    /** 物理伤害减免百分比 */
    public int damageReducedPercent;

    /** 魔法伤害减免 */
    public int magicDamageReduced;

    /** 是否是恶魔 */
    public boolean isDemon;

    /** 是否是亡灵 */
    public boolean isUndead;

    /** 是否免疫物理 */
    public boolean immunePhysical;

    /** 是否免疫各元素 */
    public boolean[] immuneElemental = new boolean[DAMAGE_TYPE_COUNT];
  }

  /**
   * 战斗结果
   */
  public static class CombatResult {
    /** 是否命中 */
    public boolean hit;

    /** 是否被格挡 */
    public boolean blocked;

    /** 是否暴击 */
    public boolean critical;

    /** 是否致命一击（双倍伤害） */
    public boolean deadlyStrike;

    /** 是否压碎性打击 */
    public boolean crushingBlow;

    /** 最终物理伤害 */
    public int physicalDamage;

    /** 各元素伤害 */
    public int[] elementalDamage = new int[DAMAGE_TYPE_COUNT];

    /** 总伤害 */
    public int totalDamage;

    /** 生命偷取数量 */
    public int lifeStolen;

    /** 法力偷取数量 */
    public int manaStolen;

    /** 实际命中率 */
    public int hitChance;

    /** 重置结果 */
    public void reset() {
      hit = false;
      blocked = false;
      critical = false;
      deadlyStrike = false;
      crushingBlow = false;
      physicalDamage = 0;
      for (int i = 0; i < DAMAGE_TYPE_COUNT; i++) {
        elementalDamage[i] = 0;
      }
      totalDamage = 0;
      lifeStolen = 0;
      manaStolen = 0;
      hitChance = 0;
    }
  }

  //==========================================================================
  // 构造函数
  //==========================================================================

  public CombatSystem() {}

  //==========================================================================
  // 核心方法 - 战斗解算
  //==========================================================================

  /**
   * 计算攻击结果
   * 
   * <p>参考 D2MOD SUNITDMG_CalculateDamage
   * 
   * @param attacker 攻击者数据
   * @param defender 防御者数据
   * @return 战斗结果
   */
  public CombatResult calculateAttack(AttackerData attacker, DefenderData defender) {
    CombatResult result = new CombatResult();

    // 1. 计算命中率并判定命中
    result.hitChance = calculateHitChance(attacker, defender);
    result.hit = rollHit(result.hitChance);

    if (!result.hit) {
      log.debug("Attack missed: hitChance={}%", result.hitChance);
      return result;
    }

    // 2. 判定格挡
    if (defender.canBlock && !attacker.isMissile) {
      int blockChance = calculateBlockChance(defender);
      result.blocked = MathUtils.random(99) < blockChance;

      if (result.blocked) {
        log.debug("Attack blocked: blockChance={}%", blockChance);
        return result;
      }
    }

    // 3. 计算基础物理伤害
    int baseDamage = calculateBaseDamage(attacker);

    // 4. 判定暴击/致命一击
    result.critical = rollCriticalStrike(attacker);
    result.deadlyStrike = rollDeadlyStrike(attacker);

    // 暴击和致命一击不叠加，取较高者
    if (result.critical || result.deadlyStrike) {
      baseDamage *= 2;
    }

    // 5. 判定压碎性打击
    result.crushingBlow = rollCrushingBlow(attacker);
    if (result.crushingBlow) {
      int crushDamage = calculateCrushingBlowDamage(defender);
      baseDamage += crushDamage;
    }

    // 6. 应用伤害减免
    result.physicalDamage = applyPhysicalDamageReduction(baseDamage, defender);

    // 7. 计算元素伤害
    for (int i = 1; i < DAMAGE_TYPE_COUNT; i++) {
      int elemDamage = calculateElementalDamage(attacker, i);
      if (elemDamage > 0) {
        result.elementalDamage[i] = applyElementalResistance(elemDamage, defender, i);
      }
    }

    // 8. 计算总伤害
    result.totalDamage = result.physicalDamage;
    for (int i = 1; i < DAMAGE_TYPE_COUNT; i++) {
      result.totalDamage += result.elementalDamage[i];
    }

    // 9. 计算偷取
    result.lifeStolen = calculateLifeLeech(result.physicalDamage, attacker);
    result.manaStolen = calculateManaLeech(result.physicalDamage, attacker);

    log.debug("Attack hit: physical={}, total={}, hitChance={}%, crit={}, deadly={}",
        result.physicalDamage, result.totalDamage, result.hitChance, 
        result.critical, result.deadlyStrike);

    return result;
  }

  //==========================================================================
  // 命中率计算
  //==========================================================================

  /**
   * 计算命中率
   * 
   * <p>暗黑 II 命中公式：
   * <pre>
   * PvM: Chance to hit = 100 * AR / (AR + DR)
   *      AR = AttackerAR * (100 + ToHitPercent) / 100
   *      DR = DefenderDefense * clvl / (clvl + alvl)
   * 
   * PvP: Chance to hit = 100 * 2 * AR / (AR + DR)
   * </pre>
   */
  public int calculateHitChance(AttackerData attacker, DefenderData defender) {
    // 计算有效攻击等级
    int attackRating = calculateEffectiveAttackRating(attacker, defender);

    // 计算有效防御等级
    int defense = calculateEffectiveDefense(attacker, defender);

    // 如果无视防御
    if (attacker.ignoreTargetDefense && !defender.isPlayer) {
      defense = 0;
    }

    // 计算命中率
    int hitChance;
    if (attackRating + defense == 0) {
      hitChance = BASE_TO_HIT_CHANCE;
    } else {
      int factor = defender.isPlayer ? PVP_HIT_FACTOR : 1;
      hitChance = 100 * factor * attackRating / (attackRating + defense);
    }

    // 限制范围
    hitChance = Math.max(MIN_TO_HIT_CHANCE, Math.min(MAX_TO_HIT_CHANCE, hitChance));

    return hitChance;
  }

  /**
   * 计算有效攻击等级
   */
  private int calculateEffectiveAttackRating(AttackerData attacker, DefenderData defender) {
    int ar = attacker.attackRating;

    // 敏捷加成（怪物：5 * dex）
    if (!attacker.isPlayer) {
      ar += 5 * attacker.dexterity;
    }

    // 攻击等级百分比加成
    if (attacker.attackRatingPercent != 0) {
      ar = ar * (100 + attacker.attackRatingPercent) / 100;
    }

    // 对特殊怪物类型加成
    if (defender.isDemon && attacker.demonToHit > 0) {
      ar += attacker.demonToHit;
    }
    if (defender.isUndead && attacker.undeadToHit > 0) {
      ar += attacker.undeadToHit;
    }

    return Math.max(0, ar);
  }

  /**
   * 计算有效防御等级
   */
  private int calculateEffectiveDefense(AttackerData attacker, DefenderData defender) {
    int defense;

    // 根据攻击类型选择防御值
    if (attacker.isMissile) {
      defense = defender.defense + defender.defenseVsMissile;
    } else {
      defense = defender.defense + defender.defenseVsMelee;
    }

    // 等级差异调整
    int levelDiff = defender.level - attacker.level;
    if (levelDiff > 0) {
      // 防御者等级高，防御更有效
      defense = defense * (defender.level + levelDiff * LEVEL_DIFF_MODIFIER) / defender.level;
    }

    return Math.max(0, defense);
  }

  /**
   * 命中判定
   */
  private boolean rollHit(int hitChance) {
    return MathUtils.random(99) < hitChance;
  }

  //==========================================================================
  // 格挡计算
  //==========================================================================

  /**
   * 计算格挡率
   * 
   * <p>格挡公式：
   * <pre>
   * Block% = (Dexterity - 15) / (clvl * 2) + BaseBlock
   * 限制在 0-75% 之间
   * </pre>
   */
  public int calculateBlockChance(DefenderData defender) {
    if (!defender.canBlock) {
      return 0;
    }

    int blockChance = defender.blockChance;

    // 敏捷加成
    int dexBonus = (defender.dexterity - 15) * BLOCK_DEXTERITY_FACTOR / defender.level;
    blockChance += dexBonus;

    // 限制范围
    blockChance = Math.max(MIN_BLOCK_CHANCE, Math.min(MAX_BLOCK_CHANCE, blockChance));

    return blockChance;
  }

  //==========================================================================
  // 伤害计算
  //==========================================================================

  /**
   * 计算基础物理伤害
   */
  private int calculateBaseDamage(AttackerData attacker) {
    // 随机伤害值
    int damage = MathUtils.random(attacker.minDamage, attacker.maxDamage);

    // 力量加成（仅近战）
    if (!attacker.isMissile) {
      int strBonus = attacker.strength / 100; // 100 点力量 = +100% 伤害
      damage = damage * (100 + strBonus) / 100;
    }

    // 增强伤害百分比
    if (attacker.enhancedDamagePercent > 0) {
      damage = damage * (100 + attacker.enhancedDamagePercent) / 100;
    }

    return Math.max(1, damage);
  }

  /**
   * 计算元素伤害
   */
  private int calculateElementalDamage(AttackerData attacker, int damageType) {
    int minDmg = attacker.elementalMinDamage[damageType];
    int maxDmg = attacker.elementalMaxDamage[damageType];

    if (maxDmg <= 0) {
      return 0;
    }

    return MathUtils.random(minDmg, maxDmg);
  }

  /**
   * 计算压碎性打击伤害
   * 
   * <p>压碎性打击：
   * <ul>
   *   <li>对普通怪物：当前生命的 25%</li>
   *   <li>对 BOSS：当前生命的 10%</li>
   *   <li>对玩家：当前生命的 10%</li>
   * </ul>
   */
  private int calculateCrushingBlowDamage(DefenderData defender) {
    int percent = defender.isPlayer ? 10 : 25;
    return defender.currentLife * percent / 100;
  }

  //==========================================================================
  // 伤害减免
  //==========================================================================

  /**
   * 应用物理伤害减免
   */
  private int applyPhysicalDamageReduction(int damage, DefenderData defender) {
    if (defender.immunePhysical) {
      return 0;
    }

    // 百分比减免
    if (defender.damageReducedPercent > 0) {
      damage = damage * (100 - defender.damageReducedPercent) / 100;
    }

    // 固定减免
    damage -= defender.damageReduced;

    return Math.max(1, damage);
  }

  /**
   * 应用元素抗性
   */
  private int applyElementalResistance(int damage, DefenderData defender, int damageType) {
    if (defender.immuneElemental[damageType]) {
      return 0;
    }

    int resistance = defender.resistances[damageType];

    // 抗性可以为负（增加伤害）
    // 抗性上限为 75%（或更高，如果有装备加成）
    resistance = Math.min(75, resistance);

    damage = damage * (100 - resistance) / 100;

    // 魔法伤害还有固定减免
    if (damageType == DAMAGE_MAGIC) {
      damage -= defender.magicDamageReduced;
    }

    return Math.max(0, damage);
  }

  //==========================================================================
  // 暴击和特殊攻击
  //==========================================================================

  /**
   * 判定暴击（Critical Strike - 来自技能）
   */
  private boolean rollCriticalStrike(AttackerData attacker) {
    if (attacker.criticalStrike <= 0) {
      return false;
    }
    int chance = Math.min(attacker.criticalStrike, MAX_CRIT_CHANCE);
    return MathUtils.random(99) < chance;
  }

  /**
   * 判定致命一击（Deadly Strike - 来自装备）
   */
  private boolean rollDeadlyStrike(AttackerData attacker) {
    if (attacker.deadlyStrike <= 0) {
      return false;
    }
    int chance = Math.min(attacker.deadlyStrike, MAX_CRIT_CHANCE);
    return MathUtils.random(99) < chance;
  }

  /**
   * 判定压碎性打击
   */
  private boolean rollCrushingBlow(AttackerData attacker) {
    if (attacker.crushingBlow <= 0) {
      return false;
    }
    return MathUtils.random(99) < attacker.crushingBlow;
  }

  //==========================================================================
  // 偷取计算
  //==========================================================================

  /**
   * 计算生命偷取
   * 
   * <p>注意：生命偷取仅对物理伤害有效
   */
  private int calculateLifeLeech(int physicalDamage, AttackerData attacker) {
    if (attacker.lifeLeech <= 0) {
      return 0;
    }
    return physicalDamage * attacker.lifeLeech / 100;
  }

  /**
   * 计算法力偷取
   */
  private int calculateManaLeech(int physicalDamage, AttackerData attacker) {
    if (attacker.manaLeech <= 0) {
      return 0;
    }
    return physicalDamage * attacker.manaLeech / 100;
  }

  //==========================================================================
  // 辅助方法 - 从属性获取数据
  //==========================================================================

  /**
   * 相关属性 ID 常量
   */
  public static final class Stats {
    public static final short STRENGTH = Stat.strength;
    public static final short DEXTERITY = Stat.dexterity;
    public static final short LEVEL = Stat.level;
    public static final short ATTACK_RATING = Stat.tohit;
    public static final short DEFENSE = Stat.armorclass;
    public static final short MIN_DAMAGE = Stat.mindamage;
    public static final short MAX_DAMAGE = Stat.maxdamage;
    public static final short FIRE_MIN = Stat.firemindam;
    public static final short FIRE_MAX = Stat.firemaxdam;
    public static final short LIGHT_MIN = Stat.lightmindam;
    public static final short LIGHT_MAX = Stat.lightmaxdam;
    public static final short COLD_MIN = Stat.coldmindam;
    public static final short COLD_MAX = Stat.coldmaxdam;
    public static final short POISON_MIN = Stat.poisonmindam;
    public static final short POISON_MAX = Stat.poisonmaxdam;
    public static final short LIFE_LEECH_MIN = Stat.lifedrainmindam;
    public static final short LIFE_LEECH_MAX = Stat.lifedrainmaxdam;
    public static final short MANA_LEECH_MIN = Stat.manadrainmindam;
    public static final short MANA_LEECH_MAX = Stat.manadrainmaxdam;
    public static final short DEADLY_STRIKE = Stat.item_deadlystrike;
    public static final short CRUSHING_BLOW = Stat.item_crushingblow;
    public static final short FIRE_RESIST = Stat.fireresist;
    public static final short LIGHT_RESIST = Stat.lightresist;
    public static final short COLD_RESIST = Stat.coldresist;
    public static final short POISON_RESIST = Stat.poisonresist;
    public static final short DAMAGE_REDUCED = Stat.item_armor_percent;
    public static final short MAGIC_REDUCED = Stat.item_absorbmagic_percent;
  }
}
