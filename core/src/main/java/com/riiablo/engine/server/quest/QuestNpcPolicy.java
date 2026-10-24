package com.riiablo.engine.server.quest;

import java.util.Locale;

import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.monster.MonsterType;

/**
 * Canonical NPC identity policy shared by quest command validation and event
 * handlers.  Expansion MonStats can contain compatibility ordinals, so named
 * Act V NPC rows must win before the raw {@code hcIdx} is interpreted.
 */
public final class QuestNpcPolicy {
  private QuestNpcPolicy() {}

  /** Resolves a MonStats row to the D2 quest NPC identity used by handlers. */
  public static int resolve(MonStats.Entry npc) {
    if (npc == null) return MonsterType.INVALID;
    if (named(npc, "Larzuk")) return MonsterType.LARZUK;
    if (named(npc, "Drehya")) return MonsterType.DREHYA;
    if (named(npc, "Malah")) return MonsterType.MALAH;
    if (named(npc, "Qual-Kehk") || named(npc, "QualKehk")) {
      return MonsterType.QUAL_KEHK;
    }
    if (Act5BaalQuest.isTyrael3(npc.hcIdx, npc.Id)) return MonsterType.TYRAEL3;
    return npc.hcIdx;
  }

  private static boolean named(MonStats.Entry npc, String name) {
    return name.equalsIgnoreCase(npc.Id) || name.equalsIgnoreCase(npc.NameStr)
        || (npc.NameStr != null
            && npc.NameStr.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)));
  }
}
