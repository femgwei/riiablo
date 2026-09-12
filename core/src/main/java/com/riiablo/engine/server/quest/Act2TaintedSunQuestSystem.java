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
import com.riiablo.codec.excel.Levels;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.ObjectInteractionEvent;
import com.riiablo.engine.server.object.NativeObjectOperateTable.Lifecycle;
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
  @Wire(name = "map", failOnNull = false)
  protected Map map;
  @Wire(failOnNull = false)
  protected ItemGenerator itemGenerator;
  @Wire(name = "partyManager", failOnNull = false)
  protected PartyManager partyManager;

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
      IntSet eligibleParties = new IntSet();
      for (int i = 0; i < entities.size(); i++) {
        int playerId = ids[i];
        if (!isEligible(playerId)) continue;
        Item item = createAmulet();
        if (item == null) continue;
        int entityId = factory == null ? -1 : factory.createItem(item,
            altarPosition.position.x + DROP_X[created % DROP_X.length],
            altarPosition.position.y + DROP_Y[created % DROP_Y.length]);
        if (entityId < 0) continue;
        // Valley of Snakes and Claw Viper Temple use overlapping synthetic
        // level rectangles in the Java map bridge. Preserve the altar's
        // authoritative zone on each amulet instead of re-inferring it from
        // the drop coordinates.
        if (map != null && altarWrapper != null && altarWrapper.zone != null) {
          mMapWrapper.create(entityId).set(map, altarWrapper.zone);
        }
        item.id = entityId;
        created++;
      }

      // D2Game first credits every player in the altar level, then propagates
      // PRIMARYGOALDONE/REWARDPENDING to those players' party members anywhere
      // in Act II.  Viper Amulet drop counting is deliberately game-wide and
      // remains separate from quest-record eligibility.
      for (int i = 0; i < entities.size(); i++) {
        int playerId = ids[i];
        Player player = mPlayer.get(playerId);
        if (player == null || player.data == null
            || levelId(mMapWrapper.get(playerId)) != altarLevel) continue;
        markRewardPending(player.data);
        if (partyManager != null) {
          short partyId = partyManager.getPartyId(playerId);
          if (partyId != Party.INVALID_ID) eligibleParties.add(partyId);
        }
      }
      if (partyManager != null && eligibleParties.size > 0) {
        for (int i = 0; i < entities.size(); i++) {
          int playerId = ids[i];
          short partyId = partyManager.getPartyId(playerId);
          Player player = mPlayer.get(playerId);
          if (partyId == Party.INVALID_ID || !eligibleParties.contains(partyId)
              || player == null || player.data == null
              || !isAct2Level(levelId(mMapWrapper.get(playerId)))) continue;
          markRewardPending(player.data);
        }
      }
      for (int i = 0; i < entities.size(); i++) {
        Player player = mPlayer.get(ids[i]);
        if (player != null && player.data != null) markCompletedNow(player.data);
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

  private Item createAmulet() {
    if (factory == null || itemGenerator == null || Riiablo.files == null) return null;
    try {
      Item item = itemGenerator.generate(VIPER_AMULET);
      if (item == null) return null;
      item.version = Item.VERSION_110;
      item.quality = Quality.UNIQUE;
      // Quest uniques such as 'vip' are not rows in UniqueItems.txt. Native
      // D2 still emits ITEMQUAL_UNIQUE; use the reserved id so naming and
      // serialization fall back to the base quest-item record.
      item.qualityId = Item.NO_UNIQUE_ID;
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
    if (NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
        || NativeQuestRecord.has(record, NativeQuestRecord.COMPLETED_BEFORE)) return;
    short previous = record;
    record = NativeQuestRecord.set(record, NativeQuestRecord.PRIMARY_GOAL_DONE);
    record = NativeQuestRecord.set(record, NativeQuestRecord.REWARD_PENDING);
    record = NativeQuestRecord.clear(record, NativeQuestRecord.COMPLETED_NOW);
    act2[RECORD] = record;
    if (record != previous && data.managed && Riiablo.saves != null) D2SWriter.INSTANCE.save(data);
  }

  private static void markCompletedNow(CharData data) {
    short[] act2 = data.getQuests(Riiablo.ACT2);
    short record = act2[RECORD];
    if (NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
        || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)) return;
    short next = NativeQuestRecord.set(record, NativeQuestRecord.COMPLETED_NOW);
    if (next == record) return;
    act2[RECORD] = next;
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

  private static boolean isAct2Level(int levelId) {
    return levelId >= D2LevelIds.LEVEL_LUTGHOLEIN
        && levelId <= Act2QuestSystem.ARCANE_SANCTUARY_LEVEL;
  }
}
