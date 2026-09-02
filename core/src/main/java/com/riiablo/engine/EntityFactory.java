package com.riiablo.engine;

import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Classname;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.DS1ObjectWrapper;
import com.riiablo.engine.server.component.Flags;
import com.riiablo.engine.server.component.PathWrapper;
import com.riiablo.item.Item;
import com.riiablo.map.DS1;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import com.riiablo.engine.server.quest.QuestWarp;

import net.mostlyoriginal.api.system.core.PassiveSystem;

public abstract class EntityFactory extends PassiveSystem {
  private static final String TAG = "EntityFactory";

  protected ComponentMapper<Class> mClass;
  protected ComponentMapper<Classname> mClassname;
  protected ComponentMapper<Flags> mFlags;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<DS1ObjectWrapper> mDS1ObjectWrapper;
  protected ComponentMapper<PathWrapper> mPathWrapper;

  @Wire(name = "map")
  protected Map map;

  private final Vector2 tmpVec2 = new Vector2();

  protected final int createEntity(Class.Type type, String classname) {
    int id = world.create();
    mClass.create(id).type = type;
    mClassname.create(id).classname = classname;
    mFlags.create(id).flags = 0;
    return id;
  }

  public final int createPlayer(String name, int classId, float x, float y) {
    CharData charData = CharData.createRemote(name, (byte) classId);
    return createPlayer(charData, tmpVec2.set(x, y));
  }

  public abstract int createPlayer(CharData charData, Vector2 position);

  public final int createObject(Map.Preset preset, DS1.Object object, float x, float y) {
    int entityId = createObject(preset.getDS1().getAct(), object.type, object.id, x, y);
    if (entityId == Engine.INVALID_ENTITY) {
      String objectType;
      switch (object.type) {
        case DS1.Object.DYNAMIC_TYPE:
          objectType = Riiablo.files.MonPreset.getPlace(preset.getDS1().getAct(), object.id);
          break;
        case DS1.Object.STATIC_TYPE:
          objectType = String.valueOf(Riiablo.files.obj.getObjectId(preset.getDS1().getAct(), object.id));
          break;
        default:
          objectType = null;
      }
      Gdx.app.error(TAG, "Unknown entity id: " + objectType + "; " + preset + "; object=" + object);
      return Engine.INVALID_ENTITY;
    }

    mDS1ObjectWrapper.create(entityId).set(preset.getDS1(), object);
    if (object.path != null) mPathWrapper.create(entityId).path = object.path;
    return entityId;
  }

  public final int createObject(int act, int type, int id, float x, float y) {
    switch (type) {
      case DS1.Object.DYNAMIC_TYPE:
        return createDynamicObject(act, id, x, y);
      case DS1.Object.STATIC_TYPE:
        return createStaticObject(act, id, x, y);
      default:
        Gdx.app.error(TAG, "Unexpected ds1 object type: " + type);
        return Engine.INVALID_ENTITY;
    }
  }

  public abstract int createDynamicObject(int act, int monPresetId, float x, float y);
  public abstract int createStaticObject(int act, int objId, float x, float y);
  /** Creates an object whose id is already resolved against Objects.txt. */
  public abstract int createStaticObjectByClassId(int objectId, float x, float y);

  public final int createMonster(MonStats.Entry monstats, float x, float y) {
    return createMonster(monstats.hcIdx, x, y);
  }

  public abstract int createMonster(int monsterId, float x, float y);

  /**
   * Creates a ranked monster. Client/test factories may ignore server-only
   * quality metadata; the authoritative server overrides this method.
   */
  public int createMonster(int monsterId, float x, float y,
      int rank, long affixes, int championType, int uniqueId) {
    return createMonster(monsterId, x, y);
  }

  /** Restores a dead monster entity in place; unsupported factories return false. */
  public boolean resurrectMonster(int monsterId, int sourceId) {
    return false;
  }

  /** Executes native Skills.txt SrvSt61 for a monster's resurrection animation. */
  public boolean selfResurrectMonster(int monsterId) {
    return false;
  }

  /** Restores native monster target flags when a SrvSt61 animation completes. */
  public void finishSelfResurrection(int monsterId) {}

  /** Executes native Skills.txt SrvSt51 (submerge) for a monster. */
  public boolean submergeMonster(int monsterId) { return false; }

  /** Executes native Skills.txt SrvSt52 (emerge) for a monster. */
  public boolean emergeMonster(int monsterId) { return false; }

  /** Applies native D2UnitStrc flags; client-only and test factories may ignore them. */
  public void applyNativeUnitFlags(int entityId, int flags) {}

  public abstract int createWarp(int index, float x, float y);

  /** Creates a network-serializable warp whose destination is owned by a quest. */
  public int createQuestWarp(int destinationLevelId, float x, float y) {
    return createWarp(QuestWarp.encode(destinationLevelId), x, y);
  }

  public final int createItem(Item item, Vector2 position) {
    return createItem(item, position.x, position.y);
  }

  public abstract int createItem(Item item, float x, float y);

  public final int createMissile(Missiles.Entry missile, Vector2 angle, Vector2 position) {
    return createMissile(missile.Id, angle, position);
  }

  public final int createMissile(Missiles.Entry missile, Vector2 angle, Vector2 position, int ownerId) {
    return createMissile(missile.Id, angle, position, ownerId);
  }

  public abstract int createMissile(int missileId, Vector2 angle, Vector2 position);
  
  public int createMissile(int missileId, Vector2 angle, Vector2 position, int ownerId) {
    // Default implementation: create missile without ownerId
    return createMissile(missileId, angle, position);
  }
}
