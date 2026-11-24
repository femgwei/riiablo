package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import com.artemis.Aspect;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.presentation.component.AnimationWrapper;
import org.junit.jupiter.api.Test;

class ArcticBlastClientPhaseParityTest extends RiiabloTest {
  private static int firstVisual(ArcticBlastSequenceIntegrationTest.Fixture f) {
    IntBag ids=f.world.getAspectSubscriptionManager().get(Aspect.all(Missile.class)).getEntities();
    for(int i=0;i<ids.size();i++) {
      Missile m=f.world.getMapper(Missile.class).get(ids.get(i));
      if(m.presentationOnly&&m.skillId==230)return ids.get(i);
    }
    return -1;
  }
  private static void reachFirstAction(ArcticBlastSequenceIntegrationTest.Fixture f) {
    f.start();
    for(int tick=0;tick<11;tick++){f.input(true,new Vector2(4530,5642));f.step();}
    assertEquals(1,f.probe.callbacks.size());
    assertEquals(-1,firstVisual(f));
  }

  @Test void nativeActionIsConsumedNextUpdateAndInitialLocalFlightAdvancesTwice() {
    try(ArcticBlastSequenceIntegrationTest.Fixture f=new ArcticBlastSequenceIntegrationTest.Fixture()) {
      reachFirstAction(f);
      f.input(true,new Vector2(4530,5642));f.step();
      int id=firstVisual(f);assertTrue(id>=0);
      Missile m=f.world.getMapper(Missile.class).get(id);
      assertEquals(0,m.nativeFrame);assertEquals(9,f.reference().sequenceFrame);
      assertEquals(9*256,f.world.getMapper(com.riiablo.engine.server.component.Sequence.class).get(f.caster).nativeArcticCursor);
      Vector2 at=new Vector2(f.world.getMapper(Position.class).get(id).position);
      f.input(true,new Vector2(4530,5642));f.step();
      assertEquals(2,m.nativeFrame);
      assertEquals(2*.5625f,f.world.getMapper(Position.class).get(id).position.x-at.x,.00001f);
      assertEquals(2*240>>>8,f.world.getMapper(AnimationWrapper.class).get(id).animation.getFrame());
      f.input(true,new Vector2(4530,5642));f.step();assertEquals(3,m.nativeFrame);
    }
  }

  @Test void remoteOwnersDoNotReceiveTheLocalPlayerFirstUpdateRepeat() {
    try(ArcticBlastSequenceIntegrationTest.Fixture f=new ArcticBlastSequenceIntegrationTest.Fixture()) {
      f.localPlayer[0]=-1;reachFirstAction(f);f.step();
      int id=firstVisual(f);assertTrue(id>=0);f.step();
      assertEquals(1,f.world.getMapper(Missile.class).get(id).nativeFrame);
    }
  }

  @Test void interruptionBeforeConsumptionDiscardsThePendingVisual() {
    try(ArcticBlastSequenceIntegrationTest.Fixture f=new ArcticBlastSequenceIntegrationTest.Fixture()) {
      reachFirstAction(f);f.actions.cancelCasting(f.caster,"phase-test");f.step();
      assertEquals(-1,firstVisual(f));
      for(int i=0;i<25;i++)f.step();assertEquals(0,f.liveStreams());
    }
  }

  @Test void zeroVelocityDoesNotEnterTheNativeInitialRepeatBranch() {
    try(ArcticBlastSequenceIntegrationTest.Fixture f=new ArcticBlastSequenceIntegrationTest.Fixture()) {
      reachFirstAction(f);f.step();int id=firstVisual(f);assertTrue(id>=0);
      f.world.getMapper(com.riiablo.engine.server.component.Velocity.class).get(id).velocity.setZero();
      f.step();assertEquals(1,f.world.getMapper(Missile.class).get(id).nativeFrame);
    }
  }

  @Test void turningBetweenTheActionAndConsumptionUsesTheCurrentAim() {
    try(ArcticBlastSequenceIntegrationTest.Fixture f=new ArcticBlastSequenceIntegrationTest.Fixture()) {
      reachFirstAction(f);
      f.input(true,new Vector2(4522,5650));f.step();int id=firstVisual(f);assertTrue(id>=0);
      Vector2 velocity=f.world.getMapper(com.riiablo.engine.server.component.Velocity.class).get(id).velocity;
      assertEquals(0f,velocity.x,.00001f);assertTrue(velocity.y>0);
      assertEquals(1,f.probe.callbacks.size());
    }
  }

  @Test void releasingDoesNotRetargetTheAlreadyScheduledTailToTheReleasedPointer() {
    try(ArcticBlastSequenceIntegrationTest.Fixture f=new ArcticBlastSequenceIntegrationTest.Fixture()) {
      reachFirstAction(f);
      f.input(false,new Vector2(4522,5650));f.step();int id=firstVisual(f);assertTrue(id>=0);
      Vector2 velocity=f.world.getMapper(com.riiablo.engine.server.component.Velocity.class).get(id).velocity;
      assertTrue(velocity.x>0);assertEquals(0f,velocity.y,.00001f);
      for(int tick=0;tick<40;tick++){f.input(false,new Vector2(4522,5650));f.step();}
      assertEquals(0,f.liveStreams());
    }
  }

  @Test void cancellingAndRecastingInTheSameUpdateCannotConsumeTheOldAction() {
    try(ArcticBlastSequenceIntegrationTest.Fixture f=new ArcticBlastSequenceIntegrationTest.Fixture()) {
      reachFirstAction(f);f.actions.cancelCasting(f.caster,"recast-test");f.start();
      for(int tick=1;tick<=11;tick++) {
        f.input(true,new Vector2(4522,5650));f.step();assertEquals(-1,firstVisual(f));
      }
      f.input(true,new Vector2(4522,5650));f.step();assertTrue(firstVisual(f)>=0);
      assertEquals(2,f.overlays.requests);
    }
  }

  @Test void turningDoesNotBendAnExistingStreamOrRestartTheCastOverlay() {
    try(ArcticBlastSequenceIntegrationTest.Fixture f=new ArcticBlastSequenceIntegrationTest.Fixture()) {
      reachFirstAction(f);f.step();int id=firstVisual(f);assertTrue(id>=0);
      Vector2 at=new Vector2(f.world.getMapper(Position.class).get(id).position);
      f.input(true,new Vector2(4522,5650));f.step();
      Vector2 velocity=f.world.getMapper(com.riiablo.engine.server.component.Velocity.class).get(id).velocity;
      assertTrue(velocity.x>0);assertEquals(0f,velocity.y,.00001f);
      assertTrue(f.world.getMapper(Position.class).get(id).position.x>at.x);
      assertEquals(at.y,f.world.getMapper(Position.class).get(id).position.y,.00001f);
      assertEquals(1,f.overlays.requests);
    }
  }
}
