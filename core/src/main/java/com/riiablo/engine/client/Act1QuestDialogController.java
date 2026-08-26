package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.event.NpcInteractionEvent;
import com.riiablo.engine.server.event.NpcQuestMessageEvent;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.engine.server.quest.Act1DenOfEvilQuest;
import com.riiablo.engine.server.quest.Act1BloodRavenQuest;
import com.riiablo.engine.server.quest.Act1MalusQuest;
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
    if (npc.monstats == null) return;
    Player player = mPlayer.get(event.entityId);
    CharData data = player.data;
    if (data == null) return;

    int messageIndex;
    String speech;
    if (npc.monstats.hcIdx == MonsterType.AKARA) {
      short record = data.getQuests(Riiablo.ACT1)[Act1DenOfEvilQuest.RECORD];
      messageIndex = Act1DenOfEvilQuest.selectAkaraMessage(record);
      speech = Act1DenOfEvilQuest.getAkaraSpeech(messageIndex);
    } else if (npc.monstats.hcIdx == MonsterType.CHARSI) {
      short record = data.getQuests(Riiablo.ACT1)[Act1MalusQuest.RECORD];
      int level = data.getStats().aggregate().getValue(Stat.level, 0);
      boolean hasMalus = data.getItems().containsItemCode(Act1MalusQuest.MALUS_CODE);
      messageIndex = Act1MalusQuest.selectCharsiMessage(record, level, hasMalus);
      speech = Act1MalusQuest.getCharsiSpeech(messageIndex);
    } else if (npc.monstats.hcIdx == MonsterType.KASHYA) {
      short record = data.getQuests(Riiablo.ACT1)[Act1BloodRavenQuest.RECORD];
      messageIndex = Act1BloodRavenQuest.selectKashyaMessage(record);
      speech = Act1BloodRavenQuest.getKashyaSpeech(messageIndex);
    } else {
      return;
    }
    if (speech == null) return;

    final int playerId = event.entityId;
    final int npcId = event.npcId;
    dialogManager.setDialog(new NpcDialogBox(speech, dialog -> {
      dialogManager.setDialog(null);
      events.dispatch(NpcQuestMessageEvent.obtain(playerId, npcId, messageIndex));
    }));
  }
}
