package com.riiablo.engine.replay;

import static org.junit.jupiter.api.Assertions.*;
import com.artemis.World;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.*;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.state.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UnitStateAttributeCheckpointTest extends RiiabloTest {
  @Test void encodedListsRevisionCombatPresenceAndStaminaOperandsRoundTripWithoutRebuilding() {
    World source = new World(), target = new World();
    try {
      assertEquals(0, source.create()); assertEquals(0, target.create());
      UnitStates holder = source.getMapper(UnitStates.class).create(0).init(source, 0);
      holder.stateList.addState(StateId.BATTLEORDERS, 10).maxLifeModifier = 31;
      Attributes attrs = Attributes.obtainLarge();
      attrs.base().putEncoded(Stat.maxstamina, 25701);
      attrs.base().putEncoded(Stat.stamina, 10243);
      attrs.base().putEncoded(Stat.maxhp, 10241);
      attrs.base().putEncoded(Stat.hitpoints, 7654);
      attrs.reset();
      attrs.aggregate().putEncoded(Stat.maxhp, 13415); // Already folded, must not rebuild.
      attrs.remaining().putEncoded(Stat.firemindam, 3, 1234);
      attrs.setCombatBlockContext(false, 47);
      attrs.setCombatRunning(true);
      attrs.setDifficultyResistanceIncluded(true);
      attrs.restoreAggregateRevision(10000000001L);
      attrs.staminaMaximum().applyStatePercent(attrs, 37, 23);
      holder.observedAggregateRevision = attrs.aggregateRevision();
      source.getMapper(AttributesWrapper.class).create(0).attrs = attrs;
      target.getMapper(AttributesWrapper.class).create(0).attrs = Attributes.obtainLarge();
      Map<Integer, UnitStateRuntimeCheckpoint> saved = UnitStateRuntimeCheckpoint.capture(source, new int[] {0});
      UnitStateRuntimeCheckpoint.restore(target, saved);
      Attributes restored = target.getMapper(AttributesWrapper.class).get(0).attrs;
      assertEquals(attrs.aggregateRevision(), restored.aggregateRevision());
      assertEquals(13415, restored.aggregate().get(Stat.maxhp).encodedValues());
      assertEquals(10241, restored.base().get(Stat.maxhp).encodedValues());
      assertEquals(1234, restored.remaining().get(Stat.firemindam, 3).encodedValues());
      assertTrue(restored.hasCombatShieldContext()); assertFalse(restored.combatHasShield());
      assertEquals(47, restored.combatBlockFactor());
      assertTrue(restored.hasCombatRunningContext()); assertTrue(restored.combatRunning());
      assertTrue(restored.difficultyResistanceIncluded());
      assertArrayEquals(attrs.staminaMaximum().checkpoint(), restored.staminaMaximum().checkpoint());
      assertArrayEquals(saved.get(0).bytes(), UnitStateRuntimeCheckpoint.capture(target, new int[] {0}).get(0).bytes());
      attrs.staminaMaximum().applyStatePercent(attrs, 0, 0);
      restored.staminaMaximum().applyStatePercent(restored, 0, 0);
      assertEquals(25701, restored.get(Stat.maxstamina).encodedValues());
      assertEquals(attrs.get(Stat.stamina).encodedValues(), restored.get(Stat.stamina).encodedValues());
      attrs.buildList().put(Stat.strength, 3);
      assertThrows(IllegalStateException.class, () -> UnitStateRuntimeCheckpoint.capture(source, new int[] {0}));
    } finally { source.dispose(); target.dispose(); }
  }
}
