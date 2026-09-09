package com.riiablo.engine.server;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.ai.utils.Collision;
import com.badlogic.gdx.ai.utils.Ray;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;

import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.NativeStatResolver;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.engine.server.party.PvpCombatRules;
import com.riiablo.engine.server.skill.AuraManager;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.Map;
import com.riiablo.map.DT1;
import net.mostlyoriginal.api.event.common.EventSystem;

/** Bridges native Paladin aura pulses to authoritative ECS state snapshots. */
@Wire(failOnNull = false)
public class AuraEcsSystem extends BaseSystem implements AuraManager.AuraCallback {
  private static final Logger log = LogManager.getLogger(AuraEcsSystem.class);

  private final AuraManager auras = new AuraManager();
  private final IntMap<NativeRng> damageRng = new IntMap<>();
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Mercenary> mMercenary;
  protected ComponentMapper<SummonedPet> mSummonedPet;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<AttributesWrapper> mAttributes;
  @Wire(name = "partyManager", failOnNull = false)
  protected PartyManager partyManager;
  @Wire(failOnNull = false)
  protected EventSystem events;
  @Wire(name = "map", failOnNull = false)
  protected Map map;
  @Wire(failOnNull = false)
  protected StateUpdater stateUpdater;

  @Override protected void initialize() {
    auras.setCallback(this);
  }

  @Override protected void processSystem() {
    // This system is part of the fixed 25 Hz authoritative world. One Artemis
    // process call is one native game frame; render delta is intentionally not
    // used for aura cadence.
    auras.update(world.delta);
  }

  public boolean selectAura(int entityId, int skillId) {
    if (!mPlayer.has(entityId)) return false;
    Player player = mPlayer.get(entityId);
    int level = player.data != null ? player.data.getSkill(skillId) : 0;
    if (mUnitStates.has(entityId) && mUnitStates.get(entityId).stateList != null) {
      level += mUnitStates.get(entityId).stateList.getTotalSkillModifier();
    }
    if (level <= 0) return false;
    boolean activated = auras.activateAura(entityId, skillId, level);
    if (activated) {
      log.info("[AURA] phase=activate caster={} skill={} level={} status=PASS",
          entityId, skillId, level);
    }
    return activated;
  }

  public AuraManager manager() {
    return auras;
  }

  public void clearAura(int entityId) {
    auras.deactivateAura(entityId);
  }

  @Override public void onAuraActivated(int casterId, int skillId, int skillLevel) {}

  @Override public void onAuraDeactivated(int casterId, int skillId) {
    log.info("[AURA] phase=deactivate caster={} skill={}", casterId, skillId);
  }

  @Override public void onEntityEnterAura(
      int entityId, int casterId, int skillId, int[] values) {
    log.debug("[AURA] phase=enter entity={} caster={} skill={}",
        entityId, casterId, skillId);
  }

  @Override public void onEntityLeaveAura(int entityId, int casterId, int skillId) {
    log.debug("[AURA] phase=leave entity={} caster={} skill={}",
        entityId, casterId, skillId);
  }

  @Override public float[] getEntityPosition(int entityId) {
    if (!mPosition.has(entityId) || !isAlive(entityId)) return null;
    Vector2 position = mPosition.get(entityId).position;
    return new float[] {position.x, position.y};
  }

  @Override public Array<Integer> getEntitiesInRange(float x, float y, float range) {
    Array<Integer> result = new Array<>();
    IntBag entities = world.getAspectSubscriptionManager()
        .get(Aspect.all(Position.class, AttributesWrapper.class)).getEntities();
    float range2 = range * range;
    for (int i = 0; i < entities.size(); i++) {
      int id = entities.get(i);
      if (mPosition.has(id) && mPosition.get(id).position.dst2(x, y) <= range2) result.add(id);
    }
    return result;
  }

  @Override public boolean isAlly(int first, int second) {
    if (first == second) return true;
    boolean firstAligned = isPlayerAligned(first);
    boolean secondAligned = isPlayerAligned(second);
    if (!firstAligned || !secondAligned) return !firstAligned && !secondAligned;
    int firstOwner = alignmentOwner(first);
    int secondOwner = alignmentOwner(second);
    if (firstOwner == secondOwner) return true;
    return partyManager != null && partyManager.areInSameParty(firstOwner, secondOwner);
  }

  @Override public int getBaseSkillLevel(int entityId, String skillName) {
    if (!mPlayer.has(entityId) || mPlayer.get(entityId).data == null
        || skillName == null || Riiablo.files == null || Riiablo.files.skills == null) return 0;
    com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(skillName);
    return skill == null ? 0 : Math.max(0,
        mPlayer.get(entityId).data.getBaseSkillLevel(skill.Id));
  }

  @Override public boolean isValidTarget(
      int casterId, int targetId, int auraFilter, boolean checkMonsterNoAura) {
    if (!mPosition.has(targetId) || !mAttributes.has(targetId) || !isAlive(targetId)) return false;
    boolean player = mPlayer.has(targetId);
    boolean monster = mMonster.has(targetId);
    if (player && (auraFilter & AuraManager.FILTER_PLAYER) == 0) return false;
    if (monster && (auraFilter & AuraManager.FILTER_MONSTER) == 0) return false;
    if (!player && !monster) return false;
    if (!sameZone(casterId, targetId)) return false;
    if (monster) {
      Monster unit = mMonster.get(targetId);
      if (unit.monstats != null && unit.monstats.npc) return false;
      if (checkMonsterNoAura && unit.monstats != null && unit.monstats.noAura) return false;
      if ((auraFilter & AuraManager.FILTER_CAN_BE_ATTACKED) != 0
          && unit.monstats2 != null && !unit.monstats2.isAtt) return false;
      if ((auraFilter & AuraManager.FILTER_SELECTABLE) != 0
          && unit.monstats2 != null && unit.monstats2.noSel) return false;
    }
    if ((auraFilter & (AuraManager.FILTER_IGNORE_IN_TOWN
        | AuraManager.FILTER_IGNORE_TOWN_ROOMS)) != 0 && isInTown(targetId)) return false;
    if ((auraFilter & AuraManager.FILTER_USE_LINE_OF_SIGHT) != 0
        && !hasLineOfSight(casterId, targetId)) return false;
    if ((auraFilter & AuraManager.FILTER_FIND_ALLY) != 0
        && !isAlly(casterId, targetId)) return false;
    if ((auraFilter & AuraManager.FILTER_IGNORE_ALLY) != 0
        && !isHostile(casterId, targetId)) return false;
    return true;
  }

  @Override public boolean isInTown(int entityId) {
    Map.Zone zone = zone(entityId);
    return zone != null && zone.isTown();
  }

  @Override public boolean consumeMana(int casterId, float amount) {
    if (amount <= 0f) return true;
    if (!mAttributes.has(casterId)) return false;
    Attributes attrs = mAttributes.get(casterId).attrs;
    StatRef mana = attrs != null ? attrs.get(Stat.mana, StatRef.obtain()) : null;
    if (mana == null || mana.asFixed() < amount) return false;
    mana.sub(amount);
    return true;
  }

  @Override public void applyState(int targetId, int stateId, int duration,
      int sourceEntityId, int skillId, int skillLevel,
      int[] statIds, int[] values) {
    if (!mUnitStates.has(targetId)) return;
    UnitStates states = mUnitStates.get(targetId);
    if (states.stateList == null) states.init(targetId);
    UnitState state = states.stateList.addStateLayer(
        stateId, duration, skillLevel, sourceEntityId, skillId);
    if (state == null) return;
    state.duration = duration;
    state.initialDuration = duration;
    state.level = skillLevel;
    state.sourceEntityId = sourceEntityId;
    state.skillId = skillId;
    state.needsSync = true;
    state.clearModifiers();
    if (values != null && statIds != null) {
      for (int i = 0; i < values.length && i < statIds.length; i++) {
        int statId = statIds[i];
        if (statId < 0 || values[i] == 0 || statId == Stat.hitpoints || statId == Stat.mana) {
          continue;
        }
        state.addStatContribution(statId, 0, NativeStatResolver.Operation.ADD, values[i]);
      }
    }
    if (stateUpdater != null) stateUpdater.refreshAuraVelocity(targetId);
    log.debug("[AURA] phase=apply entity={} state={} source={} skill={} level={} duration={}",
        targetId, stateId, sourceEntityId, skillId, skillLevel, duration);
  }

  @Override public void removeState(
      int targetId, int stateId, int sourceEntityId, int skillId) {
    if (mUnitStates.has(targetId) && mUnitStates.get(targetId).stateList != null) {
      mUnitStates.get(targetId).stateList.removeStateLayer(stateId, sourceEntityId, skillId);
      if (stateUpdater != null) stateUpdater.refreshAuraVelocity(targetId);
    }
  }

  @Override public void applyDirectStat(int targetId, int statId, int fixedValue,
      int sourceEntityId, int skillId) {
    if (fixedValue <= 0 || !mAttributes.has(targetId)) return;
    Attributes attrs = mAttributes.get(targetId).attrs;
    if (attrs == null) return;
    if (statId == Stat.hitpoints) {
      StatRef current = attrs.get(Stat.hitpoints, StatRef.obtain());
      StatRef maximum = attrs.get(Stat.maxhp, StatRef.obtain());
      if (current == null || maximum == null || current.asFixed() <= 0f) return;
      float requested = fixedValue / 256f;
      float restored = Math.min(requested, Math.max(0f, maximum.asFixed() - current.asFixed()));
      if (restored > 0f) current.add(restored);
      log.debug("[AURA] phase=direct_heal target={} source={} skill={} requested={} restored={}",
          targetId, sourceEntityId, skillId, requested, restored);
    }
  }

  @Override public void applyPeriodicDamage(int casterId, int targetId, int skillId,
      int skillLevel, int minimum, int maximum, String elementType) {
    if (minimum < 0 || maximum <= 0 || !mAttributes.has(targetId) || !isAlive(targetId)) return;
    Attributes target = mAttributes.get(targetId).attrs;
    if (target == null) return;
    NativeRng rng = damageRng.get(casterId);
    if (rng == null) {
      rng = NativeRng.forUnit(Riiablo.gameSeed ^ skillId, casterId);
      damageRng.put(casterId, rng);
    }
    int raw = minimum + rng.nextInt(Math.max(1, maximum - minimum + 1));
    int damageType = elementalType(elementType);
    StateList targetStates = mUnitStates.has(targetId)
        ? mUnitStates.get(targetId).stateList : null;
    CombatSystem.CombatResult combat = CombatSystem.INSTANCE.calculateFixedElementalDamage(
        target, isPlayerAligned(targetId), isPlayerAligned(casterId),
        damageType, raw, 0, targetStates, map != null ? map.getDifficulty() : 0);
    restoreAbsorb(target, combat.absorbedLife);
    if (combat.totalDamage <= 0) return;
    DamageEvent event = DamageEvent.obtain(casterId, targetId, combat.totalDamage);
    if (events != null) events.dispatch(event);
    StatRef hp = target.get(Stat.hitpoints, StatRef.obtain());
    if (hp == null || event.damage <= 0f) return;
    hp.sub(Math.max(0f, event.damage));
    if (hp.asFixed() <= 0f) {
      hp.set(0f);
      if (events != null) events.dispatch(DeathEvent.obtain(casterId, targetId));
    }
    log.info("[AURA_DAMAGE] caster={} target={} skill={} level={} raw={} applied={}",
        casterId, targetId, skillId, skillLevel, raw, event.damage);
  }

  private boolean isAlive(int entityId) {
    if (!mAttributes.has(entityId)) return false;
    Attributes attrs = mAttributes.get(entityId).attrs;
    StatRef life = attrs != null ? attrs.get(Stat.hitpoints, StatRef.obtain()) : null;
    return life != null && life.asFixed() > 0f;
  }

  private boolean sameZone(int first, int second) {
    Map.Zone firstZone = zone(first);
    Map.Zone secondZone = zone(second);
    return firstZone == null || secondZone == null || firstZone == secondZone;
  }

  private boolean hasLineOfSight(int first, int second) {
    if (!mPosition.has(first) || !mPosition.has(second)) return false;
    Map currentMap = entityMap(first);
    if (currentMap == null || currentMap.getZone(mPosition.get(first).position) == null) {
      // Synthetic tests and entities still being inserted have no collision
      // room. Native performs LOS only after room insertion, so do not reject
      // them solely because the map reference is not ready.
      return true;
    }
    Ray<Vector2> ray = new Ray<>(
        new Vector2(mPosition.get(first).position),
        new Vector2(mPosition.get(second).position));
    Collision<Vector2> collision = new Collision<>(new Vector2(), new Vector2());
    return !currentMap.castRay(ray, DT1.Tile.FLAG_BLOCK_JUMP, 0, collision);
  }

  private Map entityMap(int entityId) {
    if (mMapWrapper.has(entityId) && mMapWrapper.get(entityId).map != null) {
      return mMapWrapper.get(entityId).map;
    }
    Map.Zone zone = zone(entityId);
    return zone != null && zone.map != null ? zone.map : map;
  }

  private Map.Zone zone(int entityId) {
    if (mMapWrapper.has(entityId) && mMapWrapper.get(entityId).zone != null) {
      return mMapWrapper.get(entityId).zone;
    }
    return map != null && mPosition.has(entityId)
        ? map.getZone(mPosition.get(entityId).position) : null;
  }

  private boolean isPlayerAligned(int entityId) {
    return mPlayer.has(entityId) || mMercenary.has(entityId) || mSummonedPet.has(entityId);
  }

  private int alignmentOwner(int entityId) {
    if (mMercenary.has(entityId)) return mMercenary.get(entityId).ownerId;
    if (mSummonedPet.has(entityId)) return mSummonedPet.get(entityId).ownerId;
    return entityId;
  }

  private boolean isHostile(int sourceId, int targetId) {
    boolean sourceAligned = isPlayerAligned(sourceId);
    boolean targetAligned = isPlayerAligned(targetId);
    if (sourceAligned != targetAligned) return true;
    if (!sourceAligned) return false;
    return PvpCombatRules.canDamage(partyManager,
        alignmentOwner(sourceId), alignmentOwner(targetId), true, true);
  }

  private static int elementalType(String element) {
    if (element == null) return CombatSystem.DAMAGE_MAGIC;
    switch (element.trim().toLowerCase(java.util.Locale.ROOT)) {
      case "fire": return CombatSystem.DAMAGE_FIRE;
      case "ltng":
      case "lightning": return CombatSystem.DAMAGE_LIGHTNING;
      case "cold": return CombatSystem.DAMAGE_COLD;
      case "pois":
      case "poison": return CombatSystem.DAMAGE_POISON;
      default: return CombatSystem.DAMAGE_MAGIC;
    }
  }

  private static void restoreAbsorb(Attributes attributes, int absorbed) {
    if (attributes == null || absorbed <= 0) return;
    StatRef hp = attributes.get(Stat.hitpoints, StatRef.obtain());
    StatRef max = attributes.get(Stat.maxhp, StatRef.obtain());
    if (hp != null && max != null && hp.asFixed() > 0f) {
      hp.add(Math.min(absorbed, Math.max(0f, max.asFixed() - hp.asFixed())));
    }
  }
}
