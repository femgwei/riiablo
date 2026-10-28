package com.riiablo.presentation;




import com.riiablo.Riiablo;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Class;
import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.riiablo.engine.server.event.ModeChangeEvent;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/** Plays the native shield-block impact bank when the authoritative BL mode starts. */
@All(Class.class)
public class BlockSoundEmitter extends PassiveSystem {
  protected ComponentMapper<Class> mClass;

  @Wire(name = "clientContext")
  protected ClientPresentationServices clientContext ;

  @Subscribe
  public void onModeChanged(ModeChangeEvent event) {
    if (event == null || !mClass.has(event.entityId)) return;
    // Player and monster mode tables use different numeric indexes. A raw
    // comparison would play block audio for unrelated modes such as player
    // TW (6) or a monster special mode (9).
    Class.Type type = mClass.get(event.entityId).type;
    if (type == Class.Type.PLR) {
      if (event.mode != Engine.Player.MODE_BL) return;
    } else if (type == Class.Type.MON) {
      if (event.mode != Engine.Monster.MODE_BL) return;
    } else {
      return;
    }
    if (clientContext.audio() == null) return;
    int variant = PresentationRandom.range(1, 3);
    // Mode changes are replicated for every visible unit.  Route the one-shot
    // through the shared spatial gate so a distant monster/player block is not
    // audible as a global sound; the local player's own block remains full
    // volume because its listener-relative distance is zero.
    float gain = SpatialAudio.gain(world, clientContext.playerId(), event.entityId);
    if (gain <= 0f) return;
    com.riiablo.audio.Audio.Instance instance =
        clientContext.audio().play("block_weapon_" + variant, true);
    if (instance != null) instance.setVolume(gain);
  }
}
