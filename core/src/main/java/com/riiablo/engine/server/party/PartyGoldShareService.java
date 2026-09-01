package com.riiablo.engine.server.party;

import com.badlogic.gdx.utils.Array;
import com.riiablo.item.Item;
import com.riiablo.item.VendorPricing;
import com.riiablo.save.CharData;

/** Native PARTY_ShareGoldDrop distribution with per-player carried-gold caps. */
public final class PartyGoldShareService {
  private PartyGoldShareService() {}

  public static final class Recipient {
    public final int entityId;
    public final CharData character;
    public final boolean eligible;

    public Recipient(int entityId, CharData character, boolean eligible) {
      this.entityId = entityId;
      this.character = character;
      this.eligible = eligible;
    }
  }

  public static final class Grant {
    public final int entityId;
    public final int credited;
    public Grant(int entityId, int credited) {
      this.entityId = entityId;
      this.credited = credited;
    }
  }

  public static final class Result {
    public final Array<Grant> grants = new Array<>();
    public int remaining;
  }

  /**
   * Distributes a pile exactly like D2MOO: each other living member receives
   * amount / memberCount, failed capacity is returned to the pile, and the
   * picker receives the adjusted remainder.
   */
  public static Result distribute(Item pile, int pickerEntityId,
                                  Array<Recipient> recipients) {
    Result result = new Result();
    int amount = quantity(pile);
    if (amount <= 0 || recipients == null || recipients.size == 0) {
      result.remaining = Math.max(0, amount);
      return result;
    }
    Recipient picker = findRecipient(pickerEntityId, recipients);
    if (picker == null || !picker.eligible || picker.character == null) {
      result.remaining = amount;
      return result;
    }
    int living = 0;
    for (Recipient recipient : recipients) {
      if (recipient != null && recipient.eligible && recipient.character != null) living++;
    }
    if (living <= 1) {
      credit(result, pickerEntityId, picker.character, amount);
      return result;
    }

    int divided = amount / living;
    int adjusted = amount;
    for (Recipient recipient : recipients) {
      if (recipient == null || !recipient.eligible || recipient.character == null
          || recipient.entityId == pickerEntityId) continue;
      VendorPricing.GoldGrant grant = VendorPricing.grantCarriedGold(recipient.character, divided);
      result.grants.add(new Grant(recipient.entityId, grant.credited));
      adjusted += grant.remaining - divided;
    }
    credit(result, pickerEntityId, picker.character, adjusted);
    return result;
  }

  private static void credit(Result result, int entityId, CharData character, int amount) {
    if (character == null || amount <= 0) {
      result.remaining = Math.max(0, amount);
      return;
    }
    VendorPricing.GoldGrant grant = VendorPricing.grantCarriedGold(character, amount);
    result.grants.add(new Grant(entityId, grant.credited));
    result.remaining = grant.remaining;
  }

  private static Recipient findRecipient(int entityId, Array<Recipient> recipients) {
    for (Recipient recipient : recipients) {
      if (recipient != null && recipient.entityId == entityId) return recipient;
    }
    return null;
  }

  private static int quantity(Item pile) {
    if (pile == null || pile.attrs == null || pile.attrs.base().get(com.riiablo.attributes.Stat.quantity) == null) return 0;
    return Math.max(0, pile.attrs.base().get(com.riiablo.attributes.Stat.quantity).asInt());
  }
}
