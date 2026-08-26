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
import com.riiablo.engine.server.component.Warp;
import com.riiablo.map.Map;
import com.riiablo.engine.server.quest.QuestWarp;

public class WarpInteractor extends PassiveSystem implements Interactable.Interactor {
  private static final String TAG = "WarpInteractor";

  protected ComponentMapper<Warp> mWarp;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Box2DBody> mBox2DBody;

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
      Vector2 arrival = findQuestArrival(dst);
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
    Vector2 position = mPosition.get(src).position;
    Gdx.app.log(TAG, "Warp interaction: player=" + src
        + " source=" + source.level.LevelName + "(" + source.level.Id + ")"
        + " special=0x" + Integer.toHexString(warp.index)
        + " sourcePosition=" + position
        + " destination=" + dst.level.LevelName + "(" + dst.level.Id + ")"
        + " reverseSpecial=0x" + Integer.toHexString(dstIndex)
        + " destinationPosition=" + dstWarpPos);
    position.set(dstWarpPos);

    Box2DBody box2dWrapper = mBox2DBody.get(src);
    if (box2dWrapper != null) box2dWrapper.body.setTransform(position, 0);

    Warp dstWarp = mWarp.get(dstWarpEntity);
    LvlWarp.Entry dstWarpEntry = dstWarp.warp;
    tmpVec2.set(dstWarpPos).add(dstWarpEntry.ExitWalkX, dstWarpEntry.ExitWalkY);
    actioneer.moveTo(src, tmpVec2);
  }

  private Vector2 findQuestArrival(Map.Zone destination) {
    int centerX = destination.x() + destination.width() / 2;
    int centerY = destination.y() + destination.height() / 2;
    for (int radius = 0; radius < 32; radius++) {
      for (int dx = -radius; dx <= radius; dx++) {
        int x = centerX + dx;
        int top = centerY - radius;
        int bottom = centerY + radius;
        if (map.flags(x, top) == 0) return tmpVec2.set(x, top);
        if (map.flags(x, bottom) == 0) return tmpVec2.set(x, bottom);
      }
      for (int dy = -radius + 1; dy < radius; dy++) {
        int y = centerY + dy;
        int left = centerX - radius;
        int right = centerX + radius;
        if (map.flags(left, y) == 0) return tmpVec2.set(left, y);
        if (map.flags(right, y) == 0) return tmpVec2.set(right, y);
      }
    }
    return tmpVec2.set(centerX, centerY);
  }
}
