package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;

class Act1QuestMessageValidatorTest {
  @Test
  void rejectsForgedAkaraRewardAndAcceptsCurrentBranch() {
    CharData data = character();
    assertTrue(Act1QuestMessageValidator.isAllowed(MonsterType.AKARA, data, 1,
        false, Act1DenOfEvilQuest.MESSAGE_INIT));
    assertFalse(Act1QuestMessageValidator.isAllowed(MonsterType.AKARA, data, 1,
        false, Act1DenOfEvilQuest.MESSAGE_SUCCESS));

    data.getQuests(Riiablo.ACT1)[Act1DenOfEvilQuest.RECORD] =
        Act1DenOfEvilQuest.completeObjective((short) 0);
    assertTrue(Act1QuestMessageValidator.isAllowed(MonsterType.AKARA, data, 1,
        false, Act1DenOfEvilQuest.MESSAGE_SUCCESS));
    assertFalse(Act1QuestMessageValidator.isAllowed(MonsterType.AKARA, data, 1,
        false, Act1CainQuest.MESSAGE_REWARD));
  }

  @Test
  void validatesKashyaAndWarrivAgainstRewardPendingRecords() {
    CharData data = character();
    assertTrue(Act1QuestMessageValidator.isAllowed(MonsterType.KASHYA, data, 1,
        false, Act1BloodRavenQuest.MESSAGE_INIT));
    assertFalse(Act1QuestMessageValidator.isAllowed(MonsterType.KASHYA, data, 1,
        false, Act1BloodRavenQuest.MESSAGE_REWARD));

    data.getQuests(Riiablo.ACT1)[Act1AndarielQuest.RECORD] =
        Act1AndarielQuest.completePending((short) 0);
    assertTrue(Act1QuestMessageValidator.isAllowed(MonsterType.WARRIV, data, 1,
        false, Act1AndarielQuest.MESSAGE_WARRIV_REWARD));
    assertFalse(Act1QuestMessageValidator.isAllowed(MonsterType.WARRIV, data, 1,
        false, Act1CainQuest.MESSAGE_CAIN_TOWN));
  }

  @Test
  void requiresMalusTurnInRequirements() {
    CharData data = character();
    assertTrue(Act1QuestMessageValidator.isAllowed(MonsterType.CHARSI, data, 1,
        false, Act1MalusQuest.MESSAGE_INIT));
    assertFalse(Act1QuestMessageValidator.isAllowed(MonsterType.CHARSI, data, 8,
        false, Act1MalusQuest.MESSAGE_MALUS));
    data.getQuests(Riiablo.ACT1)[Act1MalusQuest.RECORD] = Act1MalusQuest.start((short) 0);
    assertTrue(Act1QuestMessageValidator.isAllowed(MonsterType.CHARSI, data, 8,
        true, Act1MalusQuest.MESSAGE_MALUS));
  }

  @Test
  void distinguishesRescuedCainFromTownCain() {
    CharData data = character();
    assertTrue(Act1QuestMessageValidator.isAllowed(MonsterType.DECKARDCAIN, data, 1,
        false, Act1CainQuest.MESSAGE_CAIN_TOWN));
    assertFalse(Act1QuestMessageValidator.isAllowed(MonsterType.DECKARDCAIN, data, 1,
        false, Act1CainQuest.MESSAGE_INIT));
    assertTrue(Act1QuestMessageValidator.isAllowed(MonsterType.DECKARDCAIN_TOWN, data, 1,
        false, Act1CainQuest.MESSAGE_CAIN_TOWN));
  }

  private static CharData character() {
    return CharData.obtain().set(Riiablo.NORMAL, false, "QuestValidation", Riiablo.AMAZON);
  }
}
