package com.riiablo.attributes;

import com.riiablo.codec.excel.ItemStatCost;
import java.io.*;
import java.util.*;

/** Complete owned Item attributes, including property-list order, frozen/dirty bits and raw caches.
 * Not a unit-state format upgrade: unit source lists still require their own ownership contract.
 */
public final class AttributesCheckpoint {
  public static final int MAX_BYTES = 32 * 1024;
  private final byte[] bytes;
  private AttributesCheckpoint(byte[] bytes) { this.bytes = bytes; }
  public byte[] bytes() { return bytes.clone(); }
  public static AttributesCheckpoint fromBytes(byte[] bytes) {
    if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Item attributes length");
    AttributesCheckpoint image = new AttributesCheckpoint(bytes.clone()); image.decode(); return image;
  }
  public static AttributesCheckpoint capture(Attributes attrs) {
    if (attrs == null || attrs.type() == Attributes.Type.WRAPPER) throw new IllegalStateException("Item attributes missing/unsupported");
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
      out.writeInt(1); out.writeInt(attrs.type().ordinal()); out.writeLong(attrs.aggregateRevision());
      writeList(out, attrs.base().stats); writeList(out, attrs.aggregate().stats); writeList(out, attrs.remaining().stats);
      writeList(out, attrs.list());
      out.writeBoolean(attrs.hasCombatShieldContext()); out.writeBoolean(attrs.combatHasShield()); out.writeInt(attrs.combatBlockFactor());
      out.writeBoolean(attrs.hasCombatRunningContext()); out.writeBoolean(attrs.combatRunning());
      out.writeBoolean(attrs.difficultyResistanceIncluded());
      for (long value : attrs.staminaMaximum().checkpoint()) out.writeLong(value);
      return fromBytes(bytes.toByteArray());
    } catch (IOException impossible) { throw new IllegalStateException(impossible); }
  }
  private static void writeList(DataOutputStream out, StatList lists) throws IOException {
    out.writeInt(lists.numLists()); out.writeInt(lists.checkpointImmutableMask());
    for (StatListRef list : lists.listIterator()) {
      out.writeInt(list.size());
      for (StatRef stat : list) {
        ItemStatCost.Entry row = stat.entry();
        out.writeInt(stat.id()); out.writeInt(stat.encodedParams()); out.writeInt(stat.encodedValues());
        out.writeBoolean(stat.modified()); out.writeInt(row.Encode); out.writeInt(row.ValShift);
      }
    }
  }
  /** Creates fresh attributes bound to the destination table; never updates or rolls properties. */
  public Attributes materialize(StatTable table) {
    Decoded d = decode();
    for (ListImage list : d.lists) for (int[][] group : list.stats) for (int[] stat : group) {
      ItemStatCost.Entry row = table.entry((short) stat[0]);
      if (row == null || row.Encode != stat[4] || row.ValShift != stat[5]) throw new IllegalStateException("Item attribute table identity mismatch");
    }
    Attributes attrs = d.type == Attributes.Type.LARGE.ordinal() ? Attributes.obtainLarge(table)
        : d.type == Attributes.Type.STANDARD.ordinal() ? Attributes.obtainStandard(table) : Attributes.obtainCompact(table);
    attrs.clear();
    StatList[] targets = {attrs.base().stats, attrs.aggregate().stats, attrs.remaining().stats, attrs.list()};
    for (int i = 0; i < targets.length; i++) {
      StatList target = targets[i]; target.forceClear();
      for (int[][] group : d.lists[i].stats) {
        StatListRef list = target.buildList();
        for (int[] stat : group) {
          StatRef ref = list.putEncoded((short) stat[0], stat[1], stat[2]);
          if (stat[3] == 0) ref.forceUnmodified();
        }
      }
      target.restoreCheckpointImmutableMask(d.lists[i].mask);
    }
    attrs.restoreAggregateRevision(d.revision);
    attrs.restoreCombatContext(d.shieldKnown, d.shield, d.block, d.runningKnown, d.running);
    attrs.setDifficultyResistanceIncluded(d.difficulty); attrs.staminaMaximum().restoreCheckpoint(d.stamina);
    return attrs;
  }
  private Decoded decode() {
    try {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
      if (in.readInt() != 1) throw new IllegalArgumentException("Item attributes version");
      Decoded d = new Decoded(); d.type = in.readInt(); d.revision = in.readLong();
      if (d.type < 0 || d.type >= Attributes.Type.WRAPPER.ordinal() || d.revision < 0) throw new IllegalArgumentException("Item attributes type/revision");
      for (int i = 0; i < 4; i++) {
        ListImage list = new ListImage(); d.lists[i] = list;
        int count = count(in, i == 3 ? 8 : 1); list.mask = in.readInt();
        if (i < 3 && count != 1 || list.mask < 0 || (list.mask & ~255) != 0
            || i < 3 && list.mask > 1) throw new IllegalArgumentException("Item attributes list shape/mask");
        list.stats = new int[count][][]; int total = 0;
        for (int j = 0; j < count; j++) {
          int size = count(in, i < 3 && d.type == Attributes.Type.LARGE.ordinal() ? 256 : 32);
          total += size;
          if (total > (i < 3 && d.type == Attributes.Type.LARGE.ordinal() ? 256 : 32)) throw new IllegalArgumentException("Item attributes capacity");
          int[][] stats = list.stats[j] = new int[size][6]; int previousId = -1, previousParams = 0;
          for (int[] stat : stats) {
            stat[0] = in.readInt(); stat[1] = in.readInt(); stat[2] = in.readInt(); stat[3] = bool(in) ? 1 : 0;
            stat[4] = in.readInt(); stat[5] = in.readInt();
            if (stat[0] < 0 || stat[0] > 511 || stat[0] < previousId || stat[0] == previousId && stat[1] <= previousParams
                || stat[4] < 0 || stat[4] > 7 || stat[5] < 0 || stat[5] > 31) throw new IllegalArgumentException("Item attribute identity/order/encoding");
            previousId = stat[0]; previousParams = stat[1];
          }
        }
      }
      d.shieldKnown = bool(in); d.shield = bool(in); d.block = in.readInt();
      d.runningKnown = bool(in); d.running = bool(in); d.difficulty = bool(in);
      if (d.block < 0) throw new IllegalArgumentException("Item combat block context");
      for (int i = 0; i < d.stamina.length; i++) d.stamina[i] = in.readLong();
      new StaminaMaximum().restoreCheckpoint(d.stamina);
      if (in.read() != -1) throw new IllegalArgumentException("trailing Item attributes"); return d;
    } catch (IOException e) { throw new IllegalArgumentException("truncated Item attributes", e); }
  }
  private static int count(DataInputStream in, int max) throws IOException {
    int value = in.readInt(); if (value < 0 || value > max) throw new IllegalArgumentException("Item attribute count"); return value;
  }
  private static boolean bool(DataInputStream in) throws IOException {
    int value = in.readUnsignedByte(); if (value > 1) throw new IllegalArgumentException("Item attribute boolean"); return value == 1;
  }
  private static final class ListImage { int mask; int[][][] stats; }
  private static final class Decoded {
    int type, block; long revision;
    ListImage[] lists = new ListImage[4];
    boolean shieldKnown, shield, runningKnown, running, difficulty;
    long[] stamina = new long[11];
  }
}
