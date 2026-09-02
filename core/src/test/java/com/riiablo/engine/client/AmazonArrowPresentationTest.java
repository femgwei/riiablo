package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Verifies client missile resources for the elemental Amazon bow skills. */
class AmazonArrowPresentationTest extends RiiabloTest {
  @Test
  void elementalArrowsUseTheirNativeClientMissileResources() {
    RecordingFactory factory = new RecordingFactory();
    SilentAudio audio = new SilentAudio();
    Audio previousAudio = Riiablo.audio;
    Riiablo.audio = audio;
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new SkillCastHandler(), new OverlayManager(), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int amazon = world.create();
      int target = world.create();
      world.getMapper(Position.class).create(amazon).position.set(2, 3);
      world.getMapper(Position.class).create(target).position.set(12, 3);
      String[] names = {"Magic Arrow", "Fire Arrow", "Cold Arrow", "Exploding Arrow",
          "Ice Arrow", "Immolation Arrow", "Freezing Arrow"};
      for (String name : names) {
        Skills.Entry skill = Riiablo.files.skills.get(name);
        assertNotNull(skill, name);
        Missiles.Entry visual = Riiablo.files.Missiles.get(skill.cltmissile);
        assertNotNull(visual, name + ":" + skill.cltmissile);
        assertFalse(visual.CelFile == null || visual.CelFile.isEmpty(), name + ":CelFile");
        factory.lastMissile = null;
        world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
            amazon, skill.Id, target, null, skill.srvdofunc, skill.cltdofunc));
        assertEquals(skill.cltmissile, factory.lastMissile, name);
        assertEquals(1f, factory.lastDirection.x, 0.0001f, name);
        assertEquals(0f, factory.lastDirection.y, 0.0001f, name);
      }
    } finally {
      world.dispose();
      Riiablo.audio = previousAudio;
    }
  }

  private static final class SilentAudio extends Audio {
    SilentAudio() { super(null); }
    @Override public Instance play(String id, boolean global) { return null; }
  }

  private static final class RecordingFactory extends EntityFactory {
    String lastMissile;
    final Vector2 lastDirection = new Vector2();

    @Override public int createMissile(int id, Vector2 direction, Vector2 position, int ownerId) {
      Missiles.Entry row = Riiablo.files.Missiles.get(id);
      lastMissile = row != null ? row.Missile : null;
      lastDirection.set(direction);
      return 1;
    }

    @Override public int createPlayer(CharData data, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMonster(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(Item item, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMissile(int id, Vector2 direction, Vector2 position) {
      return createMissile(id, direction, position, Engine.INVALID_ENTITY);
    }
  }
}
