package com.riiablo.engine.client;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.NativeImbueRequestEvent;
import com.riiablo.engine.server.event.NpcQuestMessageEvent;
import com.riiablo.net.packet.d2gs.QuestOperation;
import java.util.ArrayDeque;
import net.mostlyoriginal.api.event.common.EventSystem;

/** Captures dialogue/quest item intents; presentation cannot dispatch authority events. */
@Wire(failOnNull = false)
public final class ClientQuestIntentSystem extends BaseSystem {
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<UnitStates> mStates;
  protected ComponentMapper<com.riiablo.engine.server.component.NativePathIdentity> mIdentity;
  protected EventSystem events;
  protected ClientNetworkSynchronizer network;
  protected ClientNetworkReceiver receiver;
  protected ClientUnitLifecycleSystem lifecycle;
  private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
  @Wire(name = "simulationTickCoordinator", failOnNull = false)
  protected com.riiablo.engine.SimulationTickCoordinator simulationTickCoordinator;

  private void enqueue(Runnable intent, String kind) {
    if (simulationTickCoordinator != null) {
      simulationTickCoordinator.commands().submit(
          com.riiablo.engine.SimulationCommandEnvelope.builder("client-ui", kind, intent)
              .tick(simulationTickCoordinator.nextCommandTick()));
    } else {
      pending.add(intent);
    }
  }

  /** Local persistence of the journal acknowledgement stays with its Tick owner. */
  public boolean acknowledgeLog(int playerId) {
    if (playerId < 0 || network != null || replica(playerId) || !mPlayer.has(playerId)) return false;
    com.riiablo.save.CharData character = mPlayer.get(playerId).data;
    if (character == null) return false;
    final int difficulty = character.diff;
    final short[][] displayed = new short[5][];
    for (int act = 0; act < displayed.length; act++) {
      short[] records = character.getQuests(act);
      displayed[act] = records == null ? new short[0] : records.clone();
    }
    enqueue(() -> {
      if (replica(playerId) || !mPlayer.has(playerId) || mPlayer.get(playerId).data != character
          || character.diff != difficulty) return;
      for (int act = 0; act < 5; act++) {
        short[] records = character.getQuests(act);
        if (records == null) continue;
        for (int i = 0; i < Math.min(records.length, displayed[act].length); i++)
          if (records[i] != 0 && records[i] == displayed[act][i]) records[i] =
            com.riiablo.engine.server.quest.NativeQuestRecord.set(records[i],
                com.riiablo.engine.server.quest.NativeQuestRecord.UPDATE_QUEST_LOG);
      }
    }, "quest.log_ack");
    return true;
  }

  public boolean npcMessage(int playerId, int npcId, int message) {
    if (playerId < 0 || npcId < 0 || message < 0 || message > 0xffff
        || !mPlayer.has(playerId) || !mMonster.has(npcId)) return false;
    Player player = mPlayer.get(playerId);
    Monster npc = mMonster.get(npcId);
    com.riiablo.codec.excel.MonStats.Entry type = npc.monstats;
    com.riiablo.engine.server.component.NativePathIdentity.Token token = mIdentity != null && mIdentity.has(npcId)
        ? mIdentity.get(npcId).token() : null;
    com.riiablo.save.CharData character = player.data;
    if (character == null) return false;
    enqueue(() -> {
      if (!mPlayer.has(playerId) || mPlayer.get(playerId).data != character
          || !mMonster.has(npcId) || mMonster.get(npcId) != npc || npc.monstats != type
          || token != null && !token.isCurrent(world, npcId)
          || lifecycle != null && lifecycle.isPlayerDead(playerId)) return;
      if (network != null) network.requestQuest(QuestOperation.NPC_MESSAGE, npcId, message);
      else if (!replica(playerId) && events != null) {
        events.dispatch(NpcQuestMessageEvent.obtain(playerId, npcId, message));
      }
    }, "quest.npc_message");
    return true;
  }

  public boolean imbue(int playerId, int itemId) {
    // The existing protocol has no imbue request. Never run a local reward for
    // a network replica; adding that transport operation belongs to M6/M8.
    if (playerId < 0 || network != null || replica(playerId) || !mPlayer.has(playerId)) return false;
    com.riiablo.save.CharData character = mPlayer.get(playerId).data;
    if (character == null) return false;
    enqueue(() -> {
      if (!replica(playerId) && mPlayer.has(playerId) && mPlayer.get(playerId).data == character
          && events != null && (lifecycle == null || !lifecycle.isPlayerDead(playerId))) {
        events.dispatch(NativeImbueRequestEvent.obtain(playerId, itemId));
      }
    }, "quest.imbue");
    return true;
  }

  private boolean replica(int player) {
    return receiver != null || mStates.has(player) && mStates.get(player).snapshotOnly;
  }
  @Override protected void processSystem() {
    if (simulationTickCoordinator != null) return;
    for (int count = pending.size(); count > 0; count--) pending.removeFirst().run();
  }
  @Override protected void dispose() {
    pending.clear();
  }
}
