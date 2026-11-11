package com.riiablo.engine.server.monster;

/**
 * Base-class MONUMOD assignments made by D2Game's
 * {@code MONSTERSPAWN_SetupNormalMods}.
 *
 * <p>The bit set is deliberately separate from random unique affixes. These
 * assignments come from the MonStats base class and must be present on every
 * spawn of that class, including map and quest spawns.
 */
public final class NativeMonsterSpawnMods {
  private NativeMonsterSpawnMods() {}

  public static final int NONE = 0;
  public static final int SPECIAL_DAMAGE = 1 << 0;
  public static final int POISON_DEATH = 1 << 1;
  public static final int IGNORE_AC = 1 << 2;
  public static final int CHARGED_BOLTS = 1 << 3;
  public static final int SELF_RESURRECT = 1 << 4;
  public static final int FREEZE_IN_DEATH = 1 << 5;
  public static final int ICE_SHATTER_DEATH = 1 << 6;
  public static final int KILL_MINIONS_DEATH = 1 << 7;
  public static final int QUEST_MOD = 1 << 8;

  /** Returns the native base-class mod set for a MonStats BaseId. */
  public static int forBaseId(String baseId) {
    if (baseId == null || baseId.isEmpty()) return NONE;
    switch (baseId) {
      case "suicideminion1": return FREEZE_IN_DEATH;
      case "reanimatedhorde1": return SELF_RESURRECT;
      case "frozenhorror1": return ICE_SHATTER_DEATH;
      case "mummy1": return POISON_DEATH;
      case "brute2": return IGNORE_AC;
      case "scarab1": return CHARGED_BOLTS;
      case "duriel": return QUEST_MOD;
      case "trapfire":
      case "traplightning":
      case "evilhole5": return SPECIAL_DAMAGE;
      default:
        if (baseId.length() == "boneprison".length() + 1
            && baseId.startsWith("boneprison")) {
          char suffix = baseId.charAt(baseId.length() - 1);
          if (suffix >= '1' && suffix <= '4') return KILL_MINIONS_DEATH;
        }
        return NONE;
    }
  }

  public static boolean has(String baseId, int mod) {
    return (forBaseId(baseId) & mod) != 0;
  }
}
