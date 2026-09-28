package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.MathUtils;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.NativeRng;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.client.component.AnimationWrapper;
import com.riiablo.engine.server.event.MissileImpactEvent;
import com.riiablo.map.DT1.Tile;
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
    // Impact children are created from the collision event after the regular
    // MissileLoader pass has already run for this tick.  Very short native
    // effects (fireexplosion2 uses a one-frame Range) would otherwise be
    // advanced and deleted before their DCC can be attached, making the hit
    // appear to have no explosion at all.  Keep the presentation entity alive
    // until MissileLoader has installed its animation component.
    if (!mAnimationWrapper.has(entityId)) return;
    float delta = Math.max(0f, world.delta);
    Vector2 velocity = mVelocity.get(entityId).velocity;
    float distance = velocity.len() * delta;

    // pCltDoFunc is a client presentation callback and must run for both
    // network replicas and the local authoritative simulation.  The latter
    // is the normal single-player path: the server creates the PoisonSparks
    // cloud, while this system supplies the separate PoisonSmokePuff layer.
    // The client callback owns an independent frame clock because the server
    // simulation advances nativeFrame before this presentation system runs.
    if (!visual.presentationOnly) {
      processClientFlightFunction(entityId, visual, velocity, delta);
      return;
    }
    // A client-only flight child can itself carry a native client callback.
    // Plague Javelin creates presentation-only plaguejavcloud entities from
    // pCltDoFunc=3; those cloud entities must still run pCltDoFunc=4 to emit
    // their PoisonSmokePuff layer.  Skipping the callback here leaves only the
    // PoisonSparks dots along the trail.
    if (shouldProcessClientFlightCallback(visual)) {
      processClientFlightFunction(entityId, visual, velocity, delta);
    }
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
    AnimationWrapper animation = mAnimationWrapper.get(entityId);
    // Native one-shot impact rows use Range as their frame clock.  Their DCC
    // may report "finished" as soon as the loader installs the first frame;
    // deleting on that signal would remove fireexplosion2 before the renderer
    // gets a visible frame.  Only rows without a native lifetime use the
    // animation-finished fallback.
    if (visual.nativeLifetimeFrames <= 0
        && !animation.animation.isLooping()
        && animation.animation.isFinished()) {
      world.delete(entityId);
    }
  }

  private void processClientFlightFunction(int entityId, Missile visual,
      Vector2 velocity, float delta) {
    Missiles.Entry source = visual.missile;
    if (source == null) return;
    int function = source.pCltDoFunc;
    // Native client callback 3 is used by Plague Javelin itself.  Its server
    // callback is SrvDo03, so the server only creates the poison cloud fan-out
    // at impact; the client callback must lay the visual cloud trail while the
    // javelin is still in flight.  Poison Javelin also has pCltDoFunc=3, but
    // its SrvDo02 already emits authoritative cloud children every update, so
    // do not add a second client-only trail for that row.
    // Native client callback 4 is used by poisonjavcloud/plaguejavcloud:
    // frame zero is skipped, then CltParam1 is the reciprocal spawn chance
    // on each later update. The
    // authoritative server already replicates the small PoisonSparks cloud;
    // this callback supplies the separate PoisonSmokePuff layer seen in D2.
    if (!isClientFlightFunction(function)) return;
    if (function == 3 && source.pSrvDoFunc != 3) return;
    String childName = first(source.CltSubMissile, 0);
    if (childName == null || childName.isEmpty() || factory == null) return;

    // Callback 3 creates its first child on initialization. Callback 4 is
    // different: its native helper first reads MISSILE_GetCurrentFrame and
    // returns when the frame is zero, so the PoisonSmokePuff layer must wait
    // for a later client update.
    boolean trailCallback = function == 3;
    if (!visual.clientFlightInitialized && trailCallback) {
      Vector2 at = mPosition.get(entityId).position;
      float angle = velocity.isZero(0.0001f) ? 0f : MathUtils.atan2(velocity.y, velocity.x);
      createFlightVisual(source, childName, at, angle);
      visual.clientFlightInitialized = true;
      // Callback 3 emits one cloud at the missile's current position on the
      // initialization update.  Do not emit a second cloud for that same
      // update; subsequent updates emit one cloud each frame.
      if (trailCallback) {
        advanceClientFrame(visual, Math.max(1, Math.round(delta * 25f)));
        return;
      }
    }
    if (!visual.clientFlightInitialized) visual.clientFlightInitialized = true;

    int elapsedFrames = Math.max(1, Math.round(delta * 25f));
    int previousFrame = advanceClientFrame(visual, elapsedFrames);
    int interval = trailCallback ? 1 : Math.max(1, cltParam(source, 0, 1));
    int count = Math.max(1, cltParam(source, 1, 1));
    int radius = Math.max(0, cltParam(source, 2, 0));
    int firstFrame = previousFrame + 1;
    int lastFrame = visual.clientFrame;
    for (int frame = firstFrame; frame <= lastFrame; frame++) {
      // Native callback 4 (D2Client 1.10f, 0x6FB30470) does not use a
      // frame modulo. Frame zero emits immediately; subsequent updates
      // consume the missile RNG and emit with probability 1/interval.
      if (!trailCallback && !rollClientSpawn(visual, interval)) continue;
      Vector2 at = mPosition.get(entityId).position;
      float angle = velocity.isZero(0.0001f) ? 0f : MathUtils.atan2(velocity.y, velocity.x);
      for (int i = 0; i < count; i++) {
        float offsetX = radius > 0
            ? clientOffsetToWorld(randomClientOffset(visual, radius)) : 0f;
        float offsetY = radius > 0
            ? clientOffsetToWorld(randomClientOffset(visual, radius)) : 0f;
        createFlightVisual(source, childName, at, angle, offsetX, offsetY);
      }
    }
  }

  static boolean isClientFlightFunction(int function) {
    return function == 3 || function == 4 || function == 8 || function == 49;
  }

  /**
   * Client-created cloud children still execute their own native client
   * callback; terminal visuals such as poisonpuff do not.
   */
  static boolean shouldProcessClientFlightCallback(Missile visual) {
    return visual != null && visual.missile != null
        && isClientFlightFunction(visual.missile.pCltDoFunc);
  }

  static int advanceClientFrame(Missile visual, int elapsedFrames) {
    if (visual == null) return 0;
    int previousFrame = visual.clientFrame;
    visual.clientFrame += Math.max(1, elapsedFrames);
    return previousFrame;
  }

  /** Native callback-4 spawn roll: one success in {@code interval} updates. */
  static boolean rollClientSpawn(Missile visual, int interval) {
    if (interval <= 1) return true;
    if (visual == null) return false;
    NativeRng rng = new NativeRng(visual.rngState);
    boolean emit = rng.nextInt(interval) == 0;
    visual.rngState = rng.state();
    return emit;
  }

  /** CltParam3 is a symmetric integer offset around the source position. */
  static int randomClientOffset(Missile visual, int radius) {
    if (radius <= 0 || visual == null) return 0;
    NativeRng rng = new NativeRng(visual.rngState);
    int offset = rng.nextInt(radius * 2 + 1) - radius;
    visual.rngState = rng.state();
    return offset;
  }

  /**
   * Normalizes the client-only jitter for Riiablo's world coordinates. Using
   * the raw value as world subtiles turns vanilla radius 6 into a 96-pixel
   * lateral spread instead of a small displacement around the trail.
   */
  static float clientOffsetToWorld(int offset) {
    return offset / (float) Tile.SUBTILE_WIDTH50;
  }

  private void createFlightVisual(Missiles.Entry source, String childName,
      Vector2 position, float angle) {
    createFlightVisual(source, childName, position, angle, 0f, 0f);
  }

  private void createFlightVisual(Missiles.Entry source, String childName,
      Vector2 position, float angle, float offsetX, float offsetY) {
    Missiles.Entry child = Riiablo.files.Missiles.get(childName);
    if (child == null) {
      log.warn("[MISSILE_FLIGHT] source={} missing CltSubMissile={}",
          source.Missile, childName);
      return;
    }
    direction.set(MathUtils.cos(angle), MathUtils.sin(angle));
    int id;
    Vector2 visualPosition = offsetX == 0f && offsetY == 0f
        ? position : new Vector2(position.x + offsetX, position.y + offsetY);
    if (factory instanceof ClientEntityFactory) {
      id = ((ClientEntityFactory) factory).createMissilePresentation(child, direction,
          visualPosition);
    } else {
      id = factory.createMissile(child, direction, visualPosition, -1);
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

    String hitSound = event.impactSound != null ? event.impactSound : source.HitSound;
    if (hitSound != null && !hitSound.isEmpty() && Riiablo.audio != null) {
      // An empty native HitSound means silence.  Do not substitute a generic
      // impact sound: that changes the observable Missiles.txt behavior.
      Riiablo.audio.play(hitSound, true);
    }
    String hitClassSound = hitClassSound(source.HitClass, event.targetEntityId);
    if (hitClassSound != null && Riiablo.audio != null) {
      Riiablo.audio.play(hitClassSound, true);
    }

    // SrvHit22 creates the Holy Bolt authoritatively when the delay missile
    // expires.  The native client callback number 26 is only the legacy
    // client-side fallback; consuming it here as well would render a second
    // bolt on both local and network clients.
    if (source.pCltHitFunc == 26 && source.pSrvHitFunc == 22) {
      log.debug("[MISSILE_IMPACT] source={} clientHitFunc=26 action=use_server_bolt",
          source.Missile);
      return;
    }

    if (source.pCltHitFunc == 19) {
      log.info("[MISSILE_IMPACT_FUNC19] source={} sourceId={} param1={} param2={} "
              + "child1={} child2={} impact=({}, {}) direction=({}, {})",
          source.Missile, event.missileEntityId, clientHit19Param(source, 0, 0),
          clientHit19Param(source, 1, 0), first(source.CltHitSubMissile, 0),
          first(source.CltHitSubMissile, 1), event.x, event.y, event.dx, event.dy);
    }

    String[] children = source.CltHitSubMissile;
    if ((children == null || children.length == 0) && event.impactDcc != null) {
      for (Missiles.Entry candidate : Riiablo.files.Missiles) {
        if (candidate != null && event.impactDcc.equalsIgnoreCase(candidate.CelFile)) {
          children = new String[] {candidate.Missile};
          break;
        }
      }
    }
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
      int radialCount = clientHit14RadialCount(source);
      for (int i = 0; i < radialCount; i++) {
        createVisual(source, secondary, event,
            clientHit14RadialAngle(baseAngle, i, radialCount));
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

  /**
   * Native client hit function 14 emits four cardinal secondary effects for
   * the 1.10f freezing-arrow family.  A few custom data packs expose a
   * positive cHitPar1 override; honor it without changing the vanilla zero
   * value semantics.
   */
  static int clientHit14RadialCount(Missiles.Entry source) {
    if (source != null && source.cHitPar != null && source.cHitPar.length > 0
        && source.cHitPar[0] > 0) return source.cHitPar[0];
    return 4;
  }

  static float clientHit14RadialAngle(float baseAngle, int index, int count) {
    if (count <= 0) return baseAngle;
    return baseAngle + index * MathUtils.PI2 / count;
  }

  static int cltParam(Missiles.Entry source, int index, int fallback) {
    return source != null && source.CltParam != null && index >= 0
        && index < source.CltParam.length && source.CltParam[index] != 0
        ? source.CltParam[index] : fallback;
  }

  /** Raw pCltHitFunc=19 parameter accessor; zero is a valid table value. */
  static int clientHit19Param(Missiles.Entry source, int index, int fallback) {
    return source != null && source.CltParam != null && index >= 0
        && index < source.CltParam.length ? source.CltParam[index] : fallback;
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
