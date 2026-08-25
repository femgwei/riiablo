package com.riiablo.engine.server.component;

import com.artemis.PooledComponent;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntSet;
import com.riiablo.attributes.Attributes;
import com.riiablo.codec.DCC;
import com.riiablo.codec.excel.Missiles;

@Transient
@PooledWeaver
public class Missile extends PooledComponent {
  public Missiles.Entry missile;
  public float range = 0;
  public AssetDescriptor<DCC> missileDescriptor;
  public final Vector2 start = new Vector2();
  
  /** 导弹拥有者实体 ID（用于伤害计算和敌人判断） */
  public int ownerId = -1;
  
  /** 已移动距离（用于范围检查，与 d2mod 一致） */
  public float distanceTraveled = 0f;

  /** Optional monster attack profile captured when the missile is spawned. */
  public int attackMinDamage;
  public int attackMaxDamage;
  public int attackRating;

  /**
   * Native-style damage stat list captured at spawn time. D2MOO writes these
   * stats onto the missile unit before it begins moving, so later owner state
   * changes cannot alter an in-flight projectile.
   */
  public final Attributes damage = Attributes.obtainStandard();
  public boolean damageSnapshot;
  public int damageLevel;

  /** Targets already resolved by another missile from the same cast. */
  public IntSet sharedHitTargets;

  @Override
  protected void reset() {
    missile = null;
    range = 0;
    missileDescriptor = null;
    start.setZero();
    ownerId = -1;
    distanceTraveled = 0f;
    attackMinDamage = 0;
    attackMaxDamage = 0;
    attackRating = 0;
    damage.clear();
    damageSnapshot = false;
    damageLevel = 0;
    sharedHitTargets = null;
  }

  public Missile set(Missiles.Entry missile, Vector2 start, float range) {
    this.missile = missile;
    this.start.set(start);
    this.range = range;
    this.missileDescriptor = new AssetDescriptor<>(Class.Type.MIS.PATH + '\\' + missile.CelFile + ".dcc", DCC.class);
    return this;
  }
  
  public Missile setOwner(int ownerId) {
    this.ownerId = ownerId;
    return this;
  }

  public Missile shareHitTargets(IntSet targets) {
    sharedHitTargets = targets;
    return this;
  }
}
