package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.d2moo.common.drlg.D2LevelIds;
import com.riiablo.Riiablo;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;

class QuestWarpPolicyTest {
  @Test
  void a3HellGateRequiresMephistoCompletion() {
    CharData data = data();
    assertEquals("A3Q6_NOT_COMPLETE", QuestWarpPolicy.rejectionReason(data,
        Act3MephistoQuest.MEPHISTO_LEVEL, Act3MephistoQuest.DESTINATION_ACT4));

    data.getQuests(Riiablo.ACT3)[Act3MephistoQuest.RECORD] =
        Act3MephistoQuest.complete((short) 0);
    assertNull(QuestWarpPolicy.rejectionReason(data,
        Act3MephistoQuest.MEPHISTO_LEVEL, Act3MephistoQuest.DESTINATION_ACT4));
  }

  @Test
  void a4TyraelGateRequiresGrantedDiabloReward() {
    CharData data = data();
    assertEquals("A4Q2_NOT_COMPLETE", QuestWarpPolicy.rejectionReason(data,
        D2LevelIds.LEVEL_THEPANDEMONIUMFORTRESS, D2LevelIds.LEVEL_HARROGATH));

    data.getQuests(Riiablo.ACT4)[Act4DiabloQuest.RECORD] =
        NativeQuestRecord.set((short) 0, NativeQuestRecord.REWARD_GRANTED);
    assertNull(QuestWarpPolicy.rejectionReason(data,
        D2LevelIds.LEVEL_THEPANDEMONIUMFORTRESS, D2LevelIds.LEVEL_HARROGATH));
  }

  @Test
  void a5NihlathakPortalRequiresPrisonAndStartedQuest() {
    CharData data = data();
    assertEquals("A5Q4_NOT_STARTED", QuestWarpPolicy.rejectionReason(data,
        D2LevelIds.LEVEL_HARROGATH, Act5NihlathakQuest.NIHLATHAK_TEMPLE));

    data.getQuests(Riiablo.ACT5)[Act5PrisonQuest.RECORD] =
        NativeQuestRecord.set((short) 0, NativeQuestRecord.REWARD_GRANTED);
    data.getQuests(Riiablo.ACT5)[Act5NihlathakQuest.RECORD] =
        Act5NihlathakQuest.start((short) 0);
    assertNull(QuestWarpPolicy.rejectionReason(data,
        D2LevelIds.LEVEL_HARROGATH, Act5NihlathakQuest.NIHLATHAK_TEMPLE));
  }

  @Test
  void a5LastPortalRequiresPrimaryGoalAndChamberSource() {
    CharData data = data();
    assertEquals("A5Q6_NOT_COMPLETE", QuestWarpPolicy.rejectionReason(data,
        Act5BaalQuest.WORLDSTONE_CHAMBER, Act5BaalQuest.HARROGATH));

    data.getQuests(Riiablo.ACT5)[Act5BaalQuest.RECORD] =
        NativeQuestRecord.set((short) 0, NativeQuestRecord.PRIMARY_GOAL_DONE);
    assertNull(QuestWarpPolicy.rejectionReason(data,
        Act5BaalQuest.WORLDSTONE_CHAMBER, Act5BaalQuest.HARROGATH));
    assertNull(QuestWarpPolicy.rejectionReason(null, 12345, 67890));
  }

  @Test
  void unknownOrOrdinaryWarpsAreNotAccidentallyGated() {
    assertNull(QuestWarpPolicy.rejectionReason(null, 12345, 67890));
  }

  private static CharData data() {
    return CharData.obtain().set(Riiablo.NORMAL, false, "WarpPolicy", Riiablo.AMAZON);
  }
}
