package com.riiablo.presentation;

import com.riiablo.engine.client.ClientNetworkSynchronizer;

import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.event.NpcInteractionEvent;
import com.riiablo.engine.server.event.NpcQuestMessageEvent;
import com.riiablo.engine.server.quest.QuestDialogue;
import com.riiablo.engine.server.quest.QuestPlayerView;
import com.riiablo.net.packet.d2gs.QuestOperation;
import com.riiablo.widget.NpcDialogBox;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/** One presentation/submission entry for A1-A5, local authority or remote replica. */
@Wire(failOnNull = false)
public class QuestDialogController extends PassiveSystem {
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected Act1QuestDialogController act1;
  protected DialogManager dialogManager;
  protected EventSystem events;
  protected ClientNetworkSynchronizer network;

  @Subscribe public void onNpcInteraction(NpcInteractionEvent event) {
    if (event != null) openQuestDialog(event.entityId, event.npcId, true);
  }

  public boolean openQuestDialog(int playerId, int npcId) {
    return openQuestDialog(playerId, npcId, false);
  }

  private boolean openQuestDialog(int playerId, int npcId, boolean automatic) {
    if (dialogManager == null || dialogManager.getDialog() != null
        || !mPlayer.has(playerId) || !mMonster.has(npcId)) return false;
    if (act1 != null && act1.openQuestDialog(playerId, npcId)) return true;
    Player player = mPlayer.get(playerId);
    Monster npc = mMonster.get(npcId);
    if (player.data == null || npc.monstats == null) return false;
    MapWrapper map = mMapWrapper.has(npcId) ? mMapWrapper.get(npcId) : null;
    int level = map == null || map.zone == null || map.zone.level == null ? -1 : map.zone.level.Id;
    QuestDialogue.Offer offer = QuestDialogue.select(QuestPlayerView.npcType(npc.monstats),
        new QuestPlayerView(player.data), level);
    if (offer == null || (automatic && !offer.attention)) return false;
    // Remote clients submit only; they never dispatch authority events locally.
    if (network != null) {
      if (network.requestQuest(QuestOperation.NPC_MESSAGE, npcId, offer.message) == 0) return false;
    } else if (events != null) {
      events.dispatch(NpcQuestMessageEvent.obtain(playerId, npcId, offer.message));
    } else return false;
    dialogManager.setDialog(new NpcDialogBox(offer.message, dialog -> dialogManager.setDialog(null)));
    return true;
  }
}
