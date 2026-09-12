package com.riiablo.engine.server.quest;

import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.Location;
import com.riiablo.item.StoreLoc;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.save.CharData;
import com.riiablo.save.ItemData;

/** Atomic server-side cube operation for Khalim's Will. */
public final class Act3KhalimWillTransmute {
  private static final Logger log = LogManager.getLogger(Act3KhalimWillTransmute.class);

  public enum Status { SUCCESS, MISSING_FLAIL, MISSING_EYE, MISSING_HEART, MISSING_BRAIN,
    ALREADY_ASSEMBLED, MISSING_CUBE, CUBE_FULL, GENERATION_FAILED, INVALID_REQUEST,
    ROLLBACK_FAILED }

  public static final class Result {
    public final Status status;
    public final Item output;
    private Result(Status status, Item output) { this.status = status; this.output = output; }
    public boolean success() { return status == Status.SUCCESS; }
  }

  private Act3KhalimWillTransmute() {}

  public static Result transmute(CharData character, ItemGenerator generator) {
    if (character == null) return new Result(Status.INVALID_REQUEST, null);
    Result result = transmute(character.getItems(), generator);
    if (result.success()) {
      short[] act3 = character.getQuests(com.riiablo.Riiablo.ACT3);
      act3[Act3KhalimQuest.RECORD] = NativeQuestRecord.set(
          act3[Act3KhalimQuest.RECORD], NativeQuestRecord.CUSTOM6);
    }
    return result;
  }

  public static Result transmute(ItemData items, ItemGenerator generator) {
    if (items == null || generator == null) return new Result(Status.INVALID_REQUEST, null);
    if (findInCube(items, Act3KhalimQuest.KHALIM_WILL) != null) {
      return new Result(Status.ALREADY_ASSEMBLED, null);
    }
    Item flail = findInCube(items, Act3KhalimQuest.KHALIM_FLAIL);
    if (flail == null) return new Result(Status.MISSING_FLAIL, null);
    Item eye = findInCube(items, Act3KhalimQuest.KHALIM_EYE);
    if (eye == null) return new Result(Status.MISSING_EYE, null);
    Item heart = findInCube(items, Act3KhalimQuest.KHALIM_HEART);
    if (heart == null) return new Result(Status.MISSING_HEART, null);
    Item brain = findInCube(items, Act3KhalimQuest.KHALIM_BRAIN);
    if (brain == null) return new Result(Status.MISSING_BRAIN, null);
    if (findInCube(items, Act2HoradricStaffQuest.HORADRIC_CUBE) == null) {
      return new Result(Status.MISSING_CUBE, null);
    }

    final Item output;
    try {
      output = generator.generate(Act3KhalimQuest.KHALIM_WILL);
    } catch (Throwable t) {
      log.error("[A3Q3] Khalim's Will generation failed", t);
      return new Result(Status.GENERATION_FAILED, null);
    }
    if (output == null || !Act3KhalimQuest.KHALIM_WILL.equalsIgnoreCase(output.code)) {
      return new Result(Status.GENERATION_FAILED, null);
    }
    output.version = Item.VERSION_110;
    output.location = Location.STORED;
    output.storeLoc = StoreLoc.CUBE;
    if (!items.canAddToCube(output)) return new Result(Status.CUBE_FULL, null);
    if (!items.removeOwnedItem(flail) || !items.removeOwnedItem(eye)
        || !items.removeOwnedItem(heart) || !items.removeOwnedItem(brain)) {
      log.error("[A3Q3] Khalim's Will transaction preflight changed unexpectedly");
      return new Result(Status.ROLLBACK_FAILED, null);
    }
    if (!items.addToCube(output)) {
      log.error("[A3Q3] Khalim's Will output placement failed after input removal");
      return new Result(Status.ROLLBACK_FAILED, null);
    }
    log.info("[A3Q3] Khalim's Will assembled in authoritative cube");
    return new Result(Status.SUCCESS, output);
  }

  private static Item findInCube(ItemData items, String code) {
    for (Item item : items.getItems()) {
      if (item != null && item.location == Location.STORED && item.storeLoc == StoreLoc.CUBE
          && code.equalsIgnoreCase(item.code)) return item;
    }
    return null;
  }
}
