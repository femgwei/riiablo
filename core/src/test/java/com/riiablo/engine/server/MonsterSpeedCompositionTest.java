package com.riiablo.engine.server;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.monster.MonsterAffix;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MonsterSpeedCompositionTest extends RiiabloTest {
  @Test void aiFrenzyAuraAndSlowAddBeforeOneMovementMultiplier() {
    try (DiabPrisonIntegrationTest.Fixture f = new DiabPrisonIntegrationTest.Fixture("diablo")) {
      int id = f.factory.createMonster(Riiablo.files.monstats.get("fallen1").hcIdx, 150, 150);
      Velocity velocity = f.world.getMapper(Velocity.class).get(id).setMonster(10);
      velocity.setModeSpeedBonusPercent(100);
      StateList states = f.world.getMapper(UnitStates.class).get(id).stateList;
      states.addState(StateId.MONFRENZY, 100, 1, id).velocityModifier = 50;
      states.addState(StateId.STAMINA, 100, 1, id).velocityModifier = 25;
      states.addState(StateId.SLOWED, 100, 1, id).setNativeModifier(Stat.velocitypercent, -50);
      f.world.getSystem(StateUpdater.class).refreshAfterSkillMove(id);
      assertEquals(20f, velocity.speed(false) * velocity.movementMultiplier(), 0.0001f);
      velocity.velocity.set(velocity.speed(false), 0);
      assertEquals(20f, Box2DSynchronizerPre.resolveMovementVelocity(velocity, new Vector2()).x, 0.0001f);
      velocity.clearModeSpeedBonus();
      assertEquals(10f, velocity.speed(false) * velocity.movementMultiplier(), 0.0001f,
          "clearing AI bonus must not retain a ratio computed for the previous mode");
    }
  }

  @Test void extraFastFactoryStatActuallyChangesDisplacementSpeed() {
    try (DiabPrisonIntegrationTest.Fixture f = new DiabPrisonIntegrationTest.Fixture("diablo")) {
      com.riiablo.codec.excel.MonStats.Entry row = Riiablo.files.monstats.get("fallen1");
      int id = f.factory.createMonster(row.hcIdx, 150, 150, 0, MonsterAffix.EXTRA_FAST, -1, -1);
      Velocity velocity = f.world.getMapper(Velocity.class).get(id);
      int percent = f.attrs(id).getValue(Stat.velocitypercent, 0);
      assertTrue(percent > 75);
      assertEquals(row.Velocity * Velocity.PLAYER_NATIVE_SPEED_SCALE * percent / 100f,
          velocity.speed(false) * velocity.movementMultiplier(), 0.0001f);
      f.world.getSystem(StateUpdater.class).refreshAfterSkillMove(id);
      assertEquals(row.Velocity * Velocity.PLAYER_NATIVE_SPEED_SCALE * percent / 100f,
          velocity.speed(false) * velocity.movementMultiplier(), 0.0001f);
    }
  }

  @Test void combinedSlowClampsToNativeFloorAndFreezeStillLocks() {
    Velocity velocity = new Velocity().setMonster(10);
    velocity.nativeMonsterVelocityBonus = -200;
    assertEquals(2.5f, velocity.speed(false) * velocity.movementMultiplier(), 0.0001f);
    velocity.stateMovementLocked = true;
    assertEquals(0f, velocity.movementMultiplier());
    velocity.setMonster(10);
    velocity.stateMovementLocked = false;
    assertEquals(0f, velocity.nativeMonsterVelocityBonus);
    assertEquals(7.5f, velocity.speed(false) * velocity.movementMultiplier(), 0.0001f);
  }
}
