package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.riiablo.Riiablo;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.animation.AnimationTraceWriter;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.skill.SkillId;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Optional headless exporter for the canonical cross-runtime animation trace. */
@All({AnimData.class, CofReference.class, Class.class, Position.class})
public final class AnimationTraceSystem extends IteratingSystem implements AutoCloseable {
  /** Player SQ is a native sequence mode and is not present in Engine.Player.MODE. */
  private static final byte NATIVE_SEQUENCE_MODE = 18;
  private final AnimationTraceWriter writer;
  private long tick;
  private int gameFrame;
  /** Canonical SQ tail retained for three frames after native WW cleanup. */
  private int whirlwindTailFrames;
  private int focusEntity = Engine.INVALID_ENTITY;

  protected ComponentMapper<AnimData> mAnimData;
  protected ComponentMapper<Casting> mCasting;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<Class> mClass;
  protected ComponentMapper<Position> mPosition;

  public AnimationTraceSystem(AnimationTraceWriter writer) {
    if (writer == null) throw new IllegalArgumentException("writer");
    this.writer = writer;
  }

  /** Restrict output to one skill caster; the default records all animated units. */
  public AnimationTraceSystem focus(int entityId) {
    focusEntity = entityId;
    return this;
  }

  /** Supplies the authoritative game frame when the caller has one. */
  public AnimationTraceSystem gameFrame(int gameFrame) {
    this.gameFrame = gameFrame;
    return this;
  }

  @Override protected void process(int entityId) {
    if (focusEntity != Engine.INVALID_ENTITY && focusEntity != entityId) return;
    AnimData anim = mAnimData.get(entityId);
    CofReference cof = mCofReference.get(entityId);
    Class type = mClass.get(entityId);
    byte modeId = cof.effectiveMode(type.type);
    String mode = modeId >= 0 && modeId < type.type.MODE.length ? type.type.MODE[modeId] : null;
    // Skills.txt encodes Whirlwind and other native sequence skills as SQ,
    // while the presentation COF points at the transition animation.  Keep
    // the canonical trace aligned with the retail logical mode when the
    // authoritative cast is still active.
    Casting casting = mCasting.get(entityId);
    boolean whirlwindActive = false;
    if (type.type == Class.Type.PLR && casting != null
        && Riiablo.files != null && Riiablo.files.skills != null) {
      com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(casting.skillId);
      if (skill != null && skill.Id == SkillId.WHIRLWIND
          && "SQ".equalsIgnoreCase(skill.anim)) {
        modeId = NATIVE_SEQUENCE_MODE;
        mode = "SQ";
        whirlwindActive = true;
      }
    }
    // D2Client keeps the completed Whirlwind sequence visible for the three
    // samples after D2Game retires the cast, then returns to neutral.  The
    // authoritative runtime has already removed Casting by that point, so
    // retain the canonical presentation window here for trace comparison.
    if (whirlwindActive) {
      whirlwindTailFrames = 3;
    } else if (type.type == Class.Type.PLR && casting == null
        && whirlwindTailFrames > 0) {
      modeId = NATIVE_SEQUENCE_MODE;
      mode = "SQ";
      whirlwindTailFrames--;
    }
    List<String> keyframes = new ArrayList<>();
    int frameIndex = anim.frame >>> 8;
    if (anim.keyframes != null && frameIndex >= 0 && frameIndex < anim.keyframes.length) {
      byte keyframe = anim.keyframes[frameIndex];
      if (keyframe > Engine.KEYFRAME_NIL) keyframes.add(Engine.getKeyframe(keyframe));
    }
    try {
      writer.frame(tick++, gameFrame, modeId, mode, cof.effectiveToken(),
          Engine.getWClass(cof.effectiveWClass()), anim.frame, anim.numFrames >>> 8,
          anim.speed, keyframes,
          Math.round(mPosition.get(entityId).position.x * 65536f),
          Math.round(mPosition.get(entityId).position.y * 65536f),
          null, null);
    } catch (IOException error) {
      throw new IllegalStateException("Unable to write animation trace", error);
    }
  }

  @Override public void close() throws IOException {
    writer.close();
  }
}
