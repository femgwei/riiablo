package com.riiablo.item;

import com.badlogic.gdx.utils.Array;
import com.riiablo.Files;
import com.riiablo.attributes.AttributesCheckpoint;
import com.riiablo.attributes.StatTable;
import com.riiablo.engine.replay.CheckpointIO;
import java.io.*;
import java.util.*;

/** An exclusively owned native Item tree, not the potentially stale JM byte cache.
 * UI wrappers/name/tooltips and cached serialization are derived and intentionally rebuilt.
 * Native sockets are one level deep; aliases/cycles are rejected, never silently cloned.
 */
public final class ItemRuntimeCheckpoint {
  public static final int MAX_BYTES = 512 * 1024;
  private final byte[] bytes;
  private ItemRuntimeCheckpoint(byte[] bytes) { this.bytes = bytes; }
  public byte[] bytes() { return bytes.clone(); }
  public static ItemRuntimeCheckpoint fromBytes(byte[] bytes) {
    if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Item runtime length");
    ItemRuntimeCheckpoint result = new ItemRuntimeCheckpoint(bytes.clone()); result.decode(null); return result;
  }
  public static ItemRuntimeCheckpoint capture(Item item) {
    if (item == null) throw new IllegalStateException("source Item missing");
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
      out.writeInt(1); writeItem(out, item, new IdentityHashMap<>(), 0);
      ItemRuntimeCheckpoint result = fromBytes(bytes.toByteArray()); result.materialize(item.files()); return result;
    } catch (IOException impossible) { throw new IllegalStateException(impossible); }
  }
  public Item materialize(Files files) {
    if (files == null) throw new IllegalStateException("source Item destination data missing");
    return decode(files);
  }
  private static void writeItem(DataOutputStream out, Item p, IdentityHashMap<Object, Boolean> seen, int depth) throws IOException {
    if (p == null || seen.put(p, true) != null || p.attrs == null || seen.put(p.attrs, true) != null
        || p.base == null || p.type == null || p.typeEntry == null || p.quality == null
        || p.sockets == null || p.socketsFilled != p.sockets.size || p.sockets.size > 6
        || depth > 0 && p.sockets.size != 0) throw new IllegalStateException("unsupported/shared source Item tree");
    // Validate the table aliases rather than copying source World data objects.
    if (p.base != ItemUtils.getBase(p.files(), p.code) || p.typeEntry != p.files().ItemTypes.get(p.base.type)
        || p.type2Entry != p.files().ItemTypes.get(p.base.type2)) throw new IllegalStateException("foreign source Item table owner");
    out.writeInt(p.flags); out.writeInt(p.flags2); out.writeInt(p.vendorPrice); out.writeBoolean(p.vendorBuyback);
    out.writeInt(p.version); out.writeInt(ordinal(p.location)); out.writeInt(ordinal(p.bodyLoc)); out.writeInt(ordinal(p.storeLoc));
    out.writeByte(p.gridX); out.writeByte(p.gridY); CheckpointIO.writeString(out, p.code);
    CheckpointIO.writeString(out, p.base.type); CheckpointIO.writeString(out, p.base.type2);
    out.writeInt(p.getBaseType().ordinal());
    out.writeInt(p.id); out.writeByte(p.ilvl); out.writeInt(p.quality.ordinal());
    out.writeByte(p.pictureId); out.writeShort(p.classOnly); out.writeInt(p.qualityId);
    out.writeBoolean(p.qualityData != null);
    if (p.qualityData != null) {
      if (p.hasFlag(Item.ITEMFLAG_BODYPART)) {
        if (!(p.qualityData instanceof Integer)) throw new IllegalStateException("Item ear payload");
        out.writeInt((Integer) p.qualityData);
      } else if (p.quality == Quality.RARE || p.quality == Quality.CRAFTED) {
        if (!(p.qualityData instanceof RareQualityData)) throw new IllegalStateException("Item rare payload");
        RareQualityData rare = (RareQualityData) p.qualityData;
        for (int value : rare.prefixes) out.writeInt(value);
        for (int value : rare.suffixes) out.writeInt(value);
      } else if (p.quality == Quality.SET) {
        if (p.qualityData != p.files().SetItems.get(p.qualityId)) throw new IllegalStateException("Item set table owner");
      } else if (p.quality == Quality.UNIQUE) {
        if (p.qualityData != p.files().UniqueItems.get(p.qualityId)) throw new IllegalStateException("Item unique table owner");
      } else throw new IllegalStateException("unsupported Item quality payload");
    }
    out.writeInt(p.runewordData); CheckpointIO.writeString(out, p.inscription); out.writeBoolean(p.hasRandom);
    out.writeInt(p.randomTimestamp == null ? -1 : p.randomTimestamp.length);
    if (p.randomTimestamp != null) for (int value : p.randomTimestamp) out.writeInt(value);
    out.writeInt(p.charmData); out.writeInt(p.monsterId); out.writeInt(p.spellId); out.writeBoolean(p.hasSpellId); out.writeInt(p.aggFlags);
    byte[] attrs = AttributesCheckpoint.capture(p.attrs).bytes(); out.writeInt(attrs.length); out.write(attrs);
    out.writeInt(p.sockets.size);
    for (Item socket : p.sockets) {
      if (socket.files() != p.files()) throw new IllegalStateException("foreign socket table owner");
      writeItem(out, socket, seen, depth + 1);
    }
  }
  private static int ordinal(Enum<?> value) { return value == null ? -1 : value.ordinal(); }
  private Item decode(Files files) {
    try {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
      if (in.readInt() != 1) throw new IllegalArgumentException("Item runtime version");
      Item item = readItem(in, files, 0);
      if (in.read() != -1) throw new IllegalArgumentException("trailing Item runtime"); return item;
    } catch (IOException e) { throw new IllegalArgumentException("truncated Item runtime", e); }
  }
  private static Item readItem(DataInputStream in, Files files, int depth) throws IOException {
    Item p = new Item(files); p.reset();
    p.flags = in.readInt(); p.flags2 = in.readInt(); p.vendorPrice = in.readInt(); p.vendorBuyback = CheckpointIO.readBoolean(in);
    p.version = in.readInt(); p.location = value(Location.values(), in.readInt());
    p.bodyLoc = value(BodyLoc.values(), in.readInt()); p.storeLoc = value(StoreLoc.values(), in.readInt());
    p.gridX = in.readByte(); p.gridY = in.readByte(); String code = CheckpointIO.readString(in);
    String type = CheckpointIO.readString(in), type2 = CheckpointIO.readString(in); int baseKind = in.readInt();
    if (code == null || code.isEmpty() || code.length() > 4 || baseKind < 0 || baseKind > 2 || p.version < 0 || p.version > 255) {
      throw new IllegalArgumentException("Item base/version");
    }
    p.code = code; p.id = in.readInt(); p.ilvl = in.readByte();
    p.quality = value(Quality.values(), in.readInt()); p.pictureId = in.readByte(); p.classOnly = in.readShort(); p.qualityId = in.readInt();
    if (p.quality == null || p.ilvl < 0 || p.pictureId < -1 || p.classOnly < -1) throw new IllegalArgumentException("Item quality metadata");
    boolean payload = CheckpointIO.readBoolean(in);
    if (payload) {
      if (p.hasFlag(Item.ITEMFLAG_BODYPART)) p.qualityData = in.readInt();
      else if (p.quality == Quality.RARE || p.quality == Quality.CRAFTED) {
        RareQualityData rare = new RareQualityData(0, 0); p.qualityData = rare;
        for (int i = 0; i < 3; i++) rare.prefixes[i] = CheckpointIO.count(in, Item.MAGIC_AFFIX_MASK);
        for (int i = 0; i < 3; i++) rare.suffixes[i] = CheckpointIO.count(in, Item.MAGIC_AFFIX_MASK);
      } else if (p.quality != Quality.SET && p.quality != Quality.UNIQUE) throw new IllegalArgumentException("Item quality payload kind");
    }
    p.runewordData = in.readInt(); p.inscription = CheckpointIO.readString(in); p.hasRandom = CheckpointIO.readBoolean(in);
    int count = in.readInt();
    if (count < -1 || count > 4) throw new IllegalArgumentException("Item timestamp length");
    if (count >= 0) { p.randomTimestamp = new int[count]; for (int i = 0; i < count; i++) p.randomTimestamp[i] = in.readInt(); }
    p.charmData = in.readInt(); p.monsterId = in.readInt(); p.spellId = in.readInt(); p.hasSpellId = CheckpointIO.readBoolean(in); p.aggFlags = in.readInt();
    int size = CheckpointIO.count(in, AttributesCheckpoint.MAX_BYTES);
    if (size == 0 || size > in.available()) throw new IllegalArgumentException("Item attributes size");
    byte[] image = new byte[size]; in.readFully(image); AttributesCheckpoint attrs = AttributesCheckpoint.fromBytes(image);
    if (files != null) {
      p.code = null;
      try { p.setBase(code); }
      catch (RuntimeException e) { throw new IllegalStateException("source Item base table missing", e); }
      if (p.getBaseType().ordinal() != baseKind || !Objects.equals(p.base.type, type) || !Objects.equals(p.base.type2, type2)) {
        throw new IllegalStateException("source Item base table identity mismatch");
      }
      p.attrs = attrs.materialize(new StatTable(files.ItemStatCost));
      if (payload && (p.quality == Quality.SET || p.quality == Quality.UNIQUE)) {
        p.qualityData = p.quality == Quality.SET ? files.SetItems.get(p.qualityId) : files.UniqueItems.get(p.qualityId);
        if (p.qualityData == null) throw new IllegalStateException("source Item quality table missing");
      }
    }
    p.socketsFilled = CheckpointIO.count(in, depth == 0 ? 6 : 0); p.sockets = new Array<>(Item.class);
    for (int i = 0; i < p.socketsFilled; i++) p.sockets.add(readItem(in, files, depth + 1));
    return p;
  }
  private static <E> E value(E[] values, int ordinal) {
    if (ordinal < -1 || ordinal >= values.length) throw new IllegalArgumentException("Item enum"); return ordinal < 0 ? null : values[ordinal];
  }
}
