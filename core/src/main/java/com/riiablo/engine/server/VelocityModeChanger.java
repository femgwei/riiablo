package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Exclude;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.D2;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.client.component.AnimationWrapper;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.MovementModes;
import com.riiablo.engine.server.component.Networked;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.TemporaryRunning;
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
  protected ComponentMapper<AttributesWrapper> mAttributes;
  protected ComponentMapper<AnimData> mAnimData;
  protected ComponentMapper<Running> mRunning;
  protected ComponentMapper<TemporaryRunning> mTemporaryRunning;
  protected ComponentMapper<MovementModes> mMovementModes;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Networked> mNetworked;
  protected ComponentMapper<Pathfind> mPathfind;
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
   * @param applyLocalRunInput whether this world applies the local player's run/walk preference
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
    applyMovementSpeed(Riiablo.game.player, velocityComp);
  }

  /** Toggles the persistent player preference used by mouse and keyboard movement. */
  public boolean toggleRunWalk(int entityId) {
    if (entityId < 0 || !mVelocity.has(entityId)) return false;
    boolean running = !mRunning.has(entityId);
    if (running) {
      mRunning.create(entityId);
    } else {
      mRunning.remove(entityId);
    }
    applyMovementSpeed(entityId, mVelocity.get(entityId));
    log.info("[PLAYER_RUN_WALK] entity={} mode={}", entityId, running ? "run" : "walk");
    return running;
  }

  public boolean isRunning(int entityId) {
    return entityId >= 0 && mRunning.has(entityId);
  }

  private void applyMovementSpeed(int entityId, Velocity velocityComp) {
    Vector2 velocity = velocityComp.velocity;
    if (velocity.isZero()) return;
    if (isEffectivelyRunning(entityId)) {
      velocity.setLength(velocityComp.runSpeed);
    } else {
      velocity.setLength(velocityComp.walkSpeed);
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
      AnimData animData = mAnimData.get(entityId);
      if (mMonster.has(entityId) && hasMovementIntent(entityId)) {
        // Box2D can report zero displacement for a tick while an active path
        // is being resolved (contact correction, room seams, or a dynamic
        // unit footprint).  Native D2 keeps the walk COF selected during that
        // intent; switching to NU here makes the body slide while its feet
        // remain on the idle frame.  Use the configured effective speed so
        // the visual animation continues until Pathfinder clears the intent.
        boolean running = mRunning.has(entityId);
        int baseAnimSpeed = resolveMonsterBaseAnimationSpeed(entityId, running, animData);
        setMovementMode(entityId,
            running ? mMovementModes.get(entityId).RN : mMovementModes.get(entityId).WL,
            currentVelocity);
        float intendedSpeed = velocity.speed(running);
        animData.override = movementAnimationRate(
            baseAnimSpeed, intendedSpeed, running ? velocity.runSpeed : velocity.walkSpeed);
        syncClientAnimation(entityId, animData, velocity, running, baseAnimSpeed);
      } else {
        setMovementMode(entityId, mMovementModes.get(entityId).NU, currentVelocity);
        animData.override = -1;
      }
    } else if (mMonster.has(entityId)) {
      AnimData animData = mAnimData.get(entityId);
      boolean running = mRunning.has(entityId);
      int baseAnimSpeed = resolveMonsterBaseAnimationSpeed(entityId, running, animData);
      if (running) {
        setMovementMode(entityId, mMovementModes.get(entityId).RN, currentVelocity);
        animData.override = scaleAnimationSpeed(
            baseAnimSpeed, currentVelocity.len(), velocity.runSpeed);
      } else {
        setMovementMode(entityId, mMovementModes.get(entityId).WL, currentVelocity);
        animData.override = scaleAnimationSpeed(
            baseAnimSpeed, currentVelocity.len(), velocity.walkSpeed);
      }
      syncClientAnimation(entityId, animData, velocity, running, baseAnimSpeed);
    } else {
      if (isEffectivelyRunning(entityId)) {
        setMovementMode(entityId, mMovementModes.get(entityId).RN, currentVelocity);
        mAnimData.get(entityId).override = scaleAnimationSpeed(
            PLAYER_RUN_ANIM_SPEED, currentVelocity.len(), velocity.runSpeed);
      } else {
        setMovementMode(entityId, mMovementModes.get(entityId).WL, currentVelocity);
        mAnimData.get(entityId).override = scaleAnimationSpeed(
            PLAYER_WALK_ANIM_SPEED, currentVelocity.len(), velocity.walkSpeed);
      }
    }
  }

  private boolean hasMovementIntent(int entityId) {
    if (!mPathfind.has(entityId)) return false;
    Pathfind pathfind = mPathfind.get(entityId);
    return pathfind.path != null
        || pathfind.targetEntityId != Engine.INVALID_ENTITY
        || !pathfind.target.isZero(0.0001f)
        || !pathfind.destination.isZero(0.0001f);
  }

  static int movementAnimationRate(
      int baseAnimSpeed, float movementSpeed, float baseVelocity) {
    if (baseAnimSpeed <= 0) return 1;
    int scaled = scaleAnimationSpeed(baseAnimSpeed, movementSpeed, baseVelocity);
    return scaled > 0 ? scaled : baseAnimSpeed;
  }

  private void setMovementMode(int entityId, byte mode, Vector2 velocity) {
    CofReference reference = mCofReference.get(entityId);
    if (reference.mode != mode && Riiablo.game != null && entityId == Riiablo.game.player) {
      MovementModes modes = mMovementModes.get(entityId);
      log.info("[PLAYER_MOVEMENT_MODE] entity={} mode={}->{} speed={} path={}",
          entityId,
          movementModeName(modes, reference.mode),
          movementModeName(modes, mode),
          velocity.len(),
          mPathfind.has(entityId));
    }
    cofs.setMode(entityId, mode);
  }

  private static String movementModeName(MovementModes modes, byte mode) {
    if (mode == modes.NU) return "NU";
    if (mode == modes.WL) return "WL";
    if (mode == modes.RN) return "RN";
    return Byte.toString(mode);
  }

  private boolean isEffectivelyRunning(int entityId) {
    return isRunRequested(mRunning.has(entityId), mTemporaryRunning.has(entityId))
        && canRun(entityId);
  }

  static boolean isRunRequested(boolean persistentRun, boolean temporaryRun) {
    return persistentRun || temporaryRun;
  }

  /** A depleted stamina pool temporarily forces walking without changing the run preference. */
  private boolean canRun(int entityId) {
    return !mAttributes.has(entityId)
        || StaminaSystem.hasRunStamina(mAttributes.get(entityId));
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
