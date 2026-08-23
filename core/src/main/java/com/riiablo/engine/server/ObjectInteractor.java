package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.Gdx;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Levels;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Object;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import com.riiablo.save.D2SWriter;

import net.mostlyoriginal.api.system.core.PassiveSystem;

public class ObjectInteractor extends PassiveSystem implements Interactable.Interactor {
  private static final String TAG = "ObjectInteractor";

  protected ComponentMapper<Object> mObject;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<Sequence> mSequence;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Player> mPlayer;

  @Wire(name = "map")
  protected Map map;

  @Override
  public void interact(int src, int entityId) {
    Object object = mObject.get(entityId);
    if (object == null || object.base == null) {
      Gdx.app.error(TAG, "Object interaction has no object data: entity=" + entityId
          + " player=" + src);
      return;
    }
    if (object.base.OperateFn == 23) {
      CofReference cof = mCofReference.get(entityId);
      Position position = mPosition.get(entityId);
      Gdx.app.log(TAG, "Waypoint interaction received: entity=" + entityId
          + " player=" + src + " object=" + object.base.Id
          + " mode=" + (cof == null ? "none" : cof.mode)
          + " position=" + (position == null ? "none" : position.position));
    }
    operate(src, entityId, object.base.OperateFn);
  }

  private void operate(int src, int entityId, int operateFn) {
    switch (operateFn) {
      case 0:
        break;
      case 1: case 2: case 3: case 4: case 5: case 6: case 7: case 8: case 9:
      case 10: case 11: case 12: case 13: case 14: case 15: case 16: case 17: case 18: case 19:
      case 20: case 21: case 22:
        break;
      case 23: { // waypoint
        Levels.Entry level = getLevel(entityId);
        Player player = mPlayer.get(src);
        if (level == null || level.Waypoint == 0xFF || player == null || player.data == null) {
          Gdx.app.error(TAG, "Unable to activate waypoint: entity=" + entityId
              + " player=" + src + " level=" + level);
          break;
        }

        CharData data = player.data;
        boolean newlyActivated = data.activateWaypoint(level.Act, level.Waypoint);
        CofReference cofComponent = mCofReference.get(entityId);
        if (cofComponent == null) {
          Gdx.app.error(TAG, "Waypoint has no animation state: entity=" + entityId
              + " level=" + level.LevelName + "(" + level.Id + ")");
          break;
        }
        boolean openMenu = cofComponent.mode == Engine.Object.MODE_OP
            || cofComponent.mode == Engine.Object.MODE_ON;
        if (cofComponent.mode == Engine.Object.MODE_NU) {
          mSequence.create(entityId).sequence(Engine.Object.MODE_OP, Engine.Object.MODE_ON);
          Riiablo.audio.play("object_waypoint_open", true);
        }

        if (newlyActivated) {
          Gdx.app.log(TAG, "Waypoint activated: level=" + level.LevelName
              + "(" + level.Id + ") act=" + level.Act + " index=" + level.Waypoint);
          if (data.managed && Riiablo.saves != null && !D2SWriter.INSTANCE.save(data)) {
            Gdx.app.error(TAG, "Failed to persist activated waypoint for " + data.name);
          }
        }

        Gdx.app.log(TAG, "Waypoint interaction handled: level=" + level.LevelName
            + "(" + level.Id + ") newlyActivated=" + newlyActivated
            + " openMenu=" + openMenu + " mode=" + cofComponent.mode);

        // This system is installed in client and headless server worlds. Only
        // the client owns UI; activation itself is stored on the source player.
        if (openMenu && Riiablo.game != null) {
          Riiablo.game.waygatePanel.refresh();
          Riiablo.game.setLeftPanel(Riiablo.game.waygatePanel);
        }
      }
        break;
      case 24: case 25: case 26: case 27: case 28: case 29:
      case 30: case 31:
        break;
      case 32: // stash
        Riiablo.game.setLeftPanel(Riiablo.game.stashPanel);
        break;
      case 33: case 34: case 35: case 36: case 37: case 38: case 39:
      case 40: case 41: case 42: case 43: case 44: case 45: case 46: case 47: case 48: case 49:
      case 50: case 51: case 52: case 53: case 54: case 55: case 56: case 57: case 58: case 59:
      case 60: case 61: case 62: case 63: case 64: case 65: case 66: case 67: case 68: case 69:
      case 70: case 71: case 72: case 73:
        break;
      default:
        Gdx.app.error(TAG, "Invalid OperateFn for " + entityId + ": " + operateFn);
    }
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
