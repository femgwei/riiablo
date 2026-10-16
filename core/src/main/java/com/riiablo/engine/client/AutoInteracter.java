package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.Engine;
import com.riiablo.engine.client.component.Selectable;
import com.riiablo.engine.client.event.InteractEvent;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.Position;

import net.mostlyoriginal.api.event.common.Subscribe;

@All({Position.class, Selectable.class, Interactable.class})
public class AutoInteracter extends IteratingSystem {
  private static final float SCALAR = 2f;

  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Interactable> mInteractable;

  protected HoveredManager hoveredManager;
  @com.artemis.annotations.Wire(name = "clientContext")
  protected ClientGameplayContext clientContext = ClientGameplayContext.fromGlobals();

  private final Vector2 position = new Vector2();
  private float shortestDistance;
  private int closest;

  @Subscribe
  public void onInteract(InteractEvent event) {
    if (closest == Engine.INVALID_ENTITY) return;
    Interactable interactable = mInteractable.get(closest);
    if (interactable == null) return;
    int player = clientContext == null ? -1 : clientContext.playerId();
    if (player < 0) return;
    interactable.interactor.interact(player, closest);
  }

  @Override
  protected void begin() {
    closest = Engine.INVALID_ENTITY;
    shortestDistance = Float.POSITIVE_INFINITY;
    int player = clientContext == null ? -1 : clientContext.playerId();
    if (player < 0 || !mPosition.has(player) || mPosition.get(player) == null) return;
    position.set(mPosition.get(player).position);
  }

  @Override
  protected void process(int entityId) {
    Vector2 position = mPosition.get(entityId).position;
    Interactable interactable = mInteractable.get(entityId);
    float dist = this.position.dst(position);
    boolean b = dist <= interactable.range * SCALAR;
    if (b && dist < shortestDistance) {
      shortestDistance = dist;
      closest = entityId;
    }

    hoveredManager.setHovered(entityId, b);
  }
}
