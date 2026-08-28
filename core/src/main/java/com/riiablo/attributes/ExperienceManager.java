package com.riiablo.attributes;

import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntSet;

import com.riiablo.CharacterClass;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatListRef;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.party.Party;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.engine.server.party.PartyMember;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.save.CharData;

/**
 * 经验值管理系统 - 参考 D2MOD SUNITDMG_* 实现
 * 
 * <p>处理角色击杀怪物后的经验值获取和升级逻辑，包括：
 * <ul>
 *   <li>等级差惩罚/奖励机制</li>
 *   <li>组队经验值分配（参考 D2MOD SUNITDMG_DistributeExperience）</li>
 *   <li>佣兵经验值处理（33.6%经验）</li>
 *   <li>难度系数调整</li>
 *   <li>升级时的属性自动增长</li>
 *   <li>升级时的生命/法力恢复</li>
 * </ul>
 * 
 * <p>参考：D2MOD/source/D2Game/src/UNIT/SUnitDmg.cpp
 * 
 * @author riiablo team
 */
public class ExperienceManager extends PassiveSystem {

  private static final Logger log = LogManager.getLogger(ExperienceManager.class);

  private ExperienceTable expTable;
  
  /** 队伍管理器（可选注入） */
  private PartyManager partyManager;
  /** A victim can be observed by more than one lethal combat path in a frame. */
  private final IntSet rewardedVictims = new IntSet();

  //==========================================================================
  // 经验值系数常量（来自 D2MOD）
  //==========================================================================

  // 经验值惩罚系数（当攻击者等级 >= 防御者等级时）
  // 等级差 0-5: 100%, 6: 80.9%, 7: 62.1%, 8: 43.0%, 9: 23.8%, 10+: 5.1%
  private static final int[] EXPERIENCE_PENALTY_FACTORS = {
    256, 256, 256, 256, 256, 256, 207, 159, 110, 61, 13
  };

  // 经验值奖励系数（当攻击者等级 < 防御者等级 且 攻击者等级 < 25）
  // 等级差 0-5: 100%, 6: 87.9%, 7: 67.9%, 8: 35.9%, 9: 14.8%, 10+: 2.0%
  private static final int[] EXPERIENCE_BONUS_FACTORS = {
    256, 256, 256, 256, 256, 256, 225, 174, 92, 38, 5
  };

  /** 
   * 佣兵经验系数：86/256 = 33.6%
   * 当击杀者不是佣兵本身时，佣兵获得的经验比例
   */
  private static final int HIRELING_EXP_NUMERATOR = 86;
  private static final int HIRELING_EXP_DENOMINATOR = 256;

  /**
   * 组队经验加成系数：89/256 = 34.8% 每额外队员
   * 2人组队: +34.8%, 3人: +69.5%, 8人: +243.4%
   */
  private static final int PARTY_EXP_BONUS_NUMERATOR = 89;
  private static final int PARTY_EXP_BONUS_DENOMINATOR = 256;

  /** 经验共享距离（子格）*/
  private static final int EXP_SHARE_RANGE = 2 * 10; // 约两个屏幕距离

  public ExperienceManager() {
    expTable = ExperienceTable.getInstance();
  }

  /**
   * 设置队伍管理器（用于组队经验分配）
   * 
   * @param partyManager 队伍管理器实例
   */
  public void setPartyManager(PartyManager partyManager) {
    this.partyManager = partyManager;
  }

  @Subscribe
  public void onDeathEvent(DeathEvent event) {
    log.traceEntry("onDeathEvent(killer: {}, victim: {})", event.killer, event.victim);

    if (event == null || event.victim < 0) return;
    if (!rewardedVictims.add(event.victim)) {
      log.warn("[XP_SYNC] duplicate death ignored: killer={}, victim={}",
          event.killer, event.victim);
      return;
    }

    // 检查受害者是否为怪物
    Monster monster = world.getMapper(Monster.class).get(event.victim);
    if (monster == null || monster.monstats == null) {
      return; // 不是怪物，或无统计数据
    }

    // 检查击杀者是否为玩家
    Player player = world.getMapper(Player.class).get(event.killer);
    if (player == null || player.data == null) {
      return; // 不是玩家角色
    }

    // SUNITDMG_DistributeExperience reads the stats initialized on the unit,
    // not raw MonStats columns. Those values already contain area-level,
    // player-count and unique/champion modifiers.
    AttributesWrapper wrapper = world.getMapper(AttributesWrapper.class).get(event.victim);
    StatListRef defenderStats = wrapper == null || wrapper.attrs == null
        ? null : wrapper.attrs.base();
    if (defenderStats == null || defenderStats.get(Stat.level) == null
        || defenderStats.get(Stat.experience) == null) {
      log.error("[XP_NATIVE] victim lacks authoritative level/experience stats: victim={} monster={}",
          event.victim, monster.monstats.Id);
      return;
    }
    int defenderLevel = getInt(defenderStats, Stat.level, 1);
    int defenderExp = getInt(defenderStats, Stat.experience, 0);
    if (defenderExp <= 0) return;

    // 分配经验值（参考 D2MOD SUNITDMG_DistributeExperience）
    distributeExperience(player.data, event.killer, defenderLevel, defenderExp);
  }

  /**
   * 分配经验值（参考 D2MOD SUNITDMG_DistributeExperience）
   * 
   * <p>处理组队经验共享、佣兵经验等复杂逻辑
   * 
   * @param killerData 击杀者角色数据
   * @param killerEntityId 击杀者实体ID
   * @param defenderLevel 被杀怪物等级
   * @param defenderExp 被杀怪物基础经验值
   */
  private void distributeExperience(CharData killerData, int killerEntityId,
      int defenderLevel, int defenderExp) {
    
    StatListRef killerStats = killerData.getStats().base();
    int killerLevel = getInt(killerStats, Stat.level, killerData.level);
    int killerAddExperience = getInt(
        killerData.getStats().aggregate(), Stat.item_addexperience, 0);

    // TODO: 处理佣兵经验值
    // 如果有佣兵，计算佣兵应得经验（33.6%，如果击杀者不是佣兵）
    // addExperienceForHireling(...)

    // 检查是否在队伍中
    short partyId = partyManager != null ? partyManager.getPartyId(killerEntityId) : -1;
    
    if (partyId < 0 || partyManager == null) {
      // 不在队伍中，直接给击杀者经验
      long experienceGained = computeExperienceGain(
          killerData.charClass, killerLevel, defenderLevel, defenderExp,
          killerAddExperience);
      if (experienceGained > 0) {
        addExperienceForPlayer(killerData, killerLevel, experienceGained);
      }
      return;
    }

    // 在队伍中，分配经验给同场景的队友
    Party party = partyManager.getParty(partyId);
    if (party == null || party.getMemberCount() <= 1) {
      // 队伍无效或只有自己
      long experienceGained = computeExperienceGain(
          killerData.charClass, killerLevel, defenderLevel, defenderExp,
          killerAddExperience);
      if (experienceGained > 0) {
        addExperienceForPlayer(killerData, killerLevel, experienceGained);
      }
      return;
    }

    // 获取同场景存活队友
    Array<PartyMember> membersInRange = party.getMembers();
    int memberCount = 0;
    int levelSum = 0;
    
    // 收集有效队友信息
    for (PartyMember member : membersInRange) {
      if (member.alive && member.online) {
        memberCount++;
        levelSum += member.level;
      }
    }

    if (memberCount <= 1 || levelSum <= 0) {
      // 只有击杀者，直接给经验
      long experienceGained = computeExperienceGain(
          killerData.charClass, killerLevel, defenderLevel, defenderExp,
          killerAddExperience);
      if (experienceGained > 0) {
        addExperienceForPlayer(killerData, killerLevel, experienceGained);
      }
      return;
    }

    // 计算组队经验加成（D2MOD公式）
    // totalExp = baseExp + 89 * baseExp * (members - 1) / 256
    long totalExp = defenderExp + 
        (long) PARTY_EXP_BONUS_NUMERATOR * defenderExp * (memberCount - 1) / PARTY_EXP_BONUS_DENOMINATOR;
    
    log.debug("Party exp distribution: {} members, base={}, total={}", 
        memberCount, defenderExp, totalExp);

    // 按等级比例分配经验
    float multiplier = (float) totalExp / (float) levelSum;
    
    for (PartyMember member : membersInRange) {
      if (!member.alive || !member.online) {
        continue;
      }
      
      // 每个成员按等级比例获得经验
      int memberShare = (int) (member.level * multiplier);
      
      // 应用等级差系数
      // TODO: 需要获取成员的CharData来计算，这里暂时只处理击杀者
      if (member.entityId == killerEntityId) {
        long experienceGained = computeExperienceGain(
            killerData.charClass, killerLevel, defenderLevel, memberShare,
            killerAddExperience);
        if (experienceGained > 0) {
          addExperienceForPlayer(killerData, killerLevel, experienceGained);
        }
      }
      // TODO: 其他队友的经验分配需要通过网络同步
    }
  }

  /**
   * 计算经验值获取量（参考 D2MOD SUNITDMG_ComputeExperienceGain）
   * 
   * <p>完整实现包括：
   * <ul>
   *   <li>等级差惩罚/奖励</li>
   *   <li>难度系数（ExpRatio）</li>
   *   <li>装备经验值加成</li>
   * </ul>
   * 
   * @param charClass 角色职业
   * @param attackerLevel 攻击者等级
   * @param defenderLevel 防御者等级
   * @param defenderExperience 防御者基础经验值
   * @return 计算后的经验值
   */
  private long computeExperienceGain(
      byte charClass,
      int attackerLevel,
      int defenderLevel,
      int defenderExperience,
      int addExperiencePercent) {
    int classId = charClass & 0xFF;
    return computeNativeExperienceGain(
        expTable.getMaxLevel(classId), attackerLevel, defenderLevel,
        defenderExperience, expTable.getExpRatio(attackerLevel),
        expTable.getExpRatioShift(), addExperiencePercent);
  }

  /**
   * 应用难度经验系数
   * 
   * <p>参考 D2MOD DATATBLS_GetExpRatio，根据等级和难度调整经验值
   * 
   * @param experience 基础经验值
   * @param level 玩家等级
   * @return 调整后的经验值
   */
  static long computeNativeExperienceGain(
      int maxLevel,
      int attackerLevel,
      int defenderLevel,
      int defenderExperience,
      int expRatio,
      int expRatioShift,
      int addExperiencePercent) {
    // SUNITDMG_ComputeExperienceGain returns one before checking max level.
    if (defenderExperience <= 0) return 1;

    long baseExperience = Math.min(
        (long) defenderExperience, Integer.MAX_VALUE >> 8);
    if (attackerLevel >= maxLevel) return 0;

    long result = baseExperience;
    if (defenderLevel <= attackerLevel) {
      int index = Math.min(Math.max(0, attackerLevel - defenderLevel),
          EXPERIENCE_PENALTY_FACTORS.length - 1);
      int factor = EXPERIENCE_PENALTY_FACTORS[index];
      if (factor != 256) result = result * factor / 256;
    } else if (attackerLevel < 25 || defenderLevel <= 0) {
      int index = Math.min(Math.max(0, defenderLevel - attackerLevel),
          EXPERIENCE_BONUS_FACTORS.length - 1);
      int factor = EXPERIENCE_BONUS_FACTORS[index];
      if (factor != 256) result = result * factor / 256;
    } else {
      result = result * attackerLevel / defenderLevel;
    }

    if (result > 0 && expRatioShift > 0 && expRatioShift < 32) {
      int shift = expRatioShift + (expRatio >> expRatioShift);
      if (shift < 63 && result <= ((long) Integer.MAX_VALUE >> shift)) {
        result = result * expRatio >> expRatioShift;
      } else {
        result = expRatio * (result >> expRatioShift);
      }
    }

    if (addExperiencePercent != 0) {
      result += result * addExperiencePercent / 100;
    }
    return Math.max(0, result);
  }

  /**
   * 为佣兵添加经验值
   * 
   * <p>参考 D2MOD SUNITDMG_AddExperienceForHireling
   * 
   * @param playerData 玩家角色数据
   * @param hirelingLevel 佣兵等级
   * @param defenderLevel 被杀怪物等级
   * @param defenderExp 怪物基础经验值
   * @param isKiller 佣兵是否是击杀者
   */
  public void addExperienceForHireling(CharData playerData, int hirelingLevel,
      int defenderLevel, int defenderExp, boolean isKiller) {
    
    // 计算佣兵应得经验
    long baseExp = computeExperienceGain(
        (byte) 0, hirelingLevel, defenderLevel, defenderExp, 0);
    
    // 如果击杀者不是佣兵，只获得33.6%经验
    if (!isKiller) {
      baseExp = baseExp * HIRELING_EXP_NUMERATOR / HIRELING_EXP_DENOMINATOR;
    }
    
    if (baseExp <= 0) {
      return;
    }
    
    // TODO: 更新佣兵经验值
    // 需要访问 MercData 并更新其经验和等级
    log.debug("Hireling gained {} experience", baseExp);
  }

  /**
   * 为玩家添加经验值（参考 D2MOD SUNITDMG_AddExperienceForPlayer）
   * 
   * @param charData 角色数据
   * @param oldLevel 旧等级
   * @param experienceGained 获得的经验值
   */
  public void addExperienceForPlayer(CharData charData, int oldLevel, long experienceGained) {
    if (charData == null) {
      return;
    }

    StatListRef stats = charData.getStats().base();
    long currentExp = getLong(stats, Stat.experience, 0);
    int charClass = charData.charClass & 0xFF;

    // 计算最大经验值（99级所需经验值）
    long maxExp = expTable.getExperienceForNextLevel(ExperienceTable.MAX_LEVEL - 1, charClass);
    long newExp = Math.min(currentExp + experienceGained, maxExp);

    // 更新经验值
    int encodedExp = (int) Math.min(newExp, Integer.MAX_VALUE);
    int encodedLastExp = (int) Math.min(experienceGained, Integer.MAX_VALUE);
    stats.put(Stat.experience, encodedExp);
    stats.put(Stat.lastexp, encodedLastExp);
    // The HUD and combat adapter read aggregate stats.  Updating base only
    // leaves the visible XP bar unchanged until an unrelated item refresh.
    charData.getStats().aggregate().put(Stat.experience, encodedExp);
    charData.getStats().aggregate().put(Stat.lastexp, encodedLastExp);

    log.info("[XP_SYNC] character={} gained={} total={} oldLevel={} aggregate={}",
        charData.name, experienceGained, newExp, oldLevel,
        charData.getStats().get(Stat.experience).asLong());

    // 检查是否升级
    int newLevel = getCurrentLevelFromExp(charClass, newExp);
    if (newLevel != oldLevel) {
      levelUp(charData, oldLevel, newLevel);
    }
  }

  /**
   * 根据经验值计算当前等级（参考 D2MOD DATATBLS_GetCurrentLevelFromExp）
   * 
   * @param charClass 角色职业
   * @param experience 经验值
   * @return 当前等级
   */
  private int getCurrentLevelFromExp(int charClass, long experience) {
    for (int level = ExperienceTable.MAX_LEVEL; level >= 1; level--) {
      long expForLevel = expTable.getExperienceForCurrentLevel(level, charClass);
      if (experience >= expForLevel) {
        return level;
      }
    }
    return 1;
  }

  /**
   * 处理角色升级（参考 D2MOD PLAYERSTATS_LevelUp）
   * 
   * @param charData 角色数据
   * @param oldLevel 旧等级
   * @param newLevel 新等级
   */
  private void levelUp(CharData charData, int oldLevel, int newLevel) {
    if (newLevel <= oldLevel) {
      return;
    }

    int levelDiff = newLevel - oldLevel;
    charData.level = (byte) newLevel;

    StatListRef stats = charData.getStats().base();
    stats.put(Stat.level, newLevel);
    charData.getStats().aggregate().put(Stat.level, newLevel);

    // 更新下一级所需经验值
    int charClass = charData.charClass & 0xFF;
    long nextExp = expTable.getExperienceForNextLevel(newLevel, charClass);
    stats.put(Stat.nextexp, (int) Math.min(nextExp, Integer.MAX_VALUE));
    charData.getStats().aggregate().put(Stat.nextexp, (int) Math.min(nextExp, Integer.MAX_VALUE));

    log.info("Character leveled up from {} to {}!", oldLevel, newLevel);

    // 获取角色职业统计数据
    CharacterClass classId = CharacterClass.get(charData.charClass & 0xFF);
    if (classId == null) {
      log.warn("Unknown character class: {}", charData.charClass);
      return;
    }

    com.riiablo.codec.excel.CharStats.Entry charStats = classId.entry();
    if (charStats == null) {
      log.warn("CharStats entry not found for class: {}", classId);
      return;
    }

    // CharStats stores life/mana/stamina growth in quarter points. D2 adds
    // those values to the raw 24.8 stat with << 6. StatListRef.put(float)
    // already performs the 24.8 conversion, so shifting and then calling
    // put(int) double-encodes the value and turns a normal +2 life level-up
    // into roughly +512 life.
    float maxHpBefore = getFixed(charData.getStats().aggregate(), Stat.maxhp, 0f);
    float maxHpIncrease = levelDiff * charStats.LifePerLevel / 4f;
    addFixed(stats, Stat.maxhp, maxHpIncrease);
    addFixed(charData.getStats().aggregate(), Stat.maxhp, maxHpIncrease);
    float newMaxHp = getFixed(charData.getStats().aggregate(), Stat.maxhp,
        getFixed(stats, Stat.maxhp, 0f));
    stats.put(Stat.hitpoints, newMaxHp);
    charData.getStats().aggregate().put(Stat.hitpoints, newMaxHp);

    float maxManaIncrease = levelDiff * charStats.ManaPerLevel / 4f;
    addFixed(stats, Stat.maxmana, maxManaIncrease);
    addFixed(charData.getStats().aggregate(), Stat.maxmana, maxManaIncrease);
    float newMaxMana = getFixed(charData.getStats().aggregate(), Stat.maxmana,
        getFixed(stats, Stat.maxmana, 0f));
    stats.put(Stat.mana, newMaxMana);
    charData.getStats().aggregate().put(Stat.mana, newMaxMana);

    float maxStaminaIncrease = levelDiff * charStats.StaminaPerLevel / 4f;
    addFixed(stats, Stat.maxstamina, maxStaminaIncrease);
    addFixed(charData.getStats().aggregate(), Stat.maxstamina, maxStaminaIncrease);
    float newMaxStamina = getFixed(charData.getStats().aggregate(), Stat.maxstamina,
        getFixed(stats, Stat.maxstamina, 0f));
    stats.put(Stat.stamina, newMaxStamina);
    charData.getStats().aggregate().put(Stat.stamina, newMaxStamina);

    // 增加属性点（每级增长）
    int statPtsIncrease = levelDiff * charStats.StatPerLevel;
    int currentStatPts = getInt(stats, Stat.statpts, 0);
    stats.put(Stat.statpts, currentStatPts + statPtsIncrease);
    charData.getStats().aggregate().put(Stat.statpts, currentStatPts + statPtsIncrease);

    // 增加技能点（每级 1 点）
    int skillPtsBefore = getInt(stats, Stat.newskills, 0);
    int currentSkillPts = getInt(stats, Stat.newskills, 0);
    stats.put(Stat.newskills, currentSkillPts + levelDiff);
    charData.getStats().aggregate().put(Stat.newskills, currentSkillPts + levelDiff);

    // Do not reset the complete aggregate here: reset() copies base only and
    // temporarily drops equipped-item stats. Update just the changed stats.
    log.info("[XP_LEVEL] character={} level={}->{} skillPoints={}->{} maxHp={}->{} lifeGain={} maxMana={} maxStamina={}",
        charData.name, oldLevel, newLevel, skillPtsBefore, currentSkillPts + levelDiff,
        maxHpBefore, newMaxHp, maxHpIncrease, newMaxMana, newMaxStamina);

    // Level-up changes newskills without touching equipment, so CharData's
    // normal item update callback is not fired. Explicitly notify the skill
    // tree and quick-bar listeners so the available-point counter updates.
    charData.notifySkillChanged();

    // TODO: 播放升级音效
    // TODO: 触发升级事件
  }

  /**
   * 安全获取整数属性值
   */
  private int getInt(StatListRef stats, short stat, int defaultValue) {
    StatRef ref = stats.get(stat);
    return ref != null ? ref.asInt() : defaultValue;
  }

  private float getFixed(StatListRef stats, short stat, float defaultValue) {
    StatRef ref = stats.get(stat);
    return ref != null ? ref.asFixed() : defaultValue;
  }

  private void addFixed(StatListRef stats, short stat, float value) {
    if (value == 0f) return;
    StatRef ref = stats.get(stat);
    if (ref == null) {
      stats.put(stat, value);
    } else {
      ref.add(value);
    }
  }

  /**
   * 安全获取长整数属性值
   */
  private long getLong(StatListRef stats, short stat, long defaultValue) {
    StatRef ref = stats.get(stat);
    return ref != null ? ref.asLong() : defaultValue;
  }
}
