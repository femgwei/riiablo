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
import com.riiablo.engine.server.component.NativeObjectState;
import com.riiablo.engine.server.component.Object;
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
import com.riiablo.map.NativePresetObjectResolver;
import com.riiablo.save.CharData;
import com.riiablo.save.D2SWriter;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.event.common.EventSystem;
import com.artemis.BaseSystem;

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
public class Act3QuestSystem extends ContextualQuestSystem {
  private static final Logger log = LogManager.getLogger(Act3QuestSystem.class);

  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Object> mObject;
  protected ComponentMapper<NativeObjectState> mNativeObjectState;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Position> mPosition;
  @Wire(failOnNull = false)
  protected ItemGenerator itemGenerator;
  @Wire(name = "factory", failOnNull = false)
  protected EntityFactory factory;
  @Wire(name = "partyManager", failOnNull = false)
  protected PartyManager partyManager;
  @Wire(name = "map", failOnNull = false)
  protected Map map;
  @Wire(failOnNull = false)
  protected NativeQuestRandomState questRandomState;
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
  private boolean darkWandererInitialized;
  private boolean darkWandererCoordinatesCalculated;
  private boolean darkWandererDeparted;
  private int darkWandererEntity = -1;
  private com.riiablo.engine.server.component.NativePathIdentity.Token darkWandererIdentity;
  private long darkWandererDepartureFrame = -1;
  private long darkWandererFrame;
  private double darkWandererElapsed;
  private final com.badlogic.gdx.math.Vector2 darkWandererCoordinates = new com.badlogic.gdx.math.Vector2();
  private final com.badlogic.gdx.utils.IntMap<Long> afterglowDeadlines = new com.badlogic.gdx.utils.IntMap<>();
  /** Independent EVENTTYPE_ENDANIM deadline (native schedules this separately from MONUMOD). */
  private final com.badlogic.gdx.utils.IntMap<Long> afterglowEndDeadlines = new com.badlogic.gdx.utils.IntMap<>();
  private final IntSet afterglowSpawned = new IntSet();
  private final com.badlogic.gdx.utils.IntMap<com.riiablo.engine.server.component.NativePathIdentity.Token>
      afterglowIdentities = new com.badlogic.gdx.utils.IntMap<>();
  private final com.d2moo.common.drlg.D2Seed wandererQuestSeed = new com.d2moo.common.drlg.D2Seed();

  @Override protected void processSystem() {
    darkWandererElapsed += Math.max(0f, world.delta) * 25.0;
    com.riiablo.engine.server.AuthoritativeSimulation simulation =
        com.riiablo.engine.server.AuthoritativeSimulation.current();
    darkWandererFrame = simulation == null ? (long) (darkWandererElapsed + .00001)
        : simulation.tickNumber();
    updateVileDogAfterglows();
    if (!darkWandererInitialized && factory != null) {
      IntBag objects = world.getAspectSubscriptionManager().get(
          Aspect.all(Object.class, Position.class)).getEntities();
      for (int i = 0; i < objects.size(); i++) {
        int id = objects.get(i);
        Object object = mObject.get(id);
        if (object.base == null || object.base.InitFn != 43) continue;
        com.riiablo.codec.excel.MonStats.Entry row = files().monstats.get("darkwanderer");
        if (row == null) break;
        boolean alreadySeen = false;
        IntBag players = playersByZone.getEntities();
        for (int j = 0; j < players.size(); j++) {
          Player player = mPlayer.get(players.get(j));
          if (player.data != null && NativeQuestRecord.has(player.data.getQuests(Riiablo.ACT3)[7],
              NativeQuestRecord.REWARD_GRANTED)) { alreadySeen = true; break; }
        }
        if (!alreadySeen) {
          com.badlogic.gdx.math.Vector2 position = mPosition.get(id).position;
          darkWandererCoordinates.set((int) position.x + 7, (int) position.y);
          darkWandererEntity = factory.createMonster(row,
              darkWandererCoordinates.x, darkWandererCoordinates.y);
          if (darkWandererEntity < 0) continue;
          com.riiablo.engine.server.component.NativePathIdentity identity =
              world.getMapper(com.riiablo.engine.server.component.NativePathIdentity.class)
                  .get(darkWandererEntity);
          darkWandererIdentity = identity == null ? null : identity.token();
        }
        darkWandererInitialized = true;
        break;
      }
    }
    if (!darkWandererDeparted && darkWandererDepartureFrame >= 0
        && darkWandererFrame >= darkWandererDepartureFrame) {
      if (darkWandererIdentity != null
          && !darkWandererIdentity.isCurrent(world, darkWandererEntity)) {
        // A delayed quest callback must never target a recycled ECS id.
        darkWandererDeparted = true;
        return;
      }
      darkWandererDeparted = true;
      IntBag players = playersByZone.getEntities();
      for (int i = 0; i < players.size(); i++) {
        int id = players.get(i);
        Player player = mPlayer.get(id);
        MapWrapper wrapper = mMapWrapper.get(id);
        if (player.data == null || wrapper.zone == null || wrapper.zone.level == null
            || !isAct3Level(wrapper.zone.level.Id)) continue;
        player.data.getQuests(Riiablo.ACT3)[7] = NativeQuestRecord.set(
            player.data.getQuests(Riiablo.ACT3)[7], NativeQuestRecord.REWARD_GRANTED);
        persist(player.data);
      }
      // A3Q7's source starts this accumulator at (0,0), not the wanderer's
      // position. Preserve that placement behavior rather than silently fixing it.
      MapWrapper wrapper = darkWandererEntity < 0 ? null : mMapWrapper.get(darkWandererEntity);
      if (wrapper != null && wrapper.zone != null) {
        int[][] offsets = {{-3,-3},{-3,0},{-3,3},{0,-3},{0,3},{3,-3},{3,0},{-3,3}};
        com.d2moo.common.drlg.D2Seed questSeed = questRandomState == null
            ? wandererQuestSeed : questRandomState.seed();
        int start = (int) (com.d2moo.common.seed.Seed.rollRandomNumber(questSeed) & 1L);
        com.badlogic.gdx.math.Vector2 point = new com.badlogic.gdx.math.Vector2();
        com.badlogic.gdx.math.Vector2 free = new com.badlogic.gdx.math.Vector2();
        for (int i = start; i < offsets.length; i++) {
          point.add(offsets[i][0], offsets[i][1]);
          if (wrapper.zone.findFreeCoordinates(point, 3, 11, 0x1C09, true, free)) {
            // COLLISION_GetFreeCoordinates writes back through the native
            // coordinate structure. Subsequent offsets therefore accumulate
            // from the resolved location, rather than the blocked candidate.
            point.set(free);
            factory.createStaticObjectByClassId(131, free.x, free.y);
          }
        }
      }
    }
  }

  private void updateVileDogAfterglows() {
    IntBag objects = world.getAspectSubscriptionManager().get(
        Aspect.all(Object.class, Position.class, MapWrapper.class)).getEntities();
    for (int i = 0; i < objects.size(); i++) {
      int id = objects.get(i);
      Object object = mObject.get(id);
      if (object.base == null || object.base.InitFn != 59) continue;
      com.riiablo.engine.server.component.NativePathIdentity.Token previous = afterglowIdentities.get(id);
      if (previous != null && !previous.isCurrent(world, id)) {
        afterglowDeadlines.remove(id);
        afterglowEndDeadlines.remove(id);
        afterglowSpawned.remove(id);
        afterglowIdentities.remove(id);
      }
      Long deadline = afterglowDeadlines.get(id);
      if (deadline == null) {
        if (object.mode != com.riiablo.engine.Engine.Object.MODE_NU) continue;
        setAfterglowMode(id, com.riiablo.engine.Engine.Object.MODE_OP);
        afterglowDeadlines.put(id, darkWandererFrame + 27);
        // Objects.txt stores 8.8 fixed-point frame counts, matching D2MOO's
        // (FrameCnt[1] >> 8) + gameFrame + 1 expression.
        int frameCount = object.base.FrameCnt == null
            || com.riiablo.engine.Engine.Object.MODE_OP >= object.base.FrameCnt.length
            ? 0 : (object.base.FrameCnt[com.riiablo.engine.Engine.Object.MODE_OP] >> 8);
        // Hand-authored compatibility objects may omit Objects.txt timing;
        // retain the historical 27-frame visual lifetime in that case.
        if (frameCount <= 0) frameCount = 27;
        afterglowEndDeadlines.put(id, darkWandererFrame + frameCount + 1L);
        com.riiablo.engine.server.component.NativePathIdentity identity = world.getMapper(
            com.riiablo.engine.server.component.NativePathIdentity.class).get(id);
        if (identity != null) afterglowIdentities.put(id, identity.token());
      } else {
        // EVENTTYPE_MONUMOD: spawn at +27, independently of animation length.
        if (deadline >= 0 && darkWandererFrame >= deadline && !afterglowSpawned.contains(id)) {
          afterglowSpawned.add(id);
          spawnVileChild(id);
          afterglowDeadlines.put(id, -1L);
        }
        // EVENTTYPE_ENDANIM: the object enters OPENED when its operating
        // animation ends. This may occur before or after the +27 spawn.
        Long end = afterglowEndDeadlines.get(id);
        if (end != null && end >= 0 && darkWandererFrame >= end) {
          afterglowEndDeadlines.put(id, -1L);
          setAfterglowMode(id, com.riiablo.engine.Engine.Object.MODE_ON);
        }
      }
    }
  }

  private void spawnVileChild(int id) {
        MapWrapper owner = mMapWrapper.get(id);
        if (factory != null && owner != null && owner.zone != null && owner.zone.level != null
            && owner.zone.level.Id == D2LevelIds.LEVEL_SPIDERFOREST) {
          com.riiablo.codec.excel.MonStats.Entry child = files().monstats.get("vilechild1");
          com.badlogic.gdx.math.Vector2 point = mPosition.get(id).position.cpy();
          if (child != null && owner.zone.findFreeCoordinates(point, 1, 3, 0x1C09, false, point))
            factory.createMonster(child, point.x, point.y);
        }
  }

  private void setAfterglowMode(int id, byte mode) {
    com.riiablo.engine.server.CofManager cofs = world.getSystem(com.riiablo.engine.server.CofManager.class);
    if (cofs != null) cofs.setMode(id, mode);
    mObject.get(id).mode = mode;
    NativeObjectState state = mNativeObjectState.get(id);
    if (state != null) {
      state.persistMode(mode);
      if (mode == com.riiablo.engine.Engine.Object.MODE_ON)
        state.persistOpened(true);
    }
  }

  public boolean getDarkWandererCoordinates(int entityId, com.badlogic.gdx.math.Vector2 result) {
    if (!darkWandererInitialized || entityId != darkWandererEntity) return false;
    if (!darkWandererCoordinatesCalculated) {
      MapWrapper wrapper = mMapWrapper.get(entityId);
      if (wrapper == null || wrapper.map == null || wrapper.zone == null) return false;
      darkWandererCoordinatesCalculated = true;
      int x = (int) darkWandererCoordinates.x, y = (int) darkWandererCoordinates.y;
      int[][] probes = {{x,y-20},{x,y-11},{x+2,y-11},{x-2,y-11},{x,y-8}};
      darkWandererCoordinates.set(x, y - 3);
      for (int[] probe : probes) {
        if (!wrapper.map.isBlockedForPath(wrapper.zone, probe[0], probe[1], 0x1C09, 1, false)) {
          darkWandererCoordinates.set(probe[0], probe[1]); break;
        }
      }
    }
    result.set(darkWandererCoordinates);
    return true;
  }

  public void scheduleDarkWandererDeparture(int entityId, long frame) {
    if (entityId == darkWandererEntity
        && (darkWandererIdentity == null || darkWandererIdentity.isCurrent(world, entityId))
        && darkWandererDepartureFrame < 0)
      darkWandererDepartureFrame = (frame / 20 + 3) * 20;
  }

  @Override
  protected void initialize() {
    // D2Game seeds quest control from the game-owned seed.  The authoritative
    // map is the server's world seed, so prefer it over the process-wide
    // client fallback; this keeps multiple worlds isolated while all Act III
    // quest rolls in one world consume the same stream.
    if (questRandomState == null) {
      long gameSeed = map == null ? gameSeed() : map.seed();
      com.d2moo.common.seed.Seed.initLowSeed(wandererQuestSeed, (int) gameSeed);
    }
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
    prepareZoneQuestObjects(event.zone);
  }

  /** Materialize compatibility quest fixtures before an arrival is chosen,
   * not on the next ZoneChange after placing a player on their footprint. */
  public void prepareZoneQuestObjects(Map.Zone zone) {
    if (zone == null || zone.level == null) return;
    // D2Game's quest initialization owns these special object placements.
    // Some old DS1 exports omit the quest unit, so retain the native class
    // and lifecycle by materializing the missing object on first area entry.
    if (zone.level.Id == D2LevelIds.LEVEL_FLAYERJUNGLE) {
      ensureAct3QuestObject(zone, Act3GidbinnQuest.GIDBINN_DECOY_OBJECT);
    } else if (zone.level.Id == D2LevelIds.LEVEL_SPIDERCAVERN) {
      ensureAct3QuestObject(zone, Act3KhalimQuest.KHALIM_CHEST2);
    } else if (zone.level.Id == D2LevelIds.LEVEL_FLAYERDUNGEONLVL3) {
      ensureAct3QuestObject(zone, Act3KhalimQuest.KHALIM_CHEST1);
    } else if (zone.level.Id == D2LevelIds.LEVEL_SEWERSA3LEV2) {
      ensureAct3QuestObject(zone, Act3KhalimQuest.KHALIM_CHEST3);
    } else if (zone.level.Id == D2LevelIds.LEVEL_TRAVINCAL) {
      ensureAct3QuestObject(zone, Act3KhalimQuest.COMPELLING_ORB);
    }
  }

  private void ensureAct3QuestObject(Map.Zone zone, int classId) {
    if (zone == null || factory == null || mObject == null || mMapWrapper == null
        || mNativeObjectState == null || map == null || hasQuestObject(zone, classId)) return;
    Map.RoomEx room = zone.getRoomsEx().size == 0 ? null : zone.getRoomsEx().get(0);
    float x = room == null ? zone.x() + zone.width() / 2f : room.x + room.width / 2f;
    float y = room == null ? zone.y() + zone.height() / 2f : room.y + room.height / 2f;
    int entity = factory.createStaticObjectByClassId(classId, x, y);
    if (entity < 0) return;
    mMapWrapper.create(entity).set(map, zone);
    NativeObjectState state = mNativeObjectState.create(entity);
    state.set(classId, classId, classId, com.riiablo.engine.Engine.Object.MODE_NU,
        false, false, NativePresetObjectResolver.Kind.ORDINARY);
    state.source = new Map.NativeObject(classId, com.riiablo.engine.Engine.Object.MODE_NU,
        (int) (x - zone.x()), (int) (y - zone.y()), false, false);
    zone.getEntities().add(entity);
    com.riiablo.engine.server.ObjectCollisionUpdater collision = world.getSystem(
        com.riiablo.engine.server.ObjectCollisionUpdater.class);
    if (collision != null) collision.synchronizeCreatedObject(entity);
    log.info("[A3] materialized missing native quest object: level={} class={} entity={}",
        zone.level == null ? -1 : zone.level.Id, classId, entity);
  }

  private boolean hasQuestObject(Map.Zone zone, int classId) {
    if (zone == null) return false;
    if (mObject == null || mMapWrapper == null) return false;
    // A genuine preset may still be deferred in an inactive room. Native
    // A3Q2 object operators consume that preset; a room-center compatibility
    // duplicate is not a substitute for the authored quest chest.
    for (Map.NativeObject preset : zone.getNativeObjects()) {
      if (!preset.externalEntity || preset.spawned) continue;
      int id = preset.presetIndex;
      if (preset.ds1Raw) {
        if (files() == null || files().obj == null || zone.level == null) continue;
        int act = zone.level.Act + 1;
        if (id < 0 || id >= files().obj.getSize(act)) continue;
        id = files().obj.getObjectId(act, id);
      }
      if (id == classId) return true;
    }
    // Prewarming creates entities before Artemis publishes its subscriptions.
    // The zone-owned list is already complete at this transaction boundary.
    for (int i = 0; i < zone.getEntities().size; i++) {
      int id = zone.getEntities().get(i);
      Object object = mObject.get(id);
      if (object != null && object.base != null && object.base.Id == classId
          && mMapWrapper.has(id) && mMapWrapper.get(id).zone == zone) return true;
    }
    com.artemis.utils.IntBag entities = world.getAspectSubscriptionManager().get(
        com.artemis.Aspect.all(Object.class, MapWrapper.class)).getEntities();
    int[] ids = entities.getData();
    for (int i = 0; i < entities.size(); i++) {
      Object object = mObject.get(ids[i]);
      MapWrapper wrapper = mMapWrapper.get(ids[i]);
      if (object != null && object.base != null && object.base.Id == classId
          && wrapper != null && wrapper.zone == zone) return true;
    }
    return false;
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
        || player.data.getItems().containsItemCode(code) || factory == null) {
      return;
    }
    Item item = createQuestItem(code);
    Position position = mPosition.get(interaction.entityId);
    if (item == null || position == null) {
      return;
    }
    int entityId = factory.createItem(item, position.position.x, position.position.y);
    if (entityId < 0) return;
    MapWrapper objectWrapper = mMapWrapper.get(interaction.entityId);
    if (objectWrapper != null && objectWrapper.zone != null) {
      // Overlapping Act III level rectangles make coordinate-only ownership
      // ambiguous. Bind quest relics to the chest's authoritative Zone.
      mMapWrapper.create(entityId).set(map, objectWrapper.zone);
    }
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
    // D2Game grants the Khalim's Will objective to eligible party members in
    // the same area.  Keep the actor's item removal authoritative, then copy
    // only the quest record to party members (their inventories are not
    // mutated by another player's orb interaction).
    propagateKhalimStatus(interaction.playerId, next);
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
    MapWrapper victimWrapper = mMapWrapper.get(event.victim);
    if (victimWrapper != null && victimWrapper.zone != null) {
      mMapWrapper.create(entityId).set(map, victimWrapper.zone);
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
    int npcType = QuestNpcPolicy.resolve(npc.monstats);
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

  private String khalimChestCode(int objectClassId) {
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

  private void propagateKhalimStatus(int sourcePlayerId, short record) {
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
      short previous = khalimRecord(member.data);
      // Preserve any component flags already earned by the member while
      // applying the shared objective/reward bits from the actor.
      short next = (short) (previous | record);
      if (next != previous) updateKhalimRecord(member.data, next, "party-orb-smashed");
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

  private void persist(CharData data) {
    persistCharacter(data);
  }
}
