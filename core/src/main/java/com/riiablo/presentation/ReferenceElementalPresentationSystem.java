package com.riiablo.presentation;

import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import com.riiablo.engine.server.missile.NativeMoltenBoulder;
import com.riiablo.engine.server.missile.NativePoisonMissileMotion;
import com.riiablo.util.EngineUtils;
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
 * Elemental sprites: audited 1.10f Druid paths/clocks alongside the remaining
 * reference APK fallbacks. Combat stays authoritative.
 * Pixel offsets use the APK's downward screen Y, converted once at the boundary.
 */
@All(Position.class)
public class ReferenceElementalPresentationSystem extends IteratingSystem implements com.riiablo.map.RenderEffectProvider {
  private static final String[] BLAZE_SPRITES = {"blaze", "firemedium", "firesmall"};
  private final java.util.Map<Integer, BlazeTrail> blazeTrails = new java.util.HashMap<>();
  private double presentationTime;

  private static final class BlazeTrail {
    double birthTime;
    double duration = 2.0;
    int visualId = -1;
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
  @Wire(name = "map", failOnNull = false) protected com.riiablo.map.Map map;

  @Override protected void initialize() {
    // Queue short effects before the first cast; their visual clocks must not
    // block the render thread waiting for a cold DCC decoder/texture upload.
    if (clientContext.assets() == null) return;
    for (String name : new String[] {"blaze", "firewall", "firemedium", "firesmall",
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
    java.util.Iterator<java.util.Map.Entry<Integer, BlazeTrail>> it =
        blazeTrails.entrySet().iterator();
    while (it.hasNext()) {
      java.util.Map.Entry<Integer, BlazeTrail> entry = it.next();
      BlazeTrail trail = entry.getValue();
      boolean expired = presentationTime - trail.birthTime >= trail.duration;
      boolean sourceGone = entry.getKey() >= 0
          && !world.getEntityManager().isActive(entry.getKey());
      if (expired || sourceGone) {
        // The normal effect pass also expires this entity.  Delete here as a
        // safety boundary so a skipped presentation tick, a late snapshot, or
        // a missing Position component cannot leave a Blaze sprite forever.
        if (trail.visualId >= 0 && world.getEntityManager().isActive(trail.visualId)) {
          world.delete(trail.visualId);
        }
        it.remove();
      }
    }
  }

  @Override protected void process(int id) {
    if (mEffect.has(id)) {
      updateEffect(id, mEffect.get(id), Math.max(0f, world.delta));
      return;
    }
    if (mUnitStates.has(id)) updateHurricane(id);
    if (!mMissile.has(id)) return;
    Missile missile = mMissile.get(id);
    if (NativeMoltenBoulder.isRolling(missile.missile)) {
      updateBoulderTrail(id, missile);
      return;
    }
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
        float duration = Math.max(1, missile.nativeLifetimeFrames > 0
            ? missile.nativeLifetimeFrames : missile.missile.Range) / 25f;
        ReferenceElementalEffect base = sprite("volcano", at, duration);
        if (base != null) {base.ground = true; nativeSurface(base, missile.nativeFrame / 25f);}
        ReferenceElementalEffect fire = sprite("volcano overlay fire", at, duration);
        if (fire != null) nativeSurface(fire, missile.nativeFrame / 25f);
        clock.volcanoSeed = new D2Seed(missile.presentationSeedKnown ? missile.presentationSeed : missile.rngState, 666);
        clock.volcanoFrame = Math.max(0, missile.nativeFrame - 1);
      } else if ("erruption center".equals(name)) {
        int interval = fissureInterval(missile);
        int age = Math.max(0, missile.nativeFrame - 1);
        clock.next = ((age + interval - 1) / interval * interval) / 25f;
      } else if (missile.skillId == SkillId.ARMAGEDDON && "armageddoncontrol".equals(name)) {
        // CltPrg92 uses the received landing cell. Re-scattering in the
        // client displaced the effect from the authoritative strike.
        for (String layer : new String[] {"armageddontail", "armageddonrock"}) {
          ReferenceElementalEffect effect = falling(layer, at, NativeArmageddonPresentation.FRAMES / 25f,
              NativeArmageddonPresentation.FRAMES * NativeArmageddonPresentation.SLIDE,
              -NativeArmageddonPresentation.FRAMES * NativeArmageddonPresentation.FALL,
              0, 0, Math.max(0, missile.nativeFrame - 1) / 25f, null);
          if (effect != null) {
            effect.nativeArmageddon = true;
            mMissile.get(effect.entityId).ownerId = missile.ownerId;
            sample(effect, effect.age);
            applyAnimation(effect.entityId, effect);
          }
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
    } else if ("firestormmaker".equals(name)) {
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
        String child = index % 3 == 2 ? "blizzard4" : "blizzard1";
        Missiles.Entry row = clientContext.files().Missiles.get(child);
        // D2Client CltDo13 (6FB31B2D..71): Z=max(CltParam1,step),
        // velocity Z=-max(1,CltParam2), lifetime=Z/step native frames.
        int step = row.CltParam != null && row.CltParam.length > 1
            ? Math.max(1, row.CltParam[1]) : 1;
        int height = row.CltParam != null && row.CltParam.length > 0
            ? Math.max(step, row.CltParam[0]) : step;
        int frames = height / step;
        falling(child, landing, frames / 25f, 0, -height, 0,
            -(height - frames * step), Math.max(0, end - clock.next), null);
        clock.next += .1f;
      }
    } else if ("erruption center".equals(name)) {
      // Native CltDo48 tests its emission clock before the movement update.
      int life = missile.nativeLifetimeFrames > 0 ? missile.nativeLifetimeFrames : missile.missile.Range;
      while (clock.next <= clock.age + .00001f && clock.next * 25 < life) {
        Vector2 vent = scatter(at, 80 + 6 * Math.max(0, missile.damageLevel - 1), .7f, clock);
        vent.set(MathUtils.floor(vent.x + .5f), MathUtils.floor(vent.y + .5f));
        Missiles.Entry crackRow = clientContext.files().Missiles.get("erruption crack 1");
        Missiles.Entry smokeRow = clientContext.files().Missiles.get("erruption smoke 1");
        ReferenceElementalEffect crack = sprite("erruption crack 1", vent, crackRow.Range / 25f);
        if (crack != null) {crack.direction = Math.min(3, (int) (random(clock) * 4)); nativeSurface(crack, 0);}
        ReferenceElementalEffect smoke = sprite("erruption smoke 1", vent, smokeRow.Range / 25f);
        if (smoke != null) nativeSurface(smoke, 0);
        clock.next += fissureInterval(missile) / 25f;
      }
    } else if (volcano) {
      int life = missile.nativeLifetimeFrames > 0 ? missile.nativeLifetimeFrames : missile.missile.Range;
      com.riiablo.codec.excel.Skills.Entry skill = clientContext.files().skills.get(SkillId.VOLCANO);
      int radius = volcanoParameter(missile, 1, skill, skill == null ? null : skill.aurarangecalc);
      int period = volcanoParameter(missile, 0, skill, skill == null ? null : skill.calc4);
      int last = Math.min(life, (int) (end * 25 + .0001f));
      while (clock.volcanoFrame < last) {
        int frame = clock.volcanoFrame++;
        if (!NativeVolcanoPresentation.emits(frame, period, missile.missile.CltParam[2], missile.missile.CltParam[3])) continue;
        String nameRock = missile.missile.CltSubMissile[0];
        Missiles.Entry row = clientContext.files().Missiles.get(nameRock);
        NativeVolcanoPresentation.Flight flight = NativeVolcanoPresentation.flight(row, missile.damageLevel,
            MathUtils.floor(at.x + .5f), MathUtils.floor(at.y + .5f), radius, clock.volcanoSeed);
        ReferenceElementalEffect rock = sprite(nameRock, at, flight.lifetime / 25f);
        if (rock == null) continue;
        rock.nativeVolcanoRock = true; rock.nativeVolcanoOwnerId = missile.ownerId;
        rock.nativeVelocityX = flight.velocityX; rock.nativeVelocityY = flight.velocityY;
        rock.nativeVolcanoHeight = missile.missile.CltParam[4];
        applyAnimation(rock.entityId, rock);
      }
    }
    clock.age = end;
  }

  /** CltDo47 -> CltDo6 emits on path-cell crossings, with independent visual RNG. */
  private void updateBoulderTrail(int id, Missile missile) {
    ReferenceElementalEmitter clock = mEmitter.get(id);
    Vector2 at = mPosition.get(id).position;
    if (clock == null) {
      clock = mEmitter.create(id);
      clock.boulderSeed = new D2Seed(
          missile.presentationSeedKnown ? missile.presentationSeed : missile.rngState, 666);
      clock.boulderCellX = MathUtils.floor(at.x + .5f);
      clock.boulderCellY = MathUtils.floor(at.y + .5f);
      clock.boulderTime = Math.max(missile.nativeFrame, missile.clientFrame) / 25.0;
      if (!missile.authoritative) clock.boulderFrame = Math.max(missile.nativeFrame, missile.clientFrame);
    }
    clock.boulderTime = Math.max(missile.nativeFrame / 25.0,
        clock.boulderTime + Math.max(0, world.delta));
    int end = missile.authoritative ? missile.nativeFrame
        : Math.max(missile.nativeFrame, (int) (clock.boulderTime * 25 + .0001));
    if (missile.authoritative) {
      Vector2 velocity = mVelocity.has(id) ? mVelocity.get(id).velocity : Vector2.Zero;
      int vx = NativePoisonMissileMotion.frameStep(velocity.x);
      int vy = NativePoisonMissileMotion.frameStep(velocity.y);
      int ox = NativePoisonMissileMotion.precision(missile.start.x);
      int oy = NativePoisonMissileMotion.precision(missile.start.y);
      for (int frame = clock.boulderFrame; frame < end; frame++) {
        if (frame <= 0) continue;
        int x = ox + vx * frame, y = oy + vy * frame;
        if ((x >> 16) != ((x - vx) >> 16) || (y >> 16) != ((y - vy) >> 16))
          boulderTrailFire(missile, clock, new Vector2(x / 65536f - .5f, y / 65536f - .5f));
      }
    } else {
      int x = MathUtils.floor(at.x + .5f), y = MathUtils.floor(at.y + .5f);
      if (x != clock.boulderCellX || y != clock.boulderCellY) boulderTrailFire(missile, clock, at);
      clock.boulderCellX = x;
      clock.boulderCellY = y;
    }
    clock.boulderFrame = end;
    missile.clientFrame = end;
  }

  private void boulderTrailFire(Missile parent, ReferenceElementalEmitter clock, Vector2 at) {
    // Native remainder 0 selects slot 1, 1 selects slot 2, 2 retains slot 0.
    int choice = Seed.rollLimitedRandomNumber(clock.boulderSeed, 3);
    int slot = (choice + 1) % 3;
    String name = parent.missile.CltSubMissile[slot];
    Missiles.Entry row = clientContext.files().Missiles.get(name);
    if (row != null) boulderFire(name, at, row.Range,
        (int) Seed.rollRandomNumber(clock.boulderSeed));
  }

  private ReferenceElementalEffect boulderFire(String name, Vector2 at, int frames, int seed) {
    ReferenceElementalEffect effect = sprite(name, at, frames / 25f);
    if (effect != null) {
      effect.nativeBoulderFire = true;
      effect.nativeSeed = seed;
      applyAnimation(effect.entityId, effect);
    }
    return effect;
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
      clock.hurricaneTime = 0;
      clock.hurricaneFrame = 0;
      if (clock.hurricaneSeed == null) clock.hurricaneSeed = new D2Seed(id * 1103515245 + 250, 666);
    }
    double end = clock.hurricaneTime + Math.max(0f, world.delta);
    int frame = (int) Math.floor(end * 25 + .000001);
    Position center = mPosition.get(id);
    int centerX = center.hasNativePrecision() ? center.nativePrecisionX() >>> 16 : MathUtils.floor(center.position.x + .5f);
    int centerY = center.hasNativePrecision() ? center.nativePrecisionY() >>> 16 : MathUtils.floor(center.position.y + .5f);
    while (clock.hurricaneFrame < frame) {
      int born = ++clock.hurricaneFrame;
      // CltActive90 calls its constructor three times for each cltcalc1=3.
      // All nine births share a native frame; never spread them over 1/225s.
      for (int child = 0; child < 9; child++) {
        int lane = (int) Seed.rollRandomNumber(clock.hurricaneSeed) & 7;
        int type = Seed.rollLimitedRandomNumber(clock.hurricaneSeed, 3);
        ReferenceElementalEffect effect = hurricaneDebris(centerX, centerY, lane,
            type == 0 ? "hurricanerock" : type == 1 ? "hurricanetree" : "hurricaneswoosh", 0);
        if (effect != null) {
          // Native rolls height after successful allocation only.
          effect.lift = Seed.rollLimitedRandomNumber(clock.hurricaneSeed, 75);
          effect.age = (float) Math.max(0, end - born / 25.0);
          applyAnimation(effect.entityId, effect);
        }
      }
    }
    clock.hurricaneTime = end;
  }

  /** Shared constructor for runtime state emission and observation-driven diagnostic replay. */
  ReferenceElementalEffect hurricaneDebris(int centerX, int centerY, int lane, String name, int height) {
    Missiles.Entry row = clientContext.files().Missiles.get(name);
    if (row == null) return null;
    ReferenceElementalEffect effect = sprite(name, new Vector2(centerX, centerY), row.Range / 25f);
    if (effect != null) {
      effect.orbit = true;
      effect.hurricaneFlight = NativeHurricanePresentation.flight(centerX, centerY, lane, row.Vel);
      effect.lift = height;
      applyAnimation(effect.entityId, effect);
    }
    return effect;
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
      case "moltenboulder": case "moltenboulderfirepath":
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

  static boolean isFireWallNative(Missile m) {
    if (m == null || m.presentationOnly || m.missile == null
        || m.skillId != SkillId.FIRE_WALL) return false;
    String name = m.missile.Missile;
    return "firewall".equalsIgnoreCase(name) || "firewallmaker".equalsIgnoreCase(name);
  }

  private void updateBlazeTrail(int id, Missile missile) {
    // An authoritative segment is only an observation of the owner's trail.
    // Never replace its row, lifetime, position or combat RNG with visual data.
    ReferenceElementalEmitter observed = mEmitter.get(id);
    if (observed != null) return;
    mEmitter.create(id);
    float age = Math.max(0, missile.nativeFrame) / 25f;
    float duration = blazeDurationSeconds(missile);
    if (age >= duration) return; // A late baseline must not reignite expired visuals.
    double birth = presentationTime - age;
    Vector2 at = mPosition.get(id).position;
    Vector2 screen = EngineUtils.worldToScreenCoords(at, new Vector2());
    BlazeTrail trail = blazeTrails.get(missile.ownerId);
    // APK blaze.cast samples every .1s, requiring 14 pixels from the last cell.
    // Quantize that policy to the incoming 25Hz authoritative observations.
    if (trail != null && (birth - trail.birthTime < .1 - .00001
        || screen.dst2(trail.screen) < 14f * 14f)) return;
    int seed = missile.presentationSeedKnown ? missile.presentationSeed : missile.rngState;
    D2Seed visualSeed =
        new D2Seed(seed, 666);
    int variant = Seed.rollLimitedRandomNumber(visualSeed, 3);
    ReferenceElementalEffect effect = sprite(BLAZE_SPRITES[variant], at, duration);
    if (effect == null) return;
    effect.age = age;
    effect.fade = true;
    effect.phase = Seed.rollLimitedRandomNumber(visualSeed, 25);
    applyAnimation(effect.entityId, effect);
    if (trail == null) {
      trail = new BlazeTrail();
      blazeTrails.put(missile.ownerId, trail);
    }
    trail.birthTime = birth;
    trail.duration = duration;
    trail.visualId = effect.entityId;
    trail.screen.set(screen);
  }

  /** Uses the authoritative 25 Hz Blaze lifetime when the server supplied it. */
  static float blazeDurationSeconds(Missile missile) {
    if (missile == null) return 2f;
    return missile.nativeLifetimeFrames > 0
        ? missile.nativeLifetimeFrames / 25f : 2f;
  }

  @Override public boolean ground(int id) {
    ReferenceElementalEffect effect = mEffect.get(id);
    return effect != null && effect.ground;
  }

  @Override public boolean orbit(int id) {
    ReferenceElementalEffect effect = mEffect.get(id);
    return effect != null && effect.orbit;
  }

  @Override public Vector2 screenOffset(int id) {
    ReferenceElementalEffect effect = mEffect.get(id);
    return effect == null ? null : effect.offset;
  }

  @Override public boolean hideOriginal(int id) {
    if (mEffect.has(id) || !mMissile.has(id)) return false;
    Missile m = mMissile.get(id);
    if (isFireWallNative(m)) return true;
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
      boulderImpact(source, event, at);
      return true;
    }
    return false;
  }

  private void boulderImpact(Missiles.Entry source, MissileImpactEvent event, Vector2 impact) {
    // Expiry is dispatched before the normal presentation pass. Consume the
    // final pre-movement callback too, including a crossing on native age 99.
    Missile parent = event.missileEntityId >= 0 ? mMissile.get(event.missileEntityId) : null;
    if (parent != null && parent.missile == source && mPosition.has(event.missileEntityId))
      updateBoulderTrail(event.missileEntityId, parent);
    Vector2 at = new Vector2(MathUtils.floor(impact.x + .5f), MathUtils.floor(impact.y + .5f));
    ReferenceElementalEmitter clock = event.missileEntityId >= 0
        ? mEmitter.get(event.missileEntityId) : null;
    D2Seed seed = clock != null && clock.boulderSeed != null
        ? clock.boulderSeed : new D2Seed(impactFirePhase(event.missileEntityId, at), 666);
    // The null-CelFile explosion row is an invisible 13-tick carrier.
    Missiles.Entry explode = clientContext.files().Missiles.get("moltenboulderexplode");
    sprite("moltenboulderexplode", at, explode.Range / 25f);
    int attempts = Math.max(0, source.cHitPar[0]);
    int radius = attempts * 2;
    int speed = (Math.max(1, source.cHitPar[1]) << 8) * 75 / 100;
    for (int i = 0; i < attempts; i++) {
      int dx = Seed.rollLimitedRandomNumber(seed, radius * 2 + 1) - radius;
      int dy = Seed.rollLimitedRandomNumber(seed, radius * 2 + 1) - radius;
      int distance = NativeMoltenBoulder.distance(dx, dy);
      if (distance <= 3) continue;
      int frames = Math.max(1, (distance << 16) / (16 * speed));
      Vector2 v = NativePoisonMissileMotion.velocity(
          Vector2.Zero, new Vector2(dx, dy), speed, new Vector2());
      ReferenceElementalEffect fragment = sprite("moltenboulder-flyingrocks", at, frames / 25f);
      if (fragment == null) continue;
      fragment.nativeBoulderRock = true;
      // Creation flag 0x100 overrides visual facing with (targetX+targetY)&63;
      // it deliberately leaves the physical direction vector unchanged.
      fragment.direction = NativeMoltenBoulderPresentation.pathDirection(
          Math.round(at.x) + dx + Math.round(at.y) + dy, 8);
      EngineUtils.worldToScreenCoords(new Vector2(v).scl(frames / 25f), fragment.endOffset);
    }
    for (int i = 0; i < NativeMoltenBoulder.FIRE_X.length; i++) {
      Vector2 cell = new Vector2(at).add(
          NativeMoltenBoulder.FIRE_X[i],
          NativeMoltenBoulder.FIRE_Y[i]);
      boulderFire("moltenboulderfirepath", cell,
          clientContext.files().Missiles.get("moltenboulderfirepath").Range,
          (int) Seed.rollRandomNumber(seed));
    }
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

  private int fissureInterval(Missile controller) {
    com.riiablo.codec.excel.Skills.Entry skill = clientContext.files().skills.get(SkillId.FISSURE);
    return skill == null ? 6 : Math.max(1, com.riiablo.engine.server.skill.SkillFormula.evaluate(
        skill.calc2, skill, Math.max(1, controller.damageLevel)));
  }

  private int volcanoParameter(Missile missile, int index, com.riiablo.codec.excel.Skills.Entry skill, String formula) {
    int value = missile.missile.CltParam[index];
    return value > 0 ? value : skill == null ? 1 : Math.max(1,
        com.riiablo.engine.server.skill.SkillFormula.evaluate(formula, skill, Math.max(1, missile.damageLevel)));
  }

  private void volcanoLanding(int id, ReferenceElementalEffect effect) {
    clientHit55Landing(id, effect.nativeVolcanoOwnerId);
  }

  private void clientHit55Landing(int id, int ownerId) {
    if (ownerId < 0 || !world.getAspectSubscriptionManager().get(com.artemis.Aspect.all())
        .getActiveEntityIds().get(ownerId)) return;
    Missiles.Entry source = mMissile.get(id).missile;
    Vector2 at = mPosition.get(id).position;
    Vector2 cell = new Vector2(MathUtils.floor(at.x + .5f), MathUtils.floor(at.y + .5f));
    // CltHit55 attempts each configured child separately; creation failure
    // or subsequent removal must not be replaced with a random probability.
    for (String name : source.CltHitSubMissile) {
      if (name == null || name.isEmpty()) continue;
      Missiles.Entry row = clientContext.files().Missiles.get(name);
      if (row == null) continue;
      ReferenceElementalEffect child = sprite(name, cell, row.Range / 25f);
      if (child != null) nativeSurface(child, 0);
    }
  }

  private void nativeSurface(ReferenceElementalEffect effect, float age) {
    effect.nativeDruidSurface = true;
    effect.age = Math.max(0, age);
    mMissile.get(effect.entityId).nativeFrame = (int) (effect.age * 25 + .0001f);
    applyAnimation(effect.entityId, effect);
  }

  static void configure(String name, ReferenceElementalEffect e) {
    e.ground = "volcano".equals(name) || "erruption crack 1".equals(name)
        || "volcano small fire".equals(name);
    switch (name) {
      case "volcano": e.loopStart = 17; e.loopEnd = 18; break;
      case "volcano overlay fire": e.loopStart = 6; e.loopEnd = 14; break;
      case "meteor": case "meteortail": e.loopStart = 0; e.loopEnd = 11; break;
      case "blizzard1": e.loopStart = 0; e.loopEnd = 5; break;
      case "blizzard4": e.loopStart = 0; e.loopEnd = 7; break;
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
    if (e.age + .00001f >= e.duration) {
      if (e.nativeVolcanoRock) {
        if (volcanoPoseInScene(id, e, Math.round(e.duration * 25))) volcanoLanding(id, e);
      }
      if (e.nativeArmageddon && "armageddonrock".equalsIgnoreCase(mMissile.get(id).missile.Missile)) {
        clientHit55Landing(id, mMissile.get(id).ownerId);
      }
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
    if (mMissile.has(id)) mMissile.get(id).nativeFrame = (int) (e.age * 25 + .0001f);
    applyAnimation(id, e);
  }

  static void sample(ReferenceElementalEffect e, float age) {
    if (e.hurricaneFlight != null) {
      e.offset.set(0, e.lift);
    } else if (e.nativeArmageddon) {
      NativeArmageddonPresentation.offset(Math.max(0, (int) (age * 25 + .0001f)), e.offset);
    } else {
      e.offset.set(e.startOffset).lerp(e.endOffset, MathUtils.clamp(age / e.duration, 0, 1));
    }
  }

  private void applyAnimation(int id, ReferenceElementalEffect e) {
    if (e.hurricaneFlight != null) {
      e.hurricaneFlight.advanceTo(Math.max(0, (int) (e.age * 25 + .0001f)));
      mPosition.get(id).setNativePrecision(e.hurricaneFlight.x, e.hurricaneFlight.y);
      if (map != null && map.getZones() != null && map.getZones().size > 0
          && map.getZone(mPosition.get(id).position) == null) {
        // Native PATH_RecacheRoomIfNeeded clears a missile path when the
        // stepped cell has no room. Do not leave a render-only particle
        // alive after it crosses a known loaded-scene boundary.
        world.delete(id);
        return;
      }
      e.direction = NativeHurricanePresentation.direction(e.hurricaneFlight.direction,
          "hurricaneswoosh".equalsIgnoreCase(mMissile.get(id).missile.Missile) ? 8 : 1);
      e.offset.set(0, e.lift);
    } else if (e.nativeVolcanoRock) {
      int age = Math.max(0, (int) (e.age * 25 + .0001f));
      if (!volcanoPoseInScene(id, e, age)) {
        world.delete(id); return; // Leaving a known scene is removal, not CltHit55.
      }
    } else if (e.nativeBoulderRock) {
      float fraction = MathUtils.clamp(e.age / e.duration, 0, 1);
      mPosition.get(id).invalidateNativePrecision();
      pixelOffset(e.origin, e.endOffset.x * fraction, -e.endOffset.y * fraction,
          mPosition.get(id).position);
      e.offset.setZero();
    }
    if (!mAnimationWrapper.has(id)) return;
    Animation animation = mAnimationWrapper.get(id).animation;
    int frames = animation.getNumFramesPerDir();
    if (frames <= 0) return;
    animation.setMode(Animation.Mode.CLAMP);
    int age = Math.max(0, (int) (e.age * 25 + .0001f));
    animation.setFrame(e.hurricaneFlight != null ? Math.min(frames - 1,
        age * Math.max(0, mMissile.get(id).missile.AnimSpeed) / 16)
        : e.nativeDruidSurface ? NativeDruidSurfacePresentation.frame(
        mMissile.get(id).missile, frames, Math.round(e.duration * 25), age)
        : e.nativeBoulderFire ? NativeFirestormPresentation.groundFrame(
        mMissile.get(id).missile, Math.round(e.duration * 25), e.nativeSeed, frames, age, true)
        : e.nativeVolcanoRock ? NativeVolcanoPresentation.rockFrame(frames,
            mMissile.get(id).missile.AnimLen, Math.round(e.duration * 25), age)
        : e.nativeBoulderRock ? NativeMoltenBoulderPresentation.rockFrame(
            frames, Math.round(e.duration * 25), age) : frameAt(e, frames));
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

  private void volcanoPose(int id, ReferenceElementalEffect effect, int age) {
    int x = (MathUtils.floor(effect.origin.x + .5f) << 16) + 32768;
    int y = (MathUtils.floor(effect.origin.y + .5f) << 16) + 32768;
    mPosition.get(id).setNativePrecision(x + age * effect.nativeVelocityX, y + age * effect.nativeVelocityY);
    effect.offset.set(0, NativeVolcanoPresentation.heightFixed(Math.round(effect.duration * 25),
        effect.nativeVolcanoHeight, age + 1) >> 11);
  }

  private boolean volcanoPoseInScene(int id, ReferenceElementalEffect effect, int age) {
    Vector2 before = new Vector2(mPosition.get(id).position);
    volcanoPose(id, effect, age);
    return map == null || map.getZone(before) == null || map.getZone(mPosition.get(id).position) != null;
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
