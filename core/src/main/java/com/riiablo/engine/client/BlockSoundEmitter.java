package com.riiablo.engine.client;

import com.badlogic.gdx.math.MathUtils;

import com.riiablo.Riiablo;
import com.riiablo.audio.MonsterAudio;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.event.ModeChangeEvent;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/** Plays the native shield-block impact bank when the authoritative BL mode starts. */
public class BlockSoundEmitter extends PassiveSystem {
  @Subscribe
  public void onModeChanged(ModeChangeEvent event) {
    if (event == null || event.mode != Engine.Player.MODE_BL
        && event.mode != Engine.Monster.MODE_BL) return;
    if (Riiablo.audio == null) return;
    int variant = MathUtils.random(1, 3);
    // Mode changes are replicated for every visible unit.  Route the one-shot
    // through the shared spatial gate so a distant monster/player block is not
    // audible as a global sound; the local player's own block remains full
    // volume because its listener-relative distance is zero.
    MonsterAudio.play(event.entityId, "block_weapon_" + variant, true);
  }
}
