package com.riiablo.engine.server;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.RiiabloTest;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FingerMageCurseTest extends RiiabloTest {
  @Test void nativeHitRefreshesDurationAndReplacesManaRecoveryPenalty() {
    MissileCollisionSystem collisions = new MissileCollisionSystem();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), collisions).build());
    try {
      int owner = world.create();
      int victim = world.create();
      Missile missile = new Missile();
      missile.ownerId = owner;
      missile.skillId = 196;
      missile.damageLevel = 3;
      collisions.applyFingerMageCurse(missile, victim);
      com.riiablo.engine.server.state.UnitState state = world.getMapper(UnitStates.class).get(victim).stateList
          .getState(StateId.FINGERMAGECURSE);
      assertNotNull(state);
      assertEquals(50, state.duration);
      assertEquals(-72, state.getStatContributionValue(Stat.manarecovery));
      assertEquals(196, state.skillId);
      missile.damageLevel = 1;
      collisions.applyFingerMageCurse(missile, victim);
      assertEquals(30, state.duration, "native expiry is replaced, not max-refreshed");
      assertEquals(-24, state.getStatContributionValue(Stat.manarecovery));
      assertTrue(state.needsSync);
      assertEquals("fingermagespiderexplode", Riiablo.files.Missiles.get(177).ProgOverlay);
    } finally { world.dispose(); }
  }
}
