package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.Engine;
import com.riiablo.engine.client.component.Selectable;
import com.riiablo.engine.server.component.Object;
import com.riiablo.engine.server.event.ModeChangeEvent;

import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

@All(Selectable.class)
public class SelectableManager extends PassiveSystem {
  protected ComponentMapper<Selectable> mSelectable;
  protected ComponentMapper<Object> mObject;
//  protected ComponentMapper<Monster> mMonster;
//  protected ComponentMapper<Warp> mWarp;

  @Subscribe
  public void onModeChanged(ModeChangeEvent event) {
    int entityId = event.entityId;
    if (mObject.has(entityId)) {
      boolean b = isSelectable(mObject.get(entityId).base, event.mode);
      setSelectable(entityId, b);
      // Note: HANDLED WITHIN ENTITY CONSTRUCTION
//    } else if (mMonster.has(entityId)) {
//      boolean b = mMonster.get(entityId).monstats2.isSel;
//      setSelectable(entityId, b);
//    } else if (mWarp.has(entityId)) {
//      setSelectable(entityId, true);
    }
  }

  static boolean isSelectable(Objects.Entry base, int mode) {
    if (base == null) return false;
    if ((base.SubClass & Engine.Object.SUBCLASS_WAYPOINT)
        == Engine.Object.SUBCLASS_WAYPOINT) {
      // Native waypoint data does not consistently mark every animation mode
      // selectable. An activated waypoint changes to ON, but must remain a
      // mouse target so it can open the travel panel again.
      return true;
    }

    if (base.Selectable != null
        && mode >= 0
        && mode < base.Selectable.length
        && base.Selectable[mode]) return true;

    // Keep converted native doors/chests/shrines targetable when their table
    // omitted mode-specific Selectable flags. The operation mode remains a
    // valid target; the object interactor enforces one-shot chest state.
    return base.Draw
        && base.OperateFn > 0
        && base.OperateFn != 23
        && (mode == Engine.Object.MODE_NU || mode == Engine.Object.MODE_ON);
  }

  public void setSelectable(int id, boolean b) {
    if (b) {
      mSelectable.create(id);
    } else {
      mSelectable.remove(id);
    }
  }
}
