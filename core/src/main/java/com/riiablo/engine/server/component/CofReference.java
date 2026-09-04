package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;
import com.riiablo.engine.Engine;

@PooledWeaver
public class CofReference extends Component {
  private static final byte[] PLAYER_TO_MONSTER_MODE = {
      0, 1, 2, 15, 3, 1, 2, 4, 5, 6, 7, 4, 11, 8, 9, 10, 11, 12, 14, 13
  };

  public String token;
  public byte   mode;
  public byte   wclass = Engine.WEAPON_HTH;

  /** Presentation-only shape override; logical entity type remains PLR. */
  public Class.Type visualType;
  public String visualToken;
  public byte visualWClass = -1;
  public boolean[] visualModes;

  public CofReference set(String token, byte mode) {
    this.token = token;
    this.mode = mode;
    clearVisualOverride();
    return this;
  }

  public CofReference setVisualOverride(Class.Type type, String token, byte wclass) {
    return setVisualOverride(type, token, wclass, null);
  }

  public CofReference setVisualOverride(
      Class.Type type, String token, byte wclass, boolean[] supportedModes) {
    visualType = type;
    visualToken = token;
    visualWClass = wclass;
    visualModes = supportedModes;
    return this;
  }

  public CofReference clearVisualOverride() {
    visualType = null;
    visualToken = null;
    visualWClass = -1;
    visualModes = null;
    return this;
  }

  public Class.Type effectiveType(Class.Type logicalType) {
    return visualType != null ? visualType : logicalType;
  }

  public String effectiveToken() {
    return visualToken != null ? visualToken : token;
  }

  public byte effectiveWClass() {
    return visualWClass >= 0 ? visualWClass : wclass;
  }

  /** D2Common_11014 player-mode conversion used by monster-based forms. */
  public byte effectiveMode(Class.Type logicalType) {
    if (logicalType == Class.Type.PLR && visualType == Class.Type.MON
        && mode >= 0 && mode < PLAYER_TO_MONSTER_MODE.length) {
      byte converted = PLAYER_TO_MONSTER_MODE[mode];
      while (visualModes != null && converted >= 0 && converted < visualModes.length
          && !visualModes[converted]) {
        switch (converted) {
          case Engine.Monster.MODE_NU: return converted;
          case Engine.Monster.MODE_A2:
          case Engine.Monster.MODE_SC: converted = Engine.Monster.MODE_A1; break;
          case Engine.Monster.MODE_BL: converted = Engine.Monster.MODE_GH; break;
          case Engine.Monster.MODE_S2:
          case Engine.Monster.MODE_S3:
          case Engine.Monster.MODE_S4: converted = Engine.Monster.MODE_S1; break;
          case Engine.Monster.MODE_RN: converted = Engine.Monster.MODE_WL; break;
          default: converted = Engine.Monster.MODE_NU; break;
        }
      }
      return converted;
    }
    return mode;
  }
}
