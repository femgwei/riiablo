package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.event.MissileImpactEvent;
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

  @Test
  void fireArrowImpactUsesNativeHitSoundAndExplosionWiring() {
    Missiles.Entry fireArrow = Riiablo.files.Missiles.get("firearrow");
    assertNotNull(fireArrow);
    assertEquals("sorceress_firebolt_impact_1", fireArrow.HitSound);
    assertEquals("fireexplode", fireArrow.ExplosionMissile);
    assertTrue(fireArrow.CltHitSubMissile != null);
    for (String child : fireArrow.CltHitSubMissile) {
      assertTrue(child == null || child.isEmpty(),
          "plain Fire Arrow must not invent a client hit sub-missile");
    }
  }

  @Test
  void elementalImpactRowsExposeClientHitSubMissiles() {
    Missiles.Entry exploding = Riiablo.files.Missiles.get("explodingarrow");
    Missiles.Entry freezing = Riiablo.files.Missiles.get("freezingarrow");
    assertNotNull(exploding);
    assertNotNull(freezing);
    assertEquals("fireexplosion2", exploding.CltHitSubMissile[0]);
    assertEquals("freezingarrowexp1", freezing.CltHitSubMissile[0]);
    assertEquals("freezingarrowexp2", freezing.CltHitSubMissile[1]);
  }

  @Test
  void nativeMissileHitClassSoundMappingKeepsArrowTargetRule() {
    assertEquals("impact_fire_1",
        MissileImpactPresentationSystem.hitClassSound(32, Engine.INVALID_ENTITY));
    assertEquals("impact_arrow_1",
        MissileImpactPresentationSystem.hitClassSound(10, 1));
    assertNull(
        MissileImpactPresentationSystem.hitClassSound(10, Engine.INVALID_ENTITY));
  }

  @Test
  void impactEventPreservesMissileFacingForDirectionalHitAnimation() {
    MissileImpactEvent event = MissileImpactEvent.obtain(7, 8, 9, 10,
        new Vector2(12f, 13f), new Vector2(0f, 4f));
    assertEquals(0f, event.dx, 0.0001f);
    assertEquals(1f, event.dy, 0.0001f);
  }

  @Test
  void movingHitMissilesUseDistanceInsteadOfRangeAsFrameLifetime() {
    Missiles.Entry moving = Riiablo.files.Missiles.get("frozenorbnova");
    Missiles.Entry stationary = Riiablo.files.Missiles.get("freezingarrowexp1");
    assertNotNull(moving);
    assertNotNull(stationary);
    assertEquals(0, MissileImpactPresentationSystem.nativePresentationLifetimeFrames(moving));
    assertEquals(stationary.Range,
        MissileImpactPresentationSystem.nativePresentationLifetimeFrames(stationary));
  }

  @Test
  void frozenOrbClientHitScatterUsesNativeHitParStride() {
    Missiles.Entry orb = Riiablo.files.Missiles.get("frozenorb");
    assertNotNull(orb);
    assertEquals(30, orb.pCltHitFunc);
    assertEquals(4, MissileImpactPresentationSystem.clientHitStep(orb));
    // D2MOO walks the 64-point table in steps of HitPar1=4.
    assertEquals(16, MissileImpactPresentationSystem.clientHitScatterCount(orb));

    Missiles.Entry dense = new Missiles.Entry();
    dense.cHitPar = new int[] {1, 0, 0};
    assertEquals(64, MissileImpactPresentationSystem.clientHitScatterCount(dense));
  }

  @Test
  void clientFlightCallbacksReadTheirNativeFrameInterval() {
    Missiles.Entry lightning = Riiablo.files.Missiles.get("chainlightning2");
    Missiles.Entry vines = Riiablo.files.Missiles.get("vines");
    assertNotNull(lightning);
    assertNotNull(vines);
    assertEquals(8, lightning.pCltDoFunc);
    assertEquals(3, MissileImpactPresentationSystem.cltParam(lightning, 0, 1));
    assertEquals(49, vines.pCltDoFunc);
    assertEquals(9, MissileImpactPresentationSystem.cltParam(vines, 0, 1));
  }

  @Test
  void holyBoltDelayUsesAuthoritativeServerChild() {
    Missiles.Entry delay = Riiablo.files.Missiles.get("fistoftheheavensdelay");
    assertNotNull(delay);
    assertEquals(26, delay.pCltHitFunc);
    assertEquals(22, delay.pSrvHitFunc);
    assertEquals("fistoftheheavensbolt", delay.CltHitSubMissile[0]);
  }

  @Test
  void freezingArrowHitFunctionUsesNativeDefaultAndTableOverride() {
    Missiles.Entry freezing = Riiablo.files.Missiles.get("freezingarrow");
    assertNotNull(freezing);
    assertEquals(4, MissileImpactPresentationSystem.clientHit14RadialCount(freezing));
    Missiles.Entry custom = new Missiles.Entry();
    custom.cHitPar = new int[] {6, 0, 0};
    assertEquals(6, MissileImpactPresentationSystem.clientHit14RadialCount(custom));
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
