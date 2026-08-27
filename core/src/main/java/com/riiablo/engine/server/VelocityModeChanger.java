package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Exclude;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.D2;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.client.component.AnimationWrapper;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.MovementModes;
import com.riiablo.engine.server.component.Networked;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

@All({MovementModes.class, Velocity.class, AnimData.class, CofReference.class})
@Exclude(Sequence.class)
public class VelocityModeChanger extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(VelocityModeChanger.class);

  // D2Common UNITS_GetBaseAnimSpeed: player run=101, all other player
  // movement modes (including walk/town-walk)=213, in 24.8 frame units.
  static final int PLAYER_RUN_ANIM_SPEED = 101;
  static final int PLAYER_WALK_ANIM_SPEED = 213;

  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<AnimData> mAnimData;
  protected ComponentMapper<Running> mRunning;
  protected ComponentMapper<MovementModes> mMovementModes;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Networked> mNetworked;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<AnimationWrapper> mAnimationWrapper;

  protected CofManager cofs;

  private final boolean applyLocalRunInput;
  private final boolean updateNetworkedModes;

  /** Local single-player configuration. */
  public VelocityModeChanger() {
    this(true, true);
  }

  /**
   * @param applyLocalRunInput whether this world reads Shift and adjusts the local player's speed
   * @param updateNetworkedModes whether this world owns COF modes for Networked entities
   */
  public VelocityModeChanger(boolean applyLocalRunInput, boolean updateNetworkedModes) {
    this.applyLocalRunInput = applyLocalRunInput;
    this.updateNetworkedModes = updateNetworkedModes;
  }

  @Override
  protected void begin() {
    if (!applyLocalRunInput || Riiablo.game == null) return;
    Velocity velocityComp = mVelocity.get(Riiablo.game.player);
    if (velocityComp == null) return; // Player may be dead (Velocity component removed)
    Vector2 velocity = velocityComp.velocity;
    if (velocity.isZero()) return;
    if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
      mRunning.remove(Riiablo.game.player);
      velocity.setLength(velocityComp.walkSpeed);
    } else {
      mRunning.create(Riiablo.game.player);
      velocity.setLength(velocityComp.runSpeed);
    }
  }

  /**
   * TODO: it would appear after testing that monsters may require a separate system to override
   *       their movement speed correctly from players. Need to investigate more when I can create
   *       and environment where I can adjust speeds to try and compare and see if I can refine
   *       my algorithm. Below method looks sufficient for now.
   */
  @Override
  protected void process(int entityId) {
    // A network client renders the authoritative CofReferenceP sent by D2GS.
    // Re-deriving a mode locally from a slightly older VelocityP changes
    // NU/WL/RN (and attack modes) back every frame, forcing COF reloads and
    // producing the visible animation flash. The D2GS uses the same system
    // with updateNetworkedModes=true and remains the sole mode owner.
    if (!updateNetworkedModes && mNetworked.has(entityId)) return;
    Velocity velocity = mVelocity.get(entityId);
    Vector2 currentVelocity = velocity.velocity;
    if (currentVelocity.isZero()) {
      cofs.setMode(entityId, mMovementModes.get(entityId).NU);
      mAnimData.get(entityId).override = -1;
    } else if (mMonster.has(entityId)) {
      AnimData animData = mAnimData.get(entityId);
      boolean running = mRunning.has(entityId);
      int baseAnimSpeed = resolveMonsterBaseAnimationSpeed(entityId, running, animData);
      if (running) {
        cofs.setMode(entityId, mMovementModes.get(entityId).RN);
        animData.override = scaleAnimationSpeed(
            baseAnimSpeed, currentVelocity.len(), velocity.runSpeed);
      } else {
        cofs.setMode(entityId, mMovementModes.get(entityId).WL);
        animData.override = scaleAnimationSpeed(
            baseAnimSpeed, currentVelocity.len(), velocity.walkSpeed);
      }
      syncClientAnimation(entityId, animData, velocity, running, baseAnimSpeed);
    } else {
      if (mRunning.has(entityId)) {
        cofs.setMode(entityId, mMovementModes.get(entityId).RN);
        mAnimData.get(entityId).override = scaleAnimationSpeed(
            PLAYER_RUN_ANIM_SPEED, currentVelocity.len(), velocity.runSpeed);
      } else {
        cofs.setMode(entityId, mMovementModes.get(entityId).WL);
        mAnimData.get(entityId).override = scaleAnimationSpeed(
            PLAYER_WALK_ANIM_SPEED, currentVelocity.len(), velocity.walkSpeed);
      }
    }
  }

  static int scaleAnimationSpeed(int baseAnimSpeed, float currentSpeed, float baseVelocity) {
    if (baseAnimSpeed <= 0 || currentSpeed <= 0 || baseVelocity <= 0) return 0;
    return MathUtils.roundPositive(baseAnimSpeed * currentSpeed / baseVelocity);
  }

  /** Mirrors D2Common's MonStats walk/run animation speed derivation. */
  int resolveMonsterBaseAnimationSpeed(int entityId, boolean running, AnimData animData) {
    Monster monster = mMonster.get(entityId);
    MonStats.Entry current = monster != null ? monster.monstats : null;
    if (current == null) return animData.speed;

    MonStats.Entry base = current.BaseId == null || current.BaseId.isEmpty()
        ? current : Riiablo.files.monstats.get(current.BaseId);
    if (base == null) base = current;

    CofReference cof = mCofReference.get(entityId);
    int rawBaseSpeed;
    if (running && current.hcIdx < 410) {
      // Classic monsters derive RN speed from half of the base WL rate.
      rawBaseSpeed = lookupBaseAnimationSpeed(base, Engine.Monster.MODE_WL, cof, animData.speed);
      rawBaseSpeed /= 2;
    } else {
      byte mode = running ? Engine.Monster.MODE_RN : Engine.Monster.MODE_WL;
      rawBaseSpeed = lookupBaseAnimationSpeed(base, mode, cof, animData.speed);
    }

    int currentReference = running ? current.Run : current.Velocity;
    int baseReference = running ? base.Run : base.Velocity;
    return deriveVariantAnimationSpeed(rawBaseSpeed, currentReference, baseReference);
  }

  private static int lookupBaseAnimationSpeed(
      MonStats.Entry base, byte mode, CofReference cof, int fallback) {
    if (Riiablo.anim == null || base == null || base.Code == null || base.Code.isEmpty()) {
      return fallback;
    }

    String key = base.Code + Class.Type.MON.getMode(mode) + Engine.getWClass(cof.wclass);
    D2.Entry entry = Riiablo.anim.getEntry(key);
    return entry != null && entry.speed > 0 ? entry.speed : fallback;
  }

  static int deriveVariantAnimationSpeed(
      int rawBaseAnimationSpeed, int variantMovementReference, int baseMovementReference) {
    if (rawBaseAnimationSpeed <= 0) return 0;
    if (variantMovementReference <= 0 || baseMovementReference <= 0) {
      return rawBaseAnimationSpeed;
    }
    return MathUtils.clamp(
        MathUtils.roundPositive(
            rawBaseAnimationSpeed * (float) variantMovementReference / baseMovementReference),
        0,
        0x7FFF);
  }

  /**
   * COF caching used to apply AnimData.override only when the COF changed.
   * Native AI velocity bonuses often keep WL/RN unchanged, so update the
   * visible animation whenever its authoritative rate changes.
   */
  private void syncClientAnimation(
      int entityId,
      AnimData animData,
      Velocity velocity,
      boolean running,
      int baseAnimSpeed) {
    if (!mAnimationWrapper.has(entityId)) return;

    com.riiablo.codec.Animation animation = mAnimationWrapper.get(entityId).animation;
    int expectedRate = animData.override >= 0 ? animData.override : animData.speed;
    int previousRate = animation.getFrameDelta();
    if (previousRate == expectedRate) return;

    animation.setFrameDelta(expectedRate);
    Monster monster = mMonster.get(entityId);
    log.info(
        "[MONSTER_ANIM_SYNC] entity={} monster={} mode={} baseAnimRate={} "
            + "previousVisualRate={} visualRate={} movementSpeed={} baseVelocity={} baseId={}",
        entityId,
        monster != null && monster.monstats != null ? monster.monstats.Id : "unknown",
        running ? "RN" : "WL",
        baseAnimSpeed,
        previousRate,
        expectedRate,
        velocity.velocity.len(),
        running ? velocity.runSpeed : velocity.walkSpeed,
        monster != null && monster.monstats != null ? monster.monstats.BaseId : "unknown");
  }
}
