package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.utils.IntMap;

import com.riiablo.Riiablo;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.MonSounds;
import com.riiablo.engine.Engine;
import com.riiablo.engine.client.component.AnimationWrapper;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;

/**
 * Reproduces the client-side part of the native monster sound bank.
 *
 * <p>MonStats.MonSound is only a {@code MonSounds.txt} id.  The original game
 * uses that row for footsteps on the WL animation and for occasional Neutral
 * vocalizations while idle or walking.  Previously only player footsteps were
 * emitted by Riiablo, leaving moving monsters completely silent.</p>
 */
@All({Monster.class, AnimationWrapper.class, Velocity.class, CofReference.class, Position.class})
public class MonsterSoundEmitter extends IteratingSystem {
  private static final float FRAMES_PER_SECOND = Animation.FRAMES_PER_SECOND;
  private static final float DEFAULT_NEUTRAL_DELAY = 2f;
  /** Matches the positional falloff radius used by {@link SoundEmitterHandler}. */
  static final float AUDIBLE_RADIUS = 20f;
  static final float AUDIBLE_RADIUS2 = AUDIBLE_RADIUS * AUDIBLE_RADIUS;

  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<AnimationWrapper> mAnimationWrapper;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<MapWrapper> mMapWrapper;

  private final IntMap<State> states = new IntMap<>();

  @Override
  protected void process(int entityId) {
    Monster monster = mMonster.get(entityId);
    if (monster == null || monster.monstats == null || Riiablo.files == null
        || Riiablo.audio == null || !isAudible(entityId)) return;

    MonSounds.Entry bank = soundBank(monster);
    if (bank == null) return;

    Animation animation = mAnimationWrapper.get(entityId).animation;
    State state = states.get(entityId);
    if (state == null) {
      states.put(entityId, state = new State());
      // MonSounds.Init is emitted once when the monster enters the client,
      // just as D2 emits the bank's initialization vocalization on spawn.
      play(bank.Init);
    }

    int frame = animation.getFrame();
    int frameCount = Math.max(1, animation.getNumFramesPerDir());
    byte mode = mCofReference.get(entityId).mode;
    boolean moving = !mVelocity.get(entityId).velocity.isZero()
        && (mode == Engine.Monster.MODE_WL || mode == Engine.Monster.MODE_RN);
    boolean vocalMode = mode == Engine.Monster.MODE_NU || moving;

    if (!state.tauntPlayed && moving) {
      // Taunt is a one-shot encounter vocalization.  The authoritative AI
      // decides when pursuit begins; the first visible transition to WL/RN is
      // the client-side equivalent and avoids repeating it every repath.
      play(bank.Taunt);
      state.tauntPlayed = true;
    }

    if (state.mode != mode) {
      state.mode = mode;
      state.previousFrame = frame;
      // Do not make every monster vocal on the same tick after a mode change.
      if (state.neutralRemaining <= 0f) state.neutralRemaining = seededDelay(bank);
    }

    if (moving) playFootsteps(bank, state, frame, frameCount);

    if (vocalMode && hasSound(bank.Neutral)) {
      state.neutralRemaining -= com.riiablo.engine.SimulationClock.STEP_SECONDS;
      if (state.neutralRemaining <= 0f) {
        play(bank.Neutral);
        state.neutralRemaining = neutralDelay(bank);
      }
    } else if (!vocalMode) {
      state.neutralRemaining = Math.min(state.neutralRemaining,
          neutralDelay(bank));
    }
    state.previousFrame = frame;
  }

  private void playFootsteps(MonSounds.Entry bank, State state, int frame, int frameCount) {
    if (!hasSound(bank.Footstep) && !hasSound(bank.FootstepLayer)) return;
    int count = Math.max(1, bank.FsCnt);
    int interval = Math.max(1, frameCount / count);
    int offset = Math.max(0, bank.FsOff) % frameCount;
    if (!crossed(state.previousFrame, frame, offset, frameCount, interval)) return;
    int probability = bank.FsPrb <= 0 ? 100 : Math.min(100, bank.FsPrb);
    if (com.badlogic.gdx.math.MathUtils.random(99) >= probability) return;
    play(bank.Footstep);
    if (hasSound(bank.FootstepLayer)) play(bank.FootstepLayer);
  }

  private static boolean crossed(int previous, int current, int offset,
      int frameCount, int interval) {
    if (previous < 0) return current == offset;
    int p = previous;
    int c = current;
    if (c < p) c += frameCount;
    for (int frame = p + 1; frame <= c; frame++) {
      int normalized = frame % frameCount;
      if (normalized >= offset && (normalized - offset) % interval == 0) return true;
    }
    return false;
  }

  private MonSounds.Entry soundBank(Monster monster) {
    String id = monster.monstats.MonSound;
    // UMonSound is the native unique/super-unique override.  Normal monsters
    // retain MonSound, while ranked monsters use the override when present.
    if (monster.rank > 0 && hasSound(monster.monstats.UMonSound)) {
      id = monster.monstats.UMonSound;
    }
    if (!hasSound(id)) return null;
    return Riiablo.files.MonSounds.get(id);
  }

  private boolean isAudible(int entityId) {
    if (Riiablo.game == null || Riiablo.game.player < 0
        || !mPosition.has(Riiablo.game.player)) return false;
    int listenerId = Riiablo.game.player;
    if (mMapWrapper.has(listenerId) && mMapWrapper.has(entityId)) {
      MapWrapper listener = mMapWrapper.get(listenerId);
      MapWrapper emitter = mMapWrapper.get(entityId);
      // Town and its preloaded outdoor neighbours share one client world but
      // are separate native levels.  Never leak a monster bank across that
      // boundary, even when their generated coordinates happen to be close.
      if (listener != null && emitter != null && listener.zone != null
          && emitter.zone != null && listener.zone != emitter.zone) return false;
    }
    float distance2 = mPosition.get(listenerId).position.dst2(
        mPosition.get(entityId).position);
    return isAudible(distance2, true);
  }

  static boolean isAudible(float distance2, boolean sameZone) {
    return sameZone && distance2 >= 0f && distance2 <= AUDIBLE_RADIUS2;
  }

  private static float neutralDelay(MonSounds.Entry bank) {
    return bank.NeuTime > 0 ? bank.NeuTime / FRAMES_PER_SECOND : DEFAULT_NEUTRAL_DELAY;
  }

  private static float seededDelay(MonSounds.Entry bank) {
    float delay = neutralDelay(bank);
    // A small deterministic phase spread prevents a full pack from vocalizing
    // on the same tick while keeping tests and replays reproducible.
    return delay * (0.5f + com.badlogic.gdx.math.MathUtils.random(0.5f));
  }

  private static boolean hasSound(String sound) {
    return sound != null && !sound.isEmpty() && !"none".equalsIgnoreCase(sound);
  }

  private static void play(String sound) {
    if (hasSound(sound)) Riiablo.audio.play(sound, true);
  }

  private static final class State {
    private int previousFrame = -1;
    private byte mode = -1;
    private float neutralRemaining;
    private boolean tauntPlayed;
  }
}
