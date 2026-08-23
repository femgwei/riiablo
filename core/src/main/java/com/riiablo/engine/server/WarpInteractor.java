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
}
