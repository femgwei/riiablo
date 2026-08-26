package com.riiablo.engine.server.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NativeQuestRewardEventTest {
  @Test
  void distinguishesAvailableAndGrantedPhases() {
    NativeQuestRewardEvent available = NativeQuestRewardEvent.available(1, 3,
        NativeQuestRewardEvent.CHARSI_IMBUE);
    NativeQuestRewardEvent granted = NativeQuestRewardEvent.granted(1, 3,
        NativeQuestRewardEvent.CHARSI_IMBUE);
    assertEquals(NativeQuestRewardEvent.AVAILABLE, available.phase);
    assertEquals(NativeQuestRewardEvent.GRANTED, granted.phase);
  }
}
