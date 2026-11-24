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
  /** Scoped 1.10f CltDo5 / flying-rock clocks for Molten Boulder. */
  public boolean nativeBoulderFire, nativeBoulderRock;
  /** Audited Fissure and resident Volcano native frame policy. */
  public boolean nativeDruidSurface;
  public boolean nativeVolcanoRock;
  public boolean nativeArmageddon;
  public com.riiablo.presentation.NativeHurricanePresentation.Flight hurricaneFlight;
  public int nativeVolcanoOwnerId = -1, nativeVolcanoHeight;
  public int nativeVelocityX, nativeVelocityY;
  public int nativeSeed;
  public String landing;
  public boolean landingFire;
  public int landingPhase;
  public final Vector2 origin = new Vector2();
  public final Vector2 startOffset = new Vector2();
  public final Vector2 endOffset = new Vector2();
  /** Screen coordinates, positive Y upwards, applied without moving collision coordinates. */
  public final Vector2 offset = new Vector2();
  public float lift;
}
