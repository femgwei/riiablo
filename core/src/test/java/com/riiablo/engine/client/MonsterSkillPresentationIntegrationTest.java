package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.annotations.All;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.audio.Audio;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.event.SkillStartEvent;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Headless verification of monster skill start sound and overlay requests. */
class MonsterSkillPresentationIntegrationTest extends RiiabloTest {
  @Test
  void nativeResurrectSkillsRequestConfiguredSoundAndHealingOverlay() {
    Skills.Entry resurrect = Riiablo.files.skills.get("Resurrect");
    Skills.Entry resurrect2 = Riiablo.files.skills.get("Resurrect2");
    assertNotNull(resurrect);
    assertNotNull(resurrect2);

    RecordingAudio audio = new RecordingAudio();
    RecordingOverlayManager overlays = new RecordingOverlayManager();
    NoopFactory factory = new NoopFactory();
    Audio previousAudio = Riiablo.audio;
    Riiablo.audio = audio;
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new SkillCastHandler(), overlays, factory)
        .build()
        .register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int shamanId = world.create();
      world.getMapper(Monster.class).create(shamanId);

      dispatch(world, shamanId, resurrect);
      assertEquals(1, audio.requests);
      assertEquals("fallenshaman_resurrect_cast", audio.lastSound);
      assertEquals(1, overlays.requests);
      assertEquals(shamanId, overlays.lastEntity);
      assertEquals("healing", overlays.lastOverlay);

      // Resurrect2 intentionally has no start sound in the native table, but
      // it still requests the shared healing cast overlay.
      dispatch(world, shamanId, resurrect2);
      assertEquals(1, audio.requests);
      assertEquals(2, overlays.requests);
      assertEquals("healing", overlays.lastOverlay);

      System.out.println("[MONSTER_SKILL_PRESENTATION_TEST] resurrectSound="
          + resurrect.stsound + " resurrectOverlay=" + resurrect.castoverlay
          + " resurrect2Sound=" + resurrect2.stsound
          + " resurrect2Overlay=" + resurrect2.castoverlay + " status=PASS");
    } finally {
      world.dispose();
      Riiablo.audio = previousAudio;
    }
  }

  private static void dispatch(World world, int entityId, Skills.Entry skill) {
    world.getSystem(EventSystem.class).dispatch(SkillStartEvent.obtain(
        entityId, skill.Id, -1, new Vector2(1, 2), skill.srvstfunc, skill.cltstfunc));
  }

  private static final class RecordingAudio extends Audio {
    int requests;
    String lastSound;

    RecordingAudio() {
      super(null);
    }

    @Override
    public Instance play(String id, boolean global) {
      requests++;
      lastSound = id;
      return null;
    }
  }

  @All(com.riiablo.engine.client.component.Overlay.class)
  private static final class RecordingOverlayManager extends OverlayManager {
    int requests;
    int lastEntity;
    String lastOverlay;

    @Override
    public void set(int entityId, String overlayId) {
      requests++;
      lastEntity = entityId;
      lastOverlay = overlayId;
    }
  }

  private static final class NoopFactory extends EntityFactory {
    @Override public int createPlayer(CharData data, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMonster(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(Item item, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMissile(int id, Vector2 angle, Vector2 position) { return Engine.INVALID_ENTITY; }
  }
}
