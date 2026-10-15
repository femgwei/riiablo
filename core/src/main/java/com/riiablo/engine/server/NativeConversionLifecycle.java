package com.riiablo.engine.server;

import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;

/** SkillPal's Conversion remove callback, scoped to one authoritative unit lifetime. */
public final class NativeConversionLifecycle {
  private NativeConversionLifecycle() {}

  public static void bind(UnitState conversion, Monster monster, AttributesWrapper attributes) {
    conversion.removalCallback = (states, removed) -> restore(states, monster, attributes);
  }

  /** Unlinking precedes this call; the save-list must remain readable until it returns. */
  public static void restore(StateList states, Monster monster, AttributesWrapper attributes) {
    boolean wasConverted = monster != null && monster.converted;
    if (monster != null) {
      monster.converted = false;
      monster.conversionOwnerId = -1;
    }
    UnitState save = states.getState(StateId.CONVERSION_SAVE);
    Attributes attrs = attributes != null ? attributes.attrs : null;
    if (wasConverted && save != null && attrs != null) {
      StatRef level = attrs.get(Stat.level, StatRef.obtain());
      StatRef hp = attrs.get(Stat.hitpoints, StatRef.obtain());
      StatRef maxHp = attrs.get(Stat.maxhp, StatRef.obtain());
      // Preserve the existing living-unit scaling projection. Death cleanup
      // must not raise zero HP via its minimum-one-life clamp.
      if (level != null && hp != null && maxHp != null && hp.asFixed() > 0f
          && save.conversionOriginalLevel > 0 && save.conversionOriginalMaxHpEncoded > 0) {
        float ratio = Math.max(0f, Math.min(1f, hp.asFixed() / Math.max(1f, maxHp.asFixed())));
        float originalMax = save.conversionOriginalMaxHpEncoded / 256f;
        level.set(save.conversionOriginalLevel);
        maxHp.set(originalMax);
        hp.set(Math.max(1f, Math.min(originalMax, originalMax * ratio)));
      }
    }
    // Synchronous nested removal, after consuming its payload (SkillPal.cpp).
    states.removeState(StateId.CONVERSION_SAVE);
  }
}
