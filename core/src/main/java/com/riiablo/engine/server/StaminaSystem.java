package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;

import com.badlogic.gdx.math.MathUtils;

import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.component.TemporaryRunning;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * Authoritative Diablo II style player stamina consumption and recovery.
 *
 * <p>The ECS runs at the native 25Hz simulation rate. CharStats RunDrain and
 * stamina regeneration use Diablo II's 8.8 fixed-point, per-tick arithmetic.
 * Town movement never drains stamina; wilderness walking below one full point
 * cannot regenerate until the player stops.
 */
@All({Player.class, AttributesWrapper.class, Velocity.class})
public class StaminaSystem extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(StaminaSystem.class);
  private static final float DEFAULT_RUN_DRAIN = 20f;
  private static final float FIXED_POINT_SCALE = 256f;
  private static final int FULL_RECOVERY_SHIFT = 8;
  private static final int WALK_RECOVERY_SHIFT = 9;
  private static final int ALWAYS_RECOVER_BONUS = 1000;
  private static final float EPSILON = 0.0001f;

  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<AttributesWrapper> mAttributes;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<Running> mRunning;
  protected ComponentMapper<TemporaryRunning> mTemporaryRunning;
  protected ComponentMapper<MapWrapper> mMapWrapper;

  @Override
  protected void process(int entityId) {
    Player player = mPlayer.get(entityId);
    AttributesWrapper wrapper = mAttributes.get(entityId);
    Velocity velocity = mVelocity.get(entityId);
    if (player == null || wrapper == null || wrapper.attrs == null || velocity == null) return;

    Attributes attrs = wrapper.attrs;
    StatRef stamina = attrs.get(Stat.stamina, StatRef.obtain());
    StatRef maxStamina = attrs.get(Stat.maxstamina, StatRef.obtain());
    if (stamina == null || maxStamina == null) return;

    float maximum = Math.max(0f, maxStamina.asFixed());
    if (maximum <= EPSILON) {
      stamina.set(0f);
      return;
    }

    float current = MathUtils.clamp(stamina.asFixed(), 0f, maximum);
    boolean moving = !velocity.velocity.isZero(0.001f);
    boolean running = moving && VelocityModeChanger.isRunRequested(
        mRunning.has(entityId), mTemporaryRunning.has(entityId));
    boolean inTown = isInTown(entityId);
    int recoveryBonus = resolveRecoveryBonus(attrs);
    float next;
    if (shouldDrain(running, inTown, current)) {
      float drain = drainPerTick(
          resolveRunDrain(player), resolveArmorSpeed(player), resolveStaminaDrainPercent(attrs));
      next = current - drain;
      if (next <= EPSILON) {
        next = 0f;
        if (velocity.walkSpeed > 0f) velocity.velocity.setLength(velocity.walkSpeed);
        log.info("[PLAYER_STAMINA] entity={} state=exhausted stamina=0.0 maximum={}",
            entityId, maximum);
      }
    } else {
      int recoveryShift = recoveryShift(moving, running, inTown, current, recoveryBonus);
      next = recoveryShift < 0
          ? current
          : current + recoveryPerTick(maximum, recoveryBonus, recoveryShift);
    }

    if (Math.abs(next - stamina.asFixed()) > EPSILON) stamina.set(MathUtils.clamp(next, 0f, maximum));
  }

  private float resolveRunDrain(Player player) {
    if (player.data != null && player.data.classId != null
        && player.data.classId.entry() != null
        && player.data.classId.entry().RunDrain > 0) {
      return player.data.classId.entry().RunDrain;
    }
    return DEFAULT_RUN_DRAIN;
  }

  private int resolveRecoveryBonus(Attributes attrs) {
    StatRef bonus = attrs.get(Stat.staminarecoverybonus, StatRef.obtain());
    return bonus == null ? 0 : bonus.asInt();
  }

  private int resolveStaminaDrainPercent(Attributes attrs) {
    StatRef modifier = attrs.get(Stat.item_staminadrainpct, StatRef.obtain());
    return modifier == null ? 0 : modifier.asInt();
  }

  private int resolveArmorSpeed(Player player) {
    if (player == null || player.data == null || player.data.getItems() == null) return 0;
    Item torso = player.data.getItems().getEquipped(BodyLoc.TORS);
    return torso == null || torso.base == null ? 0 : torso.base.speed;
  }

  private boolean isInTown(int entityId) {
    if (!mMapWrapper.has(entityId)) return false;
    MapWrapper wrapper = mMapWrapper.get(entityId);
    return wrapper != null && wrapper.zone != null && wrapper.zone.isTown();
  }

  /** Mirrors D2Game's 8.8 fixed-point per-tick running drain. */
  static float drainPerTick(float runDrain, int armorSpeed, int staminaDrainPercent) {
    int raw = Math.max(1, MathUtils.roundPositive(2f * Math.max(0f, runDrain)));
    raw *= armorSpeed / 10 + 1;
    if (staminaDrainPercent != 0) raw += raw * staminaDrainPercent / -100;
    return Math.max(1, raw) / FIXED_POINT_SCALE;
  }

  static boolean shouldDrain(boolean running, boolean inTown, float current) {
    return running && !inTown && current > EPSILON;
  }

  static boolean hasRunStamina(AttributesWrapper wrapper) {
    if (wrapper == null || wrapper.attrs == null) return true;
    float stamina = wrapper.attrs.aggregate().getValue(Stat.stamina, 0f);
    float maximum = wrapper.attrs.aggregate().getValue(Stat.maxstamina, 0f);
    return maximum <= 0f || stamina > EPSILON;
  }

  /** Returns -1 when native rules do not permit recovery during this tick. */
  static int recoveryShift(
      boolean moving, boolean running, boolean inTown, float current, int recoveryBonus) {
    if (!moving) return FULL_RECOVERY_SHIFT;
    if (!running) {
      if (!inTown && current < 1f) return -1;
      return WALK_RECOVERY_SHIFT;
    }
    return recoveryBonus >= ALWAYS_RECOVER_BONUS ? FULL_RECOVERY_SHIFT : -1;
  }

  /** Mirrors D2Game's max-stamina based 8.8 fixed-point regeneration event. */
  static float recoveryPerTick(float maximum, int recoveryBonus, int shift) {
    if (maximum <= 0f || shift < 0) return 0f;
    int maximumRaw = Math.max(0, MathUtils.roundPositive(maximum * FIXED_POINT_SCALE));
    int recoveryRaw = maximumRaw >> shift;
    if (recoveryBonus != 0) recoveryRaw += recoveryBonus * recoveryRaw / 100;
    return recoveryRaw / FIXED_POINT_SCALE;
  }
}
