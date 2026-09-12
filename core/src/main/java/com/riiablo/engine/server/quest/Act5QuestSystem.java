package com.riiablo.engine.server.quest;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntSet;
import com.d2moo.common.drlg.D2LevelIds;
import com.d2moo.common.drlg.D2SuperUniques;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.SuperUniques;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SuperUnique;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.event.NpcQuestMessageEvent;
import com.riiablo.engine.server.event.QuestObjectInteractionEvent;
import com.riiablo.engine.server.event.ZoneChangeEvent;
import com.riiablo.engine.server.object.NativeQuestObjectResolver;
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
import net.mostlyoriginal.api.system.core.PassiveSystem;

/** Server-authoritative A5Q1 Shenk lifecycle and Larzuk completion state. */
@Wire(failOnNull = false)
public class Act5QuestSystem extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(Act5QuestSystem.class);

  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<SuperUnique> mSuperUnique;
  @Wire(name = "factory", failOnNull = false)
  protected EntityFactory factory;
  @Wire(name = "partyManager", failOnNull = false)
  protected PartyManager partyManager;
  @Wire(failOnNull = false)
  protected ItemGenerator itemGenerator;

  private EntitySubscription playersByZone;
  private final IntSet spawnedShenkLevels = new IntSet();
  private final IntSet killedShenkEntities = new IntSet();
  private final IntSet rescuedCages = new IntSet();

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
    if (event.zone.level.Id == D2LevelIds.LEVEL_BLOODYFOOTHILLS) {
      updateRecord(player.data, Act5ShenkQuest::start, "entered-bloody-foothills");
      spawnShenkIfNeeded(event.entityId, event.zone.level.Id);
    } else if (event.zone.level.Id == Act5RescueQuest.FRIGID_HIGHLANDS) {
      updateRescueRecord(player.data, Act5RescueQuest::start, "entered-frigid-highlands");
    }
  }

  @Subscribe
  public void onQuestObjectInteraction(QuestObjectInteractionEvent interaction) {
    if (interaction == null
        || interaction.type != NativeQuestObjectResolver.Type.CAGED_SOLDIER
        || !mPlayer.has(interaction.playerId) || !mMapWrapper.has(interaction.entityId)) return;
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

  @Subscribe
  public void onMonsterKilled(DeathEvent event) {
    if (event == null || event.victim < 0 || !mMonster.has(event.victim)
        || !mMapWrapper.has(event.victim) || !isShenk(event.victim)
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
    if (!isLarzuk(npc.monstats) || event.messageIndex != Act5ShenkQuest.MESSAGE_LARZUK_REWARD) return;
    short previous = record(player.data);
    short next = Act5ShenkQuest.claimReward(previous);
    if (next == previous) return;
    updateRecord(player.data, ignored -> next, "larzuk-shenk-reward");
    log.info("[A5Q1] Larzuk reward claimed: player={} (socket service entitlement)",
        event.entityId);
  }

  private void onQualKehkMessage(NpcQuestMessageEvent event, Player player) {
    short previous = rescueRecord(player.data);
    if (event.messageIndex == Act5RescueQuest.MESSAGE_QUAL_KEHK_INIT) {
      updateRescueRecord(player.data, Act5RescueQuest::start, "qual-kehk-init");
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

  private short record(CharData data) {
    return data.getQuests(Riiablo.ACT5)[Act5ShenkQuest.RECORD];
  }

  private short rescueRecord(CharData data) {
    return data.getQuests(Riiablo.ACT5)[Act5RescueQuest.RECORD];
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

  private int levelId(int entityId) {
    if (!mMapWrapper.has(entityId)) return -1;
    MapWrapper wrapper = mMapWrapper.get(entityId);
    return wrapper == null || wrapper.zone == null || wrapper.zone.level == null
        ? -1 : wrapper.zone.level.Id;
  }

  private static boolean isAct5Level(int levelId) {
    return levelId >= D2LevelIds.LEVEL_HARROGATH
        && levelId <= D2LevelIds.LEVEL_WORLDSTONECHAMBER;
  }
}
