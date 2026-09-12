package com.riiablo.engine.server.quest;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntSet;
import com.d2moo.common.drlg.D2LevelIds;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatListRef;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.NativeObjectState;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.event.NpcQuestMessageEvent;
import com.riiablo.engine.server.event.QuestObjectInteractionEvent;
import com.riiablo.engine.server.event.QuestItemPickedUpEvent;
import com.riiablo.engine.server.event.ZoneChangeEvent;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.engine.server.object.NativeQuestObjectResolver;
import com.riiablo.engine.server.party.Party;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import com.riiablo.save.D2SWriter;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.Quality;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/** Server-authoritative Act IV A4Q1 Izual lifecycle and Tyrael reward. */
@Wire(failOnNull = false)
public class Act4QuestSystem extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(Act4QuestSystem.class);
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<com.riiablo.engine.server.component.Object> mObject;
  protected ComponentMapper<NativeObjectState> mNativeObjectState;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  @Wire(name = "factory", failOnNull = false)
  protected EntityFactory factory;
  @Wire(failOnNull = false)
  protected ItemGenerator itemGenerator;
  @Wire(name = "partyManager", failOnNull = false)
  protected PartyManager partyManager;

  private EntitySubscription playersByZone;
  private EntitySubscription monstersByZone;
  private EntitySubscription objectsByZone;
  private final IntSet spawnedIzualLevels = new IntSet();
  private final IntSet rewardedIzuals = new IntSet();
  private final IntSet activatedDiabloSeals = new IntSet();
  private final IntSet sealBossEntities = new IntSet();
  private final IntSet killedSealBosses = new IntSet();
  private final IntSet completedDiablos = new IntSet();
  private boolean diabloSpawned;
  private boolean allSealsActivated;
  private final IntSet openedHellforges = new IntSet();
  private final IntIntMap hellforgeHits = new IntIntMap();
  private Map.Zone trackedChaosZone;
  private boolean soulstoneDropped;
  private boolean hammerDropped;

  @Override
  protected void initialize() {
    playersByZone = world.getAspectSubscriptionManager().get(
        Aspect.all(Player.class, MapWrapper.class));
    monstersByZone = world.getAspectSubscriptionManager().get(
        Aspect.all(Monster.class, MapWrapper.class));
    objectsByZone = world.getAspectSubscriptionManager().get(
        Aspect.all(com.riiablo.engine.server.component.Object.class, MapWrapper.class));
  }

  @Override
  protected void processSystem() {
    // Seal activation is stored on the native object/map record, while the
    // entity id set is transient. Rebuild it after a reconnect or RoomEx
    // recreation so an already-open seal cannot spawn another boss.
    if (playersByZone == null) return;
    IntBag players = playersByZone.getEntities();
    int[] ids = players.getData();
    for (int i = 0; i < players.size(); i++) {
      if (levelId(ids[i]) == Act4DiabloQuest.CHAOS_SANCTUARY) {
        MapWrapper wrapper = mMapWrapper.get(ids[i]);
        if (wrapper != null) rebuildChaosSealState(wrapper.zone);
      }
      if (levelId(ids[i]) == Act4DiabloQuest.PANDEMONIUM_FORTRESS) {
        Player player = mPlayer.get(ids[i]);
        MapWrapper wrapper = mMapWrapper.get(ids[i]);
        if (player != null && player.data != null && wrapper != null) {
          reconcileAct5PortalState(ids[i], wrapper.zone, player.data);
        }
      }
    }
  }

  @Subscribe
  public void onZoneChanged(ZoneChangeEvent event) {
    if (event == null || event.zone == null || event.zone.level == null
        || !mPlayer.has(event.entityId)) return;
    Player player = mPlayer.get(event.entityId);
    if (player == null || player.data == null) return;
    if (event.zone.level.Id == D2LevelIds.LEVEL_PLAINSOFDESPAIR) {
      updateRecord(player.data, Act4IzualQuest::start, "entered-plains-of-despair");
      spawnIzualIfNeeded(event.entityId, event.zone.level.Id);
    }
    // The previous implementation nested this branch under the Plains of
    // Despair guard, so the Diablo quest never recorded entering Chaos
    // Sanctuary.  Native A4Q2 advances its level-change state independently.
    if (event.zone.level.Id == Act4DiabloQuest.CHAOS_SANCTUARY) {
      updateDiabloRecord(player.data, Act4DiabloQuest::enterArea,
          "entered-chaos-sanctuary");
      rebuildChaosSealState(event.zone);
    } else if (event.zone.level.Id == Act4DiabloQuest.PANDEMONIUM_FORTRESS) {
      reconcileAct5PortalState(event.entityId, event.zone, player.data);
    } else if (isAct4Level(event.zone.level.Id)) {
      updateDiabloRecord(player.data, Act4DiabloQuest::start,
          "entered-act4-combat-area");
    }
  }

  @Subscribe
  public void onQuestObjectInteraction(QuestObjectInteractionEvent interaction) {
    if (interaction == null || (interaction.type != NativeQuestObjectResolver.Type.DIABLO_SEAL
        && interaction.type != NativeQuestObjectResolver.Type.HELLFORGE)
        || !mPlayer.has(interaction.playerId) || !mPosition.has(interaction.entityId)) return;
    Player player = mPlayer.get(interaction.playerId);
    if (player == null || player.data == null
        || NativeQuestRecord.has(diabloRecord(player.data), NativeQuestRecord.REWARD_GRANTED)) return;
    if (interaction.type == NativeQuestObjectResolver.Type.HELLFORGE) {
      onHellforgeInteraction(interaction, player);
      return;
    }
    if (levelId(interaction.entityId) != Act4DiabloQuest.CHAOS_SANCTUARY) return;
    rebuildChaosSealState(mMapWrapper.get(interaction.entityId).zone);
    if (!activatedDiabloSeals.add(interaction.entityId)) return;
    interaction.accept();
    updateDiabloRecord(player.data);
    log.info("[A4Q2] Chaos seal activated: object={} player={} count={}/5",
        interaction.entityId, interaction.playerId, activatedDiabloSeals.size);
    spawnSealBoss(interaction.objectClassId, interaction.entityId);
    if (activatedDiabloSeals.size >= 5) {
      allSealsActivated = true;
      spawnDiabloIfReady(interaction.entityId);
    }
  }

  private void onHellforgeInteraction(QuestObjectInteractionEvent interaction, Player player) {
    if (levelId(interaction.entityId) < D2LevelIds.LEVEL_OUTERSTEPPES
        || levelId(interaction.entityId) > D2LevelIds.LEVEL_CHAOSSANCTUM) return;
    short record = hellforgeRecord(player.data);
    if (NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
        || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)) return;
    if (!openedHellforges.contains(interaction.entityId)) {
      if (!player.data.getItems().removeItemCode(Act4HellforgeQuest.SOULSTONE)) return;
      openedHellforges.add(interaction.entityId);
      interaction.accept();
      updateHellforgeRecord(player.data, Act4HellforgeQuest.start(record), "hellforge-opened");
      log.info("[A4Q3] Hellforge opened: player={} object={}", interaction.playerId,
          interaction.entityId);
      return;
    }
    if (!player.data.getItems().containsItemCode(Act4HellforgeQuest.HAMMER)) return;
    int hits = hellforgeHits.get(interaction.entityId, 0) + 1;
    hellforgeHits.put(interaction.entityId, hits);
    if (hits < 3) {
      interaction.accept();
      log.info("[A4Q3] Hellforge hammer hit {}/3: player={} object={}", hits,
          interaction.playerId, interaction.entityId);
      return;
    }
    if (!player.data.getItems().removeItemCode(Act4HellforgeQuest.HAMMER)) return;
    interaction.accept(Engine.Object.MODE_S1);
    short next = Act4HellforgeQuest.complete(record);
    updateHellforgeRecord(player.data, next, "soulstone-smashed");
    dropHellforgeRunes(interaction.entityId);
    log.info("[A4Q3] Hellforge completed: player={} object={}", interaction.playerId,
        interaction.entityId);
  }

  private void dropHellforgeRunes(int forgeEntityId) {
    if (itemGenerator == null || factory == null || !mPosition.has(forgeEntityId)) return;
    Position position = mPosition.get(forgeEntityId);
    String[] runes = {"r07", "r08", "r09"};
    for (int i = 0; i < runes.length; i++) {
      try {
        Item rune = itemGenerator.generate(runes[i]);
        if (rune == null) continue;
        rune.version = Item.VERSION_110;
        rune.quality = Quality.NORMAL;
        rune.flags |= Item.ITEMFLAG_IDENTIFIED;
        int entity = factory.createItem(rune, position.position.x + i - 1f, position.position.y);
        if (entity >= 0) rune.id = entity;
      } catch (Throwable t) {
        log.warn("[A4Q3] Hellforge rune generation failed: code={}", runes[i], t);
      }
    }
  }

  @Subscribe
  public void onQuestItemPickedUp(QuestItemPickedUpEvent event) {
    if (event == null || !Act4HellforgeQuest.SOULSTONE.equalsIgnoreCase(event.itemCode)
        || !mPlayer.has(event.playerId)) return;
    Player player = mPlayer.get(event.playerId);
    if (player != null && player.data != null) {
      short previous = hellforgeRecord(player.data);
      short next = Act4HellforgeQuest.start(previous);
      if (next != previous) updateHellforgeRecord(player.data, next, "soulstone-picked-up");
    }
  }

  private void spawnSealBoss(int objectClassId, int sealEntityId) {
    if (factory == null || objectClassId < Act4DiabloQuest.FIRST_SEAL
        || objectClassId > Act4DiabloQuest.FIRST_SEAL + 2) return;
    int monsterType;
    switch (objectClassId) {
      case 392: monsterType = MonsterType.INFECTOR_OF_SOULS; break;
      case 393: monsterType = MonsterType.LORD_DE_SEIS; break;
      case 394: monsterType = MonsterType.GRAND_VIZIER_OF_CHAOS; break;
      default: return;
    }
    Position position = mPosition.get(sealEntityId);
    if (position == null) return;
    int entity = factory.createMonster(monsterType, position.position.x, position.position.y);
    if (entity >= 0) {
      sealBossEntities.add(entity);
      log.info("[A4Q2] Seal boss spawned: seal={} monsterType={} entity={}",
          objectClassId, monsterType, entity);
    }
  }

  private void spawnDiabloIfReady(int sourceEntityId) {
    if (allSealsActivated && killedSealBosses.size >= 3) spawnDiablo(sourceEntityId);
  }

  private void rebuildChaosSealState(Map.Zone zone) {
    if (zone == null || zone.level == null
        || zone.level.Id != Act4DiabloQuest.CHAOS_SANCTUARY
        || objectsByZone == null) return;
    if (trackedChaosZone != zone) {
      trackedChaosZone = zone;
      activatedDiabloSeals.clear();
      allSealsActivated = false;
    }
    IntBag objects = objectsByZone.getEntities();
    int[] ids = objects.getData();
    for (int i = 0; i < objects.size(); i++) {
      int id = ids[i];
      if (!mObject.has(id) || !mMapWrapper.has(id)) continue;
      MapWrapper wrapper = mMapWrapper.get(id);
      com.riiablo.engine.server.component.Object object = mObject.get(id);
      if (wrapper == null || wrapper.zone != zone || object == null || object.base == null
          || !Act4DiabloQuest.isSealObject(object.base.Id)) continue;
      NativeObjectState state = mNativeObjectState.has(id) ? mNativeObjectState.get(id) : null;
      boolean activated = state != null ? state.activated
          : (object.stateFlags & com.riiablo.engine.server.component.Object.STATE_ACTIVATED) != 0;
      if (activated) activatedDiabloSeals.add(id);
    }
    allSealsActivated = activatedDiabloSeals.size >= 5;
  }

  private void spawnDiablo(int sourceEntityId) {
    if (diabloSpawned || factory == null) return;
    if (monstersByZone != null) {
      IntBag entities = monstersByZone.getEntities();
      int[] ids = entities.getData();
      for (int i = 0; i < entities.size(); i++) {
        Monster monster = mMonster.get(ids[i]);
        if (monster != null && monster.monstats != null && monster.monstats.hcIdx == MonsterType.DIABLO
            && levelId(ids[i]) == Act4DiabloQuest.CHAOS_SANCTUARY) {
          diabloSpawned = true;
          return;
        }
      }
    }
    Position position = mPosition.get(sourceEntityId);
    if (position == null) return;
    int entity = factory.createMonster(MonsterType.DIABLO, position.position.x, position.position.y);
    if (entity >= 0) {
      diabloSpawned = true;
      log.info("[A4Q2] Diablo spawned: entity={} seal={}", entity, sourceEntityId);
    }
  }

  private void spawnIzualIfNeeded(int playerId, int levelId) {
    if (spawnedIzualLevels.contains(levelId) || factory == null) return;
    if (monstersByZone != null) {
      IntBag entities = monstersByZone.getEntities();
      int[] ids = entities.getData();
      for (int i = 0; i < entities.size(); i++) {
        Monster monster = mMonster.get(ids[i]);
        if (monster != null && monster.monstats != null
            && monster.monstats.hcIdx == MonsterType.IZUAL
            && levelId(ids[i]) == levelId) {
          spawnedIzualLevels.add(levelId);
          return;
        }
      }
    }
    Position position = mPosition.has(playerId) ? mPosition.get(playerId) : null;
    if (position == null) return;
    int entity = factory.createMonster(MonsterType.IZUAL,
        position.position.x + 2f, position.position.y);
    if (entity >= 0) {
      spawnedIzualLevels.add(levelId);
      log.info("[A4Q1] Izual spawned: player={} entity={} level={}", playerId, entity, levelId);
    }
  }

  @Subscribe
  public void onMonsterKilled(DeathEvent event) {
    if (event == null || event.victim < 0 || !mMonster.has(event.victim)) return;
    Monster monster = mMonster.get(event.victim);
    if (monster != null && monster.monstats != null && monster.monstats.hcIdx == MonsterType.MEPHISTO
        && levelId(event.victim) == D2LevelIds.LEVEL_DURANCEOFHATELEVEL3
        && !soulstoneDropped) {
      dropMephistoSoulstone(event.victim);
      return;
    }
    if (monster != null && monster.monstats != null && monster.monstats.hcIdx == MonsterType.HEPHASTO
        && levelId(event.victim) == D2LevelIds.LEVEL_RIVEROFFLAME && !hammerDropped) {
      dropQuestItem(event.victim, Act4HellforgeQuest.HAMMER, "Hephasto Hellforge Hammer");
      return;
    }
    if (sealBossEntities.contains(event.victim)) {
      if (killedSealBosses.add(event.victim)) {
        log.info("[A4Q2] Seal boss defeated: entity={} count={}/3", event.victim,
            killedSealBosses.size);
        spawnDiabloIfReady(event.victim);
      }
      return;
    }
    if (monster != null && monster.monstats != null && monster.monstats.hcIdx == MonsterType.DIABLO
        && levelId(event.victim) == Act4DiabloQuest.CHAOS_SANCTUARY
        && completedDiablos.add(event.victim)) {
      completeDiabloForPlayers();
      log.info("[A4Q2] Diablo defeated: victim={} killer={}", event.victim, event.killer);
      return;
    }
    if (monster == null || monster.monstats == null
        || monster.monstats.hcIdx != MonsterType.IZUAL
        || levelId(event.victim) != D2LevelIds.LEVEL_PLAINSOFDESPAIR
        || !rewardedIzuals.add(event.victim)) return;
    IntSet eligibleParties = new IntSet();
    if (playersByZone == null) return;
    IntBag players = playersByZone.getEntities();
    int[] ids = players.getData();
    for (int i = 0; i < players.size(); i++) {
      Player player = mPlayer.get(ids[i]);
      if (player == null || player.data == null || !isAct4Level(levelId(ids[i]))) continue;
      if (levelId(ids[i]) == D2LevelIds.LEVEL_PLAINSOFDESPAIR) {
        setObjective(player.data);
        if (partyManager != null) {
          short party = partyManager.getPartyId(ids[i]);
          if (party != Party.INVALID_ID) eligibleParties.add(party);
        }
      }
    }
    if (partyManager != null && eligibleParties.size > 0) {
      for (int i = 0; i < players.size(); i++) {
        int id = ids[i];
        Player player = mPlayer.get(id);
        if (player == null || player.data == null || !isAct4Level(levelId(id))) continue;
        if (eligibleParties.contains(partyManager.getPartyId(id))) setObjective(player.data);
      }
    }
    log.info("[A4Q1] Izual defeated: victim={} killer={}", event.victim, event.killer);
  }

  private void dropMephistoSoulstone(int victim) {
    dropQuestItem(victim, Act4HellforgeQuest.SOULSTONE, "Mephisto Soulstone");
  }

  private void dropQuestItem(int victim, String code, String description) {
    if (factory == null || itemGenerator == null || !mPosition.has(victim)) return;
    try {
      Item soulstone = itemGenerator.generate(code);
      Position position = mPosition.get(victim);
      if (soulstone == null || position == null) return;
      soulstone.version = Item.VERSION_110;
      soulstone.quality = Quality.NORMAL;
      soulstone.flags |= Item.ITEMFLAG_IDENTIFIED;
      int entity = factory.createItem(soulstone, position.position.x, position.position.y);
      if (entity >= 0) {
        soulstone.id = entity;
        if (Act4HellforgeQuest.SOULSTONE.equalsIgnoreCase(code)) soulstoneDropped = true;
        if (Act4HellforgeQuest.HAMMER.equalsIgnoreCase(code)) hammerDropped = true;
        log.info("[A4Q3] {} dropped: victim={} entity={}", description, victim, entity);
      }
    } catch (Throwable t) {
      log.warn("[A4Q3] Mephisto Soulstone generation failed", t);
    }
  }

  private void completeDiabloForPlayers() {
    if (playersByZone == null) return;
    IntSet parties = new IntSet();
    IntBag players = playersByZone.getEntities();
    int[] ids = players.getData();
    for (int i = 0; i < players.size(); i++) {
      int id = ids[i];
      Player player = mPlayer.get(id);
      if (player == null || player.data == null || levelId(id) != Act4DiabloQuest.CHAOS_SANCTUARY) continue;
      completeDiabloRecord(player.data);
      short party = partyManager == null ? Party.INVALID_ID : partyManager.getPartyId(id);
      if (party != Party.INVALID_ID) parties.add(party);
    }
    if (partyManager == null) return;
    for (int i = 0; i < players.size(); i++) {
      int id = ids[i];
      Player player = mPlayer.get(id);
      if (player == null || player.data == null || !isAct4Level(levelId(id))) continue;
      if (parties.contains(partyManager.getPartyId(id))) completeDiabloRecord(player.data);
    }
  }

  @Subscribe
  public void onNpcQuestMessage(NpcQuestMessageEvent event) {
    if (event == null || !mPlayer.has(event.entityId) || !mMonster.has(event.npcId)) return;
    Player player = mPlayer.get(event.entityId);
    Monster npc = mMonster.get(event.npcId);
    if (player == null || player.data == null || npc == null || npc.monstats == null
        || npc.monstats.hcIdx != MonsterType.TYRAEL2) return;
    short record = record(player.data);
    if (event.messageIndex == Act4IzualQuest.MESSAGE_TYRAEL_INIT) {
      updateRecord(player.data, Act4IzualQuest::start, "tyrael-init");
    } else if (event.messageIndex == Act4IzualQuest.MESSAGE_TYRAEL_REWARD
        && Act4IzualQuest.canClaimReward(record)) {
      short next = Act4IzualQuest.claimReward(record);
      addSkillPoints(event.entityId, player.data, 2);
      updateRecord(player.data, ignored -> next, "tyrael-izual-reward");
      log.info("[A4Q1] Tyrael granted 2 skill points: player={}", event.entityId);
    } else if (event.messageIndex == Act4DiabloQuest.MESSAGE_TYRAEL_ACT5
        && NativeQuestRecord.has(diabloRecord(player.data), NativeQuestRecord.PRIMARY_GOAL_DONE)) {
      openAct5Portal(event, player);
    }
  }

  private void openAct5Portal(NpcQuestMessageEvent event, Player player) {
    MapWrapper wrapper = mMapWrapper.get(event.npcId);
    Position source = mPosition.get(event.npcId);
    if (wrapper == null || wrapper.zone == null || wrapper.zone.level == null || source == null
        || factory == null || wrapper.zone.level.Id != D2LevelIds.LEVEL_THEPANDEMONIUMFORTRESS) {
      log.warn("[A4Q2] Act V portal rejected: player={} npc={}", event.entityId, event.npcId);
      return;
    }
    short record = diabloRecord(player.data);
    if (NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)) return;
    float portalX = source.position.x + 5f;
    float portalY = source.position.y;
    int visual = factory.createStaticObjectByClassId(566, portalX, portalY);
    int warp = factory.createQuestWarp(D2LevelIds.LEVEL_HARROGATH, portalX, portalY);
    if (warp == Engine.INVALID_ENTITY) {
      if (visual != Engine.INVALID_ENTITY && world != null) world.delete(visual);
      log.error("[A4Q2] Act V portal creation failed: player={} npc={}", event.entityId, event.npcId);
      return;
    }
    wrapper.zone.addWarp(warp);
    short next = Act4DiabloQuest.claimCompletion(record);
    dataSetDiabloRecord(player.data, next);
    log.info("[A4Q2] Act V portal opened: player={} visual={} warp={} destination={}",
        event.entityId, visual, warp, D2LevelIds.LEVEL_HARROGATH);
  }

  /** Recreates Tyrael's Act V portal after a reconnect or Room/ECS rebuild. */
  private void reconcileAct5PortalState(int playerId, Map.Zone zone, CharData data) {
    if (zone == null || data == null
        || !NativeQuestRecord.has(diabloRecord(data), NativeQuestRecord.REWARD_GRANTED)) return;
    ensureAct5Portal(zone, playerId);
  }

  private boolean ensureAct5Portal(Map.Zone zone, int fallbackEntityId) {
    if (zone == null || factory == null) return false;
    final int destination = Act4DiabloQuest.HARROGATH;
    final int questWarp = QuestWarp.encode(destination);
    int warp = zone.findWarp(questWarp);
    if (warp != Engine.INVALID_ENTITY) {
      if (hasAct5PortalVisual(zone)) return true;
      if (!mPosition.has(warp)) return false;
      Position position = mPosition.get(warp);
      int visual = factory.createStaticObjectByClassId(566,
          position.position.x, position.position.y);
      log.info("[A4Q2] Restored Act V portal visual: visual={} warp={} destination={}",
          visual, warp, destination);
      return visual != Engine.INVALID_ENTITY;
    }

    int tyrael = findTyraelInFortress(zone);
    if (tyrael == Engine.INVALID_ENTITY) tyrael = fallbackEntityId;
    if (!mPosition.has(tyrael)) return false;
    Position source = mPosition.get(tyrael);
    float portalX = source.position.x + 5f;
    float portalY = source.position.y;
    int visual = factory.createStaticObjectByClassId(566, portalX, portalY);
    warp = factory.createQuestWarp(destination, portalX, portalY);
    if (warp == Engine.INVALID_ENTITY) {
      if (visual != Engine.INVALID_ENTITY && world != null) world.delete(visual);
      return false;
    }
    zone.addWarp(warp);
    log.info("[A4Q2] Restored Act V portal: visual={} warp={} destination={}",
        visual, warp, destination);
    return true;
  }

  private int findTyraelInFortress(Map.Zone zone) {
    if (monstersByZone == null) return Engine.INVALID_ENTITY;
    IntBag monsters = monstersByZone.getEntities();
    int[] ids = monsters.getData();
    for (int i = 0; i < monsters.size(); i++) {
      int id = ids[i];
      if (!mMonster.has(id) || !mMapWrapper.has(id)) continue;
      MapWrapper wrapper = mMapWrapper.get(id);
      Monster monster = mMonster.get(id);
      if (wrapper != null && wrapper.zone == zone && monster != null && monster.monstats != null
          && monster.monstats.hcIdx == MonsterType.TYRAEL2) return id;
    }
    return Engine.INVALID_ENTITY;
  }

  private boolean hasAct5PortalVisual(Map.Zone zone) {
    if (objectsByZone == null) return false;
    IntBag objects = objectsByZone.getEntities();
    int[] ids = objects.getData();
    for (int i = 0; i < objects.size(); i++) {
      int id = ids[i];
      if (!mObject.has(id) || !mMapWrapper.has(id)) continue;
      com.riiablo.engine.server.component.Object object = mObject.get(id);
      MapWrapper wrapper = mMapWrapper.get(id);
      if (wrapper != null && wrapper.zone == zone && object != null && object.base != null
          && object.base.Id == 566) return true;
    }
    return false;
  }

  private void setObjective(CharData data) {
    updateRecord(data, Act4IzualQuest::completeObjective, "izual-defeated");
  }

  private void addSkillPoints(int playerId, CharData data, int amount) {
    Attributes attrs = mAttributesWrapper.has(playerId)
        ? mAttributesWrapper.get(playerId).attrs : data.getStats();
    if (attrs == null) return;
    add(attrs.base(), amount);
    add(attrs.aggregate(), amount);
  }

  private static void add(StatListRef stats, int amount) {
    StatRef current = stats.get(Stat.newskills);
    stats.put(Stat.newskills, (current == null ? 0 : current.asInt()) + amount);
  }

  private short record(CharData data) {
    return data.getQuests(Riiablo.ACT4)[Act4IzualQuest.RECORD];
  }

  private short diabloRecord(CharData data) {
    return data.getQuests(Riiablo.ACT4)[Act4DiabloQuest.RECORD];
  }

  private short hellforgeRecord(CharData data) {
    return data.getQuests(Riiablo.ACT4)[Act4HellforgeQuest.RECORD];
  }

  private void updateHellforgeRecord(CharData data, short next, String reason) {
    short previous = hellforgeRecord(data);
    if (previous == next) return;
    data.getQuests(Riiablo.ACT4)[Act4HellforgeQuest.RECORD] = next;
    persist(data);
    log.info("[A4Q3] Quest record changed: character={} reason={} previous=0x{} next=0x{}",
        data.name, reason, Integer.toHexString(Short.toUnsignedInt(previous)),
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  private void updateDiabloRecord(CharData data) {
    updateDiabloRecord(data, Act4DiabloQuest::start, "entered-chaos-sanctuary");
  }

  private void updateDiabloRecord(CharData data,
      java.util.function.UnaryOperator<Short> transition, String reason) {
    short previous = diabloRecord(data);
    short next = transition.apply(previous);
    if (next == previous) return;
    data.getQuests(Riiablo.ACT4)[Act4DiabloQuest.RECORD] = next;
    persist(data);
    log.info("[A4Q2] Quest record changed: character={} reason={} previous=0x{} next=0x{}",
        data.name, reason, Integer.toHexString(Short.toUnsignedInt(previous)),
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  private void completeDiabloRecord(CharData data) {
    short previous = diabloRecord(data);
    short next = Act4DiabloQuest.complete(previous);
    if (next == previous) return;
    data.getQuests(Riiablo.ACT4)[Act4DiabloQuest.RECORD] = next;
    persist(data);
    log.info("[A4Q2] Quest record changed: character={} reason=diablo-defeated previous=0x{} next=0x{}",
        data.name, Integer.toHexString(Short.toUnsignedInt(previous)),
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  private void dataSetDiabloRecord(CharData data, short next) {
    data.getQuests(Riiablo.ACT4)[Act4DiabloQuest.RECORD] = next;
    persist(data);
  }

  private static void persist(CharData data) {
    if (data.managed && Riiablo.saves != null) D2SWriter.INSTANCE.save(data);
  }

  private void updateRecord(CharData data, java.util.function.UnaryOperator<Short> transition,
      String reason) {
    short previous = record(data);
    short next = transition.apply(previous);
    if (previous == next) return;
    data.getQuests(Riiablo.ACT4)[Act4IzualQuest.RECORD] = next;
    if (data.managed && Riiablo.saves != null) D2SWriter.INSTANCE.save(data);
    log.info("[A4Q1] Quest record changed: character={} reason={} previous=0x{} next=0x{}",
        data.name, reason, Integer.toHexString(Short.toUnsignedInt(previous)),
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  private int levelId(int entityId) {
    if (!mMapWrapper.has(entityId)) return -1;
    MapWrapper wrapper = mMapWrapper.get(entityId);
    return wrapper == null || wrapper.zone == null || wrapper.zone.level == null
        ? -1 : wrapper.zone.level.Id;
  }

  private static boolean isAct4Level(int levelId) {
    return levelId >= D2LevelIds.LEVEL_OUTERSTEPPES
        && levelId <= D2LevelIds.LEVEL_CHAOSSANCTUM;
  }
}
