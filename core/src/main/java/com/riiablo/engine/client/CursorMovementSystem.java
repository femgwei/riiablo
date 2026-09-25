package com.riiablo.engine.client;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.net.Socket;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.UIUtils;
import com.badlogic.gdx.utils.TimeUtils;

import com.riiablo.Riiablo;
import com.riiablo.camera.IsometricCamera;
import com.riiablo.engine.Engine;
import com.riiablo.engine.client.component.Hovered;
import com.riiablo.engine.server.Actioneer;
import com.riiablo.engine.server.InteractionRange;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Networked;
import com.riiablo.engine.server.component.Size;
import com.riiablo.engine.server.component.Target;
import com.riiablo.engine.server.skill.NativeSkillResolver;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Skills;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.map.RenderSystem;
import com.riiablo.profiler.ProfilerSystem;
import com.riiablo.save.ItemController;
import com.riiablo.skill.SkillCodes;

@Wire(failOnNull = false)
public class CursorMovementSystem extends BaseSystem {
  private static final String TAG = "CursorMovementSystem";
  static final int MELEE_APPROACH_RANGE_BONUS = 0;
  private static final float HELD_PATH_REFRESH_DISTANCE = 1.5f;
  private static final float HELD_PATH_DIRECTION_COS = 0.99026805f; // 8 degrees

  protected ComponentMapper<Target> mTarget;
  protected ComponentMapper<Networked> mNetworked;
  protected ComponentMapper<Pathfind> mPathfind;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Interactable> mInteractable;
  protected ComponentMapper<Size> mSize;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;

  protected RenderSystem renderer;
  protected MenuManager menuManager;
  protected DialogManager dialogManager;
  protected ProfilerSystem profiler;
  protected Actioneer actioneer;
  protected HoveredManager hoveredManager;
  protected DeathHandler deathHandler;
  protected ClientNetworkReceiver networkReceiver;

  @Wire(name = "iso")
  protected IsometricCamera iso;

  @Wire(name = "client.socket", failOnNull = false)
  protected Socket socket;

  @Wire(name = "map")
  protected Map map;

  @Wire(name = "stage")
  protected Stage stage;

  @Wire(name = "scaledStage")
  protected Stage scaledStage;

  @Wire(name = "itemController")
  protected ItemController itemController;

  EntitySubscription hoveredSubscriber;
  boolean requireRelease;
  /** One-shot left-click captured on the render frame and consumed by the next sim tick. */
  private final PointerClickQueue pendingLeftClicks = new PointerClickQueue();
  /** Right-side skills use the same edge queue so a short Throw click cannot miss a 25 Hz tick. */
  private final PointerClickQueue pendingRightClicks = new PointerClickQueue();
  private boolean sampledLeftDown;
  private boolean sampledRightDown;
  private boolean pendingLeftRelease;
  private float pendingLeftReleaseX;
  private float pendingLeftReleaseY;
  int lastInteractionTraceTarget = Engine.INVALID_ENTITY;
  long lastInteractionTraceMillis;
  int lastAttackRangeTarget = Engine.INVALID_ENTITY;
  int lastAttackRangeSkill = Integer.MIN_VALUE;
  boolean lastAttackRangeInMelee;
  boolean lastAttackRangeCanThrow;
  boolean lastAttackRangeRangedNormal;
  boolean attackRangeTraceInitialized;
  long lastAttackRangeTraceMillis;

  private final Vector2 tmpVec2 = new Vector2();

  @Override
  protected void initialize() {
    hoveredSubscriber = world.getAspectSubscriptionManager().get(Aspect.all(Hovered.class));
  }

  @Override
  protected void processSystem() {
    if (profiler != null && profiler.hit()) {
      traceBlockedClick("profiler");
      return;
    }
    
    // D2MOD: Check if player is dead, if so, block all input except ESC key
    final int playerId = renderer.getSrc();
    if (deathHandler != null && deathHandler.isPlayerDead(playerId)) {
      // Player is dead, block all movement/attack input
      // ESC key handling for respawn should be done elsewhere (e.g., GameScreen)
      return;
    }

    // A short click can begin and end between two 25 Hz simulation ticks. The
    // render frame captures its coordinates; consume it once here so a ground
    // move is never lost just because the button was released before the next
    // fixed step.
    if (consumePendingMercenaryPotionDrop()) return;
    if (consumePendingLeftPress(playerId)) return;
    if (consumePendingRightPress(playerId)) return;
    
    stage.screenToStageCoordinates(tmpVec2.set(Gdx.input.getX(), Gdx.input.getY()));
    Actor hit1 = stage.hit(tmpVec2.x, tmpVec2.y, true);
    scaledStage.screenToStageCoordinates(tmpVec2.set(Gdx.input.getX(), Gdx.input.getY()));
    Actor hit2 = scaledStage.hit(tmpVec2.x, tmpVec2.y, true);
    boolean hit = hit1 != null || hit2 != null;
    if (hit) {
      traceBlockedClick(hit1 != null ? "stage:" + hit1.getClass().getSimpleName()
          : "scaledStage:" + hit2.getClass().getSimpleName());
      return;
    }

    final boolean leftPressed = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
    final boolean rightPressed = Gdx.input.isButtonPressed(Input.Buttons.RIGHT);
    final boolean shiftDown = UIUtils.shift();
    if ((leftPressed && shiftDown) || rightPressed) {
      final int targetId = getHovered(playerId);
      if (targetId != Engine.INVALID_ENTITY && (isTargetDead(targetId) || actioneer.didLastAttackTargetDie(playerId))) {
        actioneer.moveTo(playerId, Engine.INVALID_ENTITY);
      } else {
        final int skillId = Riiablo.charData.getAction(
            leftPressed ? Input.Buttons.LEFT : Input.Buttons.RIGHT);
        iso.agg(tmpVec2.set(Gdx.input.getX(), Gdx.input.getY())).unproject().toWorld();
        if (!isSkillAllowedForInput(playerId, skillId)) {
          actioneer.moveTo(playerId, Engine.INVALID_ENTITY);
          return;
        }
        // Shift-click/right-click bypasses updateLeft(). Keep the same melee
        // range contract here so normal Attack cannot damage a distant target.
        // Bows and crossbows are the exception: their normal Attack is ranged.
        if (!canStartCast(playerId)) {
          return;
        }
        if (rightPressed && !shiftDown && shouldMoveOnUntargetedRightClick(
            targetId, false, skillEntry(skillId))) {
          moveToGround(playerId, tmpVec2);
        } else if (targetId != Engine.INVALID_ENTITY && isMeleeNormalAttack(skillId)
            && !actioneer.isInMeleeRange(
                playerId, targetId, MELEE_APPROACH_RANGE_BONUS)) {
          Gdx.app.log(TAG, "[ATTACK_RANGE] rejected remote normal attack player=" + playerId
              + " skill=" + skillId + " target=" + targetId + " mode=melee");
          if (shiftDown) {
            requestCast(playerId, skillId, Engine.INVALID_ENTITY, tmpVec2);
          } else {
            moveToTarget(playerId, targetId);
          }
        } else {
          requestCast(playerId, skillId, targetId, tmpVec2);
        }
      }
    } else {
      updateLeft();
    }
  }

  /** Samples the input edge once per render frame, before the fixed-step loop. */
  public void capturePointerInput() {
    if (Gdx.input == null) return;
    boolean leftDown = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
    if (!leftDown && sampledLeftDown) {
      pendingLeftRelease = true;
      pendingLeftReleaseX = Gdx.input.getX();
      pendingLeftReleaseY = Gdx.input.getY();
    }
    if (leftDown && !sampledLeftDown) {
      pendingLeftClicks.capture(Gdx.input.getX(), Gdx.input.getY(), TimeUtils.millis(),
          networkReceiver == null ? 0L : networkReceiver.latestServerTick(), UIUtils.shift());
    }
    sampledLeftDown = leftDown;
    boolean rightDown = Gdx.input.isButtonPressed(Input.Buttons.RIGHT);
    if (rightDown && !sampledRightDown) {
      pendingRightClicks.capture(Gdx.input.getX(), Gdx.input.getY(), TimeUtils.millis(),
          networkReceiver == null ? 0L : networkReceiver.latestServerTick(), UIUtils.shift());
    }
    sampledRightDown = rightDown;
  }

  /**
   * Scene2D ClickListener only receives a release when its own actor captured
   * the press. A potion drag starts in an inventory slot, so the portrait must
   * consume the render-frame release explicitly when the cursor is over it.
   */
  private boolean consumePendingMercenaryPotionDrop() {
    if (!pendingLeftRelease) return false;
    pendingLeftRelease = false;
    if (Riiablo.game == null || Riiablo.game.mercenaryHud == null
        || !Riiablo.game.mercenaryHud.isVisible()) return false;
    stage.screenToStageCoordinates(tmpVec2.set(pendingLeftReleaseX, pendingLeftReleaseY));
    if (!Riiablo.game.mercenaryHud.containsStagePoint(tmpVec2.x, tmpVec2.y)) return false;
    // Keep the UI click consumed even if the item is not a healing potion; the
    // invalid item remains on the cursor instead of being dropped on the map.
    Riiablo.game.mercenaryHud.useCursorPotion();
    return true;
  }

  private boolean consumePendingLeftPress(int src) {
    PointerClickQueue.Click click = pendingLeftClicks.poll();
    if (click == null) return false;
    float pendingLeftX = click.screenX;
    float pendingLeftY = click.screenY;

    // UI clicks must remain owned by Stage. Use the captured coordinates rather
    // than the current cursor, which may already have moved by this tick.
    stage.screenToStageCoordinates(tmpVec2.set(pendingLeftX, pendingLeftY));
    Actor hit1 = stage.hit(tmpVec2.x, tmpVec2.y, true);
    scaledStage.screenToStageCoordinates(tmpVec2.set(pendingLeftX, pendingLeftY));
    Actor hit2 = scaledStage.hit(tmpVec2.x, tmpVec2.y, true);
    if (hit1 != null || hit2 != null) {
      traceBlockedClick(hit1 != null ? "queued-stage:" + hit1.getClass().getSimpleName()
          : "queued-scaledStage:" + hit2.getClass().getSimpleName());
      return true;
    }

    long age = Math.max(0L, TimeUtils.millis() - click.capturedAtMillis);
    long consumedTick = networkReceiver == null ? 0L : networkReceiver.latestServerTick();
    long tickDelay = inputTickDelay(click.observedTick, consumedTick);
    if (age > 40L || tickDelay > 1L) {
      Gdx.app.log(TAG, "[INPUT_QUEUE] phase=consume player=" + src
          + " ageMs=" + age + " observedTick=" + click.observedTick
          + " consumedTick=" + consumedTick + " tickDelay=" + tickDelay
          + " x=" + pendingLeftX + " y=" + pendingLeftY);
    }

    if (click.shiftDown) {
      if (!canStartCast(src)) {
        pendingLeftClicks.restore(click);
        return false;
      }
      int skillId = Riiablo.charData.getAction(Input.Buttons.LEFT);
      if (!isSkillAllowedForInput(src, skillId)) {
        actioneer.moveTo(src, Engine.INVALID_ENTITY);
        return true;
      }
      int targetId = getHoveredAt(src, pendingLeftX, pendingLeftY);
      iso.agg(tmpVec2.set(pendingLeftX, pendingLeftY)).unproject().toWorld();
      if (targetId != Engine.INVALID_ENTITY && isMeleeNormalAttack(skillId)
          && !actioneer.isInMeleeRange(src, targetId, MELEE_APPROACH_RANGE_BONUS)) {
        targetId = Engine.INVALID_ENTITY;
      }
      requestCast(src, skillId, targetId, tmpVec2);
      return true;
    }

    // If HoveredManager already observed the clicked entity, preserve the
    // normal interaction/attack path. Otherwise this is a ground click and we
    // can immediately enqueue its world destination from the captured point.
    if (getHoveredAt(src, pendingLeftX, pendingLeftY) != Engine.INVALID_ENTITY) {
      // A click arriving while the previous attack/throw sequence is still
      // active is not actionable yet. Do not swallow it: fall through to the
      // legacy edge/release path so a short throw click can be retried once
      // the sequence becomes interruptible.
      if (touchDown(src, pendingLeftX, pendingLeftY)) return true;
      pendingLeftClicks.restore(click);
      return false;
    }
    iso.agg(tmpVec2.set(pendingLeftX, pendingLeftY)).unproject().toWorld();
    if (actioneer.canInterrupt(src)) moveToGround(src, tmpVec2);
    return true;
  }

  private boolean consumePendingRightPress(int src) {
    PointerClickQueue.Click click = pendingRightClicks.poll();
    if (click == null) return false;

    stage.screenToStageCoordinates(tmpVec2.set(click.screenX, click.screenY));
    if (Riiablo.game != null && Riiablo.game.mercenaryHud != null
        && Riiablo.game.mercenaryHud.isVisible()
        && Riiablo.game.mercenaryHud.containsStagePoint(tmpVec2.x, tmpVec2.y)) {
      // The simulation-side pointer queue can consume the click before
      // Scene2D's ClickListener sees it. Route the portrait action here too.
      if (Riiablo.game.hirelingPanel != null) {
        Riiablo.game.setLeftPanel(Riiablo.game.hirelingPanel);
      }
      return true;
    }
    Actor hit1 = stage.hit(tmpVec2.x, tmpVec2.y, true);
    scaledStage.screenToStageCoordinates(tmpVec2.set(click.screenX, click.screenY));
    Actor hit2 = scaledStage.hit(tmpVec2.x, tmpVec2.y, true);
    if (hit1 != null || hit2 != null) return true;

    if (!canStartCast(src)) {
      pendingRightClicks.restore(click);
      return false;
    }

    int targetId = getHoveredAt(src, click.screenX, click.screenY);
    if (targetId != Engine.INVALID_ENTITY
        && (isTargetDead(targetId) || actioneer.didLastAttackTargetDie(src))) {
      actioneer.moveTo(src, Engine.INVALID_ENTITY);
      return true;
    }
    int skillId = Riiablo.charData.getAction(Input.Buttons.RIGHT);
    if (!isSkillAllowedForInput(src, skillId)) {
      actioneer.moveTo(src, Engine.INVALID_ENTITY);
      return true;
    }
    iso.agg(tmpVec2.set(click.screenX, click.screenY)).unproject().toWorld();
    if (targetId != Engine.INVALID_ENTITY && mPosition.has(targetId)) {
      tmpVec2.set(mPosition.get(targetId).position);
    }
    if (shouldMoveOnUntargetedRightClick(
        targetId, click.shiftDown, skillEntry(skillId))) {
      moveToGround(src, tmpVec2);
      return true;
    }
    if (targetId != Engine.INVALID_ENTITY && isMeleeNormalAttack(skillId)
        && !actioneer.isInMeleeRange(src, targetId, MELEE_APPROACH_RANGE_BONUS)) {
      if (click.shiftDown) {
        requestCast(src, skillId, Engine.INVALID_ENTITY, tmpVec2);
      } else {
        moveToTarget(src, targetId);
      }
      return true;
    }
    requestCast(src, skillId, targetId, tmpVec2);
    return true;
  }

  /** Returns the authoritative snapshot-Tick delay for a captured click. */
  static long inputTickDelay(long capturedTick, long consumedTick) {
    if (capturedTick <= 0L || consumedTick <= 0L) return -1L;
    return Math.max(0L, consumedTick - capturedTick);
  }

  private void updateLeft() {
    int src = renderer.getSrc();
    boolean pressed = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
    if (pressed && !requireRelease) {
      Item cursor = Riiablo.cursor.getItem();
      if (cursor != null) {
        itemController.cursorToGround();
        requireRelease = true;
        return;
      }

      // exiting dialog should block all input until button is released to prevent menu from closing the following frame
      if (dialogManager.getDialog() != null) {
        dialogManager.setDialog(null);
        requireRelease = true;
        return;
      } else if (menuManager.getMenu() != null) {
        menuManager.setMenu(null, Engine.INVALID_ENTITY);
      }

      // set target entity -- unsets and interacts when within range
      boolean touched = touchDown(src);
      if (!touched && actioneer.canInterrupt(src)) {
        iso.agg(tmpVec2.set(Gdx.input.getX(), Gdx.input.getY())).unproject().toWorld();
        moveToGround(src, tmpVec2);
      }
    } else if (!pressed && actioneer.canInterrupt(src)) {
      requireRelease = false;
      actioneer.clearLastAttackTargetDied(src);
      Target target = mTarget.get(src);
      if (target != null) {
        int targetId = target.target;
        Vector2 srcPos = mPosition.get(src).position;
        if (mPosition.get(targetId) == null) {
          actioneer.moveTo(src, Engine.INVALID_ENTITY);
          return;
        }
        Vector2 targetPos = mPosition.get(targetId).position;
        // not interactable -> attacking? check weapon range to auto attack or cast spell
        Interactable interactable = mInteractable.get(targetId);
        final float dst = srcPos.dst(targetPos);
        if (interactable != null
            && InteractionRange.contains(dst, interactable, mSize.get(src))) {
          traceInteraction(src, targetId, interactable, dst, "trigger", true);
          actioneer.moveTo(src, Engine.INVALID_ENTITY);
          actioneer.faceTarget(src, targetId);
          interactable.interactor.interact(src, targetId);
        } else if (interactable != null) {
          traceInteraction(src, targetId, interactable, dst, "approach", false);
        } else if (interactable == null) {
          if (isTargetDead(targetId)) {
            actioneer.moveTo(src, Engine.INVALID_ENTITY);
            return;
          }
          if (actioneer.didLastAttackTargetDie(src)) return;
          
          // Check if in melee range
          boolean inMeleeRange = actioneer.isInMeleeRange(
              src, targetId, MELEE_APPROACH_RANGE_BONUS);
          
          final int selectedSkillId = Riiablo.charData.getAction(Input.Buttons.LEFT);
          final boolean explicitThrowSkill = isThrowSkill(selectedSkillId);
          final boolean rangedNormalAttack = isRangedNormalAttack(selectedSkillId);

          // Check if the selected skill is an explicit throw and the equipped
          // weapon is throwable and has quantity. A throwable weapon does not
          // turn the normal Attack skill into a ranged attack.
          boolean canThrow = false;
          Item weapon = Riiablo.charData.getItems().getEquippedThrowableWeapon();
          
          if (weapon != null && weapon.attrs != null) {
            com.riiablo.attributes.StatRef quantity = weapon.attrs.base().get(Stat.quantity);
            canThrow = canStartExplicitThrow(explicitThrowSkill, true,
                quantity != null ? quantity.asInt() : 0);
          }

          traceAttackRange(src, targetId, selectedSkillId, dst, inMeleeRange,
              explicitThrowSkill, canThrow, rangedNormalAttack);

          // Bow/crossbow Attack is a normal ranged attack, even though it uses
          // SkillCodes.attack rather than an explicit bow skill.  Treating
          // only Throw as ranged made an unshifted left click chase the target
          // until melee range before ServerSkillSystem could create the arrow.
          if (canStartTargetedAttack(inMeleeRange, canThrow, rangedNormalAttack)) {
            requestCast(src, selectedSkillId, targetId, targetPos);
            // A release is a single attack request.  Clear the interaction
            // target immediately; retaining it caused every subsequent
            // interruptible frame to replay the attack animation forever.
            actioneer.moveTo(src, Engine.INVALID_ENTITY);
          }
        }
      }
    }
  }

  private void moveToGround(int src, Vector2 destination) {
    Position position = mPosition.get(src);
    Pathfind pathfind = mPathfind.get(src);
    if (!shouldRefreshHeldGroundPath(
        position != null ? position.position : null, pathfind, destination)) {
      return;
    }
    closeTradePanelsForMovement();
    actioneer.moveTo(src, destination);
  }

  private void moveToTarget(int src, int target) {
    closeTradePanelsForMovement();
    actioneer.moveTo(src, target);
  }

  private void closeTradePanelsForMovement() {
    if (Riiablo.game != null) Riiablo.game.closeTradePanelsForMovement();
  }

  static boolean shouldRefreshHeldGroundPath(
      Vector2 position, Pathfind pathfind, Vector2 requestedDestination) {
    if (position == null || pathfind == null || requestedDestination == null) return true;
    if (pathfind.targetEntityId != Engine.INVALID_ENTITY) return true;

    float remainingX = pathfind.destination.x - position.x;
    float remainingY = pathfind.destination.y - position.y;
    float remainingLen2 = remainingX * remainingX + remainingY * remainingY;
    if (remainingLen2 <= HELD_PATH_REFRESH_DISTANCE * HELD_PATH_REFRESH_DISTANCE) return true;

    float requestedX = requestedDestination.x - position.x;
    float requestedY = requestedDestination.y - position.y;
    float requestedLen2 = requestedX * requestedX + requestedY * requestedY;
    if (requestedLen2 <= 0.0001f) return false;

    float alignment = (remainingX * requestedX + remainingY * requestedY)
        / (float) Math.sqrt(remainingLen2 * requestedLen2);
    return alignment < HELD_PATH_DIRECTION_COS;
  }

  private void requestCast(int sourceId, int skillId, int targetId, Vector2 targetVec) {
    // The HUD tint is an affordance, not an input gate.  Mouse clicks can hit
    // the world directly, so repeat the native InTown check here before
    // invoking Actioneer (local) or sending a packet (multiplayer).  Without
    // this guard a red Throw icon still starts the throw animation.
    if (!isSkillAllowedForInput(sourceId, skillId)) {
      actioneer.moveTo(sourceId, Engine.INVALID_ENTITY);
      return;
    }
    if (socket == null) {
      actioneer.cast(sourceId, skillId, targetId, targetVec);
      return;
    }
    int targetServerId = Engine.INVALID_ENTITY;
    if (targetId != Engine.INVALID_ENTITY && mNetworked.has(targetId)) {
      targetServerId = mNetworked.get(targetId).serverId;
    }
    long observedTick = networkReceiver == null ? 0L : networkReceiver.latestServerTick();
    long targetTick = observedTick == 0L ? 0L : observedTick + 2L;
    NetworkedActionSender.cast(socket, skillId, targetServerId, targetVec,
        NetworkedActionSender.nextCombatSequence(), observedTick, targetTick);
  }

  /**
   * Client-side side-effect guard shared by queued clicks and the legacy
   * press/release path.  It intentionally mirrors the native table rule but
   * does not replace ServerSkillSystem's authoritative validation.
   */
  private boolean isSkillAllowedForInput(int sourceId, int skillId) {
    if (!isPlayerInTown(sourceId)) return true;
    Skills.Entry skill = Riiablo.files != null && Riiablo.files.skills != null
        ? Riiablo.files.skills.get(skillId) : null;
    boolean allowed = com.riiablo.engine.server.skill.NativeSkillResolver
        .isAllowedInTown(skill, true);
    if (!allowed && Gdx.app != null) {
      Gdx.app.log(TAG, "[SKILL_CAST] phase=input_reject skill=" + skillId
          + " reason=town_in_town_flag player=" + sourceId);
    }
    return allowed;
  }

  private boolean isPlayerInTown(int playerId) {
    if (Riiablo.engine == null || playerId < 0) return false;
    try {
      ComponentMapper<MapWrapper> mapper = Riiablo.engine.getMapper(MapWrapper.class);
      if (mapper == null || !mapper.has(playerId)) return false;
      MapWrapper wrapper = mapper.get(playerId);
      return wrapper != null && wrapper.zone != null && wrapper.zone.isTown();
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  /** Returns whether a new cast may be submitted this frame. */
  private boolean canStartCast(int entityId) {
    return actioneer.canInterrupt(entityId)
        && !actioneer.hasCasting(entityId)
        && !actioneer.hasSequence(entityId);
  }

  private int getHovered(int src) {
    return getHoveredAt(src, Gdx.input.getX(), Gdx.input.getY());
  }

  /** Delegates captured-coordinate hit testing to the hover/selection owner. */
  private int getHoveredAt(int src, float screenX, float screenY) {
    return hoveredManager.getHoveredAt(src, screenX, screenY);
  }

  private boolean touchDown(int src) {
    return touchDown(src, Gdx.input.getX(), Gdx.input.getY());
  }

  private boolean touchDown(int src, float screenX, float screenY) {
    if (actioneer.hasCasting(src) || actioneer.hasSequence(src)) return false;
    if (actioneer.didLastAttackTargetDie(src)) return false;
    
    int target = getHoveredAt(src, screenX, screenY);
    if (target == Engine.INVALID_ENTITY) {
      traceNoInteractionTarget(src);
      return false;
    }

    Interactable selectedInteractable = mInteractable.get(target);
    if (selectedInteractable != null) {
      Position srcPosition = mPosition.get(src);
      Position targetPosition = mPosition.get(target);
      float distance = srcPosition != null && targetPosition != null
          ? srcPosition.position.dst(targetPosition.position)
          : Float.NaN;
      traceInteraction(src, target, selectedInteractable, distance, "click", true);
    } else {
      if (isTargetDead(target)) return false;

      // A disabled offensive skill must not turn a town click into an
      // approach/attack command.  Keep interactables above unaffected so NPC
      // and waypoint clicks remain usable while an attack icon is red.
      final int selectedSkill = Riiablo.charData.getAction(Input.Buttons.LEFT);
      if (!isSkillAllowedForInput(src, selectedSkill)) {
        actioneer.moveTo(src, Engine.INVALID_ENTITY);
        return true;
      }
      
      Vector2 targetPos = mPosition.get(target).position;
      float dst = mPosition.get(src).position.dst(targetPos);
      
      // Check if in melee range
      boolean inMeleeRange = actioneer.isInMeleeRange(
          src, target, MELEE_APPROACH_RANGE_BONUS);
      
      final int selectedSkillId = selectedSkill;
      final boolean explicitThrowSkill = isThrowSkill(selectedSkillId);
      final boolean rangedNormalAttack = isRangedNormalAttack(selectedSkillId);

      // Check if the selected skill is an explicit throw and the equipped
      // weapon is throwable and has quantity. Normal Attack remains point-
      // blank melee even when a throwable weapon is equipped.
      boolean canThrow = false;
      Item weapon = Riiablo.charData.getItems().getEquippedThrowableWeapon();

      if (weapon != null && weapon.attrs != null) {
        com.riiablo.attributes.StatRef quantity = weapon.attrs.base().get(Stat.quantity);
        canThrow = canStartExplicitThrow(explicitThrowSkill, true,
            quantity != null ? quantity.asInt() : 0);
      }
      
      traceAttackRange(src, target, selectedSkillId, dst, inMeleeRange,
          explicitThrowSkill, canThrow, rangedNormalAttack);

      if (canStartTargetedAttack(inMeleeRange, canThrow, rangedNormalAttack)) {
        requestCast(src, selectedSkillId, target, targetPos);
        return true;
      }
    }
    
    Target currentTarget = mTarget.get(src);
    Pathfind currentPath = mPathfind.get(src);
    if (shouldIssueTargetMove(currentTarget, currentPath, target)) {
      moveToTarget(src, target);
    }
    return true;
  }

  private static Skills.Entry skillEntry(int skillId) {
    return skillId >= 0 && Riiablo.files != null && Riiablo.files.skills != null
        ? Riiablo.files.skills.get(skillId) : null;
  }

  static boolean shouldMoveOnUntargetedRightClick(
      int targetId, boolean shiftDown, Skills.Entry skill) {
    return targetId == Engine.INVALID_ENTITY && !shiftDown
        && NativeSkillResolver.isTargetableOnly(skill);
  }

  static boolean shouldIssueTargetMove(Target currentTarget, Pathfind currentPath,
      int requestedTarget) {
    return currentTarget == null || currentTarget.target != requestedTarget
        || currentPath == null || currentPath.targetEntityId != requestedTarget;
  }

  private void traceInteraction(int src, int target, Interactable interactable,
      float distance, String phase, boolean force) {
    if (Gdx.app == null) return;
    long now = TimeUtils.millis();
    if (!force
        && lastInteractionTraceTarget == target
        && now - lastInteractionTraceMillis < 1000L) {
      return;
    }

    lastInteractionTraceTarget = target;
    lastInteractionTraceMillis = now;
    Gdx.app.log(TAG, "Interaction target: phase=" + phase
        + " player=" + src + " entity=" + target
        + " distance=" + distance + " range=" + interactable.range
        + " effectiveRange=" + InteractionRange.effective(interactable, mSize.get(src))
        + " hovered=" + hoveredSubscriber.getEntities().size());
  }

  private void traceBlockedClick(String reason) {
    if (!Gdx.input.isButtonPressed(Input.Buttons.LEFT)) return;
    traceInput("blocked reason=" + reason);
  }

  private void traceNoInteractionTarget(int src) {
    traceInput("miss player=" + src
        + " cursor=(" + Gdx.input.getX() + "," + Gdx.input.getY() + ")"
        + " hovered=" + hoveredSubscriber.getEntities().size());
  }

  private void traceInput(String message) {
    if (Gdx.app == null) return;
    long now = TimeUtils.millis();
    if (now - lastInteractionTraceMillis < 1000L) return;
    lastInteractionTraceTarget = Engine.INVALID_ENTITY;
    lastInteractionTraceMillis = now;
    Gdx.app.log(TAG, "Interaction input: " + message);
  }

  /**
   * Returns whether a selected skill explicitly uses the throw/left-hand throw
   * pipeline. Weapon type alone is intentionally not enough: D2 lets a
   * javelin, throwing knife, or throwing axe perform a normal melee Attack.
   */
  private static boolean isThrowSkill(int skillId) {
    if (skillId < 0) return false;
    if (skillId == SkillCodes.throw_ || skillId == SkillCodes.left_hand_throw) {
      return true;
    }
    Skills.Entry skill = Riiablo.files.skills.get(skillId);
    return skill != null && (skill.srvdofunc == 3 || skill.srvdofunc == 5
        || skill.cltdofunc == 3 || skill.cltdofunc == 5);
  }

  /**
   * Explicit Throw is a ranged action. {@code Weapons.RangeAdder} describes
   * melee reach and must not be used as a maximum projectile targeting range.
   * World bounds, line collision and the missile lifetime remain authoritative
   * in D2GS/ServerSkillSystem.
   */
  static boolean canStartExplicitThrow(
      boolean explicitThrowSkill, boolean hasThrowableWeapon, int quantity) {
    return explicitThrowSkill && hasThrowableWeapon && quantity > 0;
  }

  static boolean canStartTargetedAttack(
      boolean inMeleeRange, boolean canThrow, boolean rangedNormalAttack) {
    return inMeleeRange || canThrow || rangedNormalAttack;
  }

  private boolean isRangedNormalAttack(int skillId) {
    return skillId == SkillCodes.attack
        && Riiablo.charData != null
        && Riiablo.charData.getItems() != null
        && Riiablo.charData.getItems().getEquippedRangedWeapon() != null;
  }

  private boolean isMeleeNormalAttack(int skillId) {
    return skillId == SkillCodes.attack && !isRangedNormalAttack(skillId);
  }

  private void traceAttackRange(int src, int targetId, int skillId, float distance,
      boolean inMeleeRange, boolean explicitThrowSkill, boolean canThrow,
      boolean rangedNormalAttack) {
    long now = TimeUtils.millis();
    boolean stateChanged = !attackRangeTraceInitialized
        || lastAttackRangeTarget != targetId
        || lastAttackRangeSkill != skillId
        || lastAttackRangeInMelee != inMeleeRange
        || lastAttackRangeCanThrow != canThrow
        || lastAttackRangeRangedNormal != rangedNormalAttack;
    if (!stateChanged && now - lastAttackRangeTraceMillis < 1000L) return;

    Item throwable = Riiablo.charData != null && Riiablo.charData.getItems() != null
        ? Riiablo.charData.getItems().getEquippedThrowableWeapon() : null;
    int quantity = -1;
    if (throwable != null && throwable.attrs != null) {
      com.riiablo.attributes.StatRef quantityRef = throwable.attrs.base().get(Stat.quantity);
      quantity = quantityRef != null ? quantityRef.asInt() : -1;
    }
    Gdx.app.log(TAG, "[ATTACK_RANGE] player=" + src + " target=" + targetId
        + " skill=" + skillId + " distance=" + distance
        + " mode=" + (explicitThrowSkill ? "throw"
            : rangedNormalAttack ? "bow_normal" : "melee")
        + " inMelee=" + inMeleeRange + " canThrow=" + canThrow
        + " throwable=" + (throwable != null ? throwable.code : "none")
        + " quantity=" + quantity);
    lastAttackRangeTarget = targetId;
    lastAttackRangeSkill = skillId;
    lastAttackRangeInMelee = inMeleeRange;
    lastAttackRangeCanThrow = canThrow;
    lastAttackRangeRangedNormal = rangedNormalAttack;
    attackRangeTraceInitialized = true;
    lastAttackRangeTraceMillis = now;
  }

  /**
   * D2MOD: Check if target entity is dead
   * @param targetId The target entity ID
   * @return true if target is dead or doesn't exist
   */
  private boolean isTargetDead(int targetId) {
    if (targetId == Engine.INVALID_ENTITY) {
      return true;
    }
    if (!mAttributesWrapper.has(targetId)) {
      return true; // Entity doesn't exist or has no attributes
    }
    Attributes attrs = mAttributesWrapper.get(targetId).attrs;
    com.riiablo.attributes.StatRef hitpoints = attrs.get(Stat.hitpoints);
    if (hitpoints == null) {
      return false; // No hitpoints stat, assume alive
    }
    return hitpoints.asFixed() <= 0f;
  }
}
