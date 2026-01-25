package com.riiablo.engine.server;

import java.util.Arrays;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.artemis.ComponentMapper;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

import com.riiablo.Riiablo;
import com.riiablo.engine.server.ai.AI;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatListRef;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.LvlWarp;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
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
import com.riiablo.engine.server.component.Size;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.Warp;
import com.riiablo.engine.server.component.ZoneAware;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;

public class ServerEntityFactory extends EntityFactory {
  private static final String TAG = "ServerEntityFactory";

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
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;

  protected ObjectInteractor objectInteractor;
  protected WarpInteractor warpInteractor;
  protected ItemInteractor itemInteractor;

  @Override
  public int createPlayer(CharData charData, Vector2 position) {
    int id = super.createEntity(Class.Type.PLR, "player");
    mPlayer.create(id).data = charData;
    mAttributesWrapper.create(id).attrs = charData.getStats();
    mMapWrapper.create(id).set(map, map.getZone(position));

    mPosition.create(id).position.set(position);
    // D2MOO: UNITS_GetRunAndWalkSpeedForPlayer reads from CharStatsTxt.nWalkSpeed and nRunSpeed
    // These values are in units that need to be converted to actual speed
    // D2MOO uses these values directly (they're already in the correct units for the game)
    // In riiablo, we read from CharStats.WalkVelocity and RunVelocity
    com.riiablo.codec.excel.CharStats.Entry charStats = charData.classId != null ? charData.classId.entry() : null;
    float walkSpeed = charStats != null ? charStats.WalkVelocity : Engine.Player.SPEED_WALK;
    float runSpeed = charStats != null ? charStats.RunVelocity : Engine.Player.SPEED_RUN;
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
    String objectType = Riiablo.files.MonPreset.getPlace(act, monPresetId);
    
    // 首先尝试从 MonStats 表查找（普通怪物）
    MonStats.Entry monstats = Riiablo.files.monstats.get(objectType);
    
    // 如果找不到，尝试从 SuperUniques 表查找（超级暗金怪）
    if (monstats == null && Riiablo.files.SuperUniques != null) {
      com.riiablo.codec.excel.SuperUniques.Entry superUnique = Riiablo.files.SuperUniques.get(objectType);
      if (superUnique != null) {
        // SuperUnique 的 MonClass 字段指向实际的 MonStats 记录
        monstats = Riiablo.files.monstats.get(superUnique.MonClass);
      }
    }
    
    if (monstats == null) return Engine.INVALID_ENTITY;

    int id = createMonster(monstats.hcIdx, x, y);
    mNetworked.create(id);
    return id;
  }

  @Override
  public int createStaticObject(int act, int objId, float x, float y) {
    int objectType = Riiablo.files.obj.getObjectId(act, objId);
    Objects.Entry base = Riiablo.files.objects.get(objectType);
    if (base == null) return Engine.INVALID_ENTITY;

    int id = super.createEntity(Class.Type.OBJ, base.Description);
    mObject.create(id).base = base;

    mPosition.create(id).position.set(x, y);

    if (base.Draw) {
      mCofReference.create(id).set(base.Token, Class.Type.OBJ.DEFAULT_MODE);
      int[] component = mCofComponents.create(id).component;
      Arrays.fill(component, CofComponents.COMPONENT_NULL);
      mCofAlphas.create(id);
      mCofTransforms.create(id);
    }

    if (base.OperateRange > 0 && ArrayUtils.contains(base.Selectable, true)) {
      mInteractable.create(id).set(base.OperateRange, objectInteractor);
    }

    mSize.create(id); // single size doesn't make any sense in this case because this is a rect
    mNetworked.create(id);
    return id;
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
      
      // Calculate monster stats based on level using D2MOO logic
      // Reference: D2MOO DATATBLS_CalculateMonsterStatsByLevel
      MonsterStatsCalculator.MonsterStatsInit statsInit = new MonsterStatsCalculator.MonsterStatsInit();
      int monsterLevel = (monstats.Level != null && monstats.Level.length > 0) ? monstats.Level[0] : 1;
      int gameType = 1; // Assume expansion (can be made configurable)
      int difficulty = 0; // Normal difficulty (can be made configurable)
      // Calculate all stats: HP (1), AC (2), Exp (4), A1 (8)
      // Flags: 1=HP, 2=AC, 4=Exp, 8=A1 (Attack 1: TH, MinD, MaxD)
      short flags = (short)(1 | 2 | 4 | 8); // Calculate HP, AC, Exp, and A1 stats
      
      boolean calculated = MonsterStatsCalculator.calculateMonsterStatsByLevel(
          monsterId, gameType, difficulty, monsterLevel, flags, statsInit);
      
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
        // Reference D2MOO: Monsters use A1MinD/A1MaxD for their base damage
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
    }

    mPosition.create(id).position.set(x, y);
    mVelocity.create(id).set(monstats.Velocity, monstats.Run);
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

  @Override
  public int createWarp(int index, float x, float y) {
    final int mainIndex   = DT1.Tile.Index.mainIndex(index);
    final int subIndex    = DT1.Tile.Index.subIndex(index);
    final int orientation = DT1.Tile.Index.orientation(index);

    Map.Zone zone = map.getZone(x, y);
    int dst = zone.level.Vis[mainIndex];
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
