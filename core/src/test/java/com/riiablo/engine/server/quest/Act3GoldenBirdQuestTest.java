package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.engine.server.monster.MonsterType;
import org.junit.jupiter.api.Test;

/** Native A3Q1 (D2MOO A3Q4) Golden Bird record and resource mapping tests. */
class Act3GoldenBirdQuestTest {
  @Test
  void usesResourceTableQuestItemCodes() {
    assertTrue("j34".equals(Act3GoldenBirdQuest.JADE_FIGURINE));
    assertTrue("g34".equals(Act3GoldenBirdQuest.GOLDEN_BIRD));
    assertTrue("xyz".equals(Act3GoldenBirdQuest.POTION_OF_LIFE));
  }

  @Test
  void nativeRecordTransitionsAreOneShotAndOrdered() {
    short record = 0;
    record = Act3GoldenBirdQuest.start(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.STARTED));
    record = Act3GoldenBirdQuest.markJadePicked(record);
    record = Act3GoldenBirdQuest.exchangeForGoldenBird(record);
    record = Act3GoldenBirdQuest.acceptAtAlkor(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));

    short claimed = Act3GoldenBirdQuest.claimReward(record);
    assertTrue(Act3GoldenBirdQuest.isFinished(claimed));
    assertFalse(NativeQuestRecord.has(claimed, NativeQuestRecord.REWARD_PENDING));
    assertTrue(claimed == Act3GoldenBirdQuest.claimReward(claimed));
  }

  @Test
  void act3NpcClassIdsMatchNativeMonsterIds() {
    assertTrue(Act3GoldenBirdQuest.isCain(MonsterType.CAIN3));
    assertTrue(Act3GoldenBirdQuest.isMeshif(MonsterType.MESHIF2));
    assertTrue(Act3GoldenBirdQuest.isAlkor(MonsterType.ALKOR));
    assertFalse(Act3GoldenBirdQuest.isAlkor(MonsterType.MESHIF1));
  }
}
