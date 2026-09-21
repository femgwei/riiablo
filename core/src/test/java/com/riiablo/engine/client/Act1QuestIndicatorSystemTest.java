package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.engine.server.quest.Act1CainQuest;
import com.riiablo.engine.server.quest.Act1DenOfEvilQuest;
import com.riiablo.engine.server.quest.Act1MalusQuest;
import com.riiablo.engine.server.quest.NativeQuestRecord;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;

class Act1QuestIndicatorSystemTest extends RiiabloTest {
  @Test
  void akaraMarkerAdvancesFromDenToCainAndCainTownDoesNotDuplicateIt() {
    CharData data = CharData.obtain().clear()
        .set(Riiablo.NORMAL, false, "QuestMarker", Riiablo.AMAZON);

    assertTrue(Act1QuestIndicatorSystem.hasQuestMarker(MonsterType.AKARA, data));
    data.getQuests(Riiablo.ACT1)[Act1DenOfEvilQuest.RECORD] =
        Act1DenOfEvilQuest.claimReward(Act1DenOfEvilQuest.completeObjective((short) 0));
    assertTrue(Act1QuestIndicatorSystem.hasQuestMarker(MonsterType.AKARA, data));

    data.getQuests(Riiablo.ACT1)[Act1CainQuest.RECORD] = Act1CainQuest.start((short) 0);
    assertFalse(Act1QuestIndicatorSystem.hasQuestMarker(MonsterType.AKARA, data));
    assertFalse(Act1QuestIndicatorSystem.hasQuestMarker(MonsterType.DECKARDCAIN_TOWN, data));
  }

  @Test
  void charsiMarkerRequiresNativeMinimumLevel() {
    CharData data = CharData.obtain().clear()
        .set(Riiablo.NORMAL, false, "MalusMarker", Riiablo.AMAZON);
    data.getStats().base().put(Stat.level, 7);
    data.getStats().reset();

    assertFalse(Act1QuestIndicatorSystem.hasQuestMarker(MonsterType.CHARSI, data));
    data.getStats().base().put(Stat.level, Act1MalusQuest.MINIMUM_LEVEL);
    data.getStats().reset();
    assertTrue(Act1QuestIndicatorSystem.hasQuestMarker(MonsterType.CHARSI, data));

    data.getQuests(Riiablo.ACT1)[Act1MalusQuest.RECORD] =
        NativeQuestRecord.set((short) 0, NativeQuestRecord.STARTED);
    assertFalse(Act1QuestIndicatorSystem.hasQuestMarker(MonsterType.CHARSI, data));
  }

  @Test
  void freshCharacterShowsInitialAkaraAndWarrivMarkers() {
    CharData data = CharData.obtain().clear()
        .set(Riiablo.NORMAL, false, "FreshMarker", Riiablo.AMAZON);

    assertTrue(Act1QuestIndicatorSystem.hasQuestMarker(MonsterType.AKARA, data));
    assertTrue(Act1QuestIndicatorSystem.hasQuestMarker(MonsterType.WARRIV, data));

    data.getQuests(Riiablo.ACT1)[Act1DenOfEvilQuest.RECORD] =
        Act1DenOfEvilQuest.start((short) 0);
    assertFalse(Act1QuestIndicatorSystem.hasQuestMarker(MonsterType.WARRIV, data));
  }
}
