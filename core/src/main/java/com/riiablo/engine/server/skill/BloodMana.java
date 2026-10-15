package com.riiablo.engine.server.skill;

import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;

/** D2GAME_SKILLS_BloodMana_6FD025E0; cost and life use the native 8-bit fraction. */
public final class BloodMana {
  private BloodMana() {}

  public static boolean active(StateList states) {
    return states != null && states.hasState(StateId.BLOOD_MANA);
  }

  /** Caller selects the player mana-consumption path and verifies an active state. */
  public static boolean consumeLife(Attributes attrs, StateList states, float manaCost) {
    UnitState curse = states == null ? null : states.getState(StateId.BLOOD_MANA);
    StatRef life = attrs == null ? null : attrs.get(Stat.hitpoints, StatRef.obtain());
    if (curse == null || life == null) return false;
    float cost = (int) (manaCost * 256f) / 256f;
    if (life.asFixed() < cost) {
      states.removeState(StateId.BLOOD_MANA);
      life.set(1f);
      return false;
    }
    // Resolve before removal: StateList returns removed layers to its pool.
    Skills.Entry source = curse.skillId >= 0 && Riiablo.files != null
        ? Riiablo.files.skills.get(curse.skillId) : null;
    int threshold = source != null && source.Param != null && source.Param.length > 4
        ? source.Param[4] : 0;
    life.sub(cost);
    if (life.asFixed() < threshold) states.removeState(StateId.BLOOD_MANA);
    return true;
  }
}
