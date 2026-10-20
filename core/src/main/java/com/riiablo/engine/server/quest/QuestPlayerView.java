package com.riiablo.engine.server.quest;

import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.save.CharData;

/** Host adapter for the independent quest module's read-only ports. */
public final class QuestPlayerView implements QuestPlayer {
  private final CharData data;

  public QuestPlayerView(CharData data) { this.data = data; }

  @Override public short record(int act, int index) {
    short[] records = data.getQuests(act);
    return records != null && index >= 0 && index < records.length ? records[index] : 0;
  }
  @Override public QuestInventory items() { return data.getItems(); }
  @Override public boolean expansion() { return data.isExpansion(); }

  /** The native expansion table includes an extra zero row. Match named NPCs
   * before interpreting compatibility ordinals, as the authority does. */
  public static int npcType(MonStats.Entry npc) {
    if (npc == null) return -1;
    if (named(npc, "Larzuk")) return MonsterType.LARZUK;
    if (named(npc, "Drehya")) return MonsterType.DREHYA;
    if (named(npc, "Malah")) return MonsterType.MALAH;
    if (named(npc, "Qual-Kehk") || named(npc, "QualKehk")) return MonsterType.QUAL_KEHK;
    if (Act5BaalQuest.isTyrael3(npc.hcIdx, npc.Id)) return Act5BaalQuest.TYRAEL3_CLASS;
    return npc.hcIdx;
  }

  private static boolean named(MonStats.Entry npc, String name) {
    return name.equalsIgnoreCase(npc.Id) || name.equalsIgnoreCase(npc.NameStr)
        || (npc.NameStr != null && npc.NameStr.toLowerCase(java.util.Locale.ROOT)
            .contains(name.toLowerCase(java.util.Locale.ROOT)));
  }
}
