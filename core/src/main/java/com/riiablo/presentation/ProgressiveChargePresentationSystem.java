package com.riiablo.presentation;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.utils.IntMap;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.codec.excel.States;

/** Renders the native Assassin charge-up state as one to three aura layers. */
@All(UnitStates.class)
public class ProgressiveChargePresentationSystem extends IteratingSystem {
  private static final int[] STATES = {StateId.PROGRESSIVE_DAMAGE, StateId.PROGRESSIVE_STEAL,
      StateId.PROGRESSIVE_OTHER, StateId.PROGRESSIVE_FIRE, StateId.PROGRESSIVE_COLD,
      StateId.PROGRESSIVE_LIGHTNING};
  private static final int SLOT_BASE = 0x5000;
  protected ComponentMapper<UnitStates> mUnitStates;
  @Wire protected OverlayManager overlays;
  @Wire(name = "clientContext") protected ClientGameplayContext clientContext;
  private final IntMap<Integer> installed = new IntMap<>();

  @Override protected void process(int entity) {
    UnitStates unit = mUnitStates.get(entity);
    if (unit == null || unit.stateList == null) return;
    int count = 0;
    for (int stateId : STATES) {
      UnitState state = unit.stateList.getState(stateId);
      int charges = state == null || state.expired ? 0
          : Math.max(0, Math.min(3, state.velocityModifier));
      String overlay = overlayFor(stateId);
      for (int slot = 0; slot < 3; slot++) {
        int key = key(stateId, slot);
        if (slot < charges && overlay != null) {
          overlays.setPersistent(entity, key, overlay);
          count++;
        } else {
          overlays.clearPersistent(entity, key);
        }
      }
    }
    installed.put(entity, count);
  }

  private String overlayFor(int stateId) {
    if (clientContext == null || clientContext.files() == null
        || clientContext.files().States == null) return null;
    States.Entry row = clientContext.files().States.get(stateId);
    if (row == null || row.overlays == null || row.overlays.length == 0) return null;
    for (String name : row.overlays) if (name != null && !name.isEmpty()) return name;
    return null;
  }

  private static int key(int state, int slot) { return SLOT_BASE + state * 4 + slot; }

  @Override protected void removed(int entity) {
    for (int state : STATES) for (int slot = 0; slot < 3; slot++)
      overlays.clearPersistent(entity, key(state, slot));
    installed.remove(entity);
  }
}
