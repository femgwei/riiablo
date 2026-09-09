package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.IntMap;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Size;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.Map;

/** Adds the attackable Bone Wall/Bone Prison units to authoritative walk collision. */
@Wire(failOnNull = false)
@All({Monster.class, Position.class, Size.class})
public class BoneWallCollisionSystem extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(BoneWallCollisionSystem.class);

  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Size> mSize;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<Corpse> mCorpse;
  @Wire(name = "map", failOnNull = false)
  protected Map map;

  private final IntMap<Footprint> footprints = new IntMap<>();

  @Override
  protected void process(int entityId) {
    Monster monster = mMonster.get(entityId);
    if (!isBoneWall(monster)) return;
    Position position = mPosition.get(entityId);
    int unitSize = Math.max(1, mSize.get(entityId).size);
    int radius = unitSize - 1;
    int x = MathUtils.round(position.position.x) - radius;
    int y = MathUtils.round(position.position.y) - radius;
    int side = radius * 2 + 1;
    MapWrapper wrapper = mMapWrapper.has(entityId) ? mMapWrapper.get(entityId) : null;
    Map objectMap = wrapper != null && wrapper.map != null ? wrapper.map : map;
    Map.Zone zone = wrapper != null ? wrapper.zone : null;
    if (zone == null && objectMap != null) zone = objectMap.getZone(position.position);
    boolean enabled = isAlive(entityId);

    Footprint previous = footprints.get(entityId);
    if (previous != null && previous.matches(zone, x, y, side, enabled)) return;
    if (previous != null) previous.remove();
    Footprint next = new Footprint(zone, x, y, side, enabled);
    if (enabled && zone != null) next.add();
    footprints.put(entityId, next);
    log.debug("[NECRO_BONE_WALL_COLLISION] entity={} enabled={} footprint={},{},{}x{}",
        entityId, enabled, x, y, side, side);
  }

  private boolean isAlive(int entityId) {
    if (mCorpse.has(entityId)) return false;
    if (mCofReference.has(entityId)) {
      byte mode = mCofReference.get(entityId).mode;
      if (mode == Engine.Monster.MODE_DT || mode == Engine.Monster.MODE_DD) return false;
    }
    if (!mAttributesWrapper.has(entityId)) return true;
    Attributes attrs = mAttributesWrapper.get(entityId).attrs;
    StatRef hp = attrs != null ? attrs.get(Stat.hitpoints, StatRef.obtain()) : null;
    return hp == null || hp.asFixed() > 0f;
  }

  @Override
  protected void removed(int entityId) {
    Footprint footprint = footprints.remove(entityId);
    if (footprint != null) footprint.remove();
  }

  static boolean isBoneWall(Monster monster) {
    MonStats.Entry row = monster != null ? monster.monstats : null;
    return row != null && row.Id != null && "bonewall".equalsIgnoreCase(row.Id);
  }

  private static final class Footprint {
    final Map.Zone zone;
    final int x;
    final int y;
    final int side;
    final boolean enabled;

    Footprint(Map.Zone zone, int x, int y, int side, boolean enabled) {
      this.zone = zone;
      this.x = x;
      this.y = y;
      this.side = side;
      this.enabled = enabled;
    }

    boolean matches(Map.Zone zone, int x, int y, int side, boolean enabled) {
      return this.zone == zone && this.x == x && this.y == y
          && this.side == side && this.enabled == enabled;
    }

    void add() {
      zone.adjustObjectCollision(x, y, side, side, 1);
    }

    void remove() {
      if (enabled && zone != null) zone.adjustObjectCollision(x, y, side, side, -1);
    }
  }
}
