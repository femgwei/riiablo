package com.riiablo.presentation;



import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.audio.Audio;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.presentation.component.AnimationWrapper;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Headless client contract for the two corpse-burst presentations. */
class NecromancerExplosionPresentationTest extends RiiabloTest {
  @Test
  void corpseAndPoisonExplosionVisualsUseAuthoritativeCorpsePosition() {
    RecordingFactory factory = new RecordingFactory();
    Audio previous = Riiablo.audio;
    com.riiablo.Colors previousColors = Riiablo.colors;
    Riiablo.audio = new SilentAudio();
    Riiablo.colors = new com.riiablo.Colors();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new SkillCastHandler(), new OverlayManager(),
            new MissileImpactPresentationSystem(), factory)
        .build().register("factory", factory)
        .register("clientContext", ClientGameplayContext.fromGlobals())
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int caster = world.create();
      int corpse = world.create();
      world.getMapper(Position.class).create(caster).position.set(2, 3);
      world.getMapper(Position.class).create(corpse).position.set(11, 13);

      assertBurst(world, factory, caster, corpse, SkillId.CORPSE_EXPLOSION,
          "corpseexplosion", "corpseexplosion", "bigblood2", "bigblood2", "bigblood2", "explodingarrowexp");
      for (int i = 0; i < 5; i++) {
        Position at = world.getMapper(Position.class).get(factory.ids.get(i));
        int radius = i < 2 ? 2 : 1;
        Vector2 anchor = i < 2 ? new Vector2(11, 13)
            : world.getMapper(Position.class).get(factory.ids.get(1)).position;
        assertTrue(at.position.x >= anchor.x - radius && at.position.x <= anchor.x + radius - 1);
        assertTrue(at.position.y >= anchor.y - radius && at.position.y <= anchor.y + radius - 1);
      }
      int flame = factory.ids.get(5);
      Missile effect = world.getMapper(Missile.class).get(flame);
      assertTrue(effect.presentationOnly);
      assertFalse(effect.authoritative);
      assertEquals(-1, effect.ownerId);
      assertEquals(16, effect.nativeLifetimeFrames);
      // The missing original flame has its own 16-tick lifetime, independent
      // of the landed gore residue. This is a headless ECS check, not GPU parity.
      for (int tick = 0; tick < 15; tick++) {
        world.setDelta(1f / 25f);
        world.process();
      }
      assertTrue(world.getEntityManager().isActive(flame));
      world.process();
      world.process(); // Artemis applies deletion at the next process boundary.
      assertFalse(world.getEntityManager().isActive(flame));
      assertBurst(world, factory, caster, corpse, SkillId.POISON_EXPLOSION,
          "poisoncorpseexplosion");
      factory.names.clear();
      Skills.Entry revive = Riiablo.files.skills.get(SkillId.REVIVE);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          caster, revive.Id, corpse, new Vector2(11, 13), revive.srvdofunc, revive.cltdofunc));
      assertTrue(factory.names.isEmpty(),
          "Revive callback must not duplicate the successful server-owned corpse burst");
    } finally {
      world.dispose();
      Riiablo.audio = previous;
      Riiablo.colors = previousColors;
    }
  }

  private static void assertBurst(World world, RecordingFactory factory,
      int caster, int corpse, int skillId, String... missiles) {
    Skills.Entry skill = Riiablo.files.skills.get(skillId);
    assertNotNull(skill);
    factory.names.clear();
    factory.ids.clear();
    world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
        caster, skill.Id, corpse, new Vector2(-100, -100),
        skill.srvdofunc, skill.cltdofunc));
    assertEquals(java.util.Arrays.asList(missiles), factory.names);
    assertEquals(11f, factory.lastPosition.x);
    assertEquals(13f, factory.lastPosition.y);
  }

  private static final class SilentAudio extends Audio {
    SilentAudio() { super(null); }
    @Override public Instance play(String id, boolean global) { return null; }
  }

  private static final class RecordingFactory extends EntityFactory {
    final java.util.List<String> names = new java.util.ArrayList<>();
    final java.util.List<Integer> ids = new java.util.ArrayList<>();
    final Vector2 lastPosition = new Vector2();

    @Override public int createMissile(int id, Vector2 direction, Vector2 position) {
      Missiles.Entry row = Riiablo.files.Missiles.get(id);
      names.add(row != null ? row.Missile : null);
      lastPosition.set(position);
      int entity = world.create();
      ids.add(entity);
      world.getMapper(Missile.class).create(entity).missile = row;
      world.getMapper(Position.class).create(entity).position.set(position);
      world.getMapper(Velocity.class).create(entity);
      world.getMapper(AnimationWrapper.class).create(entity);
      return entity;
    }

    @Override public int createPlayer(CharData data, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMonster(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(Item item, float x, float y) { return Engine.INVALID_ENTITY; }
  }
}

