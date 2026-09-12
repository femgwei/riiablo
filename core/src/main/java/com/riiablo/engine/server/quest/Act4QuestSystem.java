package com.riiablo.engine.server.quest;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntSet;
import com.d2moo.common.drlg.D2LevelIds;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatListRef;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.event.NpcQuestMessageEvent;
import com.riiablo.engine.server.event.QuestObjectInteractionEvent;
import com.riiablo.engine.server.event.ZoneChangeEvent;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.engine.server.object.NativeQuestObjectResolver;
import com.riiablo.engine.server.party.Party;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.save.CharData;
import com.riiablo.save.D2SWriter;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/** Server-authoritative Act IV A4Q1 Izual lifecycle and Tyrael reward. */
@Wire(failOnNull = false)
public class Act4QuestSystem extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(Act4QuestSystem.class);
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  @Wire(name = "factory", failOnNull = false)
  protected EntityFactory factory;
  @Wire(name = "partyManager", failOnNull = false)
  protected PartyManager partyManager;

  private EntitySubscription playersByZone;
  private EntitySubscription monstersByZone;
  private final IntSet spawnedIzualLevels = new IntSet();
  private final IntSet rewardedIzuals = new IntSet();
  private final IntSet activatedDiabloSeals = new IntSet();
  private final IntSet sealBossEntities = new IntSet();
  private final IntSet killedSealBosses = new IntSet();
  private final IntSet completedDiablos = new IntSet();
  private boolean diabloSpawned;
  private boolean allSealsActivated;

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
    if (player == null || player.data == null || event.zone.level.Id != D2LevelIds.LEVEL_PLAINSOFDESPAIR) return;
    updateRecord(player.data, Act4IzualQuest::start, "entered-plains-of-despair");
    spawnIzualIfNeeded(event.entityId, event.zone.level.Id);
    if (event.zone.level.Id == Act4DiabloQuest.CHAOS_SANCTUARY) {
      updateDiabloRecord(player.data);
    }
  }

  @Subscribe
  public void onQuestObjectInteraction(QuestObjectInteractionEvent interaction) {
    if (interaction == null || interaction.type != NativeQuestObjectResolver.Type.DIABLO_SEAL
        || !mPlayer.has(interaction.playerId) || !mPosition.has(interaction.entityId)) return;
    Player player = mPlayer.get(interaction.playerId);
    if (player == null || player.data == null || levelId(interaction.entityId) != Act4DiabloQuest.CHAOS_SANCTUARY
        || NativeQuestRecord.has(diabloRecord(player.data), NativeQuestRecord.REWARD_GRANTED)) return;
    if (!activatedDiabloSeals.add(interaction.entityId)) return;
    interaction.accept();
    updateDiabloRecord(player.data);
    log.info("[A4Q2] Chaos seal activated: object={} player={} count={}/5",
        interaction.entityId, interaction.playerId, activatedDiabloSeals.size);
    spawnSealBoss(interaction.objectClassId, interaction.entityId);
    if (activatedDiabloSeals.size >= 5) {
      allSealsActivated = true;
      spawnDiabloIfReady(interaction.entityId);
    }
  }

  private void spawnSealBoss(int objectClassId, int sealEntityId) {
    if (factory == null || objectClassId < Act4DiabloQuest.FIRST_SEAL
        || objectClassId > Act4DiabloQuest.FIRST_SEAL + 2) return;
    int monsterType;
    switch (objectClassId) {
      case 392: monsterType = MonsterType.INFECTOR_OF_SOULS; break;
      case 393: monsterType = MonsterType.LORD_DE_SEIS; break;
      case 394: monsterType = MonsterType.GRAND_VIZIER_OF_CHAOS; break;
      default: return;
    }
    Position position = mPosition.get(sealEntityId);
    if (position == null) return;
    int entity = factory.createMonster(monsterType, position.position.x, position.position.y);
    if (entity >= 0) {
      sealBossEntities.add(entity);
      log.info("[A4Q2] Seal boss spawned: seal={} monsterType={} entity={}",
          objectClassId, monsterType, entity);
    }
  }

  private void spawnDiabloIfReady(int sourceEntityId) {
    if (allSealsActivated && killedSealBosses.size >= 3) spawnDiablo(sourceEntityId);
  }

  private void spawnDiablo(int sourceEntityId) {
    if (diabloSpawned || factory == null) return;
    if (monstersByZone != null) {
      IntBag entities = monstersByZone.getEntities();
      int[] ids = entities.getData();
      for (int i = 0; i < entities.size(); i++) {
        Monster monster = mMonster.get(ids[i]);
        if (monster != null && monster.monstats != null && monster.monstats.hcIdx == MonsterType.DIABLO
            && levelId(ids[i]) == Act4DiabloQuest.CHAOS_SANCTUARY) {
          diabloSpawned = true;
          return;
        }
      }
    }
    Position position = mPosition.get(sourceEntityId);
    if (position == null) return;
    int entity = factory.createMonster(MonsterType.DIABLO, position.position.x, position.position.y);
    if (entity >= 0) {
      diabloSpawned = true;
      log.info("[A4Q2] Diablo spawned: entity={} seal={}", entity, sourceEntityId);
    }
  }

  private void spawnIzualIfNeeded(int playerId, int levelId) {
    if (spawnedIzualLevels.contains(levelId) || factory == null) return;
    if (monstersByZone != null) {
      IntBag entities = monstersByZone.getEntities();
      int[] ids = entities.getData();
      for (int i = 0; i < entities.size(); i++) {
        Monster monster = mMonster.get(ids[i]);
        if (monster != null && monster.monstats != null
            && monster.monstats.hcIdx == MonsterType.IZUAL
            && levelId(ids[i]) == levelId) {
          spawnedIzualLevels.add(levelId);
          return;
        }
      }
    }
    Position position = mPosition.has(playerId) ? mPosition.get(playerId) : null;
    if (position == null) return;
    int entity = factory.createMonster(MonsterType.IZUAL,
        position.position.x + 2f, position.position.y);
    if (entity >= 0) {
      spawnedIzualLevels.add(levelId);
      log.info("[A4Q1] Izual spawned: player={} entity={} level={}", playerId, entity, levelId);
    }
  }

  @Subscribe
  public void onMonsterKilled(DeathEvent event) {
    if (event == null || event.victim < 0 || !mMonster.has(event.victim)) return;
    Monster monster = mMonster.get(event.victim);
    if (sealBossEntities.contains(event.victim)) {
      if (killedSealBosses.add(event.victim)) {
        log.info("[A4Q2] Seal boss defeated: entity={} count={}/3", event.victim,
            killedSealBosses.size);
        spawnDiabloIfReady(event.victim);
      }
      return;
    }
    if (monster != null && monster.monstats != null && monster.monstats.hcIdx == MonsterType.DIABLO
        && levelId(event.victim) == Act4DiabloQuest.CHAOS_SANCTUARY
        && completedDiablos.add(event.victim)) {
      completeDiabloForPlayers();
      log.info("[A4Q2] Diablo defeated: victim={} killer={}", event.victim, event.killer);
      return;
    }
    if (monster == null || monster.monstats == null
        || monster.monstats.hcIdx != MonsterType.IZUAL
        || levelId(event.victim) != D2LevelIds.LEVEL_PLAINSOFDESPAIR
        || !rewardedIzuals.add(event.victim)) return;
    IntSet eligibleParties = new IntSet();
    if (playersByZone == null) return;
    IntBag players = playersByZone.getEntities();
    int[] ids = players.getData();
    for (int i = 0; i < players.size(); i++) {
      Player player = mPlayer.get(ids[i]);
      if (player == null || player.data == null || !isAct4Level(levelId(ids[i]))) continue;
      if (levelId(ids[i]) == D2LevelIds.LEVEL_PLAINSOFDESPAIR) {
        setObjective(player.data);
        if (partyManager != null) {
          short party = partyManager.getPartyId(ids[i]);
          if (party != Party.INVALID_ID) eligibleParties.add(party);
        }
      }
    }
    if (partyManager != null && eligibleParties.size > 0) {
      for (int i = 0; i < players.size(); i++) {
        int id = ids[i];
        Player player = mPlayer.get(id);
        if (player == null || player.data == null || !isAct4Level(levelId(id))) continue;
        if (eligibleParties.contains(partyManager.getPartyId(id))) setObjective(player.data);
      }
    }
    log.info("[A4Q1] Izual defeated: victim={} killer={}", event.victim, event.killer);
  }

  private void completeDiabloForPlayers() {
    if (playersByZone == null) return;
    IntSet parties = new IntSet();
    IntBag players = playersByZone.getEntities();
    int[] ids = players.getData();
    for (int i = 0; i < players.size(); i++) {
      int id = ids[i];
      Player player = mPlayer.get(id);
      if (player == null || player.data == null || levelId(id) != Act4DiabloQuest.CHAOS_SANCTUARY) continue;
      completeDiabloRecord(player.data);
      short party = partyManager == null ? Party.INVALID_ID : partyManager.getPartyId(id);
      if (party != Party.INVALID_ID) parties.add(party);
    }
    if (partyManager == null) return;
    for (int i = 0; i < players.size(); i++) {
      int id = ids[i];
      Player player = mPlayer.get(id);
      if (player == null || player.data == null || !isAct4Level(levelId(id))) continue;
      if (parties.contains(partyManager.getPartyId(id))) completeDiabloRecord(player.data);
    }
  }

  @Subscribe
  public void onNpcQuestMessage(NpcQuestMessageEvent event) {
    if (event == null || !mPlayer.has(event.entityId) || !mMonster.has(event.npcId)) return;
    Player player = mPlayer.get(event.entityId);
    Monster npc = mMonster.get(event.npcId);
    if (player == null || player.data == null || npc == null || npc.monstats == null
        || npc.monstats.hcIdx != MonsterType.TYRAEL2) return;
    short record = record(player.data);
    if (event.messageIndex == Act4IzualQuest.MESSAGE_TYRAEL_INIT) {
      updateRecord(player.data, Act4IzualQuest::start, "tyrael-init");
    } else if (event.messageIndex == Act4IzualQuest.MESSAGE_TYRAEL_REWARD
        && Act4IzualQuest.canClaimReward(record)) {
      short next = Act4IzualQuest.claimReward(record);
      addSkillPoints(event.entityId, player.data, 2);
      updateRecord(player.data, ignored -> next, "tyrael-izual-reward");
      log.info("[A4Q1] Tyrael granted 2 skill points: player={}", event.entityId);
    }
  }

  private void setObjective(CharData data) {
    updateRecord(data, Act4IzualQuest::completeObjective, "izual-defeated");
  }

  private void addSkillPoints(int playerId, CharData data, int amount) {
    Attributes attrs = mAttributesWrapper.has(playerId)
        ? mAttributesWrapper.get(playerId).attrs : data.getStats();
    if (attrs == null) return;
    add(attrs.base(), amount);
    add(attrs.aggregate(), amount);
  }

  private static void add(StatListRef stats, int amount) {
    StatRef current = stats.get(Stat.newskills);
    stats.put(Stat.newskills, (current == null ? 0 : current.asInt()) + amount);
  }

  private short record(CharData data) {
    return data.getQuests(Riiablo.ACT4)[Act4IzualQuest.RECORD];
  }

  private short diabloRecord(CharData data) {
    return data.getQuests(Riiablo.ACT4)[Act4DiabloQuest.RECORD];
  }

  private void updateDiabloRecord(CharData data) {
    short previous = diabloRecord(data);
    short next = Act4DiabloQuest.start(previous);
    if (next == previous) return;
    data.getQuests(Riiablo.ACT4)[Act4DiabloQuest.RECORD] = next;
    persist(data);
    log.info("[A4Q2] Quest record changed: character={} reason=entered-chaos-sanctuary previous=0x{} next=0x{}",
        data.name, Integer.toHexString(Short.toUnsignedInt(previous)),
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  private void completeDiabloRecord(CharData data) {
    short previous = diabloRecord(data);
    short next = Act4DiabloQuest.complete(previous);
    if (next == previous) return;
    data.getQuests(Riiablo.ACT4)[Act4DiabloQuest.RECORD] = next;
    persist(data);
    log.info("[A4Q2] Quest record changed: character={} reason=diablo-defeated previous=0x{} next=0x{}",
        data.name, Integer.toHexString(Short.toUnsignedInt(previous)),
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  private static void persist(CharData data) {
    if (data.managed && Riiablo.saves != null) D2SWriter.INSTANCE.save(data);
  }

  private void updateRecord(CharData data, java.util.function.UnaryOperator<Short> transition,
      String reason) {
    short previous = record(data);
    short next = transition.apply(previous);
    if (previous == next) return;
    data.getQuests(Riiablo.ACT4)[Act4IzualQuest.RECORD] = next;
    if (data.managed && Riiablo.saves != null) D2SWriter.INSTANCE.save(data);
    log.info("[A4Q1] Quest record changed: character={} reason={} previous=0x{} next=0x{}",
        data.name, reason, Integer.toHexString(Short.toUnsignedInt(previous)),
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  private int levelId(int entityId) {
    if (!mMapWrapper.has(entityId)) return -1;
    MapWrapper wrapper = mMapWrapper.get(entityId);
    return wrapper == null || wrapper.zone == null || wrapper.zone.level == null
        ? -1 : wrapper.zone.level.Id;
  }

  private static boolean isAct4Level(int levelId) {
    return levelId >= D2LevelIds.LEVEL_OUTERSTEPPES
        && levelId <= D2LevelIds.LEVEL_CHAOSSANCTUM;
  }
}
