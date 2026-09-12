package com.riiablo.engine.server.quest;

import com.d2moo.common.drlg.D2LevelIds;

/** Native D2MOO A5Q6 Eve of Destruction state transitions. */
public final class Act5BaalQuest {
  private Act5BaalQuest() {}

  public static final int RECORD = 6;
  public static final int WORLDSTONE_KEEP_1 = D2LevelIds.LEVEL_THEWORLDSTONEKEEPLEV1;
  public static final int THRONE_OF_DESTRUCTION = D2LevelIds.LEVEL_THEWORLDSTONEKEEPLEV2;
  public static final int WORLDSTONE_CHAMBER = D2LevelIds.LEVEL_WORLDSTONECHAMBER;
  public static final int MESSAGE_TYRAEL = 20175;
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

  /** D2MOO grants A5Q6 immediately when Baal dies; there is no pending NPC turn-in. */
  public static short complete(short record) {
    if (isFinished(record)) return record;
    record = NativeQuestRecord.clear(record, NativeQuestRecord.REWARD_PENDING);
    record = NativeQuestRecord.set(record, NativeQuestRecord.PRIMARY_GOAL_DONE);
    record = NativeQuestRecord.set(record, NativeQuestRecord.REWARD_GRANTED);
    return NativeQuestRecord.set(record, NativeQuestRecord.COMPLETED_NOW);
  }

  public static boolean isFinished(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED);
  }
}
