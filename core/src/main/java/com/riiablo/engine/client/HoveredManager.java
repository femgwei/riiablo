package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.Aspect;
import com.artemis.EntitySubscription;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.camera.IsometricCamera;
import com.riiablo.codec.util.BBox;
import com.riiablo.engine.Engine;
import com.riiablo.engine.client.component.AnimationWrapper;
import com.riiablo.engine.client.component.BBoxWrapper;
import com.riiablo.engine.client.component.Hovered;
import com.riiablo.engine.client.component.Label;
import com.riiablo.engine.client.component.Selectable;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.Item;
import com.riiablo.engine.server.component.Position;

@All({Selectable.class, BBoxWrapper.class, Position.class})
public class HoveredManager extends IteratingSystem {
  static final float INTERACTABLE_PADDING_X = 8f;
  static final float INTERACTABLE_PADDING_Y = 6f;
  static final float ITEM_PADDING_X = 12f;
  static final float ITEM_PADDING_Y = 8f;

  protected ComponentMapper<Hovered> mHovered;
  protected ComponentMapper<BBoxWrapper> mBBoxWrapper;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<AnimationWrapper> mAnimationWrapper;
  protected ComponentMapper<Label> mLabel;
  protected ComponentMapper<Interactable> mInteractable;
  protected ComponentMapper<Item> mItem;

  @Wire(name="iso")
  protected IsometricCamera iso;

  private final Vector2 coords = new Vector2();
  private final Vector2 tmpVec2 = new Vector2();
  private final Vector2 hitCoords = new Vector2();
  private final Vector2 hitEntityScreen = new Vector2();
  private EntitySubscription selectableSubscriber;

  @Override
  protected void initialize() {
    selectableSubscriber = world.getAspectSubscriptionManager().get(
        Aspect.all(Selectable.class, BBoxWrapper.class, Position.class));
  }

  @Override
  protected void begin() {
    coords.set(Gdx.input.getX(), Gdx.input.getY());
    iso.unproject(coords);
  }

  @Override
  protected void removed(int entityId) {
    setHovered(entityId, false);
  }

  @Override
  protected void process(int entityId) {
    BBox box = mBBoxWrapper.get(entityId).box;
    if (box == null) return;
    Vector2 position = mPosition.get(entityId).position;
    iso.toScreen(tmpVec2.set(position));
    boolean b = containsScreenPoint(box, tmpVec2, coords,
        hitPaddingX(entityId), hitPaddingY(entityId));
    setHovered(entityId, b);
  }

  /** Performs the same hit test used for hover at a captured pointer position. */
  public int getHoveredAt(int sourceId, float screenX, float screenY) {
    hitCoords.set(screenX, screenY);
    iso.unproject(hitCoords);

    Position sourcePosition = mPosition.get(sourceId);
    int selected = Engine.INVALID_ENTITY;
    boolean selectedInteractable = false;
    float selectedDst2 = Float.POSITIVE_INFINITY;
    IntBag selectable = selectableSubscriber.getEntities();
    for (int i = 0, size = selectable.size(); i < size; i++) {
      int candidate = selectable.get(i);
      if (candidate == sourceId) continue;
      Position candidatePosition = mPosition.get(candidate);
      BBoxWrapper boxWrapper = mBBoxWrapper.get(candidate);
      if (candidatePosition == null || boxWrapper == null || boxWrapper.box == null) continue;

      iso.toScreen(hitEntityScreen.set(candidatePosition.position));
      boolean hitAnimation = containsScreenPoint(boxWrapper.box, hitEntityScreen, hitCoords,
          hitPaddingX(candidate), hitPaddingY(candidate));
      // Ground-item names are drawn directly by LabelManager instead of being
      // attached to a Scene2D stage, so stage.hit() cannot select them. Treat
      // the visible label bounds as an additional pickup hit area. The label
      // is only eligible while it is actually shown (Alt or normal hover),
      // which avoids leaving a stale, invisible actor clickable.
      boolean hitLabel = isVisibleItemLabel(candidate) && mLabel.has(candidate)
          && mLabel.get(candidate).actor != null
          && containsLabelPoint(mLabel.get(candidate).actor, hitCoords);
      if (!hitAnimation && !hitLabel) continue;

      boolean candidateInteractable = mInteractable.has(candidate);
      float candidateDst2 = sourcePosition == null
          ? Float.POSITIVE_INFINITY
          : sourcePosition.position.dst2(candidatePosition.position);
      if (selected == Engine.INVALID_ENTITY
          || shouldReplaceTarget(candidateInteractable, candidateDst2,
              selectedInteractable, selectedDst2)) {
        selected = candidate;
        selectedInteractable = candidateInteractable;
        selectedDst2 = candidateDst2;
      }
    }
    return selected;
  }

  private boolean isVisibleItemLabel(int entityId) {
    if (!mItem.has(entityId)) return false;
    if (mHovered.has(entityId)) return true;
    return Gdx.input != null
        && (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_LEFT)
            || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_RIGHT));
  }

  private float hitPaddingX(int entityId) {
    if (mItem.has(entityId)) return ITEM_PADDING_X;
    return mInteractable.has(entityId) ? INTERACTABLE_PADDING_X : 0f;
  }

  private float hitPaddingY(int entityId) {
    if (mItem.has(entityId)) return ITEM_PADDING_Y;
    return mInteractable.has(entityId) ? INTERACTABLE_PADDING_Y : 0f;
  }

  static boolean containsScreenPoint(BBox box, Vector2 entityScreen, Vector2 pointer,
      float paddingX, float paddingY) {
    float x = entityScreen.x + box.xMin - paddingX;
    float y = entityScreen.y - box.yMax - paddingY;
    return x <= pointer.x && pointer.x <= x + box.width + paddingX * 2f
        && y <= pointer.y && pointer.y <= y + box.height + paddingY * 2f;
  }

  static boolean containsLabelPoint(com.badlogic.gdx.scenes.scene2d.Actor actor,
      Vector2 pointer) {
    float x = actor.getX();
    float y = actor.getY();
    return x <= pointer.x && pointer.x <= x + actor.getWidth()
        && y <= pointer.y && pointer.y <= y + actor.getHeight();
  }

  static boolean shouldReplaceTarget(boolean candidateInteractable, float candidateDst2,
      boolean selectedInteractable, float selectedDst2) {
    if (candidateInteractable != selectedInteractable) return candidateInteractable;
    return candidateDst2 < selectedDst2;
  }

  public void setHovered(int id, boolean b) {
    if (b) {
      mHovered.create(id);
    } else {
      mHovered.remove(id);
    }

    if (mAnimationWrapper.has(id)) mAnimationWrapper.get(id).animation.setHighlighted(b);
  }
}
