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

  @ParameterizedTest @EnumSource(HitReactionResolver.Source.class)
  void maggotEggWithoutReactionModesKeepsItsAction(HitReactionResolver.Source source) {
    World world = world();
    try {
      for (String name : new String[] {"maggotegg1", "maggotegg2", "maggotegg3"}) {
        int id = monster(world, name);
        com.riiablo.engine.server.component.Monster monster = world.getMapper(
            com.riiablo.engine.server.component.Monster.class).get(id);
        assertFalse(monster.monstats2.mMode[Engine.Monster.MODE_GH]);
        assertFalse(monster.monstats2.mMode[Engine.Monster.MODE_BL]);
        Sequence sequence = world.getMapper(Sequence.class).create(id)
            .sequence(Engine.Monster.MODE_S1, Engine.Monster.MODE_NU);
        Casting casting = world.getMapper(Casting.class).create(id).set(
            Riiablo.files.skills.get("Attack").Id, -1, new Vector2());
        for (boolean blocked : new boolean[] {false, true}) {
          HitReactionResolver.queue(world, Riiablo.files, id, blocked, source);
          assertEquals(Engine.Monster.MODE_NU, world.getMapper(CofReference.class).get(id).mode);
          assertSame(sequence, world.getMapper(Sequence.class).get(id));
          assertEquals(Engine.Monster.MODE_S1, sequence.mode1);
          assertSame(casting, world.getMapper(Casting.class).get(id));
          assertEquals(19, world.getMapper(com.riiablo.engine.server.component.NativeAiState.class)
              .get(id).previousMode);
        }
      }
    } finally { world.dispose(); }
  }

  @ParameterizedTest @EnumSource(HitReactionResolver.Source.class)
  void monsterWithHitModeStillEntersHardReaction(HitReactionResolver.Source source) {
    World world = world();
    try {
      int id = monster(world, "skeleton1");
      assertTrue(world.getMapper(com.riiablo.engine.server.component.Monster.class)
          .get(id).monstats2.mMode[Engine.Monster.MODE_GH]);
      HitReactionResolver.queue(world, Riiablo.files, id, false, source);
      assertEquals(Engine.Monster.MODE_GH, world.getMapper(CofReference.class).get(id).mode);
      assertEquals(Engine.Monster.MODE_GH, world.getMapper(Sequence.class).get(id).mode1);
    } finally { world.dispose(); }
  }

  private static int monster(World world, String name) {
    int id = world.create();
    com.riiablo.codec.excel.MonStats.Entry row = Riiablo.files.monstats.get(name);
    world.getMapper(Class.class).create(id).type = Class.Type.MON;
    world.getMapper(com.riiablo.engine.server.component.Monster.class).create(id)
        .set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
    world.getMapper(CofReference.class).create(id).set(row.Code, Engine.Monster.MODE_NU);
    world.getMapper(AnimData.class).create(id).numFrames = 512;
    world.process();
    return id;
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
