package com.riiablo.engine.server.component;

/** Native D2UnitStrc targetability predicates shared by AI and combat paths. */
public final class NativeTargeting {
  private NativeTargeting() {}

  /** D2Common unit-find target selection predicate. */
  public static boolean isTargetable(NativeUnitFlags flags) {
    return flags != null && flags.has(NativeUnitFlags.TARGETABLE);
  }

  /** D2Common unit-find attackability predicate. */
  public static boolean canBeAttacked(NativeUnitFlags flags) {
    return flags != null && flags.has(NativeUnitFlags.CAN_BE_ATTACKED);
  }

  /**
   * D2Game missile/effect predicate. Native target validation requires all
   * three independent bits; CAN_BE_ATTACKED alone is insufficient while a
   * corpse or transitional summon has TARGETABLE/IS_VALID_TARGET cleared.
   */
  public static boolean isValidCombatTarget(NativeUnitFlags flags) {
    return flags != null && flags.has(NativeUnitFlags.TARGETABLE)
        && flags.has(NativeUnitFlags.IS_VALID_TARGET)
        && flags.has(NativeUnitFlags.CAN_BE_ATTACKED);
  }
}
