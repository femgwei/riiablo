package com.riiablo.engine.server.quest;

import com.d2moo.common.drlg.D2LevelIds;

/** Native D2MOO A5Q6 Eve of Destruction state transitions. */
public final class Act5BaalQuest {
  private Act5BaalQuest() {}

  public static final int RECORD = 6;
  public static final int HARROGATH = D2LevelIds.LEVEL_HARROGATH;
  public static final int WORLDSTONE_KEEP_1 = D2LevelIds.LEVEL_THEWORLDSTONEKEEPLEV1;
  public static final int THRONE_OF_DESTRUCTION = D2LevelIds.LEVEL_THEWORLDSTONEKEEPLEV2;
  public static final int WORLDSTONE_CHAMBER = D2LevelIds.LEVEL_WORLDSTONECHAMBER;
  public static final int MESSAGE_TYRAEL = 20175;
  /** D2MOO MONSTER_TYRAEL3 (the enum includes the explicit zero row). */
  public static final int TYRAEL3_CLASS = 522;
  public static final int BAAL_THRONE_CLASS = 543;
  public static final int BAAL_CLASS = 544;
  /** Native SuperUniques.txt hardcoded ids used by BaalThrone AI. */
  public static final int[] WAVE_SUPER_UNIQUES = {
      com.d2moo.common.drlg.D2SuperUniques.SUPERUNIQUE_BAAL_SUBJECT_1,
      com.d2moo.common.drlg.D2SuperUniques.SUPERUNIQUE_BAAL_SUBJECT_2,
      com.d2moo.common.drlg.D2SuperUniques.SUPERUNIQUE_BAAL_SUBJECT_3,
      com.d2moo.common.drlg.D2SuperUniques.SUPERUNIQUE_BAAL_SUBJECT_4,
      com.d2moo.common.drlg.D2SuperUniques.SUPERUNIQUE_BAAL_SUBJECT_5};
  /** Fallback MonStats classes when SuperUniques.txt is unavailable. */
  public static final String[] WAVE_MONSTER_IDS = {
      "fallenshaman5", "unraveler5", "baalhighpriest", "venomlord", "baalminion1"};
  /**
   * Native AITHINK_BaalThrone 0xA4 class-preload hints sent before waves 1/2.
   * These are client preload hints, not extra monsters: the actual entities
   * still come from SuperUnique preset spawning and MonStats.minion1.
   */
  private static final String[][] WAVE_CLIENT_CLASS_HINTS = {
      {"fallenshaman5", "fallen5"},
      {"unraveler5", "skmage_cold3"},
      {}, {}, {}
  };
  /** 1.10f SuperUniques.txt MinGrp/MaxGrp (both columns are equal). */
  public static final int[] WAVE_MINIONS = {5, 3, 5, 8, 5};
  public static final int WAVE_COUNT = 5;
  /** AI_Function1_BaalThrone native frame delays at 25 ticks/second. */
  public static final int PRE_WAVE_DELAY_TICKS = 250;
  public static final int POST_SPAWN_LOCK_TICKS = 100;
  public static final float THRONE_CLEAR_RADIUS = 64f;

  static int minionCount(int min, int max, int gameSeed, int waveIndex) {
    min = Math.max(0, min);
    max = Math.max(min, max);
    if (max == min) return min;
    int mixed = gameSeed ^ (waveIndex + 1) * 0x9E3779B9;
    mixed ^= mixed >>> 16;
    return min + (mixed & 0x7FFFFFFF) % (max - min + 1);
  }

  /** D2GAME_SpawnSuperUnique adds difficulty (0/1/2) to non-zero groups. */
  /** Returns the native SuperUnique group range after D2Game difficulty scaling. */
  public static int[] nativeGroupRange(int min, int max, int difficulty) {
    min = Math.max(0, min);
    max = Math.max(min, max);
    difficulty = Math.max(0, Math.min(2, difficulty));
    if (min > 0 && max > 0) {
      min += difficulty;
      max += difficulty;
    }
    return new int[] {min, max};
  }

  static String[] nativeWaveClientClassHints(int waveIndex) {
    if (waveIndex < 0 || waveIndex >= WAVE_CLIENT_CLASS_HINTS.length) return new String[0];
    return WAVE_CLIENT_CLASS_HINTS[waveIndex].clone();
  }

  static boolean isBaalMonster(int hcIdx, String id) {
    return hcIdx == BAAL_CLASS || "baalcrab".equalsIgnoreCase(id)
        || "baal".equalsIgnoreCase(id);
  }

  static boolean isBaalThroneMonster(int hcIdx, String id) {
    return hcIdx == BAAL_THRONE_CLASS || "baalthrone".equalsIgnoreCase(id);
  }

  static long nativeSuperUniqueAffixes(int[] mods) {
    if (mods == null) return 0L;
    long affixes = 0L;
    for (int mod : mods) {
      switch (mod) {
        case 5: affixes |= com.riiablo.engine.server.monster.MonsterAffix.EXTRA_STRONG; break;
        case 6: affixes |= com.riiablo.engine.server.monster.MonsterAffix.EXTRA_FAST; break;
        case 7: affixes |= com.riiablo.engine.server.monster.MonsterAffix.CURSED; break;
        case 8: affixes |= com.riiablo.engine.server.monster.MonsterAffix.MAGIC_RESISTANT; break;
        case 9: affixes |= com.riiablo.engine.server.monster.MonsterAffix.FIRE_ENCHANTED; break;
        case 17: affixes |= com.riiablo.engine.server.monster.MonsterAffix.LIGHTNING_ENCHANTED; break;
        case 18: affixes |= com.riiablo.engine.server.monster.MonsterAffix.COLD_ENCHANTED; break;
        case 23: affixes |= com.riiablo.engine.server.monster.MonsterAffix.POISON_ENCHANTED; break;
        case 25: affixes |= com.riiablo.engine.server.monster.MonsterAffix.MANA_BURN; break;
        case 27: affixes |= com.riiablo.engine.server.monster.MonsterAffix.SPECTRAL_HIT; break;
        case 28: affixes |= com.riiablo.engine.server.monster.MonsterAffix.STONE_SKIN; break;
        case 29: affixes |= com.riiablo.engine.server.monster.MonsterAffix.MULTISHOT; break;
        case 30: affixes |= com.riiablo.engine.server.monster.MonsterAffix.AURA_ENCHANTED; break;
        default: break;
      }
    }
    return affixes;
  }

  public static short start(short record) {
    return isFinished(record) ? record : NativeQuestRecord.set(record, NativeQuestRecord.STARTED);
  }

  public static short enterArea(short record) {
    return NativeQuestRecord.set(start(record), NativeQuestRecord.ENTERED_AREA);
  }

  /** Mirrors A5Q6's state-3 transition when a started player leaves Harrogath. */
  public static short leaveTown(short record) {
    return NativeQuestRecord.set(start(record), NativeQuestRecord.LEFT_TOWN);
  }

  /** D2MOO grants A5Q6 immediately when Baal dies; there is no pending NPC turn-in. */
  public static short complete(short record) {
    if (!canGrantReward(record)) return record;
    record = NativeQuestRecord.set(record, NativeQuestRecord.PRIMARY_GOAL_DONE);
    return NativeQuestRecord.set(record, NativeQuestRecord.REWARD_GRANTED);
  }

  public static boolean canGrantReward(short record) {
    return !NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
        && !NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING);
  }

  /** Players who did not qualify for primary-goal credit still receive the
   * native game-wide quest-log completion marker. */
  public static short completeObserver(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
        ? record : NativeQuestRecord.set(record, NativeQuestRecord.COMPLETED_NOW);
  }

  public static boolean isFinished(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED);
  }

  /** Native SetRewardGranted only accepts players physically in the Chamber. */
  public static boolean canReceiveDirectReward(boolean expansion, int levelId) {
    return expansion && levelId == WORLDSTONE_CHAMBER;
  }

  /** Chamber recipients propagate primary-goal credit only to party members
   * that are still in Act V. */
  public static boolean canReceivePartyReward(boolean expansion, int levelId) {
    return expansion && levelId >= D2LevelIds.LEVEL_HARROGATH
        && levelId <= D2LevelIds.LEVEL_WORLDSTONECHAMBER;
  }

  /** Native object 72 gate for the Worldstone Chamber -> Harrogath portal. */
  public static boolean canUseLastPortal(short record, int sourceLevelId) {
    // OBJECTS_OperateFunction72 checks PRIMARYGOALDONE, not REWARD_GRANTED;
    // party members can receive the former before their individual reward bit
    // is persisted.
    return sourceLevelId == WORLDSTONE_CHAMBER
        && NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE);
  }

  public static boolean isTyrael3(int hcIdx, String id) {
    return hcIdx == TYRAEL3_CLASS || "tyrael3".equalsIgnoreCase(id)
        || "tyrael".equalsIgnoreCase(id);
  }

  /** The final portal is a Tyrael dialogue/deactivate transition, not a
   * direct consequence of Baal's DeathEvent. */
  public static boolean canTriggerLastPortal(short record, int sourceLevelId,
      int messageIndex) {
    return sourceLevelId == WORLDSTONE_CHAMBER && isFinished(record)
        && messageIndex == MESSAGE_TYRAEL;
  }
}
