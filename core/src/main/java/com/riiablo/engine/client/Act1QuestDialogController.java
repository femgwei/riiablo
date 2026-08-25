package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.riiablo.Riiablo;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.event.NpcInteractionEvent;
import com.riiablo.engine.server.event.NpcQuestMessageEvent;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.engine.server.quest.Act1DenOfEvilQuest;
import com.riiablo.save.CharData;
import com.riiablo.widget.NpcDialogBox;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/** Presents the native Act 1 quest speech selected by the authoritative record. */
public class Act1QuestDialogController extends PassiveSystem {
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected DialogManager dialogManager;
  protected EventSystem events;

  @Subscribe
  public void onNpcInteraction(NpcInteractionEvent event) {
    if (event == null || dialogManager.getDialog() != null
        || !mPlayer.has(event.entityId) || !mMonster.has(event.npcId)) {
      return;
    }

    Monster npc = mMonster.get(event.npcId);
    if (npc.monstats == null || npc.monstats.hcIdx != MonsterType.AKARA) return;
    Player player = mPlayer.get(event.entityId);
    CharData data = player.data;
    if (data == null) return;

    short record = data.getQuests(Riiablo.ACT1)[Act1DenOfEvilQuest.RECORD];
    int messageIndex = Act1DenOfEvilQuest.selectAkaraMessage(record);
    String speech = Act1DenOfEvilQuest.getAkaraSpeech(messageIndex);
    if (speech == null) return;

    final int playerId = event.entityId;
    final int npcId = event.npcId;
    dialogManager.setDialog(new NpcDialogBox(speech, dialog -> {
      dialogManager.setDialog(null);
      events.dispatch(NpcQuestMessageEvent.obtain(playerId, npcId, messageIndex));
    }));
  }
}
