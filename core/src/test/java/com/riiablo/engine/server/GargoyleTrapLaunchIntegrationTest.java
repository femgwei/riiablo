package com.riiablo.engine.server;

import com.artemis.Aspect;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.SkillDoEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GargoyleTrapLaunchIntegrationTest extends RiiabloTest {
  @Test void nativeLaunchClampsOnlyMinorAxisAndUsesSignedIntegerMuzzleOffset() {
    // source=(10,10); x/y dominant, negative direction, equal axes and sub-six offset.
    int[][] cases = {{40,18,30,4,14,9}, {2,-20,-4,-30,9,4},
        {-2,22,-12,4,7,9}, {18,40,4,30,9,14}, {22,22,12,4,11,9},
        {12,13,2,3,9,9}};
    for (int[] c : cases) {
      try (MonsterComponentMissileIntegrationTest.Fixture f = new MonsterComponentMissileIntegrationTest.Fixture()) {
        int owner = f.spawn("gargoyletrap");
        int target = f.world.create();
        f.world.getMapper(Position.class).create(target).position.set(c[0], c[1]);
        f.world.process();
        Skills.Entry skill = Riiablo.files.skills.get(Riiablo.files.monstats.get("gargoyletrap").Skill1);
        assertEquals(93, skill.srvdofunc);
        f.events().dispatch(SkillDoEvent.obtain(owner, skill.Id, target,
            new Vector2(999,999), skill.srvdofunc, skill.cltdofunc));
        f.world.process();
        IntBag ids = f.world.getAspectSubscriptionManager().get(Aspect.all(Missile.class)).getEntities();
        assertEquals(1, ids.size(), "exactly one authoritative missile");
        int id = ids.get(0);
        Missile missile = f.world.getMapper(Missile.class).get(id);
        assertEquals(Riiablo.files.Missiles.get(skill.srvmissilea).Id, missile.missile.Id);
        assertEquals(skill.Id, missile.skillId);
        assertEquals(new Vector2(c[4],c[5]), f.world.getMapper(Position.class).get(id).position);
        Vector2 actual = new Vector2(f.world.getMapper(Velocity.class).get(id).velocity).nor();
        Vector2 expected = new Vector2(c[2],c[3]).nor();
        assertEquals(expected.x, actual.x, .0001f);
        assertEquals(expected.y, actual.y, .0001f);
      }
    }
  }

  @Test void groundPointCannotReplaceMissingNativeTargetUnit() {
    try (MonsterComponentMissileIntegrationTest.Fixture f = new MonsterComponentMissileIntegrationTest.Fixture()) {
      int owner = f.spawn("gargoyletrap");
      f.world.process();
      Skills.Entry skill = Riiablo.files.skills.get(Riiablo.files.monstats.get("gargoyletrap").Skill1);
      f.events().dispatch(SkillDoEvent.obtain(owner, skill.Id, -1,
          new Vector2(40,18), skill.srvdofunc, skill.cltdofunc));
      f.world.process();
      assertEquals(0, f.missiles());
    }
  }
}
