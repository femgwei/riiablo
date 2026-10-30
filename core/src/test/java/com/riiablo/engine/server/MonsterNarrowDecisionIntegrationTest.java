package com.riiablo.engine.server;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.ai.Skeleton;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Pathfind;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real Skeleton decisions drive real pursuit; only the terrain and branch probability are controlled. */
class MonsterNarrowDecisionIntegrationTest extends RiiabloTest {
  @Test void skeletonDecisionsResumeQueuedFollowersAfterTheChokeIsCleared() {
    com.artemis.World previous=Riiablo.engine;
    try (PathfinderTerrainPursuitTest.Fixture f=new PathfinderTerrainPursuitTest.Fixture(
        (x,y)->!(x>=4 && x<=36 && y>=9 && y<=11),false,
        new EventSystem(),new CofManager(),new NativePlayerTargetSlots(),
        org.mockito.Mockito.mock(EntityFactory.class))) {
      Riiablo.engine=f.world;
      int follower=f.unit(6,10,3,true);
      f.world.getMapper(Player.class).create(f.target);
      f.world.process();
      Skeleton leader=ai(f,f.actor), queued=ai(f,follower);
      leader.update(.04f);
      int blocker=f.unit(20,10,3,true);
      f.world.process();
      for(int tick=0;tick<120;tick++) {
        leader.update(.04f); queued.update(.04f); f.advance(1);
        assertTrue(f.position(f.actor).x<19);
        assertTrue(f.position(follower).x<19);
      }
      Vector2 stopped=new Vector2(f.position(follower));
      f.world.delete(blocker); f.world.process();
      boolean targetTracked=false;
      for(int tick=0;tick<400;tick++) {
        leader.update(.04f); queued.update(.04f); f.advance(1);
        Vector2 p=f.position(follower);
        assertFalse(f.map.isBlockedForPath(com.riiablo.map.Map.round(p.x),com.riiablo.map.Map.round(p.y),1,3,true));
        assertTrue(f.collision.isFreeForPath(follower,-1,com.riiablo.map.Map.round(p.x),com.riiablo.map.Map.round(p.y),3));
        Pathfind path=f.world.getMapper(Pathfind.class).get(follower);
        if(path!=null){assertEquals(f.target,path.targetEntityId);assertSame(f.identity,path.targetIdentity);targetTracked=true;}
      }
      assertTrue(targetTracked,"real Skeleton decisions must submit the player target");
      assertTrue(f.position(f.actor).x>23,"leader="+f.position(f.actor));
      assertTrue(f.position(follower).x>20,"follower="+f.position(follower));
      assertTrue(f.position(follower).dst(stopped)>5,"blocked follower must make actual progress");
    } finally {Riiablo.engine=previous;}
  }

  private Skeleton ai(PathfinderTerrainPursuitTest.Fixture f,int id) {
    com.riiablo.codec.excel.MonStats.Entry row=Riiablo.files.monstats.get("skeleton1");
    f.world.getMapper(Monster.class).get(id).set(row,Riiablo.files.monstats2.get(row.MonStatsEx));
    Skeleton ai=new Skeleton(id) {
      @Override public void initialize(){super.initialize();params[0]=100;}
    };
    f.world.getInjector().inject(ai);
    ai.initialize();
    return ai;
  }
}
