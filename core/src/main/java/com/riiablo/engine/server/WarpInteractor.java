package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import net.mostlyoriginal.api.system.core.PassiveSystem;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;

import com.riiablo.codec.excel.LvlWarp;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Box2DBody;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Size;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Warp;
import com.riiablo.engine.server.event.ZoneChangeEvent;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.map.Map;
import com.riiablo.engine.server.quest.QuestWarp;
import com.riiablo.engine.server.quest.Act5BaalQuest;
import com.riiablo.Riiablo;
import com.riiablo.save.D2SWriter;
import com.riiablo.net.packet.d2gs.QuestOperation;
import net.mostlyoriginal.api.event.common.EventSystem;

@Wire(failOnNull = false)
public class WarpInteractor extends PassiveSystem implements Interactable.Interactor {
  private static final String TAG = "WarpInteractor";

  protected ComponentMapper<Warp> mWarp;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Box2DBody> mBox2DBody;
  protected ComponentMapper<Size> mSize;
  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<Player> mPlayer;

  protected Pathfinder pathfinder;
  protected Actioneer actioneer;
  protected EventSystem events;
  @Wire(failOnNull = false)
  protected com.riiablo.engine.client.ClientNetworkSynchronizer clientNetwork;

  @Wire(name = "map")
  protected Map map;

  private final Vector2 tmpVec2 = new Vector2();

  @Override
  public void interact(int src, int entity) {
    if (clientNetwork != null) {
      long requestId = clientNetwork.requestQuest(
          QuestOperation.WARP_INTERACTION, entity, -1);
      if (requestId != 0L) {
        Gdx.app.log(TAG, "Warp interaction requested: player=" + src
            + " entity=" + entity + " request=" + requestId);
      }
      return;
    }
    warp(src, entity);
  }

  /** Performs one validated server/local authoritative warp transaction. */
  public boolean warp(int src, int entity) {
    Warp warp = mWarp.get(entity);
    MapWrapper sourceWrapper = mMapWrapper.get(entity);
    Map.Zone source = sourceWrapper == null ? null : sourceWrapper.zone;
    Map.Zone dst = warp == null ? null : map.findZone(warp.dstLevel);
    if (warp == null || source == null || dst == null) {
      Gdx.app.error(TAG, "Warp interaction missing map data: player=" + src
          + " entity=" + entity + " warp=" + warp + " source=" + source + " dst=" + dst);
      return false;
    }
    if (QuestWarp.isQuestWarp(warp.index)) {
      // Keep local/offline interaction on the same native object-72 gate as
      // D2GS.  Without this check a non-network client could bypass the
      // Worldstone Chamber -> Harrogath quest requirement.
      if (warp.dstLevel.Id == Act5BaalQuest.HARROGATH
          && source.level != null
          && source.level.Id == Act5BaalQuest.WORLDSTONE_CHAMBER) {
        Player player = mPlayer == null ? null : mPlayer.get(src);
        short record = player == null || player.data == null
            ? 0 : player.data.getQuests(Riiablo.ACT5)[Act5BaalQuest.RECORD];
        if (!Act5BaalQuest.canUseLastPortal(record, source.level.Id)) {
          Gdx.app.log(TAG, "A5Q6 last portal rejected: player=" + src
              + " source=" + source.level.Id);
          return false;
        }
      }
      int unitSize = mSize != null && mSize.has(src) ? mSize.get(src).size : Size.MEDIUM;
      Vector2 arrival = findQuestArrival(dst, unitSize);
      if (arrival == null) {
        Gdx.app.error(TAG, "Quest warp destination has no free coordinates: player=" + src
            + " destination=" + dst.level.LevelName + "(" + dst.level.Id + ")"
            + " unitSize=" + unitSize);
        return false;
      }
      commitTransition(src, dst, arrival);
      markA5Q6LastPortalUsed(src, source, warp);
      Gdx.app.log(TAG, "Quest warp interaction: player=" + src
          + " source=" + source.level.LevelName + "(" + source.level.Id + ")"
          + " destination=" + dst.level.LevelName + "(" + dst.level.Id + ")"
          + " arrival=" + arrival);
      return true;
    }
    int dstIndex = source.getWarp(warp.index);
    int dstWarpEntity = dst.findWarp(dstIndex);
    if (dstWarpEntity == Engine.INVALID_ENTITY) {
      Gdx.app.error(TAG, "Warp destination entity missing: source=" + source.level.LevelName
          + "(" + source.level.Id + ") special=0x" + Integer.toHexString(warp.index)
          + " destination=" + dst.level.LevelName + "(" + dst.level.Id + ")"
          + " reverseSpecial=0x" + Integer.toHexString(dstIndex));
      return false;
    }
    Vector2 dstWarpPos = mPosition.get(dstWarpEntity).position;
    int unitSize = mSize != null && mSize.has(src) ? mSize.get(src).size : Size.MEDIUM;
    if (!dst.findFreeCoordinates(dstWarpPos, unitSize, 50, true, tmpVec2)) {
      Gdx.app.error(TAG, "Warp destination has no free coordinates: source="
          + source.level.LevelName + "(" + source.level.Id + ")"
          + " destination=" + dst.level.LevelName + "(" + dst.level.Id + ")"
          + " reverseSpecial=0x" + Integer.toHexString(dstIndex)
          + " destinationPosition=" + dstWarpPos + " unitSize=" + unitSize);
      return false;
    }
    float arrivalX = tmpVec2.x;
    float arrivalY = tmpVec2.y;
    Vector2 position = mPosition.get(src).position;
    Gdx.app.log(TAG, "Warp interaction: player=" + src
        + " source=" + source.level.LevelName + "(" + source.level.Id + ")"
        + " special=0x" + Integer.toHexString(warp.index)
        + " sourcePosition=" + position
        + " destination=" + dst.level.LevelName + "(" + dst.level.Id + ")"
        + " reverseSpecial=0x" + Integer.toHexString(dstIndex)
        + " destinationPosition=" + dstWarpPos
        + " freeArrival=(" + arrivalX + "," + arrivalY + ")");
    commitTransition(src, dst, tmpVec2.set(arrivalX, arrivalY));

    Warp dstWarp = mWarp.get(dstWarpEntity);
    LvlWarp.Entry dstWarpEntry = dstWarp.warp;
    tmpVec2.set(arrivalX, arrivalY).add(dstWarpEntry.ExitWalkX, dstWarpEntry.ExitWalkY);
    actioneer.moveTo(src, tmpVec2);
    return true;
  }

  /** Mirrors OBJECTS_OperateFunction72_LastPortal's CUSTOM6 transition. */
  private void markA5Q6LastPortalUsed(int playerId, Map.Zone source, Warp warp) {
    if (warp == null || source == null || source.level == null
        || warp.dstLevel == null || warp.dstLevel.Id != Act5BaalQuest.HARROGATH
        || source.level.Id != Act5BaalQuest.WORLDSTONE_CHAMBER || mPlayer == null) return;
    Player player = mPlayer.get(playerId);
    if (player == null || player.data == null) return;
    short[] records = player.data.getQuests(Riiablo.ACT5);
    if (records == null || records.length <= Act5BaalQuest.RECORD) return;
    short previous = records[Act5BaalQuest.RECORD];
    short next = com.riiablo.engine.server.quest.NativeQuestRecord.set(
        previous, com.riiablo.engine.server.quest.NativeQuestRecord.CUSTOM6);
    if (next == previous) return;
    records[Act5BaalQuest.RECORD] = next;
    if (player.data.managed && Riiablo.saves != null) D2SWriter.INSTANCE.save(player.data);
    Gdx.app.log(TAG, "A5Q6 last portal completed: player=" + playerId
        + " record=0x" + Integer.toHexString(Short.toUnsignedInt(next)));
  }

  /**
   * Commits every authoritative warp side effect before the next network
   * snapshot: movement intent, position/body, level/RoomEx and warp marker.
   */
  private void commitTransition(int entityId, Map.Zone destination, Vector2 arrival) {
    actioneer.moveTo(entityId, Engine.INVALID_ENTITY);

    Vector2 position = mPosition.get(entityId).position;
    position.set(arrival);

    MapWrapper wrapper = mMapWrapper.has(entityId) ? mMapWrapper.get(entityId) : null;
    if (wrapper == null) wrapper = mMapWrapper.create(entityId);
    wrapper.set(map, destination);
    Map.RoomEx room = destination.findRoomEx(arrival.x, arrival.y);
    wrapper.roomId = room == null ? -1 : room.id;

    Box2DBody box2dWrapper = mBox2DBody.has(entityId) ? mBox2DBody.get(entityId) : null;
    if (box2dWrapper != null && box2dWrapper.body != null) {
      box2dWrapper.body.setTransform(position, box2dWrapper.body.getAngle());
    }

    UnitStates states = mUnitStates.has(entityId) ? mUnitStates.get(entityId) : null;
    if (states != null) {
      if (states.stateList == null) states.init(entityId);
      states.stateList.addState(StateId.SYNC_WARPED, 2, 1, entityId);
    }
    if (events != null) events.dispatch(ZoneChangeEvent.obtain(entityId, destination));
  }

  private Vector2 findQuestArrival(Map.Zone destination, int unitSize) {
    int centerX = destination.x() + destination.width() / 2;
    int centerY = destination.y() + destination.height() / 2;
    return destination.findFreeCoordinates(
        tmpVec2.set(centerX, centerY), unitSize, 50, true, tmpVec2)
        ? tmpVec2 : null;
  }
}
