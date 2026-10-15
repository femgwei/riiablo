package com.riiablo.engine.client;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.codec.COF;
import com.riiablo.engine.SimulationClock;
import com.riiablo.engine.client.component.AnimationWrapper;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimationStepperTest extends RiiabloTest {
  @Test void localDiabRunFollowsNativeRnSeeksInsteadOfLoopingResidentCof() {
    Riiablo.colors = new com.riiablo.Colors();
    World world = new World(new WorldConfigurationBuilder().with(new AnimationStepper()).build());
    try {
      int id = world.create();
      Animation animation = world.getMapper(AnimationWrapper.class).create(id).animation;
      animation.setCOF(COF.loadFromFile(Riiablo.mpqs.resolve("data/global/monsters/DI/COF/DIRNHTH.cof")));
      AnimData data = world.getMapper(AnimData.class).create(id);
      data.speed = 256;
      world.getMapper(com.riiablo.engine.server.component.Sequence.class).create(id).nativeDiabRun = true;
      world.setDelta(SimulationClock.STEP_SECONDS);
      for (int frame : new int[] {5, 13, 7, 14, 18, 21}) {
        data.frame = frame << 8; world.process(); assertEquals(frame, animation.getFrame());
      }
    } finally { world.dispose(); }
  }
  @Test void localMonInfernoFollowsAuthoritativeRollbackAndMissileAnimationRate() {
    Riiablo.colors = new com.riiablo.Colors();
    World world = new World(new WorldConfigurationBuilder().with(new AnimationStepper()).build());
    try {
      int id = world.create();
      Animation animation = world.getMapper(AnimationWrapper.class).create(id).animation;
      animation.setCOF(COF.loadFromFile(Riiablo.mpqs.resolve("data/global/monsters/DM/COF/DMS1HTH.cof")));
      AnimData data = world.getMapper(AnimData.class).create(id);
      data.speed = 256;
      world.getMapper(com.riiablo.engine.server.component.Sequence.class).create(id);
      com.riiablo.engine.server.component.Casting cast =
          world.getMapper(com.riiablo.engine.server.component.Casting.class).create(id);
      cast.monInferno = new com.riiablo.engine.server.NativeMonInferno(Riiablo.files.skills.get(319), 6, 1);
      cast.monInferno.started = true;
      world.setDelta(SimulationClock.STEP_SECONDS);
      for (int frame : new int[] {10, 11, 10, 11, 0}) {
        data.frame = frame << 8;
        world.process();
        assertEquals(frame, animation.getFrame());
      }
      cast.monInferno = null;
      world.process();
      assertEquals(1, animation.getFrame());
      com.riiablo.engine.server.component.Missile missile = new com.riiablo.engine.server.component.Missile();
      missile.missile = Riiablo.files.Missiles.get("megademoninferno");
      missile.skillId = 319; missile.nativeLifetimeFrames = 17;
      assertEquals((missile.missile.AnimLen << 8) / 17, MissileLoader.nativeFrameDelta(missile));
      missile.missile = Riiablo.files.Missiles.get("diablight");
      missile.skillId = 193; missile.nativeLifetimeFrames = 19;
      assertEquals((missile.missile.AnimLen << 8) / 19, MissileLoader.nativeFrameDelta(missile));
      missile.skillId = -1;
      assertEquals(missile.missile.animrate > 0 ? missile.missile.animrate >> 2 : 256,
          MissileLoader.nativeFrameDelta(missile));
    } finally { world.dispose(); }
  }
  @Test void frogHiddenFollowsSourceZeroAndEmergenceClearsTheHold() {
    Riiablo.colors = new com.riiablo.Colors();
    World world = new World(new WorldConfigurationBuilder().with(new AnimationStepper()).build());
    try {
      int id = world.create();
      Animation animation = world.getMapper(AnimationWrapper.class).create(id).animation;
      animation.setCOF(COF.loadFromFile(Riiablo.mpqs.resolve("data/global/monsters/FD/COF/FDS1HTH.cof")));
      AnimData data = world.getMapper(AnimData.class).create(id);
      data.speed = 256;
      data.frame = 0;
      data.override = 0;
      com.riiablo.engine.server.component.Sequence seq =
          world.getMapper(com.riiablo.engine.server.component.Sequence.class).create(id)
              .sequence(com.riiablo.engine.Engine.Monster.MODE_S1, com.riiablo.engine.Engine.Monster.MODE_NU);
      seq.nativeFrogHidden = seq.nativeSubmergeHeld = true;
      world.setDelta(SimulationClock.STEP_SECONDS);
      for (int tick = 0; tick < 30; tick++) world.process();
      assertEquals(0, animation.getFrame());
      seq.sequence(com.riiablo.engine.Engine.Monster.MODE_S1, com.riiablo.engine.Engine.Monster.MODE_NU);
      data.override = -1;
      world.process();
      assertEquals(1, animation.getFrame());
    } finally { world.dispose(); }
  }
  @Test void residentPlayerAnimationFollowsRateChangesWithoutReloadingCof() {
    Riiablo.colors = new com.riiablo.Colors();
    World world = new World(new WorldConfigurationBuilder().with(new AnimationStepper()).build());
    try {
      int id = world.create();
      world.getMapper(Player.class).create(id);
      AnimData data = world.getMapper(AnimData.class).create(id);
      data.speed = 256;
      data.override = 0;
      Animation animation = world.getMapper(AnimationWrapper.class).create(id).animation;
      COF cof = COF.loadFromFile(Riiablo.mpqs.resolve("data/global/chars/AM/COF/AMRNHTH.cof"));
      animation.setCOF(cof);
      animation.setFrameDelta(0);
      world.setDelta(SimulationClock.STEP_SECONDS);
      world.process();
      assertEquals(0, animation.getFrameDelta());

      // Asset loading has already finished: no CofLoadingComponents exists.
      data.override = 101;
      for (int tick = 0; tick < 4; tick++) world.process();
      assertSame(cof, animation.getCOF());
      assertEquals(101, animation.getFrameDelta());
      assertTrue(animation.getFrame() > 0, "a resident RN COF must recover from zero rate");

      int pausedFrame = animation.getFrame();
      data.override = 0;
      world.process();
      assertEquals(0, animation.getFrameDelta(), "zero remains a legitimate authoritative rate");
      assertEquals(pausedFrame, animation.getFrame(), "zero rate must hold, not rewind, the frame");
      data.override = -1;
      world.process();
      assertEquals(data.speed, animation.getFrameDelta(), "removing override restores base speed");
    } finally { world.dispose(); }
  }

  @Test void objectVisualRateIsNotOverwrittenByUnitAnimData() {
    Riiablo.colors = new com.riiablo.Colors();
    World world = new World(new WorldConfigurationBuilder().with(new AnimationStepper()).build());
    try {
      int id = world.create();
      world.getMapper(com.riiablo.engine.server.component.Object.class).create(id);
      world.getMapper(AnimData.class).create(id).speed = 256;
      Animation animation = world.getMapper(AnimationWrapper.class).create(id).animation;
      animation.setFrameDelta(192);
      world.setDelta(SimulationClock.STEP_SECONDS);
      world.process();
      assertEquals(192, animation.getFrameDelta(), "preserve Objects.txt visual variation");
    } finally { world.dispose(); }
  }

  @Test void nativeFreezeAndJabKeepTheirAuthoritativeFrameControl() {
    Riiablo.colors = new com.riiablo.Colors();
    World world = new World(new WorldConfigurationBuilder().with(new AnimationStepper()).build());
    try {
      int id = world.create();
      Animation animation = world.getMapper(AnimationWrapper.class).create(id).animation;
      animation.setCOF(COF.loadFromFile(Riiablo.mpqs.resolve("data/global/chars/AM/COF/AMRNHTH.cof")));
      AnimData data = world.getMapper(AnimData.class).create(id);
      data.speed = 256;
      com.riiablo.engine.server.component.UnitStates states =
          world.getMapper(com.riiablo.engine.server.component.UnitStates.class).create(id).init(id);
      states.stateList.addState(com.riiablo.engine.server.state.StateId.FREEZE, 25, 1, id);
      world.setDelta(SimulationClock.STEP_SECONDS);
      for (int tick = 0; tick < 4; tick++) world.process();
      assertEquals(0, animation.getFrame());
      world.getMapper(com.riiablo.engine.server.component.UnitStates.class).remove(id);
      com.riiablo.engine.server.component.Sequence sequence =
          world.getMapper(com.riiablo.engine.server.component.Sequence.class).create(id);
      sequence.nativeJab = true;
      data.frame = 3 << 8;
      world.process();
      assertEquals(3, animation.getFrame(), "Jab still seeks the native sequence source frame");
    } finally { world.dispose(); }
  }

  @Test void submergeReversesResidentS1HoldsZeroAndEmergeResumesForwardPlayback() {
    Riiablo.colors = new com.riiablo.Colors();
    World world = new World(new WorldConfigurationBuilder().with(new AnimationStepper()).build());
    try {
      int id = world.create();
      Animation animation = world.getMapper(AnimationWrapper.class).create(id).animation;
      animation.setCOF(COF.loadFromFile(Riiablo.mpqs.resolve("data/global/monsters/TN/COF/TNS1HTH.cof")));
      AnimData data = world.getMapper(AnimData.class).create(id);
      data.speed = 256;
      com.riiablo.engine.server.component.Sequence seq =
          world.getMapper(com.riiablo.engine.server.component.Sequence.class).create(id)
              .sequence(com.riiablo.engine.Engine.Monster.MODE_S1, com.riiablo.engine.Engine.Monster.MODE_NU);
      seq.nativeSubmerge = true;
      world.setDelta(SimulationClock.STEP_SECONDS);
      for (int frame = 12; frame >= 0; frame--) {
        data.frame = frame << 8;
        world.process();
        assertEquals(frame, animation.getFrame());
      }
      seq.nativeSubmergeHeld = true;
      data.override = 0;
      for (int tick = 0; tick < 30; tick++) world.process();
      assertEquals(0, animation.getFrame());
      seq.sequence(com.riiablo.engine.Engine.Monster.MODE_S1, com.riiablo.engine.Engine.Monster.MODE_NU);
      data.override = -1;
      world.process();
      assertEquals(1, animation.getFrame(), "emergence must no longer seek the old submerged frame");
    } finally { world.dispose(); }
  }
}
