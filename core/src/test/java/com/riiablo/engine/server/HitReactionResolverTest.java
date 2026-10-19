package com.riiablo.engine.server;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.combat.DefenseCalculator;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Sequence;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class HitReactionResolverTest extends RiiabloTest {
  @ParameterizedTest @EnumSource(HitReactionResolver.Source.class)
  void blockTransitionsAreSharedAndCannotRestartDeath(HitReactionResolver.Source source) {
    World world = world();
    try {
      int id = player(world, Engine.Player.MODE_A1);
      world.getMapper(Casting.class).create(id).set(
          Riiablo.files.skills.get("Attack").Id, -1, new Vector2());
      world.getMapper(Sequence.class).create(id).sequence(Engine.Player.MODE_A1, Engine.Player.MODE_NU);
      HitReactionResolver.queue(world, Riiablo.files, id, true, source);
      assertFalse(world.getMapper(Casting.class).has(id));
      assertEquals(Engine.Player.MODE_BL, world.getMapper(CofReference.class).get(id).mode);
      assertEquals(Engine.Player.MODE_BL, world.getMapper(Sequence.class).get(id).mode1);
      assertEquals(Engine.Player.MODE_NU, world.getMapper(Sequence.class).get(id).mode2);
      world.getMapper(CofReference.class).get(id).mode = Engine.Player.MODE_DT;
      HitReactionResolver.queue(world, Riiablo.files, id, false, source);
      assertEquals(Engine.Player.MODE_DT, world.getMapper(CofReference.class).get(id).mode);
    } finally { world.dispose(); }
  }

  @ParameterizedTest @EnumSource(HitReactionResolver.Source.class)
  void jabAndAmazonPassiveDefenseRetainTheirAction(HitReactionResolver.Source source) {
    World world = world();
    try {
      int id = player(world, Engine.Player.MODE_A1);
      int jab = Riiablo.files.skills.get("Jab").Id;
      assertFalse(Riiablo.files.skills.get(jab).interrupt);
      world.getMapper(Casting.class).create(id).set(jab, -1, new Vector2());
      Sequence sequence = world.getMapper(Sequence.class).create(id)
          .sequence(Engine.Player.MODE_A1, Engine.Player.MODE_NU);
      sequence.started = true;
      HitReactionResolver.queue(world, Riiablo.files, id, true, source);
      assertTrue(world.getMapper(Casting.class).has(id));
      assertEquals(Engine.Player.MODE_A1, world.getMapper(CofReference.class).get(id).mode);
      CombatSystem.CombatResult dodge = new CombatSystem.CombatResult();
      dodge.defenseType = DefenseCalculator.DEFENSE_DODGE;
      HitReactionResolver.queue(world, Riiablo.files, id, dodge, source);
      assertSame(sequence, world.getMapper(Sequence.class).get(id));
      assertEquals(Engine.Player.MODE_A1, sequence.mode1);
    } finally { world.dispose(); }
  }

  private static World world() {
    return new World(new WorldConfigurationBuilder().with(new EventSystem(), new CofManager()).build());
  }

  private static int player(World world, byte mode) {
    int id = world.create();
    world.getMapper(Class.class).create(id).type = Class.Type.PLR;
    world.getMapper(CofReference.class).create(id).set("AM", mode);
    world.getMapper(AnimData.class).create(id).numFrames = 512;
    world.process();
    return id;
  }
}
