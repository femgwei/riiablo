package com.riiablo.presentation;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.MathUtils;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.NativeRng;
import com.riiablo.engine.server.missile.NativePoisonMissileMotion;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.ServerSkillSystem;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.audio.MonsterAudio;
import com.riiablo.presentation.component.AnimationWrapper;
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
  @com.artemis.annotations.Wire(failOnNull = false)
  protected OverlayManager overlays;
  @com.artemis.annotations.Wire(name = "clientContext")
  protected ClientGameplayContext clientContext ;

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
    ReferenceElementalPresentationSystem referenceElemental =
        world.getSystem(ReferenceElementalPresentationSystem.class);
    if (referenceElemental != null && referenceElemental.ownsPresentation(entityId)) return;
    Missile visual = mMissile.get(entityId);
    if (isFireWallMaker(visual)) {
      // Local authority has a separate presentation-only maker created by
      // SkillCastHandler. The authoritative maker remains combat-only.
      if (!visual.presentationOnly && world.getSystem(ServerSkillSystem.class) != null) return;
      processFireWallMaker(entityId, visual, mPosition.get(entityId).position,
          mVelocity.get(entityId).velocity, Math.max(0f, world.delta));
      return;
    }
    // Impact children are created from the collision event after the regular
    // MissileLoader pass has already run for this tick.  Very short native
    // effects (fireexplosion2 uses a one-frame Range) would otherwise be
    // advanced and deleted before their DCC can be attached, making the hit
    // appear to have no explosion at all.  Keep the presentation entity alive
    // until MissileLoader has installed its animation component.
    // Audited CltDo3/4/15 and poisonpuff clocks are simulation clocks, not
    // animation clocks. A cold DCC must not pause their movement/emission/TTL.
    boolean callbackClock = visual.missile != null
        && (visual.missile.pCltDoFunc == 9 || visual.missile.pCltDoFunc == 13
            || visual.missile.pCltDoFunc == 24
            || visual.missile.pCltDoFunc == 17 || visual.missile.pCltDoFunc == 18
            || visual.missile.pCltDoFunc == 15 || visual.missile.pCltDoFunc == 3
            || visual.missile.pCltDoFunc == 4 || MissileLoader.isPoisonSmokePuff(visual.missile));
    if (!mAnimationWrapper.has(entityId) && !callbackClock) return;
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
      if (MissileLoader.isPoisonSmokePuff(visual.missile)) {
        NativePoisonMissileMotion.advance(mPosition.get(entityId), velocity,
            Math.max(1, Math.round(delta * 25f)));
      } else {
        mPosition.get(entityId).invalidateNativePrecision();
        mPosition.get(entityId).position.mulAdd(velocity, delta);
      }
      visual.distanceTraveled += distance;
    }
    visual.nativeFrame += Math.max(1, Math.round(delta * 25f));
    if (visual.range > 0f && visual.distanceTraveled >= visual.range) {
      world.delete(entityId);
      return;
    }
    if (visual.nativeLifetimeFrames > 0
        && visual.nativeFrame >= visual.nativeLifetimeFrames) {
      processClientHitFunction(visual, mPosition.get(entityId).position);
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
        && animation != null
        && !animation.animation.isLooping()
        && animation.animation.isFinished()) {
      processClientHitFunction(visual, mPosition.get(entityId).position);
      world.delete(entityId);
    }
  }

  /**
   * Native client hit callback 31 used by the icebreak death missiles.
   *
   * <p>The callback is not an impact child callback: the icebreak animation
   * itself is the visible shatter, and the native client creates the melt
   * missile only after that animation reaches its terminal frame.  The
   * Missiles.txt rows do not carry a child name, so the two vanilla mappings
   * are kept here exactly as in the 1.10f client:</p>
   *
   * <ul>
   *   <li>icebreaksmall -> icebreaksmallmelt</li>
   *   <li>icebreakmedium/icebreaklarge -> icebreaklargemelt</li>
   * </ul>
   */
  private void processClientHitFunction(Missile visual, Vector2 position) {
    if (visual == null || visual.missile == null || position == null
        || visual.missile.pCltHitFunc != 31 || factory == null
        || clientContext.files() == null || clientContext.files().Missiles == null) return;
    String childName = clientHit31Child(visual.missile.Missile);
    if (childName == null) return;
    Missiles.Entry child = clientContext.files().Missiles.get(childName);
    if (child == null) {
      log.warn("[MISSILE_IMPACT_FUNC31] source={} missing child={}",
          visual.missile.Missile, childName);
      return;
    }
    int id;
    if (factory instanceof ClientEntityFactory) {
      id = ((ClientEntityFactory) factory).createMissilePresentation(
          child, Vector2.X, new Vector2(position));
    } else {
      id = factory.createMissile(child, Vector2.X, new Vector2(position), -1);
    }
    if (id == com.riiablo.engine.Engine.INVALID_ENTITY || !mMissile.has(id)) return;
    Missile melt = mMissile.get(id);
    melt.authoritative = false;
    melt.presentationOnly = true;
    melt.ownerId = -1;
    melt.persistent = false;
    melt.nativeLifetimeFrames = nativePresentationLifetimeFrames(child);
    log.info("[MISSILE_IMPACT_FUNC31] source={} child={} entity={} pos=({}, {})",
        visual.missile.Missile, childName, id, position.x, position.y);
  }

  static String clientHit31Child(String sourceName) {
    if (sourceName == null) return null;
    if ("icebreaksmall".equalsIgnoreCase(sourceName)) return "icebreaksmallmelt";
    if ("icebreakmedium".equalsIgnoreCase(sourceName)
        || "icebreaklarge".equalsIgnoreCase(sourceName)) return "icebreaklargemelt";
    return null;
  }

  private void processClientFlightFunction(int entityId, Missile visual,
      Vector2 velocity, float delta) {
    Missiles.Entry source = visual.missile;
    if (source == null) return;
    int function = source.pCltDoFunc;
    if (function == 24) {
      // Sorceress Inferno's CltDo24 mutates the resident flame DCC (A/B
      // selection and animation-rate setup); it does not emit another damage
      // missile.  MissileLoader performs the DCC selection, while this route
      // keeps network replicas on the native callback clock.
      if (visual.nativeLifetimeFrames <= 0) {
        visual.nativeLifetimeFrames = Math.max(1, source.Range);
      }
      visual.clientFlightInitialized = true;
      return;
    }
    if (function == 17) {
      processCurseCenter(entityId, visual, source, delta);
      return;
    }
    if (function == 18) {
      processBoneSpearTrail(entityId, visual, source, velocity, delta);
      return;
    }
    if (function == 15) {
      processFingerMageFlight(entityId, visual, source, velocity, delta);
      return;
    }
    // Native client callback 51 is used by recycler delay missiles.  The
    // server-side SrvDo33 only holds the stationary delay missile and does
    // not create these render-only vine/explosion children.
    if (function == 51) {
      processRecyclerDelayFunction(entityId, visual, source, delta);
      return;
    }
    if (function == 3 || function == 4) {
      processPoisonFlight(entityId, visual, source, velocity, delta);
      return;
    }
    // Blizzard's native CltDo13 is a presentation-only rain emitter.  The
    // server owns the damage-bearing blizzard1 missiles; the client callback
    // independently alternates the two shipped fall DCCs, so this must never
    // create an authoritative missile or an impact event.
    if (function == 13) {
      processBlizzardVisual(entityId, visual, source, delta);
      return;
    }
    // Meteor's CltDo9 draws the falling rock and its tail above the target
    // indicator.  The center remains authoritative and damage is resolved by
    // MissileCollisionSystem; these children are render-only.
    if (function == 9) {
      processMeteorVisual(entityId, visual, source);
      return;
    }
    // Keep the unaudited callback 8/49 fallback separate from poison's
    // native target coordinates and two-word client random stream.
    if (!isClientFlightFunction(function)) return;
    String childName = first(source.CltSubMissile, 0);
    if (childName == null || childName.isEmpty() || factory == null) return;

    if (!visual.clientFlightInitialized) visual.clientFlightInitialized = true;

    int elapsedFrames = Math.max(1, Math.round(delta * 25f));
    int previousFrame = advanceClientFrame(visual, elapsedFrames);
    int interval = Math.max(1, cltParam(source, 0, 1));
    int count = Math.max(1, cltParam(source, 1, 1));
    int radius = Math.max(0, cltParam(source, 2, 0));
    int firstFrame = previousFrame + 1;
    int lastFrame = visual.clientFrame;
    for (int frame = firstFrame; frame <= lastFrame; frame++) {
      if (!rollLegacyClientSpawn(visual, interval)) continue;
      Vector2 at = mPosition.get(entityId).position;
      float angle = velocity.isZero(0.0001f) ? 0f : MathUtils.atan2(velocity.y, velocity.x);
      for (int i = 0; i < count; i++) {
        float offsetX = radius > 0
            ? clientOffsetToWorld(randomLegacyClientOffset(visual, radius)) : 0f;
        float offsetY = radius > 0
            ? clientOffsetToWorld(randomLegacyClientOffset(visual, radius)) : 0f;
        createFlightVisual(source, childName, at, angle, offsetX, offsetY);
      }
    }
  }

  /**
   * Native MoveFunc 6 / CltDo6: the maker itself is invisible and emits
   * one of three client-only GroundFire rows on integer path movement. The
   * server-side SubMissile rows remain hidden combat carriers.
   */
  private void processFireWallMaker(int entityId, Missile maker, Vector2 current,
      Vector2 velocity, float delta) {
    Missiles.Entry source = maker.missile;
    if (source == null || current == null || factory == null
        || source.CltSubMissile == null || source.CltSubMissile.length == 0) return;
    int elapsed = Math.max(1, Math.round(delta * 25f));
    int previous = maker.clientFrame;
    int currentFrame = previous + elapsed;
    if (!maker.presentationOnly && maker.clientSnapshotKnown) {
      currentFrame = Math.max(currentFrame, maker.nativeFrame);
    }
    int lifetime = maker.nativeLifetimeFrames > 0
        ? maker.nativeLifetimeFrames : Math.max(1, source.Range);
    currentFrame = Math.min(currentFrame, lifetime);
    if (currentFrame <= previous) return;
    if (!maker.clientFlightInitialized) {
      // Snapshots carry current position, not the cast origin. Recover that
      // origin using the server's legacy velocity before applying the native
      // presentation path. Keep it fixed between subsequent position packets.
      Vector2 origin = new Vector2(maker.start);
      if (!maker.presentationOnly && maker.clientSnapshotKnown) {
        origin.set(current).mulAdd(velocity, -maker.nativeFrame / 25f);
      }
      origin.set(MathUtils.floor(origin.x + 0.5f), MathUtils.floor(origin.y + 0.5f));
      maker.clientSamplePosition.set(origin);
      maker.clientPathX = (int) origin.x;
      maker.clientPathY = (int) origin.y;
      maker.clientFlightInitialized = true;
      // The combat centre is deliberately hidden by MissileLoader. Rebuild
      // its visual exactly once for the opposed pair, including on replicas.
      // Assigning it to the positive-facing maker is a client adapter policy.
      if (velocity.x > 0f || (velocity.x == 0f && velocity.y > 0f)) {
        createFlightVisual(source, first(source.CltSubMissile, 0), origin, Vector2.X);
      }
    }
    // Native allocation: (Vel + level*VelLev/8)<<8, scaled by 75/100;
    // D2Common then multiplies its 12-bit direction vector and shifts by 8.
    // Do not interpret the table velocity as world units per second.
    int speed = source.Vel + Math.max(1, maker.damageLevel) * source.VelLev / 8;
    Vector2 pathVelocity = NativePoisonMissileMotion.velocity(Vector2.Zero, velocity,
        (Math.max(0, speed) << 8) * 75 / 100, new Vector2());
    int stepX = NativePoisonMissileMotion.frameStep(pathVelocity.x);
    int stepY = NativePoisonMissileMotion.frameStep(pathVelocity.y);
    Vector2 origin = maker.clientSamplePosition;
    for (int frame = previous; frame < currentFrame; frame++) {
      // CltDo6 tests ordinal 10234 BEFORE the ordinary movement update.
      // Host integer positions denote native cell centres (precision +0.5).
      int x = (int) origin.x + (int) Math.floorDiv(32768L + (long) stepX * frame, 65536L);
      int y = (int) origin.y + (int) Math.floorDiv(32768L + (long) stepY * frame, 65536L);
      if (x == maker.clientPathX && y == maker.clientPathY) continue;
      maker.clientPathX = x;
      maker.clientPathY = y;
      // D2Client's CltDo6 creates one child per changed path cell. The
      // three CltSubMissile rows are alternatives, not a three-sprite stack:
      // selecting one keeps the small/medium/big ground fires interleaved
      // without turning every cell into an overbright pile of flames.
      String child = fireWallChild(maker, source);
      // CltParam1 only samples dynamic lights (creation flag 0x4000).
      // Consume the native roll, but NEVER use it to omit the flame sprite.
      int lightInterval = cltParam(source, 0, 0);
      if (lightInterval > 0) nextClientRandom(maker, lightInterval);
      if (child != null) createFlightVisual(source, child, new Vector2(x, y), Vector2.X);
    }
    maker.clientFrame = currentFrame;
    if (maker.presentationOnly) {
      current.set(origin.x + stepX * (currentFrame / 65536f),
          origin.y + stepY * (currentFrame / 65536f));
      maker.nativeFrame = currentFrame;
    }
    if (maker.presentationOnly && currentFrame >= lifetime) world.delete(entityId);
  }

  private static boolean isFireWallMaker(Missile missile) {
    return missile != null && missile.missile != null
        && missile.skillId == SkillId.FIRE_WALL
        && "firewallmaker".equalsIgnoreCase(missile.missile.Missile);
  }

  private static String fireWallChild(Missile maker, Missiles.Entry source) {
    if (maker == null || source == null || source.CltSubMissile == null) return null;
    String first = first(source.CltSubMissile, 0);
    String second = first(source.CltSubMissile, 1);
    String third = first(source.CltSubMissile, 2);
    if (first == null && second == null && third == null) return null;

    // 1.10f CltDo6 rolls modulo three after the spawn gate.  The recovered
    // callback initializes the selected id from CltSubMissile1, replaces it
    // with CltSubMissile2 for roll==0 or CltSubMissile3 for roll==1, and
    // leaves it at SubMissile1 for roll==2.  Thus all three table entries are
    // used once per three successful rolls, but never stacked at one cell.
    int selected = nextClientRandom(maker, 3);
    if (selected == 0 && second != null) return second;
    if (selected == 1 && third != null) return third;
    if (first != null) return first;
    return second != null ? second : third;
  }

  /** 1.10f 6FB32470: one stationary directional trail cell per client update. */
  private void processBoneSpearTrail(int entityId, Missile visual, Missiles.Entry source,
      Vector2 velocity, float delta) {
    if (factory == null) return;
    int previous = advanceClientFrame(visual, Math.max(1, Math.round(delta * 25f)));
    int lifetime = visual.nativeLifetimeFrames > 0 ? visual.nativeLifetimeFrames : source.Range;
    for (int frame = previous; frame < visual.clientFrame; frame++) {
      if (lifetime - frame < source.InitSteps) continue;
      int id = createFlightVisual(source, first(source.CltSubMissile, 0),
          mPosition.get(entityId).position, velocity.isZero() ? Vector2.X : velocity);
      if (id >= 0 && mVelocity.has(id)) mVelocity.get(id).velocity.setZero();
    }
  }

  /** 1.10f 6FB32310 + 6FB35D10: three late sparse circular curse-area pulses. */
  private void processCurseCenter(int entityId, Missile visual, Missiles.Entry source, float delta) {
    int previous = advanceClientFrame(visual, Math.max(1, Math.round(delta * 25f)));
    int radius = visual.clientPathX;
    if (radius <= 0 || factory == null) return;
    Missiles.Entry child = clientContext.files().Missiles.get(visual.clientPathY);
    if (child == null) return;
    int lifetime = visual.nativeLifetimeFrames > 0 ? visual.nativeLifetimeFrames : source.Range;
    Vector2 at = mPosition.get(entityId).position;
    for (int frame = previous; frame < visual.clientFrame; frame++) {
      if (!cursePulseFrame(lifetime - frame, cltParam(source, 0, 7))) continue;
      int step = radius > 4 ? 2 : 1;
      int chance = radius > 4 ? radius / 2 : radius;
      for (int x = -radius; x <= radius; x += step) {
        for (int y = -radius; y <= radius; y += step) {
          if (x * x + y * y > radius * radius || !rollClientSpawn(visual, chance)) continue;
          createFlightVisual(source, child.Missile,
              new Vector2(MathUtils.floor(at.x) + x, MathUtils.floor(at.y) + y), Vector2.X);
        }
      }
    }
  }

  static boolean cursePulseFrame(int remaining, int threshold) {
    return remaining >= 0 && remaining < threshold && remaining % 3 == 0;
  }

  private void processBlizzardVisual(int entityId, Missile visual,
      Missiles.Entry source, float delta) {
    if (factory == null || source.CltSubMissile == null
        || source.CltSubMissile.length == 0) return;
    int previous = advanceClientFrame(visual, Math.max(1, Math.round(delta * 25f)));
    int interval = Math.max(1, cltParam(source, 0, 1));
    for (int frame = previous + 1; frame <= visual.clientFrame; frame++) {
      if (!rollClientSpawn(visual, interval)) continue;
      int n = Math.min(2, source.CltSubMissile.length);
      String child = first(source.CltSubMissile, nextClientRandom(visual, n));
      if (child != null) {
        int radius = Math.max(1, cltParam(source, 1, 5));
        Vector2 origin = mPosition.get(entityId).position;
        float x = MathUtils.floor(origin.x) + randomClientTargetOffset(visual, radius);
        float y = MathUtils.floor(origin.y) + randomClientTargetOffset(visual, radius);
        int id = createFlightVisual(source, child,
            new Vector2(x, y), 0f);
        // CltDo13 starts both Blizzard fall DCCs at frame zero and lets the
        // animation run to completion.  The client row has a non-zero Vel,
        // so the generic presentation lifetime intentionally returns zero;
        // retain the DCC's own frame count here so a replica cannot be
        // retired before the second (8-frame Blizzard) variant is visible.
        if (id != com.riiablo.engine.Engine.INVALID_ENTITY && mMissile.has(id)) {
          Missiles.Entry fall = clientContext.files().Missiles.get(child);
          if (fall != null) mMissile.get(id).nativeLifetimeFrames =
              Math.max(1, fall.AnimLen);
        }
      }
    }
  }

  private void processMeteorVisual(int entityId, Missile visual,
      Missiles.Entry source) {
    // CltDo9 is invoked once when the meteor center is initialized.  The
    // center itself remains alive for the 59-frame countdown; re-running this
    // branch every tick would stack a new rock and tail on the target and
    // make the result look like a frozen sprite.
    if (visual.clientFlightInitialized || factory == null
        || source.CltSubMissile == null) return;
    Vector2 at = mPosition.get(entityId).position;
    int spread = Math.max(1, cltParam(source, 1, 25) - 1);
    // World coordinates use an isometric projection with screenY = -(x + y).
    // The old code added the fall distance to both axes, which put the rock
    // below the impact point on screen and made it appear to rise.  Native
    // CltDo9 starts above the target and advances toward it, so mirror the
    // offset in world space here.  Keep the client random stream and spread
    // unchanged; only the descent direction is corrected.
    Vector2 impact = meteorVisualStart(at, cltParam(source, 0, 59),
        nextClientRandom(visual, spread * 2), nextClientRandom(visual, spread * 2),
        new Vector2());
    String rock = first(source.CltSubMissile, 0);
    String tail = first(source.CltSubMissile, 1);
    createMeteorVisual(source, rock, impact, at);
    createMeteorVisual(source, tail, impact, at);
    visual.clientFlightInitialized = true;
  }

  /** Converts the native CltDo9 fall offset into the host's isometric world axes. */
  static Vector2 meteorVisualStart(Vector2 target, int fallDistance,
      int randomX, int randomY, Vector2 out) {
    int distance = Math.max(0, fallDistance);
    return out.set(MathUtils.floor(target.x) - distance + randomX,
        MathUtils.floor(target.y) - distance + randomY);
  }

  /** Creates the rock/tail pair and gives them the native 59-frame descent. */
  private void createMeteorVisual(Missiles.Entry source, String childName,
      Vector2 start, Vector2 target) {
    if (childName == null) return;
    int id = createFlightVisual(source, childName, start, 0f);
    if (id == com.riiablo.engine.Engine.INVALID_ENTITY || !mMissile.has(id)) return;
    Missile child = mMissile.get(id);
    child.nativeLifetimeFrames = Math.max(1, cltParam(source, 0, 59));
    // Meteor's CltDo9 moves the visual down from the fall row to the target;
    // keep authoritative damage at the center and animate only this replica.
    if (mVelocity.has(id)) {
      float frames = child.nativeLifetimeFrames;
      mVelocity.get(id).velocity.set(target).sub(start).scl(25f / frames);
    }
  }

  private void processPoisonFlight(int entityId, Missile visual, Missiles.Entry source,
      Vector2 velocity, float delta) {
    processPoisonFlight(entityId, visual, source, velocity, delta, 0);
  }

  private void processPoisonFlight(int entityId, Missile visual, Missiles.Entry source,
      Vector2 velocity, float delta, int historyAge) {
    // SrvDo2 already supplies Poison Javelin's trail; Plague's SrvDo3 does not.
    if (source.pCltDoFunc == 3 && source.pSrvDoFunc != 3) return;
    if (factory == null) return;
    String child = first(source.CltSubMissile, 0);
    if (child == null || child.isEmpty()) return;
    boolean networkClock = !visual.authoritative && !visual.presentationOnly
        && visual.clientSnapshotKnown;
    int previous = visual.clientFrame;
    int localEnd = previous + Math.max(1, Math.round(delta * 25f));
    // The local 25-Hz presentation clock keeps running between packets.
    // Accepted snapshots can catch it up, never pause or rewind it. Some
    // transports/older servers repeat the birth frame on position updates.
    int end = networkClock ? Math.max(visual.nativeFrame, localEnd) : localEnd;
    // Network history cannot contain callbacks beyond the missile's lifetime.
    // Also bound malformed/legacy snapshots whose lifetime is unavailable.
    if (networkClock) end = Math.min(end, visual.nativeLifetimeFrames > 0
        ? visual.nativeLifetimeFrames : Math.max(1, source.Range));
    if (end <= previous) return;
    Vector2 current = mPosition.get(entityId).position;
    // Linear poison missiles can reconstruct missed native ticks from two
    // accepted snapshots. Never emit once for every crossed grid boundary:
    // native CltDo3 emits at most once per 25-Hz update, even at high speed.
    Vector2 begin = networkClock && visual.clientSampleFrame >= 0
        ? new Vector2(visual.clientSamplePosition)
        : new Vector2(current).mulAdd(velocity, networkClock ? -(end - previous) / 25f : 0f);
    if (networkClock && !visual.clientFlightInitialized && source.pCltDoFunc == 3) {
      visual.clientPathX = MathUtils.floor(begin.x);
      visual.clientPathY = MathUtils.floor(begin.y);
      visual.clientFlightInitialized = true;
    }
    int interval = source.CltParam != null && source.CltParam.length > 0
        ? source.CltParam[0] : 0;
    int count = Math.max(1, cltParam(source, 1, 1));
    int radius = Math.max(0, cltParam(source, 2, 0));
    for (int frame = previous; frame < end; frame++) {
      visual.clientFrame = frame + 1;
      Vector2 at = networkClock
          ? new Vector2(begin).lerp(current, (frame - previous + 1f) / (end - previous))
          : new Vector2(current).mulAdd(velocity, (visual.presentationOnly
              ? frame - previous : frame - end + 1) / 25f);
      if (networkClock) {
        // Interpolation's float roundoff must not change integer-cell gates
        // or LUT bins depending on how many snapshots happened to arrive.
        at.set((float) (Math.rint(at.x * 65536.0) / 65536.0),
            (float) (Math.rint(at.y * 65536.0) / 65536.0));
      }
      int age = end - frame - 1 + historyAge;
      if (source.pCltDoFunc == 3) {
        if (!poisonTrailCellChanged(visual, at)) continue;
        int id = createFlightVisual(source, child, at, Vector2.X);
        if (id < 0 || !mMissile.has(id)) continue;
        Missile cloud = mMissile.get(id);
        cloud.damageLevel = visual.damageLevel;
        cloud.poisonCloudLoops = cloud.missile.SubLoop > 0
            ? 2 * Math.max(0, visual.damageLevel - 1) : 0;
        cloud.nativeLifetimeFrames = Math.max(1, cloud.missile.Range
            + cloud.poisonCloudLoops * Math.max(0, cloud.missile.SubStop - cloud.missile.SubStart));
        // Restore the previously validated moving presentation carrier.
        // This is an ECS visual compatibility policy, NOT a claim that the
        // native 0x8005 creation flag means velocity inheritance. Its base
        // DCC remains hidden; only its smoke emitter follows this motion.
        mVelocity.get(id).velocity.set(velocity);
        seedFlightChild(visual, cloud, frame);
        agePoisonVisual(id, age);
        continue;
      }
      // #11124 is REMAINING frames, not elapsed frames. Only zero remaining
      // (or a nonpositive interval) bypasses the random roll.
      int remaining = visual.nativeLifetimeFrames - frame;
      if (remaining != 0 && !rollClientSpawn(visual, interval)) continue;
      for (int i = 0; i < count; i++) {
        int dx = randomClientTargetOffset(visual, radius);
        int dy = randomClientTargetOffset(visual, radius);
        float targetX = MathUtils.floor(at.x) + dx;
        float targetY = MathUtils.floor(at.y) + dy;
        // Flags 0x20: inherit the parent's birth position, use the two
        // randomized ABSOLUTE target coordinates to choose the path.
        int id = createFlightVisual(source, child, at,
            new Vector2(targetX - at.x, targetY - at.y).nor());
        if (id >= 0 && mMissile.has(id)) {
          Missile puff = mMissile.get(id);
          int nativeVelocity = (Math.max(0, puff.missile.Vel) << 8) * 75 / 100;
          NativePoisonMissileMotion.velocity(at, new Vector2(targetX, targetY),
              nativeVelocity, mVelocity.get(id).velocity);
          agePoisonVisual(id, age);
        }
      }
    }
    visual.clientSampleFrame = end;
    visual.clientSamplePosition.set(current);
  }

  /** Stable child allocation policy, independent of each client's local ECS ids. */
  private static void seedFlightChild(Missile parent, Missile child, int frame) {
    if (!parent.presentationSeedKnown) return;
    child.presentationSeed = NativeRng.forUnit(parent.presentationSeed, frame).state();
    child.presentationSeedKnown = true;
    child.clientSeed = new D2Seed(child.presentationSeed, 666);
  }

  /** Bring historical smoke to its current age rather than drawing fresh puffs in a burst. */
  private void agePoisonVisual(int id, int age) {
    if (age <= 0 || !mMissile.has(id)) return;
    Missile child = mMissile.get(id);
    if (child.nativeLifetimeFrames > 0 && age >= child.nativeLifetimeFrames) {
      // A cloud can outlive its own emitter through its last 25-frame puffs.
      // Replay callbacks below; terminal expired puffs need no render entity.
      if (child.missile.pCltDoFunc != 4) { world.delete(id); return; }
    }
    if (child.missile.pCltDoFunc == 4) {
      // Use the same authoritative-clock catch-up for the reconstructed carrier.
      child.presentationOnly = false;
      child.clientSnapshotKnown = true;
      child.nativeFrame = Math.min(age, child.nativeLifetimeFrames);
      Vector2 birth = new Vector2(mPosition.get(id).position);
      Vector2 velocity = mVelocity.get(id).velocity;
      // Local carriers emit BEFORE moving. Replay that phase, not the
      // server replica's post-movement phase, then place the carrier at age.
      child.clientSampleFrame = 0;
      child.clientSamplePosition.set(birth).mulAdd(velocity, -1f / 25f);
      mPosition.get(id).position.set(birth).mulAdd(velocity,
          (child.nativeFrame - 1f) / 25f);
      processPoisonFlight(id, child, child.missile, mVelocity.get(id).velocity,
          1f / 25f, Math.max(0, age - child.nativeFrame));
      mPosition.get(id).position.set(birth).mulAdd(velocity, age / 25f);
      child.presentationOnly = true;
    } else {
      NativePoisonMissileMotion.advance(mPosition.get(id), mVelocity.get(id).velocity, age);
    }
    child.nativeFrame = age;
    if (child.nativeLifetimeFrames > 0 && age >= child.nativeLifetimeFrames) world.delete(id);
    AnimationWrapper animation = mAnimationWrapper.get(id);
    if (animation != null && MissileLoader.isPoisonSmokePuff(child.missile)) {
      animation.animation.setFrame(MissileLoader.poisonSmokeFrame(child,
          animation.animation.getNumFramesPerDir()));
    }
  }

  static boolean poisonTrailCellChanged(Missile visual, Vector2 at) {
    int x = MathUtils.floor(at.x), y = MathUtils.floor(at.y);
    int previousX = visual.clientFlightInitialized ? visual.clientPathX : MathUtils.floor(visual.start.x);
    int previousY = visual.clientFlightInitialized ? visual.clientPathY : MathUtils.floor(visual.start.y);
    visual.clientFlightInitialized = true;
    visual.clientPathX = x;
    visual.clientPathY = y;
    return x != previousX || y != previousY;
  }

  static boolean isClientFlightFunction(int function) {
    return function == 3 || function == 4 || function == 8 || function == 9
        || function == 13 || function == 49 || function == 51 || function == 15
        || function == 17 || function == 18 || function == 24;
  }

  /** D2Client 1.10f 0x6FB31DD0: moving head plus stationary, finite trail cells. */
  private void processFingerMageFlight(int entityId, Missile visual,
      Missiles.Entry source, Vector2 velocity, float delta) {
    // Native children start at TargetX=0 and count down to -CltParam2.
    // They must neither seek a target nor recursively emit more children.
    if (visual.presentationOnly || factory == null) return;
    int previous = advanceClientFrame(visual, Math.max(1, Math.round(delta * 25f)));
    int interval = Math.max(1, cltParam(source, 0, 3));
    String childName = first(source.CltSubMissile, 0);
    Missiles.Entry child = clientContext.files() == null || childName == null ? null
        : clientContext.files().Missiles.get(childName);
    if (child == null) return;
    for (int frame = previous; frame < visual.clientFrame; frame++) {
      int remaining = visual.nativeLifetimeFrames - frame;
      if (remaining <= 0 || remaining % interval != 0) continue;
      Vector2 at = new Vector2(mPosition.get(entityId).position);
      Vector2 facing = velocity.isZero() ? new Vector2(1f, 0f) : new Vector2(velocity).nor();
      int id = factory instanceof ClientEntityFactory
          ? ((ClientEntityFactory) factory).createMissilePresentation(child, facing, at)
          : factory.createMissile(child, facing, at, -1);
      if (id < 0 || !mMissile.has(id)) continue;
      Missile trail = mMissile.get(id);
      configureFingerMageTrail(trail, source);
      mVelocity.get(id).velocity.setZero();
    }
  }

  static void configureFingerMageTrail(Missile trail, Missiles.Entry source) {
    trail.authoritative = false;
    trail.presentationOnly = true;
    trail.ownerId = -1;
    trail.persistent = false;
    trail.homing = false;
    trail.targetId = -1;
    trail.range = 0;
    trail.nativeFrame = 0;
    // CltDo15 checks TargetX <= -CltParam2 BEFORE decrementing it. Starting
    // at zero, 20 updates reach -20; update 21 deletes the vanilla trail.
    trail.nativeLifetimeFrames = Math.max(1, cltParam(source, 1, 20) + 1);
  }

  /** Executes the native pCltDoFunc=51 recycler-delay timeline. */
  private void processRecyclerDelayFunction(int entityId, Missile visual,
      Missiles.Entry source, float delta) {
    if (source == null) return;
    int elapsedFrames = Math.max(1, Math.round(delta * 25f));
    int previousFrame = advanceClientFrame(visual, elapsedFrames);
    int currentFrame = visual.clientFrame;
    int vineFrame = cltParam(source, 0, 20);
    int explosionFrame = cltParam(source, 1, 45);
    Vector2 at = mPosition.get(entityId).position;
    if (!visual.clientRecyclerVineSpawned
        && recyclerFrameReached(previousFrame, currentFrame, vineFrame)) {
      createFlightVisual(source, first(source.CltSubMissile, 0), at, 0f);
      visual.clientRecyclerVineSpawned = true;
      log.debug("[MISSILE_RECYCLER] source={} child=recycler_vine frame={} entity={}",
          source.Missile, currentFrame, entityId);
    }
    if (!visual.clientRecyclerExplosionSpawned
        && recyclerFrameReached(previousFrame, currentFrame, explosionFrame)) {
      createFlightVisual(source, first(source.CltSubMissile, 1), at, 0f);
      visual.clientRecyclerExplosionSpawned = true;
      log.debug("[MISSILE_RECYCLER] source={} child=recycler_explosion frame={} entity={}",
          source.Missile, currentFrame, entityId);
    }
    // The server removes the delay missile at Range. Keep a local replica
    // bounded as well when no deletion snapshot arrives in the same frame.
    if (visual.nativeLifetimeFrames > 0
        && currentFrame >= visual.nativeLifetimeFrames) {
      world.delete(entityId);
    }
  }

  /** True when a client update crosses a one-shot recycler callback frame. */
  static boolean recyclerFrameReached(int previousFrame, int currentFrame, int targetFrame) {
    return targetFrame >= 0 && previousFrame < targetFrame && currentFrame >= targetFrame;
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
    // Native helper does not roll when interval <= 0; bound 1 DOES consume RNG.
    return interval <= 0 || (visual != null && nextClientRandom(visual, interval) == 0);
  }

  static int nextClientRandom(Missile visual, int bound) {
    if (visual.clientSeed == null) {
      // Snapshot once, never alias or write the authoritative pSeed/mirror.
      visual.clientSeed = visual.presentationSeedKnown ? new D2Seed(visual.presentationSeed, 666)
          : visual.nativeSeed != null
          ? new D2Seed(visual.nativeSeed.getNLowSeed(), visual.nativeSeed.getNHighSeed())
          : new D2Seed(visual.rngState, 666);
    }
    return Seed.rollLimitedRandomNumber(visual.clientSeed, bound);
  }

  /** 0x6FB30470: [-2r,-r-1] or [r,2r-1], in world/subtile coordinates. */
  static int randomClientTargetOffset(Missile visual, int radius) {
    if (radius <= 0 || visual == null) return 0;
    int offset = nextClientRandom(visual, radius * 2) - radius;
    return offset + (offset < 0 ? -radius : radius);
  }

  private static boolean rollLegacyClientSpawn(Missile visual, int interval) {
    if (interval <= 1) return true;
    if (visual == null) return false;
    NativeRng rng = new NativeRng(visual.rngState);
    boolean emit = rng.nextInt(interval) == 0;
    visual.rngState = rng.state();
    return emit;
  }

  /** CltParam3 is a symmetric integer offset around the source position. */
  private static int randomLegacyClientOffset(Missile visual, int radius) {
    if (radius <= 0 || visual == null) return 0;
    NativeRng rng = new NativeRng(visual.rngState);
    int offset = rng.nextInt(radius * 2 + 1) - radius;
    visual.rngState = rng.state();
    return offset;
  }

  /** Legacy pixel-jitter fallback for unaudited callbacks 8/49, NOT poison targets. */
  private static float clientOffsetToWorld(int offset) {
    return offset / (float) Tile.SUBTILE_WIDTH50;
  }

  private int createFlightVisual(Missiles.Entry source, String childName,
      Vector2 position, float angle) {
    return createFlightVisual(source, childName, position, angle, 0f, 0f);
  }

  private int createFlightVisual(Missiles.Entry source, String childName,
      Vector2 position, float angle, float offsetX, float offsetY) {
    direction.set(MathUtils.cos(angle), MathUtils.sin(angle));
    Vector2 visualPosition = offsetX == 0f && offsetY == 0f
        ? position : new Vector2(position.x + offsetX, position.y + offsetY);
    return createFlightVisual(source, childName, visualPosition, direction);
  }

  private int createFlightVisual(Missiles.Entry source, String childName,
      Vector2 position, Vector2 facing) {
    Missiles.Entry child = clientContext.files().Missiles.get(childName);
    if (child == null) {
      log.warn("[MISSILE_FLIGHT] source={} missing CltSubMissile={}",
          source.Missile, childName);
      return com.riiablo.engine.Engine.INVALID_ENTITY;
    }
    int id;
    if (factory instanceof ClientEntityFactory) {
      id = ((ClientEntityFactory) factory).createMissilePresentation(child, facing, position);
    } else {
      id = factory.createMissile(child, facing, position, -1);
    }
    if (id == com.riiablo.engine.Engine.INVALID_ENTITY || !mMissile.has(id)) return id;
    Missile visual = mMissile.get(id);
    visual.authoritative = false;
    visual.presentationOnly = true;
    visual.ownerId = -1;
    visual.persistent = false;
    visual.nativeLifetimeFrames = nativePresentationLifetimeFrames(child);
    log.debug("[MISSILE_FLIGHT] source={} child={} entity={} pos=({}, {}) interval={}",
        source.Missile, childName, id, position.x, position.y,
        cltParam(source, 0, 1));
    return id;
  }

  @Subscribe
  public void onMissileImpact(MissileImpactEvent event) {
    if (event == null || clientContext.files() == null || clientContext.files().Missiles == null) return;
    Missiles.Entry source = clientContext.files().Missiles.get(event.missileId);
    if (source == null) return;

    // An authoritative heal supplies its target overlay explicitly. The
    // row's ProgOverlay alone cannot distinguish Holy Bolt heals from hits.
    // D2Client CltHit10 still supplies its native struck-unit overlay when
    // no authoritative override is present.
    String targetOverlay = event.targetOverlay;
    if ((targetOverlay == null || targetOverlay.isEmpty()) && source.pCltHitFunc == 10) {
      targetOverlay = source.ProgOverlay;
    }
    if (event.targetEntityId >= 0 && targetOverlay != null
        && !targetOverlay.isEmpty() && overlays != null) {
      overlays.set(event.targetEntityId, targetOverlay);
    }

    String hitSound = event.impactSound != null ? event.impactSound : source.HitSound;
    if (hitSound != null && !hitSound.isEmpty() && clientContext.audio() != null) {
      // An empty native HitSound means silence.  Do not substitute a generic
      // impact sound: that changes the observable Missiles.txt behavior.
      playImpactSound(event, hitSound);
    }
    String hitClassSound = hitClassSound(source.HitClass, event.targetEntityId);
    if (hitClassSound != null && clientContext.audio() != null) {
      playImpactSound(event, hitClassSound);
    }

    ReferenceElementalPresentationSystem referenceElemental =
        world.getSystem(ReferenceElementalPresentationSystem.class);
    if (referenceElemental != null && referenceElemental.impact(source, event)) return;

    // SrvHit22 creates the Holy Bolt authoritatively when the delay missile
    // expires.  The native client callback number 26 is only the legacy
    // client-side fallback; consuming it here as well would render a second
    // bolt on both local and network clients.
    if (source.pCltHitFunc == 26 && source.pSrvHitFunc == 22) {
      log.debug("[MISSILE_IMPACT] source={} clientHitFunc=26 action=use_server_bolt",
          source.Missile);
      return;
    }

    // SrvHit26 replaces the Grim Ward start with an authoritative lasting
    // ward. CltHit29 lists that same child, so emitting it here duplicates
    // the ward. Keep SrvHit27's CltHit29 child: it is the client-only stop.
    if (source.pCltHitFunc == 29 && source.pSrvHitFunc == 26) return;

    // SrvHit48 turns Molten Boulder’s emerge controller into the rolling
    // authoritative child. CltHit53 lists that same child, so replaying it on
    // the client would create a second damaging-looking boulder. Keep the
    // separate CltHit52 explosion presentation on the rolling row untouched.
    if (source.pCltHitFunc == 53 && source.pSrvHitFunc == 48) return;

    // Native CltHit2 repeats SrvHit2's ring. Our authoritative children are
    // already replicated (plaguejavcloud.ClientSend=1); rendering an extra
    // generic child here produces a duplicate central cloud/smoke emitter.
    if (usesServerPoisonCloudFanout(source)) return;

    if (source.pCltHitFunc == 19) {
      log.info("[MISSILE_IMPACT_FUNC19] source={} sourceId={} param1={} param2={} "
              + "child1={} child2={} impact=({}, {}) direction=({}, {})",
          source.Missile, event.missileEntityId, clientHit19Param(source, 0, 0),
          clientHit19Param(source, 1, 0), first(source.CltHitSubMissile, 0),
          first(source.CltHitSubMissile, 1), event.x, event.y, event.dx, event.dy);
    }

    // Ice Arrow uses ExplosionMissile as its cold hit flash when it reaches a
    // unit.  Suppress that flash only for wall/range termination, where there
    // is no target; suppressing the whole row also removes the native unit-hit
    // effect and leaves only the separate frozen-corpse shatter animation.
    if (isImpactVisualSuppressed(source, event.targetEntityId)) {
      log.debug("[MISSILE_IMPACT] source={} action=no_client_impact_visual",
          source.Missile);
      return;
    }

    String[] children = source.CltHitSubMissile;
    if (isIceArrow(source)
        && source.ExplosionMissile != null && !source.ExplosionMissile.isEmpty()
        && clientContext.files().Missiles.get(source.ExplosionMissile) != null) {
      children = new String[] {source.ExplosionMissile};
    }
    // Exploding Arrow's native ExplosionMissile is the authoritative client
    // burst (ExpArrowExplode.dcc).  CltHitSubMissile=fireexplosion2 is only a
    // legacy fallback used by data packs that omit ExplosionMissile.
    if ("explodingarrow".equalsIgnoreCase(source.Missile)
        && source.ExplosionMissile != null && !source.ExplosionMissile.isEmpty()
        && clientContext.files().Missiles.get(source.ExplosionMissile) != null) {
      children = new String[] {source.ExplosionMissile};
    }
    if ((children == null || children.length == 0) && event.impactDcc != null) {
      for (Missiles.Entry candidate : clientContext.files().Missiles) {
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
    // D2Client CltHit12 (Immolation Arrow) lays out a sparse circular field:
    // each integer cell gets one randomly selected visual child, subject to
    // cHitPar2's percentage gate.  The authoritative server creates the
    // damage-bearing immolationfire cells separately; these are render-only.
    if (source.pCltHitFunc == 12) {
      processImmolationArrowHit(source, event, children);
      return;
    }
    float baseAngle = MathUtils.atan2(event.dy, event.dx);
    for (String child : children) createVisual(source, child, event, baseAngle);
  }

  private void processImmolationArrowHit(Missiles.Entry source,
      MissileImpactEvent event, String[] children) {
    if (children == null || children.length == 0 || event == null) return;
    java.util.ArrayList<String> validChildren = new java.util.ArrayList<>(children.length);
    for (String child : children) if (child != null && !child.isEmpty()) validChildren.add(child);
    if (validChildren.isEmpty()) return;
    Missile parent = event.missileEntityId >= 0 && mMissile.has(event.missileEntityId)
        ? mMissile.get(event.missileEntityId) : null;
    if (parent == null) parent = new Missile();
    int level = Math.max(1, parent.damageLevel);
    int radius = source.cHitPar != null && source.cHitPar.length > 0 && source.cHitPar[0] > 0
        ? source.cHitPar[0] : 1;
    if ((source.cHitPar == null || source.cHitPar.length == 0 || source.cHitPar[0] <= 0)
        && clientContext.files() != null && clientContext.files().skills != null) {
      com.riiablo.codec.excel.Skills.Entry skill = parent.skillId >= 0
          ? clientContext.files().skills.get(parent.skillId)
          : clientContext.files().skills.get("Immolation Arrow");
      if (skill != null) radius = Math.max(1,
          SkillFormula.evaluate(skill.calc1, skill, level));
    }
    int chance = source.cHitPar != null && source.cHitPar.length > 1
        ? Math.max(0, Math.min(100, source.cHitPar[1])) : 100;
    // The extracted APK renders a successful unit hit as a filled, continuous
    // fire patch and adds an explicit exploding-arrow burst before the patch.
    // Its zone is visual-only; the native server immolationfire missiles still
    // own all damage and synchronization.  Keep wall/range termination on the
    // audited D2Client CltHit12 path, while using the APK-style composition
    // for the player-visible monster-hit path shown in the reference capture.
    boolean apkUnitHit = event.targetEntityId >= 0;
    if (apkUnitHit) {
      Missiles.Entry explosion = clientContext.files().Missiles.get("explodingarrowexp");
      if (explosion != null) {
        createVisual(source, explosion.Missile, event, 0f, 0f, 0f, 0, parent);
      }
      // The APK's single Ws(zone) covers the whole patch.  In the native
      // missile representation one extra cell ring is the closest equivalent
      // without changing the authoritative radius or damage ownership.
      radius = Math.max(1, radius + 1);
      chance = 100;
    }
    int lifetimeBase = nativeImmolationHitLifetime(source, parent, level);
    int centerX = MathUtils.floor(event.x), centerY = MathUtils.floor(event.y);
    for (int x = -radius; x <= radius; x++) {
      for (int y = -radius; y <= radius; y++) {
        if (x * x + y * y > radius * radius) continue;
        int lifetime = lifetimeBase;
        if (lifetimeBase > 0 && parent != null) lifetime += nextClientRandom(parent, 25) - 12;
        int childIndex = nextClientRandom(parent, validChildren.size());
        if (nextClientRandom(parent, 100) >= chance) continue;
        String child = validChildren.get(childIndex);
        createVisual(source, child, event, 0f, centerX + x - event.x, centerY + y - event.y,
            lifetimeBase > 0 ? Math.max(1, lifetime) : 0, parent);
      }
    }
  }

  static int nativeImmolationHitLifetime(Missiles.Entry source, Missile parent, int level) {
    if (source == null) return 0;
    if (source.SHitCalc1 != null && !source.SHitCalc1.isEmpty()
        && parent != null) {
      // SHitCalc1 is the value read by the native CltHit12 caller (row+0x88),
      // with Range+LevRange as its documented fallback.
      try { return Math.max(0, Integer.parseInt(source.SHitCalc1.trim())); }
      catch (NumberFormatException ignored) { /* formula handled by caller data */ }
    }
    return source.Range > 0 ? source.Range + Math.max(0, level - 1) * source.LevRange : 0;
  }

  /** Plays impact audio through the same listener/zone attenuation as monster sounds. */
  private void playImpactSound(MissileImpactEvent event, String sound) {
    if (event == null || sound == null || sound.isEmpty() || clientContext.audio() == null) return;
    if (event.targetEntityId < 0) {
      // Wall/range impacts have no target entity.  Keep them local to the
      // listener instead of turning them into global sounds.
      int player = clientContext.playerId();
      if (player < 0 || !mPosition.has(player)) return;
      com.badlogic.gdx.math.Vector2 listener = mPosition.get(player).position;
      float gain = MonsterAudio.spatialGain(listener.dst2(event.x, event.y), true);
      if (gain <= 0f) return;
      com.riiablo.audio.Audio.Instance instance = clientContext.audio().play(sound, true);
      if (instance != null) instance.setVolume(gain);
      return;
    }
    SpatialAudio.play(world, clientContext, event.targetEntityId, sound, true);
  }

  static boolean isImpactVisualSuppressed(Missiles.Entry source, int targetEntityId) {
    return isIceArrow(source) && targetEntityId < 0;
  }

  static boolean usesServerPoisonCloudFanout(Missiles.Entry source) {
    if (source == null || source.pCltHitFunc != 2 || source.pSrvHitFunc != 2) return false;
    String server = first(source.HitSubMissile, 0);
    String client = first(source.CltHitSubMissile, 0);
    return server != null && !server.isEmpty() && server.equalsIgnoreCase(client);
  }

  private static boolean isIceArrow(Missiles.Entry source) {
    return source != null && "icearrow".equalsIgnoreCase(source.Missile);
  }

  private void createVisual(Missiles.Entry source, String childName,
      MissileImpactEvent event, float angle) {
    createVisual(source, childName, event, angle, 0f, 0f, 0, null);
  }

  private void createVisual(Missiles.Entry source, String childName,
      MissileImpactEvent event, float angle, float offsetX, float offsetY) {
    createVisual(source, childName, event, angle, offsetX, offsetY, 0, null);
  }

  private void createVisual(Missiles.Entry source, String childName,
      MissileImpactEvent event, float angle, float offsetX, float offsetY,
      int lifetimeOverride, Missile parent) {
    if (childName == null || childName.isEmpty()) return;
    Missiles.Entry child = clientContext.files().Missiles.get(childName);
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
    visual.nativeLifetimeFrames = lifetimeOverride > 0
        ? lifetimeOverride : nativePresentationLifetimeFrames(child);
    if (parent != null) {
      visual.skillId = parent.skillId;
      visual.damageLevel = parent.damageLevel;
      // Keep the child's own allocation seed. Copying the same parent seed
      // into every cell makes CltDo5 select an identical animation phase
      // across the field, defeating its native per-missile randomization.
    }
    if ("explodingarrowexp".equalsIgnoreCase(childName)
        && child.TravelSound != null && !child.TravelSound.isEmpty()
        && clientContext.audio() != null) {
      clientContext.audio().play(child.TravelSound, true);
    }
    // ClientEntityFactory.createMissilePresentation() has already finished
    // the DCC load and attached the AnimationWrapper synchronously.  Do not
    // reload it here: removing and recreating the wrapper twice in one tick
    // can leave RenderSystem's Artemis subscription one update behind for a
    // recycled entity id (the impact exists, but never reaches drawEntity()).
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
    if (child == null) return 0;
    // PoisonSmokePuff's Vel=1 does not turn Range=25 into a distance.
    if (child.Vel != 0 && !MissileLoader.isPoisonSmokePuff(child)) return 0;
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

