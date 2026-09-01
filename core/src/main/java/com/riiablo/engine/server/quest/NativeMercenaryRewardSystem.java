package com.riiablo.engine.server.quest;

import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.client.component.Selectable;
import com.riiablo.engine.server.component.AIWrapper;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.NativeQuestRewardEvent;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.engine.server.pet.MercenaryManager;
import com.riiablo.item.VendorPricing;
import com.badlogic.gdx.utils.Array;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/** Owns the entity-side transaction for native quest hireling rewards. */
@Wire(failOnNull = false)
public class NativeMercenaryRewardSystem extends PassiveSystem
    implements MercenaryManager.MercenaryCallback {
  private static final Logger log = LogManager.getLogger(NativeMercenaryRewardSystem.class);

  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<AIWrapper> mAIWrapper;
  protected ComponentMapper<Interactable> mInteractable;
  protected ComponentMapper<Selectable> mSelectable;
  protected ComponentMapper<Mercenary> mMercenary;
  protected EventSystem event;
  protected EntityFactory factory;

  private final MercenaryManager mercenaries;
  private com.riiablo.engine.server.NativeHirelingExperienceTable hirelingTable;

  public NativeMercenaryRewardSystem() {
    this(new MercenaryManager());
  }

  NativeMercenaryRewardSystem(MercenaryManager mercenaries) {
    this.mercenaries = mercenaries;
  }

  @Override
  protected void initialize() {
    // The D2GS registers EntityFactory as an injected interface. Artemis
    // system lookup is concrete-class based, so looking it up by this
    // interface returns null and used to overwrite the valid injection.
    EntityFactory systemFactory = world.getSystem(EntityFactory.class);
    if (systemFactory != null) factory = systemFactory;
    mercenaries.setCallback(this);
    hirelingTable = com.riiablo.engine.server.NativeHirelingExperienceTable.load();
  }

  @Subscribe
  public void onNativeQuestReward(NativeQuestRewardEvent reward) {
    if (reward == null || reward.phase != NativeQuestRewardEvent.AVAILABLE
        || reward.questId != QuestId.A1Q2_BLOOD_RAVEN
        || reward.rewardKind != NativeQuestRewardEvent.BLOOD_RAVEN_FREE_ROGUE) {
      return;
    }

    int playerLevel = getPlayerLevel(reward.playerId);
    if (!mercenaries.grantFreeRogue(reward.playerId, playerLevel)) {
      log.warn("[A1Q2] Free Rogue grant failed; quest remains pending: player={}",
          reward.playerId);
      return;
    }

    event.dispatch(NativeQuestRewardEvent.granted(reward.playerId,
        reward.questId, reward.rewardKind));
    log.info("[A1Q2] Free Rogue entity committed: player={} level={}",
        reward.playerId, playerLevel);
  }

  MercenaryManager mercenaries() {
    return mercenaries;
  }

  /** Hires the first available Act I Rogue through the same entity transaction as A1Q2. */
  public boolean hireAct1Rogue(int playerId) {
    if (!mPlayer.has(playerId) || mPlayer.get(playerId).data == null
        || mPlayer.get(playerId).data.hasMerc()) {
      log.warn("[ACT1_HIRE] player already owns a persisted mercenary: player={}", playerId);
      return false;
    }
    int level = getPlayerLevel(playerId);
    Array<MercenaryManager.AvailableMercenary> available = mercenaries.getAvailableMercenaries(
        MercenaryManager.NPC_KASHYA, level);
    for (int i = 0; i < available.size; i++) {
      if (!available.get(i).hired
          && mercenaries.hireMercenary(playerId, MercenaryManager.NPC_KASHYA, i)) {
        log.info("[ACT1_HIRE] paid Rogue hired: player={} slot={} level={}", playerId, i,
            available.get(i).level);
        return true;
      }
    }
    log.warn("[ACT1_HIRE] no affordable/available Rogue: player={} level={}", playerId, level);
    return false;
  }

  /** Test/integration entry that exercises the native free Rogue reward path. */
  public boolean grantFreeRogue(int playerId) {
    return mPlayer.has(playerId) && mercenaries.grantFreeRogue(playerId, getPlayerLevel(playerId));
  }

  /** Server-authoritative NPC resurrection transaction. */
  public boolean resurrectMercenary(int playerId) {
    return mPlayer.has(playerId) && mercenaries.resurrectMercenary(playerId);
  }

  public boolean hasDeadMercenary(int playerId) {
    MercenaryManager.ActiveMercenary merc = mercenaries.getPlayerMercenary(playerId);
    return merc != null && merc.state == MercenaryManager.STATE_DEAD;
  }

  public int mercenaryEntityId(int playerId) {
    MercenaryManager.ActiveMercenary merc = mercenaries.getPlayerMercenary(playerId);
    return merc == null ? Engine.INVALID_ENTITY : merc.entityId;
  }

  public int resurrectionCost(int playerId) {
    MercenaryManager.ActiveMercenary merc = mercenaries.getPlayerMercenary(playerId);
    return merc == null ? 0 : MercenaryManager.nativeResurrectionCost(merc.level);
  }

  public int mercenaryState(int playerId) {
    MercenaryManager.ActiveMercenary merc = mercenaries.getPlayerMercenary(playerId);
    return merc == null ? MercenaryManager.STATE_AVAILABLE : merc.state;
  }

  public int persistedMercenaryFlags(int playerId) {
    return mPlayer.has(playerId) && mPlayer.get(playerId).data != null
        ? mPlayer.get(playerId).data.getMerc().flags : 0;
  }

  /** Persists D2's dead-hireling flag as soon as authoritative life reaches zero. */
  @Subscribe
  public void onDeath(DeathEvent death) {
    if (death == null || death.victim < 0 || !mMercenary.has(death.victim)) return;
    Mercenary merc = mMercenary.get(death.victim);
    mercenaries.onMercenaryDeath(merc.ownerId);
    log.info("[MERC_LIFECYCLE] phase=death owner={} entity={} killer={}",
        merc.ownerId, death.victim, death.killer);
  }

  @Override
  public int createMercenaryEntity(int playerId, MercenaryManager.MercenaryDefinition def,
      int level, int seed, int nameId) {
    if (factory == null) {
      log.error("[A1Q2] Failed to create Rogue entity: entity factory unavailable");
      return Engine.INVALID_ENTITY;
    }
    if (!mPosition.has(playerId)) {
      log.error("[A1Q2] Failed to create Rogue entity: owner position missing player={}", playerId);
      return Engine.INVALID_ENTITY;
    }
    Vector2 owner = mPosition.get(playerId).position;
    int monsterId = monsterId(def.mercType);
    if (monsterId == Engine.INVALID_ENTITY) return Engine.INVALID_ENTITY;

    final int entityId;
    try {
      entityId = factory.createMonster(monsterId, owner.x + 1f, owner.y + 1f);
    } catch (Throwable t) {
      log.error("[A1Q2] Failed to create Rogue entity: player={}", playerId, t);
      return Engine.INVALID_ENTITY;
    }
    if (entityId == Engine.INVALID_ENTITY) return Engine.INVALID_ENTITY;

    // Hirelings use monster presentation data, but must never run hostile
    // monster AI or expose the hostile click target installed by that factory.
    Mercenary component = mMercenary.create(entityId)
        .set(playerId, def.mercType, level, seed, nameId);
    com.riiablo.engine.server.NativeHirelingExperienceTable.Stats nativeStats =
        hirelingTable == null ? null : hirelingTable.stats(def.mercType, level);
    if (mAttributesWrapper.has(entityId)) {
      com.riiablo.engine.server.NativeHirelingStatsUpdater.apply(
          mAttributesWrapper.get(entityId).attrs, nativeStats);
    }
    com.riiablo.engine.server.NativeHirelingStatsUpdater.applySkills(component, nativeStats);
    if (mAIWrapper.has(entityId)) mAIWrapper.remove(entityId);
    if (mInteractable.has(entityId)) mInteractable.remove(entityId);
    if (mSelectable.has(entityId)) mSelectable.remove(entityId);
    return entityId;
  }

  private static int monsterId(int mercType) {
    switch (mercType) {
      case MercenaryManager.MERC_TYPE_ROGUE: return MonsterType.HIRELING_ROGUE;
      case MercenaryManager.MERC_TYPE_DESERT: return MonsterType.HIRELING_DESERT;
      case MercenaryManager.MERC_TYPE_IRON_WOLF: return MonsterType.HIRELING_IRONWOLF;
      case MercenaryManager.MERC_TYPE_BARBARIAN: return MonsterType.HIRELING_BARBARIAN;
      default: return Engine.INVALID_ENTITY;
    }
  }

  @Override
  public void removeMercenaryEntity(int entityId) {
    if (entityId != Engine.INVALID_ENTITY) world.delete(entityId);
  }

  @Override
  public boolean resurrectMercenaryEntity(int entityId, int playerId) {
    if (factory == null || entityId == Engine.INVALID_ENTITY
        || !mMercenary.has(entityId) || mMercenary.get(entityId).ownerId != playerId) {
      return false;
    }
    if (!factory.resurrectMonster(entityId, playerId)) return false;
    // The generic monster resurrection helper reconstructs MonStats AI and
    // interaction components. Hirelings retain monster presentation only;
    // their friendly skill/follow systems remain authoritative.
    if (mAIWrapper.has(entityId)) mAIWrapper.remove(entityId);
    if (mInteractable.has(entityId)) mInteractable.remove(entityId);
    if (mSelectable.has(entityId)) mSelectable.remove(entityId);
    return true;
  }

  @Override
  public void onMercenaryHired(int playerId, MercenaryManager.ActiveMercenary merc) {
    if (!mPlayer.has(playerId) || mPlayer.get(playerId).data == null || merc == null) return;
    com.riiablo.save.CharData.MercData data = mPlayer.get(playerId).data.getMerc();
    data.seed = merc.seed;
    data.name = (short) merc.nameId;
    data.type = (short) merc.definition.mercType;
    long nativeXp = hirelingTable == null ? 0L
        : hirelingTable.thresholdForHireling(merc.definition.mercType, merc.level);
    if (nativeXp > 0L) merc.experience = nativeXp;
    data.xp = merc.experience;
    com.riiablo.engine.server.NativeHirelingStatsUpdater.apply(data.getStats(),
        hirelingTable == null ? null
            : hirelingTable.stats(merc.definition.mercType, merc.level));
    data.getStats().base().put(Stat.experience,
        (int) Math.min(Integer.MAX_VALUE, data.xp));
    data.getStats().aggregate().put(Stat.experience,
        (int) Math.min(Integer.MAX_VALUE, data.xp));
  }

  @Override
  public void onMercenaryDismissed(int playerId, MercenaryManager.ActiveMercenary merc) {
    if (!mPlayer.has(playerId) || mPlayer.get(playerId).data == null) return;
    com.riiablo.save.CharData.MercData data = mPlayer.get(playerId).data.getMerc();
    data.seed = 0;
    data.name = 0;
    data.type = 0;
    data.xp = 0;
  }
  @Override
  public void onMercenaryDeath(int playerId, MercenaryManager.ActiveMercenary merc) {
    if (!mPlayer.has(playerId) || mPlayer.get(playerId).data == null) return;
    mPlayer.get(playerId).data.getMerc().flags |= MercenaryManager.FLAG_DEAD;
  }

  @Override
  public void onMercenaryResurrected(int playerId, MercenaryManager.ActiveMercenary merc) {
    if (!mPlayer.has(playerId) || mPlayer.get(playerId).data == null) return;
    com.riiablo.save.CharData.MercData data = mPlayer.get(playerId).data.getMerc();
    data.flags &= ~MercenaryManager.FLAG_DEAD;
    StatRef maxhp = data.getStats().get(Stat.maxhp, StatRef.obtain());
    StatRef hitpoints = data.getStats().get(Stat.hitpoints, StatRef.obtain());
    if (hitpoints != null && maxhp != null) hitpoints.set(Math.max(1f, maxhp.asFixed()));
    log.info("[MERC_LIFECYCLE] phase=resurrect owner={} entity={} level={} cost={}",
        playerId, merc.entityId, merc.level,
        MercenaryManager.nativeResurrectionCost(merc.level));
  }
  @Override public void onMercenaryLevelUp(int playerId, MercenaryManager.ActiveMercenary merc,
      int oldLevel, int newLevel) {}

  @Override
  public int getPlayerGold(int playerId) {
    return mPlayer.has(playerId) && mPlayer.get(playerId).data != null
        ? VendorPricing.availableGold(mPlayer.get(playerId).data) : 0;
  }

  @Override
  public boolean deductPlayerGold(int playerId, int amount) {
    return mPlayer.has(playerId) && mPlayer.get(playerId).data != null
        && VendorPricing.chargeGold(mPlayer.get(playerId).data, amount);
  }

  @Override
  public int getPlayerLevel(int playerId) {
    if (!mPlayer.has(playerId)) return 1;
    Attributes attrs = mAttributesWrapper.has(playerId)
        ? mAttributesWrapper.get(playerId).attrs : null;
    if (attrs == null && mPlayer.get(playerId).data != null) {
      attrs = mPlayer.get(playerId).data.getStats();
    }
    StatRef level = attrs == null ? null : attrs.get(Stat.level, StatRef.obtain());
    return Math.max(1, level == null ? 1 : level.asInt());
  }

  @Override
  public int getDifficulty() {
    if (com.riiablo.Riiablo.charData != null) return com.riiablo.Riiablo.charData.diff;
    return 0;
  }
}
