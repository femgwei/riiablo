package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.riiablo.audio.MonsterAudio;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Object;
import com.riiablo.engine.server.event.ModeChangeEvent;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/** One-shot break sounds from the retail 1.10f D2Client object-mode sound table. */
public class ObjectBreakSoundEmitter extends PassiveSystem {
  protected ComponentMapper<Object> mObject;

  @Subscribe
  public void onModeChanged(ModeChangeEvent event) {
    if (event.mode != Engine.Object.MODE_OP || event.restart) return;
    Object object = mObject.get(event.entityId);
    if (object == null || object.base == null) return;
    String sound = breakSound(object.base.Id);
    if (sound != null) play(event.entityId, sound);
  }

  protected void play(int entityId, String sound) {
    MonsterAudio.play(entityId, sound, true);
  }

  static String breakSound(int objectId) {
    // D2Client 1.10f: table 0x6FB957F0[classId], mode OP slot (+4).
    // The bank names retain Sounds.txt's random variants via Audio.play.
    switch (objectId) {
      case 7: return "object_wood_break_1";
      case 11: return "object_barrel_explode";
      case 4: case 9: case 52: case 94: case 95:
      case 444: case 445: case 468: case 469: case 470: case 471:
        return "object_urn_break_1";
      case 208: case 209: return "object_basket_1";
      case 362: return "object_dungeonbasket";
      default: return null;
    }
  }
}
