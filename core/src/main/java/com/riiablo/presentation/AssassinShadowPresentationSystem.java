package com.riiablo.presentation;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.riiablo.codec.COF;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofComponents;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;

/** D2Common ConvertMode: shadowwarrior GfxType=2 / GfxClass=6. */
@All({UnitStates.class, CofReference.class, CofComponents.class, Class.class})
public class AssassinShadowPresentationSystem extends IteratingSystem {
  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<Class> mClass;
  protected CofManager cofs;

  @Override
  protected void process(int entityId) {
    if (mClass.get(entityId).type != Class.Type.MON) return;
    UnitStates states = mUnitStates.get(entityId);
    CofReference reference = mCofReference.get(entityId);
    boolean active = states.stateList != null
        && states.stateList.hasState(StateId.SHADOWWARRIOR);
    if (!active) {
      if (reference.visualType == Class.Type.PLR && "AI".equals(reference.visualToken))
        cofs.clearVisualOverride(entityId);
      return;
    }
    if (reference.visualType == Class.Type.PLR && "AI".equals(reference.visualToken)) return;
    // The k9 monster composite is only a marker. Use a complete, unarmed
    // Assassin base composite even when the owner's equipment snapshot has
    // not arrived. Monster inventories/MonEquip are not replicated yet.
    for (int c = 0; c < COF.Component.NUM_COMPONENTS; c++)
      cofs.setComponent(entityId, c, CofComponents.COMPONENT_NIL);
    for (int c : new int[] {COF.Component.HD, COF.Component.TR, COF.Component.LG,
        COF.Component.RA, COF.Component.LA, COF.Component.S1, COF.Component.S2})
      cofs.setComponent(entityId, c, CofComponents.COMPONENT_LIT);
    cofs.setVisualOverride(entityId, Class.Type.PLR, "AI", Engine.WEAPON_HTH);
  }
}
