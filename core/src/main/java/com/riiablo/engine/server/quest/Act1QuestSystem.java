package com.riiablo.engine.server.quest;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import com.d2moo.common.drlg.D2LevelIds;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatListRef;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.event.NpcQuestMessageEvent;
import com.riiablo.engine.server.event.NativeQuestRewardEvent;
import com.riiablo.engine.server.event.QuestItemPickedUpEvent;
import com.riiablo.engine.server.event.QuestObjectInteractionEvent;
import com.riiablo.engine.server.event.ZoneChangeEvent;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.engine.server.object.NativeQuestObjectResolver;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import com.riiablo.save.D2SWriter;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/** Event adapter for native Act 1 quest scripts. */
public class Act1QuestSystem extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(Act1QuestSystem.class);

  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<Corpse> mCorpse;
  protected EventSystem event;

  private EntitySubscription monstersByZone;

  @Override
  protected void initialize() {
    monstersByZone = world.getAspectSubscriptionManager().get(
        Aspect.all(Monster.class, MapWrapper.class));
  }

  @Subscribe
  public void onZoneChanged(ZoneChangeEvent event) {
    if (event == null || event.zone == null || !mPlayer.has(event.entityId)) return;
    Player player = mPlayer.get(event.entityId);
    if (player.data == null || event.zone.level == null) return;

    int levelId = event.zone.level.Id;
    if (levelId == D2LevelIds.LEVEL_ROGUEENCAMPMENT) return;
    updateRecord(player.data, Act1DenOfEvilQuest::leaveTown, "left-town");
    if (levelId == D2LevelIds.LEVEL_DENOFEVIL) {
      updateRecord(player.data, Act1DenOfEvilQuest::enterDen, "entered-den");
    }
  }

  @Subscribe
  public void onMonsterKilled(DeathEvent event) {
    if (event == null || !isMonsterInLevel(event.victim, D2LevelIds.LEVEL_DENOFEVIL)) return;
    // DeathEvent is dispatched before the death animation installs Corpse.
    // Exclude this victim explicitly, while deriving every other monster's
    // state from its current corpse/life components. A Fallen revived by a
    // shaman therefore becomes part of the count again.
    int remaining = countLivingMonsters(D2LevelIds.LEVEL_DENOFEVIL, event.victim);
    log.info("[A1Q1] Den monster killed: killer={} victim={} remaining={}",
        event.killer, event.victim, remaining);
    if (remaining != 0 || !mPlayer.has(event.killer)) return;

    Player player = mPlayer.get(event.killer);
    if (player.data != null) {
      updateRecord(player.data, Act1DenOfEvilQuest::completeObjective,
          "objective-complete");
    }
  }

  @Subscribe
  public void onNpcQuestMessage(NpcQuestMessageEvent event) {
    if (event == null || !mPlayer.has(event.entityId) || !mMonster.has(event.npcId)) return;
    Monster npc = mMonster.get(event.npcId);
    if (npc.monstats == null) return;
    Player player = mPlayer.get(event.entityId);
    if (player.data == null) return;

    if (npc.monstats.hcIdx == MonsterType.CHARSI) {
      onCharsiMessage(event, player);
      return;
    }
    if (npc.monstats.hcIdx != MonsterType.AKARA) return;

    if (event.messageIndex == Act1DenOfEvilQuest.MESSAGE_INIT) {
      updateRecord(player.data, Act1DenOfEvilQuest::start, "akara-init-message");
    } else if (event.messageIndex == Act1DenOfEvilQuest.MESSAGE_SUCCESS) {
      short record = getRecord(player.data);
      if (!Act1DenOfEvilQuest.canClaimReward(record)) return;
      short claimed = Act1DenOfEvilQuest.claimReward(record);
      if (claimed != record) {
        setRecord(player.data, claimed);
        grantSkillPoint(event.entityId, player.data);
        persist(player.data);
        log.info("[A1Q1] Akara reward granted: player={}", event.entityId);
      }
    }
  }

  private void onCharsiMessage(NpcQuestMessageEvent message, Player player) {
    CharData data = player.data;
    short record = getMalusRecord(data);
    boolean hasMalus = data.getItems().containsItemCode(Act1MalusQuest.MALUS_CODE);
    if (message.messageIndex != Act1MalusQuest.MESSAGE_MALUS
        || !Act1MalusQuest.canTurnIn(record, level(message.entityId, player), hasMalus)) {
      return;
    }
    if (!data.getItems().removeItemCode(Act1MalusQuest.MALUS_CODE)) return;
    short pending = Act1MalusQuest.completeObjective(record);
    setMalusRecord(data, pending);
    persist(data);
    event.dispatch(NativeQuestRewardEvent.available(message.entityId,
        QuestId.A1Q3_MALUS, NativeQuestRewardEvent.CHARSI_IMBUE));
    log.info("[A1Q3] Charsi accepted Malus: player={}, rewardPending={}",
        message.entityId,
        NativeQuestRecord.has(pending, NativeQuestRecord.REWARD_PENDING));
  }

  @Subscribe
  public void onNativeQuestReward(NativeQuestRewardEvent reward) {
    if (reward == null || reward.phase != NativeQuestRewardEvent.GRANTED
        || reward.questId != QuestId.A1Q3_MALUS
        || reward.rewardKind != NativeQuestRewardEvent.CHARSI_IMBUE
        || !mPlayer.has(reward.playerId)) return;
    Player player = mPlayer.get(reward.playerId);
    if (player.data == null) return;
    updateMalusRecord(player.data, Act1MalusQuest::claimReward, "charsi-imbue-granted");
  }

  @Subscribe
  public void onQuestObjectInteraction(QuestObjectInteractionEvent event) {
    if (event == null
        || event.type != NativeQuestObjectResolver.Type.HORADRIC_MALUS
        || !mPlayer.has(event.playerId)) return;
    Player player = mPlayer.get(event.playerId);
    if (player.data == null) return;
    short record = getMalusRecord(player.data);
    if (!Act1MalusQuest.canOpenMalus(record, level(event.playerId, player))) return;
    setMalusRecord(player.data, Act1MalusQuest.leaveTown(record));
    persist(player.data);
    event.accept();
    log.info("[A1Q3] Malus stand accepted: player={} record=0x{}",
        event.playerId, Integer.toHexString(Short.toUnsignedInt(getMalusRecord(player.data))));
  }

  @Subscribe
  public void onQuestItemPickedUp(QuestItemPickedUpEvent event) {
    if (event == null || !Act1MalusQuest.MALUS_CODE.equalsIgnoreCase(event.itemCode)
        || !mPlayer.has(event.playerId)) return;
    Player player = mPlayer.get(event.playerId);
    if (player.data == null) return;
    updateMalusRecord(player.data, Act1MalusQuest::markMalusPickedUp, "malus-picked-up");
  }

  int countLivingMonsters(int levelId) {
    return countLivingMonsters(levelId, -1);
  }

  int countLivingMonsters(int levelId, int excludedEntityId) {
    IntBag entities = monstersByZone.getEntities();
    int[] ids = entities.getData();
    int remaining = 0;
    for (int i = 0, s = entities.size(); i < s; i++) {
      int entityId = ids[i];
      if (entityId != excludedEntityId
          && isMonsterInLevel(entityId, levelId)
          && isAlive(entityId)) {
        remaining++;
      }
    }
    return remaining;
  }

  private boolean isAlive(int entityId) {
    if (mCorpse.has(entityId)) return false;
    if (!mAttributesWrapper.has(entityId)) return true;
    Attributes attrs = mAttributesWrapper.get(entityId).attrs;
    if (attrs == null) return true;
    StatRef hitpoints = attrs.get(Stat.hitpoints, StatRef.obtain());
    return hitpoints == null || hitpoints.asFixed() > 0f;
  }

  private boolean isMonsterInLevel(int entityId, int levelId) {
    if (entityId < 0 || !mMonster.has(entityId) || !mMapWrapper.has(entityId)) return false;
    MapWrapper wrapper = mMapWrapper.get(entityId);
    Map.Zone zone = wrapper.zone;
    return zone != null && zone.level != null && zone.level.Id == levelId;
  }

  private void grantSkillPoint(int playerId, CharData data) {
    Attributes attrs = mAttributesWrapper.has(playerId)
        ? mAttributesWrapper.get(playerId).attrs : data.getStats();
    if (attrs == null) return;
    addOne(attrs.base());
    addOne(attrs.aggregate());
  }

  private static void addOne(StatListRef stats) {
    StatRef current = stats.get(Stat.newskills);
    stats.put(Stat.newskills, current == null ? 1 : current.asInt() + 1);
  }

  private interface RecordUpdate {
    short apply(short record);
  }

  private void updateRecord(CharData data, RecordUpdate update, String reason) {
    short previous = getRecord(data);
    short next = update.apply(previous);
    if (previous == next) return;
    setRecord(data, next);
    persist(data);
    log.info("[A1Q1] Quest record changed: character={} reason={} previous=0x{} next=0x{}",
        data.name, reason, Integer.toHexString(Short.toUnsignedInt(previous)),
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  private static short getRecord(CharData data) {
    return data.getQuests(Riiablo.ACT1)[Act1DenOfEvilQuest.RECORD];
  }

  private static short getMalusRecord(CharData data) {
    return data.getQuests(Riiablo.ACT1)[Act1MalusQuest.RECORD];
  }

  private static void setMalusRecord(CharData data, short record) {
    data.getQuests(Riiablo.ACT1)[Act1MalusQuest.RECORD] = record;
  }

  private void updateMalusRecord(CharData data, RecordUpdate update, String reason) {
    short previous = getMalusRecord(data);
    short next = update.apply(previous);
    if (previous == next) return;
    setMalusRecord(data, next);
    persist(data);
    log.info("[A1Q3] Quest record changed: character={} reason={} previous=0x{} next=0x{}",
        data.name, reason, Integer.toHexString(Short.toUnsignedInt(previous)),
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  private int level(int playerId, Player player) {
    Attributes attrs = mAttributesWrapper.has(playerId)
        ? mAttributesWrapper.get(playerId).attrs : player.data.getStats();
    if (attrs == null) return 0;
    StatRef level = attrs.get(Stat.level, StatRef.obtain());
    return level == null ? 0 : level.asInt();
  }

  private static void setRecord(CharData data, short record) {
    data.getQuests(Riiablo.ACT1)[Act1DenOfEvilQuest.RECORD] = record;
  }

  private static void persist(CharData data) {
    if (data.managed && Riiablo.saves != null) D2SWriter.INSTANCE.save(data);
  }
}
