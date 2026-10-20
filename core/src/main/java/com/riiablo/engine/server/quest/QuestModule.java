package com.riiablo.engine.server.quest;

import com.artemis.BaseSystem;
import com.riiablo.Files;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.save.CharData;

/** Composition root for quest authority. ECS adapters stay in core; rules live
 * in :quests. Each world receives fresh systems, RNG and encounter state. */
public final class QuestModule {
  private QuestModule() {}

  public static BaseSystem[] authoritySystems() {
    return new BaseSystem[] {
        new NativeQuestRandomState(), new Act1QuestSystem(), new Act2QuestSystem(),
        new Act3QuestSystem(), new Act3MephistoQuestSystem(), new Act4QuestSystem(),
        new Act5QuestSystem(), new Act2TaintedSunQuestSystem(), new Act2DurielQuestSystem(),
        new NativeMercenaryRewardSystem()
    };
  }

  public static BaseSystem[] itemRewardSystems() {
    return new BaseSystem[] {new NativeCountessRewardSystem(), new NativeCharsiImbueSystem()};
  }

  /** Spatial/access checks belong to the caller; no client can bypass them. */
  public static boolean isNpcMessageAllowed(Files files, MonStats.Entry npc, CharData data,
      int playerLevel, boolean hasMalus, int levelId, int message) {
    if (npc == null || data == null || message < 0 || message > 0xFFFF) return false;
    return Act1QuestMessageValidator.isAllowed(npc.hcIdx, data, playerLevel, hasMalus, message)
        || Act2QuestMessageValidator.isAllowed(npc.hcIdx, data, message)
        || Act3QuestMessageValidator.isAllowed(npc.hcIdx, data, message)
        || Act4QuestMessageValidator.isAllowed(npc.hcIdx, data, message)
        || Act5QuestMessageValidator.isAllowed(files, npc.hcIdx, data, levelId, message);
  }
}
