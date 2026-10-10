package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.riiablo.Riiablo;
import com.riiablo.CharacterClass;
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
import com.riiablo.engine.server.quest.Act1NaviQuest;
import com.riiablo.engine.server.quest.NativeQuestRecord;
import com.riiablo.engine.server.quest.Act1WarrivIntroQuest;
import com.riiablo.save.CharData;
import com.riiablo.widget.NpcDialogBox;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/** Presents the native Act 1 quest speech selected by the authoritative record. */
@Wire(failOnNull = false)
public class Act1QuestDialogController extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(Act1QuestDialogController.class);
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected DialogManager dialogManager;
  protected EventSystem events;
  protected ClientNetworkSynchronizer network;

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
        boolean hasBarkScroll = data.getItems() != null
            && data.getItems().containsItemCode(Act1CainQuest.BARK_SCROLL_CODE);
        boolean hasDecipheredScroll = data.getItems() != null
            && data.getItems().containsItemCode(Act1CainQuest.DECIPHERED_SCROLL_CODE);
        if (NativeQuestRecord.has(cainRecord, NativeQuestRecord.REWARD_PENDING)) {
          messageIndex = Act1CainQuest.MESSAGE_REWARD;
          speech = "akara_act1_q4_success";
        } else if (Act1CainQuest.canDecipherScroll(
            cainRecord, hasBarkScroll, hasDecipheredScroll)) {
          messageIndex = Act1CainQuest.MESSAGE_DECIPHER_SCROLL;
          speech = "akara_act1_q4_after_scroll";
        } else if (NativeQuestRecord.has(cainRecord, NativeQuestRecord.REWARD_GRANTED)
            || NativeQuestRecord.has(cainRecord, NativeQuestRecord.COMPLETED_BEFORE)
            || cainRecord != 0) {
          // D2MOO only activates the A1Q4 Akara speech for a new intro,
          // Scroll of Inifuss decoding, or a pending reward.  A started
          // quest therefore falls through to the ordinary NPC dialog instead
          // of repeating "rescue Cain" on every interaction.
          return false;
        } else {
          messageIndex = Act1CainQuest.MESSAGE_INIT;
          speech = "akara_act1_q4_init";
        }
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
      short denRecord = data.getQuests(Riiablo.ACT1)[Act1DenOfEvilQuest.RECORD];
      if (!Act1DenOfEvilQuest.unlocksNextQuest(denRecord)) return false;
      short record = data.getQuests(Riiablo.ACT1)[Act1BloodRavenQuest.RECORD];
      messageIndex = Act1BloodRavenQuest.selectKashyaMessage(record);
      speech = Act1BloodRavenQuest.getKashyaSpeech(messageIndex);
    } else if (npc.monstats.hcIdx == MonsterType.WARRIV) {
      short introRecord = data.getQuests(Riiablo.ACT1)[Act1WarrivIntroQuest.RECORD];
      if (Act1WarrivIntroQuest.isActive(introRecord)) {
        messageIndex = data.classId == CharacterClass.PALADIN
            ? Act1WarrivIntroQuest.MESSAGE_PALADIN
            : Act1WarrivIntroQuest.MESSAGE_NORMAL;
        speech = messageIndex == Act1WarrivIntroQuest.MESSAGE_PALADIN
            ? "warriv_act1_intro_pal" : "warriv_act1_intro";
      } else {
        short record = data.getQuests(Riiablo.ACT1)[Act1AndarielQuest.RECORD];
        if (!NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)) return false;
        messageIndex = Act1AndarielQuest.MESSAGE_WARRIV_REWARD;
        speech = "warriv_act1_q6_success";
      }
    } else if (npc.monstats.hcIdx == MonsterType.DECKARDCAIN
        || npc.monstats.hcIdx == MonsterType.DECKARDCAIN_TOWN) {
      short cainRecord = data.getQuests(Riiablo.ACT1)[Act1CainQuest.RECORD];
      messageIndex = Act1CainQuest.MESSAGE_CAIN_TOWN;
      speech = NativeQuestRecord.has(cainRecord, NativeQuestRecord.REWARD_PENDING)
          ? "cain_act1_q4_success" : "cain_act1_q4_rescued_hero";
    } else if (npc.monstats.hcIdx == MonsterType.NAVI) {
      short denRecord = data.getQuests(Riiablo.ACT1)[Act1DenOfEvilQuest.RECORD];
      messageIndex = Act1NaviQuest.select(denRecord, playerId ^ npcId);
      speech = Act1NaviQuest.speech(messageIndex);
    } else {
      return false;
    }
    if (speech == null) return false;

    log.info("[ACT1_QUEST_DIALOG] player={} npc={} message={} speech={}",
        playerId, npc.monstats.Id, messageIndex, speech);
    submitMessage(playerId, npcId, messageIndex);
    String fallback = npc.monstats.hcIdx == MonsterType.NAVI
        ? Riiablo.bundle.get(
            messageIndex < Act1NaviQuest.MESSAGE_AFTER_0
                ? "flavie_a1q1_warning" : "flavie_a1q1_after") : null;
    dialogManager.setDialog(new NpcDialogBox(speech, fallback, dialog -> {
      dialogManager.setDialog(null);
    }));
    return true;
  }

  private void submitMessage(int playerId, int npcId, int messageIndex) {
    if (messageIndex < 0) return;
    if (network != null && network.requestQuest(
        com.riiablo.net.packet.d2gs.QuestOperation.NPC_MESSAGE,
        npcId, messageIndex) != 0) return;
    // Offline/single-player worlds retain the local event path.  Dispatch at
    // dialog open, matching D2: text scrolling is presentation only and must
    // not delay quest acceptance or reward delivery.
    if (events != null) {
      events.dispatch(NpcQuestMessageEvent.obtain(playerId, npcId, messageIndex));
    }
  }

}
