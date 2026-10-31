package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.EntitySubscription;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.camera.IsometricCamera;
import com.riiablo.Riiablo;
import com.riiablo.engine.Engine;
import com.riiablo.engine.client.component.Hovered;
import com.riiablo.engine.shared.component.Selectable;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.Item;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.event.ModeChangeEvent;
import net.mostlyoriginal.api.event.common.Subscribe;

@All({Selectable.class, Position.class})
public class HoveredManager extends IteratingSystem {
  static final float INTERACTABLE_PADDING_X = 8f;
  static final float INTERACTABLE_PADDING_Y = 6f;
  static final float ITEM_PADDING_X = 12f;
  static final float ITEM_PADDING_Y = 8f;

  protected ComponentMapper<Hovered> mHovered;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Interactable> mInteractable;
  protected ComponentMapper<Item> mItem;
  protected ComponentMapper<SummonedPet> mSummonedPet;
  protected ComponentMapper<Selectable> mSelectable;

  @Wire(name="iso")
  protected IsometricCamera iso;
  @Wire(name="hoverPresentation", failOnNull = false)
  protected ClientHoverPresentationPort hoverPresentation;

  private final Vector2 coords = new Vector2();
  private final Vector2 tmpVec2 = new Vector2();
  private final Vector2 hitCoords = new Vector2();
  private final Vector2 hitEntityScreen = new Vector2();
  private EntitySubscription selectableSubscriber;
  private int selectedHoverEntity = Engine.INVALID_ENTITY;

  @Override
  protected void initialize() {
    // The production world installs the hover adapter as a system.  Some
    // hosts do not also register it under the optional "hoverPresentation"
    // service name, so resolve that system before the first hit test instead
    // of silently making every selectable entity unclickable.
    if (hoverPresentation == null) {
      for (BaseSystem system : world.getSystems()) {
        if (system instanceof ClientHoverPresentationPort) {
          hoverPresentation = (ClientHoverPresentationPort) system;
          break;
        }
      }
    }
    selectableSubscriber = world.getAspectSubscriptionManager().get(
        Aspect.all(Selectable.class, Position.class));
  }

  @Override
  protected void begin() {
    coords.set(Gdx.input.getX(), Gdx.input.getY());
    iso.unproject(coords);
    // Use the same winner for visual hover that click handling uses.  Native
    // D2 exposes one hovered unit; allowing every overlapping selectable to
    // retain Hovered makes a static corpse label and a monster header appear
    // at the same cursor position (notably around Travincal's water pools).
    selectedHoverEntity = Engine.INVALID_ENTITY;
    if (Riiablo.game != null && Riiablo.game.player >= 0) {
      selectedHoverEntity = getHoveredAt(
          Riiablo.game.player, Gdx.input.getX(), Gdx.input.getY());
    }
  }

  @Override
  protected void removed(int entityId) {
    setHovered(entityId, false);
  }

  @Override
  protected void process(int entityId) {
    if (mSummonedPet.has(entityId) && !isUnsummonSelected()) {
      setHovered(entityId, false);
      return;
    }
    ClientHoverBounds box = hoverPresentation == null ? null : hoverPresentation.bounds(entityId);
    if (box == null) {
      setHovered(entityId, false);
      return;
    }
    Vector2 position = mPosition.get(entityId).position;
    iso.toScreen(tmpVec2.set(position));
    boolean b = containsScreenPoint(box, tmpVec2, coords,
        hitPaddingX(entityId), hitPaddingY(entityId));
    if (selectedHoverEntity != Engine.INVALID_ENTITY) {
      b = b && entityId == selectedHoverEntity;
    }
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
    boolean altDown = isAltDown();
    for (int i = 0, size = selectable.size(); i < size; i++) {
      int candidate = selectable.get(i);
      if (candidate == sourceId) continue;
      if (mSummonedPet.has(candidate) && !isUnsummonSelected()) continue;
      Position candidatePosition = mPosition.get(candidate);
      if (candidatePosition == null) continue;
      ClientHoverBounds box = hoverPresentation == null ? null : hoverPresentation.bounds(candidate);
      if (box == null) continue;

      iso.toScreen(hitEntityScreen.set(candidatePosition.position));
      // While Alt is held, native D2 selects ground items through their
      // labels only. The item sprite itself must not win the click.
      boolean hitAnimation = !(mItem.has(candidate) && altDown)
          && containsScreenPoint(box, hitEntityScreen, hitCoords,
              hitPaddingX(candidate), hitPaddingY(candidate));
      // Ground-item names are drawn directly by LabelManager instead of being
      // attached to a Scene2D stage, so stage.hit() cannot select them. Treat
      // the visible label bounds as an additional pickup hit area. The label
      // is only eligible while it is actually shown (Alt or normal hover),
      // which avoids leaving a stale, invisible actor clickable.
      boolean hitLabel = altDown && isVisibleItemLabel(candidate)
          && hoverPresentation != null
          && hoverPresentation.containsLabel(candidate, hitCoords.x, hitCoords.y);
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

  /** Hide and disable the fixed Unsummon HUD as soon as dismissal starts. */
  @Subscribe
  public void onModeChanged(ModeChangeEvent event) {
    if (event == null || event.mode != Engine.Monster.MODE_DT
        || !mSummonedPet.has(event.entityId)) return;
    setHovered(event.entityId, false);
    if (mSelectable.has(event.entityId)) mSelectable.remove(event.entityId);
  }

  private boolean isVisibleItemLabel(int entityId) {
    return mItem.has(entityId) && isAltDown();
  }

  private boolean isAltDown() {
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

  static boolean containsScreenPoint(ClientHoverBounds box, Vector2 entityScreen, Vector2 pointer,
      float paddingX, float paddingY) {
    float x = entityScreen.x + box.xMin - paddingX;
    float y = entityScreen.y - box.yMax - paddingY;
    return x <= pointer.x && pointer.x <= x + box.width + paddingX * 2f
        && y <= pointer.y && pointer.y <= y + box.height + paddingY * 2f;
  }

  private boolean isUnsummonSelected() {
    return hoverPresentation != null && hoverPresentation.isUnsummonSelected();
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

    if (hoverPresentation != null) hoverPresentation.setHighlighted(id, b);
  }
}

