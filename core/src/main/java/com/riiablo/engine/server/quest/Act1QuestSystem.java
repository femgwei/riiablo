package com.riiablo.engine.server.quest;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
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
import com.riiablo.engine.server.event.NativeCainQuestEvent;
import com.riiablo.engine.server.event.QuestItemPickedUpEvent;
import com.riiablo.engine.server.event.QuestObjectInteractionEvent;
import com.riiablo.engine.server.event.ZoneChangeEvent;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.engine.server.object.NativeQuestObjectResolver;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.Quality;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import com.riiablo.save.D2SWriter;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/** Event adapter for native Act 1 quest scripts. */
@Wire(failOnNull = false)
public class Act1QuestSystem extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(Act1QuestSystem.class);

  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<Corpse> mCorpse;
  protected EventSystem event;
  @Wire(failOnNull = false)
  protected ItemGenerator itemGenerator;

  private EntitySubscription monstersByZone;
  private final Act1CainRuntime cainRuntime = new Act1CainRuntime();

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

    if (event.messageIndex == Act1CainQuest.MESSAGE_DECIPHER_SCROLL) {
      decipherInifussScroll(event.entityId, player);
      return;
    }

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
        || (event.type != NativeQuestObjectResolver.Type.HORADRIC_MALUS
            && event.type != NativeQuestObjectResolver.Type.CAIRN_STONE
            && event.type != NativeQuestObjectResolver.Type.INIFUSS_TREE
            && event.type != NativeQuestObjectResolver.Type.CAIN_GIBBET)
        || !mPlayer.has(event.playerId)) return;
    Player player = mPlayer.get(event.playerId);
    if (player.data == null) return;
    if (event.type == NativeQuestObjectResolver.Type.CAIRN_STONE
        || event.type == NativeQuestObjectResolver.Type.INIFUSS_TREE
        || event.type == NativeQuestObjectResolver.Type.CAIN_GIBBET) {
      handleCainObject(event, player);
      return;
    }
    short record = getMalusRecord(player.data);
    if (!Act1MalusQuest.canOpenMalus(record, level(event.playerId, player))) return;
    setMalusRecord(player.data, Act1MalusQuest.leaveTown(record));
    persist(player.data);
    event.accept();
    log.info("[A1Q3] Malus stand accepted: player={} record=0x{}",
        event.playerId, Integer.toHexString(Short.toUnsignedInt(getMalusRecord(player.data))));
  }

  private void handleCainObject(QuestObjectInteractionEvent interaction, Player player) {
    short record = player.data.getQuests(Riiablo.ACT1)[Act1CainQuest.RECORD];
    short next;
    NativeCainQuestEvent request;
    if (interaction.type == NativeQuestObjectResolver.Type.CAIRN_STONE) {
      if (!player.data.getItems().containsItemCode(Act1CainQuest.DECIPHERED_SCROLL_CODE)) return;
      MapWrapper wrapper = mMapWrapper.get(interaction.entityId);
      long gameSeed = wrapper == null || wrapper.map == null ? 0L : wrapper.map.seed();
      cainRuntime.initialize(gameSeed);
      Act1CainRuntime.StoneResult result = cainRuntime.inspect(interaction.objectClassId);
      if (result == Act1CainRuntime.StoneResult.WRONG
          || result == Act1CainRuntime.StoneResult.COMPLETE) {
        log.info("[A1Q4] Cairn stone ignored: player={}, object={}, operated={}, expected={}",
            interaction.playerId, interaction.objectClassId, cainRuntime.operated(),
            expectedCainStone());
        return;
      }

      request = NativeCainQuestEvent.obtain(interaction.playerId, interaction.entityId,
          interaction.objectClassId, NativeCainQuestEvent.CAIRN_STONE);
      request.stoneObjectId = interaction.objectClassId;
      request.stoneIndex = cainRuntime.operated();
      if (result == Act1CainRuntime.StoneResult.LAST_STONE) {
        NativeCainQuestEvent portal = NativeCainQuestEvent.obtain(interaction.playerId,
            interaction.entityId, interaction.objectClassId,
            NativeCainQuestEvent.PORTAL_TO_TRISTRAM);
        portal.stoneObjectId = interaction.objectClassId;
        portal.stoneIndex = cainRuntime.operated();
        portal.destinationLevelId = D2LevelIds.LEVEL_TRISTRAM;
        event.dispatch(portal);
        if (!portal.accepted
            || !player.data.getItems().removeItemCode(Act1CainQuest.DECIPHERED_SCROLL_CODE)) {
          log.warn("[A1Q4] final Cairn stone waiting for portal service: player={}, object={}",
              interaction.playerId, interaction.objectClassId);
          return;
        }
        cainRuntime.markPortalOpened();
        next = Act1CainQuest.openTristramPortal(record);
      } else {
        cainRuntime.advance();
        next = Act1CainQuest.enterDarkWood(record);
      }
      request.accept();
    } else if (interaction.type == NativeQuestObjectResolver.Type.INIFUSS_TREE) {
      if (Act1CainQuest.isFinished(record)
          || player.data.getItems().containsItemCode(Act1CainQuest.BARK_SCROLL_CODE)
          || player.data.getItems().containsItemCode(Act1CainQuest.DECIPHERED_SCROLL_CODE)) return;
      next = Act1CainQuest.acquireScroll(record);
      request = NativeCainQuestEvent.obtain(interaction.playerId, interaction.entityId,
          interaction.objectClassId, NativeCainQuestEvent.INIFUSS_TREE);
    } else {
      if (!NativeQuestRecord.has(record, NativeQuestRecord.ENTERED_AREA)
          || !isPlayerInLevel(interaction.playerId, D2LevelIds.LEVEL_TRISTRAM)) return;
      next = Act1CainQuest.releaseCain(record);
      request = NativeCainQuestEvent.obtain(interaction.playerId, interaction.entityId,
          interaction.objectClassId, NativeCainQuestEvent.CAIN_GIBBET);
    }
    event.dispatch(request);
    if (!request.accepted) return;
    if (request.action == NativeCainQuestEvent.CAIN_GIBBET) cainRuntime.markCainReleased();
    player.data.getQuests(Riiablo.ACT1)[Act1CainQuest.RECORD] = next;
    persist(player.data);
    interaction.accepted = request.accepted;
    log.info("[A1Q4] object action: player={}, object={}, action={}, accepted={}, record=0x{}",
        interaction.playerId, interaction.objectClassId, request.action, request.accepted,
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  @Subscribe
  public void onQuestItemPickedUp(QuestItemPickedUpEvent event) {
    if (event == null || !mPlayer.has(event.playerId)) return;
    Player player = mPlayer.get(event.playerId);
    if (player.data == null) return;
    if (Act1MalusQuest.MALUS_CODE.equalsIgnoreCase(event.itemCode)) {
      updateMalusRecord(player.data, Act1MalusQuest::markMalusPickedUp, "malus-picked-up");
    } else if (Act1CainQuest.BARK_SCROLL_CODE.equalsIgnoreCase(event.itemCode)
        || Act1CainQuest.DECIPHERED_SCROLL_CODE.equalsIgnoreCase(event.itemCode)) {
      updateCainRecord(player.data, Act1CainQuest::acquireScroll, "inifuss-scroll-picked-up");
    }
  }

  private void decipherInifussScroll(int playerId, Player player) {
    short record = getCainRecord(player.data);
    boolean hasBark = player.data.getItems().containsItemCode(Act1CainQuest.BARK_SCROLL_CODE);
    boolean hasKey = player.data.getItems().containsItemCode(Act1CainQuest.DECIPHERED_SCROLL_CODE);
    if (!Act1CainQuest.canDecipherScroll(record, hasBark, hasKey)) return;
    if (itemGenerator == null) {
      log.warn("[A1Q4] cannot decipher Scroll of Inifuss: item generator unavailable, player={}",
          playerId);
      return;
    }

    final Item deciphered;
    try {
      deciphered = itemGenerator.generate(Act1CainQuest.DECIPHERED_SCROLL_CODE);
      deciphered.version = Item.VERSION_110;
      deciphered.ilvl = 1;
      deciphered.quality = Quality.NORMAL;
      deciphered.flags |= Item.ITEMFLAG_IDENTIFIED;
      deciphered.attrs.base().put(Stat.questitemdifficulty,
          Math.max(0, Math.min(player.data.diff, 2)));
      deciphered.attrs.reset();
    } catch (Throwable t) {
      log.error("[A1Q4] failed to create deciphered Scroll of Inifuss: player={}", playerId, t);
      return;
    }

    if (!player.data.getItems().replaceItemCode(Act1CainQuest.BARK_SCROLL_CODE, deciphered)) {
      log.warn("[A1Q4] bark scroll disappeared before conversion: player={}", playerId);
      return;
    }
    setCainRecord(player.data, Act1CainQuest.acquireScroll(record));
    persist(player.data);
    log.info("[A1Q4] Akara deciphered Scroll of Inifuss: player={}", playerId);
  }

  private int expectedCainStone() {
    int[] order = cainRuntime.stoneOrder();
    int operated = cainRuntime.operated();
    return operated >= 0 && operated < order.length ? order[operated] : -1;
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

  private boolean isPlayerInLevel(int entityId, int levelId) {
    if (!mMapWrapper.has(entityId)) return false;
    MapWrapper wrapper = mMapWrapper.get(entityId);
    return wrapper != null && wrapper.zone != null && wrapper.zone.level != null
        && wrapper.zone.level.Id == levelId;
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

  private static short getCainRecord(CharData data) {
    return data.getQuests(Riiablo.ACT1)[Act1CainQuest.RECORD];
  }

  private static void setCainRecord(CharData data, short record) {
    data.getQuests(Riiablo.ACT1)[Act1CainQuest.RECORD] = record;
  }

  private void updateCainRecord(CharData data, RecordUpdate update, String reason) {
    short previous = getCainRecord(data);
    short next = update.apply(previous);
    if (previous == next) return;
    setCainRecord(data, next);
    persist(data);
    log.info("[A1Q4] Quest record changed: character={} reason={} previous=0x{} next=0x{}",
        data.name, reason, Integer.toHexString(Short.toUnsignedInt(previous)),
        Integer.toHexString(Short.toUnsignedInt(next)));
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
