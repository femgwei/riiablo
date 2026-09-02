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
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.SuperUnique;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.KillCreditResolver;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.event.NativeCountessQuestEvent;
import com.riiablo.engine.server.event.NativeActTransitionEvent;
import com.riiablo.engine.server.event.NpcQuestMessageEvent;
import com.riiablo.engine.server.event.NativeQuestRewardEvent;
import com.riiablo.engine.server.event.NativeCainQuestEvent;
import com.riiablo.engine.server.event.QuestItemPickedUpEvent;
import com.riiablo.engine.server.event.QuestObjectInteractionEvent;
import com.riiablo.engine.server.event.ZoneChangeEvent;
import com.riiablo.engine.server.monster.MonsterType;
import com.d2moo.common.drlg.D2SuperUniques;
import com.riiablo.engine.server.object.NativeQuestObjectResolver;
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
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/** Event adapter for native Act 1 quest scripts. */
@Wire(failOnNull = false)
public class Act1QuestSystem extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(Act1QuestSystem.class);

  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Mercenary> mMercenary;
  protected ComponentMapper<SummonedPet> mSummonedPet;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<Corpse> mCorpse;
  protected ComponentMapper<SuperUnique> mSuperUnique;
  protected EventSystem event;
  @Wire(failOnNull = false)
  protected ItemGenerator itemGenerator;
  @Wire(name = "partyManager", failOnNull = false)
  protected PartyManager partyManager;

  private EntitySubscription monstersByZone;
  private EntitySubscription playersByZone;
  private final Act1CainRuntime cainRuntime = new Act1CainRuntime();
  private final IntSet completedCountesses = new IntSet();
  private final IntSet completedAndariels = new IntSet();
  private final IntSet completedBloodRavens = new IntSet();
  private KillCreditResolver killCredits;

  @Override
  protected void initialize() {
    monstersByZone = world.getAspectSubscriptionManager().get(
        Aspect.all(Monster.class, MapWrapper.class));
    playersByZone = world.getAspectSubscriptionManager().get(
        Aspect.all(Player.class, MapWrapper.class));
    killCredits = new KillCreditResolver(
        mPlayer, mMercenary, mSummonedPet, mMapWrapper, null, partyManager);
  }

  @Subscribe
  public void onZoneChanged(ZoneChangeEvent event) {
    if (event == null || event.zone == null || !mPlayer.has(event.entityId)) return;
    Player player = mPlayer.get(event.entityId);
    if (player.data == null || event.zone.level == null) return;

    int levelId = event.zone.level.Id;
    if (levelId == D2LevelIds.LEVEL_ROGUEENCAMPMENT) return;
    updateBloodRavenRecord(player.data, Act1BloodRavenQuest::leaveTown, "left-town");
    if (levelId == D2LevelIds.LEVEL_BURIALGROUNDS) {
      updateBloodRavenRecord(player.data, Act1BloodRavenQuest::enterBurialGrounds,
          "entered-burial-grounds");
    }
    updateRecord(player.data, Act1DenOfEvilQuest::leaveTown, "left-town");
    if (levelId == D2LevelIds.LEVEL_DENOFEVIL) {
      updateRecord(player.data, Act1DenOfEvilQuest::enterDen, "entered-den");
    }
    if (levelId == D2LevelIds.LEVEL_FORGOTTENTOWER) {
      updateCountessRecord(player.data, Act1CountessQuest::enterForgottenTower,
          "entered-forgotten-tower");
    } else if (levelId >= D2LevelIds.LEVEL_TOWERCELLARLVL1
        && levelId < D2LevelIds.LEVEL_TOWERCELLARLVL5) {
      updateCountessRecord(player.data, Act1CountessQuest::enterCellar,
          "entered-tower-cellar");
    } else if (levelId == D2LevelIds.LEVEL_TOWERCELLARLVL5) {
      updateCountessRecord(player.data, Act1CountessQuest::enterCountessLevel,
          "entered-countess-level");
    }
    if (levelId >= D2LevelIds.LEVEL_CATACOMBSLVL1
        && levelId <= D2LevelIds.LEVEL_CATACOMBSLVL4) {
      if (levelId == D2LevelIds.LEVEL_CATACOMBSLVL4) {
        updateAndarielRecord(player.data, Act1AndarielQuest::enterCatacombs,
            "entered-andariel-level");
      } else {
        updateAndarielRecord(player.data, Act1AndarielQuest::leaveTown,
            "entered-catacombs");
      }
    }
  }

  @Subscribe
  public void onMonsterKilled(DeathEvent event) {
    if (isAndariel(event == null ? -1 : event.victim)) {
      completeAndariel(event);
      return;
    }
    if (isCountess(event == null ? -1 : event.victim)) {
      completeCountess(event);
      return;
    }
    if (isBloodRaven(event == null ? -1 : event.victim)) {
      propagateBloodRavenDeath(event.victim);
      return;
    }
    if (event == null || !isMonsterInLevel(event.victim, D2LevelIds.LEVEL_DENOFEVIL)) return;
    // DeathEvent is dispatched before the death animation installs Corpse.
    // Exclude this victim explicitly, while deriving every other monster's
    // state from its current corpse/life components. A Fallen revived by a
    // shaman therefore becomes part of the count again.
    int remaining = countLivingMonsters(D2LevelIds.LEVEL_DENOFEVIL, event.victim);
    log.info("[A1Q1] Den monster killed: killer={} victim={} remaining={}",
        event.killer, event.victim, remaining);
    if (remaining != 0) return;
    int owner = killCredits == null ? event.killer : killCredits.ownerOf(event.killer);
    if (owner < 0) return;
    com.badlogic.gdx.utils.IntArray credits = killCredits.eligiblePlayers(owner,
        D2LevelIds.LEVEL_DENOFEVIL, playersByZone);
    for (int i = 0; i < credits.size; i++) {
      Player player = mPlayer.get(credits.get(i));
      if (player != null && player.data != null) {
        updateRecord(player.data, Act1DenOfEvilQuest::completeObjective,
            credits.get(i) == owner ? "objective-complete" : "eligible-party-member");
      }
    }
  }

  @Subscribe
  public void onNpcQuestMessage(NpcQuestMessageEvent event) {
    if (event == null || !mPlayer.has(event.entityId) || !mMonster.has(event.npcId)) return;
    Monster npc = mMonster.get(event.npcId);
    if (npc.monstats == null) return;
    Player player = mPlayer.get(event.entityId);
    if (player.data == null) return;

    if (npc.monstats.hcIdx == MonsterType.KASHYA) {
      onKashyaMessage(event, player);
      return;
    }
    if (npc.monstats.hcIdx == MonsterType.CHARSI) {
      onCharsiMessage(event, player);
      return;
    }
    if (npc.monstats.hcIdx == MonsterType.DECKARDCAIN_TOWN) {
      onCainTownMessage(event, player);
      return;
    }
    if (npc.monstats.hcIdx == MonsterType.DECKARDCAIN) {
      if (event.messageIndex == Act1CainQuest.MESSAGE_INIT) {
        updateAndarielRecord(player.data, Act1AndarielQuest::start, "cain-act1q6-init");
      }
      return;
    }
    if (npc.monstats.hcIdx == MonsterType.WARRIV) {
      onWarrivMessage(event, player);
      return;
    }
    if (npc.monstats.hcIdx != MonsterType.AKARA) return;

    if (event.messageIndex == Act1CainQuest.MESSAGE_DECIPHER_SCROLL) {
      decipherInifussScroll(event.entityId, player);
      return;
    }

    if (event.messageIndex == Act1CainQuest.MESSAGE_REWARD) {
      claimCainReward(event.entityId, player);
      return;
    }

    if (event.messageIndex == Act1DenOfEvilQuest.MESSAGE_INIT) {
      updateRecord(player.data, Act1DenOfEvilQuest::start, "akara-init-message");
    } else if (event.messageIndex == Act1CainQuest.MESSAGE_INIT) {
      updateCainRecord(player.data, Act1CainQuest::start, "akara-act1q4-init");
    } else if (event.messageIndex == Act1DenOfEvilQuest.MESSAGE_SUCCESS) {
      short record = getRecord(player.data);
      if (!Act1DenOfEvilQuest.canClaimReward(record)) return;
      short claimed = Act1DenOfEvilQuest.claimReward(record);
      if (claimed != record) {
        setRecord(player.data, claimed);
        grantSkillPoint(event.entityId, player.data);
        persist(player.data);
        log.info("[A1Q1] Akara reward granted: player={}", event.entityId);
      }
    }
  }

  private void onKashyaMessage(NpcQuestMessageEvent message, Player player) {
    if (message.messageIndex == Act1BloodRavenQuest.MESSAGE_INIT) {
      updateBloodRavenRecord(player.data, Act1BloodRavenQuest::start,
          "kashya-init-message");
      return;
    }
    if (message.messageIndex != Act1BloodRavenQuest.MESSAGE_REWARD) return;
    short record = getBloodRavenRecord(player.data);
    if (!Act1BloodRavenQuest.canClaimReward(record)) return;
    event.dispatch(NativeQuestRewardEvent.available(message.entityId,
        QuestId.A1Q2_BLOOD_RAVEN, NativeQuestRewardEvent.BLOOD_RAVEN_FREE_ROGUE));
    log.info("[A1Q2] Kashya free Rogue reward requested: player={}", message.entityId);
  }

  /** Cain's town greeting updates native dialogue state but does not award the ring. */
  private void onCainTownMessage(NpcQuestMessageEvent message, Player player) {
    if (message.messageIndex != Act1CainQuest.MESSAGE_CAIN_TOWN) return;
    short record = getCainRecord(player.data);
    if (Act1CainQuest.isFinished(record)) {
      log.debug("[A1Q4] Cain town message acknowledged: player={}, record=0x{}",
          message.entityId, Integer.toHexString(Short.toUnsignedInt(record)));
    } else {
      log.debug("[A1Q4] Cain town message ignored before rescue: player={}, record=0x{}",
          message.entityId, Integer.toHexString(Short.toUnsignedInt(record)));
    }
  }

  private void onWarrivMessage(NpcQuestMessageEvent message, Player player) {
    if (message.messageIndex != Act1AndarielQuest.MESSAGE_WARRIV_REWARD) return;
    short record = getAndarielRecord(player.data);
    if (!NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)
        || !NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE)
        || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)) return;

    short claimed = Act1AndarielQuest.claimReward(record);
    setAndarielRecord(player.data, claimed);
    persist(player.data);
    NativeActTransitionEvent transition = NativeActTransitionEvent.obtain(
        message.entityId, D2LevelIds.LEVEL_LUTGHOLEIN);
    event.dispatch(transition);
    log.info("[A1Q6] Warriv reward claimed: player={} destination={} accepted={}",
        message.entityId, transition.destinationLevelId, transition.accepted);
  }

  /** Creates and places the native ring before committing REWARD_GRANTED. */
  private void claimCainReward(int playerId, Player player) {
    CharData data = player.data;
    short record = getCainRecord(data);
    if (!NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)
        || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)) return;
    if (itemGenerator == null) {
      log.warn("[A1Q4] Cain reward unavailable: item generator is not wired, player={}", playerId);
      return;
    }

    Act1CainQuest.RewardSpec spec;
    try {
      spec = Act1CainQuest.rewardSpec(data.diff);
    } catch (IllegalArgumentException e) {
      log.warn("[A1Q4] Cain reward unavailable: unsupported difficulty={}, player={}", data.diff, playerId);
      return;
    }

    final Item reward;
    try {
      int id = data.mapSeed + data.getItems().getItems().size + 1;
      reward = itemGenerator.generateQuestReward(spec.code, spec.itemLevel, spec.quality, id);
    } catch (Throwable t) {
      log.error("[A1Q4] failed to create Cain reward: player={}, code={}", playerId, spec.code, t);
      return;
    }
    if (!data.getItems().addToInventory(reward)) {
      log.warn("[A1Q4] Cain reward inventory full; keeping pending: player={}", playerId);
      return;
    }

    short claimed = Act1CainQuest.claimReward(record);
    setCainRecord(data, claimed);
    persist(data);
    log.info("[A1Q4] Cain ring reward granted: player={}, code={}, ilvl={}, quality={}, record=0x{}",
        playerId, spec.code, spec.itemLevel, spec.quality,
        Integer.toHexString(Short.toUnsignedInt(claimed)));
  }

  private void onCharsiMessage(NpcQuestMessageEvent message, Player player) {
    CharData data = player.data;
    short record = getMalusRecord(data);
    if (message.messageIndex == Act1MalusQuest.MESSAGE_INIT) {
      setMalusRecord(data, Act1MalusQuest.start(record));
      persist(data);
      log.info("[A1Q3] Charsi started Horadric Malus quest: player={}", message.entityId);
      return;
    }
    boolean hasMalus = data.getItems().containsItemCode(Act1MalusQuest.MALUS_CODE);
    if (message.messageIndex != Act1MalusQuest.MESSAGE_MALUS
        || !Act1MalusQuest.canTurnIn(record, level(message.entityId, player), hasMalus)) {
      return;
    }
    if (!data.getItems().removeItemCode(Act1MalusQuest.MALUS_CODE)) return;
    short pending = Act1MalusQuest.completeObjective(record);
    setMalusRecord(data, pending);
    persist(data);
    event.dispatch(NativeQuestRewardEvent.available(message.entityId,
        QuestId.A1Q3_MALUS, NativeQuestRewardEvent.CHARSI_IMBUE));
    log.info("[A1Q3] Charsi accepted Malus: player={}, rewardPending={}",
        message.entityId,
        NativeQuestRecord.has(pending, NativeQuestRecord.REWARD_PENDING));
  }

  @Subscribe
  public void onNativeQuestReward(NativeQuestRewardEvent reward) {
    if (reward == null || reward.phase != NativeQuestRewardEvent.GRANTED
        || !mPlayer.has(reward.playerId)) return;
    Player player = mPlayer.get(reward.playerId);
    if (player.data == null) return;
    if (reward.questId == QuestId.A1Q2_BLOOD_RAVEN
        && reward.rewardKind == NativeQuestRewardEvent.BLOOD_RAVEN_FREE_ROGUE) {
      updateBloodRavenRecord(player.data, Act1BloodRavenQuest::claimReward,
          "free-rogue-granted");
    } else if (reward.questId == QuestId.A1Q3_MALUS
        && reward.rewardKind == NativeQuestRewardEvent.CHARSI_IMBUE) {
      updateMalusRecord(player.data, Act1MalusQuest::claimReward, "charsi-imbue-granted");
    }
  }

  @Subscribe
  public void onQuestObjectInteraction(QuestObjectInteractionEvent event) {
    if (event == null
        || (event.type != NativeQuestObjectResolver.Type.TOWER_TOME
            && event.type != NativeQuestObjectResolver.Type.HORADRIC_MALUS
            && event.type != NativeQuestObjectResolver.Type.CAIRN_STONE
            && event.type != NativeQuestObjectResolver.Type.INIFUSS_TREE
            && event.type != NativeQuestObjectResolver.Type.CAIN_GIBBET)
        || !mPlayer.has(event.playerId)) return;
    Player player = mPlayer.get(event.playerId);
    if (player.data == null) return;
    if (event.type == NativeQuestObjectResolver.Type.TOWER_TOME) {
      updateCountessRecord(player.data, Act1CountessQuest::discover,
          "tower-tome-message-127");
      event.accept();
      return;
    }
    if (event.type == NativeQuestObjectResolver.Type.CAIRN_STONE
        || event.type == NativeQuestObjectResolver.Type.INIFUSS_TREE
        || event.type == NativeQuestObjectResolver.Type.CAIN_GIBBET) {
      handleCainObject(event, player);
      return;
    }
    short record = getMalusRecord(player.data);
    if (!Act1MalusQuest.canOpenMalus(record, level(event.playerId, player))) return;
    setMalusRecord(player.data, Act1MalusQuest.leaveTown(record));
    persist(player.data);
    event.accept();
    log.info("[A1Q3] Malus stand accepted: player={} record=0x{}",
        event.playerId, Integer.toHexString(Short.toUnsignedInt(getMalusRecord(player.data))));
  }

  private void handleCainObject(QuestObjectInteractionEvent interaction, Player player) {
    short record = player.data.getQuests(Riiablo.ACT1)[Act1CainQuest.RECORD];
    short next;
    NativeCainQuestEvent request;
    if (interaction.type == NativeQuestObjectResolver.Type.CAIRN_STONE) {
      if (!player.data.getItems().containsItemCode(Act1CainQuest.DECIPHERED_SCROLL_CODE)) return;
      MapWrapper wrapper = mMapWrapper.get(interaction.entityId);
      long gameSeed = wrapper == null || wrapper.map == null ? 0L : wrapper.map.seed();
      cainRuntime.initialize(gameSeed);
      Act1CainRuntime.StoneResult result = cainRuntime.inspect(interaction.objectClassId);
      if (result == Act1CainRuntime.StoneResult.WRONG
          || result == Act1CainRuntime.StoneResult.COMPLETE) {
        log.info("[A1Q4] Cairn stone ignored: player={}, object={}, operated={}, expected={}",
            interaction.playerId, interaction.objectClassId, cainRuntime.operated(),
            expectedCainStone());
        return;
      }

      request = NativeCainQuestEvent.obtain(interaction.playerId, interaction.entityId,
          interaction.objectClassId, NativeCainQuestEvent.CAIRN_STONE);
      request.stoneObjectId = interaction.objectClassId;
      request.stoneIndex = cainRuntime.operated();
      if (result == Act1CainRuntime.StoneResult.LAST_STONE) {
        NativeCainQuestEvent portal = NativeCainQuestEvent.obtain(interaction.playerId,
            interaction.entityId, interaction.objectClassId,
            NativeCainQuestEvent.PORTAL_TO_TRISTRAM);
        portal.stoneObjectId = interaction.objectClassId;
        portal.stoneIndex = cainRuntime.operated();
        portal.destinationLevelId = D2LevelIds.LEVEL_TRISTRAM;
        event.dispatch(portal);
        if (!portal.accepted
            || !player.data.getItems().removeItemCode(Act1CainQuest.DECIPHERED_SCROLL_CODE)) {
          log.warn("[A1Q4] final Cairn stone waiting for portal service: player={}, object={}",
              interaction.playerId, interaction.objectClassId);
          return;
        }
        cainRuntime.markPortalOpened();
        next = Act1CainQuest.openTristramPortal(record);
      } else {
        cainRuntime.advance();
        next = Act1CainQuest.enterDarkWood(record);
      }
      request.accept();
    } else if (interaction.type == NativeQuestObjectResolver.Type.INIFUSS_TREE) {
      if (Act1CainQuest.isFinished(record)
          || player.data.getItems().containsItemCode(Act1CainQuest.BARK_SCROLL_CODE)
          || player.data.getItems().containsItemCode(Act1CainQuest.DECIPHERED_SCROLL_CODE)) return;
      next = Act1CainQuest.acquireScroll(record);
      request = NativeCainQuestEvent.obtain(interaction.playerId, interaction.entityId,
          interaction.objectClassId, NativeCainQuestEvent.INIFUSS_TREE);
    } else {
      if (!NativeQuestRecord.has(record, NativeQuestRecord.ENTERED_AREA)
          || !isPlayerInLevel(interaction.playerId, D2LevelIds.LEVEL_TRISTRAM)) return;
      next = Act1CainQuest.releaseCain(record);
      request = NativeCainQuestEvent.obtain(interaction.playerId, interaction.entityId,
          interaction.objectClassId, NativeCainQuestEvent.CAIN_GIBBET);
    }
    event.dispatch(request);
    if (!request.accepted) return;
    if (request.action == NativeCainQuestEvent.CAIN_GIBBET) {
      cainRuntime.markCainReleased();
      propagateCainRelease(interaction.playerId);
    }
    player.data.getQuests(Riiablo.ACT1)[Act1CainQuest.RECORD] = next;
    persist(player.data);
    interaction.accepted = request.accepted;
    log.info("[A1Q4] object action: player={}, object={}, action={}, accepted={}, record=0x{}",
        interaction.playerId, interaction.objectClassId, request.action, request.accepted,
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  @Subscribe
  public void onQuestItemPickedUp(QuestItemPickedUpEvent event) {
    if (event == null || !mPlayer.has(event.playerId)) return;
    Player player = mPlayer.get(event.playerId);
    if (player.data == null) return;
    if (Act1MalusQuest.MALUS_CODE.equalsIgnoreCase(event.itemCode)) {
      updateMalusRecord(player.data, Act1MalusQuest::markMalusPickedUp, "malus-picked-up");
    } else if (Act1CainQuest.BARK_SCROLL_CODE.equalsIgnoreCase(event.itemCode)
        || Act1CainQuest.DECIPHERED_SCROLL_CODE.equalsIgnoreCase(event.itemCode)) {
      updateCainRecord(player.data, Act1CainQuest::acquireScroll, "inifuss-scroll-picked-up");
    }
  }

  private void decipherInifussScroll(int playerId, Player player) {
    short record = getCainRecord(player.data);
    boolean hasBark = player.data.getItems().containsItemCode(Act1CainQuest.BARK_SCROLL_CODE);
    boolean hasKey = player.data.getItems().containsItemCode(Act1CainQuest.DECIPHERED_SCROLL_CODE);
    if (!Act1CainQuest.canDecipherScroll(record, hasBark, hasKey)) return;
    if (itemGenerator == null) {
      log.warn("[A1Q4] cannot decipher Scroll of Inifuss: item generator unavailable, player={}",
          playerId);
      return;
    }

    final Item deciphered;
    try {
      deciphered = itemGenerator.generate(Act1CainQuest.DECIPHERED_SCROLL_CODE);
      deciphered.version = Item.VERSION_110;
      deciphered.ilvl = 1;
      deciphered.quality = Quality.NORMAL;
      deciphered.flags |= Item.ITEMFLAG_IDENTIFIED;
      deciphered.attrs.base().put(Stat.questitemdifficulty,
          Math.max(0, Math.min(player.data.diff, 2)));
      deciphered.attrs.reset();
    } catch (Throwable t) {
      log.error("[A1Q4] failed to create deciphered Scroll of Inifuss: player={}", playerId, t);
      return;
    }

    if (!player.data.getItems().replaceItemCode(Act1CainQuest.BARK_SCROLL_CODE, deciphered)) {
      log.warn("[A1Q4] bark scroll disappeared before conversion: player={}", playerId);
      return;
    }
    setCainRecord(player.data, Act1CainQuest.acquireScroll(record));
    persist(player.data);
    log.info("[A1Q4] Akara deciphered Scroll of Inifuss: player={}", playerId);
  }

  private int expectedCainStone() {
    int[] order = cainRuntime.stoneOrder();
    int operated = cainRuntime.operated();
    return operated >= 0 && operated < order.length ? order[operated] : -1;
  }

  int countLivingMonsters(int levelId) {
    return countLivingMonsters(levelId, -1);
  }

  int countLivingMonsters(int levelId, int excludedEntityId) {
    IntBag entities = monstersByZone.getEntities();
    int[] ids = entities.getData();
    int remaining = 0;
    for (int i = 0, s = entities.size(); i < s; i++) {
      int entityId = ids[i];
      if (entityId != excludedEntityId
          && isMonsterInLevel(entityId, levelId)
          && isAlive(entityId)) {
        remaining++;
      }
    }
    return remaining;
  }

  private boolean isAlive(int entityId) {
    if (mCorpse.has(entityId)) return false;
    if (!mAttributesWrapper.has(entityId)) return true;
    Attributes attrs = mAttributesWrapper.get(entityId).attrs;
    if (attrs == null) return true;
    StatRef hitpoints = attrs.get(Stat.hitpoints, StatRef.obtain());
    return hitpoints == null || hitpoints.asFixed() > 0f;
  }

  private boolean isMonsterInLevel(int entityId, int levelId) {
    if (entityId < 0 || !mMonster.has(entityId) || !mMapWrapper.has(entityId)) return false;
    MapWrapper wrapper = mMapWrapper.get(entityId);
    Map.Zone zone = wrapper.zone;
    return zone != null && zone.level != null && zone.level.Id == levelId;
  }

  private boolean isPlayerInLevel(int entityId, int levelId) {
    if (!mMapWrapper.has(entityId)) return false;
    MapWrapper wrapper = mMapWrapper.get(entityId);
    return wrapper != null && wrapper.zone != null && wrapper.zone.level != null
        && wrapper.zone.level.Id == levelId;
  }

  private boolean isBloodRaven(int entityId) {
    if (entityId < 0 || !mMonster.has(entityId)
        || !isMonsterInLevel(entityId, D2LevelIds.LEVEL_BURIALGROUNDS)) return false;
    Monster monster = mMonster.get(entityId);
    return monster.monstats != null && monster.monstats.hcIdx == MonsterType.BLOODRAVEN;
  }

  private boolean isCountess(int entityId) {
    return entityId >= 0 && mSuperUnique.has(entityId)
        && mSuperUnique.get(entityId).id == D2SuperUniques.SUPERUNIQUE_THE_COUNTESS
        && isMonsterInLevel(entityId, D2LevelIds.LEVEL_TOWERCELLARLVL5);
  }

  private boolean isAndariel(int entityId) {
    return entityId >= 0 && mMonster.has(entityId) && mMapWrapper.has(entityId)
        && mMonster.get(entityId).monstats != null
        && mMonster.get(entityId).monstats.hcIdx == MonsterType.ANDARIEL
        && isMonsterInLevel(entityId, D2LevelIds.LEVEL_CATACOMBSLVL4);
  }

  private void completeAndariel(DeathEvent death) {
    if (death == null || !completedAndariels.add(death.victim)) {
      if (death != null) log.warn("[A1Q6] Duplicate Andariel death ignored: victim={}", death.victim);
      return;
    }
    if (playersByZone == null) return;
    IntBag players = playersByZone.getEntities();
    int[] ids = players.getData();
    IntSet eligibleParties = new IntSet();
    for (int i = 0, size = players.size(); i < size; i++) {
      int playerId = ids[i];
      if (!isPlayerInLevel(playerId, D2LevelIds.LEVEL_CATACOMBSLVL4)) continue;
      updateAndarielRecord(mPlayer.get(playerId).data, Act1AndarielQuest::completePending,
          "andariel-room");
      if (partyManager != null) {
        short party = partyManager.getPartyId(playerId);
        if (party != Party.INVALID_ID) eligibleParties.add(party);
      }
    }
    for (int i = 0, size = players.size(); i < size; i++) {
      int playerId = ids[i];
      if (!isPlayerInAct1OutsideTown(playerId)) continue;
      if (partyManager != null && eligibleParties.contains(partyManager.getPartyId(playerId))) {
        updateAndarielRecord(mPlayer.get(playerId).data, Act1AndarielQuest::completePending,
            "eligible-party-member");
      } else {
        updateAndarielRecord(mPlayer.get(playerId).data, Act1AndarielQuest::markCompletedNow,
            "andariel-died-this-game");
      }
    }
    log.info("[A1Q6] Andariel killed: victim={} killer={} players={}",
        death.victim, death.killer, players.size());
  }

  /** Mirrors A1Q5's room grant, Act I party propagation and completion flag. */
  private void completeCountess(DeathEvent death) {
    if (!completedCountesses.add(death.victim)) {
      log.warn("[A1Q5] Duplicate Countess death ignored: victim={} killer={}",
          death.victim, death.killer);
      return;
    }
    if (playersByZone == null) return;
    IntBag players = playersByZone.getEntities();
    int[] ids = players.getData();
    IntSet eligibleParties = new IntSet();
    int rewardPlayer = mPlayer.has(death.killer) ? death.killer : -1;

    for (int i = 0, size = players.size(); i < size; i++) {
      int playerId = ids[i];
      if (!isPlayerInLevel(playerId, D2LevelIds.LEVEL_TOWERCELLARLVL5)) continue;
      completeCountessFor(playerId, true, "countess-room");
      if (rewardPlayer < 0) rewardPlayer = playerId;
      if (partyManager != null) {
        short partyId = partyManager.getPartyId(playerId);
        if (partyId != Party.INVALID_ID) eligibleParties.add(partyId);
      }
    }

    if (partyManager != null && eligibleParties.size > 0) {
      for (int i = 0, size = players.size(); i < size; i++) {
        int playerId = ids[i];
        short partyId = partyManager.getPartyId(playerId);
        if (partyId != Party.INVALID_ID && eligibleParties.contains(partyId)
            && isPlayerInAct1OutsideTown(playerId)) {
          completeCountessFor(playerId, false, "eligible-party-member");
        }
      }
    }

    for (int i = 0, size = players.size(); i < size; i++) {
      int playerId = ids[i];
      Player player = mPlayer.get(playerId);
      if (player == null || player.data == null) continue;
      updateCountessRecord(player.data, Act1CountessQuest::markCompletedNow,
          "countess-died-this-game");
    }

    int difficulty = Riiablo.NORMAL;
    if (rewardPlayer >= 0 && mPlayer.has(rewardPlayer)
        && mPlayer.get(rewardPlayer).data != null) {
      difficulty = Math.max(0, Math.min(mPlayer.get(rewardPlayer).data.diff, 2));
    }
    event.dispatch(NativeCountessQuestEvent.obtain(
        rewardPlayer, death.victim, difficulty));
    log.info("[A1Q5] Countess killed: victim={} killer={} rewardPlayer={} players={}",
        death.victim, death.killer, rewardPlayer, players.size());
  }

  private void completeCountessFor(int playerId, boolean completedNow, String reason) {
    if (!mPlayer.has(playerId)) return;
    Player player = mPlayer.get(playerId);
    if (player == null || player.data == null) return;
    updateCountessRecord(player.data,
        record -> Act1CountessQuest.complete(record, completedNow), reason);
  }

  /**
   * D2MOO grants nearby players first, then their party members in Act 1.
   * MapWrapper currently exposes level zones rather than native DRLG rooms,
   * so all players in the Burial Grounds are treated as same/adjacent-room.
   */
  private void propagateBloodRavenDeath(int victimId) {
    if (!completedBloodRavens.add(victimId)) {
      log.warn("[A1Q2] Duplicate Blood Raven death ignored: victim={}", victimId);
      return;
    }
    if (playersByZone == null) return;
    IntBag players = playersByZone.getEntities();
    int[] ids = players.getData();
    IntSet eligibleParties = new IntSet();

    for (int i = 0, size = players.size(); i < size; i++) {
      int playerId = ids[i];
      if (!isPlayerInLevel(playerId, D2LevelIds.LEVEL_BURIALGROUNDS)) continue;
      completeBloodRavenFor(playerId, "near-blood-raven");
      if (partyManager != null) {
        short partyId = partyManager.getPartyId(playerId);
        if (partyId != Party.INVALID_ID) eligibleParties.add(partyId);
      }
    }

    if (partyManager != null && eligibleParties.size > 0) {
      for (int i = 0, size = players.size(); i < size; i++) {
        int playerId = ids[i];
        short partyId = partyManager.getPartyId(playerId);
        if (partyId == Party.INVALID_ID || !eligibleParties.contains(partyId)
            || !isPlayerInAct1OutsideTown(playerId)) continue;
        markBloodRavenPartyMember(playerId);
      }
    }

    for (int i = 0, size = players.size(); i < size; i++) {
      int playerId = ids[i];
      Player player = mPlayer.get(playerId);
      if (player == null || player.data == null) continue;
      updateBloodRavenRecord(player.data, Act1BloodRavenQuest::markCompletedNow,
          "blood-raven-died-this-game");
    }
    log.info("[A1Q2] Blood Raven killed: victim={}, players={}, partyService={}",
        victimId, players.size(), partyManager != null);
  }

  private void completeBloodRavenFor(int playerId, String reason) {
    if (!mPlayer.has(playerId)) return;
    Player player = mPlayer.get(playerId);
    if (player == null || player.data == null) return;
    updateBloodRavenRecord(player.data, Act1BloodRavenQuest::completeObjective, reason);
  }

  /** Native party propagation sets the primary-goal bit; reward pending is
   * granted later when that member enters the Blood Moor/Burial Grounds room. */
  private void markBloodRavenPartyMember(int playerId) {
    if (!mPlayer.has(playerId)) return;
    Player player = mPlayer.get(playerId);
    if (player == null || player.data == null) return;
    updateBloodRavenRecord(player.data, record ->
        NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
            || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)
                ? record : NativeQuestRecord.set(record, NativeQuestRecord.PRIMARY_GOAL_DONE),
        "eligible-party-member");
  }

  private boolean isPlayerInAct1OutsideTown(int playerId) {
    if (!mMapWrapper.has(playerId)) return false;
    MapWrapper wrapper = mMapWrapper.get(playerId);
    if (wrapper == null || wrapper.zone == null || wrapper.zone.level == null) return false;
    int levelId = wrapper.zone.level.Id;
    return levelId > D2LevelIds.LEVEL_ROGUEENCAMPMENT && levelId < D2LevelIds.LEVEL_LUTGHOLEIN;
  }

  private void propagateCainRelease(int rescuerId) {
    if (playersByZone == null) return;
    IntBag entities = playersByZone.getEntities();
    int[] ids = entities.getData();
    for (int i = 0, size = entities.size(); i < size; i++) {
      int entityId = ids[i];
      if (entityId == rescuerId
          || !isPlayerInLevel(entityId, D2LevelIds.LEVEL_TRISTRAM)) continue;
      Player member = mPlayer.get(entityId);
      if (member == null || member.data == null) continue;
      short previous = getCainRecord(member.data);
      short next = Act1CainQuest.releaseCain(previous);
      if (previous == next) continue;
      setCainRecord(member.data, next);
      persist(member.data);
      log.info("[A1Q4] Cain release propagated: rescuer={}, player={}, record=0x{}",
          rescuerId, entityId, Integer.toHexString(Short.toUnsignedInt(next)));
    }
  }

  private void grantSkillPoint(int playerId, CharData data) {
    Attributes attrs = mAttributesWrapper.has(playerId)
        ? mAttributesWrapper.get(playerId).attrs : data.getStats();
    if (attrs == null) return;
    addOne(attrs.base());
    addOne(attrs.aggregate());
  }

  private static void addOne(StatListRef stats) {
    StatRef current = stats.get(Stat.newskills);
    stats.put(Stat.newskills, current == null ? 1 : current.asInt() + 1);
  }

  private interface RecordUpdate {
    short apply(short record);
  }

  private void updateRecord(CharData data, RecordUpdate update, String reason) {
    short previous = getRecord(data);
    short next = update.apply(previous);
    if (previous == next) return;
    setRecord(data, next);
    persist(data);
    log.info("[A1Q1] Quest record changed: character={} reason={} previous=0x{} next=0x{}",
        data.name, reason, Integer.toHexString(Short.toUnsignedInt(previous)),
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  private static short getRecord(CharData data) {
    return data.getQuests(Riiablo.ACT1)[Act1DenOfEvilQuest.RECORD];
  }

  private static short getMalusRecord(CharData data) {
    return data.getQuests(Riiablo.ACT1)[Act1MalusQuest.RECORD];
  }

  private static short getBloodRavenRecord(CharData data) {
    return data.getQuests(Riiablo.ACT1)[Act1BloodRavenQuest.RECORD];
  }

  private static void setBloodRavenRecord(CharData data, short record) {
    data.getQuests(Riiablo.ACT1)[Act1BloodRavenQuest.RECORD] = record;
  }

  private void updateBloodRavenRecord(CharData data, RecordUpdate update, String reason) {
    short previous = getBloodRavenRecord(data);
    short next = update.apply(previous);
    if (previous == next) return;
    setBloodRavenRecord(data, next);
    persist(data);
    log.info("[A1Q2] Quest record changed: character={} reason={} previous=0x{} next=0x{}",
        data.name, reason, Integer.toHexString(Short.toUnsignedInt(previous)),
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  private static short getCainRecord(CharData data) {
    return data.getQuests(Riiablo.ACT1)[Act1CainQuest.RECORD];
  }

  private static void setCainRecord(CharData data, short record) {
    data.getQuests(Riiablo.ACT1)[Act1CainQuest.RECORD] = record;
  }

  private void updateCainRecord(CharData data, RecordUpdate update, String reason) {
    short previous = getCainRecord(data);
    short next = update.apply(previous);
    if (previous == next) return;
    setCainRecord(data, next);
    persist(data);
    log.info("[A1Q4] Quest record changed: character={} reason={} previous=0x{} next=0x{}",
        data.name, reason, Integer.toHexString(Short.toUnsignedInt(previous)),
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  private static void setMalusRecord(CharData data, short record) {
    data.getQuests(Riiablo.ACT1)[Act1MalusQuest.RECORD] = record;
  }

  private static short getCountessRecord(CharData data) {
    return data.getQuests(Riiablo.ACT1)[Act1CountessQuest.RECORD];
  }

  private static short getAndarielRecord(CharData data) {
    return data.getQuests(Riiablo.ACT1)[Act1AndarielQuest.RECORD];
  }

  private static void setAndarielRecord(CharData data, short record) {
    data.getQuests(Riiablo.ACT1)[Act1AndarielQuest.RECORD] = record;
  }

  private void updateAndarielRecord(CharData data, RecordUpdate update, String reason) {
    short previous = getAndarielRecord(data);
    short next = update.apply(previous);
    if (previous == next) return;
    setAndarielRecord(data, next);
    persist(data);
    log.info("[A1Q6] Quest record changed: character={} reason={} previous=0x{} next=0x{}",
        data.name, reason, Integer.toHexString(Short.toUnsignedInt(previous)),
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  private static void setCountessRecord(CharData data, short record) {
    data.getQuests(Riiablo.ACT1)[Act1CountessQuest.RECORD] = record;
  }

  private void updateCountessRecord(CharData data, RecordUpdate update, String reason) {
    short previous = getCountessRecord(data);
    short next = update.apply(previous);
    if (previous == next) return;
    setCountessRecord(data, next);
    persist(data);
    log.info("[A1Q5] Quest record changed: character={} reason={} previous=0x{} next=0x{}",
        data.name, reason, Integer.toHexString(Short.toUnsignedInt(previous)),
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  private void updateMalusRecord(CharData data, RecordUpdate update, String reason) {
    short previous = getMalusRecord(data);
    short next = update.apply(previous);
    if (previous == next) return;
    setMalusRecord(data, next);
    persist(data);
    log.info("[A1Q3] Quest record changed: character={} reason={} previous=0x{} next=0x{}",
        data.name, reason, Integer.toHexString(Short.toUnsignedInt(previous)),
        Integer.toHexString(Short.toUnsignedInt(next)));
  }

  private int level(int playerId, Player player) {
    Attributes attrs = mAttributesWrapper.has(playerId)
        ? mAttributesWrapper.get(playerId).attrs : player.data.getStats();
    if (attrs == null) return 0;
    StatRef level = attrs.get(Stat.level, StatRef.obtain());
    return level == null ? 0 : level.asInt();
  }

  private static void setRecord(CharData data, short record) {
    data.getQuests(Riiablo.ACT1)[Act1DenOfEvilQuest.RECORD] = record;
  }

  private static void persist(CharData data) {
    if (data.managed && Riiablo.saves != null) D2SWriter.INSTANCE.save(data);
  }
}
