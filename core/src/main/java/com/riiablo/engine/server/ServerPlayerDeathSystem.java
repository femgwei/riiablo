package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.MovementModes;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.PlayerCorpse;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.Target;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.item.GroundDropOwnership;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.Quality;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import com.riiablo.save.ItemData;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/** Authoritative D2-style player death, corpse ownership and town respawn. */
public class ServerPlayerDeathSystem extends PassiveSystem {
  private static final String TAG = "ServerPlayerDeathSystem";
  private static final long DEATH_GOLD_OWNER_MILLIS = 10_000L;

  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<Target> mTarget;
  protected ComponentMapper<Pathfind> mPathfind;
  protected ComponentMapper<Casting> mCasting;
  protected ComponentMapper<Sequence> mSequence;
  protected ComponentMapper<PlayerCorpse> mPlayerCorpse;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Corpse> mCorpse;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<MovementModes> mMovementModes;
  protected ComponentMapper<Running> mRunning;
  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<com.riiablo.engine.server.component.Item> mItem;

  protected CofManager cofs;
  protected ItemGenerator itemGenerator;
  @Wire(name = "factory") protected EntityFactory factory;
  @Wire(name = "map") protected Map map;

  @Subscribe
  public void onDeath(DeathEvent event) {
    int playerId = event.victim;
    if (playerId < 0 || !mPlayer.has(playerId) || mPlayerCorpse.has(playerId)) return;
    Player player = mPlayer.get(playerId);
    Position position = mPosition.has(playerId) ? mPosition.get(playerId) : null;
    if (player.data == null || position == null) {
      Gdx.app.error(TAG, "[PLAYER_DEATH] phase=reject player=" + playerId
          + " reason=missing_authoritative_state");
      return;
    }

    PlayerDeathPenalty.Result penalty = PlayerDeathPenalty.apply(player.data);
    if (penalty.droppedGold > 0) {
      createDeathGold(playerId, player.data, penalty.droppedGold,
          position.position.x, position.position.y);
    }

    ItemData items = player.data.getItems();
    PlayerCorpse marker = mPlayerCorpse.create(playerId);
    marker.playerId = playerId;
    marker.deathLocation.set(position.position);
    for (BodyLoc bodyLoc : BodyLoc.values()) {
      if (bodyLoc == BodyLoc.NONE) continue;
      Item item = items.getSlot(bodyLoc);
      if (item != null && items.unequipItem(bodyLoc) != ItemData.INVALID_ITEM) {
        marker.equippedItems.put(bodyLoc, item);
      }
    }
    marker.cursorItem = items.detachCursorItem();

    int corpseId = world.create();
    mPosition.create(corpseId).position.set(position.position);
    PlayerCorpse corpse = mPlayerCorpse.create(corpseId);
    corpse.playerId = playerId;
    corpse.deathLocation.set(position.position);
    corpse.equippedItems.putAll(marker.equippedItems);
    corpse.cursorItem = marker.cursorItem;
    mCorpse.create(corpseId).reset(PlayerCorpse.CORPSE_DURATION, false);

    setLife(playerId, 0f);
    if (mVelocity.has(playerId)) mVelocity.remove(playerId);
    if (mTarget.has(playerId)) mTarget.remove(playerId);
    if (mPathfind.has(playerId)) mPathfind.remove(playerId);
    if (mCasting.has(playerId)) mCasting.remove(playerId);
    if (mSequence.has(playerId)) mSequence.remove(playerId);
    if (mRunning.has(playerId)) mRunning.remove(playerId);

    cofs.setWClass(playerId, Engine.WEAPON_HTH);
    mSequence.create(playerId).sequence(Engine.Player.MODE_DT, Engine.Player.MODE_DD);
    Gdx.app.log(TAG, String.format(
        "[PLAYER_DEATH] phase=authoritative player=%d killer=%d corpse=%d items=%d "
            + "cursor=%s penalty=%d dropped=%d bankAfter=%d position=(%.2f,%.2f)",
        playerId, event.killer, corpseId, marker.equippedItems.size,
        marker.cursorItem != null, penalty.penaltyGold, penalty.droppedGold,
        penalty.bankAfter, position.position.x, position.position.y));
  }

  public boolean isPlayerDead(int playerId) {
    return playerId >= 0 && mPlayer.has(playerId) && mPlayerCorpse.has(playerId);
  }

  public boolean canRespawn(int playerId) {
    return isPlayerDead(playerId) && mCofReference.has(playerId)
        && mCofReference.get(playerId).mode == Engine.Player.MODE_DD;
  }

  /** Applies the authenticated respawn request while preserving the corpse entity. */
  public boolean respawnAtTown(int playerId) {
    if (!canRespawn(playerId) || map == null || !mPosition.has(playerId)) return false;
    Vector2 town = map.find(Map.ID.TOWN_ENTRY_1);
    if (town == null) town = map.find(Map.ID.TOWN_ENTRY_2);
    if (town == null) town = map.find(Map.ID.TP_LOCATION);
    if (town == null) return false;

    mPosition.get(playerId).position.set(town);
    if (mMapWrapper.has(playerId)) {
      mMapWrapper.get(playerId).set(map, map.getZone(town));
    } else {
      mMapWrapper.create(playerId).set(map, map.getZone(town));
    }
    if (mPathfind.has(playerId)) mPathfind.remove(playerId);
    if (mCasting.has(playerId)) mCasting.remove(playerId);
    if (mTarget.has(playerId)) mTarget.remove(playerId);
    if (mSequence.has(playerId)) mSequence.remove(playerId);
    if (mRunning.has(playerId)) mRunning.remove(playerId);
    if (mUnitStates.has(playerId) && mUnitStates.get(playerId).stateList != null) {
      mUnitStates.get(playerId).stateList.clearAll();
    }

    AttributesWrapper attributes = mAttributesWrapper.has(playerId)
        ? mAttributesWrapper.get(playerId) : null;
    if (attributes != null && attributes.attrs != null) {
      float maxHp = attributes.attrs.aggregate().getValue(Stat.maxhp, 1f);
      float maxMana = attributes.attrs.aggregate().getValue(Stat.maxmana, 0f);
      attributes.attrs.get(Stat.hitpoints).set(Math.max(1f, maxHp));
      attributes.attrs.get(Stat.mana).set(Math.max(0f, maxMana));
    }

    Player player = mPlayer.get(playerId);
    CharData data = player.data;
    com.riiablo.codec.excel.CharStats.Entry stats = data.classId != null
        ? data.classId.entry() : null;
    float walk = stats != null && stats.WalkVelocity > 0
        ? stats.WalkVelocity : Engine.Player.SPEED_WALK;
    float run = stats != null && stats.RunVelocity > 0
        ? stats.RunVelocity : Engine.Player.SPEED_RUN;
    mVelocity.create(playerId).set(walk, run);
    mMovementModes.create(playerId).set(
        Engine.Player.MODE_TN, Engine.Player.MODE_TW, Engine.Player.MODE_RN);
    mRunning.create(playerId);
    mPlayerCorpse.remove(playerId);
    cofs.setMode(playerId, Engine.Player.MODE_NU);
    cofs.setWClass(playerId, Engine.WEAPON_HTH);
    Gdx.app.log(TAG, String.format(
        "[PLAYER_RESPAWN] phase=authoritative player=%d position=(%.2f,%.2f) hp=full",
        playerId, town.x, town.y));
    return true;
  }

  private void createDeathGold(int playerId, CharData data, int amount, float x, float y) {
    if (factory == null || itemGenerator == null || amount <= 0) return;
    try {
      Item gold = itemGenerator.generate("gld");
      if (gold == null) return;
      gold.ilvl = (byte) MathUtils.clamp(Math.max(1, data.level), 1, 99);
      gold.quality = Quality.NORMAL;
      gold.flags |= Item.ITEMFLAG_IDENTIFIED;
      gold.attrs.base().put(Stat.quantity, amount);
      gold.attrs.aggregate().put(Stat.quantity, amount);
      int entityId = factory.createItem(gold, x, y);
      if (entityId < 0) return;
      gold.id = entityId;
      GroundDropOwnership.register(entityId, playerId, -1,
          DEATH_GOLD_OWNER_MILLIS, 0L, false);
      if (mItem.has(entityId)) {
        com.riiablo.engine.server.component.Item drop = mItem.get(entityId);
        drop.dropOwnerId = playerId;
        drop.dropOwnerUntilMillis = System.currentTimeMillis() + DEATH_GOLD_OWNER_MILLIS;
      }
    } catch (Throwable t) {
      Gdx.app.error(TAG, "[PLAYER_DEATH_GOLD] phase=create_failed player=" + playerId, t);
    }
  }

  private void setLife(int playerId, float value) {
    if (!mAttributesWrapper.has(playerId)) return;
    StatRef life = mAttributesWrapper.get(playerId).attrs.get(Stat.hitpoints, StatRef.obtain());
    if (life != null) life.set(value);
  }
}
