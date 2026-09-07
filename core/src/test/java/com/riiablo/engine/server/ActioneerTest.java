package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.combat.CombatPositionHistory;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Size;
import org.junit.jupiter.api.Test;

class ActioneerTest {
  @Test
  void nativeResurrectSkillMayExecuteAgainstDeadTarget() {
    Skills.Entry resurrect = new Skills.Entry();
    resurrect.srvdofunc = 97;
    assertTrue(Actioneer.allowsDeadTarget(resurrect));

    Skills.Entry attack = new Skills.Entry();
    attack.srvdofunc = 1;
    assertFalse(Actioneer.allowsDeadTarget(attack));
    assertFalse(Actioneer.allowsDeadTarget(null));
  }

  @Test
  void activeMeleeCastUsesItsFrozenPositionTick() {
    CombatPositionHistory history = new CombatPositionHistory();
    World world = new World(new WorldConfigurationBuilder().build());
    try {
      int attacker = unit(world, 0, 0);
      int target = unit(world, 5, 0);
      world.process();
      history.capture(world, 1);
      world.getMapper(Position.class).get(target).position.set(20, 0);
      history.capture(world, 2);
      world.getMapper(Casting.class).create(attacker).set(0, target, new Vector2(5, 0), 1);

      Actioneer actioneer = new Actioneer();
      actioneer.mClass = world.getMapper(Class.class);
      actioneer.mPosition = world.getMapper(Position.class);
      actioneer.mSize = world.getMapper(Size.class);
      actioneer.mCasting = world.getMapper(Casting.class);
      actioneer.mPlayer = world.getMapper(Player.class);
      actioneer.mMonster = world.getMapper(Monster.class);
      actioneer.mCofReference = world.getMapper(CofReference.class);
      actioneer.combatPositionHistory = history;
      assertTrue(actioneer.isInMeleeRange(attacker, target, 3));
      assertFalse(actioneer.isInMeleeRangeAtTick(attacker, target, 3, 2));
    } finally {
      world.dispose();
    }
  }

  private static int unit(World world, float x, float y) {
    int entity = world.create();
    world.getMapper(Class.class).create(entity).type = Class.Type.PLR;
    world.getMapper(Position.class).create(entity).position.set(x, y);
    world.getMapper(Size.class).create(entity).size = Size.MEDIUM;
    return entity;
  }
}
