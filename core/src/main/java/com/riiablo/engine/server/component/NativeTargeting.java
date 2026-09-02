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

  /** D2Game missile/effect predicate: valid target and attackable. */
  public static boolean isValidCombatTarget(NativeUnitFlags flags) {
    return flags != null && flags.has(NativeUnitFlags.IS_VALID_TARGET)
        && flags.has(NativeUnitFlags.CAN_BE_ATTACKED);
  }
}
