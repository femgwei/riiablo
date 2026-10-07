package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.d2moo.common.drlg.D2ObjectIds;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Object;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.EnvironmentCycle;

/** Mirrors D2Game's native day/night event for the Rogue Encampment bonfire. */
@All({Object.class, CofReference.class, MapWrapper.class})
public final class EnvironmentObjectSystem extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(EnvironmentObjectSystem.class);
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
        log.info("[ENVIRONMENT_BONFIRE] entity={} object={} action=extinguish mode={}",
            entityId, object.base.Id, (int) reference.mode);
        cofs.setMode(entityId, Engine.Object.MODE_NU);
      }
      return;
    }

    if (reference.mode == Engine.Object.MODE_NU && !mSequence.has(entityId)) {
      // D2Game first plays OP and EVENTTYPE_ENDANIM then settles on ON.
      log.info("[ENVIRONMENT_BONFIRE] entity={} object={} action=ignite sequence=OP->ON",
          entityId, object.base.Id);
      mSequence.create(entityId).sequence(Engine.Object.MODE_OP, Engine.Object.MODE_ON);
    }
  }

  static boolean isRogueBonfire(Object object, MapWrapper mapping) {
    return object != null && object.base != null
        && mapping != null && mapping.zone != null && mapping.zone.level != null
        && isRogueBonfire(object.base.Id, mapping.zone.level.Id);
  }

  static boolean isRogueBonfire(int objectId, int levelId) {
    return objectId == D2ObjectIds.OBJECT_ROGUEBONFIRE && levelId == ROGUE_ENCAMPMENT;
  }
}
