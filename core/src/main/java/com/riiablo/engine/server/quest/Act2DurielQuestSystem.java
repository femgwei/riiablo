package com.riiablo.engine.server.quest;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntSet;
import com.d2moo.common.drlg.D2LevelIds;
import com.riiablo.Riiablo;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.NativeObjectState;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.event.NpcQuestMessageEvent;
import com.riiablo.engine.server.event.ObjectInteractionEvent;
import com.riiablo.engine.server.event.ZoneChangeEvent;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.engine.server.object.NativeQuestObjectResolver;
import com.riiablo.engine.server.object.NativeObjectOperateTable.Lifecycle;
import com.riiablo.engine.server.party.Party;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.save.ItemData;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import com.riiablo.save.D2SWriter;
import net.mostlyoriginal.api.event.common.Subscribe;

/** A2Q6 bridge for inserting the assembled staff and opening Duriel's portal. */
@Wire(failOnNull = false)
public class Act2DurielQuestSystem extends BaseSystem {
  private static final Logger log = LogManager.getLogger(Act2DurielQuestSystem.class);
  private static final int ORIFICE_OBJECT_ID = 152;
  public static final int RECORD = Act2DurielQuest.RECORD;
  private static final String REMOVED_TAINTED_SUN_STAFF = "tsh";
  private static final String REMOVED_FALSE_STAFF = "fsm";

  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<com.riiablo.engine.server.component.Object> mObject;
  protected ComponentMapper<NativeObjectState> mNativeObjectState;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Position> mPosition;
  @Wire(name = "factory", failOnNull = false)
  protected EntityFactory factory;
  @Wire(name = "partyManager", failOnNull = false)
  protected PartyManager partyManager;
  protected CofManager cofs;

  private EntitySubscription playersByZone;
  private EntitySubscription objectsByZone;
  private EntitySubscription monstersByZone;
  private final IntSet killedDuriels = new IntSet();
  private boolean lutGholeinPortalOpened;

  @Override
  protected void initialize() {
    playersByZone = world.getAspectSubscriptionManager().get(
        Aspect.all(Player.class, MapWrapper.class));
    objectsByZone = world.getAspectSubscriptionManager().get(
        Aspect.all(com.riiablo.engine.server.component.Object.class, MapWrapper.class));
    monstersByZone = world.getAspectSubscriptionManager().get(
        Aspect.all(Monster.class, MapWrapper.class, Position.class));
  }

  @Override
  protected void processSystem() {
    // Quest records are persisted, whereas the door/portal entities are not.
    // Reconcile them every simulation step so reconnects and rebuilt zones do
    // not depend on the original Duriel death/dialogue event being replayed.
    reconcileDurielLairState();
  }

  @Subscribe
  public void onZoneChanged(ZoneChangeEvent event) {
    if (event == null || event.zone == null || event.zone.level == null
        || !mPlayer.has(event.entityId)
        || event.zone.level.Id != D2LevelIds.LEVEL_DURIELSLAIR) return;
    Player player = mPlayer.get(event.entityId);
    if (player == null || player.data == null) return;
    reconcileDurielLairState(event.entityId, event.zone, player.data);
  }

  @Subscribe
  public void onObjectInteraction(ObjectInteractionEvent event) {
    if (event == null || !event.firstActivation()
        || event.lifecycle != Lifecycle.STAFF_ORIFICE
        || event.objectClassId != ORIFICE_OBJECT_ID) return;
    Player player = mPlayer.get(event.playerId);
    MapWrapper wrapper = mMapWrapper.get(event.entityId);
    Position source = mPosition.get(event.entityId);
    if (player == null || player.data == null || wrapper == null || wrapper.map == null
        || wrapper.zone == null
        || wrapper.zone.level == null || source == null
        || !isAct2(wrapper.zone.level.Id)
        || !Act2TombSelection.forGameSeed(wrapper.map.seed())
            .isStaffTomb(wrapper.zone.level.Id)) {
      log.warn("[A2Q6] Staff orifice activation rejected by level/world validation: entity={} player={}",
          event.entityId, event.playerId);
      return;
    }

    ItemData items = player.data.getItems();
    if (items == null || !items.containsItemCode(Act2HoradricStaffQuest.HORADRIC_STAFF)) {
      log.warn("[A2Q6] Staff orifice activation had no authoritative hst: player={}",
          event.playerId);
      return;
    }
    float portalX = source.position.x - 13f;
    float portalY = source.position.y + 3f;
    if (wrapper.map != null && wrapper.map.flags((int) portalX, (int) portalY) != 0) {
      portalX = source.position.x;
      portalY = source.position.y + 2f;
    }
    int visual = factory == null ? Engine.INVALID_ENTITY
        : factory.createStaticObjectByClassId(60, portalX, portalY);
    int warp = factory == null ? Engine.INVALID_ENTITY
        : factory.createQuestWarp(D2LevelIds.LEVEL_DURIELSLAIR, portalX, portalY);
    if (warp == Engine.INVALID_ENTITY) {
      if (visual != Engine.INVALID_ENTITY && world != null) world.delete(visual);
      log.error("[A2Q6] failed to create Duriel quest portal: orifice={} player={}",
          event.entityId, event.playerId);
      return;
    }

    // Portal creation is the last fallible world operation.  Only after it
    // succeeds do we consume the quest materials and persist the records;
    // a missing destination/map can therefore never eat the player's staff.
    if (!items.removeItemCode(Act2HoradricStaffQuest.HORADRIC_STAFF)) {
      if (visual != Engine.INVALID_ENTITY && world != null) world.delete(visual);
      if (world != null) world.delete(warp);
      log.error("[A2Q6] staff disappeared during orifice transaction: player={}",
          event.playerId);
      return;
    }
    // D2MOO also removes old quest aliases when a player inserts the staff.
    items.removeItemCode(REMOVED_TAINTED_SUN_STAFF);
    items.removeItemCode(Act2HoradricStaffQuest.VIPER_AMULET);
    items.removeItemCode(REMOVED_FALSE_STAFF);
    markRecords(player.data);
    wrapper.zone.addWarp(warp);
    log.info("[A2Q6] Tal Rasha staff inserted: player={} orifice={} visual={} warp={} destination={}",
        event.playerId, event.entityId, visual, warp, D2LevelIds.LEVEL_DURIELSLAIR);
  }

  @Subscribe
  public void onMonsterKilled(DeathEvent event) {
    if (event == null || event.victim < 0 || !isDuriel(event.victim)
        || !killedDuriels.add(event.victim)) return;
    markDurielKilledForPlayers();
    openTyraelsDoor();
    log.info("[A2Q6] Duriel defeated: victim={} killer={}", event.victim, event.killer);
  }

  @Subscribe
  public void onNpcQuestMessage(NpcQuestMessageEvent event) {
    if (event == null || !mPlayer.has(event.entityId) || !mMonster.has(event.npcId)) return;
    Player player = mPlayer.get(event.entityId);
    Monster npc = mMonster.get(event.npcId);
    if (player == null || player.data == null || npc == null || npc.monstats == null) return;
    int npcType = npc.monstats.hcIdx;
    if (npcType == MonsterType.TYRAEL1
        && event.messageIndex == Act2DurielQuest.MESSAGE_TYRAEL_PORTAL) {
      if (!isInLevel(event.entityId, D2LevelIds.LEVEL_DURIELSLAIR)
          || !openLutGholeinPortal(event.npcId, event.entityId)) return;
      grantTyraelCredit();
    } else if (npcType == MonsterType.JERHYN
        && event.messageIndex == Act2DurielQuest.MESSAGE_JERHYN_END) {
      updateRecord(player.data, Act2DurielQuest::acknowledgeJerhyn, "jerhyn-end");
    } else if (npcType == MonsterType.MESHIF1
        && event.messageIndex == Act2DurielQuest.MESSAGE_MESHIF_TRAVEL) {
      short previous = record(player.data);
      short next = Act2DurielQuest.travelWithMeshif(previous);
      if (next == previous) return;
      removeObsoleteHoradricItems(player.data.getItems());
      setRecord(player.data, next, "meshif-travel");
    }
  }

  private boolean isDuriel(int entityId) {
    if (!mMonster.has(entityId) || !isInLevel(entityId, D2LevelIds.LEVEL_DURIELSLAIR)) {
      return false;
    }
    Monster monster = mMonster.get(entityId);
    return monster != null && monster.monstats != null
        && monster.monstats.hcIdx == MonsterType.DURIEL;
  }

  private void markDurielKilledForPlayers() {
    if (playersByZone == null) return;
    IntSet parties = new IntSet();
    IntBag players = playersByZone.getEntities();
    int[] ids = players.getData();
    for (int i = 0; i < players.size(); i++) {
      int playerId = ids[i];
      if (!isInLevel(playerId, D2LevelIds.LEVEL_DURIELSLAIR)) continue;
      Player player = mPlayer.get(playerId);
      if (player == null || player.data == null) continue;
      updateRecord(player.data, Act2DurielQuest::markDurielKilled, "duriel-killed");
      if (partyManager != null) {
        short party = partyManager.getPartyId(playerId);
        if (party != Party.INVALID_ID) parties.add(party);
      }
    }
    if (partyManager == null) return;
    for (int i = 0; i < players.size(); i++) {
      int playerId = ids[i];
      Player player = mPlayer.get(playerId);
      if (player == null || player.data == null || !isInAct2(playerId)
          || !parties.contains(partyManager.getPartyId(playerId))) continue;
      updateRecord(player.data, Act2DurielQuest::markDurielKilled, "duriel-party-sync");
    }
  }

  private void grantTyraelCredit() {
    if (playersByZone == null) return;
    IntSet parties = new IntSet();
    IntBag players = playersByZone.getEntities();
    int[] ids = players.getData();
    for (int i = 0; i < players.size(); i++) {
      int playerId = ids[i];
      if (!isInLevel(playerId, D2LevelIds.LEVEL_DURIELSLAIR)) continue;
      Player player = mPlayer.get(playerId);
      if (player == null || player.data == null) continue;
      grantPrimaryGoal(playerId, player, "tyrael-portal");
      if (partyManager != null && NativeQuestRecord.has(
          record(player.data), NativeQuestRecord.PRIMARY_GOAL_DONE)) {
        short party = partyManager.getPartyId(playerId);
        if (party != Party.INVALID_ID) parties.add(party);
      }
    }
    if (partyManager != null) {
      for (int i = 0; i < players.size(); i++) {
        int playerId = ids[i];
        Player player = mPlayer.get(playerId);
        if (player == null || player.data == null || !isInAct2(playerId)
            || !parties.contains(partyManager.getPartyId(playerId))) continue;
        grantPrimaryGoal(playerId, player, "tyrael-party-sync");
      }
    }
    for (int i = 0; i < players.size(); i++) {
      Player player = mPlayer.get(ids[i]);
      if (player != null && player.data != null) {
        updateRecord(player.data, Act2DurielQuest::markCompletedNow,
            "duriel-completed-observer");
      }
    }
  }

  private void grantPrimaryGoal(int playerId, Player player, String reason) {
    short previous = record(player.data);
    short next = Act2DurielQuest.acceptTyraelPortal(previous);
    if (next == previous) return;
    player.data.flags = NativeCharacterProgression.update(
        player.data.flags, 2, difficulty(playerId), player.data.isExpansion());
    setRecord(player.data, next, reason);
  }

  private boolean openLutGholeinPortal(int npcId, int playerId) {
    int sourceId = mPosition.has(npcId) ? npcId : playerId;
    if (!mPosition.has(sourceId) || !mMapWrapper.has(sourceId)) return false;
    MapWrapper wrapper = mMapWrapper.get(sourceId);
    if (wrapper == null || wrapper.zone == null) return false;
    return ensureLutGholeinPortal(wrapper.zone, sourceId);
  }

  private void reconcileDurielLairState() {
    if (playersByZone == null) return;
    IntBag players = playersByZone.getEntities();
    int[] ids = players.getData();
    for (int i = 0; i < players.size(); i++) {
      int playerId = ids[i];
      if (!mPlayer.has(playerId) || !mMapWrapper.has(playerId)) continue;
      MapWrapper wrapper = mMapWrapper.get(playerId);
      if (wrapper == null || wrapper.zone == null || wrapper.zone.level == null
          || wrapper.zone.level.Id != D2LevelIds.LEVEL_DURIELSLAIR) continue;
      Player player = mPlayer.get(playerId);
      if (player != null && player.data != null) {
        reconcileDurielLairState(playerId, wrapper.zone, player.data);
      }
    }
  }

  private void reconcileDurielLairState(int playerId, Map.Zone zone, CharData data) {
    short quest = record(data);
    if (Act2DurielQuest.shouldRestoreTyraelDoor(quest)) openTyraelsDoor();
    if (Act2DurielQuest.shouldRestoreTownPortal(quest)) {
      ensureLutGholeinPortal(zone, playerId);
    }
  }

  /** Rebuilds the visual/Warp pair exactly once for a rebuilt Duriel Lair. */
  private boolean ensureLutGholeinPortal(Map.Zone zone, int fallbackEntityId) {
    if (zone == null || factory == null) return false;
    final int destination = D2LevelIds.LEVEL_LUTGHOLEIN;
    final int questWarp = QuestWarp.encode(destination);
    int warp = zone.findWarp(questWarp);
    if (warp != Engine.INVALID_ENTITY) {
      lutGholeinPortalOpened = true;
      if (hasPortalVisual(zone)) return true;
      if (!mPosition.has(warp)) return false;
      Position warpPosition = mPosition.get(warp);
      int visual = factory.createStaticObjectByClassId(
          NativeQuestObjectResolver.TOWN_PORTAL,
          warpPosition.position.x, warpPosition.position.y);
      log.info("[A2Q6] Restored Tyrael portal visual: visual={} warp={} destination={}",
          visual, warp, destination);
      return visual != Engine.INVALID_ENTITY;
    }

    int sourceId = findTyraelInZone(zone);
    if (sourceId == Engine.INVALID_ENTITY) sourceId = fallbackEntityId;
    if (!mPosition.has(sourceId)) return false;
    Position source = mPosition.get(sourceId);
    float x = source.position.x + 2f;
    float y = source.position.y;
    int visual = factory.createStaticObjectByClassId(
        NativeQuestObjectResolver.TOWN_PORTAL, x, y);
    warp = factory.createQuestWarp(destination, x, y);
    if (warp == Engine.INVALID_ENTITY) {
      if (visual != Engine.INVALID_ENTITY && world != null) world.delete(visual);
      return false;
    }
    zone.addWarp(warp);
    lutGholeinPortalOpened = true;
    log.info("[A2Q6] Restored Tyrael portal: visual={} warp={} destination={}",
        visual, warp, destination);
    return true;
  }

  private int findTyraelInZone(Map.Zone zone) {
    if (monstersByZone == null) return Engine.INVALID_ENTITY;
    IntBag monsters = monstersByZone.getEntities();
    int[] ids = monsters.getData();
    for (int i = 0; i < monsters.size(); i++) {
      int id = ids[i];
      if (!mMonster.has(id) || !mMapWrapper.has(id)) continue;
      MapWrapper wrapper = mMapWrapper.get(id);
      Monster monster = mMonster.get(id);
      if (wrapper != null && wrapper.zone == zone && monster != null
          && monster.monstats != null && monster.monstats.hcIdx == MonsterType.TYRAEL1) {
        return id;
      }
    }
    return Engine.INVALID_ENTITY;
  }

  private boolean hasPortalVisual(Map.Zone zone) {
    if (objectsByZone == null) return false;
    IntBag objects = objectsByZone.getEntities();
    int[] ids = objects.getData();
    for (int i = 0; i < objects.size(); i++) {
      int id = ids[i];
      if (!mObject.has(id) || !mMapWrapper.has(id)) continue;
      com.riiablo.engine.server.component.Object object = mObject.get(id);
      MapWrapper wrapper = mMapWrapper.get(id);
      if (wrapper != null && wrapper.zone == zone && object != null && object.base != null
          && object.base.Id == NativeQuestObjectResolver.TOWN_PORTAL) return true;
    }
    return false;
  }

  private void openTyraelsDoor() {
    if (objectsByZone == null) return;
    IntBag objects = objectsByZone.getEntities();
    int[] ids = objects.getData();
    for (int i = 0; i < objects.size(); i++) {
      int id = ids[i];
      com.riiablo.engine.server.component.Object object = mObject.get(id);
      if (object == null || object.base == null
          || object.base.Id != Act2DurielQuest.TYRAELS_DOOR
          || !isInLevel(id, D2LevelIds.LEVEL_DURIELSLAIR)) continue;
      NativeObjectState state = mNativeObjectState.has(id) ? mNativeObjectState.get(id) : null;
      boolean alreadyOpen = state != null ? state.opened && state.activated
          && state.currentMode == Engine.Object.MODE_ON
          : object.mode == Engine.Object.MODE_ON
              && (object.stateFlags & com.riiablo.engine.server.component.Object.STATE_OPENED) != 0;
      if (alreadyOpen) continue;
      if (state != null) {
        state.persistOpened(true);
        state.persistActivated(true);
        state.persistMode((byte) Engine.Object.MODE_ON);
      }
      if (cofs != null && mCofReference.has(id)) {
        cofs.setMode(id, (byte) Engine.Object.MODE_ON);
      } else {
        object.mode = (byte) Engine.Object.MODE_ON;
        object.stateFlags |= com.riiablo.engine.server.component.Object.STATE_OPENED;
      }
      log.info("[A2Q6] Tyrael door opened: entity={}", id);
    }
  }

  private boolean isInAct2(int entityId) {
    return Act2DurielQuest.isAct2(levelId(entityId));
  }

  private boolean isInLevel(int entityId, int expected) {
    return levelId(entityId) == expected;
  }

  private int levelId(int entityId) {
    if (!mMapWrapper.has(entityId)) return -1;
    MapWrapper wrapper = mMapWrapper.get(entityId);
    return wrapper == null || wrapper.zone == null || wrapper.zone.level == null
        ? -1 : wrapper.zone.level.Id;
  }

  private int difficulty(int entityId) {
    if (!mMapWrapper.has(entityId)) return 0;
    MapWrapper wrapper = mMapWrapper.get(entityId);
    return wrapper == null || wrapper.map == null ? 0 : wrapper.map.getDifficulty();
  }

  private static short record(CharData data) {
    return data.getQuests(Riiablo.ACT2)[RECORD];
  }

  private void updateRecord(CharData data,
      java.util.function.UnaryOperator<Short> transition, String reason) {
    short previous = record(data);
    short next = transition.apply(previous);
    if (next != previous) setRecord(data, next, reason);
  }

  private void setRecord(CharData data, short next, String reason) {
    short previous = record(data);
    if (next == previous) return;
    data.getQuests(Riiablo.ACT2)[RECORD] = next;
    if (data.managed && Riiablo.saves != null) D2SWriter.INSTANCE.save(data);
    log.info("[A2Q6] Quest record changed: character={} reason={} previous=0x{} next=0x{}",
        data.name, reason, Integer.toHexString(Short.toUnsignedInt(previous)),
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  private static void removeObsoleteHoradricItems(ItemData items) {
    if (items == null) return;
    items.removeItemCode(REMOVED_TAINTED_SUN_STAFF);
    items.removeItemCode(Act2HoradricStaffQuest.VIPER_AMULET);
    items.removeItemCode(REMOVED_FALSE_STAFF);
  }

  private static void markRecords(CharData data) {
    short[] act2 = data.getQuests(Riiablo.ACT2);
    short staff = act2[Act2HoradricStaffQuest.RECORD];
    staff = NativeQuestRecord.set(staff, NativeQuestRecord.REWARD_GRANTED);
    staff = NativeQuestRecord.set(staff, NativeQuestRecord.PRIMARY_GOAL_DONE);
    act2[Act2HoradricStaffQuest.RECORD] = staff;

    short duriel = act2[RECORD];
    duriel = NativeQuestRecord.set(duriel, NativeQuestRecord.STARTED);
    // Native insertion advances the quest state, but LEFT_TOWN belongs to
    // Tyrael's post-Duriel message and must not be set here.
    duriel = Act2DurielQuest.repairLegacyOrificeFlags(duriel);
    act2[RECORD] = duriel;
    if (data.managed && Riiablo.saves != null) D2SWriter.INSTANCE.save(data);
  }

  static boolean isAct2(int levelId) {
    return Act2DurielQuest.isAct2(levelId);
  }
}
