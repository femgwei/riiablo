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
  void landingRequestsNativeDustInsteadOfReplayingLeapStart() {
    RecordingOverlayManager overlays = new RecordingOverlayManager();
    NoopFactory factory = new NoopFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new SkillCastHandler(), overlays, factory)
        .build().register("factory", factory)
        .register("clientContext", ClientGameplayContext.fromGlobals())
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int id = world.create();
      world.getMapper(com.riiablo.engine.server.component.Position.class).create(id).position.set(14, 10);
      world.getSystem(EventSystem.class).dispatch(
          com.riiablo.engine.server.event.LeapLandEvent.obtain(id));
      assertEquals(1, overlays.requests);
      assertEquals(id, overlays.lastEntity);
      assertEquals("dust", overlays.lastOverlay);
    } finally { world.dispose(); }
  }

  @Test
  void leapStartPresentationPreservesAuthoritativeAirborneProgress() {
    Skills.Entry leapSkill = Riiablo.files.skills.get("Leap");
    assertEquals(29, leapSkill.cltstfunc);
    NoopFactory factory = new NoopFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new SkillCastHandler(), new RecordingOverlayManager(),
            new com.riiablo.engine.server.LeapSystem(), factory)
        .build().register("factory", factory)
        .register("clientContext", ClientGameplayContext.fromGlobals())
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int id = world.create();
      world.getMapper(Monster.class).create(id);
      com.riiablo.engine.server.component.Position position = world.getMapper(
          com.riiablo.engine.server.component.Position.class).create(id);
      com.riiablo.engine.server.component.Leap leap = world.getMapper(
          com.riiablo.engine.server.component.Leap.class).create(id)
          .set(new Vector2(2, 3), new Vector2(8, 3), .6f, -1);
      world.setDelta(.1f);
      world.process();
      float elapsed = leap.elapsed, x = position.position.x;
      dispatch(world, id, leapSkill);
      assertEquals(elapsed, leap.elapsed);
      assertEquals(x, position.position.x);
      for (int i = 0; i < 6; i++) world.process();
      assertEquals(8f, position.position.x, .001f);
      org.junit.jupiter.api.Assertions.assertFalse(world.getMapper(
          com.riiablo.engine.server.component.Leap.class).has(id));
    } finally { world.dispose(); }
  }

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
        .register("clientContext", ClientGameplayContext.fromGlobals())
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
