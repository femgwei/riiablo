package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;

import com.riiablo.Riiablo;
import com.riiablo.camera.IsometricCamera;
import com.riiablo.engine.client.component.Hovered;
import com.riiablo.engine.client.component.Label;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.Item;
import com.riiablo.engine.server.component.Object;
import com.riiablo.engine.server.component.Position;
import com.riiablo.map.RenderSystem;
import com.riiablo.profiler.GpuSystem;

@GpuSystem
@All({Label.class, Position.class})
public class LabelManager extends IteratingSystem {
  protected ComponentMapper<Hovered> mHovered;
  protected ComponentMapper<Interactable> mInteractable;
  protected ComponentMapper<Label> mLabel;
  protected ComponentMapper<Item> mItem;
  protected ComponentMapper<Object> mObject;
  protected ComponentMapper<Position> mPosition;

  protected RenderSystem renderer;
  protected MenuManager menuManager;

  @Wire(name = "iso")
  protected IsometricCamera iso;

  private final Vector2 tmpVec2 = new Vector2();
  private final Array<Actor> labels = new Array<>();
  private final Array<GroundLabel> groundLabels = new Array<>();
  private final Array<Rectangle> occupiedLabels = new Array<>();
  private boolean showGroundItems;

  private static final float LABEL_GAP = 2f;
  private static final int MAX_LABEL_RING = 12;

  private static final class GroundLabel {
    final Actor actor;
    final float desiredX;
    final float desiredY;

    GroundLabel(Actor actor) {
      this.actor = actor;
      desiredX = actor.getX();
      desiredY = actor.getY();
    }
  }

  @Override
  protected boolean checkProcessing() {
    return menuManager.getMenu() == null;
  }

  @Override
  protected void begin() {
    labels.clear();
    groundLabels.clear();
    occupiedLabels.clear();
    showGroundItems = Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT)
        || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT);
  }

  @Override
  protected void end() {
    layoutGroundLabels();
    for (Actor label : labels) {
      tmpVec2.x = label.getX();
      tmpVec2.y = label.getY();
      tmpVec2.x = MathUtils.clamp(tmpVec2.x, renderer.getMinX(), renderer.getMaxX() - label.getWidth());
      tmpVec2.y = MathUtils.clamp(tmpVec2.y, renderer.getMinY(), renderer.getMaxY() - label.getHeight());
      label.setPosition(tmpVec2.x, tmpVec2.y);
    }

    Riiablo.batch.begin();
    for (Actor label : labels) {
      label.draw(Riiablo.batch, 1);
    }
    Riiablo.batch.end();
  }

  @Override
  protected void process(int entityId) {
    if (!shouldDisplayLabel(mHovered.has(entityId), mItem.has(entityId), showGroundItems,
        mObject.has(entityId), mInteractable.has(entityId))) {
      return;
    }

    tmpVec2.set(mPosition.get(entityId).position);
    iso.toScreen(tmpVec2);

    Label label = mLabel.get(entityId);
    // Ground gold can be partially picked up without replacing its entity.
    // Refresh the cached header so the displayed amount follows quantity.
    if (mItem.has(entityId)) {
      label.actor = mItem.get(entityId).item.header();
    }
    tmpVec2.add(label.offset);

    Actor actor = label.actor;
    actor.setPosition(tmpVec2.x, tmpVec2.y, Align.center | Align.bottom);
    labels.add(actor);
    if (mItem.has(entityId)) groundLabels.add(new GroundLabel(actor));
  }

  /**
   * Arranges visible ground-item labels like the native client: keep the
   * preferred position when possible, then stack conflicting labels vertically
   * and expand the cluster horizontally only when necessary. Non-item labels
   * remain fixed and act as obstacles for item labels.
   */
  private void layoutGroundLabels() {
    // A hovered item is intentionally left at its native position.  Native
    // Diablo II only resolves collisions for the Alt overlay, where several
    // ground labels are shown at once; moving a single hovered label makes the
    // tooltip appear to drift away from the item under the cursor.
    if (!shouldLayoutGroundLabels(showGroundItems, groundLabels.size)) return;

    for (Actor label : labels) {
      if (containsGroundLabel(label)) continue;
      clampLabel(label);
      occupiedLabels.add(new Rectangle(label.getX(), label.getY(),
          label.getWidth(), label.getHeight()));
    }

    groundLabels.sort((a, b) -> {
      int y = Float.compare(a.desiredY, b.desiredY);
      return y != 0 ? y : Float.compare(a.desiredX, b.desiredX);
    });

    for (GroundLabel groundLabel : groundLabels) {
      Actor actor = groundLabel.actor;
      Rectangle placed = findGroundLabelPosition(
          groundLabel.desiredX, groundLabel.desiredY,
          actor.getWidth(), actor.getHeight(), occupiedLabels,
          renderer.getMinX(), renderer.getMinY(), renderer.getMaxX(), renderer.getMaxY());
      actor.setPosition(placed.x, placed.y);
      occupiedLabels.add(placed);
    }
  }

  private boolean containsGroundLabel(Actor actor) {
    for (GroundLabel groundLabel : groundLabels) {
      if (groundLabel.actor == actor) return true;
    }
    return false;
  }

  private void clampLabel(Actor label) {
    tmpVec2.x = MathUtils.clamp(label.getX(), renderer.getMinX(),
        renderer.getMaxX() - label.getWidth());
    tmpVec2.y = MathUtils.clamp(label.getY(), renderer.getMinY(),
        renderer.getMaxY() - label.getHeight());
    label.setPosition(tmpVec2.x, tmpVec2.y);
  }

  /** Package-private for deterministic layout tests without an ECS world. */
  static Rectangle findGroundLabelPosition(
      float desiredX, float desiredY, float width, float height,
      Array<Rectangle> occupied, float minX, float minY, float maxX, float maxY) {
    Rectangle best = null;
    float bestDistance = Float.POSITIVE_INFINITY;
    float verticalStep = Math.max(1f, height + LABEL_GAP);
    float horizontalStep = Math.max(1f, width + LABEL_GAP);

    // Twelve rings is enough for the usual drop cluster, but it is not enough
    // when the labels are wide or the anchor is near a screen edge.  Extend
    // the search to cover the whole viewport so a dense Alt overlay does not
    // fall through to the overlapping fallback merely because the fixed ring
    // limit was reached.
    int viewportRings = (int) Math.ceil(
        Math.max(maxX - minX, maxY - minY) / Math.max(1f, Math.min(horizontalStep, verticalStep)));
    int maxRing = Math.max(MAX_LABEL_RING, viewportRings + occupied.size + 1);
    float lastX = Float.NaN;
    float lastY = Float.NaN;
    for (int ring = 0; ring <= maxRing; ring++) {
      for (int row = -ring; row <= ring; row++) {
        for (int column = -ring; column <= ring; column++) {
          if (ring != 0 && Math.abs(row) != ring && Math.abs(column) != ring) continue;
          // Prefer the native-looking vertical stack before moving sideways.
          if (ring > 0 && column != 0 && Math.abs(row) != ring) continue;

          float x = MathUtils.clamp(desiredX + column * horizontalStep,
              minX, Math.max(minX, maxX - width));
          float y = MathUtils.clamp(desiredY + row * verticalStep,
              minY, Math.max(minY, maxY - height));
          // Clamping can collapse many ring coordinates to the same edge
          // position.  Skip those duplicates instead of repeatedly testing
          // the same blocked rectangle.
          if (x == lastX && y == lastY) continue;
          lastX = x;
          lastY = y;
          Rectangle candidate = new Rectangle(x, y, width, height);
          if (overlapsAny(candidate, occupied)) continue;

          float distance = Math.abs(x - desiredX) + Math.abs(y - desiredY);
          if (distance < bestDistance) {
            best = candidate;
            bestDistance = distance;
          }
        }
      }
      if (best != null) return best;
    }

    // Extremely dense drops can exhaust the search radius. Pick the nearest
    // bounded location rather than hiding a label or allowing an unbounded UI.
    return new Rectangle(
        MathUtils.clamp(desiredX, minX, Math.max(minX, maxX - width)),
        MathUtils.clamp(desiredY, minY, Math.max(minY, maxY - height)),
        width, height);
  }

  private static boolean overlapsAny(Rectangle candidate, Array<Rectangle> occupied) {
    for (Rectangle rectangle : occupied) {
      if (candidate.x < rectangle.x + rectangle.width + LABEL_GAP
          && candidate.x + candidate.width + LABEL_GAP > rectangle.x
          && candidate.y < rectangle.y + rectangle.height + LABEL_GAP
          && candidate.y + candidate.height + LABEL_GAP > rectangle.y) {
        return true;
      }
    }
    return false;
  }

  static boolean shouldDisplayLabel(boolean hovered, boolean groundItem,
      boolean showGroundItems, boolean object, boolean interactable) {
    if (groundItem) return hovered || showGroundItems;
    return hovered && (!object || interactable);
  }

  static boolean shouldLayoutGroundLabels(boolean showGroundItems, int groundLabelCount) {
    return showGroundItems && groundLabelCount > 1;
  }
}
