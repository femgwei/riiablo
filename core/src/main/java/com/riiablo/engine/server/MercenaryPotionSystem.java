package com.riiablo.engine.server;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.UnitLifecycle;
import com.riiablo.item.Item;
import com.riiablo.item.Location;
import com.riiablo.item.Type;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.save.CharData;

/** Authoritative 1.14-style cursor potion use on the owner's hireling. */
@Wire(failOnNull = false)
public final class MercenaryPotionSystem extends BaseSystem {
  private static final Logger log = LogManager.getLogger(MercenaryPotionSystem.class);
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Mercenary> mMercenary;
  protected ComponentMapper<AttributesWrapper> mAttributes;
  protected ComponentMapper<UnitLifecycle> mLifecycle;
  private com.artemis.EntitySubscription mercenaries;

  @Override
  protected void initialize() {
    mercenaries = world.getAspectSubscriptionManager()
        .get(Aspect.all(Mercenary.class, AttributesWrapper.class));
  }

  /** Validates and heals, but does not consume the cursor item. */
  public boolean useOnMercenary(int playerId, Item potion) {
    if (playerId < 0 || mPlayer == null || !mPlayer.has(playerId)) {
      log.info("[MERC_POTION] phase=rejected reason=player_missing player={}", playerId);
      return false;
    }
    if (potion == null) {
      log.info("[MERC_POTION] phase=rejected reason=empty_cursor player={}", playerId);
      return false;
    }
    if (potion.location != Location.CURSOR) {
      log.info("[MERC_POTION] phase=rejected reason=cursor_location item={} code={} location={} player={}",
          potion.id, potion.code, potion.location, playerId);
      return false;
    }
    if (!isHealingPotion(potion)) {
      log.info("[MERC_POTION] phase=rejected reason=not_healing_potion item={} code={} type={} player={}",
          potion.id, potion.code, potion.type, playerId);
      return false;
    }
    Player player = mPlayer.get(playerId);
    CharData character = player == null ? null : player.data;
    if (character == null || !character.isExpansion()) {
      log.info("[MERC_POTION] phase=rejected reason=non_expansion_character player={}", playerId);
      return false;
    }

    IntBag entities = mercenaries == null ? null : mercenaries.getEntities();
    if (entities == null) {
      log.info("[MERC_POTION] phase=rejected reason=mercenary_subscription_missing player={}", playerId);
      return false;
    }
    int[] data = entities.getData();
    boolean ownedMercenary = false;
    for (int i = 0; i < entities.size(); i++) {
      int entityId = data[i];
      Mercenary merc = mMercenary.get(entityId);
      if (merc == null || merc.ownerId != playerId || isDead(entityId)) continue;
      ownedMercenary = true;
      AttributesWrapper wrapper = mAttributes.get(entityId);
      if (wrapper == null || wrapper.attrs == null) continue;
      StatRef hp = wrapper.attrs.aggregate().get(Stat.hitpoints, StatRef.obtain());
      StatRef max = wrapper.attrs.aggregate().get(Stat.maxhp, StatRef.obtain());
      if (hp == null || max == null) {
        log.info("[MERC_POTION] phase=rejected reason=health_stats_missing player={} merc={}",
            playerId, entityId);
        return false;
      }
      if (max.asFixed() <= hp.asFixed()) {
        log.info("[MERC_POTION] phase=rejected reason=mercenary_full_health player={} merc={} hp={} max={}",
            playerId, entityId, hp.asFixed(), max.asFixed());
        return false;
      }
      float healed = Math.min(max.asFixed(), hp.asFixed() + healingAmount(potion));
      if (healed <= hp.asFixed()) {
        log.info("[MERC_POTION] phase=rejected reason=no_effect player={} merc={} hp={} max={} amount={}",
            playerId, entityId, hp.asFixed(), max.asFixed(), healingAmount(potion));
        return false;
      }
      hp.set(healed);
      wrapper.attrs.base().put(Stat.hitpoints, healed);
      wrapper.attrs.aggregate().put(Stat.hitpoints, healed);
      log.info("[MERC_POTION] phase=healed player={} merc={} item={} code={} hp={} max={} amount={}",
          playerId, entityId, potion.id, potion.code, healed, max.asFixed(), healingAmount(potion));
      return true;
    }
    log.info("[MERC_POTION] phase=rejected reason={} player={}",
        ownedMercenary ? "mercenary_unavailable" : "owned_mercenary_missing", playerId);
    return false;
  }

  public static boolean isHealingPotion(Item potion) {
    if (potion == null || potion.type == null) return false;
    if (potion.type.is(Type.HPOT)) return true;
    return potion.code != null && potion.code.toLowerCase(java.util.Locale.ROOT).startsWith("hp");
  }

  private boolean isDead(int entityId) {
    if (mLifecycle != null && mLifecycle.has(entityId) && mLifecycle.get(entityId).isDead()) return true;
    StatRef hp = mAttributes.get(entityId).attrs.aggregate().get(Stat.hitpoints, StatRef.obtain());
    return hp == null || hp.asFixed() <= 0f;
  }

  private static float healingAmount(Item potion) {
    int tier = 1;
    if (potion.code != null && potion.code.length() > 2
        && Character.isDigit(potion.code.charAt(2))) tier = potion.code.charAt(2) - '0';
    tier = Math.max(1, Math.min(5, tier));
    int[] fallback = {0, 30, 60, 100, 180, 320};
    if (potion.base instanceof com.riiablo.codec.excel.Misc.Entry) {
      com.riiablo.codec.excel.Misc.Entry misc = (com.riiablo.codec.excel.Misc.Entry) potion.base;
      if (misc.calc != null && misc.calc.length > 0) {
        try {
          int value = Integer.parseInt(misc.calc[0].trim());
          if (value > 0) return value;
        } catch (NumberFormatException ignored) {}
      }
    }
    return fallback[tier];
  }

  @Override
  protected void processSystem() {}
}
