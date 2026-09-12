package com.riiablo.engine.server.quest;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntSet;
import com.d2moo.common.drlg.D2LevelIds;
import com.d2moo.common.drlg.D2SuperUniques;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.SuperUniques;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SuperUnique;
import com.riiablo.engine.server.component.AttributesWrapper;
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
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
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
  private EntitySubscription monstersByZone;
  private final IntSet spawnedShenkLevels = new IntSet();
  private final IntSet killedShenkEntities = new IntSet();
  private final IntSet spawnedNihlathakLevels = new IntSet();
  private final IntSet killedNihlathakEntities = new IntSet();
  private final IntSet activatedAncientStatues = new IntSet();
  private final IntIntMap ancientStatueEntities = new IntIntMap();
  private final IntSet spawnedAncientEntities = new IntSet();
  private final IntSet killedAncientEntities = new IntSet();
  private final IntSet rescuedCages = new IntSet();

  @Override
  protected void initialize() {
    playersByZone = world.getAspectSubscriptionManager().get(
        Aspect.all(Player.class, MapWrapper.class));
    monstersByZone = world.getAspectSubscriptionManager().get(
        Aspect.all(Monster.class, MapWrapper.class));
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

  @Subscribe
  public void onMonsterKilled(DeathEvent event) {
    if (event == null || event.victim < 0 || !mMonster.has(event.victim)
        || !mMapWrapper.has(event.victim)) return;
    if (isAncient(event.victim) && isAncientSummit(levelId(event.victim))
        && killedAncientEntities.add(event.victim)) {
      onAncientKilled(event.victim);
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
      updateAncientsRecord(player.data, Act5AncientsQuest::complete, "ancients-defeated");
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
        updateAncientsRecord(player.data, Act5AncientsQuest::complete, "ancients-party-sync");
      }
    }
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
