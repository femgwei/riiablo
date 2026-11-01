package com.riiablo.presentation;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.utils.IntMap;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Monster;

/** Hydra ground fire is a client-only overlay, never an additional damage source. */
@All({Monster.class, CofReference.class})
public class HydraPresentationSystem extends IteratingSystem {
  private static final int FIRE_LAYER = Integer.MIN_VALUE + 86;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<CofReference> mCofReference;
  @Wire protected OverlayManager overlays;
  private final IntMap<Integer> phases = new IntMap<>();

  @Override protected void process(int entity) {
    Monster monster = mMonster.get(entity);
    if (monster.monstats == null || !"Hydra".equalsIgnoreCase(monster.monstats.AI)) return;
    int mode = mCofReference.get(entity).mode;
    int phase = mode == Engine.Monster.MODE_DT || mode == Engine.Monster.MODE_DD ? 2
        : mode == Engine.Monster.MODE_S1 ? 1 : 0;
    Integer previous = phases.get(entity);
    if (previous != null && previous == phase) return;
    phases.put(entity, phase);
    if (phase == 2) {
      overlays.clearPersistent(entity, FIRE_LAYER);
    } else {
      // Start/end transition timing belongs to the native client callback;
      // do not overlap guessed one-shots with the verified persistent fire.
      overlays.setPersistent(entity, FIRE_LAYER, "HydraGroundFire");
    }
  }

  @Override protected void removed(int entity) {
    phases.remove(entity);
    overlays.clearPersistent(entity, FIRE_LAYER);
  }
}
