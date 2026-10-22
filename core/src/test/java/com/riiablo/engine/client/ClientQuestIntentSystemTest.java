package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.NpcQuestMessageEvent;
import com.riiablo.net.packet.d2gs.QuestOperation;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class ClientQuestIntentSystemTest {
  @Test void journalAcknowledgementIsDeferredAndDoesNotAcknowledgeUnseenProgress() {
    try (Fixture f = new Fixture()) {
      CharData data = f.world.getMapper(Player.class).get(f.player).data;
      short[] records = new short[] {1, 2};
      when(data.getQuests(0)).thenReturn(records);
      assertTrue(f.system.acknowledgeLog(f.player));
      assertEquals(1, records[0]);
      records[1] = 4;
      f.world.process();
      assertTrue(com.riiablo.engine.server.quest.NativeQuestRecord.has(records[0],
          com.riiablo.engine.server.quest.NativeQuestRecord.UPDATE_QUEST_LOG));
      assertEquals(4, records[1]);
      f.world.getMapper(UnitStates.class).create(f.player).snapshotOnly = true;
      assertFalse(f.system.acknowledgeLog(f.player));
    }
  }

  @Test void reusedNpcIdentityCannotExecuteQueuedDialogue() {
    try (Fixture f = new Fixture()) {
      com.riiablo.engine.server.component.NativePathIdentity identity = f.world.getMapper(
          com.riiablo.engine.server.component.NativePathIdentity.class).create(f.npc).allocated(f.world, f.npc);
      f.system.npcMessage(f.player, f.npc, 304);
      identity.allocated(f.world, f.npc);
      f.world.process();
      verifyNoInteractions(f.system.events);
    }
  }
  @Test void localDialogueDispatchesOnlyDuringTick() {
    try (Fixture f = new Fixture()) {
      assertTrue(f.system.npcMessage(f.player, f.npc, 304));
      verifyNoInteractions(f.system.events);
      f.world.process();
      verify(f.system.events).dispatch(argThat((NpcQuestMessageEvent e) ->
          e.entityId == f.player && e.npcId == f.npc && e.messageIndex == 304));
    }
  }
  @Test void networkDialogueNeverDispatchesLocalRewardEvent() {
    try (Fixture f = new Fixture()) {
      f.system.network = mock(ClientNetworkSynchronizer.class);
      f.world.getMapper(UnitStates.class).create(f.player).snapshotOnly = true;
      assertTrue(f.system.npcMessage(f.player, f.npc, 304));
      assertFalse(f.system.imbue(f.player, 41));
      f.world.process();
      verify(f.system.network).requestQuest(QuestOperation.NPC_MESSAGE, f.npc, 304);
      verifyNoInteractions(f.system.events);
    }
  }
  @Test void changedPlayerAndReplicaWithoutTransportCannotGrantLocalRewards() {
    try (Fixture f = new Fixture()) {
      f.system.npcMessage(f.player, f.npc, 304);
      f.world.getMapper(Player.class).get(f.player).data = mock(CharData.class);
      f.world.process();
      verifyNoInteractions(f.system.events);
      f.world.getMapper(UnitStates.class).create(f.player).snapshotOnly = true;
      assertTrue(f.system.npcMessage(f.player, f.npc, 304));
      f.world.process();
      verifyNoInteractions(f.system.events);
    }
  }
  private static final class Fixture implements AutoCloseable {
    final ClientQuestIntentSystem system = new ClientQuestIntentSystem();
    final World world = new World(new WorldConfigurationBuilder().with(system).build());
    final int player = world.create(), npc = world.create();
    Fixture() {
      world.getMapper(Player.class).create(player).data = mock(CharData.class);
      world.getMapper(Monster.class).create(npc);
      system.events = mock(EventSystem.class); world.process();
    }
    @Override public void close() { world.dispose(); }
  }
}
