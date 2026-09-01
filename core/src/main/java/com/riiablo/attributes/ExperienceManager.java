package com.riiablo.attributes;

import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

import com.artemis.Aspect;
import com.artemis.EntitySubscription;
import com.badlogic.gdx.utils.IntArray;

import com.riiablo.CharacterClass;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatListRef;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.MonsterRewardState;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.KillCreditResolver;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.quest.NativeMercenaryRewardSystem;
import com.riiablo.engine.server.party.Party;
import com.riiablo.engine.server.party.PartyManager;
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
  @com.artemis.annotations.Wire(name = "partyManager", failOnNull = false)
  private PartyManager partyManager;
  private com.artemis.ComponentMapper<Player> mPlayer;
  private com.artemis.ComponentMapper<Mercenary> mMercenary;
  private com.artemis.ComponentMapper<Corpse> mCorpse;
  private com.artemis.ComponentMapper<AttributesWrapper> mAttributesWrapper;
  private com.artemis.ComponentMapper<MapWrapper> mMapWrapper;
  private com.artemis.ComponentMapper<Position> mPosition;
  private com.artemis.ComponentMapper<MonsterRewardState> mMonsterRewardState;
  private EntitySubscription players;
  private EntitySubscription mercenaries;
  private KillCreditResolver killCredits;
  private com.riiablo.engine.server.NativeHirelingExperienceTable hirelingExperience;
  @com.artemis.annotations.SkipWire
  private NativeMercenaryRewardSystem mercenaryRewards;
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

  public ExperienceManager() {
    expTable = ExperienceTable.getInstance();
  }

  @Override
  protected void initialize() {
    players = world.getAspectSubscriptionManager().get(Aspect.all(Player.class));
    mercenaries = world.getAspectSubscriptionManager().get(Aspect.all(Mercenary.class));
    killCredits = new KillCreditResolver(
        mPlayer, mMercenary, mMapWrapper, mPosition, partyManager);
    hirelingExperience = com.riiablo.engine.server.NativeHirelingExperienceTable.load();
    mercenaryRewards = world.getSystem(NativeMercenaryRewardSystem.class);
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
    // 检查受害者是否为怪物
    Monster monster = world.getMapper(Monster.class).get(event.victim);
    if (monster == null || monster.monstats == null) {
      return; // 不是怪物，或无统计数据
    }
    MonsterRewardState rewards = mMonsterRewardState.has(event.victim)
        ? mMonsterRewardState.get(event.victim)
        : mMonsterRewardState.create(event.victim).reset();
    if (!rewards.claimExperience()) {
      if (rewards.noExperience()) {
        log.debug("[XP_NATIVE] no experience for resurrected monster: victim={}", event.victim);
      } else {
        log.warn("[XP_SYNC] duplicate death ignored: killer={}, victim={}",
            event.killer, event.victim);
      }
      return;
    }

    // D2Game resolves player, hireling and owned minion kills to the owning
    // player before distributing either player or hireling experience.
    int ownerId = killCredits == null ? event.killer : killCredits.ownerOf(event.killer);
    Player player = ownerId < 0 ? null : mPlayer.get(ownerId);
    if (player == null || player.data == null) {
      return; // unowned hostile or unsupported summon
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

    awardHirelingExperience(ownerId, event.killer, defenderLevel, defenderExp);

    // 分配经验值（参考 D2MOD SUNITDMG_DistributeExperience）
    distributeExperience(player.data, ownerId, event.victim, defenderLevel, defenderExp);
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
  private void distributeExperience(CharData killerData, int ownerEntityId,
      int victimEntityId, int defenderLevel, int defenderExp) {
    
    StatListRef killerStats = killerData.getStats().base();
    int killerLevel = getInt(killerStats, Stat.level, killerData.level);
    int killerAddExperience = getInt(
        killerData.getStats().aggregate(), Stat.item_addexperience, 0);

    // 检查是否在队伍中
    short partyId = partyManager != null ? partyManager.getPartyId(ownerEntityId) : -1;
    
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

    // 在队伍中，按 D2MOO PARTY_IteratePartyMembersInSameLevel 收集真实 ECS
    // 玩家。PartyMember 是网络/UI 快照，不能作为等级、场景或存活状态的权威来源。
    Party party = partyManager.getParty(partyId);
    if (party == null) {
      // Stale party id: fall back to the owning player.
      long experienceGained = computeExperienceGain(
          killerData.charClass, killerLevel, defenderLevel, defenderExp,
          killerAddExperience);
      if (experienceGained > 0) {
        addExperienceForPlayer(killerData, killerLevel, experienceGained);
      }
      return;
    }

    int victimLevelId = levelId(victimEntityId);
    IntArray eligible = killCredits == null ? new IntArray()
        : killCredits.eligibleExperiencePlayers(
            ownerEntityId, victimEntityId, victimLevelId, players);
    int memberCount = eligible.size;
    int levelSum = 0;
    for (int i = 0; i < eligible.size; i++) {
      levelSum += memberLevel(eligible.get(i), 1);
    }

    if (memberCount <= 0 || levelSum <= 0) {
      // Native party iteration can reject every member when nobody is alive,
      // in the victim's level and within 80 subtiles.
      return;
    }

    if (memberCount == 1) {
      int targetId = eligible.first();
      Player target = mPlayer.get(targetId);
      if (target == null || target.data == null) return;
      int targetLevel = memberLevel(targetId, 1);
      int targetBonus = itemExperienceBonus(targetId);
      long experienceGained = computeExperienceGain(
          target.data.charClass, targetLevel, defenderLevel, defenderExp, targetBonus);
      if (experienceGained > 0) {
        addExperienceForPlayer(target.data, targetLevel, experienceGained);
      }
      return;
    }

    // 计算组队经验加成（D2MOD公式）
    // totalExp = baseExp + 89 * baseExp * (members - 1) / 256
    long totalExp = defenderExp +
        (long) PARTY_EXP_BONUS_NUMERATOR * defenderExp * (memberCount - 1)
            / PARTY_EXP_BONUS_DENOMINATOR;
    
    log.debug("Party exp distribution: {} members, base={}, total={}", 
        memberCount, defenderExp, totalExp);

    // 按等级比例分配经验
    for (int i = 0; i < eligible.size; i++) {
      int targetId = eligible.get(i);
      Player target = mPlayer == null ? null : mPlayer.get(targetId);
      if (target == null || target.data == null) continue;
      int targetLevel = memberLevel(targetId, 1);
      int targetBonus = itemExperienceBonus(targetId);
      long memberShare = computeNativePartyShare(defenderExp, targetLevel,
          memberCount, levelSum);
      int share = (int) Math.min(Integer.MAX_VALUE, memberShare);
      long experienceGained = computeExperienceGain(
          target.data.charClass, targetLevel, defenderLevel, share,
          targetBonus);
      if (experienceGained > 0) addExperienceForPlayer(target.data, targetLevel, experienceGained);
    }
  }

  private int itemExperienceBonus(int entityId) {
    if (mAttributesWrapper == null || !mAttributesWrapper.has(entityId)) return 0;
    Attributes attrs = mAttributesWrapper.get(entityId).attrs;
    return attrs == null ? 0 : getInt(attrs.aggregate(), Stat.item_addexperience, 0);
  }

  private int memberLevel(int entityId, int fallback) {
    if (mAttributesWrapper != null && mAttributesWrapper.has(entityId)) {
      Attributes attrs = mAttributesWrapper.get(entityId).attrs;
      if (attrs != null) return Math.max(1, getInt(attrs.aggregate(), Stat.level, fallback));
    }
    Player player = mPlayer == null || !mPlayer.has(entityId) ? null : mPlayer.get(entityId);
    return player == null || player.data == null
        ? Math.max(1, fallback) : Math.max(1, player.data.level & 0xFF);
  }

  private int levelId(int entityId) {
    if (mMapWrapper == null || !mMapWrapper.has(entityId)) return -1;
    MapWrapper wrapper = mMapWrapper.get(entityId);
    return wrapper == null || wrapper.zone == null || wrapper.zone.level == null
        ? -1 : wrapper.zone.level.Id;
  }

  /** Mirrors D2MOO's total-exp bonus and level-weighted party share. */
  static long computeNativePartyShare(int defenderExperience, int memberLevel,
      int memberCount, int levelSum) {
    if (memberCount <= 1 || levelSum <= 0) return defenderExperience;
    long total = defenderExperience
        + (long) PARTY_EXP_BONUS_NUMERATOR * defenderExperience * (memberCount - 1)
            / PARTY_EXP_BONUS_DENOMINATOR;
    return (long) ((double) memberLevel * total / (double) levelSum);
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
    addExperienceForHireling(playerData, -1, hirelingLevel, defenderLevel,
        defenderExp, isKiller);
  }

  private void addExperienceForHireling(CharData playerData, int hirelingId,
      int hirelingLevel, int defenderLevel, int defenderExp, boolean isKiller) {
    addExperienceForHireling(playerData, hirelingId, hirelingLevel,
        defenderLevel, defenderExp, isKiller, 0);
  }

  private void addExperienceForHireling(CharData playerData, int hirelingId,
      int hirelingLevel, int defenderLevel, int defenderExp, boolean isKiller,
      int addExperiencePercent) {
    if (playerData == null || !playerData.hasMerc()) return;
    int playerLevel = Math.max(1,
        getInt(playerData.getStats().aggregate(), Stat.level, playerData.level & 0xFF));
    // SUNITDMG_AddExperienceForHireling refuses a new award once the hireling
    // has already reached its owner's level.
    if (hirelingLevel >= playerLevel) return;
    // D2Game caps hirelings one level below DATATBLS_GetMaxLevel. Unlike
    // players, a level-98 hireling must not continue accumulating experience.
    if (hirelingLevel >= Math.max(1, expTable.getMaxLevel(0) - 1)) return;

    // 计算佣兵应得经验
    long baseExp = computeExperienceGain(
        (byte) 0, hirelingLevel, defenderLevel, defenderExp, addExperiencePercent);
    if (hirelingExperience != null && hirelingId >= 0 && hirelingExperience.size() > 0) {
      long maximumAward = hirelingExperience.maximumAward(hirelingId, hirelingLevel);
      // Native code clamps even to zero when the selected record has no span.
      baseExp = Math.min(baseExp, maximumAward);
    }
    baseExp = computeNativeHirelingAward(baseExp, isKiller);
    
    if (baseExp <= 0) return;

    CharData.MercData mercData = playerData.getMerc();
    long oldExperience = Math.max(0L, mercData.xp);
    long newExperience = Math.min(0xFFFFFFFFL, oldExperience + baseExp);
    mercData.xp = newExperience;
    int encodedExperience = (int) Math.min(Integer.MAX_VALUE, newExperience);
    int encodedGain = (int) Math.min(Integer.MAX_VALUE, newExperience - oldExperience);
    mercData.getStats().base().put(Stat.experience, encodedExperience);
    mercData.getStats().base().put(Stat.lastexp, encodedGain);
    mercData.getStats().aggregate().put(Stat.experience, encodedExperience);
    mercData.getStats().aggregate().put(Stat.lastexp, encodedGain);
    log.info("[XP_MERC] owner={} level={} killer={} gained={} total={}",
        playerData.name, hirelingLevel, isKiller, encodedGain, newExperience);
  }

  static long computeNativeHirelingAward(long experienceGain, boolean isKiller) {
    if (experienceGain <= 0) return 0;
    return isKiller ? experienceGain
        : experienceGain * HIRELING_EXP_NUMERATOR / HIRELING_EXP_DENOMINATOR;
  }

  private void awardHirelingExperience(int ownerId, int killerId,
      int defenderLevel, int defenderExp) {
    int hirelingId = findLivingHireling(ownerId);
    if (hirelingId < 0 || !mPlayer.has(ownerId) || mPlayer.get(ownerId).data == null) return;
    Mercenary hireling = mMercenary.get(hirelingId);
    int hirelingLevel = Math.max(1, hireling.level);
    if (mAttributesWrapper.has(hirelingId)) {
      Attributes attrs = mAttributesWrapper.get(hirelingId).attrs;
      if (attrs != null) {
        hirelingLevel = Math.max(1,
            getInt(attrs.aggregate(), Stat.level, hirelingLevel));
      }
    }

    CharData owner = mPlayer.get(ownerId).data;
    long oldExperience = owner.getMerc().xp;
    int addExperiencePercent = 0;
    if (mAttributesWrapper.has(hirelingId)) {
      Attributes attrs = mAttributesWrapper.get(hirelingId).attrs;
      if (attrs != null) {
        addExperiencePercent = getInt(
            attrs.aggregate(), Stat.item_addexperience, 0);
      }
    }
    addExperienceForHireling(owner, hireling.mercType, hirelingLevel,
        defenderLevel, defenderExp, killerId == hirelingId, addExperiencePercent);
    long newExperience = owner.getMerc().xp;
    if (newExperience == oldExperience) return;

    int ownerLevel = Math.max(1,
        getInt(owner.getStats().aggregate(), Stat.level, owner.level & 0xFF));
    int newLevel = hirelingExperience == null ? hirelingLevel
        : hirelingExperience.levelForExperience(hireling.mercType, hirelingLevel,
            newExperience, ownerLevel);
    if (newLevel > hirelingLevel) {
      hireling.level = newLevel;
      com.riiablo.engine.server.NativeHirelingExperienceTable.Stats nativeStats =
          hirelingExperience == null ? null
              : hirelingExperience.stats(hireling.mercType, newLevel);
      com.riiablo.engine.server.NativeHirelingStatsUpdater.apply(
          owner.getMerc().getStats(), nativeStats);
      owner.getMerc().getItems().updateStats();
      com.riiablo.engine.server.NativeHirelingStatsUpdater.applySkills(hireling, nativeStats);
      log.info("[XP_MERC_LEVEL] owner={} merc={} level={}->{} xp={}",
          owner.name, hirelingId, hirelingLevel, newLevel, newExperience);
    }
    if (mercenaryRewards != null
        && !mercenaryRewards.synchronizeMercenaryProgress(
            ownerId, hirelingId, newExperience, newLevel)) {
      log.warn("[XP_MERC] owner={} merc={} failed to synchronize lifecycle progress",
          ownerId, hirelingId);
    }
    if (!mAttributesWrapper.has(hirelingId)) return;

    Attributes attrs = mAttributesWrapper.get(hirelingId).attrs;
    if (attrs == null) return;
    int encodedExperience = (int) Math.min(Integer.MAX_VALUE, newExperience);
    int encodedGain = (int) Math.min(Integer.MAX_VALUE, newExperience - oldExperience);
    attrs.base().put(Stat.experience, encodedExperience);
    attrs.base().put(Stat.lastexp, encodedGain);
    attrs.aggregate().put(Stat.experience, encodedExperience);
    attrs.aggregate().put(Stat.lastexp, encodedGain);
    if (newLevel > hirelingLevel) {
      // Restored and newly hired mercenaries share the persistent mercenary
      // Attributes instance, including equipment modifiers. Avoid applying
      // native base values to that same aggregate a second time.
      if (attrs != owner.getMerc().getStats()) {
        com.riiablo.engine.server.NativeHirelingStatsUpdater.apply(attrs,
            hirelingExperience == null ? null
                : hirelingExperience.stats(hireling.mercType, newLevel));
      }
      // Applying native base stats rebuilds aggregate stats; restore the
      // experience values written by this award afterwards.
      attrs.base().put(Stat.experience, encodedExperience);
      attrs.base().put(Stat.lastexp, encodedGain);
      attrs.aggregate().put(Stat.experience, encodedExperience);
      attrs.aggregate().put(Stat.lastexp, encodedGain);
    }
  }

  private int findLivingHireling(int ownerId) {
    if (mercenaries == null || mMercenary == null) return -1;
    com.artemis.utils.IntBag ids = mercenaries.getEntities();
    int[] data = ids.getData();
    for (int i = 0; i < ids.size(); i++) {
      int id = data[i];
      Mercenary mercenary = mMercenary.get(id);
      if (mercenary != null && mercenary.ownerId == ownerId
          && (mCorpse == null || !mCorpse.has(id))) return id;
    }
    return -1;
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
