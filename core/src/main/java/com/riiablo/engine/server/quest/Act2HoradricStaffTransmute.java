package com.riiablo.engine.server.quest;

import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.Location;
import com.riiablo.item.StoreLoc;
import com.riiablo.save.CharData;
import com.riiablo.save.ItemData;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * Server-authoritative A2Q2 Horadric Staff assembly.
 *
 * <p>D2MOO's cube operation consumes exactly the Staff of Kings and Viper
 * Amulet from the player's cube, keeps the Horadric Cube, and creates one
 * Horadric Staff in the cube.  The operation is deliberately implemented as
 * a preflight followed by a single mutation phase: missing materials,
 * wrong-store materials, a full cube, duplicate requests, and item generation
 * failures never consume anything.</p>
 */
public final class Act2HoradricStaffTransmute {
  private static final Logger log = LogManager.getLogger(Act2HoradricStaffTransmute.class);

  public enum Status {
    SUCCESS,
    MISSING_STAFF_OF_KINGS,
    MISSING_VIPER_AMULET,
    MISSING_HORADRIC_CUBE,
    ALREADY_ASSEMBLED,
    CUBE_FULL,
    GENERATION_FAILED,
    INVALID_REQUEST,
    ROLLBACK_FAILED
  }

  public static final class Result {
    public final Status status;
    public final Item output;

    private Result(Status status, Item output) {
      this.status = status;
      this.output = output;
    }

    public boolean success() {
      return status == Status.SUCCESS;
    }
  }

  private Act2HoradricStaffTransmute() {}

  /** Applies the operation and updates the character's native A2Q2 record. */
  public static Result transmute(CharData character, ItemGenerator generator) {
    if (character == null) return new Result(Status.INVALID_REQUEST, null);
    Result result = transmute(character.getItems(), generator);
    if (result.success()) {
      short[] act2 = character.getQuests(com.riiablo.Riiablo.ACT2);
      act2[Act2HoradricStaffQuest.RECORD] =
          Act2HoradricStaffQuest.markStaffCubed(act2[Act2HoradricStaffQuest.RECORD]);
    }
    return result;
  }

  /**
   * Performs a strict cube-only transaction.  Inventory or ground copies of
   * the quest items are intentionally not accepted; this prevents a client
   * from assembling the staff by merely claiming ownership of a dropped item.
   */
  public static Result transmute(ItemData items, ItemGenerator generator) {
    if (items == null || generator == null) return new Result(Status.INVALID_REQUEST, null);

    if (findInCube(items, Act2HoradricStaffQuest.HORADRIC_STAFF) != null) {
      return new Result(Status.ALREADY_ASSEMBLED, null);
    }
    Item staff = findInCube(items, Act2HoradricStaffQuest.STAFF_OF_KINGS);
    if (staff == null) return new Result(Status.MISSING_STAFF_OF_KINGS, null);
    Item amulet = findInCube(items, Act2HoradricStaffQuest.VIPER_AMULET);
    if (amulet == null) return new Result(Status.MISSING_VIPER_AMULET, null);
    if (findInCube(items, Act2HoradricStaffQuest.HORADRIC_CUBE) == null) {
      return new Result(Status.MISSING_HORADRIC_CUBE, null);
    }

    final Item output;
    try {
      output = generator.generate(Act2HoradricStaffQuest.HORADRIC_STAFF);
    } catch (Throwable t) {
      log.error("[A2Q2] Horadric Staff generation failed", t);
      return new Result(Status.GENERATION_FAILED, null);
    }
    if (output == null || !Act2HoradricStaffQuest.HORADRIC_STAFF.equalsIgnoreCase(output.code)) {
      return new Result(Status.GENERATION_FAILED, null);
    }
    output.version = Item.VERSION_110;
    output.location = Location.STORED;
    output.storeLoc = StoreLoc.CUBE;
    if (!items.canAddToCube(output)) return new Result(Status.CUBE_FULL, null);

    // No user callbacks or network messages occur between these operations;
    // ItemData is owned by the simulation thread, so this is one atomic tick.
    if (!items.removeOwnedItem(staff) || !items.removeOwnedItem(amulet)) {
      log.error("[A2Q2] Horadric Staff transaction preflight changed unexpectedly");
      return new Result(Status.ROLLBACK_FAILED, null);
    }
    if (!items.addToCube(output)) {
      log.error("[A2Q2] Horadric Staff output placement failed after input removal");
      return new Result(Status.ROLLBACK_FAILED, null);
    }
    log.info("[A2Q2] Horadric Staff assembled in authoritative cube");
    return new Result(Status.SUCCESS, output);
  }

  private static Item findInCube(ItemData items, String code) {
    for (Item item : items.getItems()) {
      if (item == null || item.location != Location.STORED || item.storeLoc != StoreLoc.CUBE) {
        continue;
      }
      if (code.equalsIgnoreCase(item.code)) return item;
    }
    return null;
  }
}
