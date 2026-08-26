package com.riiablo.engine.server;

import java.util.Arrays;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.artemis.ComponentMapper;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

import com.riiablo.Riiablo;
import com.riiablo.engine.server.ai.AI;
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
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AIWrapper;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofAlphas;
import com.riiablo.engine.server.component.CofComponents;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.CofTransforms;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.Item;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.MovementModes;
import com.riiablo.engine.server.component.Networked;
import com.riiablo.engine.server.component.Object;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.Size;
import com.riiablo.engine.server.component.SuperUnique;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.Warp;
import com.riiablo.engine.server.component.ZoneAware;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

public class ServerEntityFactory extends EntityFactory {
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
  protected ComponentMapper<Warp> mWarp;
  protected ComponentMapper<Item> mItem;
  protected ComponentMapper<Missile> mMissile;
  protected ComponentMapper<AIWrapper> mAIWrapper;
  protected ComponentMapper<Corpse> mCorpse;
  protected ComponentMapper<Sequence> mSequence;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<SuperUnique> mSuperUnique;

  protected ObjectInteractor objectInteractor;
  protected WarpInteractor warpInteractor;
  protected ItemInteractor itemInteractor;

  @Override
  public int createPlayer(CharData charData, Vector2 position) {
    int id = super.createEntity(Class.Type.PLR, "player");
    mPlayer.create(id).data = charData;
    mAttributesWrapper.create(id).attrs = charData.getStats();
    mUnitStates.create(id).init(id);
    mMapWrapper.create(id).set(map, map.getZone(position));

    mPosition.create(id).position.set(position);
    // D2MOD: UNITS_GetRunAndWalkSpeedForPlayer reads from CharStatsTxt.nWalkSpeed and nRunSpeed
    // These values are in units that need to be converted to actual speed
    // D2MOD uses these values directly (they're already in the correct units for the game)
    // In riiablo, we read from CharStats.WalkVelocity and RunVelocity
    com.riiablo.codec.excel.CharStats.Entry charStats = charData.classId != null ? charData.classId.entry() : null;
    float walkSpeed = charStats != null && charStats.WalkVelocity > 0
        ? charStats.WalkVelocity : Engine.Player.SPEED_WALK;
    float runSpeed = charStats != null && charStats.RunVelocity > 0
        ? charStats.RunVelocity : Engine.Player.SPEED_RUN;
    log.info("[MOVEMENT] player={} walkSpeed={} runSpeed={} source={}",
        id, walkSpeed, runSpeed, charStats == null ? "fallback" : "CharStats");
    mVelocity.create(id).set(walkSpeed, runSpeed);
    mAngle.create(id);

    mCofReference.create(id).set(Engine.Player.getToken(charData.charClass), Class.Type.PLR.DEFAULT_MODE);
    mCofComponents.create(id);
    mCofAlphas.create(id);
    mCofTransforms.create(id);

    mMovementModes.create(id).set(Engine.Player.MODE_TN, Engine.Player.MODE_TW, Engine.Player.MODE_RN);

    mSize.create(id).size = Size.MEDIUM;

    mRunning.create(id);
    mNetworked.create(id);
    mZoneAware.create(id);
    return id;
  }

  @Override
  public int createDynamicObject(int act, int monPresetId, float x, float y) {
    String objectType;
    try {
      MonPreset.Entry preset = Riiablo.files.MonPreset.get(act, monPresetId);
      objectType = preset != null ? preset.Place : null;
    } catch (RuntimeException ex) {
      log.error("[MONSTER_PLACEMENT] phase=resolve_failed act={} presetId={} reason={}",
          act, monPresetId, ex.toString());
      return Engine.INVALID_ENTITY;
    }
    log.info("[MONSTER_PLACEMENT] phase=resolve act={} presetId={} placement={} position=({}, {})",
        act, monPresetId, objectType, x, y);
    
    // 首先尝试从 MonStats 表查找（普通怪物）
    MonStats.Entry monstats = Riiablo.files.monstats.get(objectType);

    // D2Game's placement directives are not MonStats keys. Resolve the
    // single-monster directives to their native base rows before giving up;
    // group directives (unique/champion packs) remain a separate generator.
    if (monstats == null) monstats = resolvePlacementMonster(objectType);
    
    // 如果找不到，尝试从 SuperUniques 表查找（超级暗金怪）
    com.riiablo.codec.excel.SuperUniques.Entry superUnique = null;
    if (monstats == null && Riiablo.files.SuperUniques != null) {
      superUnique = Riiablo.files.SuperUniques.get(objectType);
      if (superUnique != null) {
        // SuperUnique 的 MonClass 字段指向实际的 MonStats 记录
        monstats = Riiablo.files.monstats.get(superUnique.MonClass);
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
    }
    log.info("[MONSTER_PLACEMENT] phase=created act={} presetId={} placement={} monster={} entity={}",
        act, monPresetId, objectType, monstats.Id, id);
    mNetworked.create(id);
    return id;
  }

  @Override
  public int createStaticObject(int act, int objId, float x, float y) {
    int objectType = Riiablo.files.obj.getObjectId(act, objId);
    return createStaticObjectBase(objectType, x, y);
  }

  @Override
  public int createStaticObjectByClassId(int objectId, float x, float y) {
    return createStaticObjectBase(objectId, x, y);
  }

  private int createStaticObjectBase(int objectId, float x, float y) {
    Objects.Entry base = Riiablo.files.objects.get(objectId);
    if (base == null) return Engine.INVALID_ENTITY;

    int id = super.createEntity(Class.Type.OBJ, base.Description);
    mObject.create(id).base = base;

    mPosition.create(id).position.set(x, y);
    mMapWrapper.create(id).set(map, map.getZone(x, y));

    if (base.Draw) {
      mCofReference.create(id).set(base.Token, Class.Type.OBJ.DEFAULT_MODE);
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
  static MonStats.Entry resolvePlacementMonster(String placement) {
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
    return Riiablo.files.monstats.get(monsterId);
  }

  protected static boolean isWaypointObject(Objects.Entry base) {
    return base != null
        && (base.SubClass & Engine.Object.SUBCLASS_WAYPOINT)
            == Engine.Object.SUBCLASS_WAYPOINT;
  }

  protected static float resolveObjectInteractionRange(Objects.Entry base) {
    if (base == null) return 0;
    if (isWaypointObject(base)) return base.OperateRange > 0 ? base.OperateRange : 5f;
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

  @Override
  public int createMonster(int monsterId, float x, float y) {
    MonStats.Entry monstats = Riiablo.files.monstats.get(monsterId);
    MonStats2.Entry monstats2 = Riiablo.files.monstats2.get(monstats.MonStatsEx);

    int id = super.createEntity(Class.Type.MON, monstats.Id);
    mMonster.create(id).set(monstats, monstats2);

    // TODO: move this somewhere else (a special class?)
    {
      Attributes attrs = Attributes.obtainStandard();
      StatListRef base = attrs.base();
      base.clear();
      
      // Calculate monster stats based on level using D2MOD logic
      // Reference: D2MOD DATATBLS_CalculateMonsterStatsByLevel
      MonsterStatsCalculator.MonsterStatsInit statsInit = new MonsterStatsCalculator.MonsterStatsInit();
      int monsterLevel = (monstats.Level != null && monstats.Level.length > 0) ? monstats.Level[0] : 1;
      int gameType = 1; // Assume expansion (can be made configurable)
      int difficulty = 0; // Normal difficulty (can be made configurable)
      // Calculate all stats: HP (1), AC (2), Exp (4), A1 (8)
      // Flags: 1=HP, 2=AC, 4=Exp, 8=A1 (Attack 1: TH, MinD, MaxD)
      short flags = (short)(1 | 2 | 4 | 8); // Calculate HP, AC, Exp, and A1 stats
      
      boolean calculated = MonsterStatsCalculator.calculateMonsterStatsByLevel(
          monsterId, gameType, difficulty, monsterLevel, flags, statsInit);

      // A2 values in MonStats are ratios, just like A1. They must be scaled
      // through MonLvl before Actioneer attaches them to a ranged/native A2
      // attack. Passing the raw table values made a quill rat's 1..2 damage
      // spike resolve for roughly 18..22 damage with an inflated to-hit value.
      MonsterStatsCalculator.MonsterStatsInit attack2Init =
          new MonsterStatsCalculator.MonsterStatsInit();
      boolean calculatedAttack2 = MonsterStatsCalculator.calculateMonsterStatsByLevel(
          monsterId, gameType, difficulty, monsterLevel, (short) 0x10, attack2Init);
      Monster monster = mMonster.get(id);
      if (calculatedAttack2) {
        monster.setAttack2Profile(
            attack2Init.A2MinD, attack2Init.A2MaxD, attack2Init.TH);
      } else {
        monster.setAttack2Profile(
            arrayValue(monstats.A2MinD, difficulty),
            arrayValue(monstats.A2MaxD, difficulty),
            arrayValue(monstats.A2TH, difficulty));
      }
      
      if (calculated) {
        // Use calculated HP values
        // Monsters spawn at full HP (maxHP), not random HP
        final float maxHp = statsInit.maxHP;
        base.put(Stat.hitpoints, maxHp);
        base.put(Stat.maxhp, maxHp);
        
        // Use calculated AC (Armor Class)
        base.put(Stat.armorclass, statsInit.AC);
        
        // Use calculated A1 (Attack 1) stats: TH (To Hit), MinD (Min Damage), MaxD (Max Damage)
        base.put(Stat.tohit, statsInit.TH);
        base.put(Stat.mindamage, statsInit.A1MinD);
        base.put(Stat.maxdamage, statsInit.A1MaxD);
      } else {
        // Fallback to direct MonStats values if calculation fails
        // Monsters spawn at full HP (maxHP), not random HP
        final float maxHp = monstats.maxHP[0];
        base.put(Stat.hitpoints, maxHp);
        base.put(Stat.maxhp, maxHp);
        
        // Set monster damage attributes (A1MinD/A1MaxD for attack 1)
        // Reference D2MOD: Monsters use A1MinD/A1MaxD for their base damage
        if (monstats.A1MinD != null && monstats.A1MaxD != null && 
            monstats.A1MinD.length > 0 && monstats.A1MaxD.length > 0) {
          base.put(Stat.mindamage, monstats.A1MinD[0]);
          base.put(Stat.maxdamage, monstats.A1MaxD[0]);
        }
        
        // Set attack rating (A1TH - Attack 1 To Hit)
        if (monstats.A1TH != null && monstats.A1TH.length > 0) {
          base.put(Stat.tohit, monstats.A1TH[0]);
        }
        
        // Set armor class
        if (monstats.AC != null && monstats.AC.length > 0) {
          base.put(Stat.armorclass, monstats.AC[0]);
        }
      }
      
      // Set monster level (for damage calculation and hit chance)
      if (monstats.Level != null && monstats.Level.length > 0) {
        base.put(Stat.level, monstats.Level[0]);
      }

      attrs.reset(); // propagate base changes
      mAttributesWrapper.create(id).attrs = attrs;
      mUnitStates.create(id).init(id);
      log.debug("[MONSTER_COMBAT_STATS] entity={} monster={} level={} "
              + "a1={}..{} ar={} a2={}..{} ar={} rawA2={}..{} rawA2Ar={}",
          id, monstats.Id, monsterLevel,
          statInt(attrs, Stat.mindamage), statInt(attrs, Stat.maxdamage),
          statInt(attrs, Stat.tohit),
          monster.attack2MinDamage, monster.attack2MaxDamage, monster.attack2ToHit,
          arrayValue(monstats.A2MinD, difficulty),
          arrayValue(monstats.A2MaxD, difficulty),
          arrayValue(monstats.A2TH, difficulty));
    }

    mPosition.create(id).position.set(x, y);
    // Monsters created by map/quest generators must carry their owning zone,
    // just like players and objects. Without this, quest systems cannot see
    // the generated boss and clients may resolve the wrong level at a boundary.
    mMapWrapper.create(id).set(map, map.getZone(x, y));
    // D2Common UNITS_GetBaseVelocity always uses MonStats.Velocity for
    // monsters. MonStats.Run only participates in run-animation-rate table
    // generation; treating it as displacement speed makes animation and
    // authoritative movement diverge.
    mVelocity.create(id).setMonster(monstats.Velocity);
    mAngle.create(id);

    CofReference reference = mCofReference.create(id);
    // D2 COF table uses MonStats.Code (abbreviation like "FA"), not Id (full name like "fallen1")
    reference.token  = monstats.Code;
    reference.mode   = monstats.spawnmode.isEmpty() ? Engine.Monster.MODE_NU : (byte) Riiablo.files.MonMode.index(monstats.spawnmode);
    reference.wclass = (byte) Riiablo.files.WeaponClass.index(monstats2.BaseW);
    int[] component = mCofComponents.create(id).component;
    for (byte i = 0; i < monstats2.ComponentV.length; i++) {
      String ComponentV = monstats2.ComponentV[i];
      if (!ComponentV.isEmpty()) {
        String[] v = StringUtils.remove(ComponentV, '"').split(",");
        int random = MathUtils.random(0, v.length - 1);
        component[i] = Riiablo.files.compcode.index(v[random]);
      }
    }

    mCofAlphas.create(id);
    mCofTransforms.create(id);

    mMovementModes.create(id).set(Engine.Monster.MODE_NU, Engine.Monster.MODE_WL, Engine.Monster.MODE_RN);

    float size = mSize.create(id).size = monstats2.SizeX; // FIXME: SizeX and SizeY appear to always be equal -- is this method sufficient?
    AI ai = mAIWrapper.create(id).findAI(id, monstats.AI).ai;
    world.getInjector().inject(ai);
    ai.initialize();
    if (monstats.interact) {
      mInteractable.create(id).set(size, ai);
    }

    mNetworked.create(id);
    return id;
  }

  private static int arrayValue(int[] values, int index) {
    return values != null && index >= 0 && index < values.length
        ? Math.max(0, values[index]) : 0;
  }

  private static int statInt(Attributes attrs, short stat) {
    StatRef ref = attrs != null ? attrs.get(stat, StatRef.obtain()) : null;
    return ref != null ? ref.asInt() : 0;
  }

  @Override
  public boolean resurrectMonster(int monsterId, int sourceId) {
    if (!mMonster.has(monsterId) || !mCorpse.has(monsterId)
        || !mAttributesWrapper.has(monsterId) || !mPosition.has(monsterId)) {
      log.warn("[MONSTER_RAISE] phase=reject source={} target={} reason=missing_components",
          sourceId, monsterId);
      return false;
    }

    Corpse corpse = mCorpse.get(monsterId);
    Monster monster = mMonster.get(monsterId);
    if (!corpse.usable || corpse.fading || monster.monstats == null || monster.monstats2 == null) {
      log.warn("[MONSTER_RAISE] phase=reject source={} target={} reason=unusable_corpse",
          sourceId, monsterId);
      return false;
    }

    Attributes attrs = mAttributesWrapper.get(monsterId).attrs;
    StatRef hitpoints = attrs != null ? attrs.get(Stat.hitpoints, StatRef.obtain()) : null;
    StatRef maxhp = attrs != null ? attrs.get(Stat.maxhp, StatRef.obtain()) : null;
    if (hitpoints == null || maxhp == null || hitpoints.asFixed() > 0f) {
      log.warn("[MONSTER_RAISE] phase=reject source={} target={} reason=not_dead hp={}",
          sourceId, monsterId, hitpoints != null ? hitpoints.asFixed() : -1f);
      return false;
    }

    hitpoints.set(Math.max(1f, maxhp.asFixed()));
    if (mUnitStates.has(monsterId) && mUnitStates.get(monsterId).stateList != null) {
      mUnitStates.get(monsterId).stateList.clearAll();
    }

    mCorpse.remove(monsterId);
    mRunning.remove(monsterId);
    mVelocity.create(monsterId).setMonster(monster.monstats.Velocity);
    mMovementModes.create(monsterId).set(
        Engine.Monster.MODE_NU, Engine.Monster.MODE_WL, Engine.Monster.MODE_RN);

    AI ai = mAIWrapper.create(monsterId).findAI(monsterId, monster.monstats.AI).ai;
    world.getInjector().inject(ai);
    ai.initialize();
    if (monster.monstats.interact && mSize.has(monsterId)) {
      mInteractable.create(monsterId).set(mSize.get(monsterId).size, ai);
    }

    int resurrectMode = Engine.Monster.MODE_NU;
    String configuredMode = monster.monstats2.ResurrectMode;
    if (configuredMode != null && !configuredMode.isEmpty()) {
      int mode = Riiablo.files.MonMode.index(configuredMode);
      if (mode >= 0 && mode < 16) resurrectMode = mode;
    }
    mSequence.create(monsterId).sequence((byte) resurrectMode, Engine.Monster.MODE_NU);

    log.info("[MONSTER_RAISE] phase=restored source={} target={} monster={} hp={} "
            + "mode={} resurrectSkill={} position=({}, {})",
        sourceId, monsterId, monster.monstats.Id, hitpoints.asFixed(), resurrectMode,
        monster.monstats2.ResurrectSkill,
        mPosition.get(monsterId).position.x, mPosition.get(monsterId).position.y);
    return true;
  }

  @Override
  public int createWarp(int index, float x, float y) {
    if (com.riiablo.engine.server.quest.QuestWarp.isQuestWarp(index)) {
      int destination = com.riiablo.engine.server.quest.QuestWarp.destinationLevelId(index);
      Map.Zone zone = map.getZone(x, y);
      Levels.Entry destinationLevel = Riiablo.files.Levels.get(destination);
      LvlWarp.Entry portalBounds = Riiablo.files.LvlWarp.get(0);
      if (zone == null || destinationLevel == null || portalBounds == null) {
        log.error("[QUEST_WARP] creation failed: sourceZone={} destination={} bounds={}",
            zone, destination, portalBounds);
        return Engine.INVALID_ENTITY;
      }
      int id = super.createEntity(Class.Type.WRP, "quest-warp");
      mWarp.create(id).set(index, portalBounds, destinationLevel);
      mMapWrapper.create(id).set(map, zone);
      mPosition.create(id).position.set(x, y);
      mInteractable.create(id).set(3.0f, warpInteractor);
      mNetworked.create(id);
      log.info("[QUEST_WARP] created: entity={} source={} destination={} position=({}, {})",
          id, zone.level.Id, destination, x, y);
      return id;
    }
    final int mainIndex   = DT1.Tile.Index.mainIndex(index);
    final int subIndex    = DT1.Tile.Index.subIndex(index);
    final int orientation = DT1.Tile.Index.orientation(index);

    Map.Zone zone = map.getZone(x, y);
    int dstFromOverride = map.getWarpDestinationOverride(zone.level.Id, mainIndex);
    int dst = dstFromOverride;
    if (dst <= 0) {
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
    int wrp = zone.level.Warp[mainIndex];
    assert wrp >= 0 : "Invalid warp";

    Levels.Entry dstLevel = Riiablo.files.Levels.get(dst);

    LvlWarp.Entry warp = Riiablo.files.LvlWarp.get(wrp);
    if (warp == null) {
      // LvlWarp entry not found, skip creating warp
      return Engine.INVALID_ENTITY;
    }

    int id = super.createEntity(Class.Type.WRP, "warp");
    mWarp.create(id).set(index, warp, dstLevel);
    mMapWrapper.create(id).set(map, zone);

    mPosition.create(id).position.set(x, y).add(warp.OffsetX, warp.OffsetY);

    mInteractable.create(id).set(3.0f, warpInteractor);
    mNetworked.create(id);
    return id;
  }

  @Override
  public int createItem(com.riiablo.item.Item item, float x, float y) {
    int id = super.createEntity(Class.Type.ITM, "item");
    mItem.create(id).set(item);

    mPosition.create(id).position.set(x, y);
    mInteractable.create(id).set(1f, itemInteractor);
    mNetworked.create(id);
    return id;
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
    Missiles.Entry missile = Riiablo.files.Missiles.get(missileId);
    int id = super.createEntity(Class.Type.MIS, missile.Missile);
    com.riiablo.engine.server.component.Missile missileComponent = mMissile.create(id);
    missileComponent.set(missile, position, missile.Range).setOwner(ownerId);

    Attributes ownerAttrs = ownerId >= 0 && mAttributesWrapper.has(ownerId)
        ? mAttributesWrapper.get(ownerId).attrs : null;
    Monster ownerMonster = ownerId >= 0 && mMonster.has(ownerId)
        ? mMonster.get(ownerId) : null;
    int ownerMode = ownerId >= 0 && mCofReference.has(ownerId)
        ? mCofReference.get(ownerId).mode : -1;
    int damageLevel = Math.max(1, statInt(ownerAttrs, Stat.level));
    MissileDamageResolver.initialize(missileComponent, ownerAttrs, ownerMonster,
        ownerMode, damageLevel, 0);

    mPosition.create(id).position.set(position);
    mVelocity.create(id).velocity.set(angle).setLength(missile.Vel);
    mAngle.create(id).set(angle);
    mSize.create(id).size = Size.SMALL;
    
    // Preload missile asset so it's ready when MissileLoader processes it
    if (missileComponent.missileDescriptor != null) {
      Riiablo.assets.load(missileComponent.missileDescriptor);
    }
    
    com.riiablo.logger.Logger log = com.riiablo.logger.LogManager.getLogger(ServerEntityFactory.class);
    log.debug("Created missile {} with ownerId={}, range={}, pos=({}, {}), asset={}", 
        id, ownerId, missile.Range, position.x, position.y, 
        missileComponent.missileDescriptor != null ? missileComponent.missileDescriptor.fileName : "null");
    return id;
  }
}
