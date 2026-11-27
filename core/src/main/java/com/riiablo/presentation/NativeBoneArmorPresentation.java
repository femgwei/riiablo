package com.riiablo.presentation;

import com.badlogic.gdx.utils.IntIntMap;
import com.riiablo.Files;
import com.riiablo.codec.excel.States;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.presentation.component.Overlay;

/** 1.10f setfunc6/remfunc3: staggered ONCE emergence chains become looping pairs. */
final class NativeBoneArmorPresentation {
  private final IntIntMap counts = new IntIntMap();
  private final IntIntMap revisions = new IntIntMap();

  void reconcile(int entity, UnitState state, Files files, OverlayManager manager) {
    States.Entry definition = files == null ? null : files.States.get(StateId.BONEARMOR);
    int count = definition != null && definition.setFunc == 6
        ? NativeCycloneArmorPresentation.count(state) : 0;
    int previous = counts.get(entity, 0);
    if (count > previous || revisions.get(entity, -1) != state.presentationRevision) {
      manager.clearPersistent(entity, StateId.BONEARMOR);
      if (count > 0) manager.setBoneArmorStage(entity, 1, definition.castOverlay);
      // Native mode7 null timers create mode4 emerge->front/back chains.
      // A newly created two-bone shield uses the slower null2 timer; a full
      // shield also installs null1, yielding three independent phase offsets.
      if (count == 3) manager.setBoneArmorStage(entity, 2, definition.overlays[2]);
      if (count >= 2) manager.setBoneArmorStage(entity, count, definition.overlays[3]);
    } else if (count < previous) {
      if (count == 0) manager.clearPersistent(entity, StateId.BONEARMOR);
      else for (int lost = previous - count; lost > 0; lost--)
        manager.removeBoneArmorPair(entity);
    }
    counts.put(entity, count);
    revisions.put(entity, state.presentationRevision);
  }

  void removed(int entity) { counts.remove(entity, 0); revisions.remove(entity, -1); }

  static boolean handles(Overlay overlay) {
    return overlay.stateId == StateId.BONEARMOR && overlay.boneArmorGroup > 0;
  }

  static boolean isLoop(Overlay overlay) {
    return "bonearmor_front".equalsIgnoreCase(overlay.entry.overlay)
        || "bonearmor_back".equalsIgnoreCase(overlay.entry.overlay);
  }

  /** The asset supplies the native length; never retire on entry to its last cel. */
  static boolean advance(Overlay overlay, float delta) {
    if (overlay.nativeClockStarted) overlay.nativeClockTime += Math.max(0, delta);
    else overlay.nativeClockStarted = true;
    overlay.nativeFrame = (int) Math.floor(overlay.nativeClockTime * 25 + .00001);
    long cursor = (long) overlay.nativeFrame * Math.max(0, overlay.entry.AnimRate) * 16;
    int frames = overlay.animation.getNumFramesPerDir();
    if (frames <= 0) return true;
    overlay.animation.setFrame((int) ((cursor >> 8) % frames));
    return isLoop(overlay) || cursor < (long) frames * 256;
  }
}
