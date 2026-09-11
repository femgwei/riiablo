package com.riiablo.engine.server.quest;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.d2moo.common.drlg.D2LevelIds;
import com.riiablo.Riiablo;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.ObjectInteractionEvent;
import com.riiablo.engine.server.object.NativeObjectOperateTable.Lifecycle;
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
  /** Act II record slot for The Seven Tombs / Duriel. */
  public static final int RECORD = 6;
  private static final String REMOVED_TAINTED_SUN_STAFF = "tsh";
  private static final String REMOVED_FALSE_STAFF = "fsm";

  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Position> mPosition;
  @Wire(name = "factory", failOnNull = false)
  protected EntityFactory factory;

  @Override
  protected void processSystem() {
    // Opening is driven by the authoritative object interaction event.
  }

  @Subscribe
  public void onObjectInteraction(ObjectInteractionEvent event) {
    if (event == null || !event.firstActivation()
        || event.lifecycle != Lifecycle.STAFF_ORIFICE
        || event.objectClassId != ORIFICE_OBJECT_ID) return;
    Player player = mPlayer.get(event.playerId);
    MapWrapper wrapper = mMapWrapper.get(event.entityId);
    Position source = mPosition.get(event.entityId);
    if (player == null || player.data == null || wrapper == null || wrapper.zone == null
        || wrapper.zone.level == null || source == null
        || !isAct2(wrapper.zone.level.Id)) {
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

  private static void markRecords(CharData data) {
    short[] act2 = data.getQuests(Riiablo.ACT2);
    short staff = act2[Act2HoradricStaffQuest.RECORD];
    staff = NativeQuestRecord.set(staff, NativeQuestRecord.REWARD_GRANTED);
    staff = NativeQuestRecord.set(staff, NativeQuestRecord.PRIMARY_GOAL_DONE);
    act2[Act2HoradricStaffQuest.RECORD] = staff;

    short duriel = act2[RECORD];
    duriel = NativeQuestRecord.set(duriel, NativeQuestRecord.STARTED);
    duriel = NativeQuestRecord.set(duriel, NativeQuestRecord.LEFT_TOWN);
    act2[RECORD] = duriel;
    if (data.managed && Riiablo.saves != null) D2SWriter.INSTANCE.save(data);
  }

  static boolean isAct2(int levelId) {
    return levelId >= D2LevelIds.LEVEL_LUTGHOLEIN
        && levelId < D2LevelIds.LEVEL_KURASTDOCKTOWN;
  }
}
