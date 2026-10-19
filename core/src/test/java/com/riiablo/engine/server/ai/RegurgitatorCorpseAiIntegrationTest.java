package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.skill.SkillCodes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Fn071's conditional A2 remains available only after the corpse-eating phase. */
class RegurgitatorCorpseAiIntegrationTest extends RiiabloTest {
  @ParameterizedTest
  @ValueSource(strings = {"regurgitator1", "regurgitator2", "regurgitator3", "regurgitator4"})
  void eatingACorpseStillEnablesOneA2ThenReturnsToOrdinaryPursuit(String rowId) {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Act1NativeAiIntegrationTest.Fixture f =
          new Act1NativeAiIntegrationTest.Fixture(rowId, difficulty, 10)) {
        Regurgitator ai = (Regurgitator) f.ai;
        ai.params[1] = ai.params[2] = ai.params[4] = 100;
        int corpse = f.ally("zombie1", 0, 11, 10);
        assertTrue(f.world.getMapper(Monster.class).get(corpse).monstats2.soft);
        f.world.getMapper(CofReference.class).create(corpse).mode = Engine.Monster.MODE_DD;
        f.world.process();

        ai.think(f.target, 10, false); // select corpse, phase 2
        verify(f.paths).findNativePath(f.actor, corpse, 1, 0);
        assertNull(f.casting(), "unloaded monster must not fire A2");
        ai.think(f.target, 10, false); // corpse in eating range, phase 3
        ai.think(f.target, 10, false); // eat, phase 4
        assertNotNull(f.casting());
        assertEquals(Riiablo.files.skills.get("RegurgitatorEat").Id, f.casting().skillId);
        assertEquals(corpse, f.casting().targetId);
        f.finishAction();

        ai.think(f.target, 10, false); // loaded: A2 without approaching
        assertEquals(SkillCodes.attack, f.casting().skillId);
        assertEquals(Engine.Monster.MODE_A2, f.sequence().mode1);
        assertEquals(f.target, f.casting().targetId);
        f.finishAction();
        f.world.delete(corpse);
        f.world.process();
        ai.think(f.target, 10, false); // phase 5: escape and unload
        ai.think(f.target, 10, false); // ordinary chase, not another A2
        assertNull(f.casting());
        assertEquals("APPROACH", ai.getState());
        verify(f.paths).findPath(f.actor, f.targetPosition(), false, f.target);
      }
    }
  }
}
