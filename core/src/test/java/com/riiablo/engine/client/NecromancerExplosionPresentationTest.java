package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    Riiablo.audio = new SilentAudio();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new SkillCastHandler(), new OverlayManager(), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int caster = world.create();
      int corpse = world.create();
      world.getMapper(Position.class).create(caster).position.set(2, 3);
      world.getMapper(Position.class).create(corpse).position.set(11, 13);

      assertBurst(world, factory, caster, corpse, SkillId.CORPSE_EXPLOSION,
          "corpseexplosion");
      assertBurst(world, factory, caster, corpse, SkillId.POISON_EXPLOSION,
          "poisoncorpseexplosion");
    } finally {
      world.dispose();
      Riiablo.audio = previous;
    }
  }

  private static void assertBurst(World world, RecordingFactory factory,
      int caster, int corpse, int skillId, String missile) {
    Skills.Entry skill = Riiablo.files.skills.get(skillId);
    assertNotNull(skill);
    factory.lastMissile = null;
    world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
        caster, skill.Id, corpse, new Vector2(-100, -100),
        skill.srvdofunc, skill.cltdofunc));
    assertEquals(missile, factory.lastMissile);
    assertEquals(11f, factory.lastPosition.x);
    assertEquals(13f, factory.lastPosition.y);
  }

  private static final class SilentAudio extends Audio {
    SilentAudio() { super(null); }
    @Override public Instance play(String id, boolean global) { return null; }
  }

  private static final class RecordingFactory extends EntityFactory {
    String lastMissile;
    final Vector2 lastPosition = new Vector2();

    @Override public int createMissile(int id, Vector2 direction, Vector2 position) {
      Missiles.Entry row = Riiablo.files.Missiles.get(id);
      lastMissile = row != null ? row.Missile : null;
      lastPosition.set(position);
      return 1;
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
