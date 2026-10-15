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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AIStepperTest {
  @Test void aiTourSamplingIsOptInAndDoesNotChangeUpdateCount() {
    com.riiablo.logger.Logger logger = com.riiablo.logger.LogManager.getLogger(AIStepper.class);
    com.riiablo.logger.Appender previous = logger.getAppender();
    Runnable restoreLevel = logger.temporaryLevel(com.riiablo.logger.Level.DEBUG);
    java.util.List<String> samples = new java.util.ArrayList<>();
    logger.addAppender(new com.riiablo.logger.Appender() {
      @Override public com.riiablo.logger.Encoder encoder() { return previous.encoder(); }
      @Override public void append(com.riiablo.logger.LogEvent event) {
        String message = event.message().format();
        if (message.startsWith("[MONSTER_AI_TICK]")) samples.add(message);
        previous.append(event);
      }
    });
    try {
      for (boolean enabled : new boolean[] {false, true}) {
        samples.clear();
        int[] updates = {0};
        World world = new World(new WorldConfigurationBuilder().with(new AIStepper(enabled)).build());
        try {
          int actor = world.create();
          world.getMapper(Position.class).create(actor);
          world.getMapper(Monster.class).create(actor);
          world.getMapper(AIWrapper.class).create(actor).ai = new AI(actor) {
            @Override public boolean updateSpecialAiControl(float delta) { return false; }
            @Override public void update(float delta) { updates[0]++; }
            @Override public String getState() { return "WAIT"; }
          };
          for (int i = 0; i < 25; i++) world.process();
          assertEquals(25, updates[0]);
          assertEquals(enabled ? 1 : 0, samples.size());
          if (enabled) {
            assertTrue(samples.get(0).contains("state=WAIT"));
            assertTrue(samples.get(0).contains("sample=AFTER_AI_UPDATE"));
          }
        } finally { world.dispose(); }
      }
    } finally {
      logger.addAppender(previous);
      restoreLevel.run();
    }
  }

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
