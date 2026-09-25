package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.MathUtils;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.client.component.AnimationWrapper;
import com.riiablo.engine.server.event.MissileImpactEvent;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/**
 * Executes the client-side portion of Missiles.txt impact behavior.
 *
 * <p>The server owns collision and damage.  This system only consumes the
 * one-shot impact event and creates visual-only client hit sub-missiles.  It
 * deliberately never creates a server-authoritative projectile or dispatches
 * another damage event.</p>
 */
@All({Missile.class, Position.class, Velocity.class})
public class MissileImpactPresentationSystem extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(MissileImpactPresentationSystem.class);

  protected ComponentMapper<Missile> mMissile;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<AnimationWrapper> mAnimationWrapper;
  @com.artemis.annotations.Wire(name = "factory")
  protected EntityFactory factory;

  private final Vector2 direction = new Vector2(1f, 0f);

  // D2MOO MISSMODE_SrvHit29_FrozenOrb uses this exact 64-point lattice.  The
  // client hit callback uses the same table, but skips entries according to
  // Missiles.txt.cHitPar1.  Keeping the table here avoids turning the native
  // scatter into an evenly-spaced approximation (which is visibly different
  // near the cardinal directions).
  private static final int[] FROZEN_ORB_X = {
      30, 29, 29, 28, 27, 26, 24, 23, 21, 19, 16, 14, 11, 8, 5, 2,
      0, -2, -5, -8, -11, -14, -16, -19, -21, -23, -24, -26, -27, -28,
      -29, -29, -30, -29, -29, -28, -27, -26, -24, -23, -21, -19, -16,
      -14, -11, -8, -5, -2, 0, 2, 5, 8, 11, 14, 16, 19, 21, 23, 24, 26,
      27, 28, 29, 29
  };
  private static final int[] FROZEN_ORB_Y = {
      0, 2, 5, 8, 11, 14, 16, 19, 21, 23, 24, 26, 27, 28, 29, 29,
      30, 29, 29, 28, 27, 26, 24, 23, 21, 19, 16, 14, 11, 8, 5, 2,
      0, -2, -5, -8, -11, -14, -16, -19, -21, -23, -24, -26, -27, -28,
      -29, -29, -30, -29, -29, -28, -27, -26, -24, -23, -21, -19, -16,
      -14, -11, -8, -5, -2
  };

  @Override
  protected void process(int entityId) {
    Missile visual = mMissile.get(entityId);
    float delta = Math.max(0f, world.delta);
    Vector2 velocity = mVelocity.get(entityId).velocity;
    float distance = velocity.len() * delta;

    // Network replicas are visual-only from the local simulation's point of
    // view, but they still run the native pCltDoFunc callback while flying.
    // Local authoritative missiles already execute their server-side control
    // functions, so restricting this to authoritative=false prevents doubled
    // trails in single-player and on the server host.
    if (!visual.presentationOnly && !visual.authoritative) {
      processClientFlightFunction(entityId, visual, velocity, delta);
      return;
    }
    if (!visual.presentationOnly) return;
    if (distance > 0f) {
      mPosition.get(entityId).position.mulAdd(velocity, delta);
      visual.distanceTraveled += distance;
    }
    visual.nativeFrame += Math.max(1, Math.round(delta * 25f));
    if (visual.range > 0f && visual.distanceTraveled >= visual.range) {
      world.delete(entityId);
      return;
    }
    if (visual.nativeLifetimeFrames > 0
        && visual.nativeFrame >= visual.nativeLifetimeFrames) {
      world.delete(entityId);
      return;
    }
    AnimationWrapper animation = mAnimationWrapper.has(entityId)
        ? mAnimationWrapper.get(entityId) : null;
    if (animation != null && !animation.animation.isLooping()
        && animation.animation.isFinished()) {
      world.delete(entityId);
    }
  }

  private void processClientFlightFunction(int entityId, Missile visual,
      Vector2 velocity, float delta) {
    Missiles.Entry source = visual.missile;
    if (source == null) return;
    int function = source.pCltDoFunc;
    if (function != 8 && function != 49) return;
    String childName = first(source.CltSubMissile, 0);
    if (childName == null || childName.isEmpty() || factory == null) return;

    int elapsedFrames = Math.max(1, Math.round(delta * 25f));
    int previousFrame = visual.nativeFrame;
    visual.nativeFrame += elapsedFrames;
    int interval = Math.max(1, cltParam(source, 0, 1));
    int firstFrame = previousFrame + 1;
    int lastFrame = visual.nativeFrame;
    for (int frame = firstFrame; frame <= lastFrame; frame++) {
      if (frame % interval != 0) continue;
      Vector2 at = mPosition.get(entityId).position;
      float angle = velocity.isZero(0.0001f) ? 0f : MathUtils.atan2(velocity.y, velocity.x);
      createFlightVisual(source, childName, at, angle);
    }
  }

  private void createFlightVisual(Missiles.Entry source, String childName,
      Vector2 position, float angle) {
    Missiles.Entry child = Riiablo.files.Missiles.get(childName);
    if (child == null) {
      log.warn("[MISSILE_FLIGHT] source={} missing CltSubMissile={}",
          source.Missile, childName);
      return;
    }
    direction.set(MathUtils.cos(angle), MathUtils.sin(angle));
    int id;
    if (factory instanceof ClientEntityFactory) {
      id = ((ClientEntityFactory) factory).createMissilePresentation(child, direction,
          position);
    } else {
      id = factory.createMissile(child, direction, position, -1);
    }
    if (id == com.riiablo.engine.Engine.INVALID_ENTITY || !mMissile.has(id)) return;
    Missile visual = mMissile.get(id);
    visual.authoritative = false;
    visual.presentationOnly = true;
    visual.ownerId = -1;
    visual.persistent = false;
    visual.nativeLifetimeFrames = nativePresentationLifetimeFrames(child);
    log.debug("[MISSILE_FLIGHT] source={} child={} entity={} pos=({}, {}) interval={}",
        source.Missile, childName, id, position.x, position.y,
        cltParam(source, 0, 1));
  }

  @Subscribe
  public void onMissileImpact(MissileImpactEvent event) {
    if (event == null || Riiablo.files == null || Riiablo.files.Missiles == null) return;
    Missiles.Entry source = Riiablo.files.Missiles.get(event.missileId);
    if (source == null) return;

    String hitSound = source.HitSound;
    if (hitSound != null && !hitSound.isEmpty() && Riiablo.audio != null) {
      // An empty native HitSound means silence.  Do not substitute a generic
      // impact sound: that changes the observable Missiles.txt behavior.
      Riiablo.audio.play(hitSound, true);
    }
    String hitClassSound = hitClassSound(source.HitClass, event.targetEntityId);
    if (hitClassSound != null && Riiablo.audio != null) {
      Riiablo.audio.play(hitClassSound, true);
    }

    String[] children = source.CltHitSubMissile;
    if (children == null || factory == null) return;

    // These are the native client hit-function families that emit a radial
    // visual rather than a single child at the impact point.  The authoritative
    // server already owns the corresponding damage/target selection; these
    // projectiles are strictly render-only.
    // Frozen Orb's SrvHit29 already creates the same nova as authoritative
    // HitSubMissile entities.  Do not create a second client-only copy.
    if (source.pCltHitFunc == 30 && source.pSrvHitFunc != 29) {
      String child = first(children, 0);
      int step = clientHitStep(source);
      for (int i = 0; i < FROZEN_ORB_X.length; i += step) {
        // The native server/client coordinate transform is the isometric
        // half-sum/half-difference used by Frozen Orb's late-path steering.
        float offsetX = (FROZEN_ORB_X[i] - FROZEN_ORB_Y[i]) * 0.5f;
        float offsetY = (FROZEN_ORB_X[i] + FROZEN_ORB_Y[i]) * 0.5f;
        float angle = MathUtils.atan2(offsetY, offsetX);
        createVisual(source, child, event, angle, offsetX, offsetY);
      }
      return;
    }
    if (source.pCltHitFunc == 14) {
      float baseAngle = MathUtils.atan2(event.dy, event.dx);
      createVisual(source, first(children, 0), event, baseAngle);
      String secondary = first(children, 1);
      for (int i = 0; i < 4; i++) {
        createVisual(source, secondary, event, i * MathUtils.PI2 / 4f);
      }
      return;
    }
    float baseAngle = MathUtils.atan2(event.dy, event.dx);
    for (String child : children) createVisual(source, child, event, baseAngle);
  }

  private void createVisual(Missiles.Entry source, String childName,
      MissileImpactEvent event, float angle) {
    createVisual(source, childName, event, angle, 0f, 0f);
  }

  private void createVisual(Missiles.Entry source, String childName,
      MissileImpactEvent event, float angle, float offsetX, float offsetY) {
    if (childName == null || childName.isEmpty()) return;
    Missiles.Entry child = Riiablo.files.Missiles.get(childName);
    if (child == null) {
      log.warn("[MISSILE_IMPACT] source={} missing CltHitSubMissile={}",
          source.Missile, childName);
      return;
    }
    direction.set(MathUtils.cos(angle), MathUtils.sin(angle));
    int id;
    if (factory instanceof ClientEntityFactory) {
      id = ((ClientEntityFactory) factory).createMissilePresentation(child, direction,
          new Vector2(event.x + offsetX, event.y + offsetY));
    } else {
      id = factory.createMissile(child, direction,
          new Vector2(event.x + offsetX, event.y + offsetY), -1);
    }
    if (id == com.riiablo.engine.Engine.INVALID_ENTITY || !mMissile.has(id)) return;
    Missile visual = mMissile.get(id);
    visual.authoritative = false;
    visual.presentationOnly = true;
    visual.ownerId = -1;
    visual.persistent = false;
    // D2 uses Range as a distance for moving missiles, but as a frame clock
    // for zero-velocity one-shot effects. Treating both forms as a frame
    // lifetime makes slow directional hit missiles disappear before they
    // reach their native endpoint.
    visual.nativeLifetimeFrames = nativePresentationLifetimeFrames(child);
    log.debug("[MISSILE_IMPACT] source={} sourceId={} child={} entity={} pos=({}, {})",
        source.Missile, event.missileEntityId, childName, id, event.x, event.y);
  }

  private static String first(String[] values, int index) {
    return values != null && index >= 0 && index < values.length ? values[index] : null;
  }

  /** Native client Frozen Orb step: cHitPar1 is the stride through 64 offsets. */
  static int clientHitStep(Missiles.Entry source) {
    if (source == null || source.cHitPar == null || source.cHitPar.length == 0) return 4;
    return Math.max(1, source.cHitPar[0]);
  }

  /** Number of client Frozen Orb scatter children emitted for a row. */
  static int clientHitScatterCount(Missiles.Entry source) {
    int step = clientHitStep(source);
    return (FROZEN_ORB_X.length + step - 1) / step;
  }

  static int cltParam(Missiles.Entry source, int index, int fallback) {
    return source != null && source.CltParam != null && index >= 0
        && index < source.CltParam.length && source.CltParam[index] != 0
        ? source.CltParam[index] : fallback;
  }

  static int nativePresentationLifetimeFrames(Missiles.Entry child) {
    if (child == null || child.Vel != 0) return 0;
    return child.Range > 0
        ? Math.max(1, child.Range)
        : Math.max(1, child.AnimLen > 0 ? child.AnimLen : 25);
  }

  /** Native SoundInfo.GetHitSound mapping for Missiles.txt.HitClass. */
  static String hitClassSound(int hitClass, int targetEntityId) {
    if (hitClass == 10 && targetEntityId >= 0) return "impact_arrow_1";
    switch (hitClass) {
      case 32: return "impact_fire_1";
      case 48: return "impact_cold_1";
      case 64: return "impact_lightning_1";
      case 80: return "impact_poison_1";
      case 96: return "impact_stun_1";
      case 112: return "impact_bash";
      case 176: return "impact_goo_1";
      default: return null;
    }
  }
}
