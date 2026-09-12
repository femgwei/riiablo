package com.riiablo.engine.client;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;

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
import com.riiablo.engine.client.component.BBoxWrapper;
import com.riiablo.engine.client.component.Hovered;
import com.riiablo.engine.client.component.Selectable;
import com.riiablo.engine.server.Actioneer;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.Object;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Networked;
import com.riiablo.engine.server.component.Target;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Skills;
import com.riiablo.item.Item;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Type;
import com.riiablo.map.Map;
import com.riiablo.map.RenderSystem;
import com.riiablo.profiler.ProfilerSystem;
import com.riiablo.save.ItemController;
import com.riiablo.skill.SkillCodes;

@Wire(failOnNull = false)
public class CursorMovementSystem extends BaseSystem {
  private static final String TAG = "CursorMovementSystem";

  protected ComponentMapper<Target> mTarget;
  protected ComponentMapper<Networked> mNetworked;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Interactable> mInteractable;
  protected ComponentMapper<BBoxWrapper> mBBoxWrapper;
  protected ComponentMapper<Object> mObject;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;

  protected RenderSystem renderer;
  protected MenuManager menuManager;
  protected DialogManager dialogManager;
  protected ProfilerSystem profiler;
  protected Actioneer actioneer;
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
  EntitySubscription selectableSubscriber;
  EntitySubscription waypointInputSubscriber;
  boolean requireRelease;
  /** One-shot left-click captured on the render frame and consumed by the next sim tick. */
  private final PointerClickQueue pendingLeftClicks = new PointerClickQueue();
  /** Right-side skills use the same edge queue so a short Throw click cannot miss a 25 Hz tick. */
  private final PointerClickQueue pendingRightClicks = new PointerClickQueue();
  private boolean sampledLeftDown;
  private boolean sampledRightDown;
  int lastInteractionTraceTarget = Engine.INVALID_ENTITY;
  long lastInteractionTraceMillis;
  int lastAttackRangeTarget = Engine.INVALID_ENTITY;
  int lastAttackRangeSkill = Integer.MIN_VALUE;
  boolean lastAttackRangeInMelee;
  boolean lastAttackRangeCanThrow;
  boolean attackRangeTraceInitialized;
  long lastAttackRangeTraceMillis;

  private final Vector2 tmpVec2 = new Vector2();
  private final Vector2 cursorScreen = new Vector2();
  private final Vector2 entityScreen = new Vector2();

  @Override
  protected void initialize() {
    hoveredSubscriber = world.getAspectSubscriptionManager().get(Aspect.all(Hovered.class));
    selectableSubscriber = world.getAspectSubscriptionManager().get(
        Aspect.all(Selectable.class, Position.class, BBoxWrapper.class));
    waypointInputSubscriber = world.getAspectSubscriptionManager().get(
        Aspect.all(Interactable.class, Position.class, BBoxWrapper.class, Object.class));
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
    if ((leftPressed && UIUtils.shift()) || Gdx.input.isButtonPressed(Input.Buttons.RIGHT)) {
      final int targetId = getHovered(playerId);
      if (targetId != Engine.INVALID_ENTITY && (isTargetDead(targetId) || actioneer.didLastAttackTargetDie(playerId))) {
        actioneer.moveTo(playerId, Engine.INVALID_ENTITY);
      } else {
        final int skillId = Riiablo.charData.getAction(leftPressed ? Input.Buttons.LEFT : Input.Buttons.RIGHT);
        iso.agg(tmpVec2.set(Gdx.input.getX(), Gdx.input.getY())).unproject().toWorld();
        // Shift-click/right-click bypasses updateLeft(). Keep the same melee
        // range contract here so normal Attack cannot damage a distant target.
        // Bows and crossbows are the exception: their normal Attack is ranged.
        if (!canStartCast(playerId)) {
          return;
        }
        if (targetId != Engine.INVALID_ENTITY && isMeleeNormalAttack(skillId)
            && !actioneer.isInMeleeRange(playerId, targetId, 3)) {
          Gdx.app.log(TAG, "[ATTACK_RANGE] rejected remote normal attack player=" + playerId
              + " skill=" + skillId + " target=" + targetId + " mode=melee");
          actioneer.moveTo(playerId, targetId);
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
    if (leftDown && !sampledLeftDown) {
      pendingLeftClicks.capture(Gdx.input.getX(), Gdx.input.getY(), TimeUtils.millis(),
          networkReceiver == null ? 0L : networkReceiver.latestServerTick());
    }
    sampledLeftDown = leftDown;
    boolean rightDown = Gdx.input.isButtonPressed(Input.Buttons.RIGHT);
    if (rightDown && !sampledRightDown) {
      pendingRightClicks.capture(Gdx.input.getX(), Gdx.input.getY(), TimeUtils.millis(),
          networkReceiver == null ? 0L : networkReceiver.latestServerTick());
    }
    sampledRightDown = rightDown;
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
    if (actioneer.canInterrupt(src)) actioneer.moveTo(src, tmpVec2);
    return true;
  }

  private boolean consumePendingRightPress(int src) {
    PointerClickQueue.Click click = pendingRightClicks.poll();
    if (click == null) return false;

    stage.screenToStageCoordinates(tmpVec2.set(click.screenX, click.screenY));
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
    iso.agg(tmpVec2.set(click.screenX, click.screenY)).unproject().toWorld();
    if (targetId != Engine.INVALID_ENTITY && mPosition.has(targetId)) {
      tmpVec2.set(mPosition.get(targetId).position);
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
        actioneer.moveTo(src, tmpVec2);
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
        if (interactable != null && dst <= interactable.range) {
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
          boolean inMeleeRange = actioneer.isInMeleeRange(src, targetId, 3);
          
          final int selectedSkillId = Riiablo.charData.getAction(Input.Buttons.LEFT);
          final boolean explicitThrowSkill = isThrowSkill(selectedSkillId);

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
              explicitThrowSkill, canThrow);

          // Allow attack if in melee range or can throw
          if (inMeleeRange || canThrow) {
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

  private void requestCast(int sourceId, int skillId, int targetId, Vector2 targetVec) {
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

  /** Returns whether a new cast may be submitted this frame. */
  private boolean canStartCast(int entityId) {
    return actioneer.canInterrupt(entityId)
        && !actioneer.hasCasting(entityId)
        && !actioneer.hasSequence(entityId);
  }

  private int getHovered(int src) {
    return getHoveredAt(src, Gdx.input.getX(), Gdx.input.getY());
  }

  /** Hit-tests the supplied screen coordinates against the current selectable snapshot. */
  private int getHoveredAt(int src, float screenX, float screenY) {
    Position srcPosition = mPosition.get(src);
    int selected = Engine.INVALID_ENTITY;
    boolean selectedInteractable = false;
    float selectedDst2 = Float.POSITIVE_INFINITY;
    // HoveredManager first converts the native input point through the camera
    // before comparing it with iso.toScreen(entityPosition).  The queued click
    // path must use the exact same coordinate space; comparing raw window
    // coordinates makes every monster/NPC miss whenever the viewport or camera
    // has a non-zero projection offset.
    cursorScreen.set(screenX, screenY);
    iso.unproject(cursorScreen);
    IntBag selectableEntities = selectableSubscriber.getEntities();
    for (int i = 0, size = selectableEntities.size(); i < size; i++) {
      int candidate = selectableEntities.get(i);
      Position candidatePosition = mPosition.get(candidate);
      BBoxWrapper boxWrapper = mBBoxWrapper.get(candidate);
      if (candidatePosition == null || boxWrapper == null || boxWrapper.box == null) continue;

      boolean candidateInteractable = mInteractable.has(candidate);
      iso.toScreen(entityScreen.set(candidatePosition.position));
      if (!containsScreenPoint(boxWrapper.box, entityScreen,
          cursorScreen)) continue;
      float candidateDst2 = srcPosition == null
          ? Float.POSITIVE_INFINITY
          : srcPosition.position.dst2(candidatePosition.position);
      if (selected == Engine.INVALID_ENTITY
          || shouldReplaceHoveredTarget(candidateInteractable, candidateDst2,
              selectedInteractable, selectedDst2)) {
        selected = candidate;
        selectedInteractable = candidateInteractable;
        selectedDst2 = candidateDst2;
      }
    }

    // CursorMovementSystem runs before HoveredManager. A waypoint that becomes
    // selectable or is entered by the cursor on the click frame would
    // otherwise be absent until the following frame. Perform a synchronous
    // hit test for waypoints so the click cannot be lost to system ordering.
    cursorScreen.set(screenX, screenY);
    iso.unproject(cursorScreen);
    IntBag waypoints = waypointInputSubscriber.getEntities();
    for (int i = 0, size = waypoints.size(); i < size; i++) {
      int candidate = waypoints.get(i);
      Object object = mObject.get(candidate);
      if (!isWaypoint(object)) continue;

      Position candidatePosition = mPosition.get(candidate);
      BBoxWrapper boxWrapper = mBBoxWrapper.get(candidate);
      if (candidatePosition == null || boxWrapper == null || boxWrapper.box == null) continue;
      iso.toScreen(entityScreen.set(candidatePosition.position));
      if (!containsScreenPoint(boxWrapper.box, entityScreen, cursorScreen)) continue;

      float candidateDst2 = srcPosition == null
          ? Float.POSITIVE_INFINITY
          : srcPosition.position.dst2(candidatePosition.position);
      if (selected == Engine.INVALID_ENTITY
          || !selectedInteractable
          || candidateDst2 < selectedDst2) {
        selected = candidate;
        selectedInteractable = true;
        selectedDst2 = candidateDst2;
      }
    }
    return selected;
  }

  static boolean isWaypoint(Object object) {
    return object != null
        && object.base != null
        && (object.base.SubClass & Engine.Object.SUBCLASS_WAYPOINT)
            == Engine.Object.SUBCLASS_WAYPOINT;
  }

  static boolean containsScreenPoint(com.riiablo.codec.util.BBox box,
      Vector2 entityScreen, Vector2 cursorScreen) {
    float x = entityScreen.x + box.xMin;
    float y = entityScreen.y - box.yMax;
    return x <= cursorScreen.x && cursorScreen.x <= x + box.width
        && y <= cursorScreen.y && cursorScreen.y <= y + box.height;
  }

  static boolean shouldReplaceHoveredTarget(
      boolean candidateInteractable,
      float candidateDst2,
      boolean selectedInteractable,
      float selectedDst2) {
    if (candidateInteractable != selectedInteractable) return candidateInteractable;
    return candidateDst2 < selectedDst2;
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
      
      Vector2 targetPos = mPosition.get(target).position;
      float dst = mPosition.get(src).position.dst(targetPos);
      
      // Check if in melee range
      boolean inMeleeRange = actioneer.isInMeleeRange(src, target, 3);
      
      final int selectedSkillId = Riiablo.charData.getAction(Input.Buttons.LEFT);
      final boolean explicitThrowSkill = isThrowSkill(selectedSkillId);

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
          explicitThrowSkill, canThrow);

      // Allow attack if in melee range or can throw
      if (inMeleeRange || canThrow) {
        requestCast(src, selectedSkillId, target, targetPos);
        return true;
      }
    }
    
    actioneer.moveTo(src, target);
    return true;
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
        + " hovered=" + hoveredSubscriber.getEntities().size());
  }

  private void traceBlockedClick(String reason) {
    if (!Gdx.input.isButtonPressed(Input.Buttons.LEFT)) return;
    traceInput("blocked reason=" + reason);
  }

  private void traceNoInteractionTarget(int src) {
    cursorScreen.set(Gdx.input.getX(), Gdx.input.getY());
    iso.unproject(cursorScreen);
    int nearest = Engine.INVALID_ENTITY;
    float nearestScreenDst2 = Float.POSITIVE_INFINITY;
    IntBag waypoints = waypointInputSubscriber.getEntities();
    for (int i = 0, size = waypoints.size(); i < size; i++) {
      int candidate = waypoints.get(i);
      if (!isWaypoint(mObject.get(candidate))) continue;
      Position position = mPosition.get(candidate);
      if (position == null) continue;
      iso.toScreen(entityScreen.set(position.position));
      float dst2 = cursorScreen.dst2(entityScreen);
      if (dst2 < nearestScreenDst2) {
        nearest = candidate;
        nearestScreenDst2 = dst2;
      }
    }
    traceInput("miss player=" + src + " cursor=" + cursorScreen
        + " nearestWaypoint=" + nearest
        + " nearestScreenDistance=" + (float) Math.sqrt(nearestScreenDst2)
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

  private boolean isMeleeNormalAttack(int skillId) {
    if (skillId != SkillCodes.attack) return false;
    Item weapon = Riiablo.charData.getItems().getEquipped(BodyLoc.RARM);
    if (weapon == null) weapon = Riiablo.charData.getItems().getEquipped(BodyLoc.LARM);
    if (weapon == null || weapon.type == null) return true;
    return !weapon.type.is(Type.BOW) && !weapon.type.is(Type.XBOW);
  }

  private void traceAttackRange(int src, int targetId, int skillId, float distance,
      boolean inMeleeRange, boolean explicitThrowSkill, boolean canThrow) {
    long now = TimeUtils.millis();
    boolean stateChanged = !attackRangeTraceInitialized
        || lastAttackRangeTarget != targetId
        || lastAttackRangeSkill != skillId
        || lastAttackRangeInMelee != inMeleeRange
        || lastAttackRangeCanThrow != canThrow;
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
        + " mode=" + (explicitThrowSkill ? "throw" : "melee")
        + " inMelee=" + inMeleeRange + " canThrow=" + canThrow
        + " throwable=" + (throwable != null ? throwable.code : "none")
        + " quantity=" + quantity);
    lastAttackRangeTarget = targetId;
    lastAttackRangeSkill = skillId;
    lastAttackRangeInMelee = inMeleeRange;
    lastAttackRangeCanThrow = canThrow;
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
