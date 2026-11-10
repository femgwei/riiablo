package com.riiablo.presentation;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.MissileImpactEvent;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.graphics.BlendMode;
import com.riiablo.map.DT1.Tile;
import com.riiablo.presentation.component.AnimationWrapper;
import com.riiablo.presentation.component.ReferenceElementalEffect;
import com.riiablo.presentation.component.ReferenceElementalEmitter;

/**
 * Sprite composition recovered from the reference APK's w/xne, Sne, Y4, Tbe,
 * Ebe and Abe helpers. Authoritative missiles still own all combat/lifetimes.
 * Pixel offsets use the APK's downward screen Y, converted once at the boundary.
 */
@All(Position.class)
public class ReferenceElementalPresentationSystem extends IteratingSystem {
  private static final float VOLCANO_DURATION_SECONDS = 5.5f;
  private static final float VOLCANO_FIRST_ROCK_DELAY = .3f;
  private static final float VOLCANO_ROCK_INTERVAL = .2f;
  private static final int[] HURRICANE_X = {7, 5, 0, -5, -7, -5, 0, 5};
  private static final int[] HURRICANE_Y = {0, 5, 7, 5, 0, -5, -7, -5};
  private static final String[] BLAZE_SPRITES = {"blaze", "firemedium", "firesmall"};
  private final java.util.Map<Integer, BlazeTrail> blazeTrails = new java.util.HashMap<>();
  private double presentationTime;

  private static final class BlazeTrail {
    double birthTime;
    final Vector2 screen = new Vector2();
  }
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Missile> mMissile;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<AnimationWrapper> mAnimationWrapper;
  protected ComponentMapper<ReferenceElementalEffect> mEffect;
  protected ComponentMapper<ReferenceElementalEmitter> mEmitter;
  @Wire(name = "factory") protected EntityFactory factory;
  @Wire(name = "clientContext") protected ClientGameplayContext clientContext;

  @Override protected void initialize() {
    // Queue short effects before the first cast; their visual clocks must not
    // block the render thread waiting for a cold DCC decoder/texture upload.
    if (clientContext.assets() == null) return;
    for (String name : new String[] {"blaze", "firemedium", "firesmall",
        "meteorcenter", "meteor", "meteortail", "meteorexplode",
        "blizzard1", "blizzard4", "firestormmaker", "firestorm", "moltenboulderemerge",
        "moltenboulder", "moltenboulderexplode", "moltenboulderfirepath",
        "moltenboulder-flyingrocks",
        "erruption crack 1", "erruption smoke 1", "volcano", "volcano overlay fire",
        "volcano debris 2", "volcano explosion", "volcano small fire", "armageddontail",
        "armageddonrock", "armageddonexplosion", "armageddonfire", "hurricaneswoosh",
        "hurricanerock", "hurricanetree"}) {
      Missiles.Entry row = clientContext.files().Missiles.get(name);
      if (row == null) continue;
      String path = "data\\global\\missiles\\" + row.CelFile + ".dcc";
      if (!clientContext.assets().contains(path)) clientContext.assets().load(
          new com.badlogic.gdx.assets.AssetDescriptor<>(path, com.riiablo.codec.DCC.class));
    }
  }

  @Override protected void begin() {
    presentationTime += Math.max(0f, world.delta);
    blazeTrails.entrySet().removeIf(entry ->
        presentationTime - entry.getValue().birthTime >= 2.0
            || !world.getEntityManager().isActive(entry.getKey()));
  }

  @Override protected void process(int id) {
    if (mEffect.has(id)) {
      updateEffect(id, mEffect.get(id), Math.max(0f, world.delta));
      return;
    }
    if (mUnitStates.has(id)) updateHurricane(id);
    if (!mMissile.has(id)) return;
    Missile missile = mMissile.get(id);
    if (isBlazeTrail(missile)) {
      updateBlazeTrail(id, missile);
      return;
    }
    if (!replaces(missile)) return;
    ReferenceElementalEmitter clock = mEmitter.get(id);
    if (clock == null) {
      clock = mEmitter.create(id);
      clock.seed = missile.presentationSeedKnown ? missile.presentationSeed : missile.rngState;
      clock.age = Math.max(0, missile.nativeFrame - 1) / 25f;
      clock.next = clock.age;
    }
    String name = missile.missile.Missile.toLowerCase(java.util.Locale.ROOT);
    Vector2 at = mPosition.get(id).position;
    boolean volcano = isVolcanoController(missile, name);
    if (!clock.initialized) {
      clock.initialized = true;
      if ("meteorcenter".equals(name)) {
        float duration = Math.max(1, missile.nativeLifetimeFrames > 0
            ? missile.nativeLifetimeFrames : missile.missile.Range) / 25f;
        // Retain the authoritative warning delay, but use Sne's actual 0.6s
        // fall in its final segment instead of stretching the descent to 2.4s.
        clock.next = Math.max(0, duration - .6f);
      } else if (volcano) {
        // Tbe creates both resident layers for the authored S0=5.5 seconds.
        // Do not shorten the volcano to the remaining network carrier life:
        // a late controller snapshot would otherwise show no volcano at all.
        float duration = VOLCANO_DURATION_SECONDS;
        ReferenceElementalEffect base = sprite("volcano", at, duration);
        if (base != null) base.ground = true;
        sprite("volcano overlay fire", at, duration);
        clock.next = clock.age + VOLCANO_FIRST_ROCK_DELAY;
      } else if (missile.skillId == SkillId.ARMAGEDDON) {
        // Ebe: both layers share one landing point and a 0.45s diagonal fall.
        // The native state is attached to the caster and the server emits a
        // fresh strike from the caster's current position every period. Keep
        // that moving center for the visual area; the authoritative missile
        // remains at its server-selected target for combat.
        Vector2 center = armageddonCenter(missile.ownerId, at);
        Vector2 landing = scatter(center, 220f, .68f, clock);
        falling("armageddontail", landing, .45f, 17160f / 127f, -330, 0, 0, 0, null);
        ReferenceElementalEffect rock = falling("armageddonrock", landing, .45f,
            17160f / 127f, -330, 0, 0, 0, "armageddonexplosion");
        if (rock != null) {
          rock.landingFire = random(clock) < .3f;
          rock.landingPhase = (int) (random(clock) * 25);
        }
      }
      if ("firestormmaker".equals(name)) {
        // The reference client releases three trails at -0.2, 0 and +0.2
        // radians. The authoritative server stream ids are intentionally not
        // used for damage here; this is a visual-only lane derived from the
        // immutable presentation seed.
        clock.firestormLaneAngle = (Math.floorMod(clock.seed, 3) - 1) * .2f;
      }
    }
    float end = clock.age + Math.max(0f, world.delta);
    if ("meteorcenter".equals(name) && end + .00001f >= clock.next) {
      float age = Math.max(0, end - clock.next);
      // Sne: target minus (140,400), plus the 44px rock anchor correction.
      falling("meteortail", at, .6f, -140, -356, 0, 44, age, null);
      falling("meteor", at, .6f, -140, -356, 0, 44, age, null);
      clock.next = Float.POSITIVE_INFINITY;
    } else if ("firestormmaker".equals(name) || "moltenboulder".equals(name)) {
      boolean firestorm = "firestormmaker".equals(name);
      while (clock.next <= end + .00001f) {
        Vector2 trailAt = new Vector2(at);
        if (firestorm && mVelocity.has(id)) {
          Vector2 velocity = mVelocity.get(id).velocity;
          if (!velocity.isZero(0.0001f)) {
            float speed = velocity.len();
            Vector2 direction = new Vector2(velocity).nor()
                .rotateRad(clock.firestormLaneAngle);
            Vector2 origin = new Vector2(missile.start);
            if (origin.isZero(0.0001f) && !at.isZero(0.0001f)) {
              origin.set(at).mulAdd(velocity, -clock.age);
            }
            trailAt.set(origin).mulAdd(direction, speed * clock.next);
          }
        } else if (mVelocity.has(id)) {
          trailAt.mulAdd(mVelocity.get(id).velocity, clock.next - end);
        }
        groundFire(firestorm ? "firestorm" : "moltenboulderfirepath", trailAt,
            firestorm ? 1.2f : 2f, clock);
        clock.next += firestorm ? 26f / 320f : 30f / 230f;
      }
    } else if ("blizzardcenter".equals(name)) {
      while (clock.next <= end + .00001f) {
        int index = Math.round(clock.next * 10f);
        Vector2 landing = scatter(at, 110, .75f, clock);
        sprite(index % 3 == 2 ? "blizzard4" : "blizzard1", landing,
            index % 3 == 2 ? 8f / 25f : 6f / 25f);
        clock.next += .1f;
      }
    } else if ("erruption center".equals(name)) {
      while (clock.next <= end + .00001f) {
        Vector2 vent = scatter(at, 80 + 6 * Math.max(0, missile.damageLevel - 1), .7f, clock);
        ReferenceElementalEffect crack = sprite("erruption crack 1", vent, .9f);
        if (crack != null) crack.direction = Math.min(3, (int) (random(clock) * 4));
        sprite("erruption smoke 1", vent, .9f);
        clock.next += .09f;
      }
    } else if (volcano) {
      while (clock.next <= end + .00001f) {
        float angle = random(clock) * MathUtils.PI2;
        float radius = 20 + random(clock) * 110;
        Vector2 landing = pixelOffset(at, MathUtils.cos(angle) * radius,
            -MathUtils.sin(angle) * radius * .7f, new Vector2());
        // Start at the mouth, 50px above the base; land after 0.45s (wbe).
        ReferenceElementalEffect rock = sprite("volcano debris 2", landing, .45f);
        if (rock != null) {
          rock.startOffset.set(-MathUtils.cos(angle) * radius,
              -50 - MathUtils.sin(angle) * radius * .7f);
          rock.landing = "volcano explosion";
          sample(rock, 0);
        }
        clock.next += VOLCANO_ROCK_INTERVAL;
      }
    }
    clock.age = end;
  }

  private void updateHurricane(int id) {
    UnitStates states = mUnitStates.get(id);
    boolean active = states.stateList != null && states.stateList.hasState(StateId.HURRICANE);
    ReferenceElementalEmitter clock = mEmitter.get(id);
    if (!active) {
      if (clock != null) clock.hurricane = false;
      return;
    }
    if (clock == null) clock = mEmitter.create(id);
    if (!clock.hurricane) {
      clock.hurricane = true;
      clock.age = clock.next = 0;
      clock.seed = id * 1103515245 + 250;
    }
    float end = clock.age + Math.max(0f, world.delta);
    while (clock.next < end) {
      int lane = Math.min(7, (int) (random(clock) * 8));
      int type = Math.min(2, (int) (random(clock) * 3));
      float x = (HURRICANE_X[lane] - HURRICANE_Y[lane]) * 16f;
      float y = (HURRICANE_X[lane] + HURRICANE_Y[lane]) * 8f;
      ReferenceElementalEffect effect = sprite(type == 0 ? "hurricaneswoosh"
          : type == 1 ? "hurricanerock" : "hurricanetree",
          mPosition.get(id).position, (type == 0 ? 22 : 20) / 25f);
      if (effect != null) {
        effect.orbit = true;
        effect.radius = (float) Math.hypot(x, y / .5f);
        effect.angle = MathUtils.atan2(y / .5f, x);
        effect.spin = -(type == 0 ? 20 : 18) * 21 / effect.radius;
        effect.lift = random(clock) * 75;
        effect.age = Math.max(0, end - clock.next);
        sample(effect, effect.age);
        applyAnimation(effect.entityId, effect);
      }
      clock.next += 1f / 225f;
    }
    clock.age = end;
  }

  /** Suppress native duplicate children while the replacement system is installed. */
  public boolean ownsPresentation(int id) {
    return mEffect.has(id) || mMissile.has(id) && replaces(mMissile.get(id));
  }

  public static boolean replaces(Missile m) {
    if (m == null || m.missile == null) return false;
    if (isBlazeTrail(m)) return true;
    if (m.skillId == SkillId.VOLCANO || m.skillId == SkillId.HURRICANE
        || m.skillId == SkillId.ARMAGEDDON) return true;
    switch (m.missile.Missile.toLowerCase(java.util.Locale.ROOT)) {
      case "meteorcenter": case "blizzardcenter":
      case "blizzard1": case "blizzard2": case "blizzard3": case "blizzard4":
      case "firestormmaker": case "firestorm": case "moltenboulder": case "moltenboulderfirepath":
      case "moltenboulderexplode":
      case "erruption center": case "erruption crack 1": case "erruption smoke 1":
      case "volcano": case "volcano debris 2": return true;
      default: return false;
    }
  }

  static boolean isBlazeTrail(Missile m) {
    return m != null && !m.presentationOnly && m.missile != null
        && m.skillId == SkillId.BLAZE && "blaze".equalsIgnoreCase(m.missile.Missile);
  }

  private void updateBlazeTrail(int id, Missile missile) {
    // An authoritative segment is only an observation of the owner's trail.
    // Never replace its row, lifetime, position or combat RNG with visual data.
    ReferenceElementalEmitter observed = mEmitter.get(id);
    if (observed != null) return;
    mEmitter.create(id);
    float age = Math.max(0, missile.nativeFrame) / 25f;
    if (age >= 2f) return; // A late baseline must not reignite expired visuals.
    double birth = presentationTime - age;
    Vector2 at = mPosition.get(id).position;
    Vector2 screen = com.riiablo.util.EngineUtils.worldToScreenCoords(at, new Vector2());
    BlazeTrail trail = blazeTrails.get(missile.ownerId);
    // APK blaze.cast samples every .1s, requiring 14 pixels from the last cell.
    // Quantize that policy to the incoming 25Hz authoritative observations.
    if (trail != null && (birth - trail.birthTime < .1 - .00001
        || screen.dst2(trail.screen) < 14f * 14f)) return;
    int seed = missile.presentationSeedKnown ? missile.presentationSeed : missile.rngState;
    com.d2moo.common.drlg.D2Seed visualSeed =
        new com.d2moo.common.drlg.D2Seed(seed, 666);
    int variant = com.d2moo.common.seed.Seed.rollLimitedRandomNumber(visualSeed, 3);
    ReferenceElementalEffect effect = sprite(BLAZE_SPRITES[variant], at, 2f);
    if (effect == null) return;
    effect.age = age;
    effect.fade = true;
    effect.phase = com.d2moo.common.seed.Seed.rollLimitedRandomNumber(visualSeed, 25);
    applyAnimation(effect.entityId, effect);
    if (trail == null) {
      trail = new BlazeTrail();
      blazeTrails.put(missile.ownerId, trail);
    }
    trail.birthTime = birth;
    trail.screen.set(screen);
  }

  public boolean hideOriginal(int id) {
    if (mEffect.has(id) || !mMissile.has(id)) return false;
    Missile m = mMissile.get(id);
    String name = m.missile.Missile;
    return replaces(m) && !"meteorcenter".equalsIgnoreCase(name)
        && !"firestormmaker".equalsIgnoreCase(name) && !"moltenboulder".equalsIgnoreCase(name);
  }

  public boolean impact(Missiles.Entry source, MissileImpactEvent event) {
    String name = source.Missile.toLowerCase(java.util.Locale.ROOT);
    Vector2 at = new Vector2(event.x, event.y);
    if ("meteorcenter".equals(name)) {
      sprite("meteorexplode", at, 16f / 25f);
      return true;
    }
    if (name.matches("blizzard[1-4]") || "volcano debris 2".equals(name)) return true;
    if ("moltenboulder".equals(name)) {
      // CltHit52 draws the rolling boulder's explicit explosion before the
      // eight fragments.  The server event is presentation-only; damage and
      // collision remain owned by the authoritative missile.
      sprite("moltenboulderexplode", at, 16f / 25f);
      for (int i = 0; i < 8; i++) {
        ReferenceElementalEffect fragment = sprite("moltenboulder-flyingrocks", at, .5f);
        if (fragment == null) continue;
        float angle = i * MathUtils.PI2 / 8;
        fragment.direction = i;
        fragment.endOffset.set(MathUtils.cos(angle) * 70, -MathUtils.sin(angle) * 45);
      }
      // The reference client's final Ws(moltenboulderfirepath) cell remains
      // on the ground after the boulder expires.  The native server may have
      // already removed its damage child by the time this event is rendered,
      // so retain the visual cell independently for the authored two seconds.
      ReferenceElementalEffect fire = sprite("moltenboulderfirepath", at, 2f);
      if (fire != null) {
        fire.fade = true;
        fire.phase = impactFirePhase(event.missileEntityId, at);
      }
      return true;
    }
    return false;
  }

  private void groundFire(String name, Vector2 at, float duration, ReferenceElementalEmitter clock) {
    ReferenceElementalEffect effect = sprite(name, at, duration);
    if (effect != null) {
      effect.fade = true;
      effect.phase = groundFirePhase(name, clock);
    }
  }

  private static int groundFirePhase(String name, ReferenceElementalEmitter clock) {
    int phase = (int) (random(clock) * 25);
    if ("moltenboulderfirepath".equals(name)) {
      // Ws samples a fresh phase for every cell.  Keep the deterministic
      // visual RNG, while preventing adjacent boulder cells from repeatedly
      // landing on the same loop frame in a sparse network snapshot.
      if (phase == clock.lastBoulderFirePhase) phase = (phase + 7) % 25;
      clock.lastBoulderFirePhase = phase;
    }
    return phase;
  }

  private static int impactFirePhase(int entityId, Vector2 at) {
    int seed = entityId * 0x9E3779B9;
    seed ^= Float.floatToIntBits(at.x) * 31;
    seed ^= Float.floatToIntBits(at.y) * 17;
    seed ^= seed >>> 16;
    return Math.floorMod(seed, 25);
  }

  private ReferenceElementalEffect falling(String name, Vector2 at, float duration, float x, float y,
      float endX, float endY, float age, String landing) {
    ReferenceElementalEffect effect = sprite(name, at, duration);
    if (effect == null) return null;
    effect.startOffset.set(x, -y);
    effect.endOffset.set(endX, -endY);
    effect.age = age;
    effect.landing = landing;
    sample(effect, age);
    applyAnimation(effect.entityId, effect);
    return effect;
  }

  private ReferenceElementalEffect sprite(String name, Vector2 at, float duration) {
    Missiles.Entry row = clientContext.files().Missiles.get(name);
    if (row == null || factory == null || duration <= 0) return null;
    int id = factory instanceof ClientEntityFactory
        ? ((ClientEntityFactory) factory).createMissilePresentation(row, Vector2.X, new Vector2(at))
        : factory.createMissile(row, Vector2.X, new Vector2(at), -1);
    if (id < 0 || !mMissile.has(id)) return null;
    Missile m = mMissile.get(id);
    m.authoritative = false;
    m.presentationOnly = true;
    m.ownerId = -1;
    m.persistent = false;
    m.range = 0;
    m.nativeLifetimeFrames = Math.max(1, Math.round(duration * 25));
    if (mVelocity.has(id)) mVelocity.get(id).velocity.setZero();
    ReferenceElementalEffect effect = mEffect.create(id);
    effect.entityId = id;
    effect.duration = duration;
    effect.origin.set(at);
    configure(name, effect);
    applyAnimation(id, effect);
    return effect;
  }

  static void configure(String name, ReferenceElementalEffect e) {
    e.ground = "volcano".equals(name) || "erruption crack 1".equals(name)
        || "volcano small fire".equals(name);
    switch (name) {
      case "volcano": e.loopStart = 17; e.loopEnd = 18; break;
      case "volcano overlay fire": e.loopStart = 6; e.loopEnd = 14; break;
      case "meteor": case "meteortail": e.loopStart = 0; e.loopEnd = 11; break;
      case "armageddonrock": case "armageddontail":
      case "moltenboulder-flyingrocks": e.loopStart = 0; e.loopEnd = 15; break;
      case "volcano debris 2": e.loopStart = 0; e.loopEnd = 14; break;
      case "blaze": case "firemedium": case "firesmall":
      case "firestorm": case "moltenboulderfirepath": case "armageddonfire":
        e.loopStart = 12; e.loopEnd = 36; break;
      case "hurricaneswoosh": e.fps = 12.5f; break;
      default: break;
    }
  }

  private void updateEffect(int id, ReferenceElementalEffect e, float delta) {
    e.age += delta;
    if (e.age >= e.duration) {
      if (e.landing != null) {
        sprite(e.landing, e.origin, "volcano explosion".equals(e.landing) ? .72f : .64f);
        if ("volcano explosion".equals(e.landing)) sprite("volcano small fire", e.origin, .72f);
        if (e.landingFire) {
          ReferenceElementalEffect fire = sprite("armageddonfire", e.origin, 1.5f);
          if (fire != null) {
            fire.fade = true;
            fire.phase = e.landingPhase;
          }
        }
      }
      world.delete(id);
      return;
    }
    sample(e, e.age);
    if (mMissile.has(id)) mMissile.get(id).nativeFrame = (int) (e.age * 25);
    applyAnimation(id, e);
  }

  static void sample(ReferenceElementalEffect e, float age) {
    if (e.orbit) {
      float angle = e.angle + e.spin * age;
      e.offset.set(MathUtils.cos(angle) * e.radius,
          -MathUtils.sin(angle) * e.radius * .5f + e.lift);
      // Convert screen tangent into the host world axes before DCC selection.
      Vector2 tangent = pixelOffset(Vector2.Zero, -MathUtils.sin(angle) * e.spin,
          MathUtils.cos(angle) * .5f * e.spin, new Vector2());
      e.direction = com.riiablo.engine.Direction.radiansToDirection(tangent.angleRad(), 8);
    } else {
      e.offset.set(e.startOffset).lerp(e.endOffset, MathUtils.clamp(age / e.duration, 0, 1));
    }
  }

  private void applyAnimation(int id, ReferenceElementalEffect e) {
    if (e.orbit) {
      float angle = e.angle + e.spin * e.age;
      mPosition.get(id).invalidateNativePrecision();
      pixelOffset(e.origin, MathUtils.cos(angle) * e.radius,
          MathUtils.sin(angle) * e.radius * .5f, mPosition.get(id).position);
      // Sorting/culling follows the ground orbit, while lift affects only drawing.
      e.offset.set(0, e.lift);
    }
    if (!mAnimationWrapper.has(id)) return;
    Animation animation = mAnimationWrapper.get(id).animation;
    int frames = animation.getNumFramesPerDir();
    if (frames <= 0) return;
    animation.setMode(Animation.Mode.CLAMP);
    animation.setFrame(frameAt(e, frames));
    animation.setDirection(Math.floorMod(e.direction, Math.max(1, animation.getNumDirections())));
    if (animation.getLayer(0) != null) {
      String name = mMissile.get(id).missile.Missile;
      boolean opaque = "meteor".equals(name) || "armageddonrock".equals(name)
          || "volcano".equals(name) || "volcano small fire".equals(name)
          || "erruption crack 1".equals(name) || "hurricanerock".equals(name)
          || "hurricanetree".equals(name) || "moltenboulder-flyingrocks".equals(name);
      animation.getLayer(0).setBlendMode(opaque ? BlendMode.ID : BlendMode.ADDITIVE);
      animation.getLayer(0).setAlpha(e.fade ? MathUtils.clamp((e.duration - e.age) / .4f, 0, 1) : 1);
    }
  }

  /** Publish the proper pose/blend on the same frame an asynchronous DCC arrives. */
  public void refreshLoadedEffect(int id) {
    ReferenceElementalEffect effect = mEffect.get(id);
    if (effect != null) applyAnimation(id, effect);
  }

  static int frameAt(ReferenceElementalEffect e, int frames) {
    int frame = Math.max(0, (int) (e.age * e.fps));
    if (e.loopStart >= 0 && frame >= e.loopStart) {
      frame = e.loopStart + Math.floorMod(frame - e.loopStart + e.phase,
          e.loopEnd - e.loopStart + 1);
    }
    return Math.min(frames - 1, frame);
  }

  static Vector2 pixelOffset(Vector2 at, float dx, float downY, Vector2 out) {
    float x = dx / Tile.SUBTILE_WIDTH50, y = downY / Tile.SUBTILE_HEIGHT50;
    return out.set(at.x + (x + y) / 2, at.y + (y - x) / 2);
  }

  private Vector2 scatter(Vector2 at, float radius, float flatten, ReferenceElementalEmitter clock) {
    float r = (float) Math.sqrt(random(clock)) * radius;
    float angle = random(clock) * MathUtils.PI2;
    return pixelOffset(at, MathUtils.cos(angle) * r, MathUtils.sin(angle) * r * flatten, new Vector2());
  }

  private Vector2 armageddonCenter(int ownerId, Vector2 fallback) {
    return ownerId >= 0 && mPosition.has(ownerId)
        ? mPosition.get(ownerId).position : fallback;
  }

  private static boolean isVolcanoController(Missile missile, String name) {
    // Volcano eruption children inherit skillId=VOLCANO for damage, so the
    // skill id alone must not make every child spawn another resident volcano.
    return "volcano".equals(name)
        || missile.skillId == SkillId.VOLCANO && missile.missile.pSrvDoFunc == 123;
  }

  private static float random(ReferenceElementalEmitter clock) {
    clock.seed = clock.seed * 1664525 + 1013904223;
    return (clock.seed >>> 8) / 16777216f;
  }

  private static float remaining(Missile m) {
    return Math.max(1, (m.nativeLifetimeFrames > 0 ? m.nativeLifetimeFrames : m.missile.Range)
        - m.nativeFrame) / 25f;
  }
}
