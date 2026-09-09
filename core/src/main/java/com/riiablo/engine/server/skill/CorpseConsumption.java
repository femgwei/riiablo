package com.riiablo.engine.server.skill;

import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;

/** Shared D2Common corpse-selection and single-consumer reservation rules. */
public final class CorpseConsumption {
  private CorpseConsumption() {}

  /** Mirrors {@code SKILLS_CanUnitCorpseBeSelected} plus server reservation state. */
  public static boolean selectable(
      Corpse corpse, Monster monster, Attributes attributes, StateList states) {
    if (corpse == null || !corpse.usable || corpse.fading
        || monster == null || monster.monstats2 == null || !monster.monstats2.corpseSel) {
      return false;
    }
    StatRef hitpoints = attributes != null
        ? attributes.get(Stat.hitpoints, StatRef.obtain()) : null;
    if (hitpoints == null || hitpoints.asFixed() > 0f) return false;
    return states == null
        || !states.hasState(StateId.CORPSE_NOSELECT)
            && !states.hasState(StateId.CORPSE_NODRAW);
  }

  /**
   * Atomically claims a corpse on the authoritative sim thread before any
   * spawn, damage or loot side effect can run.
   */
  public static boolean tryReserve(
      Corpse corpse, Monster monster, Attributes attributes, StateList states,
      boolean hide, int level, int sourceEntityId, int skillId) {
    if (states == null || !selectable(corpse, monster, attributes, states)) return false;
    corpse.usable = false;
    UnitState noSelect = states.addState(
        StateId.CORPSE_NOSELECT, Integer.MAX_VALUE, Math.max(1, level), sourceEntityId);
    if (noSelect != null) {
      noSelect.skillId = skillId;
      noSelect.needsSync = true;
    }
    if (hide) {
      UnitState noDraw = states.addState(
          StateId.CORPSE_NODRAW, Integer.MAX_VALUE, Math.max(1, level), sourceEntityId);
      if (noDraw != null) {
        noDraw.skillId = skillId;
        noDraw.needsSync = true;
      }
    }
    return true;
  }
}
