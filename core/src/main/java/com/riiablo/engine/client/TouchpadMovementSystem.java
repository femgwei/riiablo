package com.riiablo.engine.client;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.Actioneer;
import com.riiablo.engine.server.component.Position;
import com.riiablo.map.RenderSystem;

/** Captured host direction becomes a movement intent at the next simulation tick. */
public class TouchpadMovementSystem extends BaseSystem {
  private static final float MOVE_DISTANCE = 4f;

  protected ComponentMapper<Position> mPosition;
  protected RenderSystem renderer;
  protected Actioneer actioneer;

  private final Vector2 direction = new Vector2();
  private final Vector2 target = new Vector2();
  private boolean moving;

  public void capture(float worldX, float worldY) {
    if (!Float.isFinite(worldX) || !Float.isFinite(worldY)) direction.setZero();
    else direction.set(worldX, worldY).nor();
  }

  @Override
  protected void processSystem() {
    int player = renderer.getSrc();
    if (player < 0 || !mPosition.has(player) || !actioneer.canInterrupt(player)) return;
    if (!direction.isZero()) {
      target.set(mPosition.get(player).position).mulAdd(direction, MOVE_DISTANCE);
      actioneer.moveTo(player, target);
      moving = true;
    } else if (moving) {
      actioneer.moveTo(player, Engine.INVALID_ENTITY);
      moving = false;
    }
  }
}
