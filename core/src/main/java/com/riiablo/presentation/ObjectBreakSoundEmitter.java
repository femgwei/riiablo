package com.riiablo.presentation;

import com.artemis.ComponentMapper;
import com.artemis.BaseEntitySystem;
import com.artemis.annotations.All;
import com.badlogic.gdx.utils.IntSet;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Object;
import com.riiablo.engine.server.event.ModeChangeEvent;
import net.mostlyoriginal.api.event.common.Subscribe;

/** One-shot container/search sounds from the retail 1.10f object-mode sound table. */
@All(Object.class)
public class ObjectBreakSoundEmitter extends BaseEntitySystem {
  protected ComponentMapper<Object> mObject;
  private final IntSet present = new IntSet();

  @com.artemis.annotations.Wire(name = "clientContext")
  protected ClientPresentationServices clientContext ;

  @Override protected void inserted(int entityId) { present.add(entityId); }
  @Override protected void removed(int entityId) { present.remove(entityId); }
  @Override protected void processSystem() {}

  @Subscribe
  public void onModeChanged(ModeChangeEvent event) {
    // Initial snapshots/room restoration configure modes before insertion;
    // only an already-present object's operation should emit a one-shot.
    if (event.restart || !present.contains(event.entityId)) return;
    Object object = mObject.get(event.entityId);
    if (object == null || object.base == null) return;
    String sound = operationSound(object.base.Id, event.mode);
    if (sound != null) play(event.entityId, sound);
  }

  protected void play(int entityId, String sound) {
    clientContext.playEntitySound(entityId, sound, true);
  }

  static String breakSound(int objectId) {
    return operationSound(objectId, Engine.Object.MODE_OP);
  }

  static String operationSound(int objectId, byte mode) {
    // D2Client 1.10f: table 0x6FB957F0[classId], mode slot (+4 * mode).
    // Explicit classes: identical OperateFn values can have different sounds,
    // or be silent placeholders. Keep Sounds.txt's original random banks.
    if (mode == Engine.Object.MODE_ON) {
      switch (objectId) {
        case 5: case 6: return "object_chest_large";
        case 104: case 105: case 550: case 551: return "object_armorstand";
        case 106: case 107: case 548: case 549: return "object_weaponrack";
        case 179: case 180: return "object_bookshelf";
        default: return null;
      }
    }
    if (mode != Engine.Object.MODE_OP) return null;
    switch (objectId) {
      case 7: case 46: case 518: case 535: return "object_wood_break_1";
      case 11: case 454: return "object_barrel_explode";
      case 4: case 9: case 52: case 94: case 95:
      case 142: case 143: case 443: case 444: case 445:
      case 467: case 468: case 469: case 470: case 471:
        return "object_urn_break_1";
      case 208: case 209: return "object_basket_1";
      case 362: return "object_dungeonbasket";
      case 1: case 3: case 50: case 51: case 53: case 79: case 89:
      case 284: case 365: case 485: case 486: case 487: case 524: case 525:
      case 526: case 529: case 530: case 531: case 532: case 533: case 534:
        return "object_casket";
      case 87: case 88: case 139: case 144: case 147: case 148: case 181:
      case 240: case 329: case 330: case 331: case 332: case 333: case 334:
      case 335: case 336: case 387: case 388: case 389: case 390: case 391:
      case 397: case 405: case 406: case 407: case 501: case 502: case 505:
        return "object_chest_large";
      case 140: case 141: case 146: case 176: case 177: case 183: case 198:
      case 241: case 242: case 243: case 413: case 504:
        return "object_chest_small";
      case 182: case 354: case 355: case 356: case 455: case 466:
        return "object_chest_special";
      case 28: return "object_grave";
      case 54: case 55: case 56: case 154: case 158: case 171: case 270:
      case 271: case 272: case 314: case 315: case 316: case 317: case 326:
      case 363: case 477: return "object_corpse_roll";
      case 57: case 58: case 178: return "object_corpse_drop";
      case 125: case 222: return "object_stash_hell_2";
      case 126: case 225: case 372: return "object_skullpile_1";
      case 127: return "object_stash_hell_4";
      case 128: return "object_stash_hell_3";
      case 155: case 159: return "object_stone_large";
      case 163: return "object_shrine_hell_2";
      case 169: return "skeleton_walk_1";
      case 174: case 175: return "object_stone_small";
      case 185: case 205: case 463: case 516: case 517:
        return "object_stash_jungle_1";
      case 186: return "object_stash_jungle_2";
      case 187: return "object_stash_jungle_3";
      case 188: return "object_stash_jungle_4";
      case 203: return "object_stash_mephisto_1";
      case 204: return "object_stash_mephisto_2";
      case 223: case 224: return "object_cocoon_1";
      case 233: return "object_stash_hell_1";
      case 244: case 246: return "object_ratnest_1";
      case 247: case 248: case 289: return "object_bed";
      case 250: return "explosion_medium_1";
      case 259: case 373: return "object_fissure_1";
      case 266: return "object_goopile_1";
      case 360: case 430: case 432: case 433: case 541: case 544:
        return "object_rockpile";
      case 364: return "object_dungeonguy";
      case 383: case 384: return "trappedsoul_down_1";
      case 416: case 500: return "object_xhiddenstash";
      case 418: case 419: return "object_xbarrel";
      case 420: case 431: return "object_xwoodchest";
      case 424: case 425: return "object_burialchest";
      case 448: return "object_skel";
      case 540: return "object_xbody1";
      case 545: case 556: return "object_xbody2";
      default: return null;
    }
  }
}

