package com.riiablo.engine.server;

import com.artemis.BaseEntitySystem;
import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.Gdx;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Classname;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.NativeObjectState;
import com.riiablo.engine.server.component.Object;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.object.NativeObjectInteractTypeResolver;
import com.riiablo.engine.server.object.NativeObjectOperateTable;
import com.riiablo.engine.server.object.NativeObjectOperateTable.Lifecycle;
import com.riiablo.map.Map;

@All(Object.class)
public class ObjectInitializer extends BaseEntitySystem {
  private static final String TAG = "ObjectInitializer";

  protected ComponentMapper<Object> mObject;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<Classname> mClassname;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<NativeObjectState> mNativeObjectState;
  protected ComponentMapper<Interactable> mInteractable;

  protected CofManager cofs;
  @Wire(name = "map")
  protected Map map;

  @Override
  protected void inserted(int entityId) {
    initialize(entityId);
  }

  @Override
  protected void processSystem() {}

  public void initialize(int entityId) {
    Objects.Entry base = mObject.get(entityId).base;
    NativeObjectState nativeState = mNativeObjectState.get(entityId);
    if (nativeState != null && mCofReference.has(entityId)
        && nativeState.initialMode >= Engine.Object.MODE_NU
        && nativeState.initialMode <= Engine.Object.MODE_S5) {
      // D2MOO exports nMode with the preset unit. Apply it before the
      // object-specific InitFn so doors/chests do not reset to NU on reload.
      cofs.setMode(entityId, nativeState.initialMode);
    }
    initializeInteractType(entityId, base, nativeState);
    switch (base.InitFn) {
      case 0:
        break;
      case 1: case 2: case 3: case 4: case 5: case 6: case 7:
        break;
      case 8: // torch
        cofs.setMode(entityId, Engine.Object.MODE_ON);

        // FIXME: Set random start frame?
        //int framesPerDir = animation.getNumFramesPerDir();
        //animation.setFrame(MathUtils.random(0, framesPerDir - 1));
        break;
      case 9 : case 10: case 11: case 12: case 13: case 14: case 15: case 16:
        break;
      case 17: // waypoint
        Levels.Entry level = getLevel(entityId);
        boolean active = level != null
            && level.Waypoint != 0xFF
            && Riiablo.charData != null
            && Riiablo.charData.isWaypointActivated(level.Act, level.Waypoint);
        cofs.setMode(entityId, active ? Engine.Object.MODE_ON : Engine.Object.MODE_NU);
        break;
      case 18:
      case 19: case 20: case 21: case 22: case 23: case 24: case 25: case 26: case 27: case 28:
      case 29: case 30: case 31: case 32: case 33: case 34: case 35: case 36: case 37: case 38:
      case 39: case 40: case 41: case 42: case 43: case 44: case 45: case 46: case 47: case 48:
      case 49: case 50: case 51: case 52: case 53: case 54: case 55: case 56: case 57: case 58:
      case 59: case 60: case 61: case 62: case 63: case 64: case 65: case 66: case 67: case 68:
      case 69: case 70: case 71: case 72: case 73: case 74: case 75: case 76: case 77: case 78:
      case 79:
        break;
      default:
        Gdx.app.error(TAG, "Invalid InitFn for " + mClassname.get(entityId).classname + ": " + base.InitFn);
    }
    restorePersistentInteractionState(entityId, base, nativeState);
  }

  private void initializeInteractType(int entityId, Objects.Entry base,
      NativeObjectState state) {
    if (state == null || state.interactType >= 0
        || !NativeObjectInteractTypeResolver.supports(base)) return;

    Levels.Entry level = getLevel(entityId);
    Position position = mPosition.get(entityId);
    int normalMonsterLevel = level == null || level.MonLvl == null
        || level.MonLvl.length == 0 ? 0 : level.MonLvl[0];
    int levelId = level == null ? 0 : level.Id;
    int x = state.source != null ? state.source.x
        : position == null ? 0 : (int) position.position.x;
    int y = state.source != null ? state.source.y
        : position == null ? 0 : (int) position.position.y;
    int seed = map == null ? 0 : map.seed();
    int interactType = NativeObjectInteractTypeResolver.resolve(base,
        normalMonsterLevel, seed, levelId, state.currentClassId, x, y);
    state.persistInteractType(interactType);
    Gdx.app.debug(TAG, "Native object InteractType initialized: entity=" + entityId
        + " object=" + base.Id + " initFn=" + base.InitFn
        + " level=" + levelId + " interactType=" + interactType);
  }

  private void restorePersistentInteractionState(int entityId, Objects.Entry base,
      NativeObjectState state) {
    if (state == null || !mInteractable.has(entityId)) return;
    Lifecycle lifecycle = NativeObjectOperateTable.resolve(base, state.kind);
    boolean exhausted;
    switch (lifecycle) {
      case ANIMATED_CONTAINER:
      case INSTANT_CONTAINER:
      case ONE_WAY_DOOR:
        exhausted = state.opened;
        break;
      case SHRINE:
      case ARCANE_SYMBOL:
      case TRAP:
      case QUEST_OBJECT:
        exhausted = state.activated;
        break;
      default:
        exhausted = false;
        break;
    }
    if (exhausted) mInteractable.remove(entityId);
  }

  private Levels.Entry getLevel(int entityId) {
    MapWrapper wrapper = mMapWrapper.get(entityId);
    if (wrapper != null && wrapper.zone != null) return wrapper.zone.level;
    Position position = mPosition.get(entityId);
    if (position == null || map == null) return null;
    Map.Zone zone = map.getZone(position.position);
    return zone == null ? null : zone.level;
  }
}
