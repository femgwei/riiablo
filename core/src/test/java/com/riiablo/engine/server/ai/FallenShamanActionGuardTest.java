package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static com.riiablo.engine.server.ai.LegacyFsmCheckpointIntegrationTest.fixture;

import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.Monster;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FallenShamanActionGuardTest extends RiiabloTest {
  @ParameterizedTest
  @ValueSource(ints={0,1})
  void activeResurrectionOrFireballCannotBeReplacedByAnotherThink(int skillSlot) {
    try(Fixture f=fixture("FallenShaman",8)) {
      FallenShaman ai=(FallenShaman)f.ai;
      ai.params=new int[]{0,100,0,15,20,0,0,0};
      assertTrue(ai.useMonsterSkill(skillSlot,f.target,f.targetPosition(),
          FallenShaman.SHAMAN_SEQUENCE_MODE));
      long action=f.casting().actionId;
      Object sequence=f.sequence();
      Monster monster=f.world.getMapper(Monster.class).get(f.actor);
      int low=monster.rngState;
      ai.time=.2f;
      ai.update(10f);
      assertEquals(action,f.casting().actionId,"active skill must reach its keyframe before another decision");
      assertSame(sequence,f.sequence());
      assertEquals(low,monster.rngState,"an active action must not consume AI random draws");
      assertEquals(.2f,ai.time,.00001f,"post-action delay starts after completion");
    }
  }
}
