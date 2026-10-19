package com.riiablo.engine.server;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.missile.FingerMageFlight;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FingerMageLaunchIntegrationTest extends RiiabloTest {
  @Test void actualSkillLaunchKeepsVictimAfterCastingEndsAndTurnsTowardMovedVictim() {
    try (MonsterComponentMissileIntegrationTest.Fixture f =
        new MonsterComponentMissileIntegrationTest.Fixture()) {
      int owner = f.spawn("fingermage2");
      int target = f.world.create();
      f.world.getMapper(Position.class).create(target).position.set(20, 10);
      f.world.process();
      assertFalse(f.world.getMapper(Target.class).has(owner));
      com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(196);
      f.events().dispatch(SkillDoEvent.obtain(owner, skill.Id, target,
          new Vector2(20, 10), skill.srvdofunc, skill.cltdofunc));
      f.world.process();
      com.artemis.utils.IntBag ids = f.world.getAspectSubscriptionManager()
          .get(com.artemis.Aspect.all(Missile.class)).getEntities();
      assertEquals(1, ids.size());
      int id = ids.get(0);
      Missile projectile = f.world.getMapper(Missile.class).get(id);
      assertEquals(target, projectile.targetId, "launch must keep the victim on the missile");
      f.world.getMapper(Casting.class).remove(owner);
      f.world.getMapper(Target.class).remove(owner);
      Vector2 position = f.world.getMapper(Position.class).get(id).position;
      Vector2 velocity = f.world.getMapper(Velocity.class).get(id).velocity;
      float speed = velocity.len();
      assertTrue(speed > 0);
      f.world.getMapper(Position.class).get(target).position.set(position.x, position.y + 10);
      FingerMageFlight.update(f.world, id, projectile, position, velocity, 5);
      assertEquals(0, velocity.x, 0.0001);
      assertEquals(speed, velocity.y, 0.0001);
      f.world.getMapper(Position.class).get(target).position.set(position.x - 10, position.y);
      FingerMageFlight.update(f.world, id, projectile, position, velocity, 10);
      assertEquals(-speed, velocity.x, 0.0001);
      assertEquals(0, velocity.y, 0.0001);
    }
  }
}
