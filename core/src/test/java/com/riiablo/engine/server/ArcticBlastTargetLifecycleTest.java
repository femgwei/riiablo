package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import com.artemis.Aspect;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.DeathEvent;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Actual pending visual consumption, not a synthetic SkillDo. */
class ArcticBlastTargetLifecycleTest extends RiiabloTest {
  private static int target(ArcticBlastSequenceIntegrationTest.Fixture f) {
    int id=f.world.create();f.world.getMapper(Position.class).create(id).position.set(4522,5650);
    Attributes attrs=Attributes.obtainStandard();attrs.base().put(Stat.hitpoints,1000);attrs.reset();
    f.world.getMapper(AttributesWrapper.class).create(id).attrs=attrs;
    return id;
  }
  private static void pending(ArcticBlastSequenceIntegrationTest.Fixture f,int target) {
    f.start();
    for(int i=0;i<11;i++) {
      f.actions.updateArcticBlastInput(f.caster,true,target,new Vector2(4530,5642));f.step();
    }
    assertEquals(1,f.probe.callbacks.size());assertEquals(-1,visual(f));
  }
  private static int visual(ArcticBlastSequenceIntegrationTest.Fixture f) {
    IntBag ids=f.world.getAspectSubscriptionManager().get(Aspect.all(Missile.class)).getEntities();
    for(int i=0;i<ids.size();i++) {
      Missile m=f.world.getMapper(Missile.class).get(ids.get(i));
      if(m.presentationOnly&&m.skillId==230)return ids.get(i);
    }
    return -1;
  }
  private static Vector2 velocity(ArcticBlastSequenceIntegrationTest.Fixture f) {
    int id=visual(f);assertTrue(id>=0);return f.world.getMapper(Velocity.class).get(id).velocity;
  }

  @Test void pendingActionUsesTheTargetsPositionAtConsumption() {
    try(ArcticBlastSequenceIntegrationTest.Fixture f=new ArcticBlastSequenceIntegrationTest.Fixture()) {
      int target=target(f);pending(f,target);
      f.world.getMapper(Position.class).get(target).position.set(4514,5642);f.step();
      Vector2 v=velocity(f);assertTrue(v.x<0);assertEquals(0f,v.y,.00001f);
    }
  }

  @Test void retainedCorpsePositionIsNotRejectedMerelyBecauseHpReachedZero() {
    try(ArcticBlastSequenceIntegrationTest.Fixture f=new ArcticBlastSequenceIntegrationTest.Fixture()) {
      int target=target(f);pending(f,target);
      f.world.getMapper(AttributesWrapper.class).get(target).attrs.get(Stat.hitpoints).set(0f);
      f.step();Vector2 v=velocity(f);assertEquals(0f,v.x,.00001f);assertTrue(v.y>0);
    }
  }

  @Test void removedTargetFallsBackToTheRetainedGroundAim() {
    try(ArcticBlastSequenceIntegrationTest.Fixture f=new ArcticBlastSequenceIntegrationTest.Fixture()) {
      int target=target(f);pending(f,target);
      Vector2 fallback=new Vector2(f.world.getMapper(Casting.class).get(f.caster).targetVec);
      f.world.delete(target);f.step();
      Vector2 v=velocity(f);assertTrue(v.x>0,"v="+v+" fallback="+fallback
          +" active="+f.world.getEntityManager().isActive(target)+" position="+f.world.getMapper(Position.class).has(target));
      assertEquals(0f,v.y,.00001f);
      f.input(false,Vector2.Zero);for(int i=0;i<40;i++)f.step();assertEquals(0,f.liveStreams());
    }
  }

  @Test void realCasterDeathEventPreventsThePendingVisualFromBeingPublished() {
    try(ArcticBlastSequenceIntegrationTest.Fixture f=new ArcticBlastSequenceIntegrationTest.Fixture()) {
      pending(f,target(f));
      f.world.getSystem(EventSystem.class).dispatch(DeathEvent.obtain(-1,f.caster));
      f.step();assertEquals(-1,visual(f));assertFalse(f.world.getMapper(Casting.class).has(f.caster));
      for(int i=0;i<40;i++)f.step();assertEquals(0,f.liveStreams());
    }
  }

  @Test void casterRemovalBeforeConsumptionCannotCreateAnOwnerlessStream() {
    try(ArcticBlastSequenceIntegrationTest.Fixture f=new ArcticBlastSequenceIntegrationTest.Fixture()) {
      pending(f,target(f));f.world.delete(f.caster);f.step();assertEquals(-1,visual(f));
      for(int i=0;i<40;i++)f.step();assertEquals(0,f.liveStreams());
    }
  }
}
