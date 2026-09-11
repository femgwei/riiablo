package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.riiablo.Riiablo;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.event.NpcInteractionEvent;
import com.riiablo.engine.server.event.NpcQuestMessageEvent;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.engine.server.quest.Act2RadamentQuest;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.save.CharData;
import com.riiablo.widget.NpcDialogBox;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/** Presents Atma's native A2Q1 speech and submits its authoritative message id. */
@Wire(failOnNull = false)
public class Act2QuestDialogController extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(Act2QuestDialogController.class);

  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected DialogManager dialogManager;
  protected EventSystem events;
  protected ClientNetworkSynchronizer network;

  @Subscribe
  public void onNpcInteraction(NpcInteractionEvent event) {
    if (event != null) openQuestDialog(event.entityId, event.npcId);
  }

  public boolean openQuestDialog(int playerId, int npcId) {
    if (dialogManager == null || dialogManager.getDialog() != null
        || !mPlayer.has(playerId) || !mMonster.has(npcId)) return false;
    Monster npc = mMonster.get(npcId);
    Player player = mPlayer.get(playerId);
    CharData data = player == null ? null : player.data;
    if (npc == null || npc.monstats == null || npc.monstats.hcIdx != MonsterType.ATMA
        || data == null) return false;

    short record = data.getQuests(Riiablo.ACT2)[Act2RadamentQuest.RECORD];
    int message = Act2RadamentQuest.selectAtmaMessage(record);
    String speech = speech(message);
    if (speech == null) return false;

    log.info("[ACT2_QUEST_DIALOG] player={} npc={} message={} speech={}",
        playerId, npc.monstats.Id, message, speech);
    dialogManager.setDialog(new NpcDialogBox(speech, dialog -> {
      dialogManager.setDialog(null);
      if (network != null && network.requestQuest(
          com.riiablo.net.packet.d2gs.QuestOperation.NPC_MESSAGE,
          npcId, message) != 0) return;
      events.dispatch(NpcQuestMessageEvent.obtain(playerId, npcId, message));
    }));
    return true;
  }

  static String speech(int message) {
    switch (message) {
      case Act2RadamentQuest.MESSAGE_INIT: return "atma_act2_q1_init";
      case Act2RadamentQuest.MESSAGE_EARLY:
      case Act2RadamentQuest.MESSAGE_SEWERS: return "atma_act2_q1_early";
      case Act2RadamentQuest.MESSAGE_REWARD: return "atma_act2_q1_success";
      default: return null;
    }
  }
}
