package com.riiablo.engine.server.combat;

import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.StatRef;

/** Host-only resource write boundary. Does not decide life/death eligibility or emit events. */
public final class CombatResourceWriter {
  private CombatResourceWriter() {}

  /** Inputs are whole resource units, not raw 8.8 packet values. Missing maxima reject healing. */
  public static float restore(Attributes attributes, short currentStat, short maximumStat,
      float requested) {
    if (attributes == null || requested <= 0f) return 0f;
    StatRef current = attributes.get(currentStat, StatRef.obtain());
    StatRef maximum = attributes.get(maximumStat, StatRef.obtain());
    if (current == null || maximum == null) return 0f;
    float delta = NativeResourceMath.cappedRestore(current.asFixed(), maximum.asFixed(), requested);
    if (delta > 0f) current.add(delta);
    return delta;
  }
}
