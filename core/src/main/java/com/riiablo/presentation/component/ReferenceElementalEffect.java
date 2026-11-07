package com.riiablo.presentation.component;

import com.artemis.Component;
import com.artemis.annotations.Transient;
import com.badlogic.gdx.math.Vector2;

/** Client-only sprite clock/path. Never serialized into combat or replay state. */
@Transient
public class ReferenceElementalEffect extends Component {
  public float age, duration, fps = 25f;
  public int loopStart = -1, loopEnd = -1, phase, direction;
  public int entityId;
  public boolean fade, orbit;
  public boolean ground;
  public String landing;
  public boolean landingFire;
  public int landingPhase;
  public final Vector2 origin = new Vector2();
  public final Vector2 startOffset = new Vector2();
  public final Vector2 endOffset = new Vector2();
  /** Screen coordinates, positive Y upwards, applied without moving collision coordinates. */
  public final Vector2 offset = new Vector2();
  public float angle, radius, spin, lift;
}
