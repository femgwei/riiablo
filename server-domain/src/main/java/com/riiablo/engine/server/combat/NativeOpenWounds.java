package com.riiablo.engine.server.combat;

/** SkillItem.cpp 0x6FD04C40/0x6FD04CF0: raw negative HPREGEN, not ordinary hit damage. */
public final class NativeOpenWounds {
  public static final int DURATION = 200;
  private NativeOpenWounds() {}
  public static int rate(int level, boolean targetPlayer, boolean ranged, boolean championOrUnique) {
    int remaining = Math.max(1, level) - 1;
    long raw = 40;
    int[] widths = {14,15,15,15,Integer.MAX_VALUE};
    int[] slopes = {9,18,27,36,45};
    for (int i = 0; i < widths.length && remaining > 0; i++) {
      int count = Math.min(remaining, widths[i]);
      raw += (long) count * slopes[i]; remaining -= count;
    }
    if (targetPlayer) { raw /= 4; if (ranged) raw /= 2; }
    else if (championOrUnique) raw /= 2;
    return (int) Math.min(Integer.MAX_VALUE, raw);
  }
}
