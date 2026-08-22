package com.riiablo.map;

import com.artemis.annotations.Wire;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;

import net.mostlyoriginal.api.system.core.PassiveSystem;

public class MapManager extends PassiveSystem {
  private static final String TAG = "MapManager";

  @Wire(name = "map")
  protected Map map;

  @Wire(name = "factory")
  protected EntityFactory factory;

  public void createEntities() {
    for (Map.Zone zone : new Array.ArrayIterator<>(map.zones)) {
      createWarps(zone);
      createEntities(zone);
    }
  }

  private void createWarps(Map.Zone zone) {
    IntMap<DS1.Cell> specials = zone.specials;
    for (IntMap.Entry<DS1.Cell> entry : specials.entries()) {
      DS1.Cell cell = entry.value;
      if (Map.ID.WARPS.contains(cell.id)) {
        int hash = entry.key;
        int x = zone.x + (Map.Zone.tileHashX(hash) * DT1.Tile.SUBTILE_SIZE);
        int y = zone.y + (Map.Zone.tileHashY(hash) * DT1.Tile.SUBTILE_SIZE);
        int id = factory.createWarp(cell.id, x, y);
        if (id != Engine.INVALID_ENTITY) {
          zone.addWarp(id);
        } else {
          Gdx.app.error(TAG, String.format(
              "Unable to create warp level=%s(%d) special=0x%08X mainIndex=%d pos=(%d,%d)",
              zone.level.LevelName, zone.level.Id, cell.id, cell.mainIndex, x, y));
        }
      }
    }
  }

  public void createEntities(Map.Zone zone) {
    createNativeObjects(zone);

    // 只对城镇区域创建 NPC 和其他对象
    // 野外区域的对象应该通过 generator 或其他方式创建
    if (!zone.town) {
      return; // 跳过非城镇区域，避免在野外区域创建 NPC
    }
    
    for (int x = 0, gridX = 0, gridY = 0; x < zone.gridsX; x++, gridX += zone.gridSizeX, gridY = 0) {
      for (int y = 0; y < zone.gridsY; y++, gridY += zone.gridSizeY) {
        Map.Preset preset = zone.presets[x][y];
        if (preset == null) continue;
        createEntities(zone, preset, gridX, gridY);
      }
    }
  }

  private void createNativeObjects(Map.Zone zone) {
    int created = 0;
    int failed = 0;
    for (Map.NativeObject object : zone.nativeObjects) {
      // DS1 stores Act as zero-based in the file and riiablo's loader exposes
      // it as one-based. Act I therefore uses table section 1 here.
      int id = factory.createObject(1, DS1.Object.STATIC_TYPE, object.presetIndex,
          zone.x + object.x, zone.y + object.y);
      if (id == Engine.INVALID_ENTITY) {
        failed++;
      } else {
        zone.addEntity(id);
        created++;
      }
    }
    if (zone.nativeObjects.size > 0) {
      Gdx.app.log(TAG, String.format(
          "D2MOO native objects: level=%s(%d) exported=%d created=%d failed=%d",
          zone.level.LevelName, zone.level.Id, zone.nativeObjects.size, created, failed));
    }
  }

  private void createEntities(Map.Zone zone, Map.Preset preset, int gridX, int gridY) {
    final int x = zone.x + (gridX * DT1.Tile.SUBTILE_SIZE);
    final int y = zone.y + (gridY * DT1.Tile.SUBTILE_SIZE);
    DS1 ds1 = preset.ds1;
    for (int i = 0, size = ds1.numObjects; i < size; i++) {
      DS1.Object object = ds1.objects[i];
      int id = factory.createObject(preset, object, x + object.x, y + object.y);
      if (id != Engine.INVALID_ENTITY) zone.entities.add(id);
    }
  }
}
