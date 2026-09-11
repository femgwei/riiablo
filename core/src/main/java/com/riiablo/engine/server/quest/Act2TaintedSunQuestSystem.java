package com.riiablo.engine.server.quest;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;
import com.d2moo.common.drlg.D2LevelIds;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Levels;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.ObjectInteractionEvent;
import com.riiablo.engine.server.object.NativeObjectOperateTable.Lifecycle;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.Quality;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import com.riiablo.save.D2SWriter;
import net.mostlyoriginal.api.event.common.Subscribe;

/** Native A2Q3 Tainted Sun altar state and Viper Amulet reward bridge. */
@Wire(failOnNull = false)
public class Act2TaintedSunQuestSystem extends BaseSystem {
  /** Act II records: Q0 gossip, Q1 Radament, Q2 Staff, Q3 Tainted Sun. */
  public static final int RECORD = 3;
  private static final Logger log = LogManager.getLogger(Act2TaintedSunQuestSystem.class);
  private static final int ALTAR_OBJECT_ID = 149;
  private static final String VIPER_AMULET = Act2HoradricStaffQuest.VIPER_AMULET;
  /** Native A2Q3 guard item (fourcc ' tsh'). */
  private static final String TAINTED_SUN_STAFF = "tsh";
  private static final float[] DROP_X = {1.5f, -1.5f, 0f, 2.5f, -2.5f};
  private static final float[] DROP_Y = {0f, 0f, 1.5f, -1.5f, -1.5f};

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

  @Override
  protected void processSystem() {
    // Altar state is event-driven; no per-frame work is required.
  }

  @Subscribe
  public void onObjectInteraction(ObjectInteractionEvent event) {
    if (event == null || !event.firstActivation()
        || event.lifecycle != Lifecycle.TAINTED_SUN_ALTAR
        || event.objectClassId != ALTAR_OBJECT_ID) return;
    MapWrapper altarWrapper = mMapWrapper.get(event.entityId);
    Position altarPosition = mPosition.get(event.entityId);
    int altarLevel = levelId(altarWrapper);
    if (!isTaintedSunLevel(altarLevel) || altarPosition == null) {
      log.warn("[A2Q3] Ignoring altar outside Valley of Snakes/Claw Viper Temple: "
          + "entity={} level={}", event.entityId, altarLevel);
      return;
    }

    int created = 0;
    if (players != null) {
      IntBag entities = players.getEntities();
      int[] ids = entities.getData();
      for (int i = 0; i < entities.size(); i++) {
        int playerId = ids[i];
        if (!isEligible(playerId)) continue;
        Player player = mPlayer.get(playerId);
        Item item = createAmulet(altarPosition, created);
        if (item == null) continue;
        int entityId = factory == null ? -1 : factory.createItem(item,
            altarPosition.position.x + DROP_X[created % DROP_X.length],
            altarPosition.position.y + DROP_Y[created % DROP_Y.length]);
        if (entityId < 0) continue;
        item.id = entityId;
        markRewardPending(player.data);
        created++;
      }
    }
    log.info("[A2Q3] Tainted Sun altar activated: entity={} level={} player={} amulets={}",
        event.entityId, altarLevel, event.playerId, created);
  }

  private boolean isEligible(int playerId) {
    if (!mPlayer.has(playerId)) return false;
    Player player = mPlayer.get(playerId);
    if (player == null || player.data == null) return false;
    short a2q2 = player.data.getQuests(Riiablo.ACT2)[Act2HoradricStaffQuest.RECORD];
    if (NativeQuestRecord.has(a2q2, NativeQuestRecord.REWARD_GRANTED)) return false;
    return player.data.getItems() == null
        || (!player.data.getItems().containsItemCode(VIPER_AMULET)
            && !player.data.getItems().containsItemCode(TAINTED_SUN_STAFF));
  }

  private Item createAmulet(Position origin, int index) {
    if (factory == null || itemGenerator == null || Riiablo.files == null) return null;
    try {
      Item item = itemGenerator.generate(VIPER_AMULET);
      if (item == null) return null;
      item.version = Item.VERSION_110;
      item.quality = Quality.UNIQUE;
      item.flags |= Item.ITEMFLAG_IDENTIFIED;
      return item;
    } catch (Throwable t) {
      log.error("[A2Q3] Viper Amulet generation failed", t);
      return null;
    }
  }

  private static void markRewardPending(CharData data) {
    short[] act2 = data.getQuests(Riiablo.ACT2);
    short record = act2[RECORD];
    record = NativeQuestRecord.set(record, NativeQuestRecord.PRIMARY_GOAL_DONE);
    record = NativeQuestRecord.set(record, NativeQuestRecord.REWARD_PENDING);
    record = NativeQuestRecord.set(record, NativeQuestRecord.COMPLETED_NOW);
    act2[RECORD] = record;
    if (data.managed && Riiablo.saves != null) D2SWriter.INSTANCE.save(data);
  }

  private static int levelId(MapWrapper wrapper) {
    Map.Zone zone = wrapper == null ? null : wrapper.zone;
    Levels.Entry level = zone == null ? null : zone.level;
    return level == null ? -1 : level.Id;
  }

  static boolean isTaintedSunLevel(int levelId) {
    return levelId == D2LevelIds.LEVEL_VALLEYOFSNAKES
        || levelId == D2LevelIds.LEVEL_CLAWVIPERTEMPLELEV1
        || levelId == D2LevelIds.LEVEL_CLAWVIPERTEMPLELEV2;
  }
}
