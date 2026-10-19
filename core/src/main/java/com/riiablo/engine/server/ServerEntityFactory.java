package com.riiablo.engine.server;

import java.util.Arrays;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

import com.riiablo.Riiablo;
import com.riiablo.Files;
import com.riiablo.engine.server.ai.AI;
import com.riiablo.engine.server.ai.NecroPet;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.attributes.StatListRef;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.LvlWarp;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.codec.excel.MonPreset;
import com.riiablo.codec.excel.Objects;
import com.riiablo.codec.excel.Skills;
import com.riiablo.codec.COF;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AIWrapper;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofAlphas;
import com.riiablo.engine.server.component.CofComponents;
import com.riiablo.engine.server.component.Box2DBody;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.CofTransforms;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.Item;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.pet.PetType;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.NativeAiPathCache;
import com.riiablo.engine.server.component.MovementModes;
import com.riiablo.engine.server.component.Networked;
import com.riiablo.engine.server.component.Object;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.Size;
import com.riiablo.engine.server.component.SuperUnique;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.UnitLifecycle;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.Warp;
import com.riiablo.engine.server.component.ZoneAware;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.event.SkillStartEvent;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import net.mostlyoriginal.api.event.common.EventSystem;

public class ServerEntityFactory extends EntityFactory {
  private final java.util.IdentityHashMap<Map.Zone, com.d2moo.common.drlg.D2Seed> legacySpawnSeeds =
      new java.util.IdentityHashMap<>();
  static byte nativeMonsterWeaponClass(int resolvedClass) {
    // D2Composit.cpp COMPOSIT_GetWeaponClassIdFromCode returns native
    // class 0 (HTH) for an unknown code; riiablo's HTH index is 1.
    return resolvedClass >= Engine.WEAPON_NIL && resolvedClass <= Engine.WEAPON_HT2
        ? (byte) resolvedClass : Engine.WEAPON_HTH;
  }

  private static final String TAG = "ServerEntityFactory";
  private static final Logger log = LogManager.getLogger(ServerEntityFactory.class);

  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<Angle> mAngle;
  protected ComponentMapper<CofComponents> mCofComponents;
  protected ComponentMapper<CofAlphas> mCofAlphas;
  protected ComponentMapper<CofTransforms> mCofTransforms;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Object> mObject;
  protected ComponentMapper<Running> mRunning;
  protected ComponentMapper<Networked> mNetworked;
  protected ComponentMapper<MovementModes> mMovementModes;
  protected ComponentMapper<Size> mSize;
  protected ComponentMapper<ZoneAware> mZoneAware;
  protected ComponentMapper<Interactable> mInteractable;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<NativeUnitFlags> mNativeUnitFlags;
  protected ComponentMapper<NativeAiPathCache> mNativeAiPathCache;
  protected ComponentMapper<Warp> mWarp;
  protected ComponentMapper<Item> mItem;
  protected ComponentMapper<Missile> mMissile;
  protected ComponentMapper<AIWrapper> mAIWrapper;
  protected ComponentMapper<Casting> mCasting;
  protected ComponentMapper<Box2DBody> mBox2DBody;
  protected ComponentMapper<Corpse> mCorpse;
  protected ComponentMapper<Sequence> mSequence;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<UnitLifecycle> mLifecycle;
  protected ComponentMapper<SuperUnique> mSuperUnique;
  protected ComponentMapper<SummonedPet> mSummonedPet;

  private final Vector2 summonPosition = new Vector2();

  protected ObjectInteractor objectInteractor;
  protected WarpInteractor warpInteractor;
  protected ItemInteractor itemInteractor;
  @com.artemis.annotations.Wire(failOnNull = false)
  protected EventSystem events;

  @Override
  public int createPlayer(CharData charData, Vector2 position) {
    int id = super.createEntity(Class.Type.PLR, "player");
    mPlayer.create(id).data = charData;
    mAttributesWrapper.create(id).attrs = charData.getStats();
    mUnitStates.create(id).init(world, id);
    mMapWrapper.create(id).set(map, map.getZone(position));

    Position playerPosition = mPosition.create(id);
    playerPosition.invalidateNativePrecision();
    playerPosition.position.set(position);
    // CharStats stores native speed units; setPlayer converts to subtiles/sec.
    com.riiablo.codec.excel.CharStats.Entry charStats = charData.classId != null ? charData.classId.entry() : null;
    float walkSpeed = charStats != null && charStats.WalkVelocity > 0
        ? charStats.WalkVelocity : Engine.Player.SPEED_WALK;
    float runSpeed = charStats != null && charStats.RunVelocity > 0
        ? charStats.RunVelocity : Engine.Player.SPEED_RUN;
    log.info("[MOVEMENT] player={} nativeWalkSpeed={} nativeRunSpeed={} source={}",
        id, walkSpeed, runSpeed, charStats == null ? "fallback" : "CharStats");
    mVelocity.create(id).setPlayer(walkSpeed, runSpeed);
    mAngle.create(id);

    CofReference playerCof = mCofReference.create(id)
        .set(Engine.Player.getToken(charData.charClass), Class.Type.PLR.DEFAULT_MODE);
    // A headless D2GS has no PlayerItemHandler presentation lifecycle. Resolve
    // the equipped weapon class here so TH/SC attack animations use their
    // native keyframes and can dispatch authoritative missile creation.
    playerCof.wclass = PlayerItemHandler.resolveWeaponClass(charData.getItems());
    mCofComponents.create(id);
    mCofAlphas.create(id);
    mCofTransforms.create(id);

    mMovementModes.create(id).set(Engine.Player.MODE_TN, Engine.Player.MODE_TW, Engine.Player.MODE_RN);

    mSize.create(id).size = Size.MEDIUM;

    mRunning.create(id);
    mNetworked.create(id);
    mZoneAware.create(id);
    NativePlayerTargetSlots slots = world.getSystem(NativePlayerTargetSlots.class);
    if (slots != null) slots.registerPlayer(id);
    DynamicUnitCollisionSystem spawnCollision = world.getSystem(DynamicUnitCollisionSystem.class);
    if (spawnCollision != null) spawnCollision.reservePlayerSpawn(id);
    return id;
  }

  @Override
  public int createDynamicObject(int act, int monPresetId, float x, float y) {
    String objectType;
    try {
      MonPreset.Entry preset = files().MonPreset.get(act, monPresetId);
      objectType = preset != null ? preset.Place : null;
    } catch (RuntimeException ex) {
      log.error("[MONSTER_PLACEMENT] phase=resolve_failed act={} presetId={} reason={}",
          act, monPresetId, ex.toString());
      return Engine.INVALID_ENTITY;
    }
    log.info("[MONSTER_PLACEMENT] phase=resolve act={} presetId={} placement={} position=({}, {})",
        act, monPresetId, objectType, x, y);
    
    // 首先尝试从 MonStats 表查找（普通怪物）
    MonStats.Entry monstats = files().monstats.get(objectType);

    // D2Game's placement directives are not MonStats keys. Resolve the
    // single-monster directives to their native base rows before giving up;
    // group directives (unique/champion packs) remain a separate generator.
    if (monstats == null) monstats = resolvePlacementMonster(files(), objectType);
    
    // 如果找不到，尝试从 SuperUniques 表查找（超级暗金怪）
    com.riiablo.codec.excel.SuperUniques.Entry superUnique = null;
    if (monstats == null && files().SuperUniques != null) {
      superUnique = files().SuperUniques.get(objectType);
      if (superUnique != null) {
        // SuperUnique 的 MonClass 字段指向实际的 MonStats 记录
        monstats = files().monstats.get(superUnique.MonClass);
      }
    }
    
    if (monstats == null) {
      log.warn("[MONSTER_PLACEMENT] phase=failed act={} presetId={} placement={} reason=unresolved_monstats",
          act, monPresetId, objectType);
      return Engine.INVALID_ENTITY;
    }

    int id = createMonster(monstats.hcIdx, x, y);
    if (superUnique != null) {
      mSuperUnique.create(id).set(superUnique.hcIdx, superUnique.Superunique);
      mMonster.get(id).setRank(
          com.riiablo.engine.server.monster.MonsterRank.SUPER_UNIQUE, 0L, -1, -1);
    }
    log.info("[MONSTER_PLACEMENT] phase=created act={} presetId={} placement={} monster={} entity={}",
        act, monPresetId, objectType, monstats.Id, id);
    mNetworked.create(id);
    return id;
  }

  @Override
  public int createStaticObject(int act, int objId, float x, float y) {
    int objectType = files().obj.getObjectId(act, objId);
    return createStaticObjectBase(objectType, x, y);
  }

  @Override
  public int createStaticObjectByClassId(int objectId, float x, float y) {
    return createStaticObjectBase(objectId, x, y);
  }

  private int createStaticObjectBase(int objectId, float x, float y) {
    Objects.Entry base = files().objects.get(objectId);
    if (base == null) return Engine.INVALID_ENTITY;

    // Objects.txt's ClientFn=2 is the native client-side rain-ripple
    // callback.  Retail marks the four 1R..4R dummy rows themselves, while
    // map exports can carry the actual pool/river surface rows.  Give those
    // surface objects the same callback so the client can use their SizeX/Y
    // rectangle as the local ripple emission area.
    configureRainRippleClientFunction(base);

    int id = super.createEntity(Class.Type.OBJ, base.Description);
    Object object = mObject.create(id);
    object.base = base;

    // Town portal objects have no neutral (NU) TR sprite in the 1.10 MPQ.
    // They are spawned already open by D2Game, so starting them in NU makes
    // the client request the nonexistent TPTRLITNUhth.dc6 and crash while the
    // AssetManager is resolving the object animation. Mirror the native ON
    // state for both ordinary and permanent portal visuals.
    byte initialMode = (objectId == com.riiablo.engine.server.object.NativeQuestObjectResolver.TOWN_PORTAL
        || objectId == 60) ? Engine.Object.MODE_ON : Class.Type.OBJ.DEFAULT_MODE;
    object.mode = initialMode;

    Position objectPosition = mPosition.create(id);
    objectPosition.invalidateNativePrecision();
    objectPosition.position.set(x, y);
    mMapWrapper.create(id).set(map, map.getZone(x, y));

    if (base.Draw) {
      // D2Common COMPOSIT_GetCof uses ObjType, not Objects.Token.
      mCofReference.create(id).set(files().ObjType.get(base.Id).Token, initialMode);
      int[] component = mCofComponents.create(id).component;
      Arrays.fill(component, CofComponents.COMPONENT_NULL);
      mCofAlphas.create(id);
      mCofTransforms.create(id);
    }

    boolean waypoint = isWaypointObject(base);
    float operateRange = resolveObjectInteractionRange(base);
    if (operateRange > 0) {
      // Native D2 treats waypoint OperateFn 23 as an operable object. Keep a
      // conservative fallback so incomplete table mode flags cannot create a
      // visible but inert waypoint.
      mInteractable.create(id).set(operateRange, objectInteractor);
      if (waypoint) {
        log.info("Waypoint server entity wired: entity={} object={} operateFn={} range={} "
                + "position=({}, {})",
            id, base.Id, base.OperateFn, operateRange, x, y);
      }
    }

    mSize.create(id); // single size doesn't make any sense in this case because this is a rect
    mNetworked.create(id);
    return id;
  }

  /** Resolves MonPreset.Place directives that spawn one concrete monster. */
  public static MonStats.Entry resolvePlacementMonster(String placement) {
    return resolvePlacementMonster(Riiablo.files, placement);
  }

  private static MonStats.Entry resolvePlacementMonster(Files files, String placement) {
    if (files == null) return null;
    if (placement == null) return null;
    String normalized = placement.trim().toLowerCase(java.util.Locale.ROOT);
    String monsterId;
    switch (normalized) {
      case "place_fallen":
        monsterId = "fallen1";
        break;
      case "place_fallenshaman":
        monsterId = "fallenshaman1";
        break;
      case "place_bloodraven":
        monsterId = "bloodraven";
        break;
      default:
        return null;
    }
    return files.monstats.get(monsterId);
  }

  protected static boolean isWaypointObject(Objects.Entry base) {
    return base != null
        && (base.SubClass & Engine.Object.SUBCLASS_WAYPOINT)
            == Engine.Object.SUBCLASS_WAYPOINT;
  }

  protected static float resolveObjectInteractionRange(Objects.Entry base) {
    if (base == null) return 0;
    // Town portals are dynamic objects with a deliberately small/omitted
    // OperateRange in some converted Objects.txt tables.  Native D2 treats
    // the portal like a chest: clicking its sprite selects it and the player
    // walks into a generous interaction radius before warping.
    if (base.Id == com.riiablo.engine.server.object.NativeQuestObjectResolver.TOWN_PORTAL
        || base.Id == 60) return 5f;
    if (isWaypointObject(base)) return base.OperateRange > 0 ? base.OperateRange : 5f;
    if (!supportsObjectInteraction(base)) return 0;
    if (base.OperateFn == 11 || base.OperateFn == 13) {
      // D2Game applies InitFunction08 before nSelectable and leaves these
      // torches in ON. Do not use the incomplete-table fallback below: the
      // current mode must explicitly be selectable or the torch is scenery.
      int mode = base.InitFn == 8 ? Engine.Object.MODE_ON : Engine.Object.MODE_NU;
      return base.OperateRange > 0
          && base.Selectable != null
          && mode < base.Selectable.length
          && base.Selectable[mode] ? base.OperateRange : 0f;
    }
    // Native Objects.cpp sets targetability from Selectable[currentMode].
    // All-zero flags are an explicit denial, not an incomplete-table signal.
    // Doors retain their existing reversible-door interaction exception.
    if (base.Selectable != null && !ArrayUtils.contains(base.Selectable, true)
        && !base.IsDoor) return 0;
    if (base.OperateRange > 0 && ArrayUtils.contains(base.Selectable, true)) {
      return base.OperateRange;
    }

    // A number of native Objects.txt rows have a valid D2Game OperateFn but
    // omit OperateRange/Selectable in converted tables.  D2Game still creates
    // an interactable unit for these rows (doors, chests, shrines and quest
    // switches).  Keep invisible/non-operable rows untouched and provide a
    // conservative fallback only for drawable objects with an operate fn.
    if (base.Draw && base.OperateFn > 0 && base.OperateFn != 23) return 3f;
    return 0;
  }

  /**
   * Only exposes operations that currently have an authoritative Java path.
   * Treating every nonzero OperateFn as usable gives unsupported scenery
   * objects a name label and makes clicks walk to them without an effect.
   */
  static boolean supportsObjectInteraction(Objects.Entry base) {
    if (base == null) return false;
    if (base.Id == com.riiablo.engine.server.object.NativeQuestObjectResolver.TOWN_PORTAL
        || base.Id == 60
        || isWaypointObject(base)
        || base.OperateFn == 32) return true;
    return com.riiablo.engine.server.object.NativeObjectOperateTable.resolve(
        base, com.riiablo.map.NativePresetObjectResolver.Kind.ORDINARY)
        != com.riiablo.engine.server.object.NativeObjectOperateTable.Lifecycle.NONE;
  }

  @Override
  public int createMonster(int monsterId, float x, float y) {
    return createMonster(monsterId, x, y, 0, 0L, -1, -1);
  }

  /**
   * Creates a monster while preserving the native quality context used by
   * D2Game's drop pipeline. Existing callers keep the normal-rank overload.
   */
  @Override
  public int createMonster(int monsterId, float x, float y,
      int rank, long affixes, int championType, int uniqueId) {
    return initializeMonster(Engine.INVALID_ENTITY, monsterId, x, y,
        rank, affixes, championType, uniqueId);
  }

  @Override
  public int createMonsterAtIntegerSelection(int monsterId, int x, int y,
      int rank, long affixes, int championType, int uniqueId) {
    int id = createMonster(monsterId, (float) x, (float) y,
        rank, affixes, championType, uniqueId);
    if (id == Engine.INVALID_ENTITY) return id;
    Position position = mPosition.get(id);
    // Preserve legacy allocation success outside the raw domain. A changed
    // final landing is not authority for the original integer selection.
    if (x >= 0 && x <= 0xFFFF && y >= 0 && y <= 0xFFFF
        && position != null && position.position.x == x && position.position.y == y) {
      position.initializeNativeCell(x, y);
    }
    return id;
  }

  @Override
  public int createMonsterChild(int sourceId, int monsterId, int mode, float x, float y,
      int searchRadius, boolean replaceSource, int unitFlags) {
    if (map == null || !mMonster.has(sourceId) || !mMapWrapper.has(sourceId) || !mPosition.has(sourceId)
        || mode < 0 || mode >= 16 || searchRadius < 0 || searchRadius > 12
        || !Float.isFinite(x) || !Float.isFinite(y)) return Engine.INVALID_ENTITY;
    MapWrapper source = mMapWrapper.get(sourceId);
    Map.Zone zone = source.zone;
    MonStats.Entry row = files().monstats.get(monsterId);
    MonStats2.Entry dimensions = row == null ? null : files().monstats2.get(row.MonStatsEx);
    if (zone == null || zone.isTown() || dimensions == null) return Engine.INVALID_ENTITY;
    int size = Math.max(1, dimensions.SizeX);
    DynamicUnitCollisionSystem collision = world.getSystem(DynamicUnitCollisionSystem.class);
    if (collision == null) return Engine.INVALID_ENTITY;
    int centerX = Map.round(x), centerY = Map.round(y);
    Vector2 sourcePosition = mPosition.get(sourceId).position;
    Map.RoomEx room = zone.findRoomEx(sourcePosition.x, sourcePosition.y);
    // Legacy/synthetic maps have no native room stream. Keep that fallback
    // separate; never seed a real room stream from a monster or global RNG.
    com.d2moo.common.drlg.D2Seed seed = room != null ? room.runtimeSeed()
        : legacySpawnSeeds.computeIfAbsent(zone, ignored -> new com.d2moo.common.drlg.D2Seed(0, 666));
    int mask = NativeMonsterSpawnSearch.collisionMask(dimensions.spawnCol);
    final int[] selected = new int[2];
    boolean found;
    if (NativeMonsterSpawnSearch.usesSpecialFloor(monsterId, dimensions.spawnCol)) {
      found = NativeMonsterSpawnSearch.searchSpecialFloor(seed, room == null ? null : room.nativeSpawnRoom(),
          (px, py, probeSize, probeMask) -> collision.isFreeForMonsterSpawnMask(
              replaceSource ? sourceId : Engine.INVALID_ENTITY, zone, px, py, probeSize, probeMask),
          (px, py) -> { selected[0] = px; selected[1] = py; return true; });
    } else {
      found = NativeMonsterSpawnSearch.search(seed, centerX, centerY, searchRadius, (px, py) -> {
        if (px == 0 || py == 0 || !zone.contains(px, py)
            || room != null && !room.contains(px, py)
            || !zone.areRoomsAdjacent(sourcePosition.x, sourcePosition.y, px, py)
            || !collision.isFreeForMonsterSpawnMask(
                replaceSource ? sourceId : Engine.INVALID_ENTITY, zone, px, py, size, mask)) return false;
        selected[0] = px; selected[1] = py;
        return true;
      });
    }
    if (!found || selected[0] == 0 || selected[1] == 0 || !zone.contains(selected[0], selected[1])
        || !zone.areRoomsAdjacent(sourcePosition.x, sourcePosition.y, selected[0], selected[1]))
      return Engine.INVALID_ENTITY;
    // Native selects once, then allocates once; allocation failure must not
    // resume the search or consume another ring's RNG.
    int id = createMonster(monsterId, selected[0], selected[1]);
    if (id == Engine.INVALID_ENTITY) return id;
    applyNativeUnitFlags(id, unitFlags);
    if (mode != Engine.Monster.MODE_NU) {
      mSequence.create(id).sequence((byte) mode, Engine.Monster.MODE_NU);
      CofManager cofs = world.getSystem(CofManager.class);
      if (cofs != null) cofs.setMode(id, (byte) mode, true);
    }
    collision.reserveMonsterSpawn(id, selected[0], selected[1], size);
    return id;
  }

  @Override public boolean reinitializeMonster(int entityId, int monsterId, int mode) {
    if (!mMonster.has(entityId) || !mPosition.has(entityId) || mode < 0 || mode >= 16) return false;
    MonStats.Entry row = files().monstats.get(monsterId);
    if (row == null || !row.enabled) return false;
    Vector2 position = mPosition.get(entityId).position;
    int rebuilt = initializeMonster(entityId, monsterId, position.x, position.y,
        0, 0L, -1, -1);
    if (rebuilt < 0) return false;
    CofManager cofs = world.getSystem(CofManager.class);
    if (cofs != null) cofs.setMode(entityId, (byte) mode, true);
    else mCofReference.get(entityId).mode = (byte) mode;
    if (mLifecycle != null && mLifecycle.has(entityId)) mLifecycle.get(entityId).revive();
    com.riiablo.map.Box2DPhysics physics = world.getSystem(com.riiablo.map.Box2DPhysics.class);
    if (physics != null) physics.rebuildMonsterBody(entityId);
    DynamicUnitCollisionSystem collisions = world.getSystem(DynamicUnitCollisionSystem.class);
    if (collisions != null) {
      collisions.refreshNow();
      reserveRestoredMonsterCollision(entityId, collisions);
    }
    return true;
  }

  private int initializeMonster(int existingId, int monsterId, float x, float y,
      int rank, long affixes, int championType, int uniqueId) {
    MonStats.Entry monstats = files().monstats.get(monsterId);
    if (monstats == null) {
      log.error("[MONSTER_NATIVE_STATS] monster row missing: id={}", monsterId);
      return Engine.INVALID_ENTITY;
    }
    MonStats2.Entry monstats2 = files().monstats2.get(monstats.MonStatsEx);
    if (monstats2 == null) {
      log.error("[MONSTER_NATIVE_STATS] MonStats2 row missing: monster={} row={}",
          monstats.Id, monstats.MonStatsEx);
      return Engine.INVALID_ENTITY;
    }

    // Direct map and quest spawns only know the MonStats id. Preserve native
    // story-boss quality when no explicit MonsterSpawner rank was supplied.
    // SetBoss is *not* a quality flag: D2MOO uses it for boss/minion ownership
    // setup (MonsterSpawn.cpp), and ordinary Fallen/Skeleton rows commonly set
    // it. Promoting SetBoss rows to SUPER_UNIQUE selects TreasureClass3 and
    // causes every normal monster to roll the unique multi-pick table.
    rank = normalizeNativeRank(rank, monstats);

    Map.Zone zone = map.getZone(x, y);
    int difficulty = map.getDifficulty();
    int gameType = 1; // This fork currently runs the LoD ruleset.
    int baseMonsterLevel = MonsterStatsCalculator.resolveMonsterLevel(
        monstats, zone == null ? null : zone.level, difficulty, true);

    MonsterStatsCalculator.MonsterStatsInit statsInit =
        new MonsterStatsCalculator.MonsterStatsInit();
    MonsterStatsCalculator.MonsterStatsInit attack1Init =
        new MonsterStatsCalculator.MonsterStatsInit();
    MonsterStatsCalculator.MonsterStatsInit attack2Init =
        new MonsterStatsCalculator.MonsterStatsInit();
    boolean calculated = MonsterStatsCalculator.calculateMonsterStatsByLevel(files(),
        monsterId, gameType, difficulty, baseMonsterLevel, (short) 7, statsInit)
        && MonsterStatsCalculator.calculateMonsterStatsByLevel(files(),
            monsterId, gameType, difficulty, baseMonsterLevel, (short) 8, attack1Init)
        && MonsterStatsCalculator.calculateMonsterStatsByLevel(files(),
            monsterId, gameType, difficulty, baseMonsterLevel, (short) 0x10, attack2Init);
    if (!calculated) {
      log.error("[MONSTER_NATIVE_STATS] calculation failed: monster={} level={} difficulty={}",
          monstats.Id, baseMonsterLevel, difficulty);
      return Engine.INVALID_ENTITY;
    }

    int connectedPlayers = world.getAspectSubscriptionManager()
        .get(Aspect.all(Player.class)).getEntities().size();
    int playerCount = MonsterStatsCalculator.nativePlayerCount(monstats, connectedPlayers);
    int combatMultiplier = MonsterStatsCalculator.nativeCombatMultiplier(
        monstats, difficulty, playerCount, gameType != 0);
    MonsterStatsCalculator.applyNativeCombatMultiplier(attack1Init, combatMultiplier);
    MonsterStatsCalculator.applyNativeCombatMultiplier(attack2Init, combatMultiplier);
    int hpBonus = MonsterStatsCalculator.nativeHpBonus(playerCount);
    int expBonus = MonsterStatsCalculator.nativeExperienceBonus(playerCount);
    int minHp = Math.min(statsInit.minHP, statsInit.maxHP);
    int maxHp = Math.max(statsInit.minHP, statsInit.maxHP);
    int baseHp = minHp == maxHp ? minHp : MathUtils.random(minHp, maxHp);
    int hitpoints = Math.min(
        baseHp + percentage(baseHp, hpBonus, 100), (1 << 23) - 1);
    hitpoints = MonsterStatsCalculator.nativeRankHitpoints(hitpoints, rank, difficulty);
    int experience = statsInit.Exp + percentage(statsInit.Exp, expBonus, 100);
    experience = MonsterStatsCalculator.nativeRankExperience(experience, rank);
    int monsterLevel = baseMonsterLevel
        + MonsterStatsCalculator.nativeRankLevelBonus(rank);

    int id = existingId == Engine.INVALID_ENTITY
        ? super.createEntity(Class.Type.MON, monstats.Id) : existingId;
    if (existingId != Engine.INVALID_ENTITY) {
      DynamicUnitCollisionSystem collision = world.getSystem(DynamicUnitCollisionSystem.class);
      if (collision != null) collision.releaseNativeCollision(id);
      NativeTargetNodes.get(world, id).detach();
      if (mUnitStates.has(id) && mUnitStates.get(id).stateList != null)
        mUnitStates.get(id).stateList.discardAll();
      mCasting.remove(id);
      mSequence.remove(id);
      mRunning.remove(id);
      mCorpse.remove(id);
      mInteractable.remove(id);
      mSuperUnique.remove(id);
      world.getMapper(com.riiablo.engine.server.component.Pathfind.class).remove(id);
      world.getMapper(com.riiablo.engine.server.component.NativeAiCommand.class).remove(id);
      world.getMapper(com.riiablo.engine.server.component.NativeAiState.class).remove(id);
      mNativeAiPathCache.remove(id);
      mClassname.create(id).classname = monstats.Id;
    }
    // D2MOO stores town/transition NPCs in UNIT_MONSTER, but the native
    // MonStats NPC flag keeps them out of the monster faction and combat AI.
    // Flavie at the Blood Moor -> Cold Plains transition is the visible case.
    NativeUnitFlags nativeFlags = mNativeUnitFlags.create(id).reset();
    if (!monstats.npc) nativeFlags.set(NativeUnitFlags.MONSTER_TARGET);
    Monster monster = mMonster.create(id).set(monstats, monstats2)
        .setRank(rank, affixes, championType, uniqueId);
    if ("BaalCrab".equals(monstats.AI) || "BaalCrabClone".equals(monstats.AI)) {
      // PATH_AllocDynamicPath is zero-allocated. AI replacement is NOT a new
      // allocation, so initialize here rather than in BaalCrab.initialize().
      mNativeAiPathCache.create(id).allocated();
    }
    monster.rngState = NativeRng.forUnit(gameSeed(), id).state();
    monster.setAttack2Profile(attack2Init.A2MinD, attack2Init.A2MaxD, attack2Init.TH);

    Attributes attrs = existingId != Engine.INVALID_ENTITY && mAttributesWrapper.has(id)
        ? mAttributesWrapper.get(id).attrs : Attributes.obtainStandard();
    StatListRef base = attrs.base();
    base.clear();
    base.put(Stat.level, monsterLevel);
    base.put(Stat.monster_playercount, playerCount);
    base.put(Stat.damageresist, arrayValue(monstats.ResDm, difficulty));
    base.put(Stat.magicresist, arrayValue(monstats.ResMa, difficulty));
    base.put(Stat.fireresist, arrayValue(monstats.ResFi, difficulty));
    base.put(Stat.lightresist, arrayValue(monstats.ResLi, difficulty));
    base.put(Stat.coldresist, arrayValue(monstats.ResCo, difficulty));
    base.put(Stat.poisonresist, arrayValue(monstats.ResPo, difficulty));
    base.put(Stat.toblock, arrayValue(monstats.ToBlock, difficulty));
    base.put(Stat.attackrate, 100);
    base.put(Stat.velocitypercent, 75);
    base.put(Stat.other_animrate, 100);
    base.put(Stat.last_sent_hp_pct, 128);
    base.put(Stat.hitpoints, (float) hitpoints);
    base.put(Stat.maxhp, (float) hitpoints);
    base.put(Stat.armorclass, statsInit.AC);
    base.put(Stat.experience, experience);
    base.putEncoded(Stat.hpregen,
        (int) ((((long) hitpoints << 8) * monstats.DamageRegen) >> 12));
    base.put(Stat.tohit, attack1Init.TH);
    base.put(Stat.mindamage, attack1Init.A1MinD);
    base.put(Stat.maxdamage, attack1Init.A1MaxD);
    // Monster ToBlock is only eligible for the native shield branch when the
    // monster presentation actually carries a shield (or the native row
    // explicitly opts out of the no-shield gate).  Do not let a bare ToBlock
    // value make every monster block player attacks and emit a BL sound.
    boolean monsterShield = monstats.NoShldBlock;
    if (!monsterShield && monstats2 != null && monstats2.ComponentV != null
        && monstats2.ComponentV.length > 7) {
      String shieldComponent = monstats2.ComponentV[7];
      monsterShield = shieldComponent != null && !shieldComponent.trim().isEmpty();
    }
    if (rank == com.riiablo.engine.server.monster.MonsterRank.CHAMPION
        || com.riiablo.engine.server.monster.MonsterRank.isUnique(rank)) {
      // MONSTERUNIQUE_UMod2_HealthBonus disables regeneration on the unique
      // leader after replacing its maximum/current life.
      base.put(Stat.hpregen, 0);
    }
    MonsterStatsCalculator.applyNativeAffixStats(files(), base, monstats, difficulty, affixes);
    attrs.reset();
    attrs.setCombatBlockContext(monsterShield, 0);
    mAttributesWrapper.create(id).attrs = attrs;
    mUnitStates.create(id).init(world, id);
    log.debug("[MONSTER_NATIVE_STATS] entity={} monster={} level={} baseLevel={} "
            + "difficulty={} players={} hp={} baseHpRange={}..{} exp={} "
            + "a1={}..{} ar={} a2={}..{} ar={} combatMultiplier={}",
        id, monstats.Id, monsterLevel, baseMonsterLevel, difficulty, playerCount,
        hitpoints, statsInit.minHP, statsInit.maxHP, experience,
        attack1Init.A1MinD, attack1Init.A1MaxD, attack1Init.TH,
        attack2Init.A2MinD, attack2Init.A2MaxD, attack2Init.TH,
        combatMultiplier);

    Position spawnPosition = mPosition.create(id);
    // Reinitialization may reuse this component at the identical float point.
    // A legacy allocation/rebuild does not establish native low words.
    spawnPosition.invalidateNativePrecision();
    spawnPosition.position.set(x, y);
    // Monsters created by map/quest generators must carry their owning zone,
    // just like players and objects. Without this, quest systems cannot see
    // the generated boss and clients may resolve the wrong level at a boundary.
    mMapWrapper.create(id).set(map, zone);
    monster.setSpawnAnchor(zone, x, y);
    // AITHINK_Fn127_NpcBarb joins the independent target list at slot 8.
    if ("NpcBarb".equals(monstats.AI)) {
      NativeTargetNodes.get(world, id).insert(
          com.riiablo.engine.server.component.NativeTargetNode.ALLIED, -1);
    }
    // D2Common UNITS_GetBaseVelocity always uses MonStats.Velocity for
    // monsters. MonStats.Run only participates in run-animation-rate table
    // generation; treating it as displacement speed makes animation and
    // authoritative movement diverge.
    mVelocity.create(id).setMonsterFromNativeTable(monstats.Velocity);
    mVelocity.get(id).baseSpeedMultiplier = attrs.getValue(Stat.velocitypercent, 75) / 100f;
    mAngle.create(id);

    CofReference reference = mCofReference.create(id);
    if (existingId != Engine.INVALID_ENTITY) reference.clearVisualOverride();
    // D2 COF table uses MonStats.Code (abbreviation like "FA"), not Id (full name like "fallen1")
    reference.token  = monstats.Code;
    reference.mode   = monstats.spawnmode.isEmpty() ? Engine.Monster.MODE_NU : (byte) files().MonMode.index(monstats.spawnmode);
    int weaponClass = files().WeaponClass.index(monstats2.BaseW);
    reference.wclass = nativeMonsterWeaponClass(weaponClass);
    if (weaponClass < Engine.WEAPON_NIL || weaponClass > Engine.WEAPON_HT2) {
      log.info("[MONSTER_ANIMATION] monster={} BaseW={} resolved=HTH reason=unknown_base_weapon",
          monstats.Id, monstats2.BaseW);
    }
    int[] component = mCofComponents.create(id).component;
    if (existingId != Engine.INVALID_ENTITY) Arrays.fill(component, CofComponents.COMPONENT_NIL);
    boolean hasComponentValue = false;
    for (byte i = 0; i < monstats2.ComponentV.length; i++) {
      String ComponentV = monstats2.ComponentV[i];
      if (!ComponentV.isEmpty()) {
        hasComponentValue = true;
        String[] v = StringUtils.remove(ComponentV, '"').split(",");
        int random = MathUtils.random(0, v.length - 1);
        component[i] = files().compcode.index(v[random]);
      }
    }
    // A few native summon rows (most notably Valkyrie/Dopplezon) leave
    // ComponentV completely empty even though their COF contains a mandatory
    // TR body layer. D2Common resolves that omitted standalone body to LIT;
    // keeping it as NIL makes CofLayerCacher clear the only layer and leaves
    // an ECS monster with valid AI/physics but no visible sprite. Optional
    // equipment layers remain NIL.
    if (!hasComponentValue && component[COF.Component.TR] == CofComponents.COMPONENT_NIL) {
      component[COF.Component.TR] = CofComponents.COMPONENT_LIT;
      log.debug("[MONSTER_PRESENTATION] entity={} monster={} defaultTR=LIT reason=empty_ComponentV",
          id, monstats.Id);
    }

    mCofAlphas.create(id);
    CofTransforms transforms = mCofTransforms.create(id);
    if (existingId != Engine.INVALID_ENTITY) {
      Arrays.fill(mCofAlphas.get(id).alpha, CofAlphas.ALPHA_NULL);
      Arrays.fill(transforms.transform, CofTransforms.TRANSFORM_NULL);
    }
    // MonStats variants share a Code/model and select their native colours
    // through TransLvl and the matching monster palshift.dat. palshift.dat's
    // first two rows are reserved; TransLvl 0 uses row 2.
    byte monsterPaletteRow = CofTransforms.monsterPaletteRow(monstats.TransLvl);
    if (monsterPaletteRow != CofTransforms.TRANSFORM_NULL) {
      for (int c = 0; c < transforms.transform.length; c++) {
        transforms.transform[c] = monsterPaletteRow;
      }
    }

    mMovementModes.create(id).set(Engine.Monster.MODE_NU, Engine.Monster.MODE_WL, Engine.Monster.MODE_RN);

    float size = mSize.create(id).size = monstats2.SizeX; // FIXME: SizeX and SizeY appear to always be equal -- is this method sufficient?
    AIWrapper aiWrapper = mAIWrapper.create(id);
    boolean wildernessGuide = monstats.hcIdx == MonsterType.NAVI;
    // Flavie is an NPC for faction/dialogue purposes, but D2Game assigns her
    // a defensive wilderness AI. The table's generic Npc AI only patrols and
    // never acquires hostile monsters, so use the dedicated bridge here.
    boolean prison = monstats.hcIdx >= 340 && monstats.hcIdx <= 343
        && monstats.Id.equals("boneprison" + (monstats.hcIdx - 339));
    AI ai = prison ? new com.riiablo.engine.server.ai.BonePrison(id) : wildernessGuide
        ? new com.riiablo.engine.server.ai.Navi(id)
        : monstats.npc ? AI.IDLE : aiWrapper.findAI(id, monstats.AI).ai;
    if (wildernessGuide || prison) aiWrapper.ai = ai;
    if (prison) nativeFlags.set(NativeUnitFlags.NO_TREASURE_CLASS);
    if (monstats.npc && !wildernessGuide) aiWrapper.ai = ai;
    world.getInjector().inject(ai);
    ai.initialize();
    if (monstats.interact) {
      mInteractable.create(id).set(size, ai);
    }

    DynamicUnitCollisionSystem spawnCollision = world.getSystem(DynamicUnitCollisionSystem.class);
    if (spawnCollision != null) spawnCollision.registerMonsterSpawnMarkers(id);
    mNetworked.create(id);
    return id;
  }

  @Override
  public int createSummonedPet(int ownerId, MonStats.Entry summon, String petType,
      int skillId, int skillLevel, int petMax, boolean passive, int durationFrames,
      float x, float y) {
    if (ownerId < 0 || summon == null || petType == null || petType.isEmpty()
        || !mPlayer.has(ownerId)) {
      log.warn("[SUMMON_PET] phase=reject owner={} summon={} petType={} reason=invalid_request",
          ownerId, summon != null ? summon.Id : "null", petType);
      return Engine.INVALID_ENTITY;
    }

    String nativePetType = PetType.canonical(petType);
    int maximum = Math.max(1, petMax);
    com.artemis.utils.IntBag pets = world.getAspectSubscriptionManager()
        .get(Aspect.all(SummonedPet.class)).getEntities();
    int matching = 0;
    com.badlogic.gdx.utils.IntArray matchingIds = new com.badlogic.gdx.utils.IntArray();
    com.badlogic.gdx.utils.IntArray conflictingGroupIds =
        new com.badlogic.gdx.utils.IntArray();
    for (int i = 0; i < pets.size(); i++) {
      SummonedPet pet = mSummonedPet.get(pets.get(i));
      if (pet == null || pet.ownerId != ownerId) continue;
      if (PetType.sameNativeType(nativePetType, pet.petType)) {
        if (pet.unsummonPending) continue;
        matching++;
        matchingIds.add(pets.get(i));
      } else if (PetType.sameNativeList(nativePetType, pet.petType)) {
        if (pet.unsummonPending) continue;
        // D2MOO sub_6FC7D7A0 removes every pet in another PetType row from
        // the same non-zero group. It does not retain old Spirit Wolves up to
        // the new Dire Wolf limit.
        conflictingGroupIds.add(pets.get(i));
      }
    }

    MonStats2.Entry summonStats2 = files().monstats2.get(summon.MonStatsEx);
    int footprint = summonStats2 != null ? Math.max(1, summonStats2.SizeX) : 1;
    summonPosition.set(x, y);
    Map.Zone zone = map != null ? map.getZone(x, y) : null;
    if (zone == null && mMapWrapper.has(ownerId)) {
      zone = mMapWrapper.get(ownerId).zone;
      if (mPosition.has(ownerId)) summonPosition.set(mPosition.get(ownerId).position);
    }
    if (zone == null || !zone.findFreeCoordinates(
        summonPosition, footprint, 8, true, summonPosition)) {
      log.warn("[SUMMON_PET] phase=reject owner={} summon={} petType={} "
              + "position=({}, {}) reason=no_free_position",
          ownerId, summon.Id, petType, x, y);
      return Engine.INVALID_ENTITY;
    }

    int entityId = createMonster(summon.hcIdx, summonPosition.x, summonPosition.y);
    if (entityId == Engine.INVALID_ENTITY) return entityId;
    mSummonedPet.create(entityId).bindNativeTargetNode(NativeTargetNodes.get(world, entityId))
        .set(ownerId, nativePetType, skillId, skillLevel,
        passive, durationFrames);
    mNativeUnitFlags.get(entityId).reset().set(NativeUnitFlags.PLAYER_SUMMON);
    SummonedPetSystem petSystem = world.getSystem(SummonedPetSystem.class);
    for (int i = 0; i < conflictingGroupIds.size; i++) {
      int oldId = conflictingGroupIds.get(i);
      if (petSystem == null || !petSystem.beginUnsummon(oldId, "group_replaced")) {
        world.delete(oldId);
      }
      log.info("[SUMMON_PET] phase=replace_group owner={} petType={} oldEntity={} newEntity={}",
          ownerId, nativePetType, oldId, entityId);
    }
    int replacements = Math.max(0, matching + 1 - maximum);
    // D2MOO removes the head of the native pet list.  Entity iteration order
    // is not a stable contract, so explicitly sort by the insertion sequence
    // recorded on each SummonedPet component and evict the oldest first.
    for (int i = 0; i < replacements; i++) {
      int oldest = Engine.INVALID_ENTITY;
      long oldestOrder = Long.MAX_VALUE;
      for (int j = 0; j < matchingIds.size; j++) {
        int candidate = matchingIds.get(j);
        if (!world.getEntityManager().isActive(candidate) || !mSummonedPet.has(candidate)) continue;
        SummonedPet candidatePet = mSummonedPet.get(candidate);
        if (candidatePet == null || candidatePet.unsummonPending) continue;
        if (candidatePet.spawnOrder < oldestOrder) {
          oldest = candidate;
          oldestOrder = candidatePet.spawnOrder;
        }
      }
      if (oldest == Engine.INVALID_ENTITY) break;
      if (petSystem == null || !petSystem.beginUnsummon(oldest, "quota_replaced")) {
        world.delete(oldest);
      }
      log.info("[SUMMON_PET] phase=replace owner={} petType={} oldEntity={} newEntity={} "
              + "spawnOrder={}",
          ownerId, nativePetType, oldest, entityId, oldestOrder);
    }
    log.info("[SUMMON_PET] phase=created owner={} entity={} summon={} petType={} "
            + "skill={} level={} max={} passive={} duration={} position=({}, {})",
        ownerId, entityId, summon.Id, nativePetType, skillId, skillLevel, maximum,
        passive, durationFrames, summonPosition.x, summonPosition.y);
    return entityId;
  }

  @Override
  public int createDiabPrisonSegment(int monsterId, int x, int y) {
    // This exact-cell allocation is the source of new precision, not a
    // reconstruction of an existing unit's floating-point position.
    if (x <= 0 || x > 0xFFFF || y <= 0 || y > 0xFFFF) return Engine.INVALID_ENTITY;
    Map.Zone zone = map == null ? null : map.getZone(x, y);
    MonStats.Entry row = files().monstats.get(monsterId);
    if (row == null || monsterId < 340 || monsterId > 343 || x == 0 || y == 0 || zone == null || zone.isTown()
        || !isStaticFootprintFree(zone, x, y, 1)) return Engine.INVALID_ENTITY;
    DynamicUnitCollisionSystem collision = world.getSystem(DynamicUnitCollisionSystem.class);
    if (collision != null && !collision.isFreeForMonsterSpawn(zone, x, y, 1))
      return Engine.INVALID_ENTITY;
    int id = createMonster(monsterId, x, y);
    if (id < 0) return id;
    mPosition.get(id).initializeNativeCell(x, y);
    mSequence.create(id).sequence(Engine.Monster.MODE_S1, Engine.Monster.MODE_NU);
    if (collision != null) collision.reserveMonsterSpawn(id, x, y, 1);
    return id;
  }

  /** Native Bone Wall/Bone Prison segments use exact coordinates and no PetType limit. */
  @Override
  public int createBoneWallSegment(int ownerId, MonStats.Entry summon,
      int skillId, int skillLevel, int durationFrames, float x, float y) {
    if (ownerId < 0 || summon == null || !mPlayer.has(ownerId) || map == null) {
      log.warn("[NECRO_BONE_WALL] phase=segment_reject owner={} summon={} "
              + "position=({}, {}) reason=invalid_request",
          ownerId, summon != null ? summon.Id : "null", x, y);
      return Engine.INVALID_ENTITY;
    }
    MonStats2.Entry stats2 = files().monstats2.get(summon.MonStatsEx);
    Map.Zone zone = map.getZone(x, y);
    int footprint = stats2 != null ? Math.max(1, stats2.SizeX) : 1;
    if (zone == null || zone.isTown() || !isStaticFootprintFree(zone, x, y, footprint)) {
      log.debug("[NECRO_BONE_WALL] phase=segment_reject owner={} summon={} "
              + "position=({}, {}) footprint={} reason=town_or_static_collision",
          ownerId, summon.Id, x, y, footprint);
      return Engine.INVALID_ENTITY;
    }

    int entityId = createMonster(summon.hcIdx, MathUtils.round(x), MathUtils.round(y));
    if (entityId == Engine.INVALID_ENTITY) return entityId;
    mSummonedPet.create(entityId).bindNativeTargetNode(NativeTargetNodes.get(world, entityId))
        .set(ownerId, "bonewall", skillId, skillLevel,
        true, durationFrames).boneWall = true;
    mNativeUnitFlags.get(entityId).reset().set(NativeUnitFlags.PLAYER_SUMMON);
    if (mVelocity.has(entityId)) mVelocity.get(entityId).velocity.setZero();
    log.info("[NECRO_BONE_WALL] phase=segment_created owner={} entity={} skill={} "
            + "level={} duration={} position=({}, {}) footprint={}",
        ownerId, entityId, skillId, skillLevel, durationFrames,
        MathUtils.round(x), MathUtils.round(y), footprint);
    return entityId;
  }

  /** Unit overlap is legal for the native wall rings; only terrain is tested here. */
  static boolean isStaticFootprintFree(Map.Zone zone, float x, float y, int unitSize) {
    if (zone == null) return false;
    int centerX = MathUtils.round(x);
    int centerY = MathUtils.round(y);
    int radius = Math.max(0, unitSize - 1);
    Map.RoomEx centerRoom = zone.findRoomEx(centerX, centerY);
    if (!zone.getRoomsEx().isEmpty() && centerRoom == null) return false;
    for (int dy = -radius; dy <= radius; dy++) {
      for (int dx = -radius; dx <= radius; dx++) {
        int worldX = centerX + dx;
        int worldY = centerY + dy;
        if (worldX < zone.x() || worldY < zone.y()
            || worldX >= zone.x() + zone.width()
            || worldY >= zone.y() + zone.height()) return false;
        if ((zone.staticFlags(worldX - zone.x(), worldY - zone.y())
            & DT1.Tile.FLAG_BLOCK_WALK) != 0) return false;
        if (!zone.getRoomsEx().isEmpty()) {
          Map.RoomEx room = zone.findRoomEx(worldX, worldY);
          if (room == null || centerRoom != null && room != centerRoom
              && !centerRoom.isAdjacentTo(room.id)) return false;
        }
      }
    }
    return true;
  }

  /** Applies only rank information encoded by the monster row itself. */
  static int normalizeNativeRank(int requestedRank, MonStats.Entry monstats) {
    if (requestedRank == com.riiablo.engine.server.monster.MonsterRank.NORMAL
        && monstats != null && (monstats.boss || monstats.primeevil)) {
      return com.riiablo.engine.server.monster.MonsterRank.BOSS;
    }
    return requestedRank;
  }

  private static int arrayValue(int[] values, int index) {
    return values != null && index >= 0 && index < values.length
        ? values[index] : 0;
  }

  private static int percentage(int value, int multiplier, int divisor) {
    return divisor == 0 ? 0 : (int) ((long) value * multiplier / divisor);
  }

  private static int statInt(Attributes attrs, short stat) {
    StatRef ref = attrs != null ? attrs.get(stat, StatRef.obtain()) : null;
    return ref != null ? ref.asInt() : 0;
  }

  @Override
  public boolean resurrectMonster(int monsterId, int sourceId) {
    return restoreMonster(monsterId, sourceId, false);
  }

  @Override
  public boolean reviveMonster(int monsterId, int ownerId) {
    return restoreMonster(monsterId, ownerId, true);
  }

  private boolean restoreMonster(int monsterId, int sourceId, boolean playerRevive) {
    boolean hasMonster = mMonster.has(monsterId);
    boolean hasAttributes = mAttributesWrapper.has(monsterId);
    boolean hasPosition = mPosition.has(monsterId);
    if (!hasMonster || !hasAttributes || !hasPosition) {
      log.warn("[MONSTER_RAISE] phase=reject source={} target={} "
              + "reason=structural_components monster={} attributes={} position={}",
          sourceId, monsterId, hasMonster, hasAttributes, hasPosition);
      return false;
    }

    Monster monster = mMonster.get(monsterId);
    if (monster.shatteredAtDeath) {
      log.warn("[MONSTER_RAISE] phase=reject source={} target={} reason=shattered_at_death",
          sourceId, monsterId);
      return false;
    }
    Attributes attrs = mAttributesWrapper.get(monsterId).attrs;
    StatRef hitpoints = attrs != null ? attrs.get(Stat.hitpoints, StatRef.obtain()) : null;
    StatRef maxhp = attrs != null ? attrs.get(Stat.maxhp, StatRef.obtain()) : null;
    if (hitpoints == null || maxhp == null) {
      log.warn("[MONSTER_RAISE] phase=reject source={} target={} reason=vitals_missing hp={} maxhp={}",
          sourceId, monsterId, hitpoints != null, maxhp != null);
      return false;
    }
    if (hitpoints.asFixed() > 0f) {
      // Native SrvDo097 returns 0 when a second animation keyframe reaches an
      // already resurrected target. This is an expected idempotent rejection,
      // not evidence that the corpse entity lost structural components.
      log.debug("[MONSTER_RAISE] phase=reject source={} target={} reason=already_alive hp={}",
          sourceId, monsterId, hitpoints.asFixed());
      return false;
    }
    if (!mCorpse.has(monsterId)) {
      log.warn("[MONSTER_RAISE] phase=reject source={} target={} reason=corpse_marker_missing",
          sourceId, monsterId);
      return false;
    }

    Corpse corpse = mCorpse.get(monsterId);
    if (!corpse.usable || corpse.fading || monster.monstats == null || monster.monstats2 == null) {
      log.warn("[MONSTER_RAISE] phase=reject source={} target={} reason=unusable_corpse "
              + "usable={} fading={} monsterData={}",
          sourceId, monsterId, corpse.usable, corpse.fading,
          monster.monstats != null && monster.monstats2 != null);
      return false;
    }
    Position corpsePosition = mPosition.get(monsterId);
    if (map != null && (map.flags(corpsePosition.position) & DT1.Tile.FLAG_BLOCK_WALK) != 0) {
      log.debug("[MONSTER_RAISE] phase=reject source={} target={} reason=blocked_position "
              + "position=({}, {})",
          sourceId, monsterId, corpsePosition.position.x, corpsePosition.position.y);
      return false;
    }

    // Reserve the corpse before restoring life so another Shaman processed in
    // the same frame cannot start a second authoritative resurrection.
    corpse.usable = false;
    hitpoints.set(Math.max(1f, maxhp.asFixed()));
    applyNativeUnitFlags(monsterId, NativeUnitFlags.MONSTER_RESURRECTION);
    if (mUnitStates.has(monsterId) && mUnitStates.get(monsterId).stateList != null) {
      mUnitStates.get(monsterId).stateList.clearAll();
    }

    mCorpse.remove(monsterId);
    monster.nativeCorpseCollision = false;
    monster.nativeRestoredCollisionPattern = true;
    monster.frogNoPresence = false;
    monster.frogSmallPresence = false;
    monster.frogSpawnMarkerInitialized = false;
    monster.frogSpawnMarkerMoved = false;
    // D2MOO's SKILLS_ResurrectUnit restores a retained corpse as a live,
    // targetable monster. Reopen the authoritative lifecycle as well, or the
    // next death event is incorrectly treated as a duplicate of the old death.
    if (mLifecycle != null && mLifecycle.has(monsterId)) {
      mLifecycle.get(monsterId).revive();
    }
    mRunning.remove(monsterId);
    mVelocity.create(monsterId).setMonsterFromNativeTable(monster.monstats.Velocity)
        .stateMovementLocked = false;
    mMovementModes.create(monsterId).set(
        Engine.Monster.MODE_NU, Engine.Monster.MODE_WL, Engine.Monster.MODE_RN);

    mCasting.remove(monsterId);
    mSequence.remove(monsterId);
    AI ai = restoredMonsterAi(monsterId, monster.monstats, playerRevive);
    monster.setResurrected(sourceId, playerRevive);
    mAIWrapper.create(monsterId).ai = ai;
    world.getInjector().inject(ai);
    ai.initialize();
    if (monster.monstats.interact && mSize.has(monsterId)) {
      mInteractable.create(monsterId).set(mSize.get(monsterId).size, ai);
    }

    int resurrectMode = Engine.Monster.MODE_NU;
    String configuredSkill = "";
    if (playerRevive) {
      // Native SrvDo058 enters neutral immediately and switches to
      // AISPECIALSTATE_REVIVED. It must not replay the monster's own
      // ResurrectSkill (used by self-resurrecting monsters and Shamans).
      if (mCofReference.has(monsterId)) {
        mCofReference.get(monsterId).mode = Engine.Monster.MODE_NU;
      }
    } else {
      String configuredMode = monster.monstats2.ResurrectMode;
      if (configuredMode != null && !configuredMode.isEmpty()) {
        int mode = files().MonMode.index(configuredMode);
        if (mode >= 0 && mode < 16) resurrectMode = mode;
      }
      mSequence.create(monsterId).sequence((byte) resurrectMode, Engine.Monster.MODE_NU);

      configuredSkill = monster.monstats2.ResurrectSkill;
      Skills.Entry resurrectSkill = configuredSkill == null || configuredSkill.isEmpty()
          ? null : files().skills.get(configuredSkill);
      if (resurrectSkill != null) {
        Vector2 position = mPosition.get(monsterId).position;
        mCasting.create(monsterId).set(
            resurrectSkill.Id, monsterId, position);
        if (events != null) {
          events.dispatch(SkillStartEvent.obtain(
              monsterId, resurrectSkill.Id, monsterId, position.cpy(),
              resurrectSkill.srvstfunc, resurrectSkill.cltstfunc));
        } else {
          log.warn("[MONSTER_SELF_RESURRECT] phase=event_bus_missing entity={} skill={} srvStFunc={}",
              monsterId, configuredSkill, resurrectSkill.srvstfunc);
        }
      } else if (configuredSkill != null && !configuredSkill.isEmpty()) {
        log.warn("[MONSTER_SELF_RESURRECT] phase=skill_lookup_failed entity={} skill={}",
            monsterId, configuredSkill);
      }
    }

    DynamicUnitCollisionSystem restoredCollision = world.getSystem(DynamicUnitCollisionSystem.class);
    if (restoredCollision != null) reserveRestoredMonsterCollision(monsterId, restoredCollision);
    log.info("[MONSTER_RAISE] phase=restored source={} target={} monster={} hp={} "
            + "mode={} resurrectSkill={} resurrected={} resurrectedBy={} playerRevive={} "
            + "ai={} position=({}, {})",
        sourceId, monsterId, monster.monstats.Id, hitpoints.asFixed(), resurrectMode,
        configuredSkill, monster.resurrected, monster.resurrectedBy, playerRevive,
        ai.getClass().getSimpleName(),
        mPosition.get(monsterId).position.x, mPosition.get(monsterId).position.y);
    return true;
  }

  static AI restoredMonsterAi(int monsterId, MonStats.Entry monstats, boolean playerRevive) {
    return playerRevive
        ? new NecroPet(monsterId)
        : AI.findAI(monsterId, monstats != null ? monstats.AI : null);
  }

  private void reserveRestoredMonsterCollision(int id, DynamicUnitCollisionSystem collisions) {
    Vector2 position = mPosition.get(id).position;
    collisions.reserveMonsterSpawn(id, Map.round(position.x), Map.round(position.y),
        mSize.has(id) ? mSize.get(id).size : 1);
  }

  @Override
  public boolean selfResurrectMonster(int monsterId) {
    if (!mMonster.has(monsterId) || !mAttributesWrapper.has(monsterId)
        || !mPosition.has(monsterId)) {
      log.warn("[MONSTER_SELF_RESURRECT] phase=reject entity={} reason=missing_components",
          monsterId);
      return false;
    }
    Attributes attrs = mAttributesWrapper.get(monsterId).attrs;
    StatRef hp = attrs == null ? null : attrs.get(Stat.hitpoints, StatRef.obtain());
    StatRef maxHp = attrs == null ? null : attrs.get(Stat.maxhp, StatRef.obtain());
    if (hp == null || maxHp == null || maxHp.asFixed() <= 0f) {
      log.warn("[MONSTER_SELF_RESURRECT] phase=reject entity={} reason=vitals_missing",
          monsterId);
      return false;
    }
    if (hp.asFixed() <= 0f) {
      // Keep the native skill entry point complete for scripted callers that
      // begin directly from a corpse rather than an already restored unit.
      return resurrectMonster(monsterId, monsterId);
    }

    hp.set(Math.max(1f, maxHp.asFixed()));
    NativeUnitFlags flags = mNativeUnitFlags.has(monsterId)
        ? mNativeUnitFlags.get(monsterId)
        : mNativeUnitFlags.create(monsterId).reset();
    flags.markSelfResurrection();
    log.info("[MONSTER_SELF_RESURRECT] phase=applied entity={} hp={} flags=0x{}",
        monsterId, hp.asFixed(), Integer.toHexString(flags.flags()));
    return true;
  }

  @Override
  public void finishSelfResurrection(int monsterId) {
    if (!mMonster.has(monsterId) || !mAttributesWrapper.has(monsterId)
        || !mNativeUnitFlags.has(monsterId)) return;
    Attributes attrs = mAttributesWrapper.get(monsterId).attrs;
    StatRef hp = attrs == null ? null : attrs.get(Stat.hitpoints, StatRef.obtain());
    NativeUnitFlags flags = mNativeUnitFlags.get(monsterId);
    if (hp == null || hp.asFixed() <= 0f
        || !flags.has(NativeUnitFlags.NO_RESURRECTION_REWARD)) {
      log.debug("[MONSTER_SELF_RESURRECT] phase=animation_complete_ignored entity={} hp={} flags=0x{}",
          monsterId, hp != null ? hp.asFixed() : -1f, Integer.toHexString(flags.flags()));
      return;
    }
    applyNativeUnitFlags(monsterId, NativeUnitFlags.MONSTER_TARGET);
    log.info("[MONSTER_SELF_RESURRECT] phase=animation_complete entity={} flags=0x{}",
        monsterId, Integer.toHexString(mNativeUnitFlags.get(monsterId).flags()));
  }

  @Override
  public boolean submergeMonster(int monsterId) {
    if (!mMonster.has(monsterId) || !mNativeUnitFlags.has(monsterId)) return false;
    NativeUnitFlags flags = mNativeUnitFlags.get(monsterId);
    flags.clear(NativeUnitFlags.MONSTER_TARGET);
    if (mVelocity.has(monsterId)) mVelocity.get(monsterId).velocity.setZero();
    if (mBox2DBody.has(monsterId) && mBox2DBody.get(monsterId).body != null) {
      mBox2DBody.get(monsterId).body.setActive(false);
    }
    log.info("[MONSTER_SUBMERGE] phase=applied entity={} flags=0x{}",
        monsterId, Integer.toHexString(flags.flags()));
    return true;
  }

  @Override
  public boolean emergeMonster(int monsterId) {
    if (!mMonster.has(monsterId) || !mNativeUnitFlags.has(monsterId)) return false;
    NativeUnitFlags flags = mNativeUnitFlags.get(monsterId);
    flags.set(NativeUnitFlags.MONSTER_TARGET);
    if (mBox2DBody.has(monsterId) && mBox2DBody.get(monsterId).body != null) {
      mBox2DBody.get(monsterId).body.setActive(true);
    }
    log.info("[MONSTER_EMERGE] phase=applied entity={} flags=0x{}",
        monsterId, Integer.toHexString(flags.flags()));
    return true;
  }

  @Override
  public boolean enterFrogHidden(int monsterId) {
    if (!submergeMonster(monsterId)) return false;
    Monster monster = mMonster.get(monsterId);
    int previousSize = monster.frogSmallPresence ? 1
        : mSize.has(monsterId) ? mSize.get(monsterId).size : 1;
    monster.frogSpawnMarkerInitialized = previousSize >= 1 && previousSize <= 2
        && mPosition.has(monsterId);
    monster.frogSpawnMarkerMoved = false;
    if (monster.frogSpawnMarkerInitialized) {
      Vector2 position = mPosition.get(monsterId).position;
      monster.frogSpawnMarkerX = Map.round(position.x);
      monster.frogSpawnMarkerY = Map.round(position.y);
    }
    monster.frogNoPresence = true;
    monster.frogSmallPresence = true;
    DynamicUnitCollisionSystem collisions = world.getSystem(DynamicUnitCollisionSystem.class);
    if (collisions != null) collisions.refreshNow();
    return true;
  }

  @Override
  public boolean restoreFrogPresence(int monsterId) {
    if (!mMonster.has(monsterId) || !mNativeUnitFlags.has(monsterId)) return false;
    // Native helper sets target flags even when its landing/pattern test fails.
    mNativeUnitFlags.get(monsterId).set(NativeUnitFlags.MONSTER_TARGET);
    Monster monster = mMonster.get(monsterId);
    int size = mSize.has(monsterId) ? mSize.get(monsterId).size : 1;
    if (!monster.frogNoPresence && (monster.frogSmallPresence || size < 3)) return false;
    MapWrapper wrapper = mMapWrapper.get(monsterId);
    if (wrapper == null || wrapper.zone == null || !mPosition.has(monsterId)) return false;
    Map.Zone zone = wrapper.zone;
    Vector2 position = mPosition.get(monsterId).position;
    int originX = Map.round(position.x), originY = Map.round(position.y);
    DynamicUnitCollisionSystem collisions = world.getSystem(DynamicUnitCollisionSystem.class);
    if (collisions != null) collisions.refreshNow();
    Vector2 candidate = new Vector2(), result = new Vector2(), best = new Vector2();
    // Projection of GetFreeCoordinates(mask=0x1C09, radius=50, increment=1).
    // FALSE rejects an unsuccessful search, not valid neighboring rooms:
    // the native implementation still calls GetRoomBySubTileCoordinates.
    for (int radius = 0; radius < 50; radius++) {
      int bestDistance = Integer.MAX_VALUE;
      for (int dy = -radius; dy <= radius; dy++) {
        for (int dx = -radius; dx <= radius; dx++) {
          if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) continue;
          int x = originX + dx, y = originY + dy;
          if (!zone.areRoomsAdjacent(originX, originY, x, y)) continue;
          if (!zone.findFreeCoordinates(candidate.set(x, y), size, 0,
              DT1.Tile.FLAG_BLOCK_WALK | DT1.Tile.FLAG_BLOCK_PLAYER_WALK, true, result)) continue;
          boolean sameRoom = true;
          int footprintRadius = size >= 2 ? 1 : 0;
          for (int fy = -footprintRadius; fy <= footprintRadius; fy++) {
            for (int fx = -footprintRadius; fx <= footprintRadius; fx++) {
              if (size == 2 && fx != 0 && fy != 0) continue;
              if (!zone.areRoomsAdjacent(originX, originY, x + fx, y + fy)) sameRoom = false;
            }
          }
          if (!sameRoom || collisions != null
              && !collisions.isFreeForFrogEmergence(monsterId, x, y, size)) continue;
          int distance = Math.abs(dx) + Math.abs(dy);
          if (distance < bestDistance) { bestDistance = distance; best.set(result); }
        }
      }
      if (bestDistance == Integer.MAX_VALUE) continue;
      mPosition.get(monsterId).invalidateNativePrecision();
      position.set(best);
      monster.frogNoPresence = false;
      monster.frogSmallPresence = true;
      if (mBox2DBody.has(monsterId) && mBox2DBody.get(monsterId).body != null) {
        mBox2DBody.get(monsterId).body.setTransform(position, 0f);
        mBox2DBody.get(monsterId).body.setLinearVelocity(0f, 0f);
        mBox2DBody.get(monsterId).body.setActive(true);
      }
      if (collisions != null) collisions.refreshNow();
      log.info("[FROG_PRESENCE] entity={} from=({}, {}) to={} pattern=small", monsterId,
          originX, originY, position);
      return true;
    }
    return false;
  }

  @Override
  public void applyNativeUnitFlags(int entityId, int flags) {
    if (entityId == Engine.INVALID_ENTITY || flags == 0) return;
    NativeUnitFlags unitFlags = mNativeUnitFlags.has(entityId)
        ? mNativeUnitFlags.get(entityId)
        : mNativeUnitFlags.create(entityId).reset();
    unitFlags.set(flags);
  }

  @Override
  public int createWarp(int index, float x, float y) {
    return createWarp(null, index, x, y);
  }

  @Override
  public int createWarp(Map.Zone sourceZoneHint, int index, float x, float y) {
    if (com.riiablo.engine.server.quest.QuestWarp.isQuestWarp(index)) {
      int destination = com.riiablo.engine.server.quest.QuestWarp.destinationLevelId(index);
      Map.Zone zone = sourceZoneHint != null ? sourceZoneHint : map.getZone(x, y);
      Levels.Entry destinationLevel = files().Levels.get(destination);
      LvlWarp.Entry portalBounds = neutralWarpBounds();
      if (zone == null || destinationLevel == null || portalBounds == null) {
        log.error("[QUEST_WARP] creation failed: sourceZone={} destination={} bounds={}",
            zone, destination, portalBounds);
        return Engine.INVALID_ENTITY;
      }
      int id = super.createEntity(Class.Type.WRP, "quest-warp");
      mWarp.create(id).set(index, portalBounds, destinationLevel);
      mMapWrapper.create(id).set(map, zone);
      Position questWarpPosition = mPosition.create(id);
      questWarpPosition.invalidateNativePrecision();
      questWarpPosition.position.set(x, y);
      mInteractable.create(id).set(3.0f, warpInteractor);
      mNetworked.create(id);
      log.info("[QUEST_WARP] created: entity={} source={} destination={} position=({}, {})",
          id, zone.level.Id, destination, x, y);
      return id;
    }
    final int mainIndex   = DT1.Tile.Index.mainIndex(index);
    final int subIndex    = DT1.Tile.Index.subIndex(index);
    final int orientation = DT1.Tile.Index.orientation(index);

    // Act I native zones may overlap in world coordinates. Resolve the
    // source from the exact DS1 Warp marker first; coordinate-only lookup can
    // otherwise classify Blood Moor's Den entrance as a town exit and create
    // a 2->2 self-warp instead of the intended 2->8 transition.
    Map.Zone zone = sourceZoneHint != null
        ? sourceZoneHint : map.findWarpSourceZone(index, x, y);
    int dstFromOverride = zone == null || zone.level == null
        ? -1 : map.getWarpDestinationOverride(zone.level.Id, mainIndex);
    if (dstFromOverride <= 0 && map != null && sourceZoneHint == null) {
      // Coordinates of synthetic/outdoor markers can be outside the strict
      // rectangle (or overlap an earlier quest branch). Prefer the Zone that
      // owns an explicit runtime destination for this logical slot, choosing
      // the nearest rectangle when several levels reuse the slot number.
      Map.Zone best = null;
      long bestDistance = Long.MAX_VALUE;
      for (int zi = 0; zi < map.getZones().size; zi++) {
        Map.Zone candidate = map.getZones().get(zi);
        if (candidate == null || candidate.level == null) continue;
        int candidateDestination = map.getWarpDestinationOverride(candidate.level.Id, mainIndex);
        if (candidateDestination <= 0) continue;
        int cx = Math.max(candidate.x(), Math.min(Math.round(x), candidate.x() + candidate.width() - 1));
        int cy = Math.max(candidate.y(), Math.min(Math.round(y), candidate.y() + candidate.height() - 1));
        long dx = Math.round(x) - cx, dy = Math.round(y) - cy;
        long distance = dx * dx + dy * dy;
        if (distance < bestDistance) {
          bestDistance = distance;
          best = candidate;
          dstFromOverride = candidateDestination;
        }
      }
      if (best != null && best != zone) {
        zone = best;
        Gdx.app.log(TAG, "[WARP] runtime slot owner resolved: level=" + zone.level.Id
            + " mainIndex=" + mainIndex + " destination=" + dstFromOverride);
      }
    }
    if (zone == null || zone.level == null) {
      Gdx.app.error(TAG, String.format("[WARP] source zone missing index=0x%08X pos=(%.1f,%.1f)", index, x, y));
      log.warn("[WARP] creation failed: sourceZone={} index={} pos=({}, {})",
          zone == null ? "null" : zone.level, index, x, y);
      return Engine.INVALID_ENTITY;
    }
    int dst = dstFromOverride;
    if (dst <= 0) {
      if (zone.level.Vis == null || mainIndex < 0 || mainIndex >= zone.level.Vis.length) {
        return Engine.INVALID_ENTITY;
      }
      dst = zone.level.Vis[mainIndex];
    }
    // 调试：Rogue Encampment 所有 warp 打日志
    if (zone.level.Id == 1) {
      Gdx.app.log(TAG, "createWarp RogueEnc: mainIndex=" + mainIndex + " dstFromOverride=" + dstFromOverride + " dstFromVis=" + zone.level.Vis[mainIndex] + " finalDst=" + dst);
    }
    // Act1 城镇出口：Rogue Encampment(1) 任意 warp 若指向 Cold Plains(3) 均改为 Blood Moor(2)
    if (zone.level.Id == 1 && dst == 3) {
      Gdx.app.log(TAG, "Warp fallback: RogueEnc dst ColdPlains(3)->BloodMoor(2) mainIndex=" + mainIndex);
      dst = 2;
    }
    assert dst > 0 : "Warp to unknown level!";
    int wrp = zone.level.Warp != null && mainIndex >= 0
        && mainIndex < zone.level.Warp.length ? zone.level.Warp[mainIndex] : -1;
    // D2Common can append a runtime link even when a reduced Levels.txt
    // export has no native LvlWarp index for that slot.  The Java map builder
    // emits a synthetic marker and destination override in that case; use
    // the neutral portal bounds entry while retaining the authoritative
    // destination level.
    if (wrp < 0 && dstFromOverride > 0) wrp = 0;
    if (wrp < 0) {
      Gdx.app.error(TAG, String.format("[WARP] warp row missing source=%d mainIndex=%d dst=%d override=%d",
          zone.level.Id, mainIndex, dst, dstFromOverride));
      return Engine.INVALID_ENTITY;
    }

    Levels.Entry dstLevel = files().Levels.get(dst);
    if (dstLevel == null) {
      Gdx.app.error(TAG, String.format("[WARP] destination level missing source=%d mainIndex=%d dst=%d override=%d index=0x%08X",
          zone.level.Id, mainIndex, dst, dstFromOverride, index));
      log.error("[WARP] destination level missing: source={} mainIndex={} dst={} override={} index=0x{}",
          zone.level.Id, mainIndex, dst, dstFromOverride, Integer.toHexString(index));
      return Engine.INVALID_ENTITY;
    }

    LvlWarp.Entry warp = files().LvlWarp.get(wrp);
    if (warp == null && dstFromOverride > 0) warp = neutralWarpBounds();
    if (warp == null) {
      // LvlWarp entry not found, skip creating warp
      log.error("[WARP] bounds missing: source={} mainIndex={} dst={} wrp={} override={} index=0x{}",
          zone.level.Id, mainIndex, dst, wrp, dstFromOverride, Integer.toHexString(index));
      return Engine.INVALID_ENTITY;
    }

    int id = super.createEntity(Class.Type.WRP, "warp");
    mWarp.create(id).set(index, warp, dstLevel);
    mMapWrapper.create(id).set(map, zone);

    Position warpPosition = mPosition.create(id);
    warpPosition.invalidateNativePrecision();
    warpPosition.position.set(x, y).add(warp.OffsetX, warp.OffsetY);

    mInteractable.create(id).set(3.0f, warpInteractor);
    mNetworked.create(id);
    return id;
  }

  @Override
  public int createItem(com.riiablo.item.Item item, float x, float y) {
    int id = super.createEntity(Class.Type.ITM, "item");
    // An ECS item entity created at world coordinates is always the native
    // ground representation.  Items moved out of the character data are
    // cleared to null by ItemData.drop(), so preserve the authoritative
    // location explicitly for network snapshots and client presentation.
    if (item != null) item.location = com.riiablo.item.Location.GROUND;
    com.riiablo.engine.server.item.GroundDropOwnership.created(id);
    mItem.create(id).set(item);

    Position itemPosition = mPosition.create(id);
    itemPosition.invalidateNativePrecision();
    itemPosition.position.set(x, y);
    // Ground items are native room units too. Without MapWrapper they fall
    // through NetworkSynchronizer's legacy broadcast path and leak to every
    // client/level instead of following RoomEx visibility and persistence.
    mMapWrapper.create(id).set(map, map == null ? null : map.getZone(x, y));
    mInteractable.create(id).set(1f, itemInteractor);
    mNetworked.create(id);
    DynamicUnitCollisionSystem collisions = world.getSystem(DynamicUnitCollisionSystem.class);
    if (collisions != null) collisions.reserveNativeCollision(id);
    return id;
  }

  /** Returns a usable LvlWarp bounds record even when a reduced table omits
   * the conventional id 0 row used by quest portals and synthetic markers. */
  private LvlWarp.Entry neutralWarpBounds() {
    if (files() == null || files().LvlWarp == null) return null;
    LvlWarp.Entry entry = files().LvlWarp.get(0);
    if (entry != null) return entry;
    for (LvlWarp.Entry candidate : files().LvlWarp) {
      if (candidate != null) return candidate;
    }
    return null;
  }

  @Override
  public int createMissile(int missileId, Vector2 angle, Vector2 position) {
    return createMissile(missileId, angle, position, -1);
  }
  
  /**
   * 创建导弹（带拥有者 ID）
   * @param missileId 导弹类型 ID
   * @param angle 方向向量
   * @param position 起始位置
   * @param ownerId 拥有者实体 ID（-1 表示无拥有者）
   * @return 创建的实体 ID
   */
  public int createMissile(int missileId, Vector2 angle, Vector2 position, int ownerId) {
    int ownerLevel = ownerId >= 0 && mAttributesWrapper.has(ownerId)
        ? statInt(mAttributesWrapper.get(ownerId).attrs, Stat.level) : 1;
    return createMissile(missileId, angle, position, ownerId, ownerLevel);
  }

  @Override
  public int createMissile(Missiles.Entry missile, Vector2 angle, Vector2 position,
      int ownerId, int skillLevel) {
    if (missile == null) return Engine.INVALID_ENTITY;
    return createMissile(missile.Id, angle, position, ownerId, skillLevel);
  }

  private int createMissile(int missileId, Vector2 angle, Vector2 position,
      int ownerId, int damageLevel) {
    Missiles.Entry missile = files().Missiles.get(missileId);
    int id = super.createEntity(Class.Type.MIS, missile.Missile);
    com.riiablo.engine.server.component.Missile missileComponent = mMissile.create(id);
    missileComponent.set(missile, position, missile.Range).setOwner(ownerId);
    if (com.riiablo.engine.server.component.Missile.hasNativeFrameLifetime(missile)
        || missile.Vel == 0) {
      missileComponent.nativeLifetimeFrames =
          com.riiablo.engine.server.component.Missile.nativeRangeFrames(missile,
              damageLevel);
    }
    missileComponent.rngState = NativeRng.forUnit(gameSeed(), id).state();

    Attributes ownerAttrs = ownerId >= 0 && mAttributesWrapper.has(ownerId)
        ? mAttributesWrapper.get(ownerId).attrs : null;
    Monster ownerMonster = ownerId >= 0 && mMonster.has(ownerId)
        ? mMonster.get(ownerId) : null;
    int ownerMode = ownerId >= 0 && mCofReference.has(ownerId)
        ? mCofReference.get(ownerId).mode : -1;
    MissileDamageResolver.initialize(files(), missileComponent, ownerAttrs, ownerMonster,
        ownerMode, Math.max(1, damageLevel), 0,
        ownerId >= 0 && mUnitStates.has(ownerId) ? mUnitStates.get(ownerId).stateList : null,
        // Hireling/equipped-weapon coefficients retain the legacy creation policy.
        ownerId < 0 || !world.getMapper(Mercenary.class).has(ownerId));

    Position missilePosition = mPosition.create(id);
    missilePosition.invalidateNativePrecision();
    missilePosition.position.set(position);
    MapWrapper ownerMap = ownerId >= 0 && mMapWrapper.has(ownerId)
        ? mMapWrapper.get(ownerId) : null;
    Map.Zone missileZone = ownerMap != null && ownerMap.zone != null
        && ownerMap.zone.findRoomEx(position.x, position.y) != null
        ? ownerMap.zone : map.getZone(position);
    mMapWrapper.create(id).set(map, missileZone);
    Map.RoomEx missileRoom = missileZone != null
        ? missileZone.findRoomEx(position.x, position.y) : null;
    missileComponent.roomId = missileRoom != null ? missileRoom.id : -1;
    float velocity = missile.Vel;
    int slowPercent = slowMissileVelocityPercent(ownerId, missile);
    if (slowPercent > 0) velocity = velocity * slowPercent / 100f;
    mVelocity.create(id).velocity.set(angle).setLength(velocity);
    missileComponent.acceleration.initialize(missile.Accel, missile.MaxVel);
    if (missile.pSrvDoFunc == 20 || missile.pSrvDoFunc == 21) {
      missileComponent.attached = true;
      missileComponent.attachedEntityId = ownerId;
    }
    mAngle.create(id).set(angle);
    mSize.create(id).size = Size.SMALL;
    DynamicUnitCollisionSystem spawnCollision = world.getSystem(DynamicUnitCollisionSystem.class);
    if (spawnCollision != null) spawnCollision.reserveMissileSpawn(id);
    // Missiles are authoritative network entities. Their deletion is then
    // announced by NetworkSynchronizer when collision/range removes them.
    mNetworked.create(id);
    
    com.riiablo.logger.Logger log = com.riiablo.logger.LogManager.getLogger(ServerEntityFactory.class);
    log.debug("Created missile {} with ownerId={}, range={}, pos=({}, {}), asset={}", 
        id, ownerId, missile.Range, position.x, position.y, 
        missileComponent.missileDescriptor != null ? missileComponent.missileDescriptor.fileName : "null");
    return id;
  }

  static void configureRainRippleClientFunction(Objects.Entry base) {
    if (base == null || base.ClientFn == 2) return;
    // 1R..4R are visual products and must retain their table value.  The
    // ordinary Act-I river rows and finite pool rows are the surface emitters
    // when a map places them as objects.  Blood pools are deliberately not
    // water, despite sharing the word "pool" in their description.
    if (base.Id >= 40 && base.Id <= 44
        || base.Id == 65 || base.Id == 66
        || (base.Description != null
            && base.Description.toLowerCase(java.util.Locale.ROOT).contains("pool")
            && !base.Description.toLowerCase(java.util.Locale.ROOT).contains("blood"))) {
      base.ClientFn = 2;
    }
  }

  /**
   * D2MOO Missiles.cpp applies slowmissiles when a monster projectile is
   * created, not while it is already in flight.  The state carries the native
   * STAT_SKILL_HANDOFATHENA percentage; CanSlow remains a per-missile gate.
   */
  private int slowMissileVelocityPercent(int ownerId, Missiles.Entry missile) {
    if (ownerId < 0 || !mMonster.has(ownerId) || !mUnitStates.has(ownerId)) return 0;
    UnitStates unitStates = mUnitStates.get(ownerId);
    return slowMissileVelocityPercent(missile,
        unitStates != null ? unitStates.stateList : null);
  }

  static int slowMissileVelocityPercent(Missiles.Entry missile, StateList states) {
    if (missile == null || !missile.CanSlow || states == null
        || !states.hasState(StateId.SLOWMISSILES)) return 0;
    int percent = states.getTotalStatContribution(Stat.skill_handofathena);
    return Math.max(0, Math.min(100, percent));
  }
}
