package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Record and resource-code checks for project A3Q2 / native D2MOO A3Q3. */
class Act3GidbinnQuestTest {
  @Test
  void usesLittleEndianResourceCodes() {
    assertTrue("g33".equals(Act3GidbinnQuest.GIDBINN));
    assertTrue("rin".equals(Act3GidbinnQuest.ORMUS_REWARD));
  }

  @Test
  void bothIndependentRewardsAreRequiredForCompletion() {
    short record = Act3GidbinnQuest.markBroughtToOrmus(
        Act3GidbinnQuest.enterArea(Act3GidbinnQuest.start((short) 0)));
    record = Act3GidbinnQuest.markOrmusReward(record);
    assertFalse(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED));
    record = Act3GidbinnQuest.markAshearaReward(record);
    record = Act3GidbinnQuest.completeIfBothRewards(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED));
    assertTrue(record == Act3GidbinnQuest.completeIfBothRewards(record));
  }
}
