package com.riiablo.engine.server.quest;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntSet;
import com.d2moo.common.drlg.D2LevelIds;
import com.riiablo.Riiablo;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.event.NpcQuestMessageEvent;
import com.riiablo.engine.server.event.QuestItemPickedUpEvent;
import com.riiablo.engine.server.event.ZoneChangeEvent;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.engine.server.party.Party;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.Quality;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import com.riiablo.save.D2SWriter;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/**
 * Event adapter for the native Act III Golden Bird quest.
 *
 * <p>The original D2Game names this script A3Q4 because the first three
 * entries are the intro, Golden Bird is the fourth native quest slot. The
 * Java save table exposes it as {@link QuestId#A3Q1_GOLDEN_BIRD}; this class
 * deliberately keeps that project-facing id while matching the native item
 * and dialogue transitions.</p>
 */
@Wire(failOnNull = false)
public class Act3QuestSystem extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(Act3QuestSystem.class);

  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Position> mPosition;
  @Wire(failOnNull = false)
  protected ItemGenerator itemGenerator;
  @Wire(name = "factory", failOnNull = false)
  protected EntityFactory factory;
  @Wire(name = "partyManager", failOnNull = false)
  protected PartyManager partyManager;

  private EntitySubscription playersByZone;
  private final IntSet jadeDropVictims = new IntSet();
  private boolean jadeDropIssued;

  @Override
  protected void initialize() {
    playersByZone = world.getAspectSubscriptionManager().get(
        Aspect.all(Player.class, MapWrapper.class));
  }

  @Subscribe
  public void onZoneChanged(ZoneChangeEvent event) {
    if (event == null || event.zone == null || event.zone.level == null
        || !mPlayer.has(event.entityId)) return;
    Player player = mPlayer.get(event.entityId);
    if (player == null || player.data == null || !isAct3Level(event.zone.level.Id)) return;
    short previous = record(player.data);
    short next = Act3GoldenBirdQuest.enterArea(previous);
    if (next != previous) updateRecord(player.data, next, "entered-act3");
  }

  @Subscribe
  public void onMonsterKilled(DeathEvent event) {
    if (event == null || event.victim < 0 || jadeDropIssued
        || !mMonster.has(event.victim) || !mPosition.has(event.victim)) return;
    Monster monster = mMonster.get(event.victim);
    if (monster == null || monster.monstats == null
        || !isEligibleDropMonster(monster.monstats.hcIdx)
        || !isAct3Level(levelId(event.victim)) || !hasEligiblePlayer()) return;
    // Death events can be emitted more than once by melee/missile paths. Mark
    // the victim before generating the item, and only commit the global flag
    // after the authoritative ground entity exists.
    if (jadeDropVictims.contains(event.victim)) return;
    Item figurine = createQuestItem(Act3GoldenBirdQuest.JADE_FIGURINE);
    if (figurine == null || factory == null) {
      log.warn("[A3Q1] Jade Figurine drop unavailable: victim={} item={} factory={}",
          event.victim, figurine != null, factory != null);
      return;
    }
    Position origin = mPosition.get(event.victim);
    int entityId = factory.createItem(figurine, origin.position.x, origin.position.y);
    if (entityId < 0) {
      log.warn("[A3Q1] Jade Figurine ground creation failed: victim={}", event.victim);
      return;
    }
    figurine.id = entityId;
    jadeDropVictims.add(event.victim);
    jadeDropIssued = true;
    log.info("[A3Q1] Jade Figurine dropped: victim={} killer={} entity={}",
        event.victim, event.killer, entityId);
  }

  @Subscribe
  public void onQuestItemPickedUp(QuestItemPickedUpEvent event) {
    if (event == null || !Act3GoldenBirdQuest.JADE_FIGURINE.equalsIgnoreCase(event.itemCode)
        || !mPlayer.has(event.playerId)) return;
    Player player = mPlayer.get(event.playerId);
    if (player == null || player.data == null) return;
    updateRecord(player.data, Act3GoldenBirdQuest.markJadePicked(record(player.data)),
        "jade-figurine-picked-up");
    propagateJadeStatus(event.playerId);
  }

  @Subscribe
  public void onNpcQuestMessage(NpcQuestMessageEvent event) {
    if (event == null || !mPlayer.has(event.entityId) || !mMonster.has(event.npcId)) return;
    Player player = mPlayer.get(event.entityId);
    Monster npc = mMonster.get(event.npcId);
    if (player == null || player.data == null || npc == null || npc.monstats == null) return;
    int npcType = npc.monstats.hcIdx;
    if (Act3GoldenBirdQuest.isCain(npcType)) {
      onCainMessage(event, player);
    } else if (Act3GoldenBirdQuest.isMeshif(npcType)) {
      onMeshifMessage(event, player);
    } else if (Act3GoldenBirdQuest.isAlkor(npcType)) {
      onAlkorMessage(event, player);
    }
  }

  private void onCainMessage(NpcQuestMessageEvent event, Player player) {
    short previous = record(player.data);
    short next = previous;
    if (event.messageIndex == Act3GoldenBirdQuest.MESSAGE_CAIN_INIT) {
      next = Act3GoldenBirdQuest.start(previous);
    } else if (event.messageIndex == Act3GoldenBirdQuest.MESSAGE_CAIN_ENTERED_AREA) {
      next = Act3GoldenBirdQuest.enterArea(previous);
    }
    if (next != previous) updateRecord(player.data, next, "cain-message-" + event.messageIndex);
  }

  private void onMeshifMessage(NpcQuestMessageEvent event, Player player) {
    if (event.messageIndex != Act3GoldenBirdQuest.MESSAGE_MESHIF_EXCHANGE) return;
    CharData data = player.data;
    short previous = record(data);
    if (!Act3GoldenBirdQuest.canProgress(previous)
        || !data.getItems().containsItemCode(Act3GoldenBirdQuest.JADE_FIGURINE)) return;
    Item goldenBird = createQuestItem(Act3GoldenBirdQuest.GOLDEN_BIRD);
    // Reserve an inventory slot before consuming the figurine. This mirrors
    // the native exchange's atomicity when a full inventory or a missing
    // item-table entry prevents the reward from being created.
    if (goldenBird == null || !data.getItems().addToInventory(goldenBird)) {
      log.warn("[A3Q1] Meshif exchange could not place Golden Bird: player={} generated={}",
          event.entityId, goldenBird != null);
      return;
    }
    if (!data.getItems().removeItemCode(Act3GoldenBirdQuest.JADE_FIGURINE)) {
      data.getItems().removeInventoryItem(goldenBird);
      log.warn("[A3Q1] Meshif exchange rolled back: Jade Figurine disappeared: player={}",
          event.entityId);
      return;
    }
    updateRecord(data, Act3GoldenBirdQuest.exchangeForGoldenBird(previous),
        "meshif-exchanged-jade");
    log.info("[A3Q1] Meshif exchanged Jade Figurine: player={} item={}",
        event.entityId, goldenBird.code);
  }

  private void onAlkorMessage(NpcQuestMessageEvent event, Player player) {
    CharData data = player.data;
    short previous = record(data);
    if (event.messageIndex == Act3GoldenBirdQuest.MESSAGE_ALKOR_RECEIVE) {
      if (!Act3GoldenBirdQuest.canProgress(previous)
          || !data.getItems().removeItemCode(Act3GoldenBirdQuest.GOLDEN_BIRD)) return;
      short next = Act3GoldenBirdQuest.acceptAtAlkor(previous);
      updateRecord(data, next, "alkor-accepted-golden-bird");
      log.info("[A3Q1] Alkor accepted Golden Bird: player={} record=0x{}", event.entityId,
          Integer.toHexString(Short.toUnsignedInt(next)));
    } else if (event.messageIndex == Act3GoldenBirdQuest.MESSAGE_ALKOR_REWARD) {
      short next = Act3GoldenBirdQuest.claimReward(previous);
      if (next == previous) return;
      updateRecord(data, next, "alkor-reward-claimed");
      createLifePotion(event.entityId, player);
    }
  }

  private void createLifePotion(int playerId, Player player) {
    Item potion = createQuestItem(Act3GoldenBirdQuest.POTION_OF_LIFE);
    if (potion != null && player.data.getItems().addToInventory(potion)) {
      log.info("[A3Q1] Potion of Life placed in inventory: player={} item={}", playerId,
          potion.code);
      return;
    }
    if (potion != null && factory != null && mPosition.has(playerId)) {
      Position pos = mPosition.get(playerId);
      int entityId = factory.createItem(potion, pos.position.x, pos.position.y);
      if (entityId >= 0) {
        potion.id = entityId;
        log.info("[A3Q1] Potion of Life dropped at player: player={} entity={}", playerId,
            entityId);
        return;
      }
    }
    log.warn("[A3Q1] Potion of Life reward could not be placed: player={}", playerId);
  }

  private Item createQuestItem(String code) {
    if (itemGenerator == null) return null;
    try {
      Item item = itemGenerator.generate(code);
      if (item == null) return null;
      item.version = Item.VERSION_110;
      item.ilvl = 1;
      item.quality = Quality.NORMAL;
      item.flags |= Item.ITEMFLAG_IDENTIFIED;
      return item;
    } catch (Throwable t) {
      log.error("[A3Q1] Quest item generation failed: code={}", code, t);
      return null;
    }
  }

  private boolean hasEligiblePlayer() {
    if (playersByZone == null) return false;
    IntBag players = playersByZone.getEntities();
    int[] ids = players.getData();
    for (int i = 0; i < players.size(); i++) {
      Player player = mPlayer.get(ids[i]);
      if (player != null && player.data != null && isAct3Level(levelId(ids[i]))) {
        short record = record(player.data);
        if (!Act3GoldenBirdQuest.isFinished(record)
            && !player.data.getItems().containsItemCode(Act3GoldenBirdQuest.JADE_FIGURINE)
            && !player.data.getItems().containsItemCode(Act3GoldenBirdQuest.GOLDEN_BIRD)) return true;
      }
    }
    return false;
  }

  private void propagateJadeStatus(int sourcePlayerId) {
    if (playersByZone == null || partyManager == null) return;
    short partyId = partyManager.getPartyId(sourcePlayerId);
    if (partyId == Party.INVALID_ID) return;
    IntBag players = playersByZone.getEntities();
    int[] ids = players.getData();
    for (int i = 0; i < players.size(); i++) {
      int playerId = ids[i];
      if (playerId == sourcePlayerId || partyManager.getPartyId(playerId) != partyId
          || !isAct3Level(levelId(playerId))) continue;
      Player member = mPlayer.get(playerId);
      if (member == null || member.data == null) continue;
      short previous = record(member.data);
      short next = Act3GoldenBirdQuest.markJadePicked(previous);
      if (next != previous) updateRecord(member.data, next, "party-jade-picked");
    }
  }

  private boolean isEligibleDropMonster(int hcIdx) {
    // Native quest drops from a selected monster, never from town NPCs or a
    // boss. Keeping this list explicit prevents DeathEvent on NPC proxies,
    // summons and quest bosses from consuming the one-shot drop.
    switch (hcIdx) {
      case MonsterType.AKARA: case MonsterType.CHARSI: case MonsterType.KASHYA:
      case MonsterType.DECKARDCAIN: case MonsterType.DECKARDCAIN_TOWN:
      case MonsterType.DECKARDCAIN_ACT2: case MonsterType.WARRIV: case MonsterType.WARRIV2:
      case MonsterType.ATMA: case MonsterType.DROGNAN: case MonsterType.FARA:
      case MonsterType.ELZIX: case MonsterType.GEGLASH: case MonsterType.JERHYN:
      case MonsterType.LYSANDER: case MonsterType.MESHIF1: case MonsterType.CAIN3:
      case MonsterType.ASHEARA: case MonsterType.HRATLI: case MonsterType.ALKOR:
      case MonsterType.ORMUS: case MonsterType.MESHIF2: case MonsterType.NATALYA:
      case MonsterType.ANDARIEL: case MonsterType.DURIEL: case MonsterType.MEPHISTO:
      case MonsterType.DIABLO: case MonsterType.BAALCRAB:
        return false;
      default:
        return true;
    }
  }

  private int levelId(int entityId) {
    if (!mMapWrapper.has(entityId)) return -1;
    MapWrapper wrapper = mMapWrapper.get(entityId);
    Map.Zone zone = wrapper == null ? null : wrapper.zone;
    return zone == null || zone.level == null ? -1 : zone.level.Id;
  }

  static boolean isAct3Level(int levelId) {
    return levelId >= D2LevelIds.LEVEL_SPIDERFOREST
        && levelId <= D2LevelIds.LEVEL_DURANCEOFHATELEVEL3;
  }

  private short record(CharData data) {
    return data.getQuests(Riiablo.ACT3)[Act3GoldenBirdQuest.RECORD];
  }

  private void updateRecord(CharData data, short next, String reason) {
    short previous = record(data);
    if (previous == next) return;
    data.getQuests(Riiablo.ACT3)[Act3GoldenBirdQuest.RECORD] = next;
    persist(data);
    log.info("[A3Q1] Quest record changed: character={} reason={} previous=0x{} next=0x{}",
        data.name, reason, Integer.toHexString(Short.toUnsignedInt(previous)),
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  private static void persist(CharData data) {
    if (data.managed && Riiablo.saves != null) D2SWriter.INSTANCE.save(data);
  }
}
