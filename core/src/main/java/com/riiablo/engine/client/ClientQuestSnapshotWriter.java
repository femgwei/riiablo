package com.riiablo.engine.client;

import com.riiablo.engine.server.quest.QuestSnapshot;
import com.riiablo.save.CharData;

/** Explicit write port from committed records to the current-difficulty replica. */
final class ClientQuestSnapshotWriter {
  private ClientQuestSnapshotWriter() {}
  static boolean apply(CharData character, AuthoritativeQuestState.Snapshot snapshot) {
    return character != null && snapshot != null && character.diff == snapshot.difficulty
        && QuestSnapshot.apply(character, snapshot.length(), snapshot::record);
  }
}