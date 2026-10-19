package com.riiablo.engine.server.combat;

import static com.riiablo.engine.server.combat.NativeCombatConstants.*;

/** Mutable scalar combat snapshot. No runtime attribute/ECS references are retained.
 * Array fields are per-snapshot, and remain mutable for compatibility with host builders.
 */
public class NativeDefenseData {
  /** 实体 ID */
  public int entityId;

  /** 是否是玩家 */
  public boolean isPlayer;

  /** 是否是怪物 */
  public boolean isMonster;

  /**
   * Whether the native Ignore Target Defense property is allowed to zero
   * this defender's armor class.  D2 excludes players, unique/super-unique
   * monsters, bosses, and hirelings; synthetic callers default to true so
   * ordinary monster behavior remains backwards compatible.
   */
  public boolean ignoreTargetDefenseAllowed = true;

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
  /** The defender has an actual active shield equipped. */
  public boolean hasEquippedShield = true;
  /** CharStats.txt BlockFactor, kept separate from item ToBlock. */
  public int blockFactor;
  /** Native movement mode: running lowers shield block chance to one third. */
  public boolean isRunning;
  /** Native item_fasterblockrate and lastblockframe snapshots. */
  public int fasterBlockRate;
  public int lastBlockFrame;
  public int currentFrame;

  /** Native passive-defense context. Callers may set isMoving for Evade. */
  public int attackType = NativeCombatConstants.ATTACK_MELEE;
  public boolean isMoving;
  public int passiveDodge;
  public int passiveAvoid;
  public int passiveEvade;
  public int passiveWeaponBlock;

  /** 各元素抗性 */
  public int[] resistances = new int[DAMAGE_TYPE_COUNT];

  /** Native maximum resistance caps (75 + max*resist stats). */
  public int[] maxResistances = {0, 75, 75, 75, 75, 75};

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

  /** Native cold-state duration gates evaluated after cold resistance. */
  public boolean cannotBeFrozen;
  public boolean halfFreezeDuration;

  /** Native PoisLen row, including its separate difficulty penalty. */
  public int poisonLengthResistance;
  public boolean poisonShrine;

  /** Native elemental absorption, applied after resistance. */
  public int[] absorbPercent = new int[DAMAGE_TYPE_COUNT];
  public int[] absorbFlat = new int[DAMAGE_TYPE_COUNT];
}
