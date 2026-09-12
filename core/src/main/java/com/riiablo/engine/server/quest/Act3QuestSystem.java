package com.riiablo.engine.server.quest;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.IntIntMap;
import com.d2moo.common.drlg.D2LevelIds;
import com.riiablo.Riiablo;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.event.NativeQuestRewardEvent;
import com.riiablo.engine.server.event.NpcQuestMessageEvent;
import com.riiablo.engine.server.event.QuestObjectInteractionEvent;
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
import net.mostlyoriginal.api.event.common.EventSystem;
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
  protected EventSystem event;

  private EntitySubscription playersByZone;
  private final IntSet jadeDropVictims = new IntSet();
  private final IntSet gidbinnBosses = new IntSet();
  private final IntSet gidbinnDropVictims = new IntSet();
  private final IntSet khalimChestDrops = new IntSet();
  private final IntIntMap compellingOrbHits = new IntIntMap();
  private boolean jadeDropIssued;
  private boolean gidbinnDropIssued;
  private boolean khalimFlailDropIssued;

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
    short gidbinnPrevious = gidbinnRecord(player.data);
    short gidbinnNext = event.zone.level.Id == D2LevelIds.LEVEL_FLAYERJUNGLE
        ? Act3GidbinnQuest.enterArea(gidbinnPrevious) : gidbinnPrevious;
    if (gidbinnNext != gidbinnPrevious) updateGidbinnRecord(player.data, gidbinnNext,
        "entered-flayer-jungle");
  }

  @Subscribe
  public void onQuestObjectInteraction(QuestObjectInteractionEvent interaction) {
    if (interaction == null || (interaction.type != com.riiablo.engine.server.object.NativeQuestObjectResolver.Type.GIDBINN_DECOY
        && interaction.type != com.riiablo.engine.server.object.NativeQuestObjectResolver.Type.KHALIM_CHEST
        && interaction.type != com.riiablo.engine.server.object.NativeQuestObjectResolver.Type.COMPELLING_ORB)
        || !mPlayer.has(interaction.playerId) || !mPosition.has(interaction.entityId)) return;
    Player player = mPlayer.get(interaction.playerId);
    if (player == null || player.data == null) return;
    if (interaction.type == com.riiablo.engine.server.object.NativeQuestObjectResolver.Type.KHALIM_CHEST) {
      onKhalimChestInteraction(interaction, player);
      return;
    }
    if (interaction.type == com.riiablo.engine.server.object.NativeQuestObjectResolver.Type.COMPELLING_ORB) {
      onCompellingOrbInteraction(interaction, player);
      return;
    }
    short record = gidbinnRecord(player.data);
    if (!Act3GidbinnQuest.canProgress(record)
        || NativeQuestRecord.has(record, NativeQuestRecord.CUSTOM2)
        || gidbinnDropIssued) return;
    if (factory == null) return;
    Position position = mPosition.get(interaction.entityId);
    int boss = factory.createMonster(MonsterType.FETISH11,
        position.position.x, position.position.y);
    if (boss < 0) return;
    interaction.accept();
    gidbinnBosses.add(boss);
    log.info("[A3Q2] Gidbinn guardian spawned: object={} player={} boss={}",
        interaction.entityId, interaction.playerId, boss);
  }

  private void onKhalimChestInteraction(QuestObjectInteractionEvent interaction, Player player) {
    short record = khalimRecord(player.data);
    String code = khalimChestCode(interaction.objectClassId);
    if (code == null || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
        || khalimChestDrops.contains(interaction.entityId)
        || player.data.getItems().containsItemCode(code) || factory == null) return;
    Item item = createQuestItem(code);
    Position position = mPosition.get(interaction.entityId);
    if (item == null || position == null) return;
    int entityId = factory.createItem(item, position.position.x, position.position.y);
    if (entityId < 0) return;
    item.id = entityId;
    khalimChestDrops.add(interaction.entityId);
    interaction.accept();
    log.info("[A3Q3] Khalim relic dropped: chest={} code={} entity={} player={}",
        interaction.objectClassId, code, entityId, interaction.playerId);
  }

  private void onCompellingOrbInteraction(QuestObjectInteractionEvent interaction, Player player) {
    short record = khalimRecord(player.data);
    if (!player.data.getItems().containsItemCode(Act3KhalimQuest.KHALIM_WILL)
        || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)) return;
    int hits = compellingOrbHits.get(interaction.entityId, 0) + 1;
    compellingOrbHits.put(interaction.entityId, hits);
    if (hits < 2) {
      log.info("[A3Q3] Compelling Orb hit {}/2: player={} object={}", hits,
          interaction.playerId, interaction.entityId);
      return;
    }
    if (!player.data.getItems().removeItemCode(Act3KhalimQuest.KHALIM_WILL)) return;
    short next = NativeQuestRecord.set(record, NativeQuestRecord.PRIMARY_GOAL_DONE);
    next = NativeQuestRecord.set(next, NativeQuestRecord.REWARD_GRANTED);
    updateKhalimRecord(player.data, next, "compelling-orb-smashed");
    interaction.accept();
    log.info("[A3Q3] Compelling Orb smashed: player={} object={}", interaction.playerId,
        interaction.entityId);
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
  public void onGidbinnGuardianKilled(DeathEvent event) {
    if (event == null || event.victim < 0 || gidbinnDropIssued
        || !gidbinnBosses.contains(event.victim) || !mPosition.has(event.victim)) return;
    if (!gidbinnDropVictims.add(event.victim)) return;
    Item item = createQuestItem(Act3GidbinnQuest.GIDBINN);
    if (item == null || factory == null) return;
    Position origin = mPosition.get(event.victim);
    int entityId = factory.createItem(item, origin.position.x, origin.position.y);
    if (entityId < 0) return;
    item.id = entityId;
    gidbinnDropIssued = true;
    markGidbinnDropForAct3Players();
    log.info("[A3Q2] Gidbinn dropped: victim={} killer={} entity={}",
        event.victim, event.killer, entityId);
  }

  @Subscribe
  public void onKhalimCouncilKilled(DeathEvent event) {
    if (event == null || event.victim < 0 || khalimFlailDropIssued
        || !mMonster.has(event.victim) || !mPosition.has(event.victim)
        || !hasKhalimEligiblePlayer() || !isAct3Level(levelId(event.victim))) return;
    Monster monster = mMonster.get(event.victim);
    if (monster == null || monster.monstats == null
        || monster.monstats.hcIdx != MonsterType.COUNCILMEMBER || factory == null) return;
    Item item = createQuestItem(Act3KhalimQuest.KHALIM_FLAIL);
    Position origin = mPosition.get(event.victim);
    if (item == null || origin == null) return;
    int entityId = factory.createItem(item, origin.position.x, origin.position.y);
    if (entityId < 0) return;
    item.id = entityId;
    khalimFlailDropIssued = true;
    markKhalimDropForAct3Players();
    log.info("[A3Q3] Khalim Flail dropped: victim={} entity={}", event.victim, entityId);
  }

  @Subscribe
  public void onQuestItemPickedUp(QuestItemPickedUpEvent event) {
    if (event == null || !mPlayer.has(event.playerId)) return;
    Player player = mPlayer.get(event.playerId);
    if (player == null || player.data == null) return;
    if (Act3GoldenBirdQuest.JADE_FIGURINE.equalsIgnoreCase(event.itemCode)) {
      updateRecord(player.data, Act3GoldenBirdQuest.markJadePicked(record(player.data)),
          "jade-figurine-picked-up");
      propagateJadeStatus(event.playerId);
    } else if (Act3GidbinnQuest.GIDBINN.equalsIgnoreCase(event.itemCode)) {
      short previous = gidbinnRecord(player.data);
      updateGidbinnRecord(player.data, Act3GidbinnQuest.markGidbinnPicked(previous),
          "gidbinn-picked-up");
    } else if (Act3KhalimQuest.isPart(event.itemCode)) {
      short previous = khalimRecord(player.data);
      short next = Act3KhalimQuest.markPicked(previous, event.itemCode);
      if (next != previous) updateKhalimRecord(player.data, next, "khalim-part-picked");
    }
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
    } else if (npcType == MonsterType.HRATLI || npcType == MonsterType.ORMUS
        || npcType == MonsterType.ASHEARA) {
      onGidbinnNpcMessage(event, player, npcType);
    }
  }

  private void onGidbinnNpcMessage(NpcQuestMessageEvent event, Player player, int npcType) {
    CharData data = player.data;
    short previous = gidbinnRecord(data);
    if (npcType == MonsterType.HRATLI
        && event.messageIndex == Act3GidbinnQuest.MESSAGE_HRATLI_INIT) {
      updateGidbinnRecord(data, Act3GidbinnQuest.start(previous), "hratli-gidbinn-init");
      return;
    }
    if (npcType == MonsterType.ORMUS
        && event.messageIndex == Act3GidbinnQuest.MESSAGE_ORMUS_TURN_IN) {
      if (!data.getItems().containsItemCode(Act3GidbinnQuest.GIDBINN)
          || NativeQuestRecord.has(previous, NativeQuestRecord.CUSTOM2)) return;
      if (!data.getItems().removeItemCode(Act3GidbinnQuest.GIDBINN)) return;
      short next = Act3GidbinnQuest.markBroughtToOrmus(previous);
      updateGidbinnRecord(data, next, "ormus-gidbinn-turn-in");
      propagateGidbinnTurnIn(event.entityId);
      return;
    }
    if (npcType == MonsterType.ORMUS
        && event.messageIndex == Act3GidbinnQuest.MESSAGE_ORMUS_REWARD) {
      if (!NativeQuestRecord.has(previous, NativeQuestRecord.CUSTOM2)
          || NativeQuestRecord.has(previous, NativeQuestRecord.CUSTOM4)) return;
      short next = Act3GidbinnQuest.markOrmusReward(previous);
      updateGidbinnRecord(data, next, "ormus-ring-reward");
      createGidbinnReward(event.entityId, player);
      completeGidbinnIfReady(data);
      return;
    }
    if (npcType == MonsterType.ASHEARA
        && event.messageIndex == Act3GidbinnQuest.MESSAGE_ASHEARA_REWARD
        && NativeQuestRecord.has(previous, NativeQuestRecord.CUSTOM2)
        && !NativeQuestRecord.has(previous, NativeQuestRecord.CUSTOM3)) {
      if (this.event != null) this.event.dispatch(NativeQuestRewardEvent.available(event.entityId,
          QuestId.A3Q2_BLADE_OF_OLD_RELIGION, NativeQuestRewardEvent.GIDBINN_FREE_IRON_WOLF));
    }
  }

  @Subscribe
  public void onNativeQuestReward(NativeQuestRewardEvent reward) {
    if (reward == null || reward.phase != NativeQuestRewardEvent.GRANTED
        || reward.questId != QuestId.A3Q2_BLADE_OF_OLD_RELIGION || !mPlayer.has(reward.playerId)) return;
    Player player = mPlayer.get(reward.playerId);
    if (player == null || player.data == null) return;
    short previous = gidbinnRecord(player.data);
    short next = Act3GidbinnQuest.markAshearaReward(previous);
    if (next != previous) {
      updateGidbinnRecord(player.data, next, "asheara-free-iron-wolf");
      completeGidbinnIfReady(player.data);
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

  private void markGidbinnDropForAct3Players() {
    if (playersByZone == null) return;
    IntBag players = playersByZone.getEntities();
    int[] ids = players.getData();
    for (int i = 0; i < players.size(); i++) {
      Player player = mPlayer.get(ids[i]);
      if (player == null || player.data == null || !isAct3Level(levelId(ids[i]))) continue;
      short previous = gidbinnRecord(player.data);
      short next = Act3GidbinnQuest.enterArea(Act3GidbinnQuest.start(previous));
      if (next != previous) updateGidbinnRecord(player.data, next, "gidbinn-guardian-died");
    }
  }

  private void markKhalimDropForAct3Players() {
    if (playersByZone == null) return;
    IntBag players = playersByZone.getEntities();
    int[] ids = players.getData();
    for (int i = 0; i < players.size(); i++) {
      Player player = mPlayer.get(ids[i]);
      if (player == null || player.data == null || !isAct3Level(levelId(ids[i]))) continue;
      short previous = khalimRecord(player.data);
      short next = Act3KhalimQuest.start(previous);
      if (next != previous) updateKhalimRecord(player.data, next, "khalim-council-killed");
    }
  }

  private boolean hasKhalimEligiblePlayer() {
    if (playersByZone == null) return false;
    IntBag players = playersByZone.getEntities();
    int[] ids = players.getData();
    for (int i = 0; i < players.size(); i++) {
      Player player = mPlayer.get(ids[i]);
      if (player == null || player.data == null || !isAct3Level(levelId(ids[i]))) continue;
      short record = khalimRecord(player.data);
      if (!NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
          && !player.data.getItems().containsItemCode(Act3KhalimQuest.KHALIM_FLAIL)
          && !player.data.getItems().containsItemCode(Act3KhalimQuest.KHALIM_WILL)) return true;
    }
    return false;
  }

  private static String khalimChestCode(int objectClassId) {
    switch (objectClassId) {
      case Act3KhalimQuest.KHALIM_CHEST1: return Act3KhalimQuest.KHALIM_HEART;
      case Act3KhalimQuest.KHALIM_CHEST2: return Act3KhalimQuest.KHALIM_EYE;
      case Act3KhalimQuest.KHALIM_CHEST3: return Act3KhalimQuest.KHALIM_BRAIN;
      default: return null;
    }
  }

  private void propagateGidbinnTurnIn(int sourcePlayerId) {
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
      short previous = gidbinnRecord(member.data);
      short next = Act3GidbinnQuest.markBroughtToOrmus(previous);
      if (next != previous) updateGidbinnRecord(member.data, next, "party-ormus-turn-in");
    }
  }

  private void createGidbinnReward(int playerId, Player player) {
    Item reward = createQuestItem(Act3GidbinnQuest.ORMUS_REWARD);
    if (reward != null) {
      reward.quality = Quality.RARE;
      reward.ilvl = (byte) (player.data.diff == Riiablo.NORMAL ? 21
          : player.data.diff == Riiablo.NIGHTMARE ? 35 : 75);
      if (player.data.getItems().addToInventory(reward)) {
        log.info("[A3Q2] Ormus rare ring placed in inventory: player={}", playerId);
        return;
      }
      if (factory != null && mPosition.has(playerId)) {
        Position pos = mPosition.get(playerId);
        int entityId = factory.createItem(reward, pos.position.x, pos.position.y);
        if (entityId >= 0) {
          reward.id = entityId;
          log.info("[A3Q2] Ormus rare ring dropped at player: player={} entity={}",
              playerId, entityId);
          return;
        }
      }
    }
    log.warn("[A3Q2] Ormus rare ring reward could not be placed: player={}", playerId);
  }

  private void completeGidbinnIfReady(CharData data) {
    short previous = gidbinnRecord(data);
    short next = Act3GidbinnQuest.completeIfBothRewards(previous);
    if (next != previous) updateGidbinnRecord(data, next, "gidbinn-both-rewards");
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
      case MonsterType.DIABLO: case MonsterType.BAALCRAB: case MonsterType.FETISH11:
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

  private short gidbinnRecord(CharData data) {
    return data.getQuests(Riiablo.ACT3)[Act3GidbinnQuest.RECORD];
  }

  private short khalimRecord(CharData data) {
    return data.getQuests(Riiablo.ACT3)[Act3KhalimQuest.RECORD];
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

  private void updateGidbinnRecord(CharData data, short next, String reason) {
    short previous = gidbinnRecord(data);
    if (previous == next) return;
    data.getQuests(Riiablo.ACT3)[Act3GidbinnQuest.RECORD] = next;
    persist(data);
    log.info("[A3Q2] Quest record changed: character={} reason={} previous=0x{} next=0x{}",
        data.name, reason, Integer.toHexString(Short.toUnsignedInt(previous)),
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  private void updateKhalimRecord(CharData data, short next, String reason) {
    short previous = khalimRecord(data);
    if (previous == next) return;
    data.getQuests(Riiablo.ACT3)[Act3KhalimQuest.RECORD] = next;
    persist(data);
    log.info("[A3Q3] Quest record changed: character={} reason={} previous=0x{} next=0x{}",
        data.name, reason, Integer.toHexString(Short.toUnsignedInt(previous)),
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  private static void persist(CharData data) {
    if (data.managed && Riiablo.saves != null) D2SWriter.INSTANCE.save(data);
  }
}
