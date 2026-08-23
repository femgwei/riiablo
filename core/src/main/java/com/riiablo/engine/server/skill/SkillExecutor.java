package com.riiablo.engine.server.skill;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntMap;

import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 技能执行器 - 基于 D2MOD Skills.cpp 移植
 * 
 * <p>管理技能的执行流程：
 * <ul>
 *   <li>技能施放前检查（法力、冷却、前置技能）</li>
 *   <li>技能效果计算（协同加成）</li>
 *   <li>技能执行（创建投射物、应用效果）</li>
 *   <li>技能后处理（消耗、冷却）</li>
 * </ul>
 * 
 * <p>参考：D2MOD/source/D2Game/src/SKILLS/Skills.cpp
 * 
 * @author riiablo team
 */
public class SkillExecutor {
  private static final Logger log = LogManager.getLogger(SkillExecutor.class);

  //==========================================================================
  // 常量 - 技能类型
  //==========================================================================

  /** 被动技能（无需施放） */
  public static final int SKILL_TYPE_PASSIVE = 0;

  /** 攻击技能（需要目标） */
  public static final int SKILL_TYPE_ATTACK = 1;

  /** 法术技能（可能需要目标或位置） */
  public static final int SKILL_TYPE_SPELL = 2;

  /** 光环技能（切换开关） */
  public static final int SKILL_TYPE_AURA = 3;

  /** 召唤技能 */
  public static final int SKILL_TYPE_SUMMON = 4;

  /** 陷阱技能 */
  public static final int SKILL_TYPE_TRAP = 5;

  //==========================================================================
  // 常量 - 技能执行结果
  //==========================================================================

  /** 执行成功 */
  public static final int RESULT_SUCCESS = 0;

  /** 法力不足 */
  public static final int RESULT_NO_MANA = 1;

  /** 技能冷却中 */
  public static final int RESULT_ON_COOLDOWN = 2;

  /** 缺少目标 */
  public static final int RESULT_NO_TARGET = 3;

  /** 距离过远 */
  public static final int RESULT_OUT_OF_RANGE = 4;

  /** 技能等级不足 */
  public static final int RESULT_LEVEL_TOO_LOW = 5;

  /** 缺少前置技能 */
  public static final int RESULT_MISSING_PREREQ = 6;

  /** 技能不可用 */
  public static final int RESULT_UNAVAILABLE = 7;

  //==========================================================================
  // 内部类 - 技能施放上下文
  //==========================================================================

  /**
   * 技能施放上下文
   */
  public static class SkillContext {
    /** 施放者实体 ID */
    public int casterId;

    /** 技能 ID */
    public int skillId;

    /** 技能等级 */
    public int skillLevel;

    /** 目标实体 ID（-1 表示无目标） */
    public int targetId = -1;

    /** 目标位置 */
    public float targetX;
    public float targetY;

    /** 施放者位置 */
    public float casterX;
    public float casterY;

    /** 施放者当前法力 */
    public int currentMana;

    /** 施放者最大法力 */
    public int maxMana;

    /** 施放者等级 */
    public int casterLevel;

    /** 是否是玩家 */
    public boolean isPlayer;

    /** 当前游戏帧 */
    public long currentFrame;

    /** 重置上下文 */
    public void reset() {
      casterId = -1;
      skillId = -1;
      skillLevel = 0;
      targetId = -1;
      targetX = 0;
      targetY = 0;
      casterX = 0;
      casterY = 0;
      currentMana = 0;
      maxMana = 0;
      casterLevel = 0;
      isPlayer = false;
      currentFrame = 0;
    }
  }

  /**
   * 技能执行结果
   */
  public static class SkillResult {
    /** 结果代码 */
    public int resultCode = RESULT_SUCCESS;

    /** 是否成功 */
    public boolean success;

    /** 消耗的法力 */
    public int manaUsed;

    /** 冷却时间（帧） */
    public int cooldownFrames;

    /** 造成的伤害（用于攻击技能） */
    public int damageDealt;

    /** 创建的投射物 ID */
    public int missileId = -1;

    /** 创建的召唤物 ID */
    public int summonId = -1;

    /** 应用的状态效果 ID */
    public int stateId = -1;

    /** 重置结果 */
    public void reset() {
      resultCode = RESULT_SUCCESS;
      success = false;
      manaUsed = 0;
      cooldownFrames = 0;
      damageDealt = 0;
      missileId = -1;
      summonId = -1;
      stateId = -1;
    }
  }

  /**
   * 技能数据（从 Skills.txt 读取）
   */
  public static class SkillData {
    public int skillId;
    public String skillName;
    public int skillType;
    public int charClass; // -1 表示通用
    public int reqLevel;
    public int[] reqSkills = new int[3]; // 前置技能
    public int baseMana;
    public int manaPerLevel;
    public int cooldown; // 冷却时间（帧）
    public float range; // 施放范围
    public int missileId; // 创建的投射物
    public int[] synergySkills = new int[6]; // 协同技能
    public int[] synergyBonusPercent = new int[6]; // 协同加成百分比
    public boolean isPassive;
    public boolean isAura;
    public boolean requireTarget;
    public boolean requirePosition;
  }

  //==========================================================================
  // 回调接口
  //==========================================================================

  /**
   * 技能执行回调
   */
  public interface SkillCallback {
    /**
     * 创建投射物
     */
    int onCreateMissile(int missileId, int ownerId, float startX, float startY,
        float targetX, float targetY, int skillId, int skillLevel);

    /**
     * 创建召唤物
     */
    int onCreateSummon(int summonType, int ownerId, float posX, float posY, int skillLevel);

    /**
     * 应用状态效果
     */
    void onApplyState(int targetId, int stateId, int duration, int value);

    /**
     * 消耗法力
     */
    void onConsumeMana(int casterId, int amount);

    /**
     * 播放技能动画/音效
     */
    void onSkillEffect(int casterId, int skillId, float targetX, float targetY);
  }

  //==========================================================================
  // 字段
  //==========================================================================

  /** 技能数据表 */
  private final IntMap<SkillData> skillTable = new IntMap<>();

  /** 技能冷却管理器 */
  private SkillCooldownManager cooldownManager;

  /** 技能协同管理器 */
  private SkillSynergyManager synergyManager;

  /** 回调 */
  private SkillCallback callback;

  /** 临时向量（避免频繁分配） */
  private final Vector2 tmpVec = new Vector2();

  //==========================================================================
  // 构造函数
  //==========================================================================

  public SkillExecutor() {
    // 注册一些基础技能数据
    registerDefaultSkills();
  }

  //==========================================================================
  // 核心方法 - 技能执行
  //==========================================================================

  /**
   * 执行技能
   * 
   * <p>参考 D2MOD SKILLS_DoSkill
   * 
   * @param context 技能上下文
   * @return 执行结果
   */
  public SkillResult execute(SkillContext context) {
    SkillResult result = new SkillResult();

    // 1. 获取技能数据
    SkillData skillData = skillTable.get(context.skillId);
    if (skillData == null) {
      result.resultCode = RESULT_UNAVAILABLE;
      log.debug("Skill {} not found", context.skillId);
      return result;
    }

    // 2. 检查前置条件
    int checkResult = checkPrerequisites(context, skillData);
    if (checkResult != RESULT_SUCCESS) {
      result.resultCode = checkResult;
      log.debug("Skill {} check failed: {}", context.skillId, getResultName(checkResult));
      return result;
    }

    // 3. 计算法力消耗
    int manaCost = calculateManaCost(skillData, context.skillLevel);
    if (context.currentMana < manaCost) {
      result.resultCode = RESULT_NO_MANA;
      log.debug("Skill {} failed: not enough mana ({} < {})", 
          context.skillId, context.currentMana, manaCost);
      return result;
    }

    // 4. 检查冷却
    if (cooldownManager != null && cooldownManager.isOnCooldown(context.casterId, context.skillId, context.currentFrame)) {
      result.resultCode = RESULT_ON_COOLDOWN;
      log.debug("Skill {} on cooldown", context.skillId);
      return result;
    }

    // 5. 执行技能效果
    executeSkillEffect(context, skillData, result);

    // 6. 消耗法力
    if (result.success && callback != null) {
      callback.onConsumeMana(context.casterId, manaCost);
      result.manaUsed = manaCost;
    }

    // 7. 设置冷却
    if (result.success && skillData.cooldown > 0 && cooldownManager != null) {
      // SkillCooldownManager expects the current frame here. Passing the
      // duration used to make every cooldown end near frame 0, so a skill
      // could be cast again immediately after the first cast.
      cooldownManager.startCooldown(context.casterId, context.skillId, context.currentFrame);
      result.cooldownFrames = skillData.cooldown;
    }

    log.debug("Skill {} executed: success={}, mana={}, cooldown={}",
        context.skillId, result.success, result.manaUsed, result.cooldownFrames);

    return result;
  }

  /**
   * 检查技能前置条件
   */
  private int checkPrerequisites(SkillContext context, SkillData skillData) {
    // 检查等级要求
    if (context.casterLevel < skillData.reqLevel) {
      return RESULT_LEVEL_TOO_LOW;
    }

    // 检查是否需要目标
    if (skillData.requireTarget && context.targetId < 0) {
      return RESULT_NO_TARGET;
    }

    // 检查距离
    if (skillData.range > 0) {
      float dx = context.targetX - context.casterX;
      float dy = context.targetY - context.casterY;
      float distSq = dx * dx + dy * dy;
      if (distSq > skillData.range * skillData.range) {
        return RESULT_OUT_OF_RANGE;
      }
    }

    // TODO: 检查前置技能

    return RESULT_SUCCESS;
  }

  /**
   * 执行技能效果
   */
  private void executeSkillEffect(SkillContext context, SkillData skillData, SkillResult result) {
    switch (skillData.skillType) {
      case SKILL_TYPE_ATTACK:
        executeAttackSkill(context, skillData, result);
        break;

      case SKILL_TYPE_SPELL:
        executeSpellSkill(context, skillData, result);
        break;

      case SKILL_TYPE_AURA:
        executeAuraSkill(context, skillData, result);
        break;

      case SKILL_TYPE_SUMMON:
        executeSummonSkill(context, skillData, result);
        break;

      case SKILL_TYPE_TRAP:
        executeTrapSkill(context, skillData, result);
        break;

      case SKILL_TYPE_PASSIVE:
        // 被动技能不需要执行
        result.success = true;
        break;

      default:
        result.success = false;
        break;
    }
  }

  /**
   * 执行攻击技能
   */
  private void executeAttackSkill(SkillContext context, SkillData skillData, SkillResult result) {
    // 播放效果
    if (callback != null) {
      callback.onSkillEffect(context.casterId, context.skillId, context.targetX, context.targetY);
    }

    // 如果有投射物，创建它
    if (skillData.missileId > 0 && callback != null) {
      result.missileId = callback.onCreateMissile(
          skillData.missileId,
          context.casterId,
          context.casterX,
          context.casterY,
          context.targetX,
          context.targetY,
          context.skillId,
          context.skillLevel
      );
    }

    result.success = true;
  }

  /**
   * 执行法术技能
   */
  private void executeSpellSkill(SkillContext context, SkillData skillData, SkillResult result) {
    // 播放效果
    if (callback != null) {
      callback.onSkillEffect(context.casterId, context.skillId, context.targetX, context.targetY);
    }

    // 创建投射物
    if (skillData.missileId > 0 && callback != null) {
      result.missileId = callback.onCreateMissile(
          skillData.missileId,
          context.casterId,
          context.casterX,
          context.casterY,
          context.targetX,
          context.targetY,
          context.skillId,
          context.skillLevel
      );
    }

    result.success = true;
  }

  /**
   * 执行光环技能
   */
  private void executeAuraSkill(SkillContext context, SkillData skillData, SkillResult result) {
    // 光环是切换型技能
    // TODO: 实现光环切换逻辑
    if (callback != null) {
      callback.onSkillEffect(context.casterId, context.skillId, context.casterX, context.casterY);
    }

    result.success = true;
  }

  /**
   * 执行召唤技能
   */
  private void executeSummonSkill(SkillContext context, SkillData skillData, SkillResult result) {
    if (callback != null) {
      result.summonId = callback.onCreateSummon(
          context.skillId, // 使用技能 ID 作为召唤物类型
          context.casterId,
          context.targetX,
          context.targetY,
          context.skillLevel
      );
    }

    result.success = result.summonId >= 0;
  }

  /**
   * 执行陷阱技能
   */
  private void executeTrapSkill(SkillContext context, SkillData skillData, SkillResult result) {
    // 陷阱类似于召唤物
    if (callback != null) {
      result.summonId = callback.onCreateSummon(
          context.skillId,
          context.casterId,
          context.targetX,
          context.targetY,
          context.skillLevel
      );
    }

    result.success = result.summonId >= 0;
  }

  //==========================================================================
  // 法力和协同计算
  //==========================================================================

  /**
   * 计算技能法力消耗
   */
  public int calculateManaCost(SkillData skillData, int skillLevel) {
    int manaCost = skillData.baseMana + (skillLevel - 1) * skillData.manaPerLevel;
    return Math.max(0, manaCost);
  }

  /**
   * 计算技能伤害（含协同加成）
   * 
   * @param skillId 技能 ID
   * @param skillLevel 技能等级
   * @param baseDamage 基础伤害
   * @param casterSkillLevels 施放者的技能等级表（技能ID -> 等级）
   * @return 最终伤害
   */
  public int calculateSkillDamage(int skillId, int skillLevel, int baseDamage, 
      IntMap<Integer> casterSkillLevels) {
    
    SkillData skillData = skillTable.get(skillId);
    if (skillData == null) {
      return baseDamage;
    }

    int totalBonus = 0;

    // 计算协同加成
    for (int i = 0; i < skillData.synergySkills.length; i++) {
      int synergySkillId = skillData.synergySkills[i];
      if (synergySkillId <= 0) {
        continue;
      }

      Integer synergyLevel = casterSkillLevels.get(synergySkillId);
      if (synergyLevel != null && synergyLevel > 0) {
        totalBonus += synergyLevel * skillData.synergyBonusPercent[i];
      }
    }

    // 应用加成
    int finalDamage = baseDamage * (100 + totalBonus) / 100;

    log.debug("Skill {} damage calculation: base={}, synergy={}%, final={}",
        skillId, baseDamage, totalBonus, finalDamage);

    return finalDamage;
  }

  //==========================================================================
  // 技能注册
  //==========================================================================

  /**
   * 注册技能数据
   */
  public void registerSkill(SkillData skillData) {
    skillTable.put(skillData.skillId, skillData);
  }

  /**
   * 注册默认技能（简化版本）
   */
  private void registerDefaultSkills() {
    // 法师技能
    registerSkillData(SkillId.FIRE_BOLT, "Fire Bolt", SKILL_TYPE_SPELL, 0, 1, 3, 1, 0, 30);
    registerSkillData(SkillId.FIRE_BALL, "Fire Ball", SKILL_TYPE_SPELL, 0, 12, 10, 1, 0, 40);
    registerSkillData(SkillId.METEOR, "Meteor", SKILL_TYPE_SPELL, 0, 24, 17, 1, 60, 50);
    registerSkillData(SkillId.FROZEN_ORB, "Frozen Orb", SKILL_TYPE_SPELL, 0, 30, 25, 2, 30, 50);
    registerSkillData(SkillId.BLIZZARD, "Blizzard", SKILL_TYPE_SPELL, 0, 24, 23, 2, 60, 50);
    registerSkillData(SkillId.LIGHTNING, "Lightning", SKILL_TYPE_SPELL, 0, 12, 8, 1, 0, 40);
    registerSkillData(SkillId.CHAIN_LIGHTNING, "Chain Lightning", SKILL_TYPE_SPELL, 0, 18, 12, 1, 0, 40);

    // 圣骑士技能
    registerSkillData(SkillId.BLESSED_HAMMER, "Blessed Hammer", SKILL_TYPE_SPELL, 3, 18, 5, 1, 0, 40);

    // 死灵法师技能
    registerSkillData(SkillId.BONE_SPEAR, "Bone Spear", SKILL_TYPE_SPELL, 2, 18, 7, 1, 0, 40);
    registerSkillData(SkillId.BONE_SPIRIT, "Bone Spirit", SKILL_TYPE_SPELL, 2, 24, 12, 1, 0, 45);

    // 亚马逊技能
    registerSkillData(SkillId.GUIDED_ARROW, "Guided Arrow", SKILL_TYPE_ATTACK, 1, 18, 8, 1, 0, 50);
    registerSkillData(SkillId.MULTIPLE_SHOT, "Multishot", SKILL_TYPE_ATTACK, 1, 6, 4, 1, 0, 40);

    // 野蛮人技能
    registerSkillData(SkillId.WHIRLWIND, "Whirlwind", SKILL_TYPE_ATTACK, 4, 30, 25, 3, 0, 20);

    log.debug("Registered {} default skills", skillTable.size);
  }

  private void registerSkillData(int id, String name, int type, int charClass, int reqLevel,
      int baseMana, int manaPerLevel, int cooldown, float range) {
    SkillData data = new SkillData();
    data.skillId = id;
    data.skillName = name;
    data.skillType = type;
    data.charClass = charClass;
    data.reqLevel = reqLevel;
    data.baseMana = baseMana;
    data.manaPerLevel = manaPerLevel;
    data.cooldown = cooldown;
    data.range = range;
    data.requireTarget = (type == SKILL_TYPE_ATTACK);
    data.requirePosition = (type == SKILL_TYPE_SPELL || type == SKILL_TYPE_SUMMON);
    skillTable.put(id, data);
  }

  //==========================================================================
  // 辅助方法
  //==========================================================================

  private String getResultName(int resultCode) {
    switch (resultCode) {
      case RESULT_SUCCESS: return "SUCCESS";
      case RESULT_NO_MANA: return "NO_MANA";
      case RESULT_ON_COOLDOWN: return "ON_COOLDOWN";
      case RESULT_NO_TARGET: return "NO_TARGET";
      case RESULT_OUT_OF_RANGE: return "OUT_OF_RANGE";
      case RESULT_LEVEL_TOO_LOW: return "LEVEL_TOO_LOW";
      case RESULT_MISSING_PREREQ: return "MISSING_PREREQ";
      case RESULT_UNAVAILABLE: return "UNAVAILABLE";
      default: return "UNKNOWN";
    }
  }

  //==========================================================================
  // 配置方法
  //==========================================================================

  public void setCallback(SkillCallback callback) {
    this.callback = callback;
  }

  public void setCooldownManager(SkillCooldownManager cooldownManager) {
    this.cooldownManager = cooldownManager;
  }

  public void setSynergyManager(SkillSynergyManager synergyManager) {
    this.synergyManager = synergyManager;
  }

  /**
   * 获取技能数据
   */
  public SkillData getSkillData(int skillId) {
    return skillTable.get(skillId);
  }
}
