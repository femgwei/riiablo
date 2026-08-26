package com.riiablo.engine.server.ai;

import com.artemis.ComponentMapper;
import com.badlogic.gdx.math.Vector2;

import com.riiablo.Riiablo;
import com.riiablo.codec.Animation;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/** D2MOO AITHINK_Fn043_FoulCrowNest. */
public class FoulCrowNest extends AI {
  private static final Logger log = LogManager.getLogger(FoulCrowNest.class);
  private static final float ACTIVATION_DISTANCE = 20f;

  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<Sequence> mSequence;

  private float elapsedFrames;
  private float lastSpawnFrame;
  private float nextThink;
  private int spawned;
  private String state = "IDLE";

  public FoulCrowNest(int entityId) {
    super(entityId);
  }

  @Override
  public void update(float delta) {
    elapsedFrames += delta / Animation.FRAME_DURATION;
    nextThink -= delta;
    if (nextThink > 0f || monster == null || !mPosition.has(entityId)) return;
    nextThink = 20f * Animation.FRAME_DURATION;

    if (mAttributesWrapper.has(entityId)) {
      com.riiablo.attributes.StatRef hp = mAttributesWrapper.get(entityId).attrs.get(
          com.riiablo.attributes.Stat.hitpoints, com.riiablo.attributes.StatRef.obtain());
      if (hp != null && hp.asFixed() <= 0f) return;
    }
    if (mCasting.has(entityId) || mSequence.has(entityId)) {
      state = "CASTING";
      return;
    }

    float[] distance = {Float.MAX_VALUE};
    int targetId = findNearestTargetWithAidist(distance);
    int interval = params.length > 0 ? Math.max(0, params[0]) : 25;
    int maximum = params.length > 2 ? Math.max(0, params[2]) : 0;
    boolean hasSkill = monster.monstats.Skill1 != null && !monster.monstats.Skill1.isEmpty();
    if (!shouldSpawn(distance[0], spawned, maximum,
        elapsedFrames - lastSpawnFrame, interval, hasSkill)) {
      state = spawned >= maximum ? "EXHAUSTED" : "IDLE";
      return;
    }

    Vector2 target = mPosition.get(targetId).position;
    if (useMonsterSkill(0, targetId, target)) {
      spawned++;
      lastSpawnFrame = elapsedFrames;
      state = "SPAWN";
      log.info("[MONSTER_NEST] phase=ai_cast source={} monster={} target={} spawned={}/{} "
              + "intervalFrames={} targetDistance={}",
          entityId, monster.monstats.Id, targetId, spawned, maximum, interval, distance[0]);
    } else {
      state = "CAST_REJECTED";
      log.warn("[MONSTER_NEST] phase=ai_rejected source={} monster={} target={} skill={}",
          entityId, monster.monstats.Id, targetId, monster.monstats.Skill1);
    }
  }

  public static boolean shouldSpawn(float targetDistance, int spawned, int maximum,
      float framesSinceSpawn, int interval, boolean hasSkill) {
    return hasSkill && targetDistance <= ACTIVATION_DISTANCE
        && spawned < maximum && framesSinceSpawn >= interval;
  }

  @Override
  public void kill() {
    if ("DEAD".equals(state)) return;
    stopMovement();
    state = "DEAD";
    mSequence.create(entityId).sequence(Engine.Monster.MODE_DT, Engine.Monster.MODE_DD);
    Riiablo.audio.play(monsound + "_death_1", true);
  }

  @Override
  public String getState() {
    return state;
  }
}
