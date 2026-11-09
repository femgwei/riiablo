package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.COF;
import com.riiablo.engine.Engine;
import com.riiablo.engine.SimulationClock;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Leap;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.serializer.CofReferenceSerializer;
import com.riiablo.engine.server.event.AnimDataFinishedEvent;
import com.riiablo.engine.server.event.AnimDataKeyframeEvent;
import com.riiablo.engine.server.event.LeapLandEvent;
import com.riiablo.net.packet.d2gs.CofReferenceP;
import com.riiablo.presentation.AnimationStepper;
import com.riiablo.presentation.component.AnimationWrapper;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import org.junit.jupiter.api.Test;

class PlayerLeapPresentationTest extends RiiabloTest {
  @Test void actualCastKeepsTakeoffAtOriginAndFinishesBothSkills() {
    for (int skill : new int[] {132, 143}) for (int distance : new int[] {1, 6}) {
      Probe probe = new Probe();
      NoopFactory factory = new NoopFactory();
      World world = new World(new WorldConfigurationBuilder().with(new EventSystem(),
          new Actioneer(), new Pathfinder(), new LeapSystem(), new CofManager(),
          new SequenceHandler(), new AnimStepper(), probe, factory).build()
          .register("factory", factory).register("map", org.mockito.Mockito.mock(com.riiablo.map.Map.class)));
      try {
        int id = world.create();
        world.getMapper(com.riiablo.engine.server.component.Class.class).create(id).type =
            com.riiablo.engine.server.component.Class.Type.PLR;
        Position position = world.getMapper(Position.class).create(id);
        position.position.set(10, 10);
        world.getMapper(com.riiablo.engine.server.component.Angle.class).create(id);
        world.getMapper(com.riiablo.engine.server.component.Velocity.class).create(id);
        world.getMapper(com.riiablo.engine.server.component.MovementModes.class).create(id)
            .set(Engine.Player.MODE_NU, Engine.Player.MODE_WL, Engine.Player.MODE_RN);
        world.getMapper(CofReference.class).create(id).set("BA", Engine.Player.MODE_NU);
        AnimData animation = world.getMapper(AnimData.class).create(id);
        animation.numFrames = 15 << 8; animation.speed = 256;
        world.getSystem(Actioneer.class).cast(id, skill, -1, new Vector2(10 + distance, 10));
        assertTrue(world.getMapper(Sequence.class).get(id).nativeLeap);
        world.setDelta(SimulationClock.STEP_SECONDS);
        world.process();
        assertEquals(Engine.Player.MODE_S1, world.getMapper(CofReference.class).get(id).mode);
        assertEquals(10f, position.position.x, .001f);
        boolean landed = false;
        for (int tick = 0; tick < 65; tick++) {
          world.process();
          if (!landed && !world.getMapper(Leap.class).has(id)) {
            landed = true;
            assertEquals(11, world.getMapper(CofReference.class).get(id).sequenceFrame,
                "even a short flight must enter the landing frame on arrival");
          }
        }
        assertTrue(landed);
        assertEquals(new Vector2(10 + distance, 10), position.position);
        assertEquals(1, probe.landings);
        assertEquals(skill == 143 ? 1 : 0, probe.strikes);
        assertFalse(world.getMapper(Sequence.class).has(id));
        assertFalse(world.getMapper(com.riiablo.engine.server.component.Casting.class).has(id));
        assertEquals(Engine.Player.MODE_NU, world.getMapper(CofReference.class).get(id).mode);
        assertEquals(-1, world.getMapper(CofReference.class).get(id).sequenceFrame);
      } finally { world.dispose(); }
    }
  }

  @Test void playerSkillsSelectRealS1AndWeaponDependentLandingTail() {
    Actioneer actions = new Actioneer();
    for (String skill : new String[] {"Leap", "Leap Attack"}) {
      assertEquals(Engine.Player.MODE_S1, actions.getMode(Riiablo.files.skills.get(skill),
          com.riiablo.engine.server.component.Class.Type.PLR));
    }
    for (byte weapon : new byte[] {Engine.WEAPON_HTH, Engine.WEAPON_1HS,
        Engine.WEAPON_2HS, Engine.WEAPON_1SS}) {
      COF cof = COF.loadFromFile(Riiablo.mpqs.resolve(
          "data/global/chars/BA/COF/BAS1" + Engine.getWClass(weapon) + ".cof"));
      assertEquals(15, cof.getNumFramesPerDir());
    }
    assertEquals(15, NativePlayerLeapSequence.frameCount(false, Engine.WEAPON_1HS));
    assertEquals(22, NativePlayerLeapSequence.frameCount(true, Engine.WEAPON_HTH));
    assertEquals(28, NativePlayerLeapSequence.frameCount(true, Engine.WEAPON_1HS));
    assertEquals(25, NativePlayerLeapSequence.frameCount(true, Engine.WEAPON_1HT));
    assertEquals(27, NativePlayerLeapSequence.frameCount(true, Engine.WEAPON_STF));
  }

  @Test void takeoffStaysAtStartFlightHoldsS1ThenLandsOnceAndPlaysA1Tail() {
    Probe probe = new Probe();
    World world = new World(new WorldConfigurationBuilder().with(new EventSystem(),
        new LeapSystem(), new CofManager(), new AnimStepper(), probe).build());
    try {
      int id = world.create();
      Position position = world.getMapper(Position.class).create(id);
      position.position.set(2, 3);
      CofReference cof = world.getMapper(CofReference.class).create(id)
          .set("BA", Engine.Player.MODE_S1);
      AnimData data = world.getMapper(AnimData.class).create(id);
      data.numFrames = 15 << 8;
      data.speed = 256;
      Sequence sequence = world.getMapper(Sequence.class).create(id)
          .sequence(Engine.Player.MODE_S1, Engine.Player.MODE_NU);
      sequence.nativeLeap = sequence.nativeLeapAttack = sequence.started = true;
      sequence.nativeLeapWeaponClass = Engine.WEAPON_1HS;
      world.getMapper(Leap.class).create(id).set(
          position.position, new Vector2(8, 3), .6f, -1);
      world.setDelta(SimulationClock.STEP_SECONDS);
      for (int i = 0; i < 5; i++) {
        world.process();
        assertEquals(2f, position.position.x, .001f, "no horizontal slide before takeoff");
      }
      world.process();
      assertTrue(position.position.x > 2 && position.position.x < 8);
      for (int i = 0; i < 8; i++) world.process();
      assertTrue(world.getMapper(Leap.class).has(id));
      assertEquals(10, sequence.nativeLeapPoint);
      assertEquals(10 << 8, data.frame);
      assertEquals(10, cof.sequenceFrame);
      assertEquals(0, probe.landings);
      while (world.getMapper(Leap.class).has(id)) world.process();
      assertEquals(8f, position.position.x, .001f);
      assertEquals(1, probe.landings);
      assertEquals(11, sequence.nativeLeapPoint);
      for (int i = 0; i < 4; i++) world.process();
      assertEquals(Engine.Player.MODE_A1, cof.mode);
      assertEquals(6, cof.sequenceFrame, "native 1HS attack tail starts at A1 frame 6");
      for (int i = 0; i < 13; i++) world.process();
      assertEquals(1, probe.strikes);
      assertEquals(1, probe.finishes);
      assertEquals(1, probe.landings);
      assertEquals(-1, cof.sequenceFrame);
    } finally { world.dispose(); }
  }

  @Test void networkReplicaDoesNotAdvanceAuthoritativePosition() {
    World world = new World(new WorldConfigurationBuilder().with(new LeapSystem(false)).build());
    try {
      int id = world.create();
      Position position = world.getMapper(Position.class).create(id);
      position.position.set(2, 3);
      Leap leap = world.getMapper(Leap.class).create(id).set(
          position.position, new Vector2(8, 3), .3f, -1);
      world.setDelta(.1f);
      world.process();
      assertEquals(new Vector2(2, 3), position.position);
      assertEquals(0f, leap.elapsed);
    } finally { world.dispose(); }
  }

  @Test void wireFrameHoldsRemoteSpriteWithoutLocalLeapOrSequence() {
    Riiablo.colors = new com.riiablo.Colors();
    World world = new World(new WorldConfigurationBuilder().with(new AnimationStepper()).build());
    try {
      int id = world.create();
      com.riiablo.codec.Animation animation = world.getMapper(AnimationWrapper.class).create(id).animation;
      animation.setCOF(COF.loadFromFile(Riiablo.mpqs.resolve("data/global/chars/BA/COF/BAS11HS.cof")));
      CofReference reference = world.getMapper(CofReference.class).create(id).set("BA", Engine.Player.MODE_S1);
      world.setDelta(SimulationClock.STEP_SECONDS);
      for (int frame : new int[] {0, 1, 4, 7, 10, 10, 10, 11, 12, 14}) {
        reference.sequenceFrame = frame;
        FlatBufferBuilder b = new FlatBufferBuilder();
        b.finish(new CofReferenceSerializer().putData(b, reference));
        reference.sequenceFrame = CofReferenceP.getRootAsCofReferenceP(b.dataBuffer()).sequenceFrame();
        world.process();
        assertEquals(frame, animation.getFrame());
      }
      reference.sequenceFrame = -1;
      animation.setFrame(0); animation.setFrameDelta(256);
      world.process();
      assertEquals(1, animation.getFrame());
      FlatBufferBuilder old = new FlatBufferBuilder();
      CofReferenceP.startCofReferenceP(old);
      CofReferenceP.addMode(old, Engine.Player.MODE_S1);
      old.finish(CofReferenceP.endCofReferenceP(old));
      assertEquals(-1, CofReferenceP.getRootAsCofReferenceP(old.dataBuffer()).sequenceFrame());
    } finally { world.dispose(); }
  }

  public static class Probe extends PassiveSystem {
    int landings, strikes, finishes;
    @Subscribe public void land(LeapLandEvent event) { landings++; }
    @Subscribe public void strike(AnimDataKeyframeEvent event) { strikes++; }
    @Subscribe public void finish(AnimDataFinishedEvent event) { finishes++; }
  }

  static class NoopFactory extends com.riiablo.engine.EntityFactory {
    @Override public int createPlayer(com.riiablo.save.CharData d, Vector2 p) { return -1; }
    @Override public int createDynamicObject(int a, int i, float x, float y) { return -1; }
    @Override public int createStaticObject(int a, int i, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int i, float x, float y) { return -1; }
    @Override public int createMonster(int i, float x, float y) { return -1; }
    @Override public int createWarp(int i, float x, float y) { return -1; }
    @Override public int createItem(com.riiablo.item.Item i, float x, float y) { return -1; }
    @Override public int createMissile(int i, Vector2 a, Vector2 p) { return -1; }
  }
}
