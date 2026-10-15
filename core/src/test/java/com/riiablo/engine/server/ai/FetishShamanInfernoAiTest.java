package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;
import org.junit.jupiter.api.Test;

class FetishShamanInfernoAiTest extends RiiabloTest {
  @Test void infernoUsesStrictSkillLevelRangeAcrossFreshWorlds() {
    for (float distance : new float[] {3, 7, 3}) {
      try (Act1NativeAiIntegrationTest.Fixture f = fixture(distance)) {
        f.ai.update(.04f);
        if (distance < 7) f.assertSkill(0);
        else assertNull(f.casting(), "equal to level is not in inferno range");
      }
    }
  }

  @Test void booleanToggleIsConsumedOnlyAfterTheCurrentCastFinishes() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(3)) {
      f.ai.update(.04f);
      f.assertSkill(0);
      UnitStates states = f.world.getMapper(UnitStates.class).create(f.actor).init(f.actor);
      states.stateList.addState(StateId.INFERNO, 0, 7, f.actor);
      f.ai.update(10f);
      assertTrue(states.stateList.hasState(StateId.INFERNO));
      f.assertSkill(0);
      f.finishAction();
      f.ai.update(10f);
      assertFalse(states.stateList.hasState(StateId.INFERNO));
      assertNull(f.casting(), "clearing the toggle must not immediately restart the stream");
    }
  }

  private Act1NativeAiIntegrationTest.Fixture fixture(float distance) {
    Act1NativeAiIntegrationTest.Fixture f = new Act1NativeAiIntegrationTest.Fixture("fetishshaman1", 0, distance);
    f.world.getMapper(com.riiablo.engine.server.component.Class.class).create(f.target).type
        = com.riiablo.engine.server.component.Class.Type.PLR;
    f.world.process();
    return f;
  }
}
