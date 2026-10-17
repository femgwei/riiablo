package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BooleanArray;

@Transient
@PooledWeaver
public class Box2DBody extends Component {
  public Body body;
  /** Generation of the Box2D world that owns {@link #body}. */
  public long worldGeneration;
  public final Array<Fixture> skillMoveFixtures = new Array<>();
  public final BooleanArray skillMoveSensors = new BooleanArray();
}
