package com.riiablo.engine.server.combat;

/** SrvDo064 reads the mitigated combat record, caps it by current target life,
 * then applies calc2. It does not run the attacker's resistance/PvP a second time. */
public final class NativeSacrificeMath {
  private NativeSacrificeMath() {}

  public static int selfDamage(int physicalFixed, int targetLifeFixed, int percent) {
    int base = Math.min(Math.max(0, physicalFixed), Math.max(0, targetLifeFixed));
    return (int) Math.max(0L, Math.min(Integer.MAX_VALUE,
        NativePhysicalDamageMath.percentage(base, Math.max(0, percent))));
  }
}
