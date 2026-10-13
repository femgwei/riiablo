package com.riiablo.engine.server;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.engine.server.ai.AI;
import com.riiablo.engine.server.component.AIWrapper;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AIStepperTest {
  @Test void groundTargetCastHasNoEntityButStillLogsTheDecisionSafely() {
    World world = new World(new WorldConfigurationBuilder().with(new AIStepper()).build());
    try {
      int actorId = world.create();
      world.getMapper(Position.class).create(actorId);
      world.getMapper(Monster.class).create(actorId);
      world.getMapper(AIWrapper.class).create(actorId).ai = new AI(actorId) {
        @Override public boolean updateSpecialAiControl(float delta) { return false; }
        @Override public void update(float delta) {
          Casting cast = world.getMapper(Casting.class).create(actorId);
          cast.skillId = 1;
          cast.targetId = -1;
          cast.targetVec.set(10, 20);
        }
      };
      assertDoesNotThrow(world::process);
    } finally { world.dispose(); }
  }
}
