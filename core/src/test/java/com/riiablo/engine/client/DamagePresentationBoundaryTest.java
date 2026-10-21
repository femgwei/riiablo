package com.riiablo.engine.client;

import com.riiablo.presentation.ElementalHitPresentationSystem;

import com.riiablo.presentation.DamageHandler;
import com.riiablo.presentation.OverlayManager;


import com.riiablo.presentation.ClientGameplayContext;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.utils.Array;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.audio.Audio;
import com.riiablo.engine.server.combat.DamageSettlement;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.DamageEvent;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DamagePresentationBoundaryTest extends RiiabloTest {
  @Test void absorbOnlySettlementDoesNotInventImpactAudioOrElementalEffects() {
    Audio audio = mock(Audio.class);
    RecordingOverlays overlays = new RecordingOverlays();
    World world = world(audio, overlays);
    try {
      for (float initial : new float[] {20f, 40f}) {
        int victim = unit(world);
        Attributes attrs = world.getMapper(AttributesWrapper.class).get(victim).attrs;
        attrs.base().put(Stat.maxhp, 40f);
        attrs.base().put(Stat.hitpoints, initial);
        attrs.reset();
        DamageEvent hit = DamageEvent.obtainMelee(-1, victim, 0f, 0f);
        hit.absorbedLife = 10f;
        DamageSettlement.commit(world, hit);
        DamageSettlement.commit(world, hit);
        assertEquals(Math.min(40f, initial + 10f), attrs.get(Stat.hitpoints).asFixed());
        assertEquals(initial < 40f ? 10f : 0f, hit.lifeAbsorbed);
      }
      verifyNoInteractions(audio);
      assertEquals(0, overlays.shown.size);
    } finally { world.dispose(); }
  }

  @Test void leechCueTargetsAttackerAndIsIndependentOfMissileElementalSuppression() {
    Audio audio = mock(Audio.class);
    RecordingOverlays overlays = new RecordingOverlays();
    World world = world(audio, overlays);
    try {
      int attacker = unit(world), victim = unit(world);
      DamageEvent hit = DamageEvent.obtainMissile(attacker, victim, 2, 2, null);
      hit.suppressElementalPresentation = true;
      hit.leechOverlay = "steallife";
      DamageSettlement.prepare(world, hit);
      assertEquals(0, overlays.shown.size);
      DamageSettlement.commit(world, hit);
      assertEquals(1, overlays.shown.size);
      assertEquals("steallife", overlays.shown.first());
      assertEquals(attacker, overlays.lastEntity);
      DamageSettlement.commit(world, hit);
      assertEquals(1, overlays.shown.size);
    } finally { world.dispose(); }
  }
  @Test void mutableRequestCannotPlayEffectsBeforeLifeIsCommitted() {
    Audio audio = mock(Audio.class);
    RecordingOverlays overlays = new RecordingOverlays();
    World world = world(audio, overlays);
    try {
      int victim = unit(world);
      DamageEvent hit = DamageEvent.obtainMelee(-1, victim, 10, 5);
      hit.fireDamage = 5;
      DamageSettlement.prepare(world, hit);
      verifyNoInteractions(audio);
      assertEquals(0, overlays.shown.size);
      DamageSettlement.commit(world, hit);
      assertEquals(10f, world.getMapper(AttributesWrapper.class).get(victim)
          .attrs.get(Stat.hitpoints).asFixed());
      assertEquals("fire_explode", overlays.shown.first());
      verify(audio).play("impact_fire_1", true);
      verify(audio).play("impact_blunt_1", true);
      DamageSettlement.commit(world, hit);
      assertEquals(1, overlays.shown.size);
      verifyNoMoreInteractions(audio);
    } finally { world.dispose(); }
  }

  @Test void dotAndNativeMissilePresentationDoNotReplayGenericImpacts() {
    Audio audio = mock(Audio.class);
    RecordingOverlays overlays = new RecordingOverlays();
    World world = world(audio, overlays);
    try {
      int victim = unit(world);
      DamageSettlement.commit(world, DamageEvent.obtainDamageOverTime(-1, victim, 1));
      DamageEvent nativeMissile = DamageEvent.obtainMissile(-1, victim, 2, 0, "native");
      nativeMissile.fireDamage = 2;
      nativeMissile.suppressElementalPresentation = true;
      DamageSettlement.commit(world, nativeMissile);
      verifyNoInteractions(audio);
      assertEquals(0, overlays.shown.size);
    } finally { world.dispose(); }
  }

  private static World world(Audio audio, RecordingOverlays overlays) {
    ClientGameplayContext context = mock(ClientGameplayContext.class);
    when(context.audio()).thenReturn(audio);
    return new World(new WorldConfigurationBuilder().with(new EventSystem(), overlays,
        new DamageHandler(), new ElementalHitPresentationSystem()).build()
        .register("clientContext", context));
  }

  private static int unit(World world) {
    int id = world.create();
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.hitpoints, 20f);
    attrs.reset();
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
    world.getMapper(Position.class).create(id);
    return id;
  }

  @com.artemis.annotations.All(com.riiablo.engine.client.component.Overlay.class)
  private static final class RecordingOverlays extends OverlayManager {
    final Array<String> shown = new Array<>();
    int lastEntity;
    @Override public void set(int entity, String overlay) { shown.add(overlay); lastEntity = entity; }
  }
}

