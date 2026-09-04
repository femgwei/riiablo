package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.Aspect;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.artemis.utils.IntBag;

import com.badlogic.gdx.math.Vector2;

import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.NativeTargeting;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.combat.StatusEffectApplier;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.item.ItemDurabilityManager;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.engine.server.party.PvpCombatRules;
import com.riiablo.engine.server.skill.AssassinSkills;
import com.riiablo.codec.excel.Skills;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import net.mostlyoriginal.api.event.common.EventSystem;

/**
 * 状态更新系统 - 基于 D2MOD 状态处理逻辑移植
 * 
 * <p>该系统每帧更新所有单位的状态，处理：
 * <ul>
 *   <li>状态持续时间倒计时</li>
 *   <li>过期状态移除</li>
 *   <li>持续伤害（DOT）效果</li>
 *   <li>状态效果应用（减速、眩晕等）</li>
 * </ul>
 * 
 * <p>参考：D2MOD/source/D2Common/src/D2States.cpp
 * 
 * @author riiablo team
 */
@All(UnitStates.class)
public class StateUpdater extends IteratingSystem implements StatusEffectApplier.StateSink {
  private static final Logger log = LogManager.getLogger(StateUpdater.class);

  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Mercenary> mMercenary;
  protected ComponentMapper<SummonedPet> mSummonedPet;
  protected ComponentMapper<NativeUnitFlags> mNativeUnitFlags;

  @Wire(name = "partyManager", failOnNull = false)
  protected PartyManager partyManager;

  @Wire(name = "map", failOnNull = false)
  protected Map map;

  @Wire(name = "factory", failOnNull = false)
  protected EntityFactory factory;

  protected EventSystem events;

  @Override
  protected void initialize() {
    super.initialize();
    StatusEffectApplier.INSTANCE.setStateSink(this);
  }

  //==========================================================================
  // 系统处理
  //==========================================================================

  @Override
  protected void process(int entityId) {
    UnitStates unitStates = mUnitStates.get(entityId);
    if (unitStates == null || unitStates.stateList == null) {
      return;
    }

    // Network clients render the server snapshot. They must not independently
    // tick DOT, expire states, or emit DeathEvent and rewards a second time.
    if (unitStates.snapshotOnly) {
      if (mVelocity.has(entityId)) applyVelocityModifiers(entityId, unitStates.stateList);
      return;
    }

    StateList stateList = unitStates.stateList;

    processBladeShield(entityId, stateList);
    processSpiderLayTrail(entityId, stateList);
    
    // Resolve this tick before decrementing duration. A one-frame state must
    // still deal its final DOT tick, then expire.
    processDamageOverTime(entityId, stateList);
    stateList.update();

    // Apply movement/control effects only while the state remains active.
    if (mVelocity.has(entityId)) {
      applyVelocityModifiers(entityId, stateList);
    }
  }

  //==========================================================================
  // 状态效果应用
  //==========================================================================

  /**
   * 应用移动速度修正
   * 
   * @param entityId 实体ID
   * @param stateList 状态列表
   */
  private void applyVelocityModifiers(int entityId, StateList stateList) {
    Velocity velocity = mVelocity.get(entityId);

    // Keep the desired velocity untouched. VelocityAdder applies this state
    // multiplier after Pathfinder has selected the direction for this tick.
    velocity.stateSpeedMultiplier = 1f;
    velocity.stateMovementLocked = false;
    
    // 检查冰冻状态 - 完全停止移动
    if (stateList.hasState(StateId.FREEZE)) {
      velocity.stateMovementLocked = true;
      return;
    }
    
    // 检查眩晕状态 - 完全停止移动
    if (stateList.hasState(StateId.STUNNED)) {
      velocity.stateMovementLocked = true;
      return;
    }
    
    // 计算减速效果
    int slowPercent = 0;

    // Frenzy velocitypercent has already been evaluated from AuraStatCalc at
    // its native runtime stack. It is not a fixed percentage per hit.
    int frenzyPercent = 0;
    UnitState frenzy = stateList.getState(StateId.FRENZY);
    if (frenzy == null) frenzy = stateList.getState(StateId.MONFRENZY);
    if (frenzy != null) {
      frenzyPercent = Math.min(200, Math.max(0, frenzy.velocityModifier));
    }
    
    // 寒冷减速
    if (stateList.hasState(StateId.COLD)) {
      UnitState coldState = stateList.getState(StateId.COLD);
      slowPercent += 30 + coldState.level * 2; // 基础30% + 等级加成
    }
    
    // 减速状态
    if (stateList.hasState(StateId.SLOWED)) {
      UnitState slowState = stateList.getState(StateId.SLOWED);
      slowPercent += 25 + slowState.level * 5;
    }
    
    // 衰老诅咒
    if (stateList.hasState(StateId.DECREPIFY)) {
      slowPercent += 50;
    }
    
    // 应用减速（限制最大减速为90%）
    if (slowPercent > 0) {
      slowPercent = Math.min(slowPercent, 90);
      velocity.stateSpeedMultiplier = 1.0f - (slowPercent / 100.0f);
    }
    if (frenzyPercent > 0 && !velocity.stateMovementLocked) {
      velocity.stateSpeedMultiplier *= 1.0f + frenzyPercent / 100.0f;
    }
    int auraVelocityPercent = stateList.getTotalVelocityModifier();
    if (auraVelocityPercent != 0 && !velocity.stateMovementLocked) {
      // Aura velocity modifiers are percentages; unlike Frenzy they are not
      // stack counts and are therefore applied independently.
      auraVelocityPercent = Math.max(-90, Math.min(200, auraVelocityPercent));
      velocity.stateSpeedMultiplier *= 1.0f + auraVelocityPercent / 100.0f;
    }
  }

  /**
   * 处理持续伤害效果
   * 
   * @param entityId 实体ID
   * @param stateList 状态列表
   */
  private void processDamageOverTime(int entityId, StateList stateList) {
    // 处理中毒
    if (stateList.hasState(StateId.POISON)) {
      UnitState poisonState = stateList.getState(StateId.POISON);
      if (poisonState.damagePerFrame > 0) {
        applyDamageOverTime(entityId, poisonState.sourceEntityId,
            poisonState.damagePerFrame, stateList, StateId.POISON);
      }
    }
    
    // 处理燃烧
    if (stateList.hasState(StateId.BURNING)) {
      UnitState burningState = stateList.getState(StateId.BURNING);
      if (burningState.damagePerFrame > 0) {
        applyDamageOverTime(entityId, burningState.sourceEntityId,
            burningState.damagePerFrame, stateList, StateId.BURNING);
      }
    }
    
    // 处理撕开伤口
    if (stateList.hasState(StateId.OPENWOUNDS)) {
      UnitState woundsState = stateList.getState(StateId.OPENWOUNDS);
      // 撕开伤口伤害基于角色等级
      int damage = woundsState.level * 2;
      if (damage > 0) {
        applyDamageOverTime(entityId, woundsState.sourceEntityId,
            damage, stateList, StateId.OPENWOUNDS);
      }
    }
  }

  /** D2MOO EVENTTYPE_PERIODICSKILLS -> SrvDo054 -> SrvDo142. */
  private void processBladeShield(int entityId, StateList states) {
    UnitState state = states.getState(StateId.BLADESHIELD);
    if (state == null || state.periodicCountdownFrames < 0) return;
    if (state.periodicCountdownFrames > 0) {
      state.periodicCountdownFrames--;
      if (state.periodicCountdownFrames > 0) return;
    }
    Skills.Entry skill = Riiablo.files.skills.get(state.skillId);
    if (skill == null || skill.srvdofunc != 54 || !isAlive(entityId)
        || !stillOwnsSkill(entityId, state.skillId)) {
      state.expired = true;
      log.info("[ASSASSIN_BLADE_SHIELD] phase=periodic_stop entity={} skill={} reason=invalid_owner",
          entityId, state.skillId);
      return;
    }
    state.periodicCountdownFrames = Math.max(5, state.periodicDelayFrames);
    Map.Zone zone = map != null && mPosition.has(entityId)
        ? map.getZone(mPosition.get(entityId).position) : null;
    if (zone != null && zone.isTown()) {
      log.debug("[ASSASSIN_BLADE_SHIELD] phase=pulse_skip entity={} reason=town", entityId);
      return;
    }
    int level = Math.max(1, state.level);
    int range = AssassinSkills.bladeShieldRange(skill, level);
    int[] skillDamage = AssassinSkills.bladeShieldDamageRange(skill, level);
    if (range <= 0 || !mPosition.has(entityId) || !mAttributesWrapper.has(entityId)) return;
    Attributes source = mAttributesWrapper.get(entityId).attrs;
    if (source == null) return;
    Vector2 origin = mPosition.get(entityId).position;
    float range2 = range * (float) range;
    int affected = 0;
    IntBag candidates = world.getAspectSubscriptionManager()
        .get(Aspect.all(Position.class, AttributesWrapper.class)).getEntities();
    for (int i = 0; i < candidates.size(); i++) {
      int targetId = candidates.get(i);
      if (targetId == entityId || !isAlive(targetId)
          || origin.dst2(mPosition.get(targetId).position) > range2
          || !isHostile(entityId, targetId)
          || mNativeUnitFlags.has(targetId)
              && !NativeTargeting.isValidCombatTarget(mNativeUnitFlags.get(targetId))) continue;
      Attributes target = mAttributesWrapper.get(targetId).attrs;
      StateList targetStates = null;
      if (mUnitStates.has(targetId)) {
        UnitStates targetUnitStates = mUnitStates.get(targetId);
        if (targetUnitStates.stateList == null) targetUnitStates.init(targetId);
        targetStates = targetUnitStates.stateList;
      }
      int toHitPercent = skill.ToHit + Math.max(0, level - 1) * skill.LevToHit;
      boolean alwaysHit = (skill.ResultFlags & 1) != 0;
      CombatSystem.CombatResult combat = CombatSystem.INSTANCE.calculateBladeShieldAttack(
          source, target, isPlayerAligned(entityId), isPlayerAligned(targetId),
          skillDamage[0], skillDamage[1], skill.SrcDam, toHitPercent, alwaysHit,
          states, targetStates, isMoving(targetId));
      if (!combat.hit || combat.blocked) continue;
      StatRef hp = target.get(Stat.hitpoints, StatRef.obtain());
      if (hp == null || hp.asFixed() <= 0f) continue;
      float requested = Math.max(0f, combat.totalDamage);
      if (requested > 0f) {
        DamageEvent damage = DamageEvent.obtain(entityId, targetId, requested);
        if (events != null) events.dispatch(damage);
        hp.sub(Math.max(0f, damage.damage));
        if (hp.asFixed() < 0f) hp.set(0f);
      }
      if (combat.poisonDuration > 0
          && combat.elementalDamage[CombatSystem.DAMAGE_POISON] > 0
          && mUnitStates.has(targetId)) {
        applyState(targetId, StateId.POISON, combat.poisonDuration, 1, entityId,
            combat.elementalDamage[CombatSystem.DAMAGE_POISON], CombatSystem.DAMAGE_POISON);
      }
      if (combat.coldDuration > 0
          && combat.elementalDamage[CombatSystem.DAMAGE_COLD] > 0
          && mUnitStates.has(targetId)) {
        UnitState cold = targetStates.addState(
            StateId.COLD, combat.coldDuration, 1, entityId);
        if (cold != null) cold.needsSync = true;
      }
      drainBladeShieldDurability(entityId, targetId);
      affected++;
      if (hp.asFixed() <= 0f && events != null) {
        hp.set(0f);
        events.dispatch(DeathEvent.obtain(entityId, targetId));
      }
    }
    log.info("[ASSASSIN_BLADE_SHIELD] phase=pulse entity={} skill={} level={} range={} "
            + "damage={}..{} srcDam={} affected={}",
        entityId, skill.Id, level, range, skillDamage[0], skillDamage[1],
        skill.SrcDam, affected);
  }

  private boolean stillOwnsSkill(int entityId, int skillId) {
    if (!mPlayer.has(entityId)) return true;
    Player player = mPlayer.get(entityId);
    return player.data != null && player.data.getSkill(skillId) > 0;
  }

  private boolean isAlive(int entityId) {
    if (!mAttributesWrapper.has(entityId)) return false;
    Attributes attrs = mAttributesWrapper.get(entityId).attrs;
    if (attrs == null) return false;
    StatRef hp = attrs.get(Stat.hitpoints, StatRef.obtain());
    return hp != null && hp.asFixed() > 0f;
  }

  private boolean isMoving(int entityId) {
    return mVelocity.has(entityId) && !mVelocity.get(entityId).velocity.isZero(0.0001f);
  }

  private boolean isPlayerAligned(int entityId) {
    return mPlayer.has(entityId) || mMercenary.has(entityId) || mSummonedPet.has(entityId);
  }

  private boolean isHostile(int sourceId, int targetId) {
    if (mMonster.has(targetId) && !mMercenary.has(targetId)
        && !mSummonedPet.has(targetId)) return isPlayerAligned(sourceId);
    return PvpCombatRules.canDamage(partyManager, sourceId, targetId,
        isPlayerAligned(sourceId), isPlayerAligned(targetId));
  }

  private void drainBladeShieldDurability(int sourceId, int targetId) {
    if (mPlayer.has(sourceId) && mPlayer.get(sourceId).data != null) {
      Item weapon = mPlayer.get(sourceId).data.getItems().getEquipped(BodyLoc.RARM);
      if (weapon == null) {
        weapon = mPlayer.get(sourceId).data.getItems().getEquipped(BodyLoc.LARM);
      }
      ItemDurabilityManager.INSTANCE.drainWeaponDurability(weapon, true);
    }
    if (mPlayer.has(targetId) && mPlayer.get(targetId).data != null) {
      ItemDurabilityManager.INSTANCE.drainArmorDurability(
          mPlayer.get(targetId).data.getItems());
    }
  }

  /** Applies one server tick of DOT and emits the normal damage/death events. */
  private void applyDamageOverTime(int entityId, int sourceEntityId, float damage,
      StateList stateList, int stateId) {
    if (damage <= 0 || !mAttributesWrapper.has(entityId)) return;
    if (mPlayer.has(sourceEntityId) && mPlayer.has(entityId)
        && !PvpCombatRules.canDamage(partyManager, sourceEntityId, entityId, true, true)) {
      // Hostility may be removed while poison/open-wounds is active.  Native
      // friendly checks must still prevent later DOT ticks from bypassing the
      // current authoritative relation.
      log.info("[PVP] phase=dot_reject source={} target={} state={} reason=not_hostile",
          sourceEntityId, entityId, StateId.getName(stateId));
      return;
    }
    Attributes attrs = mAttributesWrapper.get(entityId).attrs;
    if (attrs == null) return;
    StatRef hitpoints = attrs.get(Stat.hitpoints, StatRef.obtain());
    if (hitpoints == null || hitpoints.asFixed() <= 0f) return;

    DamageEvent event = DamageEvent.obtain(sourceEntityId, entityId, damage);
    if (events != null) events.dispatch(event);
    float appliedDamage = Math.max(0f, event.damage);
    hitpoints.sub(appliedDamage);
    float hpAfter = hitpoints.asFixed();
    if (hpAfter <= 0f) {
      hitpoints.set(0f);
      log.debug("Entity {} died from state {} (damage={})", entityId,
          StateId.getName(stateId), appliedDamage);
      if (events != null) events.dispatch(DeathEvent.obtain(sourceEntityId, entityId));
      // Prevent a dead entity from emitting the same death event every tick.
      stateList.clearAll();
    } else {
      log.trace("Entity {} takes {} damage from state {} (hp={})", entityId,
          appliedDamage, StateId.getName(stateId), hpAfter);
    }
  }

  /** Emits the native SpiderLay movement trail into the authoritative missile pipeline. */
  private void processSpiderLayTrail(int entityId, StateList stateList) {
    if (!stateList.hasState(StateId.SPIDERLAY) || factory == null
        || !mVelocity.has(entityId) || !mPosition.has(entityId)) return;
    Velocity velocity = mVelocity.get(entityId);
    if (velocity.velocity.isZero(0.0001f)) return;
    UnitState state = stateList.getState(StateId.SPIDERLAY);
    int elapsed = Math.max(0, state.initialDuration - state.duration);
    if ((elapsed & 3) != 0) return;
    Missiles.Entry missile = Riiablo.files.Missiles.get("spidergoolay");
    if (missile == null) {
      log.warn("[SPIDER_LAY] phase=reject entity={} reason=missing_spidergoolay", entityId);
      state.expired = true;
      return;
    }
    Vector2 direction = new Vector2(velocity.velocity).nor();
    Vector2 position = new Vector2(mPosition.get(entityId).position).mulAdd(direction, -0.5f);
    int missileId = factory.createMissile(missile, direction, position, entityId);
    log.info("[SPIDER_LAY] phase=missile entity={} skill={} missileId={} position=({}, {})",
        entityId, state.skillId, missileId, position.x, position.y);
  }

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 向实体添加状态（便捷方法）
   * 
   * @param entityId 目标实体ID
   * @param stateId 状态ID
   * @param duration 持续时间（帧数）
   * @param level 状态等级
   * @param sourceId 来源实体ID
   */
  public void addState(int entityId, int stateId, int duration, int level, int sourceId) {
    if (!mUnitStates.has(entityId)) {
      log.warn("实体 {} 没有 UnitStates 组件", entityId);
      return;
    }
    
    UnitStates unitStates = mUnitStates.get(entityId);
    if (unitStates.stateList == null) {
      unitStates.init(entityId);
    }
    
    unitStates.stateList.addState(stateId, duration, level, sourceId);
  }

  @Override
  public void applyState(int entityId, int stateId, int duration, int level,
      int sourceId, int damagePerFrame, int damageType) {
    if (!mUnitStates.has(entityId)) {
      log.warn("Entity {} has no UnitStates component; state {} ignored", entityId, stateId);
      return;
    }
    UnitStates unitStates = mUnitStates.get(entityId);
    if (unitStates.stateList == null) unitStates.init(entityId);
    UnitState existing = unitStates.stateList.getState(stateId);
    if ((stateId == StateId.POISON || stateId == StateId.BURNING)
        && existing != null) {
      // SUNITDMG_ApplyPoisonDamage/ApplyBurnDamage replace an existing DOT
      // only when the new per-frame rate is at least as strong. The new
      // expire frame is assigned directly, so Venom can intentionally
      // shorten an older item poison to its ten-frame override length.
      if (existing.damagePerFrame > damagePerFrame) return;
      existing.duration = Math.max(1, duration);
      existing.initialDuration = existing.duration;
      existing.level = Math.max(1, level);
      existing.sourceEntityId = sourceId;
      existing.damagePerFrame = damagePerFrame;
      existing.damageType = damageType;
      existing.expired = false;
      existing.needsSync = true;
      return;
    }
    UnitState state = unitStates.stateList.addState(stateId, duration, level, sourceId);
    if (state == null) return;
    if (damagePerFrame > state.damagePerFrame) state.damagePerFrame = damagePerFrame;
    state.damageType = damageType;
    state.needsSync = true;
  }

  /**
   * 移除实体的状态
   * 
   * @param entityId 目标实体ID
   * @param stateId 状态ID
   */
  public void removeState(int entityId, int stateId) {
    if (!mUnitStates.has(entityId)) {
      return;
    }
    
    UnitStates unitStates = mUnitStates.get(entityId);
    if (unitStates.stateList != null) {
      unitStates.stateList.removeState(stateId);
    }
  }

  /**
   * 检查实体是否有指定状态
   * 
   * @param entityId 实体ID
   * @param stateId 状态ID
   * @return true 如果有状态
   */
  public boolean hasState(int entityId, int stateId) {
    if (!mUnitStates.has(entityId)) {
      return false;
    }
    
    UnitStates unitStates = mUnitStates.get(entityId);
    return unitStates.stateList != null && unitStates.stateList.hasState(stateId);
  }

  /**
   * 检查实体是否被冰冻
   * 
   * @param entityId 实体ID
   * @return true 如果被冰冻
   */
  public boolean isFrozen(int entityId) {
    return hasState(entityId, StateId.FREEZE);
  }

  /**
   * 检查实体是否被眩晕
   * 
   * @param entityId 实体ID
   * @return true 如果被眩晕
   */
  public boolean isStunned(int entityId) {
    return hasState(entityId, StateId.STUNNED);
  }

  /**
   * 检查实体是否能行动
   * 
   * @param entityId 实体ID
   * @return true 如果能行动
   */
  public boolean canAct(int entityId) {
    return !isFrozen(entityId) && !isStunned(entityId);
  }

  /**
   * 检查实体是否在变形状态
   * 
   * @param entityId 实体ID
   * @return true 如果在变形状态
   */
  public boolean isTransformed(int entityId) {
    if (!mUnitStates.has(entityId)) {
      return false;
    }
    
    UnitStates unitStates = mUnitStates.get(entityId);
    return unitStates.stateList != null && unitStates.stateList.isTransformed();
  }
}
