package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NativeQuestRecordTest {
  @Test
  void usesNativeBitIndexes() {
    short record = 0;
    record = NativeQuestRecord.set(record, NativeQuestRecord.REWARD_GRANTED);
    record = NativeQuestRecord.set(record, NativeQuestRecord.PRIMARY_GOAL_DONE);
    record = NativeQuestRecord.set(record, NativeQuestRecord.COMPLETED_BEFORE);
    assertEquals(0xA001, Short.toUnsignedInt(record));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.COMPLETED_BEFORE));
  }

  @Test
  void resetsOnlyNativeIntermediateFlags() {
    short record = (short) 0xFFFF;
    short reset = NativeQuestRecord.resetIntermediate(record);
    assertEquals(0xF003, Short.toUnsignedInt(reset));
    assertTrue(NativeQuestRecord.has(reset, NativeQuestRecord.REWARD_GRANTED));
    assertTrue(NativeQuestRecord.has(reset, NativeQuestRecord.REWARD_PENDING));
    assertFalse(NativeQuestRecord.has(reset, NativeQuestRecord.STARTED));
    assertTrue(NativeQuestRecord.has(reset, NativeQuestRecord.UPDATE_QUEST_LOG));
  }

  @Test
  void rejectsFlagsOutsideSixteenBitRecord() {
    assertThrows(IllegalArgumentException.class,
        () -> NativeQuestRecord.set((short) 0, -1));
    assertThrows(IllegalArgumentException.class,
        () -> NativeQuestRecord.set((short) 0, 16));
  }
}
