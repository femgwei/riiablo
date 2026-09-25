package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.badlogic.gdx.Gdx;
import com.riiablo.Riiablo;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Monster;
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
  protected ComponentMapper<Monster> mMonster;

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
    // Reduced 1.10f exports occasionally leave a freshly spawned unit with
    // an unset mode/weapon class. Native D2 falls back to the type defaults;
    // never index the compact mode/wclass tables with -1 and crash the sim.
    if (mode < 0 || mode >= t.MODE.length) {
      log.warn("COF mode out of range -> default | entity={} type={} mode={}",
          entityId, t, (int) mode);
      mode = t.DEFAULT_MODE;
    }
    if (wclass < 0 || wclass >= 15) {
      log.warn("COF wclass out of range -> HTH | entity={} wclass={}",
          entityId, (int) wclass);
      wclass = Engine.WEAPON_HTH;
    }
    String modeStr = t.MODE[mode];
    String wclassStr = Engine.getWClass(wclass);
    String cof = token + modeStr + wclassStr;
    D2.Entry entry = Riiablo.anim.getEntry(cof);
    if (entry == null) {
      // A few 1.10f asset packs omit presentation-only monster COFs (most
      // commonly BL/XX).  Native DATATBLS_GetAnimDataRecord falls back to a
      // usable mode record rather than running a 2048-frame empty animation.
      // Keep the requested mode in CofReference for networking, but resolve
      // the local keyframes through the closest safe attack/reaction mode.
      for (String fallbackMode : fallbackModes(modeStr)) {
        String candidate = token + fallbackMode + wclassStr;
        D2.Entry fallback = Riiablo.anim.getEntry(candidate);
        if (fallback != null) {
          entry = fallback;
          log.warn("COF lookup fallback | entity={} requested=\"{}\" resolved=\"{}\"",
              entityId, cof, candidate);
          break;
        }
      }
    }
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
      Monster monster = mMonster.get(entityId);
      if (monster != null && Monster.isMeleeMode(mode)) {
        int attackKeyframes = 0;
        if (entry.data != null) {
          for (byte keyframe : entry.data) {
            if (keyframe == Engine.KEYFRAME_ATK) attackKeyframes++;
          }
        }
        log.info("[MONSTER_MELEE] phase=anim_resolved entity={} monster={} cof={} mode={} "
                + "frames={} speed={} keyframes={} atkKeyframes={}",
            entityId, monster.monstats != null ? monster.monstats.Id : "unknown", cof,
            Monster.modeName(mode), entry.framesPerDir, entry.speed,
            entry.data != null ? entry.data.length : 0, attackKeyframes);
      }
    }
  }

  /** Ordered local fallbacks for presentation-only monster modes. */
  static String[] fallbackModes(String mode) {
    if ("BL".equals(mode)) return new String[] {"GH", "A1"};
    if ("XX".equals(mode)) return new String[] {"A1", "S1"};
    if ("S2".equals(mode) || "S3".equals(mode) || "S4".equals(mode)
        || "SC".equals(mode)) return new String[] {"S1", "A1"};
    if ("RN".equals(mode)) return new String[] {"WL"};
    return new String[0];
  }
}
