package com.riiablo.engine.client;

import com.artemis.BaseEntitySystem;
import com.artemis.ComponentMapper;
import com.artemis.annotations.All;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;

import com.riiablo.CharacterClass;
import com.riiablo.Riiablo;
import com.riiablo.audio.Audio;
import com.riiablo.codec.Animation;
import com.riiablo.engine.Engine;
import com.riiablo.engine.client.component.AnimationWrapper;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.map.Map;
import com.riiablo.map.Material;

@All({Player.class, AnimationWrapper.class, Velocity.class, Position.class, MapWrapper.class,
    CofReference.class})
public class FootstepEmitter extends BaseEntitySystem {
  private static final String TAG = "FootstepEmitter";

  private static final boolean DEBUG = !true;
  private static final boolean DEBUG_TRIGGER = DEBUG && true;
  private static final boolean DEBUG_MATERIAL = DEBUG && true;

  protected ComponentMapper<AnimationWrapper> mAnimationWrapper;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<CofReference> mCofReference;

  protected Map map;

  private int previousFrame = -1;
  private byte previousMode = -1;

  @Override
  protected void processSystem() {
    final int entityId = Riiablo.game.player;
    // 检查玩家实体是否存在且有 Velocity 组件（防止死亡后访问空指针）
    if (entityId < 0 || !mVelocity.has(entityId)) {
      return;
    }
    Velocity velocityComponent = mVelocity.get(entityId);
    if (velocityComponent == null || velocityComponent.velocity == null) {
      return;
    }
    boolean isMoving = !velocityComponent.velocity.isZero();
    if (!isMoving) {
      resetStepState();
      return;
    }

    Animation animation = mAnimationWrapper.get(entityId).animation;
    int frame = animation.getFrame();
    int frameCount = Math.max(1, animation.getNumFramesPerDir());
    byte mode = mCofReference.get(entityId).mode;
    if (!isMovementMode(mode)) {
      resetStepState();
      return;
    }

    // Network clients render the authoritative mode snapshot, which can trail
    // local movement input by a tick. Prefer RN, then use actual velocity as a
    // fallback while the snapshot still reports WL/TW.
    float speed = velocityComponent.velocity.len();
    boolean running = isRunning(mode, speed,
        velocityComponent.walkSpeed, velocityComponent.runSpeed);
    if (mode != previousMode || previousFrame < 0) {
      previousMode = mode;
      previousFrame = frame;
      return;
    }

    if (crossedFootstep(previousFrame, frame, frameCount)) {
      if (DEBUG_TRIGGER) Gdx.app.debug(TAG,
          String.format("Triggered on frame %d (%s)", frame, running ? "run" : "walk"));

      Map map = mMapWrapper.get(entityId).map;
      Vector2 position = mPosition.get(entityId).position;
      Material material = map.material(position);
      if (DEBUG_MATERIAL) Gdx.app.debug(TAG, "Material: " + material);
      Player player = mPlayer.get(entityId);
      CharacterClass characterClass = player.data == null ? null : player.data.classId;
      String sound = sound(characterClass, running, material);
      Audio.Instance playback = Riiablo.audio.play(sound, true);
      Gdx.app.log(TAG, String.format(java.util.Locale.ROOT,
          "[PLAYER_FOOTSTEP] entity=%d mode=%s running=%s speed=%.3f "
              + "walkSpeed=%.3f runSpeed=%.3f frame=%d/%d material=%s sound=%s "
              + "accepted=%s loaded=%s",
          entityId, movementModeName(mode), running, speed,
          velocityComponent.walkSpeed, velocityComponent.runSpeed,
          frame, frameCount, material, sound,
          playback != null, playback != null && playback.isLoaded()));
    }
    previousFrame = frame;
  }

  private void resetStepState() {
    previousFrame = -1;
    previousMode = -1;
  }

  static boolean isMovementMode(byte mode) {
    return mode == Engine.Player.MODE_RN
        || mode == Engine.Player.MODE_WL
        || mode == Engine.Player.MODE_TW;
  }

  private static String movementModeName(byte mode) {
    if (mode == Engine.Player.MODE_RN) return "RN";
    if (mode == Engine.Player.MODE_WL) return "WL";
    if (mode == Engine.Player.MODE_TW) return "TW";
    return Byte.toString(mode);
  }

  static boolean isRunning(byte mode, float speed, float walkSpeed, float runSpeed) {
    if (mode == Engine.Player.MODE_RN) return true;
    if (mode != Engine.Player.MODE_WL && mode != Engine.Player.MODE_TW) return false;
    if (speed <= 0f || runSpeed <= walkSpeed) return false;
    return Math.abs(speed - runSpeed) < Math.abs(speed - walkSpeed);
  }

  /** Detects either foot plant even when a fast animation skips the exact frame. */
  static boolean crossedFootstep(int previous, int current, int frameCount) {
    if (previous < 0 || frameCount <= 0 || previous == current) return false;
    int end = current < previous ? current + frameCount : current;
    int middle = Math.max(1, frameCount / 2);
    for (int frame = previous + 1; frame <= end; frame++) {
      int normalized = frame % frameCount;
      if (normalized == 0 || normalized == middle) return true;
    }
    return false;
  }

  /** Selects the native class-weight, walk/run, and terrain sound group. */
  static String sound(CharacterClass characterClass, boolean running, Material material) {
    String weight;
    if (characterClass == CharacterClass.AMAZON || characterClass == CharacterClass.PALADIN) {
      weight = "medium";
    } else if (characterClass == CharacterClass.BARBARIAN) {
      weight = "heavy";
    } else {
      weight = "light";
    }
    return weight + "_" + (running ? "run" : "walk") + "_" + material + "_1";
  }
}
