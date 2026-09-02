package com.riiablo.engine.server;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.math.MathUtils;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.MonsterRewardState;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.item.ItemQuality;
import com.riiablo.engine.server.item.LootManager;
import com.riiablo.engine.server.item.GroundDropOwnership;
import com.riiablo.engine.server.monster.MonsterRank;
import com.riiablo.engine.server.party.Party;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.engine.server.party.PartyMember;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.Quality;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/**
 * Applies server-authoritative rewards after a monster dies.
 *
 * <p>Death can be reported by both the melee and missile paths in the same
 * frame. Per-entity reward state makes processing idempotent without leaking
 * a numeric entity id into a later Artemis lifecycle.</p>
 */
public class DeathRewardSystem extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(DeathRewardSystem.class);

  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Mercenary> mMercenary;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<com.riiablo.engine.server.component.MapWrapper> mMapWrapper;
  protected ComponentMapper<com.riiablo.engine.server.component.Item> mGroundItem;
  protected ComponentMapper<com.riiablo.engine.server.component.SuperUnique> mSuperUnique;
  protected ComponentMapper<MonsterRewardState> mMonsterRewardState;
  protected ComponentMapper<NativeUnitFlags> mNativeUnitFlags;

  @Wire(name = "factory")
  protected EntityFactory factory;

  @Wire(name = "partyManager", failOnNull = false)
  protected PartyManager partyManager;

  /** ItemGenerator is registered as a world system by D2GS. */
  protected ItemGenerator itemGenerator;

  private final LootManager lootManager = new LootManager();
  private EntitySubscription players;
  private KillCreditResolver killCredits;

  @Override
  protected void initialize() {
    players = world.getAspectSubscriptionManager().get(Aspect.all(Player.class));
    killCredits = new KillCreditResolver(
        mPlayer, mMercenary, mMapWrapper, mPosition, partyManager);
  }

  @Subscribe
  public void onDeath(DeathEvent event) {
    if (event == null || event.victim < 0) return;
    if (!mMonster.has(event.victim)) return;
    // Hirelings retain Monster presentation data, but their death is a pet
    // lifecycle transition and must never roll hostile monster XP or loot.
    if (mMercenary.has(event.victim)) return;
    NativeUnitFlags unitFlags = mNativeUnitFlags.get(event.victim);
    if (unitFlags != null && unitFlags.has(NativeUnitFlags.NO_TREASURE_CLASS)) {
      log.debug("[DEATH_REWARD] UNITFLAG_NOTC suppresses treasure class: victim={} flags=0x{}",
          event.victim, Integer.toHexString(unitFlags.flags()));
      return;
    }
    MonsterRewardState rewards = mMonsterRewardState.has(event.victim)
        ? mMonsterRewardState.get(event.victim)
        : mMonsterRewardState.create(event.victim).reset();
    if (!rewards.claimTreasureClass()) {
      log.warn("[DEATH_REWARD] duplicate death ignored: killer={}, victim={}",
          event.killer, event.victim);
      return;
    }
    int ownerId = killCredits == null ? event.killer : killCredits.ownerOf(event.killer);
    if (ownerId < 0 || !mPlayer.has(ownerId) || mPlayer.get(ownerId).data == null) {
      log.debug("[DEATH_REWARD] skip unowned killer: killer={}, victim={}",
          event.killer, event.victim);
      return;
    }
    Monster monster = mMonster.get(event.victim);
    Player player = mPlayer.get(ownerId);
    Position position = mPosition.has(event.victim) ? mPosition.get(event.victim) : null;
    if (position == null) {
      log.warn("[DEATH_REWARD] victim has no position: killer={}, victim={}",
          event.killer, event.victim);
      return;
    }

    int difficulty = Math.max(0, Math.min(2, player.data.diff));
    Attributes victimAttrs = mAttributesWrapper.has(event.victim)
        ? mAttributesWrapper.get(event.victim).attrs : null;
    com.riiablo.attributes.StatRef levelStat = victimAttrs == null
        ? null : victimAttrs.get(Stat.level);
    if (levelStat == null) {
      log.error("[DEATH_REWARD] victim lacks authoritative level stat: victim={} monster={}",
          event.victim, monster.monstats == null ? "unknown" : monster.monstats.Id);
      return;
    }
    int monsterLevel = Math.max(1, levelStat.asInt());
    LootManager.LootConfig config = new LootManager.LootConfig();
    config.monsterLevel = monsterLevel;
    config.rngSeed = Riiablo.gameSeed ^ (event.victim * 0x45D9F3B);
    config.areaLevel = monsterLevel;
    config.difficulty = difficulty;
    config.isBoss = monster.rank == MonsterRank.BOSS || monster.monstats != null
        && (monster.monstats.boss || monster.monstats.primeevil);
    config.isSuperUnique = monster.rank == MonsterRank.SUPER_UNIQUE
        || mSuperUnique != null && mSuperUnique.has(event.victim);
    config.isElite = isEliteRank(monster.rank) || config.isBoss || config.isSuperUnique;
    config.noRatio = monster.monstats != null && monster.monstats.noRatio;
    // D2Game starts with the current connected-player count. The value stored
    // on the monster is only an upper bound captured at spawn time.
    config.playerCount = players == null ? 1 : Math.max(1, players.getEntities().size());
    config.partyMembersInLevel = partyMembersInLevel(ownerId);
    config.monsterPlayerCount = config.playerCount;
    if (mAttributesWrapper.has(event.victim) && mAttributesWrapper.get(event.victim).attrs != null) {
      com.riiablo.attributes.StatRef monsterPlayers =
          mAttributesWrapper.get(event.victim).attrs.get(Stat.monster_playercount);
      if (monsterPlayers != null) {
        config.monsterPlayerCount = Math.max(1, monsterPlayers.asInt());
      }
    }
    config.treasureClass = treasureClass(event.victim, monster.monstats, difficulty,
        monster.rank, config.isSuperUnique);
    if (mAttributesWrapper.has(ownerId)) {
      Attributes attrs = mAttributesWrapper.get(ownerId).attrs;
      lootManager.applyPlayerBonuses(attrs, config);
    }

    LootManager.LootResult result = lootManager.calculateLoot(config);
    int createdItems = 0;
    for (int i = 0; i < result.getItemCount(); i++) {
      String code = result.itemCodes.get(i);
      int quality = result.itemQualities.get(i);
      int itemLevel = result.itemLevels.get(i);
      int itemSeed = config.rngSeed ^ ((i + 1) * 0x9E3779B9);
      int itemId = createItem(code, quality, itemLevel, itemSeed, difficulty,
          position.position.x, position.position.y, ownerId);
      if (itemId >= 0) createdItems++;
      log.debug("[DEATH_REWARD] item: killer={}, victim={}, code={}, rolledQuality={}, "
              + "ilvl={}, entity={}", event.killer, event.victim, code, quality, itemLevel, itemId);
    }

    int goldEntity = createGold(result.goldAmount, monsterLevel,
        position.position.x, position.position.y, ownerId);
    log.info("[DEATH_REWARD] killer={}, victim={}, monster={}, rank={}, tc={}, level={}, "
            + "difficulty={}, players={}, partyInLevel={}, monsterPlayers={}, "
            + "effectivePlayers={}, boss={}, gold={}, goldEntity={}, itemsRolled={}, itemsCreated={}",
        ownerId, event.victim,
        monster.monstats != null ? monster.monstats.Id : "unknown", monster.rank,
        config.treasureClass, monsterLevel, difficulty,
        config.playerCount, config.partyMembersInLevel, config.monsterPlayerCount,
        new com.riiablo.item.TreasureClassResolver.PlayerContext(
            config.playerCount, config.partyMembersInLevel,
            config.monsterPlayerCount).effectivePlayerCount(),
        config.isBoss, result.goldAmount, goldEntity, result.getItemCount(), createdItems);
  }

  static boolean isEliteRank(int rank) {
    return rank == MonsterRank.CHAMPION || rank == MonsterRank.UNIQUE
        || rank == MonsterRank.SUPER_UNIQUE;
  }

  /** Selects the native MonStats TC column: normal, champion/unique or quest. */
  private String treasureClass(int victim, MonStats.Entry stats, int difficulty,
                               int rank, boolean superUnique) {
    if (superUnique && mSuperUnique != null && mSuperUnique.has(victim)
        && Riiablo.files != null && Riiablo.files.SuperUniques != null) {
      com.riiablo.codec.excel.SuperUniques.Entry entry =
          Riiablo.files.SuperUniques.get(mSuperUnique.get(victim).key);
      if (entry != null) {
        String tc = difficulty == 1 ? entry.TCNightmare : difficulty == 2 ? entry.TCHell : entry.TC;
        if (tc != null && !tc.trim().isEmpty()) return tc;
      }
    }
    if (stats == null) return null;
    String[] values = treasureClassColumn(stats, rank, superUnique);
    if (values == null || values.length == 0) return null;
    String tc = values[Math.min(Math.max(0, difficulty), values.length - 1)];
    return tc == null || tc.trim().isEmpty() ? null : tc;
  }

  static String[] treasureClassColumn(MonStats.Entry stats, int rank, boolean superUnique) {
    if (stats == null) return null;
    if (superUnique || rank == MonsterRank.UNIQUE || rank == MonsterRank.BOSS
        || rank == MonsterRank.SUPER_UNIQUE) return stats.TreasureClass3;
    if (rank == MonsterRank.CHAMPION) return stats.TreasureClass2;
    return stats.TreasureClass1;
  }

  private int createItem(String code, int rolledQuality, int itemLevel,
      int itemSeed, int difficulty, float x, float y, int ownerId) {
    if (factory == null || itemGenerator == null || code == null || code.isEmpty()) {
      log.warn("[DEATH_REWARD] item creation unavailable: factory={}, generator={}, code={}",
          factory != null, itemGenerator != null, code);
      return -1;
    }
    try {
      Quality quality = safeQuality(rolledQuality);
      Item item = itemGenerator.generateLootItem(code, itemLevel, quality,
          itemSeed, difficulty);
      int entityId = factory.createItem(item, x + MathUtils.random(-2f, 2f),
          y + MathUtils.random(-2f, 2f));
      markDrop(entityId, ownerId);
      GroundDropOwnership.register(entityId, ownerId, dropPartyId(ownerId),
          10_000L, 10_000L, "gld".equalsIgnoreCase(item.code));
      // Item ids are serialized from the same object held by the component.
      // The entity id is a stable server-side fallback when no item id service
      // is available yet.
      item.id = entityId;
      return entityId;
    } catch (Throwable t) {
      log.error("[DEATH_REWARD] item creation failed: code={}, quality={}, ilvl={}",
          code, rolledQuality, itemLevel, t);
      return -1;
    }
  }

  private int createGold(int amount, int itemLevel, float x, float y, int ownerId) {
    if (amount <= 0 || factory == null || itemGenerator == null) return -1;
    try {
      Item gold = itemGenerator.generate("gld");
      gold.ilvl = (byte) MathUtils.clamp(itemLevel, 1, 99);
      gold.quality = Quality.NORMAL;
      gold.flags |= Item.ITEMFLAG_IDENTIFIED;
      gold.attrs.base().put(Stat.quantity, amount);
      int entityId = factory.createItem(gold, x + MathUtils.random(-1f, 1f),
          y + MathUtils.random(-1f, 1f));
      markDrop(entityId, ownerId);
      GroundDropOwnership.register(entityId, ownerId, dropPartyId(ownerId),
          10_000L, 10_000L, true);
      gold.id = entityId;
      return entityId;
    } catch (Throwable t) {
      log.error("[DEATH_REWARD] gold creation failed: amount={}, level={}", amount, itemLevel, t);
      return -1;
    }
  }

  private void markDrop(int entityId, int ownerId) {
    if (entityId < 0 || mGroundItem == null || !mGroundItem.has(entityId)) return;
    com.riiablo.engine.server.component.Item item = mGroundItem.get(entityId);
    item.dropOwnerId = ownerId;
    item.dropOwnerUntilMillis = System.currentTimeMillis() + 10_000L;
  }

  private int dropPartyId(int ownerId) {
    return partyManager == null ? Party.INVALID_ID : partyManager.getPartyId(ownerId);
  }

  /** Counts living party members in the killer's current native level. */
  private int partyMembersInLevel(int killerId) {
    if (partyManager == null || killerId < 0) return 1;
    short partyId = partyManager.getPartyId(killerId);
    if (partyId == Party.INVALID_ID) return 1;
    Party party = partyManager.getParty(partyId);
    if (party == null) return 1;
    int killerLevelId = levelId(killerId);
    if (killerLevelId < 0) return 1;
    int count = 0;
    for (PartyMember member : party.getMembers()) {
      if (member == null || !member.online || !member.alive || !mPlayer.has(member.entityId)) continue;
      if (levelId(member.entityId) != killerLevelId) continue;
      count++;
    }
    return Math.max(1, count);
  }

  private int levelId(int entityId) {
    if (mMapWrapper == null || !mMapWrapper.has(entityId)) return -1;
    com.riiablo.engine.server.component.MapWrapper wrapper = mMapWrapper.get(entityId);
    return wrapper == null || wrapper.zone == null || wrapper.zone.level == null
        ? -1 : wrapper.zone.level.Id;
  }

  private Quality safeQuality(int rolledQuality) {
    switch (rolledQuality) {
      case ItemQuality.INFERIOR: return Quality.LOW;
      case ItemQuality.SUPERIOR: return Quality.HIGH;
      case ItemQuality.MAGIC: return Quality.MAGIC;
      case ItemQuality.NORMAL: return Quality.NORMAL;
      case ItemQuality.SET: return Quality.SET;
      case ItemQuality.RARE: return Quality.RARE;
      case ItemQuality.UNIQUE: return Quality.UNIQUE;
      case ItemQuality.CRAFT:
      case ItemQuality.TEMPERED:
        return Quality.MAGIC;
      default: return Quality.NORMAL;
    }
  }
}
