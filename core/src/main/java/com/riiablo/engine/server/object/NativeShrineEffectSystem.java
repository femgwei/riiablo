package com.riiablo.engine.server.object;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.ShrineInteractionEvent;
import com.riiablo.engine.server.event.WellInteractionEvent;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/**
 * Applies the server-authoritative unit effects emitted by native shrines and
 * wells.
 *
 * <p>D2Game stores shrine bonuses in timed stat lists.  Keeping the bonuses on
 * {@link UnitState} has the same lifetime semantics and, importantly, avoids
 * permanently mutating the character's saved base stats.</p>
 */
public final class NativeShrineEffectSystem extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(NativeShrineEffectSystem.class);
  private static final int SKILL_SHRINE_BONUS = 2;

  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<Mercenary> mMercenary;

  private EntitySubscription mercenaries;

  @Override
  protected void initialize() {
    mercenaries = world.getAspectSubscriptionManager().get(
        Aspect.all(Mercenary.class, AttributesWrapper.class));
  }

  @Subscribe
  public void onWellInteraction(WellInteractionEvent event) {
    if (event == null) return;

    boolean chargeWorthy = cleanse(event.playerId, event.cleansePoison,
        event.cleanseFreeze, event.cleanseCurses);
    if (event.healAndCleansePets) {
      IntBag entities = mercenaries == null ? null : mercenaries.getEntities();
      int[] ids = entities == null ? null : entities.getData();
      int size = entities == null ? 0 : entities.size();
      for (int i = 0; i < size; i++) {
        int petId = ids[i];
        Mercenary mercenary = mMercenary.get(petId);
        if (mercenary == null || mercenary.ownerId != event.playerId) continue;

        // OBJMODE_PetIterate_Heal marks the well as used only when pet life is
        // restored. It still removes poison/freeze/curses unconditionally.
        chargeWorthy |= healToMaximum(petId);
        chargeWorthy |= cleanse(petId, event.cleansePoison, event.cleanseFreeze,
            event.cleanseCurses);
      }
    }

    if (chargeWorthy) event.appliedByConsumer = true;
    log.debug("[WELL_EFFECT] player={} chargeWorthy={} localMask={}",
        event.playerId, chargeWorthy, event.localRestorationMask);
  }

  @Subscribe
  public void onShrineInteraction(ShrineInteractionEvent event) {
    if (event == null || event.code < 6 || event.code > 15) return;
    UnitState state = applyTimedEffect(event.playerId, event.entityId,
        event.code, event.arg0, event.arg1, event.durationFrames);
    if (state == null) {
      log.warn("[SHRINE_EFFECT] rejected: player={}, shrine={}, code={}, reason=no_states",
          event.playerId, event.entityId, event.code);
      return;
    }

    if (event.code == 14) restoreStamina(event.playerId);
    log.info("[SHRINE_EFFECT] applied: player={}, shrine={}, code={}, state={}, "
            + "arg0={}, arg1={}, duration={}",
        event.playerId, event.entityId, event.code, state.stateId,
        event.arg0, event.arg1, state.duration);
  }

  UnitState applyTimedEffect(int playerId, int shrineEntityId, int code,
      int arg0, int arg1, int durationFrames) {
    if (!mUnitStates.has(playerId)) return null;
    UnitStates states = mUnitStates.get(playerId);
    if (states.stateList == null) states.init(playerId);
    return applyTimedEffect(states.stateList, shrineEntityId, code,
        arg0, arg1, durationFrames);
  }

  static UnitState applyTimedEffect(StateList states, int shrineEntityId,
      int code, int arg0, int arg1, int durationFrames) {
    if (states == null) return null;
    int stateId = stateIdForCode(code);
    if (stateId == StateId.NONE) return null;

    int duration = Math.max(1, durationFrames);
    UnitState state = states.addState(stateId, duration, 1, shrineEntityId);
    if (state == null) return null;

    // Refreshing a shrine state must replace its stat list values, not retain
    // modifiers left by the previous source.
    state.clearModifiers();
    switch (code) {
      case 6:
        state.defenseModifier = Math.max(0, arg0);
        break;
      case 7:
        state.attackModifier = Math.max(0, arg0);
        state.damageModifier = Math.max(0, arg1);
        break;
      case 8:
        state.fireResistModifier = Math.max(0, arg0);
        break;
      case 9:
        state.coldResistModifier = Math.max(0, arg0);
        break;
      case 10:
        state.lightResistModifier = Math.max(0, arg0);
        break;
      case 11:
        state.poisonResistModifier = Math.max(0, arg0);
        break;
      case 12:
        // Native state 0x86 supplies +2 to all learned skills; Shrines.txt
        // arguments are deliberately ignored by D2GAME_SHRINES_SkillBoost.
        state.skillModifier = SKILL_SHRINE_BONUS;
        break;
      case 13:
        state.manaRecoveryModifier = Math.max(0, arg0);
        break;
      case 14:
        state.maxStaminaModifier = Math.max(0, arg0);
        state.staminaRecoveryModifier = 1000;
        break;
      case 15:
        state.experienceModifier = Math.max(0, arg0);
        break;
      default:
        return null;
    }
    state.needsSync = true;
    return state;
  }

  static int stateIdForCode(int code) {
    switch (code) {
      case 6: return StateId.SHRINE_ARMOR;
      case 7: return StateId.SHRINE_COMBAT;
      case 8: return StateId.SHRINE_RESIST_FIRE;
      case 9: return StateId.SHRINE_RESIST_COLD;
      case 10: return StateId.SHRINE_RESIST_LIGHTNING;
      case 11: return StateId.SHRINE_RESIST_POISON;
      case 12: return StateId.SHRINE_SKILL;
      case 13: return StateId.SHRINE_MANA_REGEN;
      case 14: return StateId.SHRINE_STAMINA;
      case 15: return StateId.SHRINE_EXPERIENCE;
      default: return StateId.NONE;
    }
  }

  private boolean cleanse(int entityId, boolean poison, boolean freeze,
      boolean curses) {
    if (!mUnitStates.has(entityId)) return false;
    UnitStates component = mUnitStates.get(entityId);
    StateList states = component == null ? null : component.stateList;
    if (states == null) return false;
    boolean changed = false;
    if (poison) changed |= states.removeState(StateId.POISON);
    if (freeze) changed |= states.removeState(StateId.FREEZE);
    if (curses) changed |= states.removeCurses() > 0;
    return changed;
  }

  private boolean healToMaximum(int entityId) {
    if (!mAttributesWrapper.has(entityId)) return false;
    AttributesWrapper wrapper = mAttributesWrapper.get(entityId);
    Attributes attrs = wrapper == null ? null : wrapper.attrs;
    if (attrs == null) return false;
    float life = attrs.aggregate().getValue(Stat.hitpoints, 0f);
    float maximum = attrs.aggregate().getValue(Stat.maxhp, life);
    if (life >= maximum) return false;
    attrs.base().put(Stat.hitpoints, maximum);
    attrs.aggregate().put(Stat.hitpoints, maximum);
    return true;
  }

  private void restoreStamina(int entityId) {
    if (!mAttributesWrapper.has(entityId)) return;
    Attributes attrs = mAttributesWrapper.get(entityId).attrs;
    if (attrs == null) return;
    float maximum = attrs.aggregate().getValue(Stat.maxstamina, 0f);
    attrs.base().put(Stat.stamina, maximum);
    attrs.aggregate().put(Stat.stamina, maximum);
  }
}
