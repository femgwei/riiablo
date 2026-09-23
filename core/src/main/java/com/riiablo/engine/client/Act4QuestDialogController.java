package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.riiablo.Riiablo;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.event.NpcInteractionEvent;
import com.riiablo.engine.server.event.NpcQuestMessageEvent;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.engine.server.quest.Act4HellforgeQuest;
import com.riiablo.engine.server.quest.Act4IzualQuest;
import com.riiablo.engine.server.quest.Act4DiabloQuest;
import com.riiablo.engine.server.quest.NativeQuestRecord;
import com.riiablo.save.CharData;
import com.riiablo.widget.NpcDialogBox;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/** Presents native A4Q3 Cain4 speech and submits the authoritative message id. */
@Wire(failOnNull = false)
public class Act4QuestDialogController extends PassiveSystem {
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
    if (npc == null || npc.monstats == null
        || (npc.monstats.hcIdx != MonsterType.CAIN4
            && npc.monstats.hcIdx != MonsterType.TYRAEL2)
        || data == null) return false;
    short[] act4 = data.getQuests(Riiablo.ACT4);
    if (act4 == null) return false;
    final int message;
    final String speech;
    if (npc.monstats.hcIdx == MonsterType.TYRAEL2) {
      if (act4.length <= Act4IzualQuest.RECORD) return false;
      short diablo = act4.length > Act4DiabloQuest.RECORD
          ? act4[Act4DiabloQuest.RECORD] : 0;
      short izual = act4[Act4IzualQuest.RECORD];
      if (NativeQuestRecord.has(izual, NativeQuestRecord.REWARD_PENDING)) {
        message = Act4IzualQuest.MESSAGE_TYRAEL_REWARD;
        speech = "tyrael_act4_q1_success";
      } else if (NativeQuestRecord.has(diablo, NativeQuestRecord.PRIMARY_GOAL_DONE)) {
        message = Act4DiabloQuest.MESSAGE_TYRAEL_ACT5;
        speech = "tyrael_act4_q2_success";
      } else {
        message = Act4IzualQuest.MESSAGE_TYRAEL_INIT;
        speech = "tyrael_act4_q1_init";
      }
    } else {
      if (act4.length <= Act4HellforgeQuest.RECORD) return false;
      short record = act4[Act4HellforgeQuest.RECORD];
      boolean hasStone = data.getItems() != null
          && data.getItems().containsItemCode(Act4HellforgeQuest.SOULSTONE);
      message = Act4HellforgeQuest.selectCainMessage(record, hasStone);
      speech = speech(message);
    }
    if (speech == null) return false;
    submitMessage(playerId, npcId, message);
    dialogManager.setDialog(new NpcDialogBox(speech, dialog -> {
      dialogManager.setDialog(null);
    }));
    return true;
  }

  private void submitMessage(int playerId, int npcId, int message) {
    if (network != null && network.requestQuest(
        com.riiablo.net.packet.d2gs.QuestOperation.NPC_MESSAGE, npcId, message) != 0) return;
    if (events != null) events.dispatch(NpcQuestMessageEvent.obtain(playerId, npcId, message));
  }

  static String speech(int message) {
    switch (message) {
      case Act4HellforgeQuest.MESSAGE_CAIN_INIT_NO_STONE: return "cain_act4_q3_init_no_stone";
      case Act4HellforgeQuest.MESSAGE_CAIN_INIT_HAS_STONE: return "cain_act4_q3_init_has_stone";
      case Act4HellforgeQuest.MESSAGE_CAIN_REWARD: return "cain_act4_q3_success";
      default: return null;
    }
  }
}
