package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.Class;
import org.junit.jupiter.api.Test;

class ScarabBaboonPursuitTest extends RiiabloTest {
  private String row(String name) {
    for (MonStats.Entry row : Riiablo.files.monstats) if (name.equals(row.AI)) return row.Id;
    throw new AssertionError(name);
  }

  @Test void scarabRequestsPursuitWithoutPresentationClass() { assertPursuit("Scarab"); }
  @Test void baboonRequestsPursuitWithoutPresentationClass() { assertPursuit("Baboon"); }

  private void assertPursuit(String name) {

      for (int difficulty = 0; difficulty < 3; difficulty++) {
        try (Fixture f = new Fixture(row(name), difficulty, 10)) {
          assertFalse(f.world.getMapper(Class.class).has(f.target));
          f.ai.update(0.04f);
          assertEquals("APPROACH", f.ai.getState(), name + "/" + difficulty);
          verify(f.paths).findPath(eq(f.actor), eq(f.targetPosition()), eq(false), eq(f.target));
        }
      }
  }

  @Test void baboonUsesNewWorldTargetsAfterAnotherWorldWasDisposed() {
    for (int run = 0; run < 2; run++) {
      try (Fixture f = new Fixture(row("Baboon"), 0, 10)) {
        f.world.getMapper(com.riiablo.engine.server.component.Player.class).remove(f.target);
        for (int i = 0; i <= run; i++) f.world.create();
        int replacement = f.world.create();
        f.world.getMapper(com.riiablo.engine.server.component.Player.class).create(replacement);
        f.world.getMapper(com.riiablo.engine.server.component.Position.class).create(replacement).position.set(20, 10);
        f.world.getMapper(Class.class).create(replacement).type = Class.Type.PLR;
        f.world.process();
        f.ai.update(0.04f);
        assertEquals("APPROACH", f.ai.getState(), "world " + run);
        verify(f.paths).findPath(eq(f.actor), eq(new com.badlogic.gdx.math.Vector2(20, 10)), eq(false), eq(replacement));
      }
    }
  }
}
