package com.riiablo.engine.client;

import com.artemis.ComponentMapper;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.IntIntMap;

import com.riiablo.Riiablo;
import com.riiablo.audio.Audio;
import com.riiablo.engine.server.ai.AI;
import com.riiablo.engine.server.ai.Npc;
import com.riiablo.codec.excel.LvlWarp;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.codec.excel.Objects;
import com.riiablo.codec.excel.Shrines;
import com.riiablo.codec.util.BBox;
import com.riiablo.engine.Engine;
import com.riiablo.engine.client.component.AnimationWrapper;
import com.riiablo.engine.client.component.BBoxWrapper;
import com.riiablo.engine.client.component.CofComponentDescriptors;
import com.riiablo.engine.client.component.Label;
import com.riiablo.engine.client.component.Selectable;
import com.riiablo.engine.server.ServerEntityFactory;
import com.riiablo.engine.server.component.Box2DBody;
import com.riiablo.engine.server.component.Item;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.SoundEmitter;
import com.riiablo.engine.server.component.Warp;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import com.riiablo.engine.server.object.NativeShrineResolver;

public class ClientEntityFactory extends ServerEntityFactory {
  private static final String TAG = "ClientEntityFactory";

  protected ComponentMapper<CofComponentDescriptors> mCofComponentDescriptors;
  protected ComponentMapper<AnimationWrapper> mAnimationWrapper;
  protected ComponentMapper<BBoxWrapper> mBBoxWrapper;
  protected ComponentMapper<Label> mLabel;
  protected ComponentMapper<Selectable> mSelectable;
  protected ComponentMapper<SoundEmitter> mSoundEmitter;
  protected ComponentMapper<Box2DBody> mBox2DBody;

  protected MenuManager menuManager;
  protected DialogManager dialogManager;
  protected OverlayManager overlayManager;

  @Override
  public int createPlayer(CharData charData, Vector2 position) {
    int id = super.createPlayer(charData, position);
    mCofComponentDescriptors.create(id);
    mAnimationWrapper.create(id);
    mBBoxWrapper.create(id).box = mAnimationWrapper.get(id).animation.getBox();
    mBox2DBody.create(id);
    return id;
  }

  @Override
  public int createDynamicObject(int act, int monPresetId, float x, float y) {
    return super.createDynamicObject(act, monPresetId, x, y);
  }

  @Override
  public int createStaticObject(int act, int objId, float x, float y) {
    int id = super.createStaticObject(act, objId, x, y);
    return finishStaticObject(id, x, y);
  }

  @Override
  public int createStaticObjectByClassId(int objectId, float x, float y) {
    int id = super.createStaticObjectByClassId(objectId, x, y);
    return finishStaticObject(id, x, y);
  }

  private int finishStaticObject(int id, float x, float y) {
    // A DS1 object index is not guaranteed to resolve to a usable Objects.txt
    // row. The server factory reports those entries as INVALID_ENTITY; do not
    // try to attach client-only components to that sentinel entity id.
    if (id == Engine.INVALID_ENTITY) return Engine.INVALID_ENTITY;

    if (mObject.has(id) && mObject.get(id).base != null
        && mObject.get(id).base.Id
            == com.riiablo.engine.server.object.NativeQuestObjectResolver.TOWN_PORTAL
        && Riiablo.audio != null) {
      Riiablo.audio.play("object_townportal", true);
    }

    Objects.Entry base = mObject.get(id).base;
    boolean waypoint = isWaypoint(base);

    String name;
    if (waypoint) {
      Map.Zone zone = map.getZone(x, y);
      String objectName = Riiablo.string.lookup(base.Name);
      if (zone == null) {
        // A network server may own static objects for acts/levels that this
        // client has not loaded. Keep the harmless visual replica, but do not
        // attach a MapWrapper whose null zone would crash interaction systems.
        mMapWrapper.remove(id);
        name = objectName;
        com.badlogic.gdx.Gdx.app.log(TAG, String.format(
            "[ENTITY_SYNC] phase=static_object_deferred entity=%d object=%d "
                + "waypoint=true position=(%.2f,%.2f) reason=zone_unavailable",
            id, base.Id, x, y));
      } else {
        mMapWrapper.get(id).set(map, zone);
        String levelName = Riiablo.string.lookup(zone.level.LevelName);
        name = String.format("%s\n%s", levelName, objectName);
      }
    } else {
      name = shrineViewName(base, x, y);
      if (name == null) {
        name = base.Name.equalsIgnoreCase("dummy")
            ? base.Description : Riiablo.string.lookup(base.Name);
      }
    }

    if (base.Draw) {
      mCofComponentDescriptors.create(id);
      mAnimationWrapper.create(id);
    }

    BBoxWrapper boxWrapper = mBBoxWrapper.create(id);
    if (waypoint) {
      BBox box = boxWrapper.box = new BBox();
      box.xMin = -70;
      box.yMin = -30;
      box.xMax = -box.xMin;
      box.yMax = -box.yMin;
      box.width = Math.abs(2 * box.xMin);
      box.height = Math.abs(2 * box.yMin);
    } else if (mAnimationWrapper.has(id)) {
      boxWrapper.box = mAnimationWrapper.get(id).animation.getBox();
    }
    if (base.Id == com.riiablo.engine.server.object.NativeQuestObjectResolver.TOWN_PORTAL
        || base.Id == 60) {
      // The warp endpoint is anchored at the portal's ground tile, while the
      // animation rises above it.  Native D2 accepts clicks over the whole
      // portal, not only the bottom tile; use a generous visual-sized box.
      BBox box = boxWrapper.box = new BBox();
      box.asBox(-32, -64, 64, 96);
    }

    Label label = mLabel.create(id);
    label.offset.y = -base.NameOffset;
    label.actor = createLabel(name);
    label.actor.setUserObject(id);

    // Object mode events normally keep Selectable in sync, but native D2MOO
    // objects enter the world in NU mode. No transition is guaranteed before
    // the first input frame, so a waypoint could have Interactable and a hit
    // box while remaining invisible to HoveredManager. Wire its initial input
    // state explicitly; SelectableManager will still update later modes.
    if (isInitiallySelectable(base)) mSelectable.create(id);

    mBox2DBody.create(id);
    if (waypoint) {
      Map.Zone zone = mMapWrapper.has(id) ? mMapWrapper.get(id).zone : null;
      float interactionRange = mInteractable.has(id) ? mInteractable.get(id).range : 0;
      com.badlogic.gdx.Gdx.app.log(TAG, "Waypoint client entity wired: entity=" + id
          + " object=" + base.Id + " operateFn=" + base.OperateFn
          + " range=" + interactionRange + " selectable=" + mSelectable.has(id)
          + " interactable=" + mInteractable.has(id)
          + " level=" + (zone == null ? "null" : zone.level.LevelName + "(" + zone.level.Id + ")")
          + " position=(" + x + "," + y + ")");
    }
    return id;
  }

  static boolean isWaypoint(Objects.Entry base) {
    return isWaypointObject(base);
  }

  static boolean isShrine(Objects.Entry base) {
    return base != null && (base.OperateFn == 2 || base.ShrineFunction != 0);
  }

  private String shrineViewName(Objects.Entry base, float x, float y) {
    if (!isShrine(base) || Riiablo.files == null || Riiablo.files.Shrines == null) {
      return null;
    }
    int shrineId = resolveClientShrineId(base, x, y);
    if (shrineId < 0 || shrineId >= Riiablo.files.Shrines.size()) return null;
    Shrines.Entry shrine = Riiablo.files.Shrines.get(shrineId);
    return shrineDisplayName(shrine);
  }

  private int resolveClientShrineId(Objects.Entry base, float x, float y) {
    if (!isShrine(base) || map == null || Riiablo.files == null
        || Riiablo.files.Shrines == null) return -1;
    Map.Zone zone = map.getZone(x, y);
    int levelId = zone == null || zone.level == null ? 0 : zone.level.Id;
    return NativeShrineResolver.resolve(Riiablo.files.Shrines, base, base.Id,
        levelId, map.seed(), (int) x, (int) y);
  }

  /** Maps the native Shrines.txt code to its stock overhead icon. */
  static String shrineOverlay(int shrineId) {
    if (Riiablo.files == null || Riiablo.files.Shrines == null) return null;
    Shrines.Entry shrine = Riiablo.files.Shrines.get(shrineId);
    if (shrine == null) return null;
    switch (shrine.Code) {
      case 6: return "shrine_armor";
      case 7: return "shrine_combat";
      case 8: return "shrine_resist_fire";
      case 9: return "shrine_resist_cold";
      case 10: return "shrine_resist_lightning";
      case 11: return "shrine_resist_poison";
      case 12: return "shrine_skill";
      case 13: return "shrine_mana_regen";
      case 14: return "shrine_stamina";
      case 15: return "shrine_experience";
      default: return null;
    }
  }

  /**
   * Resolves the native shrine tooltip name.  Retail Shrines.txt stores
   * {@code view name} as the placeholder "blah"; the actual localized names
   * are the string-table keys ShrId0..ShrId22.
   */
  static String shrineDisplayName(Shrines.Entry shrine) {
    if (shrine == null) return null;
    String key = "ShrId" + shrine.Code;
    String localized = Riiablo.string == null ? null : Riiablo.string.lookup(key);
    if (localized != null && !localized.isEmpty()
        && !localized.regionMatches(true, 0, "ERROR:", 0, 6)) {
      return localized;
    }

    String view = shrine.ViewName;
    if (view != null && !view.isEmpty() && !view.equalsIgnoreCase("blah")) {
      String resolved = Riiablo.string == null ? null : Riiablo.string.lookup(view);
      if (resolved != null && !resolved.isEmpty()
          && !resolved.regionMatches(true, 0, "ERROR:", 0, 6)) return resolved;
      return view;
    }
    return com.riiablo.engine.server.object.ShrineType.getName(shrine.Code);
  }

  static boolean isInitiallySelectable(Objects.Entry base) {
    if (isWaypoint(base)) return true;
    if (base == null) return false;
    if (base.Id == com.riiablo.engine.server.object.NativeQuestObjectResolver.TOWN_PORTAL
        || base.Id == 60) return true;
    if (base.Selectable != null
        && Engine.Object.MODE_NU < base.Selectable.length
        && base.Selectable[Engine.Object.MODE_NU]) return true;
    // Converted Objects.txt rows may omit Selectable flags while retaining a
    // valid native OperateFn. Match ServerEntityFactory's interaction-range
    // fallback so the client can actually target the same object.
    return base.Draw && base.OperateFn > 0 && base.OperateFn != 23;
  }

  /**
   * Projects the authoritative object lifecycle into the client input
   * components. Removing Interactable is important: Selectable only controls
   * hover presentation, while interaction systems query Interactable itself.
   * Recreating it through the factory preserves the native range and object
   * interactor after a shrine cooldown or room re-entry.
   */
  public void applyAuthoritativeObjectState(int entityId, int mode, int stateFlags) {
    com.riiablo.engine.server.component.Object object = mObject.get(entityId);
    if (object == null) return;

    object.mode = (byte) mode;
    object.stateFlags = (byte) stateFlags;
    boolean interactable = (stateFlags
        & com.riiablo.engine.server.component.Object.STATE_INTERACTABLE) != 0;
    if (!interactable) {
      mInteractable.remove(entityId);
      mSelectable.remove(entityId);
      return;
    }

    float range = resolveObjectInteractionRange(object.base);
    if (range <= 0f) {
      // Do not manufacture a clickable area for an unknown or malformed
      // Objects.txt row even if a peer sends an inconsistent flag.
      mInteractable.remove(entityId);
      mSelectable.remove(entityId);
      return;
    }

    mInteractable.create(entityId).set(range, objectInteractor);
    mSelectable.create(entityId);
  }

  @Override
  public int createMonster(int monsterId, float x, float y) {
    return createMonster(monsterId, x, y, 0, 0L, -1, -1);
  }

  /**
   * Creates a ranked monster and attaches the client-side presentation
   * components. Room activation uses this overload for native SuperUnique
   * metadata; keeping the presentation setup here is essential because the
   * server factory implementation only creates authoritative components.
   */
  @Override
  public int createMonster(int monsterId, float x, float y,
      int rank, long affixes, int championType, int uniqueId) {
    int id = super.createMonster(monsterId, x, y, rank, affixes, championType, uniqueId);
    if (id == Engine.INVALID_ENTITY) return id;

    Monster monster = mMonster.get(id);
    MonStats.Entry monstats = monster.monstats;
    MonStats2.Entry monstats2 = monster.monstats2;

    String name = monstats.NameStr.equalsIgnoreCase("dummy")
        ? monstats.Id : Riiablo.string.lookup(monstats.NameStr);

    mCofComponentDescriptors.create(id);

    mAnimationWrapper.create(id);
    mBBoxWrapper.create(id).box = mAnimationWrapper.get(id).animation.getBox();
    mBox2DBody.create(id);

    if (monstats.Align == 1) {
      Label label = mLabel.create(id);
      label.offset.y = monstats2.pixHeight;
      label.actor = createLabel(name);
      label.actor.setUserObject(id);
    }

    if (monstats2.isSel) mSelectable.create(id);

    AI ai = mAIWrapper.get(id).ai;
    if (ai instanceof Npc) {
      ((Npc) ai).createMenu(menuManager, dialogManager);
    }

    return id;
  }

  @Override
  public boolean resurrectMonster(int monsterId, int sourceId) {
    if (!super.resurrectMonster(monsterId, sourceId)) return false;
    Monster monster = mMonster.get(monsterId);
    if (monster.monstats2 != null && monster.monstats2.isSel) {
      mSelectable.create(monsterId);
    }
    mBox2DBody.create(monsterId);
    return true;
  }

  @Override
  public int createWarp(int index, float x, float y) {
    return createWarp(null, index, x, y);
  }

  @Override
  public int createWarp(Map.Zone sourceZoneHint, int index, float x, float y) {
    if (sourceZoneHint == null && map.getZone(x, y) == null) {
      // The server currently sends static entities outside the client's
      // loaded map. ServerEntityFactory.createWarp dereferences the source
      // zone immediately, so reject this remote replica before delegating.
      com.badlogic.gdx.Gdx.app.log(TAG, String.format(
          "[ENTITY_SYNC] phase=warp_deferred index=0x%08X position=(%.2f,%.2f) "
              + "reason=zone_unavailable",
          index, x, y));
      return Engine.INVALID_ENTITY;
    }
    boolean questWarp = com.riiablo.engine.server.quest.QuestWarp.isQuestWarp(index);
    final int mainIndex   = DT1.Tile.Index.mainIndex(index);
    final int subIndex    = DT1.Tile.Index.subIndex(index);
    final int orientation = DT1.Tile.Index.orientation(index);

    int id = super.createWarp(sourceZoneHint, index, x, y);
    if (id == Engine.INVALID_ENTITY) {
      // Server factory returned INVALID_ENTITY (e.g., LvlWarp entry not found), skip
      return Engine.INVALID_ENTITY;
    }
    Warp warp = mWarp.get(id);
    LvlWarp.Entry entry = warp.warp;

    BBox box = new BBox();
    box.xMin = questWarp ? -2 : entry.SelectX;
    box.yMin = questWarp ? -2 : entry.SelectY;
    box.width = questWarp ? 4 : entry.SelectDX;
    box.height = questWarp ? 4 : entry.SelectDY;
    box.xMax = box.width + box.xMin;
    box.yMax = box.height + box.yMin;

    // Ordinary town portals are encoded as QuestWarp endpoints.  Their
    // LvlWarp selection rectangle is intentionally tiny because it describes
    // the floor anchor, but the client hit test must cover the complete
    // animated portal.  Detect both endpoints so this also works for a
    // network-created portal where no local registry metadata is available.
    if (questWarp && isTownPortalEndpoint(sourceZoneHint, x, y, warp.dstLevel)) {
      box.asBox(-32, -64, 64, 96);
    }

    String name = Riiablo.string.lookup(warp.dstLevel.LevelWarp);

    IntIntMap substs = warp.substs;
    if (!questWarp && entry.LitVersion) {
      // FIXME: Below will cover overwhelming majority of cases -- need to solve act 5 ice cave case where 3 tiles are used
      //        I think this can be done by checking if there's a texture with the same id, else it's a floor warp
      if (subIndex < 2) {
        for (int i = 0; i < 2; i++) {
          substs.put(DT1.Tile.Index.create(orientation, mainIndex, i), DT1.Tile.Index.create(orientation, mainIndex, i + entry.Tiles));
        }
      } else {
        substs.put(DT1.Tile.Index.create(0, subIndex, 0), DT1.Tile.Index.create(0, subIndex, 4));
        substs.put(DT1.Tile.Index.create(0, subIndex, 1), DT1.Tile.Index.create(0, subIndex, 5));
        substs.put(DT1.Tile.Index.create(0, subIndex, 2), DT1.Tile.Index.create(0, subIndex, 6));
        substs.put(DT1.Tile.Index.create(0, subIndex, 3), DT1.Tile.Index.create(0, subIndex, 7));
      }
    }

    mBBoxWrapper.create(id).box = box;

    Label label = mLabel.create(id);
    label.offset.set(box.xMin + box.width / 2, -box.yMax + box.height / 2);
    label.actor = createLabel(name);
    label.actor.setUserObject(id);

    mSelectable.create(id);
    return id;
  }

  private boolean isTownPortalEndpoint(Map.Zone sourceZoneHint, float x, float y,
      com.riiablo.codec.excel.Levels.Entry destinationLevel) {
    Map.Zone sourceZone = sourceZoneHint != null ? sourceZoneHint : map.getZone(x, y);
    if (sourceZone != null && sourceZone.isTown()) return true;
    if (destinationLevel == null) return false;
    int destination = destinationLevel.Id;
    return destination == 1 || destination == 40 || destination == 75
        || destination == 103 || destination == 109;
  }

  @Override
  public int createItem(com.riiablo.item.Item item, float x, float y) {
    int id = super.createItem(item, x, y);
    Item itemWrapper = mItem.get(id);
    Riiablo.assets.load(itemWrapper.flippyDescriptor);
    /**
     * FIXME: at least some items appear to be about a half subtile too high after their drop
     *        animations finish -- is this expected? or some issue with offsets? It's happening with
     *        runes -- but keys are placed correctly and other items I've tried look fine.
     */
    itemWrapper.item.load();
    return id;
  }

  @Override
  public int createMissile(int missileId, Vector2 angle, Vector2 position) {
    return createMissile(missileId, angle, position, -1);
  }
  
  @Override
  public int createMissile(int missileId, Vector2 angle, Vector2 position, int ownerId) {
    int id = super.createMissile(missileId, angle, position, ownerId);
    // The client replica is registered by ClientNetworkReceiver with the
    // server entity id after creation. Do not expose the temporary -1 id to
    // NetworkIdManager or it will alias every local projectile.
    if (id != Engine.INVALID_ENTITY && mNetworked.has(id)) mNetworked.remove(id);
    Missile missileWrapper = mMissile.get(id);
    Riiablo.assets.load(missileWrapper.missileDescriptor);
    mBox2DBody.create(id);

    Missiles.Entry missile = mMissile.get(id).missile;
    if (!missile.TravelSound.isEmpty()) { // FIXME: how to handle this audio for aoe spell effects?
      // D2 plays TravelSound once when the missile is created.  Keep the
      // emitter on the missile so the normal distance attenuation applies;
      // MissileCollisionSystem separately emits HitSound on impact.
      if (Riiablo.audio != null && mSoundEmitter != null) {
        Audio.Instance travel = Riiablo.audio.play(missile.TravelSound, true);
        if (travel != null) {
          mSoundEmitter.create(id).set(travel, Interpolation.pow2OutInverse);
        }
      }
      com.badlogic.gdx.Gdx.app.log(TAG, String.format(
          "[MISSILE_SOUND] phase=travel missileId=%d missile=%s travelSound=%s",
          id, missile.Missile, missile.TravelSound));
    }

    return id;
  }

  /** Creates a local-only hit child without replaying its TravelSound. */
  public int createMissilePresentation(Missiles.Entry missile, Vector2 angle,
      Vector2 position) {
    if (missile == null) return Engine.INVALID_ENTITY;
    int id = super.createMissile(missile.Id, angle, position, -1);
    if (id == Engine.INVALID_ENTITY) return id;
    if (mNetworked.has(id)) mNetworked.remove(id);
    if (mMissile.has(id)) {
      mMissile.get(id).authoritative = false;
      mMissile.get(id).presentationOnly = true;
    }
    Riiablo.assets.load(mMissile.get(id).missileDescriptor);
    return id;
  }

  private static com.riiablo.widget.Label createLabel(String text) {
    com.riiablo.widget.Label label = new com.riiablo.widget.Label(Riiablo.fonts.font16);
    label.setAlignment(Align.center);
    label.getStyle().background = com.riiablo.widget.Label.MODAL;
    label.setText(text);
    return label;
  }
}
