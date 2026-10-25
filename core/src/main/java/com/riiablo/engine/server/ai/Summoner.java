package com.riiablo.engine.server.ai;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AttributesWrapper;

/** D2MOO AiThink.cpp AITHINK_Fn053_Summoner (0x6FCDAFC0). */
public class Summoner extends NativeDecisionAI {
  @Override void writeRuntime(java.io.DataOutputStream out) throws java.io.IOException {
    super.writeRuntime(out);
    com.riiablo.engine.replay.CheckpointIO.writeFloat(out, novaCooldown);
    com.riiablo.engine.replay.CheckpointIO.writeFloat(out, firewallCooldown);
  }
  @Override void readRuntime(java.io.DataInputStream in, boolean apply) throws java.io.IOException {
    super.readRuntime(in, apply);
    float savedNovaCooldown = com.riiablo.engine.replay.CheckpointIO.readFloat(in);
    float savedFirewallCooldown = com.riiablo.engine.replay.CheckpointIO.readFloat(in);
    if (apply) {
      novaCooldown = savedNovaCooldown;
      firewallCooldown = savedFirewallCooldown;
    }
  }
  static final int PARAM_CAST_CHANCE = 0;
  static final int PARAM_WEAKEN_CHANCE = 1;
  static final int PARAM_PREFERRED_ELEMENT_CHANCE = 2;
  static final int PARAM_NOVA_TIMER = 3;
  static final int PARAM_FIREWALL_TIMER = 4;
  static final int PARAM_WALK_AWAY_CHANCE = 5;
  static final int PARAM_NOVA_DISTANCE = 6;
  static final int PARAM_MISSILE_DISTANCE = 7;

  private float novaCooldown;
  private float firewallCooldown;
  private final Vector2 escape = new Vector2();

  public Summoner(int entityId) {
    super(entityId);
  }

  @Override
  protected void beforeThink(float delta) {
    novaCooldown = Math.max(0f, novaCooldown - Math.max(0f, delta));
    firewallCooldown = Math.max(0f, firewallCooldown - Math.max(0f, delta));
  }

  @Override
  protected void think(int targetId, float distance, boolean combat) {
    if (distance < 5f && rollAiChance(params[PARAM_WALK_AWAY_CHANCE])) {
      walkAway(targetId);
      return;
    }

    if (!rollAiChance(params[PARAM_CAST_CHANCE])) {
      approach(targetId, 0);
      return;
    }

    if (hasSkill(4) && rollAiChance(params[PARAM_WEAKEN_CHANCE])) {
      cast(4, targetId);
      return;
    }

    boolean useCold = targetFireResist(targetId) >= targetColdResist(targetId);
    // D2MOO uses AI_RollPercentage() > preferred-element chance to invert it.
    if (rollAiPercentage() > params[PARAM_PREFERRED_ELEMENT_CHANCE]) useCold = !useCold;

    if (useCold) {
      if (hasSkill(1) && novaCooldown <= 0f
          && distance < params[PARAM_NOVA_DISTANCE]) {
        novaCooldown = framesToSeconds(params[PARAM_NOVA_TIMER]);
        cast(1, targetId);
        return;
      }
      if (hasSkill(0) && distance < params[PARAM_MISSILE_DISTANCE]) {
        cast(0, targetId);
        return;
      }
    }

    if (hasSkill(3) && firewallCooldown <= 0f) {
      firewallCooldown = framesToSeconds(params[PARAM_FIREWALL_TIMER]);
      cast(3, targetId);
      return;
    }

    if (hasSkill(2) && distance < params[PARAM_MISSILE_DISTANCE]) {
      cast(2, targetId);
      return;
    }

    if (!useCold && hasSkill(1) && novaCooldown <= 0f
        && distance < params[PARAM_NOVA_DISTANCE]) {
      novaCooldown = framesToSeconds(params[PARAM_NOVA_TIMER]);
      cast(1, targetId);
      return;
    }

    // Native falls back to Weaken even when its chance roll failed.
    if (hasSkill(4)) cast(4, targetId);
    else approach(targetId, 0);
  }

  private void walkAway(int targetId) {
    Vector2 position = mPosition.get(entityId).position;
    escape.set(position).sub(mPosition.get(targetId).position);
    if (escape.isZero(0.0001f)) escape.set(1f, 0f);
    escape.nor().scl(6f).add(position);
    if (!walkTo(escape, 0, Engine.INVALID_ENTITY)) idle(1);
  }

  private int targetFireResist(int targetId) {
    return targetResistance(targetId, Stat.fireresist);
  }

  private int targetColdResist(int targetId) {
    return targetResistance(targetId, Stat.coldresist);
  }

  private int targetResistance(int targetId, short stat) {
    if (!mAttributesWrapper.has(targetId)) return 0;
    AttributesWrapper wrapper = mAttributesWrapper.get(targetId);
    return wrapper == null || wrapper.attrs == null ? 0 : wrapper.attrs.getValue(stat, 0);
  }

  private static float framesToSeconds(int frames) {
    return Math.max(0, frames) * com.riiablo.codec.Animation.FRAME_DURATION;
  }
}
