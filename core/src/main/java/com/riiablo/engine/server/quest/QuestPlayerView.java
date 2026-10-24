package com.riiablo.engine.server.quest;

import com.riiablo.codec.excel.MonStats;
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
    return QuestNpcPolicy.resolve(npc);
  }
}
