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
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.NativeObjectState;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.event.QuestObjectInteractionEvent;
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
import net.mostlyoriginal.api.event.common.Subscribe;

/** Server-authoritative A3Q6 Mephisto completion and Hell Gate transition. */
@Wire(failOnNull = false)
public class Act3MephistoQuestSystem extends BaseSystem {
  private static final Logger log = LogManager.getLogger(Act3MephistoQuestSystem.class);

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
  private final IntSet killedMephistos = new IntSet();

  @Override
  protected void initialize() {
    playersByZone = world.getAspectSubscriptionManager().get(
        Aspect.all(Player.class, MapWrapper.class));
    objectsByZone = world.getAspectSubscriptionManager().get(
        Aspect.all(com.riiablo.engine.server.component.Object.class, MapWrapper.class));
  }

  @Override
  protected void processSystem() {
    reconcileCompletedDurance();
  }

  @Subscribe
  public void onZoneChanged(ZoneChangeEvent event) {
    if (event == null || event.zone == null || event.zone.level == null
        || !mPlayer.has(event.entityId)
        || event.zone.level.Id != Act3MephistoQuest.MEPHISTO_LEVEL) return;
    Player player = mPlayer.get(event.entityId);
    if (player != null && player.data != null) {
      reconcileCompletedDurance(event.zone, player.data);
    }
  }

  @Subscribe
  public void onMonsterKilled(DeathEvent event) {
    if (event == null || event.victim < 0 || !mMonster.has(event.victim)
        || !mMapWrapper.has(event.victim) || !killedMephistos.add(event.victim)) return;
    Monster monster = mMonster.get(event.victim);
    if (monster == null || monster.monstats == null
        || monster.monstats.hcIdx != MonsterType.MEPHISTO
        || levelId(event.victim) != Act3MephistoQuest.MEPHISTO_LEVEL) return;
    completeForPlayers();
    MapWrapper victimWrapper = mapWrapper(event.victim);
    openDuranceExit(victimWrapper == null ? null : victimWrapper.zone);
    log.info("[A3Q6] Mephisto defeated: victim={} killer={}", event.victim, event.killer);
  }

  @Subscribe
  public void onQuestObjectInteraction(QuestObjectInteractionEvent interaction) {
    if (interaction == null || !mPlayer.has(interaction.playerId)
        || !mMapWrapper.has(interaction.entityId)) return;
    if (interaction.type != NativeQuestObjectResolver.Type.MEPHISTO_BRIDGE
        && interaction.type != NativeQuestObjectResolver.Type.HELL_GATE_PORTAL) return;
    Player player = mPlayer.get(interaction.playerId);
    if (player == null || player.data == null
        || levelId(interaction.entityId) != Act3MephistoQuest.MEPHISTO_LEVEL
        || !Act3MephistoQuest.shouldOpenExit(record(player.data))) return;
    interaction.accept(Engine.Object.MODE_ON);
    MapWrapper interactionWrapper = mapWrapper(interaction.entityId);
    openDuranceExit(interactionWrapper == null ? null : interactionWrapper.zone);
  }

  private void reconcileCompletedDurance() {
    if (playersByZone == null) return;
    IntBag players = playersByZone.getEntities();
    int[] ids = players.getData();
    for (int i = 0; i < players.size(); i++) {
      Player player = mPlayer.get(ids[i]);
      MapWrapper wrapper = mapWrapper(ids[i]);
      if (player == null || player.data == null || wrapper == null || wrapper.zone == null
          || wrapper.zone.level == null
          || wrapper.zone.level.Id != Act3MephistoQuest.MEPHISTO_LEVEL) continue;
      reconcileCompletedDurance(wrapper.zone, player.data);
    }
  }

  private void reconcileCompletedDurance(Map.Zone zone, CharData data) {
    if (zone == null || data == null
        || !Act3MephistoQuest.shouldOpenExit(record(data))) return;
    openDuranceObjects(zone);
    ensureAct4Warp(zone);
  }

  private void completeForPlayers() {
    if (playersByZone == null) return;
    IntSet parties = new IntSet();
    IntBag players = playersByZone.getEntities();
    int[] ids = players.getData();
    for (int i = 0; i < players.size(); i++) {
      int id = ids[i];
      Player player = mPlayer.get(id);
      if (player == null || player.data == null) continue;
      if (levelId(id) == Act3MephistoQuest.MEPHISTO_LEVEL) {
        complete(player.data, id, "mephisto-direct");
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
        if (player == null || player.data == null || !Act3MephistoQuest.isAct3Level(levelId(id))
            || !parties.contains(partyManager.getPartyId(id))) continue;
        complete(player.data, id, "mephisto-party");
      }
    }
    for (int i = 0; i < players.size(); i++) {
      Player player = mPlayer.get(ids[i]);
      if (player != null && player.data != null) {
        updateRecord(player.data, Act3MephistoQuest::completeObserver, "mephisto-observer");
      }
    }
  }

  private void complete(CharData data, int playerId, String reason) {
    short previous = record(data);
    short next = Act3MephistoQuest.complete(previous);
    if (next == previous) return;
    data.getQuests(Riiablo.ACT3)[Act3MephistoQuest.RECORD] = next;
    data.flags = NativeCharacterProgression.update(data.flags, 3, difficulty(playerId),
        data.isExpansion());
    persist(data);
    log.info("[A3Q6] Quest record changed: character={} reason={} previous=0x{} next=0x{} progression={}",
        data.name, reason, Integer.toHexString(Short.toUnsignedInt(previous)),
        Integer.toHexString(Short.toUnsignedInt(next)), NativeCharacterProgression.value(data.flags));
  }

  private void updateRecord(CharData data,
      java.util.function.UnaryOperator<Short> transition, String reason) {
    short previous = record(data);
    short next = transition.apply(previous);
    if (next == previous) return;
    data.getQuests(Riiablo.ACT3)[Act3MephistoQuest.RECORD] = next;
    persist(data);
    log.info("[A3Q6] Quest record changed: character={} reason={} previous=0x{} next=0x{}",
        data.name, reason, Integer.toHexString(Short.toUnsignedInt(previous)),
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  private void openDuranceExit(Map.Zone zone) {
    if (zone == null || zone.level == null
        || zone.level.Id != Act3MephistoQuest.MEPHISTO_LEVEL) return;
    openDuranceObjects(zone);
    ensureAct4Warp(zone);
  }

  private void openDuranceObjects(Map.Zone zone) {
    if (objectsByZone == null) return;
    IntBag objects = objectsByZone.getEntities();
    int[] ids = objects.getData();
    for (int i = 0; i < objects.size(); i++) {
      int id = ids[i];
      if (!mObject.has(id) || !mMapWrapper.has(id)) continue;
      MapWrapper wrapper = mMapWrapper.get(id);
      com.riiablo.engine.server.component.Object object = mObject.get(id);
      if (wrapper == null || wrapper.zone != zone || object == null || object.base == null
          || (object.base.Id != Act3MephistoQuest.MEPHISTO_BRIDGE
              && object.base.Id != Act3MephistoQuest.HELL_GATE_PORTAL)) continue;
      NativeObjectState state = mNativeObjectState.has(id) ? mNativeObjectState.get(id) : null;
      if (state != null) {
        state.persistOpened(true);
        state.persistActivated(true);
        state.persistMode((byte) Engine.Object.MODE_ON);
      }
      if (cofs != null && mCofReference.has(id)) cofs.setMode(id, (byte) Engine.Object.MODE_ON);
      object.mode = (byte) Engine.Object.MODE_ON;
      object.stateFlags |= com.riiablo.engine.server.component.Object.STATE_OPENED;
    }
  }

  private void ensureAct4Warp(Map.Zone zone) {
    if (factory == null || zone == null) return;
    int destination = Act3MephistoQuest.DESTINATION_ACT4;
    if (zone.findWarp(QuestWarp.encode(destination)) != Engine.INVALID_ENTITY) return;
    int source = findExitObject(zone);
    if (source == Engine.INVALID_ENTITY || !mPosition.has(source)) return;
    Position position = mPosition.get(source);
    int warp = factory.createQuestWarp(destination, position.position.x, position.position.y);
    if (warp != Engine.INVALID_ENTITY) {
      zone.addWarp(warp);
      log.info("[A3Q6] Hell Gate warp opened: entity={} destination={} position=({}, {})",
          warp, destination, position.position.x, position.position.y);
    }
  }

  private int findExitObject(Map.Zone zone) {
    if (objectsByZone == null) return Engine.INVALID_ENTITY;
    IntBag objects = objectsByZone.getEntities();
    int[] ids = objects.getData();
    for (int i = 0; i < objects.size(); i++) {
      int id = ids[i];
      if (!mObject.has(id) || !mMapWrapper.has(id)) continue;
      MapWrapper wrapper = mMapWrapper.get(id);
      com.riiablo.engine.server.component.Object object = mObject.get(id);
      if (wrapper != null && wrapper.zone == zone && object != null && object.base != null
          && object.base.Id == Act3MephistoQuest.HELL_GATE_PORTAL) return id;
    }
    return Engine.INVALID_ENTITY;
  }

  private MapWrapper mapWrapper(int entityId) {
    return mMapWrapper.has(entityId) ? mMapWrapper.get(entityId) : null;
  }

  private int levelId(int entityId) {
    MapWrapper wrapper = mapWrapper(entityId);
    return wrapper == null || wrapper.zone == null || wrapper.zone.level == null
        ? -1 : wrapper.zone.level.Id;
  }

  private int difficulty(int entityId) {
    MapWrapper wrapper = mapWrapper(entityId);
    return wrapper == null || wrapper.map == null ? 0 : wrapper.map.getDifficulty();
  }

  private static short record(CharData data) {
    return data.getQuests(Riiablo.ACT3)[Act3MephistoQuest.RECORD];
  }

  private static void persist(CharData data) {
    if (data.managed && Riiablo.saves != null) D2SWriter.INSTANCE.save(data);
  }
}
