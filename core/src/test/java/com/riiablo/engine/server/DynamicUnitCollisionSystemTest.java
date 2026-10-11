package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.engine.server.component.Monster;
import org.junit.jupiter.api.Test;

class DynamicUnitCollisionSystemTest {
  @Test
  void ambientCrittersAreNotDynamicPathObstacles() {
    Monster critter = new Monster().set(null, new MonStats2.Entry());
    critter.monstats2.critter = true;

    assertFalse(DynamicUnitCollisionSystem.isDynamicObstacle(critter));
  }

  @Test
  void ordinaryMonstersUseMonsterFootprintInsteadOfPetPathMask() {
    Monster monster = new Monster().set(null, new MonStats2.Entry());

    assertTrue(DynamicUnitCollisionSystem.isDynamicObstacle(monster));
    assertFalse(DynamicUnitCollisionSystem.isPetCollisionMonster(monster));
  }

  @Test
  void nativePetPathMaskCoversTownAndRevivedMonsterUnits() {
    Monster townNpc = new Monster().set(new MonStats.Entry(),
        new MonStats2.Entry());
    townNpc.monstats.inTown = true;
    assertTrue(DynamicUnitCollisionSystem.isPetCollisionMonster(townNpc));

    townNpc.monstats2.isAtt = true;
    assertFalse(DynamicUnitCollisionSystem.isPetCollisionMonster(townNpc));

    Monster revived = new Monster().set(null, new MonStats2.Entry());
    revived.resurrected = true;
    assertTrue(DynamicUnitCollisionSystem.isPetCollisionMonster(revived));
  }
}
