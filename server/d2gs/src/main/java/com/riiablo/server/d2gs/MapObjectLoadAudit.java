package com.riiablo.server.d2gs;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.riiablo.Riiablo;
import com.riiablo.codec.COF;
import com.riiablo.codec.DC;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.map.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Headless loading evidence, independent of walking and GL appearance. */
final class MapObjectLoadAudit {
  private static final HashMap<String, String> assets = new HashMap<>();

  static List<String> inspect(World world, Map.Zone zone) {
    List<String> lines = new ArrayList<>();
    int failures = 0, created = 0, skipped = 0, pending = 0, runtime = 0;
    for (Map.NativeObject source : zone.getNativeObjects()) {
      String error = "none";
      if ("created".equals(source.creationStatus)) {
        created++;
        com.riiablo.engine.server.component.Object object = world.getMapper(
            com.riiablo.engine.server.component.Object.class).get(source.entityId);
        MapWrapper owner = world.getMapper(MapWrapper.class).get(source.entityId);
        Position position = world.getMapper(Position.class).get(source.entityId);
        if (object == null || object.base == null) error = "missing_entity";
        else if (object.base.Id != source.resolvedObjectId) error = "wrong_class";
        else if (owner == null || owner.zone != zone) error = "wrong_level";
        else if (position == null || position.position.x != zone.x() + source.x
            || position.position.y != zone.y() + source.y) error = "wrong_position";
        else {
          Map.RoomEx room = zone.findRoomEx(position.position.x, position.position.y);
          if (source.roomId >= 0 && (room == null || room.id != source.roomId)) error = "wrong_room";
        }
      } else if ("PENDING".equals(source.creationStatus)) {
        pending++;
        error = "pending_after_level_materialization";
      } else if (source.creationStatus.startsWith("skipped_")) skipped++;
      else error = source.creationStatus;
      if (!"none".equals(error)) failures++;
      lines.add("OBJECT_PRESET level=" + zone.levelId() + " room=" + source.roomId
          + " raw=" + source.presetIndex + " ds1Raw=" + source.ds1Raw
          + " class=" + source.resolvedObjectId + " entity=" + source.entityId
          + " local=" + source.x + "," + source.y + " source=" + source.sourceFile
          + " status=" + source.creationStatus + " error=" + error);
    }
    IntBag entities = world.getAspectSubscriptionManager().get(Aspect.all(
        com.riiablo.engine.server.component.Object.class, MapWrapper.class)).getEntities();
    for (int i = 0; i < entities.size(); i++) {
      int id = entities.get(i);
      if (world.getMapper(MapWrapper.class).get(id).zone != zone) continue;
      runtime++;
      com.riiablo.engine.server.component.Object object = world.getMapper(
          com.riiablo.engine.server.component.Object.class).get(id);
      CofReference reference = world.getMapper(CofReference.class).get(id);
      Position position = world.getMapper(Position.class).get(id);
      Map.RoomEx room = position == null ? null
          : zone.findRoomEx(position.position.x, position.position.y);
      String resource = "not_drawn", error = "none";
      if (object.base == null) error = "missing_objects_row";
      else if (position == null) error = "missing_position";
      else if (object.base.Draw) {
        if (reference == null) error = "missing_cof_reference";
        else {
          byte mode = reference.mode == Engine.Object.MODE_OP ? reference.mode : object.mode;
          try { resource = readVisual(reference.token, mode); }
          catch (RuntimeException e) { error = e.toString().replace('\n', ' '); }
        }
      }
      if (object.base != null && position != null) {
        int mode = reference != null && reference.mode == Engine.Object.MODE_OP
            ? reference.mode : object.mode;
        if (object.base.HasCollision != null && mode >= 0
            && mode < object.base.HasCollision.length && object.base.HasCollision[mode]) {
          int x = Math.round(position.position.x - object.base.SizeX / 2f);
          int y = Math.round(position.position.y - object.base.SizeY / 2f);
          for (int dy = 0; dy < object.base.SizeY; dy++) for (int dx = 0; dx < object.base.SizeX; dx++) {
            if (zone.contains(x + dx, y + dy) && zone.objectCollisionReferences(x + dx, y + dy) <= 0)
              error = "missing_collision_at_" + (x + dx) + "," + (y + dy);
          }
        }
      }
      if (!"none".equals(error)) failures++;
      lines.add("OBJECT_RUNTIME level=" + zone.levelId() + " room=" + (room == null ? -1 : room.id)
          + " entity=" + id + " class=" + (object.base == null ? -1 : object.base.Id)
          + " name=" + (object.base == null ? "null" : object.base.Description)
          + " token=" + (object.base == null ? "null" : object.base.Token)
          + " mode=" + object.mode + " position=" + (position == null ? "null" : position.position)
          + " resources=" + resource + " error=" + error);
    }
    lines.add("OBJECT_AUDIT level=" + zone.levelId() + " exported=" + zone.getNativeObjects().size
        + " created=" + created + " skipped=" + skipped + " pending=" + pending
        + " runtime=" + runtime + " failures=" + failures + " result=" + (failures == 0 ? "PASS" : "FAIL"));
    return lines;
  }

  static String readVisual(String token, byte mode) {
    com.riiablo.engine.server.component.Class.Type type = com.riiablo.engine.server.component.Class.Type.OBJ;
    String modeName = type.getMode(mode);
    String root = type.PATH + "\\" + token + "\\";
    String cofPath = root + "cof\\" + token + modeName + "hth.cof";
    String cached = assets.get(cofPath);
    if (cached != null) return cached;
    if (!Riiablo.mpqs.contains(cofPath)) throw new IllegalStateException("missing " + cofPath);
    COF cof = COF.loadFromFile(Riiablo.mpqs.resolve(cofPath));
    if (cof.getNumLayers() == 0 || cof.getNumFramesPerDir() == 0)
      throw new IllegalStateException("empty " + cofPath);
    StringBuilder result = new StringBuilder(cofPath);
    for (int i = 0; i < cof.getNumLayers(); i++) {
      COF.Layer layer = cof.getLayer(i);
      String component = Engine.getComposite(layer.component);
      String stem = root + component + "\\" + token + component + "LIT";
      String path = stem + modeName + layer.weaponClass + ".dcc";
      if (!Riiablo.mpqs.contains(path) && cof.getNumFramesPerDir() <= 1 && mode != 1) {
        String fallback = stem + "OP" + layer.weaponClass + ".dcc";
        if (Riiablo.mpqs.contains(fallback)) path = fallback;
      }
      if (!Riiablo.mpqs.contains(path)) {
        path = stem + modeName + layer.weaponClass + ".dc6";
        if (!Riiablo.mpqs.contains(path) && cof.getNumFramesPerDir() <= 1 && mode != 1) {
          String fallback = stem + "OP" + layer.weaponClass + ".dc6";
          if (Riiablo.mpqs.contains(fallback)) path = fallback;
        }
      }
      if (!Riiablo.mpqs.contains(path)) throw new IllegalStateException("missing " + path);
      DC dc = DC.loadFromFile(Riiablo.mpqs.resolve(path));
      try {
        if (dc.getNumDirections() <= 0 || dc.getNumFramesPerDir() <= 0)
          throw new IllegalStateException("empty " + path);
        result.append('|').append(path);
      } finally { dc.dispose(); }
    }
    assets.put(cofPath, result.toString());
    return result.toString();
  }
}
