package com.riiablo.engine.server.combat;

import static com.riiablo.engine.server.combat.NativeCombatConstants.*;

/** Mutable scalar combat snapshot. No runtime attribute/ECS references are retained.
 * Array fields are per-snapshot, and remain mutable for compatibility with host builders.
 */
public class NativeAttackData {
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

  /** Skill-owned physical conversion applied before target resistances. */
  public int physicalConversionPercent;
  public int physicalConversionType = DAMAGE_PHYSICAL;

  /** 各元素伤害（火/电/冰/毒/魔法） */
  public int[] elementalMinDamage = new int[DAMAGE_TYPE_COUNT];
  public int[] elementalMaxDamage = new int[DAMAGE_TYPE_COUNT];

  /** Target-class bonus applied to rolled magic damage before mitigation. */
  public int magicTargetBonusPercent;

  /** Native percentage resistance pierce, indexed by damage type. */
  public int[] elementalPierce = new int[DAMAGE_TYPE_COUNT];

  /** 对恶魔攻击等级加成 */
  public int demonToHit;

  /** 对亡灵攻击等级加成 */
  public int undeadToHit;

  /** Native item_demondamage/undeaddamage target-class percentages. */
  public int demonDamagePercent;
  public int undeadDamagePercent;

  /** Sanctuary ignores positive physical resistance on undead targets. */
  public boolean bypassUndeadPhysicalResistance;

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
  /** Opt-in 8.8 elemental packet; legacy callers remain in whole HP. */
  public boolean fixedPointElements;

  /** Missiles.txt ToHit is disabled, so collision itself guarantees a hit. */
  public boolean alwaysHit;

  /** 生命偷取百分比 */
  public int lifeLeech;

  /** 法力偷取百分比 */
  public int manaLeech;

  /** 击中后冰冻敌人时间（帧） */
  public int coldLength;

  /** 毒素持续时间（帧） */
  public int poisonLength;

  /** 使用的技能 ID（-1 表示普通攻击） */
  public int skillId = -1;

  /** 技能等级 */
  public int skillLevel;

  /** Difficulty snapshot used for monster cold-length scaling. */
  public int difficulty;
  /** Native state application owns difficulty scaling for production packets. */
  public boolean stateDurationsDeferred;
}
