package com.riiablo.engine.server.quest;

import com.artemis.BaseSystem;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.save.CharData;
import com.riiablo.save.ItemData;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuestModuleIntegrationTest {
  private static final class Fixture {
    final short[][] records = new short[5][8];
    final CharData data = mock(CharData.class);
    final ItemData items = mock(ItemData.class);
    Fixture() {
      when(data.isExpansion()).thenReturn(true);
      when(data.getItems()).thenReturn(items);
      for (int a = 0; a < 5; a++) when(data.getQuests(a)).thenReturn(records[a]);
    }
    void accepts(int npcType, int message) {
      MonStats.Entry npc = new MonStats.Entry(); npc.hcIdx = npcType;
      assertTrue(QuestModule.isNpcMessageAllowed(null, npc, data, 30, false,
          Act5BaalQuest.HARROGATH, message), "npc=" + npcType + " message=" + message);
      assertFalse(QuestModule.isNpcMessageAllowed(null, npc, data, 30, false,
          Act5BaalQuest.HARROGATH, message + 1));
    }
  }

  @Test void allActOffersCanReachAuthorityValidation() {
    Fixture f = new Fixture();
    f.accepts(MonsterType.ATMA, Act2RadamentQuest.MESSAGE_INIT);
    when(f.items.containsItemCode("j34")).thenReturn(true);
    f.accepts(MonsterType.CAIN3, 527);
    f.accepts(MonsterType.MESHIF2, 529);
    f.accepts(MonsterType.HRATLI, 571);
    f.accepts(MonsterType.TYRAEL2, Act4IzualQuest.MESSAGE_TYRAEL_INIT);
    f.accepts(MonsterType.LARZUK, 20077);
    f.accepts(MonsterType.QUAL_KEHK, 20096);
    f.accepts(MonsterType.MALAH, Act5PrisonQuest.MESSAGE_MALAH_INIT);
    f.records[4][3] = Act5PrisonQuest.complete((short) 0);
    f.accepts(MonsterType.DREHYA, 20137);
    f.records[4][4] = Act5NihlathakQuest.complete((short) 0);
    f.accepts(MonsterType.DREHYA, 20148);
    f.records[4][2] = Act5RescueQuest.claimReward(Act5RescueQuest.complete((short) 0));
    f.accepts(MonsterType.QUAL_KEHK, 20153);
  }

  @Test void forgedA3ItemRewardAndAct5WrongLevelAreRejected() {
    Fixture f = new Fixture();
    assertFalse(Act3QuestMessageValidator.isAllowed(MonsterType.MESHIF2, f.data, 529));
    assertFalse(Act3QuestMessageValidator.isAllowed(MonsterType.ALKOR, f.data, 538));
    f.records[2][1] = Act3GoldenBirdQuest.acceptAtAlkor((short) 0);
    f.accepts(MonsterType.ALKOR, 538);
    assertFalse(Act5QuestMessageValidator.isAllowed(null, MonsterType.MALAH, f.data,
        Act5PrisonQuest.FROZEN_RIVER, Act5PrisonQuest.MESSAGE_MALAH_INIT));
    assertFalse(Act5QuestMessageValidator.isAllowed(null, MonsterType.DREHYA, f.data,
        Act5BaalQuest.HARROGATH, 20137));
  }

  @Test void freshAnyaStartAndNativeRemappedNpcRowsWork() {
    Fixture f = new Fixture();
    f.records[4][3] = Act5PrisonQuest.complete((short) 0);
    assertTrue(Act5QuestMessageValidator.isAllowed(null, MonsterType.DREHYA, f.data,
        Act5BaalQuest.HARROGATH, 20137));
    MonStats.Entry malah = new MonStats.Entry(); malah.Id = "Malah"; malah.hcIdx = 514;
    assertEquals(MonsterType.MALAH, QuestPlayerView.npcType(malah));
    MonStats.Entry anya = new MonStats.Entry(); anya.Id = "Drehya"; anya.hcIdx = 513;
    assertEquals(MonsterType.DREHYA, QuestPlayerView.npcType(anya));
  }

  @Test void worldsDoNotShareAuthorityInstances() {
    BaseSystem[] first = QuestModule.authoritySystems();
    BaseSystem[] second = QuestModule.authoritySystems();
    assertEquals(10, first.length);
    for (int i = 0; i < first.length; i++) {
      assertEquals(first[i].getClass(), second[i].getClass());
      assertNotSame(first[i], second[i]);
    }
  }

  @Test void classicCharacterCannotRequestExpansionPortal() {
    Fixture f = new Fixture();
    f.records[3][Act4DiabloQuest.RECORD] = NativeQuestRecord.set((short) 0,
        NativeQuestRecord.PRIMARY_GOAL_DONE);
    assertTrue(Act4QuestMessageValidator.isAllowed(MonsterType.TYRAEL2, f.data,
        Act4DiabloQuest.MESSAGE_TYRAEL_ACT5));
    when(f.data.isExpansion()).thenReturn(false);
    assertFalse(Act4QuestMessageValidator.isAllowed(MonsterType.TYRAEL2, f.data,
        Act4DiabloQuest.MESSAGE_TYRAEL_ACT5));
  }
}
