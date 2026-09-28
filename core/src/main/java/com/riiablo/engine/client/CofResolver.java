package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Exclude;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.riiablo.engine.Engine;
import com.riiablo.engine.client.component.CofDescriptor;
import com.riiablo.engine.client.component.CofWrapper;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.event.CofChangeEvent;
import com.riiablo.engine.server.event.ModeChangeEvent;
import com.riiablo.codec.COF;

import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

@All({CofReference.class, Class.class})
@Exclude(CofWrapper.class)
public class CofResolver extends PassiveSystem {
  private static final String TAG = "CofResolver";
  private static final boolean DEBUG        = !true;
  private static final boolean DEBUG_EVENTS = DEBUG && true;

  protected ComponentMapper<Class> mClass;
  protected ComponentMapper<CofWrapper> mCofWrapper;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<CofDescriptor> mCofDescriptor;
  protected ComponentMapper<com.riiablo.engine.server.component.Object> mObject;

  @Subscribe
  public void onCofChanged(CofChangeEvent event) {
    if (DEBUG_EVENTS) Gdx.app.debug(TAG, "onCofChanged");
    if (event instanceof ModeChangeEvent && ((ModeChangeEvent) event).restart) {
      // A forced same-mode event is an animation restart, not a visual COF
      // change. Preserve the resolved COF and its resident DCC layers.
      return;
    }
    mCofWrapper.remove(event.entityId);
    updateCof(event.entityId);
  }

  private void updateCof(int entityId) {
    Class.Type logicalType = mClass.get(entityId).type;
    CofReference reference = mCofReference.get(entityId);
    Class.Type type = reference.effectiveType(logicalType);
    String token = reference.effectiveToken();
    byte mode = reference.effectiveMode(logicalType);
    // Native objects have two replicated mode fields: Object.mode is the
    // persistent gameplay state, while CofReference.mode can briefly retain
    // OP/ON while a room/object snapshot is being applied.  Resolve the COF
    // from the authoritative idle object mode as well; otherwise the loader
    // can keep an ON COF while collision and interaction already say NU.
    // Preserve OP because it is the real one-shot transition animation.
    if (logicalType == Class.Type.OBJ && mode != Engine.Object.MODE_OP
        && mObject.has(entityId)) {
      com.riiablo.engine.server.component.Object object = mObject.get(entityId);
      if (object != null && object.mode >= Engine.Object.MODE_NU
          && object.mode <= Engine.Object.MODE_S5) {
        mode = object.mode;
      }
    }
    byte wclass = reference.effectiveWClass();
    String name = token + type.getMode(mode) + Engine.getWClass(wclass);
    COF cof = null;//type.getCOFs().lookup(name);
    if (cof == null) {
      mCofWrapper.remove(entityId);
      CofDescriptor cofDescriptor = mCofDescriptor.create(entityId);
      cofDescriptor.descriptor = new AssetDescriptor<>(formatCofPath(type, token, name), COF.class);
      if (DEBUG) Gdx.app.debug(TAG, name + "=" + cofDescriptor.descriptor.fileName);
      return;
    }

    if (DEBUG) Gdx.app.debug(TAG, name + "=" + cof);
    mCofWrapper.create(entityId).cof = cof;
  }

  private static String formatCofPath(Class.Type type, String token, String name) {
    return type.PATH + '\\' + token + "\\cof\\" + name + ".cof";
  }
}
