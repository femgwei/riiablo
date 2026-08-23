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
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.UIUtils;
import com.badlogic.gdx.utils.TimeUtils;

import com.riiablo.Riiablo;
import com.riiablo.camera.IsometricCamera;
import com.riiablo.engine.Engine;
import com.riiablo.engine.client.component.Hovered;
import com.riiablo.engine.server.Actioneer;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Target;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.item.Item;
import com.riiablo.item.BodyLoc;
import com.riiablo.map.Map;
import com.riiablo.map.RenderSystem;
import com.riiablo.profiler.ProfilerSystem;
import com.riiablo.save.ItemController;

public class CursorMovementSystem extends BaseSystem {
  private static final String TAG = "CursorMovementSystem";

  protected ComponentMapper<Target> mTarget;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Interactable> mInteractable;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;

  protected RenderSystem renderer;
  protected MenuManager menuManager;
  protected DialogManager dialogManager;
  protected ProfilerSystem profiler;
  protected Actioneer actioneer;
  protected DeathHandler deathHandler;

  @Wire(name = "iso")
  protected IsometricCamera iso;

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
  int lastInteractionTraceTarget = Engine.INVALID_ENTITY;
  long lastInteractionTraceMillis;

  private final Vector2 tmpVec2 = new Vector2();

  @Override
  protected void initialize() {
    hoveredSubscriber = world.getAspectSubscriptionManager().get(Aspect.all(Hovered.class));
  }

  @Override
  protected void processSystem() {
    if (profiler != null && profiler.hit()) return;
    
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
    if (hit) return;

    final boolean leftPressed = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
    if ((leftPressed && UIUtils.shift()) || Gdx.input.isButtonPressed(Input.Buttons.RIGHT)) {
      final int targetId = getHovered(playerId);
      if (targetId != Engine.INVALID_ENTITY && (isTargetDead(targetId) || actioneer.didLastAttackTargetDie(playerId))) {
        actioneer.moveTo(playerId, Engine.INVALID_ENTITY);
      } else {
        final int skillId = Riiablo.charData.getAction(leftPressed ? Input.Buttons.LEFT : Input.Buttons.RIGHT);
        iso.agg(tmpVec2.set(Gdx.input.getX(), Gdx.input.getY())).unproject().toWorld();
        actioneer.cast(playerId, skillId, targetId, tmpVec2);
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
          
          // Check if equipped weapon is throwable and in throwing range
          boolean canThrow = false;
          float throwRange = 0f;
          Item weapon = Riiablo.charData.getItems().getEquipped(BodyLoc.RARM);
          if (weapon == null) {
            weapon = Riiablo.charData.getItems().getEquipped(BodyLoc.LARM);
          }
          
          if (weapon != null && weapon.base != null) {
            boolean isThrowable = weapon.type.is(com.riiablo.item.Type.JAVE) || 
                                 weapon.type.is(com.riiablo.item.Type.TKNI) || 
                                 weapon.type.is(com.riiablo.item.Type.TAXE);
            
            if (isThrowable) {
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
          }
          
          // Allow attack if in melee range or can throw
          if (inMeleeRange || canThrow) {
            actioneer.cast(src, Riiablo.charData.getAction(Input.Buttons.LEFT), targetId, targetPos);
          }
        }
      }
    }
  }

  private int getHovered(int src) {
    IntBag hoveredEntities = hoveredSubscriber.getEntities();
    if (hoveredEntities.size() == 0) return Engine.INVALID_ENTITY;

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
    return selected;
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
    if (target == Engine.INVALID_ENTITY) return false;

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
      
      // Check if equipped weapon is throwable and in throwing range
      boolean canThrow = false;
      float throwRange = 0f;
      Item weapon = Riiablo.charData.getItems().getEquipped(BodyLoc.RARM);
      if (weapon == null) {
        weapon = Riiablo.charData.getItems().getEquipped(BodyLoc.LARM);
      }
      
      if (weapon != null && weapon.base != null) {
        boolean isThrowable = weapon.type.is(com.riiablo.item.Type.JAVE) || 
                             weapon.type.is(com.riiablo.item.Type.TKNI) || 
                             weapon.type.is(com.riiablo.item.Type.TAXE);
        
        if (isThrowable) {
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
      }
      
      // Allow attack if in melee range or can throw
      if (inMeleeRange || canThrow) {
        actioneer.cast(src, Riiablo.charData.getAction(Input.Buttons.LEFT), target, targetPos);
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
