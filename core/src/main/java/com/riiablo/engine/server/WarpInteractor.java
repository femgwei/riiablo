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
import com.riiablo.engine.server.component.Size;
import com.riiablo.engine.server.component.Warp;
import com.riiablo.map.Map;
import com.riiablo.engine.server.quest.QuestWarp;

public class WarpInteractor extends PassiveSystem implements Interactable.Interactor {
  private static final String TAG = "WarpInteractor";

  protected ComponentMapper<Warp> mWarp;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Box2DBody> mBox2DBody;
  protected ComponentMapper<Size> mSize;

  protected Pathfinder pathfinder;
  protected Actioneer actioneer;

  @Wire(name = "map")
  protected Map map;

  private final Vector2 tmpVec2 = new Vector2();

  @Override
  public void interact(int src, int entity) {
    Warp warp = mWarp.get(entity);
    MapWrapper sourceWrapper = mMapWrapper.get(entity);
    Map.Zone source = sourceWrapper == null ? null : sourceWrapper.zone;
    Map.Zone dst = warp == null ? null : map.findZone(warp.dstLevel);
    if (warp == null || source == null || dst == null) {
      Gdx.app.error(TAG, "Warp interaction missing map data: player=" + src
          + " entity=" + entity + " warp=" + warp + " source=" + source + " dst=" + dst);
      return;
    }
    if (QuestWarp.isQuestWarp(warp.index)) {
      int unitSize = mSize != null && mSize.has(src) ? mSize.get(src).size : Size.MEDIUM;
      Vector2 arrival = findQuestArrival(dst, unitSize);
      if (arrival == null) {
        Gdx.app.error(TAG, "Quest warp destination has no free coordinates: player=" + src
            + " destination=" + dst.level.LevelName + "(" + dst.level.Id + ")"
            + " unitSize=" + unitSize);
        return;
      }
      Vector2 position = mPosition.get(src).position;
      position.set(arrival);
      Box2DBody box2dWrapper = mBox2DBody.get(src);
      if (box2dWrapper != null) box2dWrapper.body.setTransform(position, 0);
      Gdx.app.log(TAG, "Quest warp interaction: player=" + src
          + " source=" + source.level.LevelName + "(" + source.level.Id + ")"
          + " destination=" + dst.level.LevelName + "(" + dst.level.Id + ")"
          + " arrival=" + arrival);
      return;
    }
    int dstIndex = source.getWarp(warp.index);
    int dstWarpEntity = dst.findWarp(dstIndex);
    if (dstWarpEntity == Engine.INVALID_ENTITY) {
      Gdx.app.error(TAG, "Warp destination entity missing: source=" + source.level.LevelName
          + "(" + source.level.Id + ") special=0x" + Integer.toHexString(warp.index)
          + " destination=" + dst.level.LevelName + "(" + dst.level.Id + ")"
          + " reverseSpecial=0x" + Integer.toHexString(dstIndex));
      return;
    }
    Vector2 dstWarpPos = mPosition.get(dstWarpEntity).position;
    int unitSize = mSize != null && mSize.has(src) ? mSize.get(src).size : Size.MEDIUM;
    if (!dst.findFreeCoordinates(dstWarpPos, unitSize, 50, true, tmpVec2)) {
      Gdx.app.error(TAG, "Warp destination has no free coordinates: source="
          + source.level.LevelName + "(" + source.level.Id + ")"
          + " destination=" + dst.level.LevelName + "(" + dst.level.Id + ")"
          + " reverseSpecial=0x" + Integer.toHexString(dstIndex)
          + " destinationPosition=" + dstWarpPos + " unitSize=" + unitSize);
      return;
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
    position.set(arrivalX, arrivalY);

    Box2DBody box2dWrapper = mBox2DBody.get(src);
    if (box2dWrapper != null) box2dWrapper.body.setTransform(position, 0);

    Warp dstWarp = mWarp.get(dstWarpEntity);
    LvlWarp.Entry dstWarpEntry = dstWarp.warp;
    tmpVec2.set(arrivalX, arrivalY).add(dstWarpEntry.ExitWalkX, dstWarpEntry.ExitWalkY);
    actioneer.moveTo(src, tmpVec2);
  }

  private Vector2 findQuestArrival(Map.Zone destination, int unitSize) {
    int centerX = destination.x() + destination.width() / 2;
    int centerY = destination.y() + destination.height() / 2;
    return destination.findFreeCoordinates(
        tmpVec2.set(centerX, centerY), unitSize, 50, true, tmpVec2)
        ? tmpVec2 : null;
  }
}
