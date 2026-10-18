package com.riiablo.engine.server;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.event.ModeChangeEvent;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SequenceHandlerTest {
  @Test void monsterAttackCompletionNotifiesAiOnEntryIntoNeutral() {
    SequenceHandler handler = new SequenceHandler();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new CofManager(), handler).build());
    try {
      int id = world.create();
      world.getMapper(CofReference.class).create(id).set("VD", Engine.Monster.MODE_A1);
      world.getMapper(AnimData.class).create(id);
      world.getMapper(com.riiablo.engine.server.component.Monster.class).create(id);
      com.riiablo.engine.server.ai.AI ai = org.mockito.Mockito.mock(com.riiablo.engine.server.ai.AI.class);
      world.getMapper(com.riiablo.engine.server.component.AIWrapper.class).create(id).ai = ai;
      world.process();
      world.getMapper(Sequence.class).create(id)
          .sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
      handler.onAnimDataFinished(
          com.riiablo.engine.server.event.AnimDataFinishedEvent.obtain(id));
      org.mockito.Mockito.verify(ai).onActionReturnedToNeutral();
    } finally { world.dispose(); }
  }

  @Test
  void monsterMonSeqJabFinishesWithoutEnteringPlayerFollowupMode() {
    SequenceHandler handler = new SequenceHandler();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new CofManager(), handler).build());
    try {
      int id = world.create();
      CofReference cof = world.getMapper(CofReference.class).create(id)
          .set("SC", Engine.Monster.MODE_A2);
      AnimData anim = world.getMapper(AnimData.class).create(id);
      anim.speed = 256;
      com.riiablo.engine.server.component.Casting casting = world.getMapper(
          com.riiablo.engine.server.component.Casting.class).create(id);
      casting.jabRemainingStrikes = 1;
      casting.jabStrikeProcessed = true;
      Sequence sequence = world.getMapper(Sequence.class).create(id)
          .sequence(Engine.Monster.MODE_A2, Engine.Monster.MODE_NU);
      sequence.started = true;
      sequence.nativeMonster = new NativeMonsterSequence(Engine.Monster.MODE_A2,
          new int[] {0, 1}, new byte[] {Engine.Monster.MODE_A2, Engine.Monster.MODE_A2},
          new byte[] {0, Engine.KEYFRAME_ATK});
      world.process();
      assertEquals(-1, anim.override, "MonSeq must not use the player's 3x Jab COF speed");
      handler.onAnimDataFinished(
          com.riiablo.engine.server.event.AnimDataFinishedEvent.obtain(id));
      assertEquals(Engine.Monster.MODE_NU, cof.mode);
      org.junit.jupiter.api.Assertions.assertFalse(world.getMapper(Sequence.class).has(id));
    } finally { world.dispose(); }
  }

  @Test
  void repeatedModeRestartsAnimationBeforeItCanSkipTheKeyframe() {
    AnimationResetProbe probe = new AnimationResetProbe();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), probe, new CofManager(), new SequenceHandler())
        .build());
    try {
      int entity = world.create();
      world.getMapper(CofReference.class).create(entity)
          .set("AM", Engine.Player.MODE_TH);
      AnimData anim = world.getMapper(AnimData.class).create(entity);
      anim.frame = 12 << 8; // already past AMTH1HT's MIS frame at index 9
      world.process();

      int baselineEvents = probe.modeEvents;
      Sequence sequence = world.getMapper(Sequence.class).create(entity)
          .sequence(Engine.Player.MODE_TH, Engine.Player.MODE_TN);
      world.process();

      assertTrue(sequence.started);
      assertEquals(baselineEvents + 1, probe.modeEvents,
          "a repeated TH action must force a new mode event");
      assertTrue(probe.lastRestart,
          "same-mode forced restart must be distinguishable from a COF change");
      assertEquals(0, anim.frame,
          "the forced event must restart animation instead of inheriting a post-keyframe frame");
    } finally {
      world.dispose();
    }
  }

  private static final class AnimationResetProbe extends PassiveSystem {
    int modeEvents;
    boolean lastRestart;

    @Subscribe
    public void onModeChanged(ModeChangeEvent event) {
      modeEvents++;
      lastRestart = event.restart;
      AnimData anim = world.getMapper(AnimData.class).get(event.entityId);
      if (anim != null) {
        anim.frame = 0;
        anim.lastKeyframeIndex = -1;
      }
    }
  }
}
