package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.badlogic.gdx.Gdx;
import com.riiablo.Riiablo;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.event.CofChangeEvent;
import com.riiablo.codec.D2;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

@All({CofReference.class, Class.class})
public class AnimDataResolver extends PassiveSystem {
  private static final String TAG = "AnimDataResolver";
  private static final Logger log = LogManager.getLogger(AnimDataResolver.class);
  private static final boolean DEBUG        = !true;
  private static final boolean DEBUG_EVENTS = DEBUG && true;

  protected ComponentMapper<Class> mClass;
  protected ComponentMapper<AnimData> mAnimData;
  protected ComponentMapper<CofReference> mCofReference;

  @Subscribe
  public void onCofChanged(CofChangeEvent event) {
    if (DEBUG_EVENTS) Gdx.app.debug(TAG, "onCofChanged");
    updateAnimData(event.entityId);
  }

  /**
   * Resolve AnimData from D2 table. COF lookup key = token + mode + wclass
   * (e.g. "fallen1" + "DD" + "HTH" -> "fallen1DDHTH"). If not found, entry is null
   * and keyframes stay null (default); see log.warn for failed lookups.
   */
  private void updateAnimData(int entityId) {
    Class.Type logicalType = mClass.get(entityId).type;
    CofReference c = mCofReference.get(entityId);
    Class.Type t = c.effectiveType(logicalType);
    byte mode = c.effectiveMode(logicalType);
    String token = c.effectiveToken();
    byte wclass = c.effectiveWClass();
    String modeStr = t.MODE[mode];
    String wclassStr = Engine.getWClass(wclass);
    String cof = token + modeStr + wclassStr;
    D2.Entry entry = Riiablo.anim.getEntry(cof);
    if (DEBUG) Gdx.app.debug(TAG, cof + "=" + entry);
    
    AnimData animData = mAnimData.create(entityId);
    
    // D2MOD: DATATBLS_GetAnimDataRecord returns default record if not found
    // Default values: dwFrames = 2048, dwAnimSpeed = 256
    // D2MOD silently uses default values without logging errors
    if (entry == null) {
      // Log failed lookup so we can check for typos or missing COF data
      log.warn(
        "COF lookup failed -> keyframes=null | entity={} cof=\"{}\" (token=\"{}\" mode={} \"{}\" wclass={} \"{}\")",
        entityId, cof, token, (int) mode, modeStr, (int) wclass, wclassStr
      );
      animData.speed     = 256;  // D2MOD: dwAnimSpeed = 256
      animData.frame     = 0;
      animData.numFrames = 2048 << 8;  // D2MOD: dwFrames = 2048, converted to fixed point
      animData.keyframes  = null;  // No keyframe data for default
      animData.lastKeyframeIndex = -1;
    } else {
      animData.speed     = entry.speed;
      animData.frame     = 0;
      animData.numFrames = entry.framesPerDir << 8;
      animData.keyframes = entry.data;
      animData.lastKeyframeIndex = -1;
    }
  }
}
