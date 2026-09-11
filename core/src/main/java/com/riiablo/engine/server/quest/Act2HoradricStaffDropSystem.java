package com.riiablo.engine.server.quest;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;
import com.d2moo.common.drlg.D2LevelIds;
import com.riiablo.Riiablo;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.ObjectInteractionEvent;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.Quality;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/** Drops A2Q2's native quest items from OperateFn 39/40/41 chests. */
@Wire(failOnNull = false)
public class Act2HoradricStaffDropSystem extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(Act2HoradricStaffDropSystem.class);
  private static final int OPERATE_CUBE_CHEST = 39;
  private static final int OPERATE_SCROLL_CHEST = 40;
  private static final int OPERATE_STAFF_CHEST = 41;

  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Position> mPosition;
  @Wire(name = "factory", failOnNull = false)
  protected EntityFactory factory;
  @Wire(failOnNull = false)
  protected ItemGenerator itemGenerator;

  private EntitySubscription players;

  @Override
  protected void initialize() {
    players = world.getAspectSubscriptionManager().get(
        Aspect.all(Player.class, MapWrapper.class));
  }

  @Subscribe
  public void onObjectInteraction(ObjectInteractionEvent event) {
    if (event == null || !event.firstActivation()
        || !isStaffChest(event.operateFn) || !mPosition.has(event.entityId)
        || !mMapWrapper.has(event.entityId) || !isAct2Level(event.entityId)) return;
    String code = codeFor(event.operateFn);
    if (code == null || factory == null || itemGenerator == null || players == null) {
      log.warn("[A2Q2] quest chest drop unavailable: entity={} operateFn={} factory={} generator={}",
          event.entityId, event.operateFn, factory != null, itemGenerator != null);
      return;
    }

    int created = 0;
    IntBag entities = players.getEntities();
    int[] ids = entities.getData();
    Position origin = mPosition.get(event.entityId);
    for (int i = 0, size = entities.size(); i < size; i++) {
      Player player = mPlayer.get(ids[i]);
      if (player == null || player.data == null || !shouldDrop(player.data, code)) continue;
      if (createQuestItem(code, origin.position.x + i * 0.8f,
          origin.position.y + 0.8f, player.data)) created++;
    }
    log.info("[A2Q2] quest chest opened: entity={} operateFn={} code={} eligible={} created={}",
        event.entityId, event.operateFn, code, entities.size(), created);
  }

  private boolean shouldDrop(CharData data, String code) {
    if (NativeQuestRecord.has(data.getQuests(Riiablo.ACT2)[Act2HoradricStaffQuest.RECORD],
        NativeQuestRecord.REWARD_GRANTED)) return false;
    if (data.getItems() == null) return true;
    if (Act2HoradricStaffQuest.HORADRIC_SCROLL.equals(code)) {
      return !NativeQuestRecord.has(record(data), NativeQuestRecord.LEFT_TOWN)
          && !data.getItems().containsItemCode(code);
    }
    if (Act2HoradricStaffQuest.STAFF_OF_KINGS.equals(code)) {
      return !data.getItems().containsItemCode(code)
          && !data.getItems().containsItemCode(Act2HoradricStaffQuest.HORADRIC_STAFF);
    }
    return !data.getItems().containsItemCode(code);
  }

  private boolean createQuestItem(String code, float x, float y, CharData data) {
    try {
      Item item = itemGenerator.generate(code);
      item.version = Item.VERSION_110;
      item.ilvl = 1;
      item.quality = Quality.NORMAL;
      item.flags |= Item.ITEMFLAG_IDENTIFIED;
      if (item.attrs != null) {
        item.attrs.base().put(com.riiablo.attributes.Stat.questitemdifficulty,
            Math.max(0, Math.min(data.diff, 2)));
        item.attrs.reset();
      }
      int entityId = factory.createItem(item, x, y);
      item.id = entityId;
      return entityId >= 0;
    } catch (Throwable t) {
      log.error("[A2Q2] quest chest item creation failed: code={}", code, t);
      return false;
    }
  }

  private boolean isAct2Level(int entityId) {
    MapWrapper wrapper = mMapWrapper.get(entityId);
    Map.Zone zone = wrapper == null ? null : wrapper.zone;
    int levelId = zone == null || zone.level == null ? -1 : zone.level.Id;
    return levelId >= D2LevelIds.LEVEL_LUTGHOLEIN
        && levelId <= D2LevelIds.LEVEL_ARCANESANCTUARY;
  }

  private static short record(CharData data) {
    return data.getQuests(Riiablo.ACT2)[Act2HoradricStaffQuest.RECORD];
  }

  private static boolean isStaffChest(int operateFn) {
    return operateFn == OPERATE_CUBE_CHEST || operateFn == OPERATE_SCROLL_CHEST
        || operateFn == OPERATE_STAFF_CHEST;
  }

  private static String codeFor(int operateFn) {
    switch (operateFn) {
      case OPERATE_CUBE_CHEST: return Act2HoradricStaffQuest.HORADRIC_CUBE;
      case OPERATE_SCROLL_CHEST: return Act2HoradricStaffQuest.HORADRIC_SCROLL;
      case OPERATE_STAFF_CHEST: return Act2HoradricStaffQuest.STAFF_OF_KINGS;
      default: return null;
    }
  }
}
