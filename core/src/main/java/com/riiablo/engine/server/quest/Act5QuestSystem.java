package com.riiablo.engine.server.quest;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntSet;
import com.d2moo.common.drlg.D2LevelIds;
import com.d2moo.common.drlg.D2SuperUniques;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.ExperienceManager;
import com.riiablo.attributes.ExperienceTable;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.SuperUniques;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.ObjectInteractor;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.NativeObjectState;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SuperUnique;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.event.NpcQuestMessageEvent;
import com.riiablo.engine.server.event.QuestObjectInteractionEvent;
import com.riiablo.engine.server.event.ZoneChangeEvent;
import com.riiablo.engine.server.object.NativeQuestObjectResolver;
import com.riiablo.engine.server.monster.MonsterRank;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.Quality;
import com.riiablo.engine.server.party.Party;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import com.riiablo.save.D2SWriter;
import net.mostlyoriginal.api.event.common.Subscribe;

/** Server-authoritative A5Q1 Shenk lifecycle and Larzuk completion state. */
@Wire(failOnNull = false)
public class Act5QuestSystem extends BaseSystem {
  private static final Logger log = LogManager.getLogger(Act5QuestSystem.class);

  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Angle> mAngle;
  protected ComponentMapper<SuperUnique> mSuperUnique;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<NativeObjectState> mNativeObjectState;
  protected ComponentMapper<com.riiablo.engine.server.component.Object> mObject;
  protected ComponentMapper<Interactable> mInteractable;
  protected ComponentMapper<SummonedPet> mSummonedPet;
  protected ComponentMapper<Mercenary> mMercenary;
  @Wire(name = "factory", failOnNull = false)
  protected EntityFactory factory;
  @Wire(failOnNull = false)
  protected ObjectInteractor objectInteractor;
  @Wire(name = "partyManager", failOnNull = false)
  protected PartyManager partyManager;
  @Wire(name = "map", failOnNull = false)
  protected Map map;
  @Wire(failOnNull = false)
  protected ItemGenerator itemGenerator;
  private ExperienceManager experienceManager;

  private EntitySubscription playersByZone;
  private EntitySubscription monstersByZone;
  private EntitySubscription objectsByZone;
  private final IntSet spawnedShenkLevels = new IntSet();
  private final IntSet killedShenkEntities = new IntSet();
  private final IntSet spawnedNihlathakLevels = new IntSet();
  private final IntSet killedNihlathakEntities = new IntSet();
  private final IntSet activatedAncientStatues = new IntSet();
  private final IntIntMap ancientStatueEntities = new IntIntMap();
  private Map.Zone trackedAncientZone;
  private final IntSet spawnedAncientEntities = new IntSet();
  private final IntSet killedAncientEntities = new IntSet();
  private final IntSet spawnedBaalLevels = new IntSet();
  private final IntSet killedBaalEntities = new IntSet();
  private final IntSet baalWaveEntities = new IntSet();
  /** Rebuilt every simulation tick from SuperUnique.hcIdx, never persisted as
   * an entity id.  Entity ids are not stable across Room/ECS reconstruction. */
  private final IntIntMap baalWaveLeaders = new IntIntMap();
  private static final int BAAL_WAVE_RECOVERY_WINDOW_TICKS = 25;
  private int baalWaveMissingTicks;
  @Wire(name = "act5QuestGameState", failOnNull = false)
  protected Act5QuestGameState act5QuestGameState;
  private final Act5QuestGameState fallbackGameState = new Act5QuestGameState();
  private final IntSet rescuedCages = new IntSet();
  private Map.Zone trackedRescueZone;

  @Override
  protected void initialize() {
    playersByZone = world.getAspectSubscriptionManager().get(
        Aspect.all(Player.class, MapWrapper.class));
    monstersByZone = world.getAspectSubscriptionManager().get(
        Aspect.all(Monster.class, MapWrapper.class));
    objectsByZone = world.getAspectSubscriptionManager().get(
        Aspect.all(com.riiablo.engine.server.component.Object.class, MapWrapper.class));
    experienceManager = world.getSystem(ExperienceManager.class);
  }

  @Override
  protected void processSystem() {
    Act5BaalWaveState baalWaveState = gameState().baalWaves;
    rebuildBaalWaveEntityIndex();
    if (baalWaveState.started() && !baalWaveState.finished()) {
      int action = baalWaveState.tick(isBaalThroneClear());
      if (action >= 0 && action < Act5BaalQuest.WAVE_COUNT) {
        spawnBaalWave(action);
      } else if (action == Act5BaalWaveState.SPAWN_BAAL) {
        spawnBaalAfterWaves();
      }
    }
    // Native quest objects can be created after ZoneChangeEvent (RoomEx
    // activation), so keep the record/object reconciliation in the fixed
    // simulation phase as well as the zone-change callback.
    rebuildAct5QuestObjectState();
    rebuildNihlathakPortalState();
    rebuildBaalTyraelState();
    rebuildBaalEndPortalState();
  }

  @Subscribe
  public void onZoneChanged(ZoneChangeEvent event) {
    if (event == null || event.zone == null || event.zone.level == null
        || !mPlayer.has(event.entityId)) return;
    Player player = mPlayer.get(event.entityId);
    if (player == null || player.data == null) return;
    syncBaalChangedLevel(event, player);
    if (event.zone.level.Id == D2LevelIds.LEVEL_BLOODYFOOTHILLS) {
      updateRecord(player.data, Act5ShenkQuest::start, "entered-bloody-foothills");
      spawnShenkIfNeeded(event.entityId, event.zone.level.Id);
    } else if (event.zone.level.Id == Act5RescueQuest.FRIGID_HIGHLANDS) {
      updateRescueRecord(player.data, Act5RescueQuest::start, "entered-frigid-highlands");
      rebuildRescueCageState(event.zone);
    } else if (event.zone.level.Id == Act5PrisonQuest.FROZEN_RIVER) {
      restoreFrozenAnyaState(event.zone);
    }
    if (event.zone.level.Id == Act5NihlathakQuest.NIHLATHAK_TEMPLE
        || event.zone.level.Id == Act5NihlathakQuest.HALLS_OF_VAUGHT) {
      if (!hasPrisonPrerequisite(player.data)) return;
      updateNihlathakRecord(player.data, Act5NihlathakQuest::enterArea,
          "entered-nihlathak-area");
      if (event.zone.level.Id == Act5NihlathakQuest.HALLS_OF_VAUGHT) {
        spawnNihlathakIfNeeded(event.entityId, event.zone.level.Id);
      }
    }
    if (isAncientSummit(event.zone.level.Id)) {
      if (!hasNihlathakPrerequisite(player.data)) return;
      updateAncientsRecord(player.data, Act5AncientsQuest::enterArea,
          "entered-arreat-summit");
      rebuildAncientsState(event.zone, event.entityId);
    }
    if (isBaalArea(event.zone.level.Id)) {
      if (!hasAncientsPrerequisite(player.data)) return;
      updateBaalRecord(player.data, Act5BaalQuest::enterArea, "entered-worldstone-area");
      if (event.zone.level.Id == Act5BaalQuest.THRONE_OF_DESTRUCTION) {
        startBaalWavesIfNeeded(event.entityId);
      }
    }
  }

  /**
   * Mirrors the state-only part of D2MOO's A5Q6 ChangedLevel/JoinedGame
   * callbacks.  The ECS event does not carry the old level, so the transition
   * is derived from the authoritative destination and current quest record;
   * the resulting PlayerP/QuestResult snapshot is the Java protocol equivalent
   * of the native quest packet 0x50/0x5D updates.
   */
  private void syncBaalChangedLevel(ZoneChangeEvent event, Player player) {
    int newLevel = event.zone.level.Id;
    if (!isAct5Level(newLevel) || player == null || player.data == null) return;
    short previous = baalRecord(player.data);

    // Native PlayerStartedGame/PlayerJoinedGame set CUSTOM6 for a completed
    // character.  Do this before the portal-specific branch so a reconnect
    // into Harrogath receives the same completion marker.
    if (NativeQuestRecord.has(previous, NativeQuestRecord.REWARD_GRANTED)
        || NativeQuestRecord.has(previous, NativeQuestRecord.COMPLETED_BEFORE)) {
      short next = NativeQuestRecord.set(previous, NativeQuestRecord.CUSTOM6);
      updateBaalRecord(player.data, ignored -> next, "changed-level-completed");
      return;
    }

    // A late/reconnected party member must receive Chamber credit even though
    // the original Baal DeathEvent and its entity id are gone.
    if (newLevel == Act5BaalQuest.WORLDSTONE_CHAMBER
        && gameState().isBaalDefeated()
        && Act5BaalQuest.canReceiveDirectReward(
            player.data.isExpansion(), newLevel)) {
      if (completeBaalAndProgression(event.entityId, player, "changed-level-baal-kill")) {
        log.info("[A5Q6] ChangedLevel direct reward: player={} level={}",
            event.entityId, newLevel);
      }
      return;
    }

    // Party members entering the Chamber after a teammate's kill inherit the
    // native PRIMARYGOALDONE propagation; direct Chamber completion then adds
    // the reward/progression exactly once.
    if (newLevel == Act5BaalQuest.WORLDSTONE_CHAMBER && partyManager != null) {
      short party = partyManager.getPartyId(event.entityId);
      if (party != Party.INVALID_ID && playersByZone != null) {
        IntBag players = playersByZone.getEntities();
        int[] ids = players.getData();
        for (int i = 0; i < players.size(); i++) {
          int id = ids[i];
          Player member = mPlayer.get(id);
          if (member == null || member.data == null
              || partyManager.getPartyId(id) != party) continue;
          short memberRecord = baalRecord(member.data);
          if (!NativeQuestRecord.has(memberRecord, NativeQuestRecord.PRIMARY_GOAL_DONE)) continue;
          if (Act5BaalQuest.canReceiveDirectReward(
              player.data.isExpansion(), newLevel)) {
            completeBaalAndProgression(event.entityId, player, "changed-level-party-sync");
          }
          break;
        }
      }
    }

    if (newLevel >= Act5BaalQuest.THRONE_OF_DESTRUCTION) {
      updateBaalRecord(player.data, Act5BaalQuest::start, "changed-level-throne");
    } else if (newLevel == Act5BaalQuest.HARROGATH
        && NativeQuestRecord.has(previous, NativeQuestRecord.STARTED)) {
      updateBaalRecord(player.data, Act5BaalQuest::leaveTown, "changed-level-harrogath");
    }
  }

  @Subscribe
  public void onQuestObjectInteraction(QuestObjectInteractionEvent interaction) {
    if (interaction == null || !mPlayer.has(interaction.playerId)
        || !mMapWrapper.has(interaction.entityId)) return;
    if (interaction.type == NativeQuestObjectResolver.Type.FROZEN_ANYA) {
      onFrozenAnyaInteraction(interaction);
      return;
    }
    if (interaction.type == NativeQuestObjectResolver.Type.ANCIENT_STATUE) {
      onAncientStatueInteraction(interaction);
      return;
    }
    if (interaction.type == NativeQuestObjectResolver.Type.ANCIENTS_ALTAR) {
      onAncientsAltarInteraction(interaction);
      return;
    }
    if (interaction.type == NativeQuestObjectResolver.Type.ANCIENT_DOOR
        || interaction.type == NativeQuestObjectResolver.Type.SUMMIT_DOOR) {
      onAncientsDoorInteraction(interaction);
      return;
    }
    if (interaction.type != NativeQuestObjectResolver.Type.CAGED_SOLDIER) return;
    if (levelId(interaction.entityId) != Act5RescueQuest.FRIGID_HIGHLANDS) return;
    Player player = mPlayer.get(interaction.playerId);
    if (player == null || player.data == null || rescuedCages.contains(interaction.entityId)) return;
    short record = rescueRecord(player.data);
    if (Act5RescueQuest.isFinished(record)
        || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)) return;
    rescuedCages.add(interaction.entityId);
    interaction.accept();
    short next = Act5RescueQuest.start(record);
    if (rescuedCages.size >= Act5RescueQuest.REQUIRED_CAGES) {
      next = Act5RescueQuest.complete(next);
    }
    final short transition = next;
    updateRescueRecord(player.data, ignored -> transition,
        "cage-opened-" + rescuedCages.size);
    log.info("[A5Q2] Cage opened: player={} object={} cages={}/{} soldiers={}",
        interaction.playerId, interaction.entityId, rescuedCages.size,
        Act5RescueQuest.REQUIRED_CAGES,
        rescuedCages.size * Act5RescueQuest.SOLDIERS_PER_CAGE);
  }

  private void onFrozenAnyaInteraction(QuestObjectInteractionEvent interaction) {
    if (levelId(interaction.entityId) != Act5PrisonQuest.FROZEN_RIVER) return;
    Player player = mPlayer.get(interaction.playerId);
    if (player == null || player.data == null) return;
    short record = prisonRecord(player.data);
    if (!Act5PrisonQuest.hasPotion(record) || Act5PrisonQuest.isFinished(record)
        || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)) return;
    interaction.accept();
    updatePrisonRecord(player.data, Act5PrisonQuest::complete, "anya-defrosted");
    log.info("[A5Q3] Frozen Anya defrosted: player={} object={}",
        interaction.playerId, interaction.entityId);
  }

  private void onAncientStatueInteraction(QuestObjectInteractionEvent interaction) {
    if (!isAncientSummit(levelId(interaction.entityId)) || mPosition == null
        || !mPlayer.has(interaction.playerId)) return;
    Player player = mPlayer.get(interaction.playerId);
    if (player == null || player.data == null) return;
    short record = ancientsRecord(player.data);
    if (Act5AncientsQuest.isFinished(record)
        || playerLevel(interaction.playerId, player) < Act5AncientsQuest.requiredLevel(
            difficulty(interaction.playerId))) return;
    if (!activatedAncientStatues.add(interaction.entityId)) return;
    ancientStatueEntities.put(interaction.objectClassId, interaction.entityId);
    interaction.accept();
    updateAncientsRecord(player.data, Act5AncientsQuest::start, "ancient-statue-activated");
    if (activatedAncientStatues.size >= 3) spawnAncients(interaction.playerId);
    log.info("[A5Q5] Ancient statue activated: player={} object={} count={}/3",
        interaction.playerId, interaction.entityId, activatedAncientStatues.size);
  }

  /** Mirrors D2MOO's OperateFunction65: the altar opens its animation state
   * but does not grant the quest or bypass the three Ancient encounter. */
  private void onAncientsAltarInteraction(QuestObjectInteractionEvent interaction) {
    if (!isAncientSummit(levelId(interaction.entityId)) || !mPlayer.has(interaction.playerId)) return;
    Player player = mPlayer.get(interaction.playerId);
    if (player == null || player.data == null || Act5AncientsQuest.isFinished(
        ancientsRecord(player.data))) return;
    interaction.accept(Engine.Object.MODE_ON);
    updateAncientsRecord(player.data, Act5AncientsQuest::start, "ancients-altar-opened");
    log.info("[A5Q5] Ancients altar opened: player={} object={}",
        interaction.playerId, interaction.entityId);
  }

  /** Door handlers only change the visible mode once the native quest permits
   * it. The actual zone transition remains owned by the map Warp entity. */
  private void onAncientsDoorInteraction(QuestObjectInteractionEvent interaction) {
    if (!isAncientSummit(levelId(interaction.entityId)) || !mPlayer.has(interaction.playerId)) return;
    Player player = mPlayer.get(interaction.playerId);
    if (player == null || player.data == null) return;
    short record = ancientsRecord(player.data);
    boolean allowed = interaction.type == NativeQuestObjectResolver.Type.SUMMIT_DOOR
        ? Act5AncientsQuest.canOpenSummitDoor(record)
        : Act5AncientsQuest.canOpenAncientsDoor(record);
    if (!allowed) return;
    interaction.accept(Engine.Object.MODE_ON);
    log.info("[A5Q5] Summit door opened: player={} object={} type={}",
        interaction.playerId, interaction.entityId, interaction.type);
  }

  /** D2MOO resets the Ancient encounter when the last living Summit player
   * dies. This is separate from monster DeathEvent handling because the player
   * is not a Monster component. */
  @Subscribe
  public void onPlayerDeath(DeathEvent event) {
    if (event == null || !mPlayer.has(event.victim) || mMapWrapper == null
        || !mMapWrapper.has(event.victim)) return;
    MapWrapper wrapper = mMapWrapper.get(event.victim);
    if (wrapper == null || wrapper.zone == null || wrapper.zone.level == null
        || !isAncientSummit(wrapper.zone.level.Id) || !ancientEncounterActive()) return;
    if (!Act5AncientsQuest.shouldResetEncounter(
        ancientEncounterActive(), killedAncientEntities.size >= 3,
        countLivingSummitPlayers())) return;
    resetAncientEncounter();
  }

  @Subscribe
  public void onMonsterKilled(DeathEvent event) {
    if (event == null || event.victim < 0 || !mMonster.has(event.victim)
        || !mMapWrapper.has(event.victim)) return;
    if (isAncient(event.victim) && isAncientSummit(levelId(event.victim))
        && killedAncientEntities.add(event.victim)) {
      onAncientKilled(event.victim);
      return;
    }
    if (isBaal(event.victim) && isBaalArea(levelId(event.victim))
        && killedBaalEntities.add(event.victim)) {
      gameState().markBaalDefeated();
      completeBaalForPlayers();
      // D2MOO spawns Tyrael3 from the post-death missile callback.  The
      // DeathEvent is the authoritative equivalent in this server; the Last
      // Portal itself is intentionally deferred until Tyrael's final message.
      spawnA5Q6Tyrael(event.victim);
      log.info("[A5Q6] Baal defeated: victim={} killer={}", event.victim, event.killer);
      return;
    }
    if (baalWaveEntities.remove(event.victim)) {
      log.info("[A5Q6] Baal wave member defeated: entity={} remaining={}",
          event.victim, baalWaveEntities.size);
      if (baalWaveEntities.isEmpty()) gameState().baalWaves.markWaveCleared();
      return;
    }
    if (isNihlathak(event.victim)
        && levelId(event.victim) == Act5NihlathakQuest.HALLS_OF_VAUGHT
        && killedNihlathakEntities.add(event.victim)) {
      completeNihlathakForPlayers();
      log.info("[A5Q4] Nihlathak defeated: victim={} killer={}", event.victim, event.killer);
      return;
    }
    if (!isShenk(event.victim)
        || levelId(event.victim) != D2LevelIds.LEVEL_BLOODYFOOTHILLS
        || !killedShenkEntities.add(event.victim)) return;
    if (playersByZone == null) return;
    IntSet parties = new IntSet();
    IntBag players = playersByZone.getEntities();
    int[] ids = players.getData();
    for (int i = 0; i < players.size(); i++) {
      int id = ids[i];
      Player player = mPlayer.get(id);
      if (player == null || player.data == null || !isAct5Level(levelId(id))) continue;
      if (levelId(id) == D2LevelIds.LEVEL_BLOODYFOOTHILLS) {
        complete(player.data);
        if (partyManager != null) {
          short party = partyManager.getPartyId(id);
          if (party != Party.INVALID_ID) parties.add(party);
        }
      }
    }
    if (partyManager != null) {
      for (int i = 0; i < players.size(); i++) {
        int id = ids[i];
        Player player = mPlayer.get(id);
        if (player == null || player.data == null || !isAct5Level(levelId(id))) continue;
        if (parties.contains(partyManager.getPartyId(id))) complete(player.data);
      }
    }
    log.info("[A5Q1] Shenk defeated: victim={} killer={}", event.victim, event.killer);
  }

  @Subscribe
  public void onNpcQuestMessage(NpcQuestMessageEvent event) {
    if (event == null || !mPlayer.has(event.entityId) || !mMonster.has(event.npcId)) return;
    Player player = mPlayer.get(event.entityId);
    Monster npc = mMonster.get(event.npcId);
    if (player == null || player.data == null || npc == null || npc.monstats == null) return;
    if (isQualKehk(npc.monstats)) {
      onQualKehkMessage(event, player);
      return;
    }
    if (isMalah(npc.monstats)) {
      onMalahMessage(event, player);
      return;
    }
    if (isDrehya(npc.monstats)) {
      onDrehyaMessage(event, player);
      return;
    }
    if (Act5BaalQuest.isTyrael3(npc.monstats.hcIdx, npc.monstats.Id)) {
      onBaalTyraelMessage(event, player, npc);
      return;
    }
    if (!isLarzuk(npc.monstats) || event.messageIndex != Act5ShenkQuest.MESSAGE_LARZUK_REWARD) return;
    short previous = record(player.data);
    short next = Act5ShenkQuest.claimReward(previous);
    if (next == previous) return;
    updateRecord(player.data, ignored -> next, "larzuk-shenk-reward");
    log.info("[A5Q1] Larzuk reward claimed: player={} (socket service entitlement)",
        event.entityId);
  }

  private void onMalahMessage(NpcQuestMessageEvent event, Player player) {
    short previous = prisonRecord(player.data);
    if (event.messageIndex == Act5PrisonQuest.MESSAGE_MALAH_INIT) {
      updatePrisonRecord(player.data, Act5PrisonQuest::markPotion, "malah-defrost-potion");
    } else if (event.messageIndex == Act5PrisonQuest.MESSAGE_MALAH_REWARD
        && Act5PrisonQuest.canClaimReward(previous)) {
      updatePrisonRecord(player.data, Act5PrisonQuest::claimReward, "malah-anya-reward");
    }
  }

  private void onDrehyaMessage(NpcQuestMessageEvent event, Player player) {
    if (event.messageIndex == Act5NihlathakQuest.MESSAGE_DREHYA_START) {
      if (hasPrisonPrerequisite(player.data)) {
        updateNihlathakRecord(player.data, Act5NihlathakQuest::start, "drehya-nihlathak-start");
        ensureNihlathakPortal(event.npcId);
      }
      return;
    }
    if (event.messageIndex != Act5NihlathakQuest.MESSAGE_DREHYA_REWARD) return;
    short previous = nihlathakRecord(player.data);
    if (!Act5NihlathakQuest.canClaimReward(previous)) return;
    updateNihlathakRecord(player.data, Act5NihlathakQuest::claimReward,
        "drehya-nihlathak-reward");
    log.info("[A5Q4] Drehya reward claimed: player={}", event.entityId);
  }

  /** Recreates the A5Q4 town portal after a reconnect or map/object rebuild.
   * D2MOO stores the quest record, while the visual/Warp entities themselves
   * are transient; the destination Warp is therefore the idempotency key. */
  private void rebuildNihlathakPortalState() {
    if (playersByZone == null || monstersByZone == null) return;
    IntBag players = playersByZone.getEntities();
    int[] playerIds = players.getData();
    for (int i = 0; i < players.size(); i++) {
      int playerId = playerIds[i];
      if (!mPlayer.has(playerId)) continue;
      Player player = mPlayer.get(playerId);
      if (player == null || player.data == null
          || !Act5NihlathakQuest.shouldOpenPortal(
              nihlathakRecord(player.data), hasPrisonPrerequisite(player.data))) continue;
      int drehya = findDrehyaInHarrogath();
      if (drehya != Engine.INVALID_ENTITY) {
        ensureNihlathakPortal(drehya);
        return;
      }
    }
  }

  private int findDrehyaInHarrogath() {
    if (monstersByZone == null) return Engine.INVALID_ENTITY;
    IntBag monsters = monstersByZone.getEntities();
    int[] ids = monsters.getData();
    for (int i = 0; i < monsters.size(); i++) {
      int id = ids[i];
      if (levelId(id) == D2LevelIds.LEVEL_HARROGATH && isDrehya(
          mMonster.get(id) == null ? null : mMonster.get(id).monstats)) return id;
    }
    return Engine.INVALID_ENTITY;
  }

  private boolean ensureNihlathakPortal(int drehyaId) {
    if (factory == null || drehyaId < 0 || !mPosition.has(drehyaId)) return false;
    Map.Zone town = null;
    if (mMapWrapper.has(drehyaId)) {
      MapWrapper wrapper = mMapWrapper.get(drehyaId);
      if (wrapper != null && wrapper.zone != null && wrapper.zone.level != null
          && wrapper.zone.level.Id == D2LevelIds.LEVEL_HARROGATH) town = wrapper.zone;
    }
    if (town == null) town = findZone(D2LevelIds.LEVEL_HARROGATH);
    if (town == null) return false;
    int questWarpIndex = QuestWarp.encode(Act5NihlathakQuest.NIHLATHAK_TEMPLE);
    if (town.findWarp(questWarpIndex) != Engine.INVALID_ENTITY) return true;

    Position source = mPosition.get(drehyaId);
    float portalX = source.position.x + 10f;
    float portalY = source.position.y + 5f;
    int visual = factory.createStaticObjectByClassId(
        NativeQuestObjectResolver.TOWN_PORTAL, portalX, portalY);
    int warp = factory.createQuestWarp(Act5NihlathakQuest.NIHLATHAK_TEMPLE,
        portalX, portalY);
    if (warp == Engine.INVALID_ENTITY) {
      if (visual != Engine.INVALID_ENTITY && world != null) world.delete(visual);
      log.error("[A5Q4] Nihlathak portal creation failed: drehya={} destination={}",
          drehyaId, Act5NihlathakQuest.NIHLATHAK_TEMPLE);
      return false;
    }
    town.addWarp(warp);
    log.info("[A5Q4] Nihlathak portal opened: drehya={} visual={} warp={} destination={} "
        + "position=({}, {})", drehyaId, visual, warp,
        Act5NihlathakQuest.NIHLATHAK_TEMPLE, portalX, portalY);
    return true;
  }

  private void onQualKehkMessage(NpcQuestMessageEvent event, Player player) {
    short previous = rescueRecord(player.data);
    if (event.messageIndex == Act5RescueQuest.MESSAGE_QUAL_KEHK_INIT) {
      updateRescueRecord(player.data, Act5RescueQuest::start, "qual-kehk-init");
      return;
    }
    if (event.messageIndex == Act5AncientsQuest.MESSAGE_QUAL_KEHK_START
        && hasNihlathakPrerequisite(player.data)) {
      updateAncientsRecord(player.data, Act5AncientsQuest::start, "qual-kehk-ancients-start");
      return;
    }
    if (event.messageIndex != Act5RescueQuest.MESSAGE_QUAL_KEHK_REWARD
        || !Act5RescueQuest.canClaimReward(previous)) return;
    short next = Act5RescueQuest.claimReward(previous);
    updateRescueRecord(player.data, ignored -> next, "qual-kehk-reward");
    dropRescueRunes(event.npcId);
    log.info("[A5Q2] Qual-Kehk reward claimed: player={} runes=r07,r08,r09", event.entityId);
  }

  private void dropRescueRunes(int npcId) {
    if (itemGenerator == null || factory == null || !mPosition.has(npcId)) return;
    Position position = mPosition.get(npcId);
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
        log.warn("[A5Q2] Qual-Kehk rune generation failed: code={}", runes[i], t);
      }
    }
  }

  private void spawnShenkIfNeeded(int playerId, int levelId) {
    if (spawnedShenkLevels.contains(levelId) || factory == null || mPosition == null) return;
    if (!mPlayer.has(playerId)) return;
    Player player = mPlayer.get(playerId);
    if (player == null || player.data == null
        || !Act5ShenkQuest.shouldSpawnBoss(record(player.data))) {
      log.debug("[A5Q1] Shenk spawn suppressed by persistent quest record: player={} level={}",
          playerId, levelId);
      return;
    }
    if (playersByZone == null) return;
    IntBag entities = world.getAspectSubscriptionManager().get(
        Aspect.all(Monster.class, MapWrapper.class)).getEntities();
    int[] ids = entities.getData();
    for (int i = 0; i < entities.size(); i++) {
      int id = ids[i];
      if (isShenk(id) && levelId(id) == levelId) {
        spawnedShenkLevels.add(levelId);
        return;
      }
    }
    MonStats.Entry stats = resolveShenkStats();
    Position origin = mPosition.has(playerId) ? mPosition.get(playerId) : null;
    if (stats == null || origin == null) return;
    int entity = factory.createMonster(stats, origin.position.x + 3f, origin.position.y);
    if (entity < 0) return;
    if (mSuperUnique != null) {
      SuperUniques.Entry entry = resolveShenkSuperUnique();
      mSuperUnique.create(entity).set(
          entry == null ? Act5ShenkQuest.SUPERUNIQUE_SIEGE_BOSS : entry.hcIdx,
          entry == null ? "Siege Boss" : entry.Superunique);
    }
    spawnedShenkLevels.add(levelId);
    log.info("[A5Q1] Shenk spawned: player={} entity={} level={}", playerId, entity, levelId);
  }

  /** Reconciles transient ECS object ids with the persistent native object
   * snapshots.  RoomEx can be rebuilt after a reconnect, so entity ids alone
   * must never be used as the source of truth for A5Q2/A5Q3. */
  private void rebuildAct5QuestObjectState() {
    if (playersByZone == null || objectsByZone == null || mMapWrapper == null) return;
    IntBag players = playersByZone.getEntities();
    int[] ids = players.getData();
    for (int i = 0; i < players.size(); i++) {
      int playerId = ids[i];
      if (!mPlayer.has(playerId)) continue;
      MapWrapper wrapper = mMapWrapper.get(playerId);
      if (wrapper == null || wrapper.zone == null || wrapper.zone.level == null) continue;
      int level = wrapper.zone.level.Id;
      if (level == Act5RescueQuest.FRIGID_HIGHLANDS) {
        rebuildRescueCageState(wrapper.zone);
      } else if (level == Act5PrisonQuest.FROZEN_RIVER) {
        restoreFrozenAnyaState(wrapper.zone);
      } else if (isAncientSummit(level)) {
        rebuildAncientsState(wrapper.zone, playerId);
      }
    }
  }

  /** Reconciles A5Q5 statues and doors from persistent native object snapshots. */
  private void rebuildAncientsState(Map.Zone zone, int playerId) {
    if (zone == null || zone.level == null || !isAncientSummit(zone.level.Id)
        || objectsByZone == null) return;
    if (trackedAncientZone != zone) {
      trackedAncientZone = zone;
      activatedAncientStatues.clear();
      ancientStatueEntities.clear();
    }

    IntBag objects = objectsByZone.getEntities();
    int[] objectIds = objects.getData();
    for (int i = 0; i < objects.size(); i++) {
      int id = objectIds[i];
      if (!mObject.has(id) || !mMapWrapper.has(id)) continue;
      com.riiablo.engine.server.component.Object object = mObject.get(id);
      MapWrapper wrapper = mMapWrapper.get(id);
      if (wrapper == null || wrapper.zone != zone || object == null || object.base == null) continue;

      int classId = object.base.Id;
      NativeObjectState state = mNativeObjectState != null && mNativeObjectState.has(id)
          ? mNativeObjectState.get(id) : null;
      if (classId >= Act5AncientsQuest.FIRST_ANCIENT_STATUE
          && classId <= Act5AncientsQuest.LAST_ANCIENT_STATUE) {
        ancientStatueEntities.put(classId, id);
        boolean activated = state != null ? state.activated
            : (object.stateFlags & com.riiablo.engine.server.component.Object.STATE_ACTIVATED) != 0;
        if (activated) activatedAncientStatues.add(id);
      } else if (classId == NativeQuestObjectResolver.ANCIENT_DOOR
          || classId == NativeQuestObjectResolver.SUMMIT_DOOR) {
        Player player = mPlayer.has(playerId) ? mPlayer.get(playerId) : null;
        if (player != null && player.data != null) {
          short record = ancientsRecord(player.data);
          boolean open = classId == NativeQuestObjectResolver.SUMMIT_DOOR
              ? Act5AncientsQuest.canOpenSummitDoor(record)
              : Act5AncientsQuest.canOpenAncientsDoor(record);
          if (open) restoreAncientDoor(id, state, object);
        }
      }
    }

    Player player = mPlayer.has(playerId) ? mPlayer.get(playerId) : null;
    if (activatedAncientStatues.size >= 3 && spawnedAncientEntities.size == 0
        && player != null && player.data != null
        && !Act5AncientsQuest.isFinished(ancientsRecord(player.data))) {
      spawnAncients(playerId);
    }
  }

  private void restoreAncientDoor(int entityId, NativeObjectState state,
      com.riiablo.engine.server.component.Object object) {
    if (state != null) {
      state.persistOpened(true);
      state.persistMode((byte) Engine.Object.MODE_ON);
    }
    object.mode = (byte) Engine.Object.MODE_ON;
    object.stateFlags |= com.riiablo.engine.server.component.Object.STATE_OPENED;
    if (mCofReference != null && mCofReference.has(entityId)) {
      mCofReference.get(entityId).mode = Engine.Object.MODE_ON;
    }
  }

  private void rebuildRescueCageState(Map.Zone zone) {
    if (zone == null || zone.level == null
        || zone.level.Id != Act5RescueQuest.FRIGID_HIGHLANDS
        || objectsByZone == null) return;
    if (trackedRescueZone != zone) {
      trackedRescueZone = zone;
      rescuedCages.clear();
    }
    IntBag objects = objectsByZone.getEntities();
    int[] objectIds = objects.getData();
    for (int i = 0; i < objects.size(); i++) {
      int id = objectIds[i];
      if (!mObject.has(id) || !mMapWrapper.has(id)) continue;
      com.riiablo.engine.server.component.Object object = mObject.get(id);
      MapWrapper wrapper = mMapWrapper.get(id);
      if (wrapper == null || wrapper.zone != zone || object == null || object.base == null
          || object.base.Id != Act5RescueQuest.CAGED_SOLDIER_OBJECT) continue;
      NativeObjectState state = mNativeObjectState.has(id) ? mNativeObjectState.get(id) : null;
      boolean activated = state != null ? state.activated
          : (object.stateFlags & com.riiablo.engine.server.component.Object.STATE_ACTIVATED) != 0;
      if (activated) rescuedCages.add(id);
    }
    if (rescuedCages.size < Act5RescueQuest.REQUIRED_CAGES) return;

    // A5Q2's cage activation is game-wide.  A player reconnecting after the
    // fifth cage was opened must see the same pending reward even though the
    // old cage entity ids are gone.
    IntBag players = playersByZone == null ? null : playersByZone.getEntities();
    if (players == null) return;
    int[] playerIds = players.getData();
    for (int i = 0; i < players.size(); i++) {
      int playerId = playerIds[i];
      if (!mPlayer.has(playerId)) continue;
      MapWrapper wrapper = mMapWrapper.get(playerId);
      Player player = mPlayer.get(playerId);
      if (wrapper == null || wrapper.zone != zone || player == null || player.data == null) continue;
      short record = rescueRecord(player.data);
      if (Act5RescueQuest.shouldRestoreCompletion(record, rescuedCages.size)) {
        updateRescueRecord(player.data,
            value -> Act5RescueQuest.complete(Act5RescueQuest.start(value)),
            "cages-restored-" + rescuedCages.size);
      }
    }
  }

  private void restoreFrozenAnyaState(Map.Zone zone) {
    if (zone == null || zone.level == null
        || zone.level.Id != Act5PrisonQuest.FROZEN_RIVER || objectsByZone == null) return;
    boolean defrosted = false;
    IntBag players = playersByZone == null ? null : playersByZone.getEntities();
    if (players != null) {
      int[] ids = players.getData();
      for (int i = 0; i < players.size(); i++) {
        int playerId = ids[i];
        if (!mPlayer.has(playerId) || !mMapWrapper.has(playerId)) continue;
        MapWrapper wrapper = mMapWrapper.get(playerId);
        Player player = mPlayer.get(playerId);
        if (wrapper != null && wrapper.zone == zone && player != null && player.data != null
            && Act5PrisonQuest.shouldRestoreDefrostedObject(prisonRecord(player.data))) {
          defrosted = true;
          break;
        }
      }
    }
    if (!defrosted) return;
    IntBag objects = objectsByZone.getEntities();
    int[] ids = objects.getData();
    for (int i = 0; i < objects.size(); i++) {
      int id = ids[i];
      if (!mObject.has(id) || !mMapWrapper.has(id)) continue;
      com.riiablo.engine.server.component.Object object = mObject.get(id);
      MapWrapper wrapper = mMapWrapper.get(id);
      if (wrapper == null || wrapper.zone != zone || object == null || object.base == null
          || object.base.Id != Act5PrisonQuest.FROZEN_ANYA_OBJECT) continue;
      NativeObjectState state = mNativeObjectState.has(id) ? mNativeObjectState.get(id) : null;
      boolean already = state != null && state.activated
          && state.currentMode == Engine.Object.MODE_ON;
      if (!already && state != null) {
        state.persistActivated(true);
        state.persistOpened(true);
        state.persistMode((byte) Engine.Object.MODE_ON);
      }
      if (mCofReference.has(id)) mCofReference.get(id).mode = Engine.Object.MODE_ON;
      object.mode = (byte) Engine.Object.MODE_ON;
      object.stateFlags |= com.riiablo.engine.server.component.Object.STATE_OPENED
          | com.riiablo.engine.server.component.Object.STATE_ACTIVATED;
      if (mInteractable.has(id)) mInteractable.remove(id);
      if (!already) log.info("[A5Q3] Restored Frozen Anya defrosted state: entity={}", id);
    }
  }

  private void spawnNihlathakIfNeeded(int playerId, int levelId) {
    if (spawnedNihlathakLevels.contains(levelId) || factory == null || monstersByZone == null) return;
    IntBag entities = monstersByZone.getEntities();
    int[] ids = entities.getData();
    for (int i = 0; i < entities.size(); i++) {
      int id = ids[i];
      if (isNihlathak(id) && levelId(id) == levelId) {
        spawnedNihlathakLevels.add(levelId);
        return;
      }
    }
    MonStats.Entry stats = resolveNihlathakStats();
    Position origin = mPosition.has(playerId) ? mPosition.get(playerId) : null;
    if (stats == null || origin == null) return;
    int entity = factory.createMonster(stats, origin.position.x + 3f, origin.position.y);
    if (entity < 0) return;
    if (mSuperUnique != null) {
      SuperUniques.Entry unique = resolveNihlathakSuperUnique();
      mSuperUnique.create(entity).set(
          unique == null ? Act5NihlathakQuest.SUPERUNIQUE_NIHLATHAK_BOSS : unique.hcIdx,
          unique == null ? "Nihlathak" : unique.Superunique);
    }
    spawnedNihlathakLevels.add(levelId);
    log.info("[A5Q4] Nihlathak spawned: player={} entity={} level={}", playerId, entity, levelId);
  }

  private void spawnAncients(int playerId) {
    if (factory == null || spawnedAncientEntities.size > 0 || mPosition == null) return;
    Position origin = mPosition.has(playerId) ? mPosition.get(playerId) : null;
    if (origin == null) return;
    int[] uniqueIds = {
        Act5AncientsQuest.SUPERUNIQUE_ANCIENT_1,
        Act5AncientsQuest.SUPERUNIQUE_ANCIENT_2,
        Act5AncientsQuest.SUPERUNIQUE_ANCIENT_3};
    for (int i = 0; i < uniqueIds.length; i++) {
      MonStats.Entry stats = resolveAncientStats(uniqueIds[i], i + 1);
      if (stats == null) continue;
      int statueClass = Act5AncientsQuest.FIRST_ANCIENT_STATUE + i;
      int statueId = ancientStatueEntities.get(statueClass, -1);
      Position statue = statueId >= 0 && mPosition.has(statueId)
          ? mPosition.get(statueId) : origin;
      int entity = factory.createMonster(stats, statue.position.x, statue.position.y);
      if (entity < 0) continue;
      spawnedAncientEntities.add(entity);
      if (mSuperUnique != null) {
        SuperUniques.Entry unique = resolveAncientSuperUnique(uniqueIds[i]);
        mSuperUnique.create(entity).set(uniqueIds[i],
            unique == null ? "AncientBarb" + (i + 1) : unique.Superunique);
      }
    }
    log.info("[A5Q5] Ancients spawned: player={} count={}/3", playerId,
        spawnedAncientEntities.size);
  }

  private boolean isAncient(int entityId) {
    if (!mMonster.has(entityId)) return false;
    if (mSuperUnique != null && mSuperUnique.has(entityId)) {
      SuperUnique unique = mSuperUnique.get(entityId);
      if (unique != null && (unique.id == Act5AncientsQuest.SUPERUNIQUE_ANCIENT_1
          || unique.id == Act5AncientsQuest.SUPERUNIQUE_ANCIENT_2
          || unique.id == Act5AncientsQuest.SUPERUNIQUE_ANCIENT_3)) return true;
    }
    Monster monster = mMonster.get(entityId);
    MonStats.Entry stats = monster == null ? null : monster.monstats;
    return stats != null && (containsName(stats.Id, "ancientbarb")
        || containsName(stats.Id, "ancient") && containsName(stats.NameStr, "barb"));
  }

  private void onAncientKilled(int victim) {
    if (!spawnedAncientEntities.isEmpty() && !spawnedAncientEntities.contains(victim)) return;
    log.info("[A5Q5] Ancient defeated: entity={} count={}/3", victim,
        killedAncientEntities.size);
    if (killedAncientEntities.size < 3) return;
    completeAncientsForPlayers();
  }

  private boolean ancientEncounterActive() {
    return activatedAncientStatues.size > 0 || spawnedAncientEntities.size > 0
        || killedAncientEntities.size > 0;
  }

  private int countLivingSummitPlayers() {
    if (playersByZone == null) return 0;
    int living = 0;
    IntBag players = playersByZone.getEntities();
    int[] ids = players.getData();
    for (int i = 0; i < players.size(); i++) {
      int id = ids[i];
      if (!mPlayer.has(id) || !mMapWrapper.has(id)) continue;
      MapWrapper wrapper = mMapWrapper.get(id);
      if (wrapper == null || wrapper.zone == null || wrapper.zone.level == null
          || !isAncientSummit(wrapper.zone.level.Id)) continue;
      if (isPlayerAlive(id)) living++;
    }
    return living;
  }

  private boolean isPlayerAlive(int playerId) {
    if (mCofReference != null && mCofReference.has(playerId)) {
      byte mode = mCofReference.get(playerId).mode;
      if (mode == Engine.Player.MODE_DT || mode == Engine.Player.MODE_DD) return false;
    }
    if (mAttributesWrapper == null || !mAttributesWrapper.has(playerId)) return true;
    AttributesWrapper wrapper = mAttributesWrapper.get(playerId);
    return wrapper == null || wrapper.attrs == null
        || wrapper.attrs.aggregate().getValue(Stat.hitpoints, 0f) > 0f;
  }

  private void resetAncientEncounter() {
    IntArray entities = new IntArray(spawnedAncientEntities.size);
    for (IntSet.IntSetIterator it = spawnedAncientEntities.iterator(); it.hasNext; ) {
      entities.add(it.next());
    }
    for (int i = 0; i < entities.size; i++) {
      int entity = entities.get(i);
      if (world != null && world.getEntityManager().isActive(entity)) world.delete(entity);
    }
    spawnedAncientEntities.clear();
    killedAncientEntities.clear();
    activatedAncientStatues.clear();

    // Reset only the three native statues; the Map.NativeObject source is also
    // updated so a reconnect/recreation does not resurrect the activated mode.
    for (IntIntMap.Entry entry : ancientStatueEntities) resetAncientStatue(entry.value);
    log.info("[A5Q5] Ancient encounter reset: statues={} spawned={}",
        ancientStatueEntities.size, entities.size);
  }

  private void resetAncientStatue(int entityId) {
    byte initialMode = (byte) Engine.Object.MODE_NU;
    if (mNativeObjectState != null && mNativeObjectState.has(entityId)) {
      NativeObjectState state = mNativeObjectState.get(entityId);
      initialMode = state.initialMode;
      state.persistActivated(false);
      state.persistOpened(false);
      state.persistMode(state.initialMode);
      if (mCofReference != null && mCofReference.has(entityId)) {
        mCofReference.get(entityId).mode = state.initialMode;
      }
    }
    if (mObject != null && mObject.has(entityId)) {
      com.riiablo.engine.server.component.Object object = mObject.get(entityId);
      if (object != null) {
        object.mode = initialMode;
        object.stateFlags &= ~(com.riiablo.engine.server.component.Object.STATE_OPENED
            | com.riiablo.engine.server.component.Object.STATE_ACTIVATED);
        if (mInteractable != null && !mInteractable.has(entityId)
            && objectInteractor != null && object.base != null) {
          float range = object.base.OperateRange > 0 ? object.base.OperateRange : 3f;
          mInteractable.create(entityId).set(range, objectInteractor);
          object.stateFlags |= com.riiablo.engine.server.component.Object.STATE_INTERACTABLE;
        }
      }
    }
  }

  private void completeAncientsForPlayers() {
    if (playersByZone == null) return;
    IntSet parties = new IntSet();
    IntBag players = playersByZone.getEntities();
    int[] ids = players.getData();
    for (int i = 0; i < players.size(); i++) {
      int id = ids[i];
      Player player = mPlayer.get(id);
      if (player == null || player.data == null || !isAncientSummit(levelId(id))) continue;
      if (playerLevel(id, player) < Act5AncientsQuest.requiredLevel(difficulty(id))) continue;
      completeAncientsAndReward(id, player, "ancients-defeated");
      if (partyManager != null) {
        short party = partyManager.getPartyId(id);
        if (party != Party.INVALID_ID) parties.add(party);
      }
    }
    if (partyManager == null) return;
    for (int i = 0; i < players.size(); i++) {
      int id = ids[i];
      Player player = mPlayer.get(id);
      if (player == null || player.data == null || !isAct5Level(levelId(id))) continue;
      if (parties.contains(partyManager.getPartyId(id))
          && playerLevel(id, player) >= Act5AncientsQuest.requiredLevel(difficulty(id))) {
        completeAncientsAndReward(id, player, "ancients-party-sync");
      }
    }
  }

  private boolean completeAncientsAndReward(int playerId, Player player, String reason) {
    if (player == null || player.data == null) return false;
    short previous = ancientsRecord(player.data);
    if (Act5AncientsQuest.isFinished(previous)) return false;

    int oldLevel = playerLevel(playerId, player);
    int difficulty = difficulty(playerId);
    int classId = player.data.charClass & 0xFF;
    long reward = Act5AncientsQuest.rewardExperience(
        difficulty, oldLevel, classId, ExperienceTable.getInstance());
    if (reward > 0L) {
      if (experienceManager == null) {
        log.error("[A5Q5] Reward deferred: ExperienceManager unavailable player={} xp={}",
            playerId, reward);
        return false;
      }
      experienceManager.addExperienceForPlayer(player.data, oldLevel, reward);
    }

    short completed = Act5AncientsQuest.complete(previous);
    player.data.getQuests(Riiablo.ACT5)[Act5AncientsQuest.RECORD] = completed;
    if (player.data.managed && Riiablo.saves != null) D2SWriter.INSTANCE.save(player.data);
    log.info("[A5Q5] Ancients reward granted: player={} difficulty={} oldLevel={} "
            + "newLevel={} experience={} reason={} record=0x{}",
        playerId, difficulty, oldLevel, player.data.level & 0xFF, reward, reason,
        Integer.toHexString(Short.toUnsignedInt(completed)));
    return true;
  }

  private void startBaalWavesIfNeeded(int playerId) {
    Act5BaalWaveState baalWaveState = gameState().baalWaves;
    if (!baalWaveState.canStart() || factory == null || mPosition == null) return;
    Position origin = findBaalThronePosition();
    if (origin == null) origin = mPosition.has(playerId) ? mPosition.get(playerId) : null;
    if (origin == null) return;
    gameState().setBaalOrigin(origin.position.x, origin.position.y);
    baalWaveState.start();
    log.info("[A5Q6] Baal throne sequence armed: player={} clearRadius={} preDelay={}",
        playerId, Act5BaalQuest.THRONE_CLEAR_RADIUS, Act5BaalQuest.PRE_WAVE_DELAY_TICKS);
  }

  private void spawnBaalWave(int waveIndex) {
    if (factory == null || waveIndex < 0 || waveIndex >= Act5BaalQuest.WAVE_COUNT) return;
    SuperUniques.Entry unique = resolveBaalWaveSuperUnique(waveIndex);
    MonStats.Entry leaderStats = resolveBaalWaveStats(waveIndex, unique);
    if (leaderStats == null) {
      log.error("[A5Q6] Baal wave leader unresolved: wave={} superUnique={}",
          waveIndex + 1, Act5BaalQuest.WAVE_SUPER_UNIQUES[waveIndex]);
      return;
    }
    com.badlogic.gdx.math.Vector2 summonPoint = Act5BaalSpawnLayout.summonPoint(
        baalWaveOriginX(), baalWaveOriginY(), new com.badlogic.gdx.math.Vector2());
    float leaderX = summonPoint.x;
    float leaderY = summonPoint.y;
    int uniqueId = unique == null
        ? Act5BaalQuest.WAVE_SUPER_UNIQUES[waveIndex] : unique.hcIdx;
    int existingLeader = baalWaveLeaders.get(uniqueId, Engine.INVALID_ENTITY);
    if (existingLeader != Engine.INVALID_ENTITY && isLiveHostileMonster(existingLeader)) {
      gameState().baalWaves.markWaveSpawned();
      reindexCurrentBaalWave(existingLeader, waveIndex);
      log.info("[A5Q6] Baal wave spawn suppressed: wave={} superUnique={} leader={} reason=already_present",
          waveIndex + 1, uniqueId, existingLeader);
      return;
    }
    baalWaveEntities.clear();
    long affixes = unique == null ? 0L : Act5BaalQuest.nativeSuperUniqueAffixes(unique.Mod);
    Map.Zone throneZone = findZone(Act5BaalQuest.THRONE_OF_DESTRUCTION);
    com.badlogic.gdx.math.Vector2 free = new com.badlogic.gdx.math.Vector2();
    if (throneZone != null
        && throneZone.findFreeCoordinates(free.set(leaderX, leaderY), 2, 50, true, free)) {
      leaderX = free.x;
      leaderY = free.y;
    }
    int leader = factory.createMonster(leaderStats.hcIdx, leaderX, leaderY,
        MonsterRank.SUPER_UNIQUE, affixes, -1, uniqueId);
    if (leader < 0) return;
    if (mMonster.has(leader)) {
      mMonster.get(leader).setBaalWaveMember(waveIndex, uniqueId, true);
    }
    gameState().baalWaves.markWaveSpawned();
    applyBaalSpawnFacing(leader);
    baalWaveEntities.add(leader);
    if (mSuperUnique != null) {
      mSuperUnique.create(leader).set(uniqueId,
          unique == null ? "Baal Subject " + (waveIndex + 1) : unique.Superunique);
    }

    MonStats.Entry minionStats = resolveBaalWaveMinion(leaderStats);
    int min = unique == null ? Act5BaalQuest.WAVE_MINIONS[waveIndex] : unique.MinGrp;
    int max = unique == null ? Act5BaalQuest.WAVE_MINIONS[waveIndex] : unique.MaxGrp;
    int minions = Act5BaalQuest.minionCount(min, max, map == null ? 0 : map.seed(), waveIndex);
    for (int i = 0; i < minions; i++) {
      // The native preset uses the room collision placer rather than a
      // geometric ring.  Keep the native candidate order and let the map
      // choose the first walkable point for each candidate.
      float x = leaderX + Act5BaalSpawnLayout.candidateX(i);
      float y = leaderY + Act5BaalSpawnLayout.candidateY(i);
      if (throneZone != null
          && throneZone.findFreeCoordinates(free.set(x, y), 1, 12, true, free)) {
        x = free.x;
        y = free.y;
      }
      int entity = factory.createMonster(minionStats.hcIdx, x, y,
          MonsterRank.MINION, 0L, -1, leader);
      if (entity >= 0) {
        if (mMonster.has(entity)) {
          mMonster.get(entity).setBaalWaveMember(waveIndex, uniqueId, false);
        }
        applyBaalSpawnFacing(entity);
        baalWaveEntities.add(entity);
      }
    }
    String[] classHints = Act5BaalQuest.nativeWaveClientClassHints(waveIndex);
    log.info("[A5Q6] Baal wave spawned: wave={}/{} leader={} class={} minionClass={} "
            + "minions={} affixes=0x{} entities={} classHints={} postLock={}",
        waveIndex + 1, Act5BaalQuest.WAVE_COUNT,
        unique == null ? uniqueId : unique.Superunique, leaderStats.Id, minionStats.Id,
        minions, Long.toHexString(affixes), baalWaveEntities.size,
        java.util.Arrays.toString(classHints),
        Act5BaalQuest.POST_SPAWN_LOCK_TICKS);
  }

  /** Applies the neutral facing used by D2GAME_SpawnNormalMonster to both the
   * SuperUnique leader and its preset minions. */
  private void applyBaalSpawnFacing(int entityId) {
    if (mAngle == null || !mAngle.has(entityId)) return;
    mAngle.get(entityId).set(new com.badlogic.gdx.math.Vector2(
        Act5BaalSpawnLayout.FACING_X, Act5BaalSpawnLayout.FACING_Y));
  }

  /**
   * Rebuilds the transient wave index from authoritative components.  Native
   * SuperUnique ids survive a Room/ECS rebuild while Artemis entity ids do not.
   * Only the currently active wave is copied into {@code baalWaveEntities};
   * completed earlier waves therefore cannot be resurrected by reconnect.
   */
  private void rebuildBaalWaveEntityIndex() {
    baalWaveLeaders.clear();
    if (monstersByZone == null || mMonster == null || mSuperUnique == null) return;
    IntBag entities = monstersByZone.getEntities();
    int[] ids = entities.getData();
    for (int i = 0; i < entities.size(); i++) {
      int id = ids[i];
      if (levelId(id) != Act5BaalQuest.THRONE_OF_DESTRUCTION
          || !mSuperUnique.has(id) || !isLiveHostileMonster(id)) continue;
      SuperUnique unique = mSuperUnique.get(id);
      if (unique != null && isBaalWaveSuperUnique(unique.id)) {
        baalWaveLeaders.put(unique.id, id);
      }
    }

    Act5BaalWaveState waves = gameState().baalWaves;
    int activeWave = waves.activeWaveIndex();
    if (activeWave < 0 || activeWave >= Act5BaalQuest.WAVE_COUNT) return;
    int superUniqueId = Act5BaalQuest.WAVE_SUPER_UNIQUES[activeWave];
    int leader = baalWaveLeaders.get(superUniqueId, Engine.INVALID_ENTITY);
    int markedMembers = countMarkedBaalWaveMembers(activeWave, superUniqueId);
    if (leader == Engine.INVALID_ENTITY) {
      if (markedMembers > 0) {
        baalWaveMissingTicks = 0;
        log.warn("[A5Q6] Baal wave leader missing while members remain: wave={} "
                + "superUnique={} members={} action=do_not_respawn",
            activeWave + 1, superUniqueId, markedMembers);
      } else {
        if (waves.needsWaveRecovery()
            && ++baalWaveMissingTicks >= BAAL_WAVE_RECOVERY_WINDOW_TICKS) {
          log.warn("[A5Q6] Baal wave members missing beyond recovery window: wave={} "
                  + "superUnique={} ticks={} action=rebuild_preset",
              activeWave + 1, superUniqueId, baalWaveMissingTicks);
          spawnBaalWave(activeWave);
          baalWaveMissingTicks = 0;
        } else {
          log.debug("[A5Q6] Baal wave has no indexed members: wave={} superUnique={} "
                  + "reason=cleared_or_room_rebuild missingTicks={}",
              activeWave + 1, superUniqueId, baalWaveMissingTicks);
        }
      }
      return;
    }
    baalWaveMissingTicks = 0;
    reindexCurrentBaalWave(leader, activeWave);
  }

  private void reindexCurrentBaalWave(int leader, int waveIndex) {
    baalWaveEntities.clear();
    IntBag entities = monstersByZone == null ? null : monstersByZone.getEntities();
    if (entities == null) return;
    int[] ids = entities.getData();
    for (int i = 0; i < entities.size(); i++) {
      int id = ids[i];
      Monster monster = mMonster.has(id) ? mMonster.get(id) : null;
      boolean markedMember = monster != null && monster.baalWaveIndex == waveIndex
          && monster.baalWaveSuperUniqueId == Act5BaalQuest.WAVE_SUPER_UNIQUES[waveIndex];
      if (id != leader && !markedMember && (monster == null || monster.uniqueId != leader)) continue;
      if (levelId(id) == Act5BaalQuest.THRONE_OF_DESTRUCTION
          && isLiveHostileMonster(id)) baalWaveEntities.add(id);
    }
    log.debug("[A5Q6] Baal wave index rebuilt: wave={} superUnique={} leader={} members={}",
        waveIndex + 1, Act5BaalQuest.WAVE_SUPER_UNIQUES[waveIndex], leader,
        baalWaveEntities.size);
  }

  private int countMarkedBaalWaveMembers(int waveIndex, int superUniqueId) {
    if (monstersByZone == null || mMonster == null) return 0;
    int count = 0;
    IntBag entities = monstersByZone.getEntities();
    int[] ids = entities.getData();
    for (int i = 0; i < entities.size(); i++) {
      int id = ids[i];
      if (!mMonster.has(id)) continue;
      Monster monster = mMonster.get(id);
      if (monster != null && monster.baalWaveIndex == waveIndex
          && monster.baalWaveSuperUniqueId == superUniqueId
          && levelId(id) == Act5BaalQuest.THRONE_OF_DESTRUCTION
          && isLiveHostileMonster(id)) count++;
    }
    return count;
  }

  private static boolean isBaalWaveSuperUnique(int id) {
    for (int waveId : Act5BaalQuest.WAVE_SUPER_UNIQUES) {
      if (waveId == id) return true;
    }
    return false;
  }

  /** Native BaalThrone callback blocks while any live hostile monster is
   * within 64 tiles, rather than looking only at entities from the wave. */
  private boolean isBaalThroneClear() {
    if (monstersByZone == null || mPosition == null) return false;
    float radius2 = Act5BaalQuest.THRONE_CLEAR_RADIUS * Act5BaalQuest.THRONE_CLEAR_RADIUS;
    IntBag entities = monstersByZone.getEntities();
    int[] ids = entities.getData();
    for (int i = 0; i < entities.size(); i++) {
      int id = ids[i];
      if (levelId(id) != Act5BaalQuest.THRONE_OF_DESTRUCTION
          || !mPosition.has(id) || !isLiveHostileMonster(id)
          || isBaal(id) || isBaalThrone(id)) continue;
      Position position = mPosition.get(id);
      float dx = position.position.x - baalWaveOriginX();
      float dy = position.position.y - baalWaveOriginY();
      if (dx * dx + dy * dy < radius2) return false;
    }
    return true;
  }

  private Position findBaalThronePosition() {
    if (monstersByZone == null || mPosition == null) return null;
    IntBag entities = monstersByZone.getEntities();
    int[] ids = entities.getData();
    for (int i = 0; i < entities.size(); i++) {
      int id = ids[i];
      if (levelId(id) == Act5BaalQuest.THRONE_OF_DESTRUCTION
          && isBaalThrone(id) && mPosition.has(id)) return mPosition.get(id);
    }
    return null;
  }

  private boolean isBaalThrone(int entityId) {
    if (!mMonster.has(entityId)) return false;
    Monster monster = mMonster.get(entityId);
    return monster != null && monster.monstats != null
        && Act5BaalQuest.isBaalThroneMonster(monster.monstats.hcIdx, monster.monstats.Id);
  }

  private boolean isLiveHostileMonster(int entityId) {
    if (!mMonster.has(entityId)) return false;
    Monster monster = mMonster.get(entityId);
    if (monster == null || monster.monstats == null || monster.monstats.npc) return false;
    if ((mSummonedPet != null && mSummonedPet.has(entityId))
        || (mMercenary != null && mMercenary.has(entityId))) return false;
    if (mCofReference != null && mCofReference.has(entityId)) {
      byte mode = mCofReference.get(entityId).mode;
      if (mode == Engine.Monster.MODE_DT || mode == Engine.Monster.MODE_DD) return false;
    }
    if (mAttributesWrapper != null && mAttributesWrapper.has(entityId)) {
      AttributesWrapper attributes = mAttributesWrapper.get(entityId);
      if (attributes != null && attributes.attrs != null
          && attributes.attrs.aggregate().getValue(Stat.hitpoints, 0f) <= 0f) return false;
    }
    return true;
  }

  private static SuperUniques.Entry resolveBaalWaveSuperUnique(int waveIndex) {
    if (waveIndex < 0 || waveIndex >= Act5BaalQuest.WAVE_COUNT
        || Riiablo.files == null || Riiablo.files.SuperUniques == null) return null;
    int id = Act5BaalQuest.WAVE_SUPER_UNIQUES[waveIndex];
    for (SuperUniques.Entry entry : Riiablo.files.SuperUniques) {
      if (entry != null && entry.hcIdx == id) return entry;
    }
    return null;
  }

  private static MonStats.Entry resolveBaalWaveStats(
      int waveIndex, SuperUniques.Entry unique) {
    if (Riiablo.files == null || Riiablo.files.monstats == null) return null;
    if (unique != null && unique.MonClass != null && !unique.MonClass.isEmpty()) {
      MonStats.Entry stats = Riiablo.files.monstats.get(unique.MonClass);
      if (stats != null) return stats;
    }
    if (waveIndex < 0 || waveIndex >= Act5BaalQuest.WAVE_MONSTER_IDS.length) return null;
    MonStats.Entry stats = Riiablo.files.monstats.get(Act5BaalQuest.WAVE_MONSTER_IDS[waveIndex]);
    if (stats != null) return stats;
    for (MonStats.Entry entry : Riiablo.files.monstats) {
      if (entry != null && entry.Id != null
          && entry.Id.equalsIgnoreCase(Act5BaalQuest.WAVE_MONSTER_IDS[waveIndex])) return entry;
    }
    return null;
  }

  private static MonStats.Entry resolveBaalWaveMinion(MonStats.Entry leader) {
    if (leader == null || Riiablo.files == null || Riiablo.files.monstats == null) return leader;
    if (leader.minion1 != null && !leader.minion1.isEmpty()) {
      MonStats.Entry minion = Riiablo.files.monstats.get(leader.minion1);
      if (minion != null) return minion;
    }
    return leader;
  }

  private void spawnBaalAfterWaves() {
    int levelId = Act5BaalQuest.WORLDSTONE_CHAMBER;
    if (spawnedBaalLevels.contains(levelId) || factory == null || monstersByZone == null) return;
    IntBag entities = monstersByZone.getEntities();
    int[] ids = entities.getData();
    for (int i = 0; i < entities.size(); i++) {
      int id = ids[i];
      if (isBaal(id) && levelId(id) == levelId) {
        spawnedBaalLevels.add(levelId);
        return;
      }
    }
    MonStats.Entry stats = resolveBaalStats();
    if (stats == null) return;
    // D2MOO opens the Worldstone Chamber portal after the fifth wave is
    // cleared, before Baal becomes attackable. Keep the object and warp
    // creation idempotent because the final DeathEvent may be delivered more
    // than once by reconnect/replay paths.
    openWorldstoneChamberPortal();
    Map.Zone chamber = findZone(Act5BaalQuest.WORLDSTONE_CHAMBER);
    float spawnX = baalWaveOriginX() + 3f;
    float spawnY = baalWaveOriginY();
    if (chamber != null) {
      spawnX = chamber.x() + chamber.width() * 0.5f;
      spawnY = chamber.y() + chamber.height() * 0.5f;
      com.badlogic.gdx.math.Vector2 free = new com.badlogic.gdx.math.Vector2();
      if (chamber.findFreeCoordinates(free.set(spawnX, spawnY), 2, 50, true, free)) {
        spawnX = free.x;
        spawnY = free.y;
      }
    } else {
      // A headless/minimal map may not have constructed the destination zone
      // yet. Keep the old throne fallback so the boss is not silently lost;
      // normal games always use the Worldstone Chamber zone above.
      log.warn("[A5Q6] Worldstone Chamber zone unavailable; spawning Baal at Throne");
    }
    int entity = factory.createMonster(stats, spawnX, spawnY);
    if (entity >= 0) {
      spawnedBaalLevels.add(levelId);
      log.info("[A5Q6] Baal spawned after waves: entity={} level={} position=({}, {})",
          entity, levelId, spawnX, spawnY);
    }
  }

  /** Creates the A5Q6 Throne -> Worldstone Chamber portal once. */
  private void openWorldstoneChamberPortal() {
    if (factory == null || world == null) return;
    Map.Zone source = findZone(Act5BaalQuest.THRONE_OF_DESTRUCTION);
    if (source == null) {
      log.warn("[A5Q6] Worldstone Chamber portal deferred: throne zone not found");
      return;
    }
    int questWarp = QuestWarp.encode(Act5BaalQuest.WORLDSTONE_CHAMBER);
    int existingWarp = source.findWarp(questWarp);
    if (existingWarp != Engine.INVALID_ENTITY) {
      ensurePortalVisual(source, NativeQuestObjectResolver.BAAL_PORTAL, existingWarp);
      gameState().baalPortals.openWorldstoneChamber();
      return;
    }
    Position throne = findBaalThronePosition();
    float portalX = throne == null ? baalWaveOriginX() : throne.position.x;
    float portalY = throne == null ? baalWaveOriginY() : throne.position.y;
    if (throne == null && portalX == 0f && portalY == 0f) {
      portalX = source.x() + source.width() * 0.5f;
      portalY = source.y() + source.height() * 0.5f;
    }
    com.badlogic.gdx.math.Vector2 free = new com.badlogic.gdx.math.Vector2();
    if (source.findFreeCoordinates(free.set(portalX, portalY), 1, 32, true, free)) {
      portalX = free.x;
      portalY = free.y;
    }
    int visual = factory.createStaticObjectByClassId(
        NativeQuestObjectResolver.BAAL_PORTAL, portalX, portalY);
    int warp = factory.createQuestWarp(Act5BaalQuest.WORLDSTONE_CHAMBER, portalX, portalY);
    if (warp == Engine.INVALID_ENTITY) {
      if (visual != Engine.INVALID_ENTITY) world.delete(visual);
      log.error("[A5Q6] Worldstone Chamber portal creation failed: visual={} position=({}, {})",
          visual, portalX, portalY);
      return;
    }
    gameState().baalPortals.openWorldstoneChamber();
    source.addWarp(warp);
    log.info("[A5Q6] Worldstone Chamber portal opened: visual={} warp={} destination={} "
        + "position=({}, {})", visual, warp, Act5BaalQuest.WORLDSTONE_CHAMBER,
        portalX, portalY);
  }

  /**
   * Spawns the native MONSTER_TYRAEL3 after Baal dies.  D2MOO derives the
   * coordinates from the Baal-death missile (x-5,y-5) and searches a free
   * position; using the DeathEvent position preserves that behavior without
   * coupling quest state to a rendered missile entity.
   */
  private void spawnA5Q6Tyrael(int baalEntity) {
    if (factory == null || world == null || mMapWrapper == null || !mMapWrapper.has(baalEntity)) {
      return;
    }
    MapWrapper wrapper = mMapWrapper.get(baalEntity);
    if (wrapper == null || wrapper.zone == null || wrapper.zone.level == null
        || wrapper.zone.level.Id != Act5BaalQuest.WORLDSTONE_CHAMBER) {
      log.warn("[A5Q6] Tyrael3 spawn deferred: Baal is not in Worldstone Chamber entity={}",
          baalEntity);
      return;
    }
    int existing = findTyraelInChamber();
    if (existing != Engine.INVALID_ENTITY) {
      Position position = mPosition.has(existing) ? mPosition.get(existing) : null;
      if (position != null) gameState().markTyraelSpawned(
          position.position.x, position.position.y);
      return;
    }
    Position baalPosition = mPosition.has(baalEntity) ? mPosition.get(baalEntity) : null;
    float x = baalPosition == null ? wrapper.zone.x() : baalPosition.position.x;
    float y = baalPosition == null ? wrapper.zone.y() : baalPosition.position.y;
    spawnA5Q6TyraelAt(wrapper.zone, x - 5f, y - 5f);
  }

  private void spawnA5Q6TyraelAt(Map.Zone chamber, float x, float y) {
    if (chamber == null || factory == null) return;
    MonStats.Entry stats = resolveTyraelStats();
    if (stats == null) {
      log.warn("[A5Q6] Tyrael3 spawn deferred: MonStats row not found (hcIdx={})",
          Act5BaalQuest.TYRAEL3_CLASS);
      return;
    }
    com.badlogic.gdx.math.Vector2 free = new com.badlogic.gdx.math.Vector2();
    if (!chamber.findFreeCoordinates(free.set(x, y), 2, 32, true, free)) {
      log.warn("[A5Q6] Tyrael3 spawn deferred: no free Chamber position near=({}, {})", x, y);
      return;
    }
    x = free.x;
    y = free.y;
    int entity = factory.createMonster(stats, x, y);
    if (entity == Engine.INVALID_ENTITY) {
      log.warn("[A5Q6] Tyrael3 spawn failed: position=({}, {})", x, y);
      return;
    }
    gameState().markTyraelSpawned(x, y);
    log.info("[A5Q6] Tyrael3 spawned: entity={} position=({}, {})", entity, x, y);
  }

  private int findTyraelInChamber() {
    if (monstersByZone == null || mMonster == null || mMapWrapper == null) {
      return Engine.INVALID_ENTITY;
    }
    IntBag monsters = monstersByZone.getEntities();
    int[] ids = monsters.getData();
    for (int i = 0; i < monsters.size(); i++) {
      int id = ids[i];
      if (!mMonster.has(id) || !mMapWrapper.has(id)) continue;
      Monster monster = mMonster.get(id);
      MapWrapper wrapper = mMapWrapper.get(id);
      if (monster != null && monster.monstats != null && wrapper != null
          && wrapper.zone != null && wrapper.zone.level != null
          && wrapper.zone.level.Id == Act5BaalQuest.WORLDSTONE_CHAMBER
          && Act5BaalQuest.isTyrael3(monster.monstats.hcIdx, monster.monstats.Id)) {
        return id;
      }
    }
    return Engine.INVALID_ENTITY;
  }

  private static MonStats.Entry resolveTyraelStats() {
    if (Riiablo.files == null || Riiablo.files.monstats == null) return null;
    MonStats.Entry stats = Riiablo.files.monstats.get("Tyrael3");
    if (stats != null) return stats;
    stats = Riiablo.files.monstats.get("Tyrael");
    if (stats != null && Act5BaalQuest.isTyrael3(stats.hcIdx, stats.Id)) return stats;
    for (MonStats.Entry entry : Riiablo.files.monstats) {
      if (entry != null && Act5BaalQuest.isTyrael3(entry.hcIdx, entry.Id)) return entry;
    }
    return null;
  }

  /** Handles the terminal 20175 message (the Java equivalent of
   * D2MOO's NPCDEACTIVATE callback). */
  private void onBaalTyraelMessage(NpcQuestMessageEvent event, Player player, Monster npc) {
    if (event.messageIndex != Act5BaalQuest.MESSAGE_TYRAEL || player == null
        || player.data == null || levelId(event.entityId) != Act5BaalQuest.WORLDSTONE_CHAMBER
        || levelId(event.npcId) != Act5BaalQuest.WORLDSTONE_CHAMBER) return;
    short previous = baalRecord(player.data);
    if (!Act5BaalQuest.canTriggerLastPortal(previous,
        levelId(event.npcId), event.messageIndex)) {
      log.debug("[A5Q6] Tyrael3 message rejected: player={} npc={} record=0x{} level={}",
          event.entityId, event.npcId, Integer.toHexString(Short.toUnsignedInt(previous)),
          levelId(event.npcId));
      return;
    }
    MapWrapper wrapper = mMapWrapper.has(event.npcId) ? mMapWrapper.get(event.npcId) : null;
    Position position = mPosition.has(event.npcId) ? mPosition.get(event.npcId) : null;
    if (wrapper == null || wrapper.zone == null || position == null) return;
    if (!ensureLastPortal(wrapper.zone, position.position.x + 5f, position.position.y)) return;
    // ScrollMessage 20175 sets CUSTOM3 in the native implementation.  This
    // prevents the same player from reopening the terminal dialogue while
    // leaving the portal globally available to the party.
    short next = NativeQuestRecord.set(previous, NativeQuestRecord.CUSTOM3);
    updateBaalRecord(player.data, ignored -> next, "tyrael3-terminal-message");
    log.info("[A5Q6] Tyrael3 terminal message accepted: player={} npc={} portal=ready",
        event.entityId, event.npcId);
  }

  /** Restores Tyrael3 after a room/ECS rebuild while preserving the game-level
   * spawn position. */
  private void rebuildBaalTyraelState() {
    if (factory == null || world == null || !gameState().isTyraelSpawned()) return;
    int existing = findTyraelInChamber();
    if (existing != Engine.INVALID_ENTITY) return;
    Map.Zone chamber = findZone(Act5BaalQuest.WORLDSTONE_CHAMBER);
    if (chamber == null) return;
    spawnA5Q6TyraelAt(chamber, gameState().tyraelOriginX(), gameState().tyraelOriginY());
  }

  /** Rebuilds the end portal after Chamber entities are recreated. */
  private void rebuildBaalEndPortalState() {
    if (playersByZone == null || factory == null || world == null
        || !gameState().baalPortals.isLastPortalCreated()) return;
    IntBag players = playersByZone.getEntities();
    int[] ids = players.getData();
    for (int i = 0; i < players.size(); i++) {
      int id = ids[i];
      if (!mPlayer.has(id) || !mMapWrapper.has(id)) continue;
      Player player = mPlayer.get(id);
      MapWrapper wrapper = mMapWrapper.get(id);
      if (player == null || player.data == null || wrapper == null || wrapper.zone == null
          || wrapper.zone.level == null
          || wrapper.zone.level.Id != Act5BaalQuest.WORLDSTONE_CHAMBER
          || !Act5BaalQuest.canUseLastPortal(
              baalRecord(player.data), Act5BaalQuest.WORLDSTONE_CHAMBER)) continue;
      int tyrael = findTyraelInChamber();
      Position tyraelPosition = tyrael == Engine.INVALID_ENTITY || !mPosition.has(tyrael)
          ? null : mPosition.get(tyrael);
      float x = tyraelPosition == null ? gameState().tyraelOriginX() + 5f
          : tyraelPosition.position.x + 5f;
      float y = tyraelPosition == null ? gameState().tyraelOriginY()
          : tyraelPosition.position.y;
      ensureLastPortal(wrapper.zone, x, y);
      return;
    }
  }

  private boolean ensureLastPortal(Map.Zone zone, float portalX, float portalY) {
    if (zone == null || factory == null || world == null) return false;
    int questWarp = QuestWarp.encode(D2LevelIds.LEVEL_HARROGATH);
    int existingWarp = zone.findWarp(questWarp);
    if (existingWarp != Engine.INVALID_ENTITY) {
      ensurePortalVisual(zone, NativeQuestObjectResolver.LAST_PORTAL, existingWarp);
      gameState().baalPortals.createLastPortal();
      return true;
    }
    com.badlogic.gdx.math.Vector2 free = new com.badlogic.gdx.math.Vector2();
    if (zone.findFreeCoordinates(free.set(portalX, portalY), 1, 48, true, free)) {
      portalX = free.x;
      portalY = free.y;
    }
    int visual = factory.createStaticObjectByClassId(
        NativeQuestObjectResolver.LAST_PORTAL, portalX, portalY);
    int warp = factory.createQuestWarp(D2LevelIds.LEVEL_HARROGATH, portalX, portalY);
    if (warp == Engine.INVALID_ENTITY) {
      if (visual != Engine.INVALID_ENTITY) world.delete(visual);
      log.error("[A5Q6] Last portal creation failed: visual={} position=({}, {})",
          visual, portalX, portalY);
      return false;
    }
    gameState().baalPortals.createLastPortal();
    zone.addWarp(warp);
    log.info("[A5Q6] Last portal created: visual={} warp={} destination={} position=({}, {})",
        visual, warp, D2LevelIds.LEVEL_HARROGATH, portalX, portalY);
    return true;
  }

  /** Restores a portal's visual object when the authoritative Warp survived a
   * RoomEx/ECS rebuild but the transient static object did not. */
  private void ensurePortalVisual(Map.Zone zone, int objectClassId, int warpEntity) {
    if (zone == null || factory == null || objectsByZone == null
        || mPosition == null || !mPosition.has(warpEntity)) return;
    IntBag objects = objectsByZone.getEntities();
    int[] ids = objects.getData();
    for (int i = 0; i < objects.size(); i++) {
      int id = ids[i];
      if (!mObject.has(id) || !mMapWrapper.has(id)) continue;
      com.riiablo.engine.server.component.Object object = mObject.get(id);
      MapWrapper wrapper = mMapWrapper.get(id);
      if (wrapper != null && wrapper.zone == zone && object != null && object.base != null
          && object.base.Id == objectClassId) return;
    }
    Position position = mPosition.get(warpEntity);
    int visual = factory.createStaticObjectByClassId(
        objectClassId, position.position.x, position.position.y);
    log.info("[A5Q6] Restored portal visual: visual={} warp={} object={} position=({}, {})",
        visual, warpEntity, objectClassId, position.position.x, position.position.y);
  }

  private Map.Zone findZone(int levelId) {
    if (map != null) {
      Levels.Entry level = Riiablo.files == null || Riiablo.files.Levels == null
          ? null : Riiablo.files.Levels.get(levelId);
      Map.Zone zone = level == null ? null : map.findZone(level);
      if (zone != null) return zone;
    }
    if (mMapWrapper == null || playersByZone == null) return null;
    IntBag players = playersByZone.getEntities();
    int[] ids = players.getData();
    for (int i = 0; i < players.size(); i++) {
      MapWrapper wrapper = mMapWrapper.get(ids[i]);
      if (wrapper != null && wrapper.zone != null && wrapper.zone.level != null
          && wrapper.zone.level.Id == levelId) return wrapper.zone;
    }
    return null;
  }

  private boolean isBaal(int entityId) {
    if (!mMonster.has(entityId)) return false;
    Monster monster = mMonster.get(entityId);
    return isBaal(monster == null ? null : monster.monstats);
  }

  private boolean isBaal(MonStats.Entry stats) {
    if (stats == null) return false;
    return Act5BaalQuest.isBaalMonster(stats.hcIdx, stats.Id);
  }

  private static MonStats.Entry resolveBaalStats() {
    if (Riiablo.files == null || Riiablo.files.monstats == null) return null;
    MonStats.Entry stats = Riiablo.files.monstats.get("BaalCrab");
    if (stats != null) return stats;
    stats = Riiablo.files.monstats.get("Baal");
    if (stats != null) return stats;
    for (MonStats.Entry entry : Riiablo.files.monstats) {
      if (entry != null && Act5BaalQuest.isBaalMonster(entry.hcIdx, entry.Id)) return entry;
    }
    return null;
  }

  private void completeBaalForPlayers() {
    if (playersByZone == null) return;
    IntSet parties = new IntSet();
    IntBag players = playersByZone.getEntities();
    int[] ids = players.getData();
    for (int i = 0; i < players.size(); i++) {
      int id = ids[i];
      Player player = mPlayer.get(id);
      if (player == null || player.data == null
          || !Act5BaalQuest.canReceiveDirectReward(
              player.data.isExpansion(), levelId(id))) continue;
      completeBaalAndProgression(id, player, "baal-defeated");
      if (partyManager != null && NativeQuestRecord.has(
          baalRecord(player.data), NativeQuestRecord.PRIMARY_GOAL_DONE)) {
        short party = partyManager.getPartyId(id);
        if (party != Party.INVALID_ID) parties.add(party);
      }
    }
    if (partyManager != null) {
      for (int i = 0; i < players.size(); i++) {
        int id = ids[i];
        Player player = mPlayer.get(id);
        if (player == null || player.data == null
            || !Act5BaalQuest.canReceivePartyReward(
                player.data.isExpansion(), levelId(id))) continue;
        if (parties.contains(partyManager.getPartyId(id))) {
          completeBaalAndProgression(id, player, "baal-party-sync");
        }
      }
    }
    // Native SetCompletionFlag runs for every player after direct and party
    // rewards, but never replaces REWARD_GRANTED on qualifying players.
    for (int i = 0; i < players.size(); i++) {
      int id = ids[i];
      Player player = mPlayer.get(id);
      if (player == null || player.data == null) continue;
      updateBaalRecord(player.data, Act5BaalQuest::completeObserver,
          "baal-completed-observer");
    }
  }

  private boolean completeBaalAndProgression(int playerId, Player player, String reason) {
    if (player == null || player.data == null) return false;
    short previous = baalRecord(player.data);
    if (Act5BaalQuest.isFinished(previous)) return false;
    short completed = Act5BaalQuest.complete(previous);
    if (completed == previous) return false;
    int previousProgression = NativeCharacterProgression.value(player.data.flags);
    player.data.flags = NativeCharacterProgression.update(
        player.data.flags, 5, difficulty(playerId), player.data.isExpansion());
    player.data.getQuests(Riiablo.ACT5)[Act5BaalQuest.RECORD] = completed;
    if (player.data.managed && Riiablo.saves != null) D2SWriter.INSTANCE.save(player.data);
    log.info("[A5Q6] Baal reward granted: player={} difficulty={} progression={}->{} "
            + "reason={} record=0x{}",
        playerId, difficulty(playerId), previousProgression,
        NativeCharacterProgression.value(player.data.flags), reason,
        Integer.toHexString(Short.toUnsignedInt(completed)));
    return true;
  }

  private MonStats.Entry resolveAncientStats(int superUniqueId, int ordinal) {
    SuperUniques.Entry unique = resolveAncientSuperUnique(superUniqueId);
    if (unique != null && unique.MonClass != null && Riiablo.files != null
        && Riiablo.files.monstats != null) {
      MonStats.Entry stats = Riiablo.files.monstats.get(unique.MonClass);
      if (stats != null) return stats;
    }
    if (Riiablo.files == null || Riiablo.files.monstats == null) return null;
    String[] names = {"AncientBarb" + ordinal, "Ancient" + ordinal};
    for (String name : names) {
      MonStats.Entry stats = Riiablo.files.monstats.get(name);
      if (stats != null) return stats;
    }
    for (MonStats.Entry entry : Riiablo.files.monstats) {
      if (entry != null && containsName(entry.Id, "ancientbarb" + ordinal)) return entry;
    }
    return null;
  }

  private static SuperUniques.Entry resolveAncientSuperUnique(int id) {
    if (Riiablo.files == null || Riiablo.files.SuperUniques == null) return null;
    for (SuperUniques.Entry entry : Riiablo.files.SuperUniques) {
      if (entry != null && entry.hcIdx == id) return entry;
    }
    return null;
  }

  private boolean isNihlathak(int entityId) {
    if (!mMonster.has(entityId)) return false;
    if (mSuperUnique != null && mSuperUnique.has(entityId)) {
      SuperUnique unique = mSuperUnique.get(entityId);
      if (unique != null && unique.id == Act5NihlathakQuest.SUPERUNIQUE_NIHLATHAK_BOSS) return true;
    }
    Monster monster = mMonster.get(entityId);
    return isNihlathak(monster == null ? null : monster.monstats);
  }

  private boolean isNihlathak(MonStats.Entry stats) {
    if (stats == null) return false;
    return containsName(stats.Id, "nihlathak") || containsName(stats.NameStr, "nihlathak");
  }

  private static boolean containsName(String value, String needle) {
    return value != null && value.toLowerCase().contains(needle);
  }

  private static MonStats.Entry resolveNihlathakStats() {
    if (Riiablo.files == null || Riiablo.files.monstats == null) return null;
    SuperUniques.Entry unique = resolveNihlathakSuperUnique();
    if (unique != null && unique.MonClass != null) {
      MonStats.Entry stats = Riiablo.files.monstats.get(unique.MonClass);
      if (stats != null) return stats;
    }
    MonStats.Entry stats = Riiablo.files.monstats.get("Nihlathak");
    if (stats != null) return stats;
    for (MonStats.Entry entry : Riiablo.files.monstats) {
      if (entry != null && (containsName(entry.Id, "nihlathak")
          || containsName(entry.NameStr, "nihlathak"))) return entry;
    }
    return null;
  }

  private static SuperUniques.Entry resolveNihlathakSuperUnique() {
    if (Riiablo.files == null || Riiablo.files.SuperUniques == null) return null;
    SuperUniques.Entry indexed = Riiablo.files.SuperUniques.get("Nihlathak");
    if (indexed != null) return indexed;
    for (SuperUniques.Entry entry : Riiablo.files.SuperUniques) {
      if (entry != null && (entry.hcIdx == Act5NihlathakQuest.SUPERUNIQUE_NIHLATHAK_BOSS
          || containsName(entry.Superunique, "nihlathak"))) return entry;
    }
    return null;
  }

  private void completeNihlathakForPlayers() {
    if (playersByZone == null) return;
    IntSet parties = new IntSet();
    IntBag players = playersByZone.getEntities();
    int[] ids = players.getData();
    for (int i = 0; i < players.size(); i++) {
      int id = ids[i];
      Player player = mPlayer.get(id);
      if (player == null || player.data == null || !isAct5Level(levelId(id))) continue;
      if (levelId(id) == Act5NihlathakQuest.HALLS_OF_VAUGHT) {
        updateNihlathakRecord(player.data, Act5NihlathakQuest::complete,
            "nihlathak-defeated");
        if (partyManager != null) {
          short party = partyManager.getPartyId(id);
          if (party != Party.INVALID_ID) parties.add(party);
        }
      }
    }
    if (partyManager == null) return;
    for (int i = 0; i < players.size(); i++) {
      int id = ids[i];
      Player player = mPlayer.get(id);
      if (player == null || player.data == null || !isAct5Level(levelId(id))) continue;
      if (parties.contains(partyManager.getPartyId(id))) {
        updateNihlathakRecord(player.data, Act5NihlathakQuest::complete,
            "nihlathak-party-sync");
      }
    }
  }

  private boolean isShenk(int entityId) {
    if (mSuperUnique != null && mSuperUnique.has(entityId)) {
      SuperUnique unique = mSuperUnique.get(entityId);
      if (unique != null && unique.id == Act5ShenkQuest.SUPERUNIQUE_SIEGE_BOSS) return true;
    }
    return false;
  }

  private static MonStats.Entry resolveShenkStats() {
    if (Riiablo.files == null || Riiablo.files.monstats == null) return null;
    SuperUniques.Entry unique = resolveShenkSuperUnique();
    if (unique != null) {
      MonStats.Entry stats = Riiablo.files.monstats.get(unique.MonClass);
      if (stats != null) return stats;
    }
    return Riiablo.files.monstats.get("DeathMauler1");
  }

  private static SuperUniques.Entry resolveShenkSuperUnique() {
    if (Riiablo.files == null || Riiablo.files.SuperUniques == null) return null;
    SuperUniques.Entry indexed = Riiablo.files.SuperUniques.get("Siege Boss");
    if (indexed != null) return indexed;
    for (SuperUniques.Entry entry : Riiablo.files.SuperUniques) {
      if (entry != null && entry.hcIdx == D2SuperUniques.SUPERUNIQUE_SIEGE_BOSS) return entry;
    }
    return null;
  }

  private boolean isLarzuk(MonStats.Entry stats) {
    if (stats == null) return false;
    if ("Larzuk".equalsIgnoreCase(stats.Id)) return true;
    return stats.NameStr != null && stats.NameStr.toLowerCase().contains("larzuk");
  }

  private boolean isQualKehk(MonStats.Entry stats) {
    if (stats == null) return false;
    if ("Qual-Kehk".equalsIgnoreCase(stats.Id)
        || "Qual-Kehk".equalsIgnoreCase(stats.NameStr)) return true;
    return stats.NameStr != null && stats.NameStr.toLowerCase().contains("qual");
  }

  private boolean isMalah(MonStats.Entry stats) {
    if (stats == null) return false;
    if ("Malah".equalsIgnoreCase(stats.Id) || "Malah".equalsIgnoreCase(stats.NameStr)) return true;
    return stats.NameStr != null && stats.NameStr.toLowerCase().contains("malah");
  }

  private boolean isDrehya(MonStats.Entry stats) {
    if (stats == null) return false;
    return "Drehya".equalsIgnoreCase(stats.Id) || "Drehya".equalsIgnoreCase(stats.NameStr)
        || containsName(stats.NameStr, "drehya");
  }

  private boolean isAncientSummit(int levelId) {
    return levelId == Act5AncientsQuest.ROCKY_SUMMIT
        || levelId == Act5AncientsQuest.ARREAT_SUMMIT;
  }

  private boolean isBaalArea(int levelId) {
    return levelId == Act5BaalQuest.WORLDSTONE_KEEP_1
        || levelId == Act5BaalQuest.THRONE_OF_DESTRUCTION
        || levelId == Act5BaalQuest.WORLDSTONE_CHAMBER;
  }

  private short record(CharData data) {
    return data.getQuests(Riiablo.ACT5)[Act5ShenkQuest.RECORD];
  }

  private short rescueRecord(CharData data) {
    return data.getQuests(Riiablo.ACT5)[Act5RescueQuest.RECORD];
  }

  private short prisonRecord(CharData data) {
    return data.getQuests(Riiablo.ACT5)[Act5PrisonQuest.RECORD];
  }

  private short nihlathakRecord(CharData data) {
    return data.getQuests(Riiablo.ACT5)[Act5NihlathakQuest.RECORD];
  }

  private short ancientsRecord(CharData data) {
    return data.getQuests(Riiablo.ACT5)[Act5AncientsQuest.RECORD];
  }

  private short baalRecord(CharData data) {
    return data.getQuests(Riiablo.ACT5)[Act5BaalQuest.RECORD];
  }

  private boolean hasPrisonPrerequisite(CharData data) {
    short record = prisonRecord(data);
    return Act5PrisonQuest.isFinished(record)
        || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING);
  }

  private boolean hasNihlathakPrerequisite(CharData data) {
    short record = nihlathakRecord(data);
    return Act5NihlathakQuest.isFinished(record)
        || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING);
  }

  private boolean hasAncientsPrerequisite(CharData data) {
    short record = ancientsRecord(data);
    return Act5AncientsQuest.isFinished(record)
        || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING);
  }

  private int playerLevel(int playerId, Player player) {
    Attributes attrs = mAttributesWrapper != null && mAttributesWrapper.has(playerId)
        ? mAttributesWrapper.get(playerId).attrs : player.data.getStats();
    if (attrs == null) return player.data.level & 0xFF;
    StatRef level = attrs.get(Stat.level, StatRef.obtain());
    return level == null ? (player.data.level & 0xFF) : level.asInt();
  }

  private int difficulty(int entityId) {
    if (!mMapWrapper.has(entityId)) return 0;
    MapWrapper wrapper = mMapWrapper.get(entityId);
    return wrapper == null || wrapper.map == null ? 0 : wrapper.map.getDifficulty();
  }

  private void complete(CharData data) {
    updateRecord(data, Act5ShenkQuest::complete, "shenk-defeated");
  }

  private void updateRecord(CharData data, java.util.function.UnaryOperator<Short> transition,
      String reason) {
    short previous = record(data);
    short next = transition.apply(previous);
    if (previous == next) return;
    data.getQuests(Riiablo.ACT5)[Act5ShenkQuest.RECORD] = next;
    if (data.managed && Riiablo.saves != null) D2SWriter.INSTANCE.save(data);
    log.info("[A5Q1] Quest record changed: character={} reason={} previous=0x{} next=0x{}",
        data.name, reason, Integer.toHexString(Short.toUnsignedInt(previous)),
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  private void updateRescueRecord(CharData data,
      java.util.function.UnaryOperator<Short> transition, String reason) {
    short previous = rescueRecord(data);
    short next = transition.apply(previous);
    if (previous == next) return;
    data.getQuests(Riiablo.ACT5)[Act5RescueQuest.RECORD] = next;
    if (data.managed && Riiablo.saves != null) D2SWriter.INSTANCE.save(data);
    log.info("[A5Q2] Quest record changed: character={} reason={} previous=0x{} next=0x{}",
        data.name, reason, Integer.toHexString(Short.toUnsignedInt(previous)),
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  private void updatePrisonRecord(CharData data,
      java.util.function.UnaryOperator<Short> transition, String reason) {
    short previous = prisonRecord(data);
    short next = transition.apply(previous);
    if (previous == next) return;
    data.getQuests(Riiablo.ACT5)[Act5PrisonQuest.RECORD] = next;
    if (data.managed && Riiablo.saves != null) D2SWriter.INSTANCE.save(data);
    log.info("[A5Q3] Quest record changed: character={} reason={} previous=0x{} next=0x{}",
        data.name, reason, Integer.toHexString(Short.toUnsignedInt(previous)),
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  private void updateNihlathakRecord(CharData data,
      java.util.function.UnaryOperator<Short> transition, String reason) {
    short previous = nihlathakRecord(data);
    short next = transition.apply(previous);
    if (previous == next) return;
    data.getQuests(Riiablo.ACT5)[Act5NihlathakQuest.RECORD] = next;
    if (data.managed && Riiablo.saves != null) D2SWriter.INSTANCE.save(data);
    log.info("[A5Q4] Quest record changed: character={} reason={} previous=0x{} next=0x{}",
        data.name, reason, Integer.toHexString(Short.toUnsignedInt(previous)),
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  private void updateAncientsRecord(CharData data,
      java.util.function.UnaryOperator<Short> transition, String reason) {
    short previous = ancientsRecord(data);
    short next = transition.apply(previous);
    if (previous == next) return;
    data.getQuests(Riiablo.ACT5)[Act5AncientsQuest.RECORD] = next;
    if (data.managed && Riiablo.saves != null) D2SWriter.INSTANCE.save(data);
    log.info("[A5Q5] Quest record changed: character={} reason={} previous=0x{} next=0x{}",
        data.name, reason, Integer.toHexString(Short.toUnsignedInt(previous)),
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  private void updateBaalRecord(CharData data,
      java.util.function.UnaryOperator<Short> transition, String reason) {
    short previous = baalRecord(data);
    short next = transition.apply(previous);
    if (previous == next) return;
    data.getQuests(Riiablo.ACT5)[Act5BaalQuest.RECORD] = next;
    if (data.managed && Riiablo.saves != null) D2SWriter.INSTANCE.save(data);
    log.info("[A5Q6] Quest record changed: character={} reason={} previous=0x{} next=0x{}",
        data.name, reason, Integer.toHexString(Short.toUnsignedInt(previous)),
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  private int levelId(int entityId) {
    if (!mMapWrapper.has(entityId)) return -1;
    MapWrapper wrapper = mMapWrapper.get(entityId);
    return wrapper == null || wrapper.zone == null || wrapper.zone.level == null
        ? -1 : wrapper.zone.level.Id;
  }

  private Act5QuestGameState gameState() {
    return act5QuestGameState != null ? act5QuestGameState : fallbackGameState;
  }

  private float baalWaveOriginX() {
    return gameState().hasBaalOrigin() ? gameState().baalOriginX() : 0f;
  }

  private float baalWaveOriginY() {
    return gameState().hasBaalOrigin() ? gameState().baalOriginY() : 0f;
  }

  /** Exposes a primitive-only room snapshot for D2GS reconnect/migration. */
  public Act5QuestGameState.Snapshot snapshotGameState() {
    return gameState().snapshot();
  }

  /** Restores the room snapshot before the next authoritative quest tick. */
  public void restoreGameState(Act5QuestGameState.Snapshot snapshot) {
    gameState().restore(snapshot);
  }

  private static boolean isAct5Level(int levelId) {
    return levelId >= D2LevelIds.LEVEL_HARROGATH
        && levelId <= D2LevelIds.LEVEL_WORLDSTONECHAMBER;
  }
}
