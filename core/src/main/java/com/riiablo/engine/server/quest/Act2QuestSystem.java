package com.riiablo.engine.server.quest;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.IntSet;
import com.d2moo.common.drlg.D2LevelIds;
import com.d2moo.common.drlg.D2SuperUniques;
import com.riiablo.Riiablo;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.NativeObjectState;
import com.riiablo.engine.server.component.Object;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SuperUnique;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.event.NpcQuestMessageEvent;
import com.riiablo.engine.server.event.QuestObjectInteractionEvent;
import com.riiablo.engine.server.event.ZoneChangeEvent;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.engine.server.party.Party;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.engine.server.object.NativeQuestObjectResolver;
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
import net.mostlyoriginal.api.system.core.PassiveSystem;

/** Event adapter for native Act II quest scripts, beginning with A2Q1. */
@Wire(failOnNull = false)
public class Act2QuestSystem extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(Act2QuestSystem.class);
  /**
   * Project-facing 1.10f level id. The imported D2LevelIds table historically
   * aliases Arcane Sanctuary to Duriel's Lair (74), while Levels.txt and the
   * Java map builders correctly use 75.
   */
  static final int ARCANE_SANCTUARY_LEVEL = 75;

  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Object> mObject;
  protected ComponentMapper<NativeObjectState> mNativeObjectState;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<SuperUnique> mSuperUnique;
  @Wire(failOnNull = false)
  protected ItemGenerator itemGenerator;
  @Wire(name = "factory", failOnNull = false)
  protected EntityFactory factory;
  @Wire(name = "partyManager", failOnNull = false)
  protected PartyManager partyManager;
  @Wire(name = "map", failOnNull = false)
  protected Map map;

  private EntitySubscription playersByZone;
  private final IntSet completedRadaments = new IntSet();
  private final IntSet completedSummoners = new IntSet();

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
    if (player == null || player.data == null) return;

    int levelId = event.zone.level.Id;
    if (!isAct2Level(levelId) || levelId == D2LevelIds.LEVEL_LUTGHOLEIN) return;
    updateRecord(player.data, Act2RadamentQuest::leaveTown, "left-lut-gholein");
    if (isSewers(levelId)) {
      updateRecord(player.data, Act2RadamentQuest::enterSewers, "entered-sewers");
    }
    // Some native DS1 exports omit the Tainted Sun altar object. Materialize
    // it in the first real RoomEx so the production interaction path remains
    // available; normal exports are left untouched by hasQuestObject().
    if (levelId == D2LevelIds.LEVEL_VALLEYOFSNAKES
        || levelId == D2LevelIds.LEVEL_CLAWVIPERTEMPLELEV1
        || levelId == D2LevelIds.LEVEL_CLAWVIPERTEMPLELEV2) {
      ensureAct2QuestObject(event.zone,
          NativeQuestObjectResolver.TAINTED_SUN_ALTAR, "Tainted Sun altar", 24);
    }
    // The Horadric Orifice exists only in the seed-selected staff tomb.  A
    // few DS1 exports omit the preset, so use the same room-backed fallback
    // while preserving the native level selection rule.
    int gameSeed = map == null ? Riiablo.gameSeed : map.seed();
    if (levelId == Act2TombSelection.forGameSeed(gameSeed).staffTombLevel()) {
      ensureAct2QuestObject(event.zone,
          NativeQuestObjectResolver.HORADRIC_ORIFICE, "Horadric Orifice", 42);
    }
    // Riiablo's level table keeps Arcane Sanctuary at 75 (Act III starts at
    // 76). Preserve that project-facing convention rather than using the
    // legacy D2MOO alias which shares the Duriel id.
    if (levelId == ARCANE_SANCTUARY_LEVEL) {
      ensureAct2QuestObject(event.zone,
          NativeQuestObjectResolver.ARCANE_SANCTUARY_TOME, "Arcane Sanctuary Tome", 42);
    }
  }

  private void ensureAct2QuestObject(Map.Zone zone, int classId, String label, int interactType) {
    if (zone == null || factory == null || map == null || mObject == null
        || mMapWrapper == null || mNativeObjectState == null || hasQuestObject(zone, classId)) {
      return;
    }
    Map.RoomEx room = zone.getRoomsEx().size == 0 ? null : zone.getRoomsEx().get(0);
    float x = room == null ? zone.x() + zone.width() / 2f : room.x + room.width / 2f;
    float y = room == null ? zone.y() + zone.height() / 2f : room.y + room.height / 2f;
    int entity = factory.createStaticObjectByClassId(classId, x, y);
    if (entity < 0) return;
    mMapWrapper.create(entity).set(map, zone);
    NativeObjectState state = mNativeObjectState.create(entity);
    state.set(classId, classId, classId, com.riiablo.engine.Engine.Object.MODE_NU,
        false, false, NativePresetObjectResolver.Kind.ORDINARY);
    state.interactType = interactType;
    state.source = new Map.NativeObject(classId, com.riiablo.engine.Engine.Object.MODE_NU,
        (int) (x - zone.x()), (int) (y - zone.y()), false, false);
    log.info("[A2] materialized missing {}: level={} entity={}", label,
        zone.level == null ? -1 : zone.level.Id, entity);
  }

  private boolean hasQuestObject(Map.Zone zone, int classId) {
    if (zone == null || mObject == null || mMapWrapper == null) return false;
    IntBag entities = world.getAspectSubscriptionManager().get(
        Aspect.all(Object.class, MapWrapper.class)).getEntities();
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
  public void onNpcQuestMessage(NpcQuestMessageEvent event) {
    if (event == null || !mPlayer.has(event.entityId) || !mMonster.has(event.npcId)) return;
    Player player = mPlayer.get(event.entityId);
    Monster npc = mMonster.get(event.npcId);
    if (player == null || player.data == null || npc == null || npc.monstats == null) return;

    int npcType = npc.monstats.hcIdx;
    if (Act2SummonerQuest.isRewardNpc(npcType)
        && Act2SummonerQuest.isRewardMessage(event.messageIndex)) {
      onSummonerRewardMessage(event, player);
      return;
    }
    if (npcType != MonsterType.ATMA && npcType != MonsterType.DECKARDCAIN_ACT2) return;

    if (npcType == MonsterType.DECKARDCAIN_ACT2) {
      onCainStaffMessage(event, player);
      return;
    }
    if (event.messageIndex == Act2RadamentQuest.MESSAGE_INIT) {
      updateRecord(player.data, Act2RadamentQuest::start, "atma-init-message");
    } else if (event.messageIndex == Act2RadamentQuest.MESSAGE_REWARD) {
      short previous = getRecord(player.data);
      short next = Act2RadamentQuest.claimReward(previous);
      if (previous == next) return;
      setRecord(player.data, next);
      persist(player.data);
      log.info("[A2Q1] Atma reward acknowledged: player={} record=0x{}",
          event.entityId, Integer.toHexString(Short.toUnsignedInt(next)));
    }
  }

  private void onSummonerRewardMessage(NpcQuestMessageEvent event, Player player) {
    short[] act2 = player.data.getQuests(Riiablo.ACT2);
    if (act2 == null || act2.length <= Act2SummonerQuest.RECORD) return;
    short previous = act2[Act2SummonerQuest.RECORD];
    short next = Act2SummonerQuest.claimReward(previous);
    if (next == previous) return;
    act2[Act2SummonerQuest.RECORD] = next;
    persist(player.data);
    log.info("[A2Q5] Summoner reward acknowledged: player={} npc={} message={} record=0x{}",
        event.entityId, event.npcId, event.messageIndex,
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  /**
   * A2Q4's native OperateFn 42 handler.  The generic object interactor owns
   * the animation/state transition; this callback owns only the authoritative
   * quest record and nearby party credit.
   */
  @Subscribe
  public void onQuestObjectInteraction(QuestObjectInteractionEvent event) {
    if (event == null
        || event.type != NativeQuestObjectResolver.Type.ARCANE_SANCTUARY_TOME
        || !mPlayer.has(event.playerId)) return;
    Player player = mPlayer.get(event.playerId);
    if (player == null || player.data == null || !isPlayerInLevel(
        event.playerId, ARCANE_SANCTUARY_LEVEL)) return;

    short previous = getHorazonRecord(player.data);
    short next = Act2HorazonTomeQuest.completeObjective(previous);
    // Native A2Q4 still opens the tome and displays its scroll message after
    // the quest has already been completed.  Accepting an idempotent request
    // keeps object state and quest state independent.
    event.accept();
    if (next == previous) return;
    setHorazonRecord(player.data, next);
    persist(player.data);
    propagateHorazonTome(event.playerId);
    log.info("[A2Q4] Horazon tome activated: player={} record=0x{}",
        event.playerId, Integer.toHexString(Short.toUnsignedInt(next)));
  }

  private void onCainStaffMessage(NpcQuestMessageEvent message, Player player) {
    CharData data = player.data;
    short previous = getStaffRecord(data);
    short next;
    switch (message.messageIndex) {
      case Act2HoradricStaffQuest.MESSAGE_SCROLL:
        if (!data.getItems().removeItemCode(Act2HoradricStaffQuest.HORADRIC_SCROLL)) return;
        next = Act2HoradricStaffQuest.acknowledgeScroll(previous);
        break;
      case Act2HoradricStaffQuest.MESSAGE_AMULET:
        next = Act2HoradricStaffQuest.acknowledgeAmulet(previous);
        break;
      case Act2HoradricStaffQuest.MESSAGE_STAFF:
        next = Act2HoradricStaffQuest.acknowledgeStaff(previous);
        break;
      case Act2HoradricStaffQuest.MESSAGE_CUBE:
        next = Act2HoradricStaffQuest.acknowledgeCube(previous);
        break;
      case Act2HoradricStaffQuest.MESSAGE_ASSEMBLED:
        if (!data.getItems().containsItemCode(Act2HoradricStaffQuest.HORADRIC_STAFF)) return;
        next = Act2HoradricStaffQuest.acknowledgeAssembly(previous);
        break;
      default:
        return;
    }
    if (next == previous) return;
    setStaffRecord(data, next);
    persist(data);
    log.info("[A2Q2] Cain message applied: player={} message={} previous=0x{} next=0x{}",
        message.entityId, message.messageIndex,
        Integer.toHexString(Short.toUnsignedInt(previous)),
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  @Subscribe
  public void onMonsterKilled(DeathEvent event) {
    if (event == null) return;
    if (isSummoner(event.victim)) {
      onSummonerKilled(event.victim);
      return;
    }
    if (!isRadament(event.victim)) return;
    if (!completedRadaments.add(event.victim)) {
      log.warn("[A2Q1] Duplicate Radament death ignored: victim={}", event.victim);
      return;
    }

    IntBag players = playersByZone == null ? null : playersByZone.getEntities();
    if (players == null) return;
    int[] ids = players.getData();
    IntSet eligibleParties = new IntSet();

    // MapWrapper exposes level zones rather than native adjacent RoomEx lists.
    // Sewers level 3 is therefore the closest deterministic room proxy.
    for (int i = 0, size = players.size(); i < size; i++) {
      int playerId = ids[i];
      if (!isPlayerInLevel(playerId, D2LevelIds.LEVEL_SEWERSLVL3ACT2)) continue;
      completeObjective(playerId, "radament-level");
      if (partyManager != null) {
        short partyId = partyManager.getPartyId(playerId);
        if (partyId != Party.INVALID_ID) eligibleParties.add(partyId);
      }
    }

    // D2MOO propagates primary-goal/reward-pending to those players' party
    // members anywhere in Act II. Everyone else receives COMPLETED_NOW only.
    for (int i = 0, size = players.size(); i < size; i++) {
      int playerId = ids[i];
      Player player = mPlayer.get(playerId);
      if (player == null || player.data == null) continue;
      short partyId = partyManager == null ? Party.INVALID_ID
          : partyManager.getPartyId(playerId);
      if (partyId != Party.INVALID_ID && eligibleParties.contains(partyId)
          && isPlayerInAct2(playerId)) {
        completeObjective(playerId, "eligible-act2-party-member");
      } else {
        updateRecord(player.data, Act2RadamentQuest::markCompletedNow,
            "radament-died-this-game");
      }
    }

    int books = countRequiredSkillBooks(players);
    int created = dropSkillBooks(event.victim, books);
    log.info("[A2Q1] Radament killed: victim={} players={} booksRequired={} booksCreated={} partyService={}",
        event.victim, players.size(), books, created, partyManager != null);
  }

  private void onSummonerKilled(int victimId) {
    if (!completedSummoners.add(victimId)) {
      log.warn("[A2Q5] Duplicate Summoner death ignored: victim={}", victimId);
      return;
    }

    IntBag players = playersByZone == null ? null : playersByZone.getEntities();
    if (players == null) return;
    int[] ids = players.getData();
    int credited = 0;
    for (int i = 0; i < players.size(); i++) {
      int playerId = ids[i];
      Player player = mPlayer.get(playerId);
      if (player == null || player.data == null) continue;
      if (isPlayerInLevel(playerId, ARCANE_SANCTUARY_LEVEL)) {
        short previous = getSummonerRecord(player.data);
        short next = Act2SummonerQuest.completeObjective(previous);
        if (next != previous) {
          setSummonerRecord(player.data, next);
          persist(player.data);
          credited++;
        }
      } else if (isPlayerInAct2(playerId)) {
        short previous = getSummonerRecord(player.data);
        short next = Act2SummonerQuest.markCompletedNow(previous);
        if (next != previous) {
          setSummonerRecord(player.data, next);
          persist(player.data);
        }
      }
    }
    log.info("[A2Q5] Summoner killed: victim={} creditedArcanePlayers={}", victimId, credited);
  }

  private boolean isRadament(int entityId) {
    if (entityId < 0 || !mMonster.has(entityId)
        || !isEntityInLevel(entityId, D2LevelIds.LEVEL_SEWERSLVL3ACT2)) return false;
    if (mSuperUnique.has(entityId)
        && mSuperUnique.get(entityId).id == D2SuperUniques.SUPERUNIQUE_RADAMENT) return true;
    Monster monster = mMonster.get(entityId);
    // Some imported DS1 paths resolve the MonStats row before preserving the
    // SuperUniques row. Keep the native class-id fallback constrained to L49.
    return monster != null && monster.monstats != null
        && monster.monstats.hcIdx == MonsterType.RADAMENT;
  }

  private boolean isSummoner(int entityId) {
    if (entityId < 0 || !mMonster.has(entityId)
        || !isEntityInLevel(entityId, ARCANE_SANCTUARY_LEVEL)) return false;
    if (mSuperUnique.has(entityId)
        && mSuperUnique.get(entityId).id == D2SuperUniques.SUPERUNIQUE_THE_SUMMONER) return true;
    Monster monster = mMonster.get(entityId);
    return monster != null && monster.monstats != null
        && monster.monstats.hcIdx == MonsterType.SUMMONER;
  }

  private int countRequiredSkillBooks(IntBag players) {
    int count = 0;
    int[] ids = players.getData();
    for (int i = 0, size = players.size(); i < size; i++) {
      Player player = mPlayer.get(ids[i]);
      if (player == null || player.data == null) continue;
      boolean hasBook = player.data.getItems() != null
          && player.data.getItems().containsItemCode(Act2RadamentQuest.SKILL_BOOK_CODE);
      if (Act2RadamentQuest.needsSkillBook(getRecord(player.data), hasBook)) count++;
    }
    return count;
  }

  private int dropSkillBooks(int victimId, int count) {
    if (count <= 0) return 0;
    if (factory == null || itemGenerator == null || !mPosition.has(victimId)) {
      log.warn("[A2Q1] Skill-book drop unavailable: victim={} count={} factory={} generator={} position={}",
          victimId, count, factory != null, itemGenerator != null, mPosition.has(victimId));
      return 0;
    }

    Position origin = mPosition.get(victimId);
    int created = 0;
    for (int i = 0; i < count; i++) {
      try {
        Item book = itemGenerator.generate(Act2RadamentQuest.SKILL_BOOK_CODE);
        book.version = Item.VERSION_110;
        book.ilvl = 0;
        book.quality = Quality.NORMAL;
        book.flags |= Item.ITEMFLAG_IDENTIFIED;
        float angle = count == 1 ? 0f : MathUtils.PI2 * i / count;
        int entityId = factory.createItem(book,
            origin.position.x + MathUtils.cos(angle) * 1.25f,
            origin.position.y + MathUtils.sin(angle) * 1.25f);
        book.id = entityId;
        created++;
      } catch (Throwable t) {
        log.error("[A2Q1] Skill-book creation failed: victim={} index={}", victimId, i, t);
      }
    }
    return created;
  }

  private void completeObjective(int playerId, String reason) {
    if (!mPlayer.has(playerId)) return;
    Player player = mPlayer.get(playerId);
    if (player == null || player.data == null) return;
    updateRecord(player.data, Act2RadamentQuest::completeObjective, reason);
  }

  private boolean isPlayerInAct2(int entityId) {
    return isAct2Level(levelId(entityId));
  }

  private boolean isPlayerInLevel(int entityId, int levelId) {
    return mPlayer.has(entityId) && isEntityInLevel(entityId, levelId);
  }

  private boolean isEntityInLevel(int entityId, int levelId) {
    return levelId(entityId) == levelId;
  }

  private int levelId(int entityId) {
    if (!mMapWrapper.has(entityId)) return -1;
    MapWrapper wrapper = mMapWrapper.get(entityId);
    Map.Zone zone = wrapper == null ? null : wrapper.zone;
    return zone == null || zone.level == null ? -1 : zone.level.Id;
  }

  static boolean isSewers(int levelId) {
    return levelId >= D2LevelIds.LEVEL_SEWERSLVL1ACT2
        && levelId <= D2LevelIds.LEVEL_SEWERSLVL3ACT2;
  }

  static boolean isAct2Level(int levelId) {
    return levelId >= D2LevelIds.LEVEL_LUTGHOLEIN
        && levelId <= ARCANE_SANCTUARY_LEVEL;
  }

  private static short getRecord(CharData data) {
    return data.getQuests(Riiablo.ACT2)[Act2RadamentQuest.RECORD];
  }

  private static short getStaffRecord(CharData data) {
    return data.getQuests(Riiablo.ACT2)[Act2HoradricStaffQuest.RECORD];
  }

  private static short getSummonerRecord(CharData data) {
    return data.getQuests(Riiablo.ACT2)[Act2SummonerQuest.RECORD];
  }

  private static short getHorazonRecord(CharData data) {
    return data.getQuests(Riiablo.ACT2)[Act2HorazonTomeQuest.RECORD];
  }

  private static void setRecord(CharData data, short record) {
    data.getQuests(Riiablo.ACT2)[Act2RadamentQuest.RECORD] = record;
  }

  private static void setStaffRecord(CharData data, short record) {
    data.getQuests(Riiablo.ACT2)[Act2HoradricStaffQuest.RECORD] = record;
  }

  private static void setSummonerRecord(CharData data, short record) {
    data.getQuests(Riiablo.ACT2)[Act2SummonerQuest.RECORD] = record;
  }

  private static void setHorazonRecord(CharData data, short record) {
    data.getQuests(Riiablo.ACT2)[Act2HorazonTomeQuest.RECORD] = record;
  }

  private void propagateHorazonTome(int sourcePlayerId) {
    if (playersByZone == null || partyManager == null) return;
    short partyId = partyManager.getPartyId(sourcePlayerId);
    if (partyId == Party.INVALID_ID) return;
    IntBag entities = playersByZone.getEntities();
    int[] ids = entities.getData();
    for (int i = 0; i < entities.size(); i++) {
      int playerId = ids[i];
      if (playerId == sourcePlayerId || partyManager.getPartyId(playerId) != partyId
          || !isPlayerInLevel(playerId, ARCANE_SANCTUARY_LEVEL)) continue;
      Player member = mPlayer.get(playerId);
      if (member == null || member.data == null) continue;
      short previous = getHorazonRecord(member.data);
      short next = Act2HorazonTomeQuest.completeObjective(previous);
      if (next == previous) continue;
      setHorazonRecord(member.data, next);
      persist(member.data);
      log.info("[A2Q4] Horazon tome party credit: source={} member={} record=0x{}",
          sourcePlayerId, playerId, Integer.toHexString(Short.toUnsignedInt(next)));
    }
  }

  private void updateRecord(CharData data, RecordUpdate update, String reason) {
    short previous = getRecord(data);
    short next = update.apply(previous);
    if (previous == next) return;
    setRecord(data, next);
    persist(data);
    log.info("[A2Q1] Quest record changed: character={} reason={} previous=0x{} next=0x{}",
        data.name, reason, Integer.toHexString(Short.toUnsignedInt(previous)),
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  private static void persist(CharData data) {
    if (data.managed && Riiablo.saves != null) D2SWriter.INSTANCE.save(data);
  }

  private interface RecordUpdate {
    short apply(short record);
  }
}
