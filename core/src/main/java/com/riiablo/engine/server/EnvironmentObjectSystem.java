package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Object;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.map.EnvironmentCycle;

/** D2Game InitFn 10: toggles the Rogue Encampment bonfire with time of day. */
@All({Object.class, CofReference.class, MapWrapper.class})
public final class EnvironmentObjectSystem extends IteratingSystem {
  static final int ROGUE_ENCAMPMENT = 1;

  protected ComponentMapper<Object> mObject;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Sequence> mSequence;
  protected CofManager cofs;
  @Wire(name = "environment")
  protected EnvironmentCycle environment;

  @Override
  protected void process(int entityId) {
    Object object = mObject.get(entityId);
    MapWrapper mapping = mMapWrapper.get(entityId);
    if (!isRogueBonfire(object, mapping)) return;

    CofReference reference = mCofReference.get(entityId);
    if (environment.isDay()) {
      if (mSequence.has(entityId)) mSequence.remove(entityId);
      if (reference.mode != Engine.Object.MODE_NU) {
        cofs.setMode(entityId, Engine.Object.MODE_NU);
      }
      return;
    }

    if (reference.mode == Engine.Object.MODE_NU && !mSequence.has(entityId)) {
      // D2Game first plays OP and EVENTTYPE_ENDANIM then settles on ON.
      mSequence.create(entityId).sequence(Engine.Object.MODE_OP, Engine.Object.MODE_ON);
    }
  }

  static boolean isRogueBonfire(Object object, MapWrapper mapping) {
    return object != null && object.base != null && object.base.InitFn == 10
        && mapping != null && mapping.zone != null && mapping.zone.level != null
        && mapping.zone.level.Id == ROGUE_ENCAMPMENT;
  }
}
