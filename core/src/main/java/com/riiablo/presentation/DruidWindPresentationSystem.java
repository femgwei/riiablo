package com.riiablo.presentation;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.path.D2Path;
import com.d2moo.common.path.D2PathMisc;
import com.d2moo.common.seed.Seed;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Networked;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.missile.NativeFirestormMotion;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.presentation.component.DruidWindFlight;
import com.riiablo.presentation.component.DruidWindHiddenCarrier;
import java.util.ArrayList;
import java.util.List;
import net.mostlyoriginal.api.event.common.Subscribe;

/** Original CltDo68/69 create wind paths independently of server damage carriers. */
@All({Missile.class, Position.class})
public final class DruidWindPresentationSystem extends IteratingSystem {
  protected ComponentMapper<Missile> mMissile;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Networked> mNetworked;
  protected ComponentMapper<DruidWindFlight> mFlight;
  protected ComponentMapper<DruidWindHiddenCarrier> mHidden;
  protected ComponentMapper<com.riiablo.presentation.component.AnimationWrapper> mAnimation;
  @Wire(name="factory") protected EntityFactory factory;
  @Wire(name="clientContext") protected ClientGameplayContext context;
  @Wire(name="map",failOnNull=false) protected com.riiablo.map.Map map;
  private double time;
  private final List<Cast> casts = new ArrayList<>();

  private static final class Cast {
    int owner, skill;
    double expires;
  }

  @Override protected void begin() {
    time += Math.max(0f, world.delta);
    casts.removeIf(cast -> cast.expires <= time);
  }

  @Subscribe public void onSkillDo(SkillDoEvent event) {
    if ((event.skillId != 240 && event.skillId != 245) || !mPosition.has(event.entityId)) return;
    com.riiablo.codec.excel.Skills.Entry skill = context.files().skills.get(event.skillId);
    if (skill == null || (skill.cltdofunc != 68 && skill.cltdofunc != 69)) return;
    com.riiablo.codec.excel.Missiles.Entry row = context.files().Missiles.get(skill.cltmissilea);
    if (row == null) return;
    int level = mPlayer.has(event.entityId) && mPlayer.get(event.entityId).data != null
        ? Math.max(1, mPlayer.get(event.entityId).data.getSkill(event.skillId)) : 1;
    int count = Math.max(0, SkillFormula.evaluate(skill.calc1, skill, level));
    Vector2 origin = mPosition.get(event.entityId).position;
    Vector2 target = event.targetId >= 0 && mPosition.has(event.targetId)
        ? mPosition.get(event.targetId).position : event.targetVec;
    if (target == null) return;
    int x = MathUtils.floor(origin.x+.5f), y = MathUtils.floor(origin.y+.5f);
    int tx = MathUtils.floor(target.x+.5f), ty = MathUtils.floor(target.y+.5f);
    int main = D2PathMisc.primaryDirection(new D2Path.Point(x,y), new D2Path.Point(tx,ty));
    int life = Math.max(1, Math.min(77, Missile.nativeRangeFrames(row,level)));
    int created = 0;
    List<Integer> visuals = new ArrayList<>();
    for (int i=0; i<count; i++) {
      int id = createLocalMissile(factory,row,new Vector2(tx-x,ty-y),new Vector2(x,y),level);
      if (id < 0) continue;
      if (!mMissile.has(id) || !mVelocity.has(id)) {world.delete(id); continue;}
      visuals.add(id);
      Missile m = mMissile.get(id);
      m.skillId=event.skillId; m.damageLevel=level; m.presentationOnly=true; m.authoritative=false;
      m.ownerId=-1; m.persistent=false; m.range=0; m.nativeFrame=0; m.nativeLifetimeFrames=life;
      m.start.set(x,y); m.chargedBoltPath=true; m.chargedBoltMainDirection=main;
      // Client callback 0x29E80 initializes from PATH target X + spread index.
      m.chargedBoltSeedLow=tx+i; m.chargedBoltSeedHigh=666;
      m.chargedBoltTargetX=tx; m.chargedBoltTargetY=ty;
      DruidWindFlight flight=mFlight.create(id); flight.birthTime=time;
      // Retail sprite-facing is independent of its precomputed zig-zag path.
      // Allocator stream parity is not established; retain a stable local variant.
      flight.pathDirection=Seed.rollLimitedRandomNumber(new D2Seed(
          m.presentationSeedKnown ? m.presentationSeed : m.rngState,666),64);
      mNetworked.remove(id);
      pose(id,m,0); created++;
    }
    if (created != count) {
      for(int id:visuals)world.delete(id);
      return; // Allocation failure retains the complete authoritative snapshot fallback.
    }
    if (created > 0) {
      Cast cast=new Cast(); cast.owner=event.entityId; cast.skill=event.skillId;
      cast.expires=time+life/25.0; casts.add(cast);
    }
  }

  /** Only hide a combat carrier after this world has created its independent visual. */
  public boolean hidesCarrier(int id) {
    if (mHidden.has(id)) return true;
    Missile m=mMissile.get(id);
    if (!NativeDruidWindPresentation.handles(m) || m.presentationOnly) return false;
    for (Cast cast:casts) if (cast.owner==m.ownerId && cast.skill==m.skillId) {
      mHidden.create(id); return true;
    }
    return false; // Restored flights without a SkillDo retain the existing snapshot fallback.
  }

  @Override protected void process(int id) {
    if (!mFlight.has(id)) {hidesCarrier(id); return;}
    Missile m=mMissile.get(id);
    int age=Math.max(0,(int)Math.floor((time-mFlight.get(id).birthTime)*25+.00001));
    if (age>=m.nativeLifetimeFrames) {world.delete(id); return;}
    boolean knownScene=map!=null && map.getZone(mPosition.get(id).position)!=null;
    m.nativeFrame=age; pose(id,m,age);
    if (knownScene && map.getZone(mPosition.get(id).position)==null) world.delete(id);
  }

  private void pose(int id,Missile m,int age) {
    NativeFirestormMotion.Sample sample=NativeFirestormMotion.sample(m,age);
    mPosition.get(id).setNativePrecision(sample.x,sample.y);
    sample.velocity(mVelocity.get(id).velocity);
    if (mAnimation.has(id)) {
      com.riiablo.codec.Animation animation=mAnimation.get(id).animation;
      NativeDruidWindPresentation.initialize(m,animation);
      animation.setDirection(NativeDruidWindPresentation.direction(mFlight.get(id).pathDirection,animation.getNumDirections()));
    }
  }

  static int createLocalMissile(EntityFactory factory,com.riiablo.codec.excel.Missiles.Entry row,
      Vector2 direction,Vector2 origin,int level) {
    // The authoritative carrier already owns TravelSound. Presentation
    // allocation must neither create another physics body nor replay it.
    return factory instanceof ClientEntityFactory
        ? ((ClientEntityFactory)factory).createMissilePresentation(row,direction,origin)
        : factory.createMissile(row,direction,origin,-1,level);
  }
}
