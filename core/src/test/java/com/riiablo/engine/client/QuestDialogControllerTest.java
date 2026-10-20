package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.event.NpcInteractionEvent;
import com.riiablo.engine.server.event.NpcQuestMessageEvent;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.engine.server.quest.Act2RadamentQuest;
import com.riiablo.save.CharData;
import com.riiablo.widget.NpcDialogBox;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class QuestDialogControllerTest {
  @SuppressWarnings("unchecked")
  private static QuestDialogController controller(short record, ClientNetworkSynchronizer network) {
    QuestDialogController controller = new QuestDialogController();
    controller.mPlayer = mock(ComponentMapper.class);
    controller.mMonster = mock(ComponentMapper.class);
    controller.mMapWrapper = mock(ComponentMapper.class);
    controller.dialogManager = mock(DialogManager.class);
    controller.events = mock(EventSystem.class);
    controller.network = network;
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

  @Test void localAtmaInteractionDispatchesNativeMessageExactlyOnce() {
    QuestDialogController c = controller((short) 0, null);
    try (MockedConstruction<NpcDialogBox> dialogs = mockConstruction(NpcDialogBox.class,
        (dialog, context) -> assertEquals(304, context.arguments().get(0)))) {
      c.onNpcInteraction(NpcInteractionEvent.obatin(1, 2));
      verify(c.events).dispatch(argThat((NpcQuestMessageEvent event) ->
          event.entityId == 1 && event.npcId == 2 && event.messageIndex == 304));
      assertEquals(1, dialogs.constructed().size());
      verify(c.dialogManager).setDialog(dialogs.constructed().get(0));
    }
  }

  @Test void remoteAtmaSubmitsIntentWithoutLocalMutationEvenWhenSendFails() {
    ClientNetworkSynchronizer network = mock(ClientNetworkSynchronizer.class);
    QuestDialogController c = controller((short) 0, network);
    assertFalse(c.openQuestDialog(1, 2));
    verifyNoInteractions(c.events);
    when(network.requestQuest(anyByte(), eq(2), eq(304))).thenReturn(12L);
    try (MockedConstruction<NpcDialogBox> dialogs = mockConstruction(NpcDialogBox.class)) {
      assertTrue(c.openQuestDialog(1, 2));
      assertEquals(1, dialogs.constructed().size());
      verifyNoInteractions(c.events);
    }
  }

  @Test void earlySpeechRequiresExplicitQuestMenuAndCompletedQuestDoesNotOpen() {
    QuestDialogController c = controller(Act2RadamentQuest.start((short) 0), null);
    c.onNpcInteraction(NpcInteractionEvent.obatin(1, 2));
    verifyNoInteractions(c.events);
    try (MockedConstruction<NpcDialogBox> dialogs = mockConstruction(NpcDialogBox.class)) {
      assertTrue(c.openQuestDialog(1, 2));
      assertEquals(1, dialogs.constructed().size());
    }
    c = controller(Act2RadamentQuest.claimReward(Act2RadamentQuest.completeObjective((short) 0)), null);
    assertFalse(c.openQuestDialog(1, 2));
    verifyNoInteractions(c.events);
  }
}
