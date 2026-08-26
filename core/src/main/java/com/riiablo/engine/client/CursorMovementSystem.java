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
  EntitySubscription waypointInputSubscriber;
  boolean requireRelease;
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
          // weapon is throwable and in throwing range. A throwable weapon does
          // not turn the normal Attack skill into a ranged attack.
          boolean canThrow = false;
          float throwRange = 0f;
          Item weapon = Riiablo.charData.getItems().getEquippedThrowableWeapon();
          
          if (explicitThrowSkill && weapon != null && weapon.base != null) {
              // Check quantity
              com.riiablo.attributes.StatRef quantity = weapon.attrs.base().get(Stat.quantity);
              if (quantity != null && quantity.asInt() > 0) {
                // Get throwing range from weapon's RangeAdder or default
                if (weapon.base instanceof com.riiablo.codec.excel.Weapons.Entry) {
                  com.riiablo.codec.excel.Weapons.Entry weaponEntry = (com.riiablo.codec.excel.Weapons.Entry) weapon.base;
                  throwRange = weaponEntry.RangeAdder + 3f; // RangeAdder + player range bonus
                } else {
                  throwRange = 10f; // Default throwing range
                }
                
                // Check if target is within throwing range
                if (dst <= throwRange) {
                  canThrow = true;
                }
              }
          }

          traceAttackRange(src, targetId, selectedSkillId, dst, inMeleeRange,
              explicitThrowSkill, canThrow);

          // Allow attack if in melee range or can throw
          if (inMeleeRange || canThrow) {
            requestCast(src, selectedSkillId, targetId, targetPos);
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
    NetworkedActionSender.cast(socket, skillId, targetServerId, targetVec);
  }

  private int getHovered(int src) {
    IntBag hoveredEntities = hoveredSubscriber.getEntities();
    Position srcPosition = mPosition.get(src);
    int selected = Engine.INVALID_ENTITY;
    boolean selectedInteractable = false;
    float selectedDst2 = Float.POSITIVE_INFINITY;
    for (int i = 0, size = hoveredEntities.size(); i < size; i++) {
      int candidate = hoveredEntities.get(i);
      Position candidatePosition = mPosition.get(candidate);
      if (candidatePosition == null) continue;

      boolean candidateInteractable = mInteractable.has(candidate);
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
    cursorScreen.set(Gdx.input.getX(), Gdx.input.getY());
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
    if (actioneer.hasCasting(src) || actioneer.hasSequence(src)) return false;
    if (actioneer.didLastAttackTargetDie(src)) return false;
    
    int target = getHovered(src);
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
      // weapon is throwable and in throwing range. Normal Attack remains
      // point-blank melee even when a throwable weapon is equipped.
      boolean canThrow = false;
      float throwRange = 0f;
      Item weapon = Riiablo.charData.getItems().getEquippedThrowableWeapon();

      if (explicitThrowSkill && weapon != null && weapon.base != null) {
        com.riiablo.attributes.StatRef quantity = weapon.attrs.base().get(Stat.quantity);
        if (quantity != null && quantity.asInt() > 0) {
          if (weapon.base instanceof com.riiablo.codec.excel.Weapons.Entry) {
            com.riiablo.codec.excel.Weapons.Entry weaponEntry = (com.riiablo.codec.excel.Weapons.Entry) weapon.base;
            throwRange = weaponEntry.RangeAdder + 3f;
          } else {
            throwRange = 10f;
          }

          if (dst <= throwRange) {
            canThrow = true;
          }
        }
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
