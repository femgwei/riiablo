package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;

class Act2QuestMessageValidatorTest {
  @Test
  void acceptsSummonerRewardOnlyForNativeNpcAndPendingRecord() {
    CharData data = CharData.obtain().set(Riiablo.NORMAL, false, "A2Q5Validation", Riiablo.AMAZON);
    assertFalse(Act2QuestMessageValidator.isAllowed(MonsterType.ATMA, data, 419));
    assertFalse(Act2QuestMessageValidator.isAllowed(MonsterType.SUMMONER, data, 419));

    data.getQuests(Riiablo.ACT2)[Act2SummonerQuest.RECORD] =
        Act2SummonerQuest.completeObjective((short) 0);
    assertTrue(Act2QuestMessageValidator.isAllowed(MonsterType.ATMA, data, 419));
    assertTrue(Act2QuestMessageValidator.isAllowed(MonsterType.DECKARDCAIN_ACT2, data, 429));
    assertFalse(Act2QuestMessageValidator.isAllowed(MonsterType.AKARA, data, 419));
    assertFalse(Act2QuestMessageValidator.isAllowed(MonsterType.ATMA, data, 418));
    assertFalse(Act2QuestMessageValidator.isAllowed(MonsterType.ATMA, data, 430));
  }
}
