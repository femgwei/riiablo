package com.riiablo.presentation;

import com.riiablo.engine.client.ClientQuestIntentSystem;
import com.artemis.ComponentMapper;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.event.NpcInteractionEvent;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.engine.server.quest.Act2RadamentQuest;
import com.riiablo.save.CharData;
import com.riiablo.widget.NpcDialogBox;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuestDialogControllerTest {
  @SuppressWarnings("unchecked")
  private static QuestDialogController controller(short record, boolean admitted) {
    QuestDialogController controller = new QuestDialogController();
    controller.mPlayer = mock(ComponentMapper.class);
    controller.mMonster = mock(ComponentMapper.class);
    controller.mMapWrapper = mock(ComponentMapper.class);
    controller.dialogManager = mock(DialogManager.class);
    controller.questIntents = mock(ClientQuestIntentSystem.class);
    when(controller.questIntents.npcMessage(eq(1), eq(2), anyInt())).thenReturn(admitted);
    controller.act1 = mock(Act1QuestDialogController.class);
    Player player = new Player(); player.data = mock(CharData.class);
    for (int act = 0; act < 5; act++) when(player.data.getQuests(act)).thenReturn(new short[8]);
    player.data.getQuests(1)[1] = record;
    Monster monster = new Monster(); monster.monstats = new MonStats.Entry();
    monster.monstats.hcIdx = MonsterType.ATMA;
    when(controller.mPlayer.has(1)).thenReturn(true);
    when(controller.mPlayer.get(1)).thenReturn(player);
    when(controller.mMonster.has(2)).thenReturn(true);
    when(controller.mMonster.get(2)).thenReturn(monster);
    return controller;
  }

  @Test void atmaInteractionCapturesOneNativeMessageIntent() {
    QuestDialogController c = controller((short) 0, true);
    try (MockedConstruction<NpcDialogBox> dialogs = mockConstruction(NpcDialogBox.class,
        (dialog, context) -> assertEquals(304, context.arguments().get(0)))) {
      c.onNpcInteraction(NpcInteractionEvent.obatin(1, 2));
      verify(c.questIntents).npcMessage(1, 2, 304);
      assertEquals(1, dialogs.constructed().size());
      verify(c.dialogManager).setDialog(dialogs.constructed().get(0));
    }
  }

  @Test void rejectedIntentDoesNotOpenDialogue() {
    QuestDialogController c = controller((short) 0, false);
    try (MockedConstruction<NpcDialogBox> dialogs = mockConstruction(NpcDialogBox.class)) {
      assertFalse(c.openQuestDialog(1, 2));
      assertTrue(dialogs.constructed().isEmpty());
      verify(c.questIntents).npcMessage(1, 2, 304);
    }
  }

  @Test void earlySpeechRequiresExplicitMenuAndCompletedQuestDoesNotOpen() {
    QuestDialogController c = controller(Act2RadamentQuest.start((short) 0), true);
    c.onNpcInteraction(NpcInteractionEvent.obatin(1, 2));
    verifyNoInteractions(c.questIntents);
    try (MockedConstruction<NpcDialogBox> dialogs = mockConstruction(NpcDialogBox.class)) {
      assertTrue(c.openQuestDialog(1, 2));
      assertEquals(1, dialogs.constructed().size());
    }
    c = controller(Act2RadamentQuest.claimReward(Act2RadamentQuest.completeObjective((short) 0)), true);
    assertFalse(c.openQuestDialog(1, 2));
    verifyNoInteractions(c.questIntents);
  }
}
