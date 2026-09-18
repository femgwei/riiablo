package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;

import com.badlogic.gdx.math.MathUtils;

import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.component.Velocity;

/**
 * Authoritative Diablo II style player stamina consumption and recovery.
 *
 * <p>The ECS runs at the native 25Hz simulation rate.  RunDrain is stored in
 * CharStats as stamina points per second, while idle/walk recovery is a
 * quarter of the maximum stamina per second.  Active shrine/state recovery
 * bonuses are applied as a percentage to the recovery rate.
 */
@All({Player.class, AttributesWrapper.class, Velocity.class})
public class StaminaSystem extends IteratingSystem {
  private static final float DEFAULT_RUN_DRAIN_PER_SECOND = 20f;
  private static final float BASE_RECOVERY_PER_SECOND = 0.25f;
  private static final float EPSILON = 0.0001f;

  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<AttributesWrapper> mAttributes;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<Running> mRunning;

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
      if (mRunning.has(entityId)) mRunning.remove(entityId);
      return;
    }

    float current = MathUtils.clamp(stamina.asFixed(), 0f, maximum);
    boolean moving = !velocity.velocity.isZero(0.001f);
    boolean running = moving && mRunning.has(entityId);
    float delta = world.getDelta();
    float next;
    if (running && current > EPSILON) {
      float drain = resolveRunDrain(player);
      next = current - drain * delta;
      if (next <= EPSILON) {
        next = 0f;
        // Running is a mode component. Removing it here makes the next
        // velocity/mode pass switch to walking instead of repeatedly trying
        // to run on an empty stamina pool.
        mRunning.remove(entityId);
        if (velocity.walkSpeed > 0f) velocity.velocity.setLength(velocity.walkSpeed);
      }
    } else {
      int recoveryBonus = resolveRecoveryBonus(attrs);
      float recovery = maximum * BASE_RECOVERY_PER_SECOND
          * (1f + Math.max(0, recoveryBonus) / 100f);
      next = current + recovery * delta;
    }

    if (Math.abs(next - stamina.asFixed()) > EPSILON) stamina.set(MathUtils.clamp(next, 0f, maximum));
  }

  private float resolveRunDrain(Player player) {
    if (player.data != null && player.data.classId != null
        && player.data.classId.entry() != null
        && player.data.classId.entry().RunDrain > 0) {
      return player.data.classId.entry().RunDrain;
    }
    return DEFAULT_RUN_DRAIN_PER_SECOND;
  }

  private int resolveRecoveryBonus(Attributes attrs) {
    StatRef bonus = attrs.get(Stat.staminarecoverybonus, StatRef.obtain());
    return bonus == null ? 0 : bonus.asInt();
  }
}
