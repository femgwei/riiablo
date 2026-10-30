package com.riiablo.presentation;

import java.util.BitSet;
import com.riiablo.codec.Animation;
import com.riiablo.codec.COF;

/** Opt-in, bounded evidence for one entity/action. Records body submissions only.
 * Does not claim that submitted pixels survived occlusion or framebuffer clipping.
 * Create a new probe for each action, including consecutive actions of the same mode.
 */
public final class AnimationRenderProbe {
  public final int entityId;
  public final byte mode;
  private final BitSet frames = new BitSet();
  private COF composite;
  private int frameCount;
  private int draws;
  private boolean changedComposite;

  public AnimationRenderProbe(int entityId, byte mode) {
    this.entityId = entityId;
    this.mode = mode;
  }

  public void record(int entity, byte actualMode, COF resolved, Animation animation) {
    if (entity != entityId || actualMode != mode || resolved == null
        || resolved != animation.getCOF() || animation.getSubmittedLayerCount() == 0) return;
    if (composite == null) {
      composite = resolved;
      frameCount = animation.getNumFramesPerDir();
    }
    if (composite != resolved) { changedComposite = true; return; }
    int frame = animation.getFrame();
    if (frame < 0 || frame >= frameCount) return;
    frames.set(frame);
    draws++;
  }

  public boolean complete() {
    return !changedComposite && frameCount > 0 && frames.nextClearBit(0) >= frameCount;
  }

  @Override public String toString() {
    return "entity=" + entityId + " mode=" + mode + " frames=" + frames
        + " expected=" + frameCount + " draws=" + draws
        + " changedComposite=" + changedComposite + " complete=" + complete();
  }
}
