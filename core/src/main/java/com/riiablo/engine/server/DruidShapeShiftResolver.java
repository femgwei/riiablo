package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.riiablo.engine.Engine;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/** Restores D2Common's monster-composite presentation for Druid forms. */
@All({UnitStates.class, CofReference.class, Class.class})
public class DruidShapeShiftResolver extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(DruidShapeShiftResolver.class);
  // States.txt wolf/bear GfxType=1 and GfxClass resolve to these MonStats codes.
  static final String WOLF_TOKEN = "40";
  static final String BEAR_TOKEN = "TG";

  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<Class> mClass;
  protected CofManager cofs;

  @Override
  protected void process(int entityId) {
    if (mClass.get(entityId).type != Class.Type.PLR) return;
    UnitStates unitStates = mUnitStates.get(entityId);
    StateList states = unitStates != null ? unitStates.stateList : null;
    String token = states != null && states.hasState(StateId.WOLF) ? WOLF_TOKEN
        : states != null && states.hasState(StateId.BEAR) ? BEAR_TOKEN : null;
    CofReference reference = mCofReference.get(entityId);
    if (token == null) {
      if (reference.visualType != null) {
        cofs.clearVisualOverride(entityId);
        log.info("[DRUID_SHAPE_PRESENTATION] phase=restore entity={} token={}",
            entityId, reference.token);
      }
      return;
    }
    if (reference.visualType != Class.Type.MON
        || !token.equals(reference.visualToken)
        || reference.visualWClass != Engine.WEAPON_HTH) {
      cofs.setVisualOverride(entityId, Class.Type.MON, token, Engine.WEAPON_HTH,
          supportedModes(states.hasState(StateId.WOLF) ? 430 : 431));
      log.info("[DRUID_SHAPE_PRESENTATION] phase=apply entity={} token={} logicalToken={} state={}",
          entityId, token, reference.token,
          states.hasState(StateId.WOLF) ? "wolf" : "bear");
    }
  }

  private static boolean[] supportedModes(int monsterId) {
    if (Riiablo.files == null || Riiablo.files.monstats == null
        || Riiablo.files.monstats2 == null) return null;
    MonStats.Entry monster = Riiablo.files.monstats.get(monsterId);
    if (monster == null || monster.MonStatsEx == null) return null;
    MonStats2.Entry monster2 = Riiablo.files.monstats2.get(monster.MonStatsEx);
    return monster2 != null ? monster2.mMode : null;
  }
}
