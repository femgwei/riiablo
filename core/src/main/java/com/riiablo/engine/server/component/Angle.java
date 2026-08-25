package com.riiablo.engine.server.component;

import com.artemis.PooledComponent;
import com.artemis.annotations.PooledWeaver;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.Direction;

@PooledWeaver
public class Angle extends PooledComponent {
  private static final float DEFAULT_ANGLE = Direction.direction8ToRadians(Direction.DOWN);
  public final Vector2 angle  = Vector2.X.cpy().rotateRad(DEFAULT_ANGLE);
  public final Vector2 target = angle.cpy();

  @Override
  public void reset() {
    // Components are pooled. Re-applying the default rotation to the current
    // vector accumulates a turn every time an entity is recycled, eventually
    // spawning players/monsters with an incorrect facing.
    angle.set(Vector2.X).rotateRad(DEFAULT_ANGLE);
    target.set(angle);
  }

  public Angle set(Vector2 angle) {
    this.angle.set(angle);
    this.target.set(angle);
    return this;
  }
}
