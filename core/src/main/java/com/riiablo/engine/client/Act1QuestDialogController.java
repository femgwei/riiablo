package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.event.NpcInteractionEvent;
import com.riiablo.engine.server.event.NpcQuestMessageEvent;
import com.riiablo.engine.server.event.NativeImbueRequestEvent;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.engine.server.quest.Act1DenOfEvilQuest;
import com.riiablo.engine.server.quest.Act1BloodRavenQuest;
import com.riiablo.engine.server.quest.Act1MalusQuest;
import com.riiablo.engine.server.quest.Act1AndarielQuest;
import com.riiablo.engine.server.quest.Act1CainQuest;
import com.riiablo.engine.server.quest.NativeQuestRecord;
import com.riiablo.save.CharData;
import com.riiablo.widget.NpcDialogBox;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/** Presents the native Act 1 quest speech selected by the authoritative record. */
public class Act1QuestDialogController extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(Act1QuestDialogController.class);
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected DialogManager dialogManager;
  protected EventSystem events;

  @Subscribe
  public void onNpcInteraction(NpcInteractionEvent event) {
    if (event != null) openQuestDialog(event.entityId, event.npcId);
  }

  /** Opens the currently authoritative quest speech for an Act I NPC. */
  public boolean openQuestDialog(int playerId, int npcId) {
    if (dialogManager.getDialog() != null
        || !mPlayer.has(playerId) || !mMonster.has(npcId)) return false;

    Monster npc = mMonster.get(npcId);
    if (npc.monstats == null) return false;
    Player player = mPlayer.get(playerId);
    CharData data = player.data;
    if (data == null) return false;

    int messageIndex;
    String speech;
    if (npc.monstats.hcIdx == MonsterType.AKARA) {
      short record = data.getQuests(Riiablo.ACT1)[Act1DenOfEvilQuest.RECORD];
      short cainRecord = data.getQuests(Riiablo.ACT1)[Act1CainQuest.RECORD];
      if (Act1QuestPresentation.isComplete(record)) {
        messageIndex = NativeQuestRecord.has(cainRecord, NativeQuestRecord.REWARD_PENDING)
            ? Act1CainQuest.MESSAGE_REWARD
            : NativeQuestRecord.has(cainRecord, NativeQuestRecord.STARTED)
                ? Act1CainQuest.MESSAGE_EARLY : Act1CainQuest.MESSAGE_INIT;
        speech = NativeQuestRecord.has(cainRecord, NativeQuestRecord.REWARD_PENDING)
            ? "akara_act1_q4_success"
            : NativeQuestRecord.has(cainRecord, NativeQuestRecord.STARTED)
                ? "akara_act1_q4_early" : "akara_act1_q4_init";
      } else {
        messageIndex = Act1DenOfEvilQuest.selectAkaraMessage(record);
        speech = Act1DenOfEvilQuest.getAkaraSpeech(messageIndex);
      }
    } else if (npc.monstats.hcIdx == MonsterType.CHARSI) {
      short record = data.getQuests(Riiablo.ACT1)[Act1MalusQuest.RECORD];
      if (NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)) {
        com.riiablo.item.Item cursor = data.getItems().getCursor();
        if (cursor != null) {
          events.dispatch(NativeImbueRequestEvent.obtain(playerId, cursor.id));
        }
        return cursor != null;
      }
      int level = data.getStats().aggregate().getValue(Stat.level, 0);
      boolean hasMalus = data.getItems().containsItemCode(Act1MalusQuest.MALUS_CODE);
      messageIndex = Act1MalusQuest.selectCharsiMessage(record, level, hasMalus);
      speech = Act1MalusQuest.getCharsiSpeech(messageIndex);
    } else if (npc.monstats.hcIdx == MonsterType.KASHYA) {
      short record = data.getQuests(Riiablo.ACT1)[Act1BloodRavenQuest.RECORD];
      messageIndex = Act1BloodRavenQuest.selectKashyaMessage(record);
      speech = Act1BloodRavenQuest.getKashyaSpeech(messageIndex);
    } else if (npc.monstats.hcIdx == MonsterType.WARRIV) {
      short record = data.getQuests(Riiablo.ACT1)[Act1AndarielQuest.RECORD];
      if (NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)) {
        messageIndex = Act1AndarielQuest.MESSAGE_WARRIV_REWARD;
        speech = "warriv_act1_q6_success";
      } else {
        return false;
      }
    } else if (npc.monstats.hcIdx == MonsterType.DECKARDCAIN
        || npc.monstats.hcIdx == MonsterType.DECKARDCAIN_TOWN) {
      short cainRecord = data.getQuests(Riiablo.ACT1)[Act1CainQuest.RECORD];
      messageIndex = Act1CainQuest.MESSAGE_CAIN_TOWN;
      speech = NativeQuestRecord.has(cainRecord, NativeQuestRecord.REWARD_PENDING)
          ? "cain_act1_q4_success" : "cain_act1_q4_rescued_hero";
    } else {
      return false;
    }
    if (speech == null) return false;

    final int selectedMessage = messageIndex;
    log.info("[ACT1_QUEST_DIALOG] player={} npc={} message={} speech={}",
        playerId, npc.monstats.Id, messageIndex, speech);
    dialogManager.setDialog(new NpcDialogBox(speech, dialog -> {
      dialogManager.setDialog(null);
      events.dispatch(NpcQuestMessageEvent.obtain(playerId, npcId, selectedMessage));
    }));
    return true;
  }
}
