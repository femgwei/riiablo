package com.riiablo.engine.server.skill;

import java.util.Arrays;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.LongMap;

import com.riiablo.Riiablo;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * Authoritative 25 Hz Paladin aura scheduler.
 *
 * <p>The native game does not keep a permanent, anonymous aura modifier on a
 * target. Every {@code perdelay} pulse posts a short-lived stat-list owned by
 * the caster and skill. Equal levels refresh, weaker levels are rejected and
 * stronger levels replace the current list. This manager performs the same
 * arbitration before asking ECS to publish the winning state layer.</p>
 */
public class AuraManager {
  private static final Logger log = LogManager.getLogger(AuraManager.class);

  public static final int AURA_TYPE_BUFF = 0;
  public static final int AURA_TYPE_DEBUFF = 1;
  public static final int AURA_TYPE_DAMAGE = 2;
  public static final int EXCLUSIVE_NONE = 0;
  public static final int EXCLUSIVE_DEFENSE = 1;
  public static final int EXCLUSIVE_OFFENSE = 2;
  public static final int EXCLUSIVE_CURSE = 3;
  public static final int MAX_AURA_STATS = 6;

  // D2Common D2C_AuraFilters.
  public static final int FILTER_PLAYER = 0x0001;
  public static final int FILTER_MONSTER = 0x0002;
  public static final int FILTER_CAN_BE_ATTACKED = 0x0080;
  public static final int FILTER_IGNORE_IN_TOWN = 0x0100;
  public static final int FILTER_USE_LINE_OF_SIGHT = 0x0200;
  public static final int FILTER_SELECTABLE = 0x0400;
  public static final int FILTER_IGNORE_TOWN_ROOMS = 0x2000;
  public static final int FILTER_IGNORE_ALLY = 0x8000;
  public static final int FILTER_FIND_ALLY = 0x10000;

  public static class AuraDefinition {
    public int skillId;
    public String name;
    public int auraType;
    /** Compatibility alias for the state applied to range targets. */
    public int stateId = -1;
    public int selfStateId = -1;
    public int targetStateId = -1;
    public int exclusiveGroup;
    public float baseRange;
    public float rangePerLevel;
    public float manaCostPerSecond;
    public int perDelayFrames = 50;
    public int auraFilter;
    public final int[] statIds = filledStats();
    public final int[] baseStatValues = new int[MAX_AURA_STATS];
    public final int[] statPerLevel = new int[MAX_AURA_STATS];
    public final int[] passiveStatIds = filledStats();
    public boolean affectsSelf;
    public boolean affectsParty;
    public boolean affectsMercenary;
    public boolean affectsEnemy;
    public Skills.Entry nativeSkill;

    private static int[] filledStats() {
      int[] values = new int[MAX_AURA_STATS];
      Arrays.fill(values, -1);
      return values;
    }
  }

  public static class ActiveAura {
    public AuraDefinition definition;
    public int casterId;
    public int skillLevel;
    public float range;
    public final int[] statValues = new int[MAX_AURA_STATS];
    public final int[] passiveStatValues = new int[MAX_AURA_STATS];
    public Array<Integer> affectedEntities = new Array<>();
    public float lastUpdateTime;
    public float manaCostAccumulator;
    public boolean active;
    long nextPulseFrame;
    boolean pulsed;
  }

  /** Public winner view retained for diagnostics. */
  public static class AuraEffect {
    public int skillId;
    public int casterId;
    public int stateId;
    public int skillLevel;
    public final int[] statValues = new int[MAX_AURA_STATS];
  }

  private static final class Candidate {
    ActiveAura aura;
    int targetId;
    int stateId;
    int[] statIds;
    int[] statValues;
    boolean direct;
  }

  public interface AuraCallback {
    void onAuraActivated(int casterId, int skillId, int skillLevel);
    void onAuraDeactivated(int casterId, int skillId);
    void onEntityEnterAura(int entityId, int casterId, int skillId, int[] statValues);
    void onEntityLeaveAura(int entityId, int casterId, int skillId);
    float[] getEntityPosition(int entityId);
    Array<Integer> getEntitiesInRange(float x, float y, float range);
    boolean isAlly(int entityId1, int entityId2);
    int getBaseSkillLevel(int entityId, String skillName);
    boolean isValidTarget(
        int casterId, int targetId, int auraFilter, boolean checkMonsterNoAura);
    boolean isInTown(int entityId);
    boolean consumeMana(int casterId, float amount);
    void applyState(int targetId, int stateId, int duration, int sourceEntityId,
        int skillId, int skillLevel, int[] statIds, int[] statValues);
    void removeState(int targetId, int stateId, int sourceEntityId, int skillId);
    void applyDirectStat(int targetId, int statId, int fixedValue,
        int sourceEntityId, int skillId);
    void applyPeriodicDamage(int casterId, int targetId, int skillId,
        int skillLevel, int minimum, int maximum, String elementType);
  }

  private final IntMap<AuraDefinition> auraDefinitions = new IntMap<>();
  private final IntMap<ActiveAura> activeAuras = new IntMap<>();
  private final IntMap<Array<AuraEffect>> entityAuraEffects = new IntMap<>();
  private final LongMap<Candidate> winners = new LongMap<>();
  private AuraCallback callback;
  private long gameFrame;

  public AuraManager() {
    registerDefaultAuras();
    registerNativePaladinAuras();
  }

  public boolean activateAura(int casterId, int skillId, int skillLevel) {
    AuraDefinition definition = auraDefinitions.get(skillId);
    if (definition == null || skillLevel <= 0) {
      log.debug("Aura {} not found or has invalid level {}", skillId, skillLevel);
      return false;
    }
    ActiveAura existing = activeAuras.get(casterId);
    if (existing != null) {
      if (existing.definition.skillId == skillId && existing.skillLevel == skillLevel) return true;
      deactivateAura(casterId);
    }
    ActiveAura aura = new ActiveAura();
    aura.definition = definition;
    aura.casterId = casterId;
    aura.skillLevel = Math.max(1, skillLevel);
    aura.range = calculateAuraRange(definition, aura.skillLevel);
    calculateAuraStats(aura);
    aura.active = true;
    // Selection executes the aura once immediately; later executions use the
    // native perdelay cadence in 25 Hz simulation frames.
    aura.nextPulseFrame = gameFrame + 1;
    activeAuras.put(casterId, aura);
    if (callback != null) callback.onAuraActivated(casterId, skillId, aura.skillLevel);
    return true;
  }

  public void deactivateAura(int casterId) {
    ActiveAura aura = activeAuras.remove(casterId);
    if (aura == null) return;
    aura.active = false;
    for (int targetId : aura.affectedEntities) {
      if (callback != null) {
        callback.onEntityLeaveAura(targetId, casterId, aura.definition.skillId);
      }
    }
    aura.affectedEntities.clear();
    removeWinnerReferences(aura);
    if (callback != null) callback.onAuraDeactivated(casterId, aura.definition.skillId);
  }

  public boolean hasActiveAura(int casterId) {
    return activeAuras.containsKey(casterId);
  }

  public ActiveAura getActiveAura(int casterId) {
    return activeAuras.get(casterId);
  }

  /** Advances exactly one authoritative simulation frame. */
  public void update(float ignoredDeltaTime) {
    gameFrame++;
    Array<Integer> invalidCasters = new Array<>();
    boolean reconcile = false;
    for (IntMap.Entry<ActiveAura> entry : activeAuras) {
      ActiveAura aura = entry.value;
      aura.pulsed = false;
      if (callback == null || callback.getEntityPosition(entry.key) == null) {
        invalidCasters.add(entry.key);
        continue;
      }
      if (gameFrame >= aura.nextPulseFrame) {
        pulse(aura);
        if (!aura.active) {
          invalidCasters.add(entry.key);
          continue;
        }
        aura.nextPulseFrame = gameFrame + Math.max(5, aura.definition.perDelayFrames);
        aura.pulsed = true;
        reconcile = true;
      }
    }
    for (int casterId : invalidCasters) {
      deactivateAura(casterId);
      reconcile = true;
    }
    if (reconcile) reconcileWinners();
  }

  private void pulse(ActiveAura aura) {
    AuraDefinition definition = aura.definition;
    float manaCost = nativeManaCost(definition, aura.skillLevel);
    if (manaCost > 0f && !callback.consumeMana(aura.casterId, manaCost)) {
      aura.active = false;
      return;
    }
    float[] casterPos = callback.getEntityPosition(aura.casterId);
    if (casterPos == null) return;
    Array<Integer> range = callback.getEntitiesInRange(casterPos[0], casterPos[1], aura.range);
    Array<Integer> affected = new Array<>();
    for (int targetId : range) {
      boolean self = targetId == aura.casterId;
      // SrvDo065/066 applies AuraState to the owner through sub_6FD0FE50
      // before running the filtered room scan. Hostile filters such as Holy
      // Fire's IGNALLY must therefore never reject its own passive layer.
      if (self) {
        if (definition.affectsSelf) affected.add(targetId);
        continue;
      }
      boolean checkMonsterNoAura = definition.nativeSkill != null
          ? definition.nativeSkill.srvdofunc == 65 : definition.affectsParty;
      if (!callback.isValidTarget(
          aura.casterId, targetId, definition.auraFilter, checkMonsterNoAura)) continue;
      boolean ally = callback.isAlly(aura.casterId, targetId);
      boolean rangeTarget = (definition.affectsParty && ally)
          || (definition.affectsEnemy && !ally);
      if (rangeTarget) affected.add(targetId);
      if (rangeTarget && definition.auraType == AURA_TYPE_DAMAGE
          && definition.nativeSkill != null && !callback.isInTown(aura.casterId)) {
        int[] damage = nativeElementalDamageRange(definition.nativeSkill, aura.skillLevel,
            name -> callback.getBaseSkillLevel(aura.casterId, name));
        if (damage[1] > 0) {
          callback.applyPeriodicDamage(aura.casterId, targetId, definition.skillId,
              aura.skillLevel, damage[0], damage[1], definition.nativeSkill.EType);
        }
      }
    }
    aura.affectedEntities = affected;
    aura.lastUpdateTime = gameFrame / 25f;
  }

  private void reconcileWinners() {
    LongMap<Array<Candidate>> candidates = new LongMap<>();
    for (IntMap.Entry<ActiveAura> entry : activeAuras) {
      ActiveAura aura = entry.value;
      if (!aura.active) continue;
      AuraDefinition definition = aura.definition;
      for (int targetId : aura.affectedEntities) {
        boolean self = targetId == aura.casterId;
        int stateId = self ? definition.selfStateId : definition.targetStateId;
        if (stateId < 0) continue;
        Candidate candidate = new Candidate();
        candidate.aura = aura;
        candidate.targetId = targetId;
        candidate.stateId = stateId;
        candidate.statIds = self && hasStats(definition.passiveStatIds)
            ? mergeStats(definition.statIds, definition.passiveStatIds) : definition.statIds;
        candidate.statValues = self && hasStats(definition.passiveStatIds)
            ? mergeValues(definition.statIds, aura.statValues,
                definition.passiveStatIds, aura.passiveStatValues) : aura.statValues;
        candidate.direct = containsDirectStat(candidate.statIds);
        long key = effectKey(targetId, stateId, definition.skillId);
        Array<Candidate> bucket = candidates.get(key);
        if (bucket == null) candidates.put(key, bucket = new Array<>());
        bucket.add(candidate);
      }
    }

    LongMap<Candidate> next = new LongMap<>();
    for (LongMap.Entry<Array<Candidate>> entry : candidates) {
      Candidate current = winners.get(entry.key);
      Candidate winner = strongest(entry.value, current);
      next.put(entry.key, winner);
      boolean changed = current == null || !sameSource(current, winner);
      if (changed && current != null && callback != null) {
        callback.removeState(current.targetId, current.stateId,
            current.aura.casterId, current.aura.definition.skillId);
        removePublicEffect(current);
      }
      if (changed || winner.aura.pulsed) applyWinner(winner, changed);
    }
    for (LongMap.Entry<Candidate> entry : winners) {
      if (next.containsKey(entry.key)) continue;
      // A native aura layer is allowed to live until its short perdelay+1
      // expiry. Do not tear it down immediately merely because the source
      // moved, switched skills or left the world.
      removePublicEffect(entry.value);
    }
    winners.clear();
    winners.putAll(next);
  }

  private void applyWinner(Candidate winner, boolean entered) {
    if (callback == null) return;
    int duration = Math.max(6, winner.aura.definition.perDelayFrames + 1);
    callback.applyState(winner.targetId, winner.stateId, duration,
        winner.aura.casterId, winner.aura.definition.skillId,
        winner.aura.skillLevel, winner.statIds, winner.statValues);
    if (winner.aura.pulsed && winner.direct) {
      for (int i = 0; i < winner.statIds.length && i < winner.statValues.length; i++) {
        if (winner.statIds[i] == Stat.hitpoints && winner.statValues[i] > 0) {
          callback.applyDirectStat(winner.targetId, winner.statIds[i], winner.statValues[i],
              winner.aura.casterId, winner.aura.definition.skillId);
        }
      }
    }
    if (entered) {
      callback.onEntityEnterAura(winner.targetId, winner.aura.casterId,
          winner.aura.definition.skillId, winner.statValues);
      addPublicEffect(winner);
    }
  }

  private static Candidate strongest(Array<Candidate> candidates, Candidate current) {
    Candidate winner = null;
    for (Candidate candidate : candidates) {
      if (winner == null || candidate.aura.skillLevel > winner.aura.skillLevel) {
        winner = candidate;
      } else if (candidate.aura.skillLevel == winner.aura.skillLevel) {
        if (current != null && sameSource(candidate, current)) winner = candidate;
        else if ((current == null || !sameSource(winner, current))
            && candidate.aura.casterId < winner.aura.casterId) winner = candidate;
      }
    }
    return winner;
  }

  private static boolean sameSource(Candidate a, Candidate b) {
    return a != null && b != null && a.targetId == b.targetId && a.stateId == b.stateId
        && a.aura.casterId == b.aura.casterId
        && a.aura.definition.skillId == b.aura.definition.skillId
        && a.aura.skillLevel == b.aura.skillLevel;
  }

  private void removeWinnerReferences(ActiveAura aura) {
    Array<Long> keys = new Array<>();
    for (LongMap.Entry<Candidate> entry : winners) {
      if (entry.value.aura == aura) keys.add(entry.key);
    }
    for (long key : keys) {
      Candidate old = winners.remove(key);
      if (old != null) removePublicEffect(old);
    }
  }

  private void addPublicEffect(Candidate winner) {
    Array<AuraEffect> effects = entityAuraEffects.get(winner.targetId);
    if (effects == null) entityAuraEffects.put(winner.targetId, effects = new Array<>());
    AuraEffect effect = new AuraEffect();
    effect.skillId = winner.aura.definition.skillId;
    effect.casterId = winner.aura.casterId;
    effect.stateId = winner.stateId;
    effect.skillLevel = winner.aura.skillLevel;
    System.arraycopy(winner.statValues, 0, effect.statValues, 0,
        Math.min(effect.statValues.length, winner.statValues.length));
    effects.add(effect);
  }

  private void removePublicEffect(Candidate candidate) {
    Array<AuraEffect> effects = entityAuraEffects.get(candidate.targetId);
    if (effects == null) return;
    for (int i = effects.size - 1; i >= 0; i--) {
      AuraEffect effect = effects.get(i);
      if (effect.stateId == candidate.stateId && effect.casterId == candidate.aura.casterId
          && effect.skillId == candidate.aura.definition.skillId) effects.removeIndex(i);
    }
    if (effects.isEmpty()) entityAuraEffects.remove(candidate.targetId);
  }

  private float calculateAuraRange(AuraDefinition definition, int level) {
    if (definition.nativeSkill != null) {
      return Math.max(0, SkillFormula.evaluate(
          definition.nativeSkill.aurarangecalc, definition.nativeSkill, level));
    }
    return definition.baseRange + (level - 1) * definition.rangePerLevel;
  }

  private void calculateAuraStats(ActiveAura aura) {
    AuraDefinition definition = aura.definition;
    if (definition.nativeSkill == null) {
      for (int i = 0; i < MAX_AURA_STATS; i++) {
        aura.statValues[i] = definition.statIds[i] < 0 ? 0
            : definition.baseStatValues[i] + (aura.skillLevel - 1) * definition.statPerLevel[i];
      }
      return;
    }
    Skills.Entry skill = definition.nativeSkill;
    java.util.function.ToIntFunction<String> baseSkills = callback == null
        ? name -> 0 : name -> callback.getBaseSkillLevel(aura.casterId, name);
    for (int i = 0; i < MAX_AURA_STATS; i++) {
      aura.statValues[i] = definition.statIds[i] < 0 ? 0
          : SkillFormula.evaluate(skill.aurastatcalc[i], skill, aura.skillLevel, baseSkills);
      if (definition.passiveStatIds[i] >= 0) {
        // The formula result is already encoded for the destination stat.
        // Holy Fire, for example, explicitly divides its 8.8 `enms` token by
        // 256 inside `enms*par5/256`; dividing a second time erased its melee
        // fire bonus entirely.
        aura.passiveStatValues[i] =
            SkillFormula.evaluate(skill.passivecalc[i], skill, aura.skillLevel, baseSkills);
      }
    }
  }

  private float nativeManaCost(AuraDefinition definition, int level) {
    if (definition.nativeSkill == null) {
      return definition.manaCostPerSecond * definition.perDelayFrames / 25f;
    }
    return NativeSkillResolver.manaCost(definition.nativeSkill, level);
  }

  private void registerNativePaladinAuras() {
    if (Riiablo.files == null || Riiablo.files.skills == null || Riiablo.files.States == null) return;
    int[] ids = {SkillId.MIGHT, SkillId.PRAYER, SkillId.RESIST_FIRE,
        SkillId.RESIST_COLD, SkillId.RESIST_LIGHTNING, SkillId.HOLY_FIRE,
        SkillId.CONCENTRATION, SkillId.CONVICTION, SkillId.SALVATION};
    for (int id : ids) registerNativeAura(id);
  }

  private void registerNativeAura(int skillId) {
    Skills.Entry skill = Riiablo.files.skills.get(skillId);
    if (skill == null || (skill.srvdofunc != 65 && skill.srvdofunc != 66)) return;
    AuraDefinition definition = new AuraDefinition();
    definition.skillId = skill.Id;
    definition.name = skill.skill;
    definition.nativeSkill = skill;
    definition.selfStateId = stateId(skill.aurastate);
    definition.targetStateId = stateId(skill.auratargetstate);
    definition.stateId = definition.targetStateId >= 0
        ? definition.targetStateId : definition.selfStateId;
    definition.auraFilter = skill.aurafilter;
    definition.perDelayFrames = Math.max(5,
        SkillFormula.evaluate(skill.perdelay, skill, 1));
    definition.baseRange = Math.max(0,
        SkillFormula.evaluate(skill.aurarangecalc, skill, 1));
    definition.affectsSelf = definition.selfStateId >= 0;
    definition.affectsParty = (skill.aurafilter & FILTER_FIND_ALLY) != 0;
    definition.affectsMercenary = definition.affectsParty;
    definition.affectsEnemy = (skill.aurafilter & FILTER_IGNORE_ALLY) != 0;
    boolean damage = hasText(skill.EType) && (skill.EMin > 0 || skill.EMax > 0);
    definition.auraType = damage ? AURA_TYPE_DAMAGE
        : definition.affectsEnemy ? AURA_TYPE_DEBUFF : AURA_TYPE_BUFF;
    for (int i = 0; i < MAX_AURA_STATS; i++) {
      definition.statIds[i] = statId(skill.aurastat[i]);
      // SrvDo065 folds PassiveStat into AuraState only when the row does not
      // own a separate PassiveState. Resist Fire/Cold/Lightning use that
      // separate permanent list for their hard-point max-resist bonus.
      if (!hasText(skill.passivestate) && i < skill.passivestat.length) {
        definition.passiveStatIds[i] = statId(skill.passivestat[i]);
      }
    }
    auraDefinitions.put(skillId, definition);
  }

  private int stateId(String name) {
    if (!hasText(name)) return -1;
    com.riiablo.codec.excel.States.Entry state = Riiablo.files.States.get(name.trim());
    return state != null ? state.id : -1;
  }

  private static int statId(String name) {
    if (!hasText(name)) return -1;
    int id = Stat.index(name.trim());
    return id >= 0 ? id : -1;
  }

  /** D2Common SKILLS_GetMin/MaxElemDamage converted from 8.8 to life units. */
  public static int[] nativeElementalDamageRange(Skills.Entry skill, int level) {
    return nativeElementalDamageRange(skill, level, name -> 0);
  }

  /** D2Game elemental aura packet including Skills.txt hard-point synergies. */
  public static int[] nativeElementalDamageRange(Skills.Entry skill, int level,
      java.util.function.ToIntFunction<String> baseSkillLevel) {
    return PaladinSkills.getAuraElementalDamage(skill, level, baseSkillLevel);
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private static boolean hasStats(int[] ids) {
    for (int id : ids) if (id >= 0) return true;
    return false;
  }

  private static boolean containsDirectStat(int[] ids) {
    for (int id : ids) if (id == Stat.hitpoints || id == Stat.mana) return true;
    return false;
  }

  private static int[] mergeStats(int[] first, int[] second) {
    int count = 0;
    for (int id : first) if (id >= 0) count++;
    for (int id : second) if (id >= 0) count++;
    int[] merged = new int[count];
    int index = 0;
    for (int id : first) if (id >= 0) merged[index++] = id;
    for (int id : second) if (id >= 0) merged[index++] = id;
    return merged;
  }

  private static int[] mergeValues(
      int[] firstIds, int[] first, int[] secondIds, int[] second) {
    int[] merged = new int[countStats(firstIds) + countStats(secondIds)];
    int index = 0;
    for (int i = 0; i < firstIds.length; i++) {
      if (firstIds[i] >= 0) merged[index++] = i < first.length ? first[i] : 0;
    }
    for (int i = 0; i < secondIds.length; i++) {
      if (secondIds[i] >= 0) merged[index++] = i < second.length ? second[i] : 0;
    }
    return merged;
  }

  private static int countStats(int[] ids) {
    int count = 0;
    for (int id : ids) if (id >= 0) count++;
    return count;
  }

  private static long effectKey(int targetId, int stateId, int skillId) {
    // Native replacement compares both state and originating skill. D2 table
    // ordinals are unsigned 16-bit values, leaving the upper half for the ECS
    // entity id without collisions between distinct skills sharing a state.
    return ((long) targetId << 32)
        | ((long) stateId & 0xFFFFL) << 16
        | ((long) skillId & 0xFFFFL);
  }

  private void registerDefaultAuras() {
    registerAura(SkillId.MIGHT, "Might", AURA_TYPE_BUFF, StateId.MIGHT,
        EXCLUSIVE_OFFENSE, 16, 2, 0,
        new int[] {Stat.damagepercent}, new int[] {40}, new int[] {10},
        true, true, true, false);
    registerAura(SkillId.PRAYER, "Prayer", AURA_TYPE_BUFF, StateId.PRAYER,
        EXCLUSIVE_NONE, 16, 2, 0.5f,
        new int[] {Stat.hitpoints}, new int[] {2}, new int[] {1},
        true, true, true, false);
    registerAura(SkillId.RESIST_LIGHTNING, "Resist Lightning", AURA_TYPE_BUFF,
        StateId.RESISTLIGHT,
        EXCLUSIVE_DEFENSE, 16, 2, 0,
        new int[] {Stat.lightresist}, new int[] {30}, new int[] {5},
        true, true, true, false);
    registerAura(SkillId.RESIST_FIRE, "Resist Fire", AURA_TYPE_BUFF, StateId.RESISTFIRE,
        EXCLUSIVE_DEFENSE, 16, 2, 0,
        new int[] {Stat.fireresist}, new int[] {30}, new int[] {5},
        true, true, true, false);
    registerAura(SkillId.RESIST_COLD, "Resist Cold", AURA_TYPE_BUFF, StateId.RESISTCOLD,
        EXCLUSIVE_DEFENSE, 16, 2, 0,
        new int[] {Stat.coldresist}, new int[] {30}, new int[] {5},
        true, true, true, false);
    registerAura(SkillId.DEFIANCE, "Defiance", AURA_TYPE_BUFF, StateId.DEFIANCE,
        EXCLUSIVE_DEFENSE, 16, 2, 0,
        new int[] {Stat.item_armor_percent}, new int[] {70}, new int[] {15},
        true, true, true, false);
    registerAura(SkillId.CONCENTRATION, "Concentration", AURA_TYPE_BUFF,
        StateId.CONCENTRATION,
        EXCLUSIVE_OFFENSE, 16, 2, 0,
        new int[] {Stat.damagepercent}, new int[] {60}, new int[] {15},
        true, true, true, false);
    registerAura(SkillId.FANATICISM, "Fanaticism", AURA_TYPE_BUFF, StateId.FANATICISM,
        EXCLUSIVE_OFFENSE, 10, 1, 0,
        new int[] {Stat.damagepercent, Stat.velocitypercent},
        new int[] {180, 15}, new int[] {21, 1}, true, true, true, false);
    registerAura(SkillId.CONVICTION, "Conviction", AURA_TYPE_DEBUFF, StateId.CONVICTION,
        EXCLUSIVE_CURSE, 20, 0, 0,
        new int[] {Stat.fireresist, Stat.coldresist, Stat.lightresist,
            Stat.skill_armor_percent},
        new int[] {-30, -30, -30, -49}, new int[] {-5, -5, -5, -2},
        false, false, false, true);
    registerAura(SkillId.REDEMPTION, "Redemption", AURA_TYPE_BUFF, StateId.REDEMPTION,
        EXCLUSIVE_NONE, 16, 1, 0, new int[0], new int[0], new int[0],
        true, false, false, false);
    registerAura(SkillId.MEDITATION, "Meditation", AURA_TYPE_BUFF, StateId.MEDITATION,
        EXCLUSIVE_NONE, 16, 2, 0,
        new int[] {Stat.manarecoverybonus}, new int[] {60}, new int[] {15},
        true, true, true, false);
  }

  private void registerAura(int skillId, String name, int auraType, int stateId,
      int exclusiveGroup, float baseRange, float rangePerLevel, float manaCost,
      int[] statIds, int[] baseStats, int[] perLevel,
      boolean self, boolean party, boolean mercenary, boolean enemy) {
    AuraDefinition definition = new AuraDefinition();
    definition.skillId = skillId;
    definition.name = name;
    definition.auraType = auraType;
    definition.stateId = stateId;
    definition.selfStateId = self ? stateId : -1;
    definition.targetStateId = party || enemy ? stateId : -1;
    definition.exclusiveGroup = exclusiveGroup;
    definition.baseRange = baseRange;
    definition.rangePerLevel = rangePerLevel;
    definition.manaCostPerSecond = manaCost;
    definition.affectsSelf = self;
    definition.affectsParty = party;
    definition.affectsMercenary = mercenary;
    definition.affectsEnemy = enemy;
    definition.auraFilter = FILTER_PLAYER | FILTER_MONSTER
        | (party ? FILTER_FIND_ALLY : 0) | (enemy ? FILTER_IGNORE_ALLY : 0);
    for (int i = 0; i < Math.min(statIds.length, MAX_AURA_STATS); i++) {
      definition.statIds[i] = statIds[i];
      definition.baseStatValues[i] = i < baseStats.length ? baseStats[i] : 0;
      definition.statPerLevel[i] = i < perLevel.length ? perLevel[i] : 0;
    }
    auraDefinitions.put(skillId, definition);
  }

  public void setCallback(AuraCallback callback) {
    this.callback = callback;
  }

  public void registerAuraDefinition(AuraDefinition definition) {
    auraDefinitions.put(definition.skillId, definition);
  }

  public AuraDefinition getAuraDefinition(int skillId) {
    return auraDefinitions.get(skillId);
  }

  public Array<AuraEffect> getEntityAuraEffects(int entityId) {
    return entityAuraEffects.get(entityId);
  }

  public void removeEntity(int entityId) {
    deactivateAura(entityId);
    entityAuraEffects.remove(entityId);
  }
}
