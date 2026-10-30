package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.Velocity;
import org.junit.jupiter.api.Test;

/** Real path search, swept movement and unit occupancy on controlled narrow terrain.
 * Repeated pursuit requests model AI retry cadence; this does not execute monster decision AI.
 */
class MonsterNarrowPursuitIntegrationTest {
  private boolean corridor(int x, int y) {
    return x >= 5 && x <= 33 && y >= 9 && y <= 11
        || x >= 27 && x <= 29 && y >= 9 && y <= 22;
  }

  @Test void sizeThreePursuerTurnsThroughThreeCellCorridorWithoutWallCutsOrJumps() {
    try (PathfinderTerrainPursuitTest.Fixture f =
        new PathfinderTerrainPursuitTest.Fixture((x,y) -> !corridor(x,y), false)) {
      f.position(f.target).set(28,20);
      f.collision.refreshNow();
      float initial = f.position(f.actor).dst(f.position(f.target));
      assertTrue(f.start());
      for (int tick=0; tick<300; tick++) advance(f, f.actor);
      assertTrue(f.position(f.actor).x >= 27, "must reach the elbow");
      assertTrue(f.position(f.actor).y >= 15, "must turn and advance down the second arm");
      assertTrue(f.position(f.actor).dst(f.position(f.target)) < initial-10);
    }
  }

  @Test void movingTargetAroundElbowRefreshesPursuitWithoutLosingTargetIdentity() {
    try (PathfinderTerrainPursuitTest.Fixture f =
        new PathfinderTerrainPursuitTest.Fixture((x,y) -> !corridor(x,y), false)) {
      assertTrue(f.start());
      for (int tick=0; tick<30; tick++) advance(f,f.actor);
      // Test stimulus moves only the target; the pursuing monster never gets repositioned.
      f.position(f.target).set(28,20);
      f.collision.refreshNow();
      for (int tick=0; tick<300; tick++) advance(f,f.actor);
      assertTrue(f.position(f.actor).y >= 15, "must follow the new target arm, not the stale endpoint");
      assertTrue(f.position(f.actor).dst(f.position(f.target)) < 7);
    }
  }

  @Test void twoQueuedPursuersStopAtOccupiedChokeAndResumeAfterBlockerRemoval() {
    try (PathfinderTerrainPursuitTest.Fixture f =
        new PathfinderTerrainPursuitTest.Fixture((x,y) -> !(x>=4 && x<=36 && y>=9 && y<=11), false)) {
      int follower = f.unit(6,10,3,true);
      f.world.process();
      assertTrue(f.start());
      int blocker = f.unit(20,10,3,true);
      f.world.process();
      Vector2 previousLeader = new Vector2(f.position(f.actor));
      int stationaryTicks=0;
      for (int tick=0; tick<120; tick++) {
        retry(f,f.actor,tick); retry(f,follower,tick);
        advance(f,f.actor,follower);
        if (previousLeader.dst2(f.position(f.actor)) < .000001f) stationaryTicks++;
        previousLeader.set(f.position(f.actor));
        assertTrue(f.position(f.actor).x < 19, "leader cannot pass the occupied choke");
        assertTrue(f.position(follower).x < 19, "follower cannot pass the occupied choke");
      }
      assertTrue(stationaryTicks>=10, "must observe actual lack of progress, not only a rejected request");
      Vector2 stoppedLeader = new Vector2(f.position(f.actor));
      Vector2 stoppedFollower = new Vector2(f.position(follower));
      f.world.delete(blocker);
      f.world.process();
      for (int tick=0; tick<400; tick++) {
        retry(f,f.actor,tick); retry(f,follower,tick);
        advance(f,f.actor,follower);
      }
      assertTrue(f.position(f.actor).x > 23, "leader must resume beyond the choke");
      assertTrue(f.position(follower).x > 20, "queued follower must resume too: leader="+f.position(f.actor)+" follower="+f.position(follower)+" stopped="+stoppedFollower);
      assertTrue(f.position(f.actor).dst(stoppedLeader)>5);
      assertTrue(f.position(follower).dst(stoppedFollower)>5);
    }
  }

  private void retry(PathfinderTerrainPursuitTest.Fixture f,int actor,int tick) {
    if (tick%13==0 && !f.world.getMapper(Pathfind.class).has(actor))
      f.paths.findPath(actor,f.position(f.target),false,f.target);
  }

  private void advance(PathfinderTerrainPursuitTest.Fixture f,int... actors) {
    Vector2[] before = new Vector2[actors.length];
    float[] speed = new float[actors.length];
    for (int i=0;i<actors.length;i++) {
      before[i]=new Vector2(f.position(actors[i]));
      Velocity v=f.world.getMapper(Velocity.class).get(actors[i]);
      speed[i]=Math.max(v.speed(false),v.speed(true));
    }
    f.advance(1);
    for (int i=0;i<actors.length;i++) {
      int actor=actors[i]; Vector2 p=f.position(actor);
      assertTrue(before[i].dst(p)<=speed[i]*.04f+.01f,"discontinuous movement: "+before[i]+" -> "+p);
      assertFalse(f.map.isBlockedForPath(com.riiablo.map.Map.round(p.x),com.riiablo.map.Map.round(p.y),1,3,true),
          "monster footprint entered terrain at "+p);
      assertTrue(f.collision.isFreeForPath(actor,Engine.INVALID_ENTITY,
          com.riiablo.map.Map.round(p.x),com.riiablo.map.Map.round(p.y),3),"monster overlapped another unit at "+p);
      Pathfind path=f.world.getMapper(Pathfind.class).get(actor);
      if(path!=null){assertEquals(f.target,path.targetEntityId);assertSame(f.identity,path.targetIdentity);}
    }
  }
}
