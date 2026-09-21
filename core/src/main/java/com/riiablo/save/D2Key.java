package com.riiablo.save;

import com.badlogic.gdx.files.FileHandle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Reader/writer for Diablo II's external keyboard binding file ({@code .key}).
 *
 * <p>The character form used by 1.10f is a small binary table:
 * <pre>
 *   6-byte header
 *   113 records x 10 bytes
 *   6-byte trailer
 * </pre>
 * Each record starts with the unsigned D2 virtual-key code.  The remaining
 * eight bytes are kept opaque except for the little-endian action/slot fields
 * exposed by {@link Record}; this lets us rewrite a file without dropping
 * fields that Riiablo does not currently use.  The installed game's
 * {@code default.key} has a ten-byte {@code WS} header and is accepted too.
 */
public final class D2Key {
  public static final int CHARACTER_HEADER_SIZE = 6;
  public static final int DEFAULT_HEADER_SIZE = 10;
  public static final int RECORD_SIZE = 10;
  public static final int TRAILER_SIZE = 6;
  public static final int CHARACTER_RECORD_COUNT = 113;

  private final byte[] header;
  private final byte[] trailer;
  private final Record[] records;

  private D2Key(byte[] header, Record[] records, byte[] trailer) {
    this.header = header;
    this.records = records;
    this.trailer = trailer;
  }

  public static D2Key read(FileHandle file) throws IOException {
    if (file == null || !file.exists()) throw new IOException("Missing .key file");
    return read(file.readBytes());
  }

  public static D2Key read(byte[] bytes) throws IOException {
    if (bytes == null || bytes.length < CHARACTER_HEADER_SIZE + RECORD_SIZE + TRAILER_SIZE) {
      throw new IOException("Invalid .key file: too short");
    }

    // default.key starts with "WS" and carries two extra metadata words.
    final int headerSize = bytes[0] == 'W' && bytes[1] == 'S'
        ? DEFAULT_HEADER_SIZE : CHARACTER_HEADER_SIZE;
    if (bytes.length < headerSize + TRAILER_SIZE
        || ((bytes.length - headerSize - TRAILER_SIZE) % RECORD_SIZE) != 0) {
      throw new IOException("Invalid .key file: malformed record area");
    }

    final int recordCount = (bytes.length - headerSize - TRAILER_SIZE) / RECORD_SIZE;
    Record[] records = new Record[recordCount];
    for (int i = 0; i < recordCount; i++) {
      records[i] = new Record(Arrays.copyOfRange(
          bytes, headerSize + i * RECORD_SIZE, headerSize + (i + 1) * RECORD_SIZE));
    }
    return new D2Key(
        Arrays.copyOfRange(bytes, 0, headerSize),
        records,
        Arrays.copyOfRange(bytes, bytes.length - TRAILER_SIZE, bytes.length));
  }

  /** Creates a valid character-form file with all bindings initially unset. */
  public static D2Key emptyCharacter() {
    byte[] header = new byte[CHARACTER_HEADER_SIZE];
    putU16(header, 0, 0x25); // native key table version/count marker
    Record[] records = new Record[CHARACTER_RECORD_COUNT];
    for (int i = 0; i < records.length; i++) {
      byte[] raw = new byte[RECORD_SIZE];
      putU16(raw, 0, 0xFFFF);
      putU16(raw, 2, (i == 0 || (i & 1) == 0) ? 1 : 0);
      putU32(raw, 6, actionIdForRecord(i));
      records[i] = new Record(raw);
    }
    return new D2Key(header, records, new byte[TRAILER_SIZE]);
  }

  public int recordCount() {
    return records.length;
  }

  public Record record(int index) {
    if (index < 0 || index >= records.length) throw new IndexOutOfBoundsException("record=" + index);
    return records[index];
  }

  public void write(FileHandle file) throws IOException {
    if (file == null) throw new IOException("Missing .key destination");
    file.writeBytes(toBytes(), false);
  }

  public byte[] toBytes() {
    ByteArrayOutputStream out = new ByteArrayOutputStream(
        header.length + records.length * RECORD_SIZE + trailer.length);
    out.write(header, 0, header.length);
    for (Record record : records) out.write(record.raw, 0, record.raw.length);
    out.write(trailer, 0, trailer.length);
    return out.toByteArray();
  }

  private static int actionIdForRecord(int record) {
    if (record == 0) return 0;
    // This is the action ordering in the 1.10f default.key table.  There is
    // one intentionally unused action (21) which is stored after action 45.
    if (record >= 1 && record <= 40) return 1 + (record - 1) / 2;
    if (record == 41 || record == 42) return 22;
    if (record >= 43 && record <= 88) return 23 + (record - 43) / 2;
    if (record == 89 || record == 90) return 21;
    return 46 + (record - 91) / 2;
  }

  private static int u16(byte[] data, int off) {
    return (data[off] & 0xFF) | ((data[off + 1] & 0xFF) << 8);
  }

  private static void putU16(byte[] data, int off, int value) {
    data[off] = (byte) value;
    data[off + 1] = (byte) (value >>> 8);
  }

  private static void putU32(byte[] data, int off, int value) {
    data[off] = (byte) value;
    data[off + 1] = (byte) (value >>> 8);
    data[off + 2] = (byte) (value >>> 16);
    data[off + 3] = (byte) (value >>> 24);
  }

  public static final class Record {
    private final byte[] raw;

    private Record(byte[] raw) {
      if (raw.length != RECORD_SIZE) throw new IllegalArgumentException("record size");
      this.raw = raw;
    }

    public int keyCode() {
      return u16(raw, 0);
    }

    public void setKeyCode(int keyCode) {
      if (keyCode < 0 || keyCode > 0xFFFF) throw new IllegalArgumentException("keyCode=" + keyCode);
      putU16(raw, 0, keyCode);
    }

    /** The little-endian secondary/primary marker found at record offset 2. */
    public int mappingMarker() {
      return u16(raw, 2);
    }

    /** Native action identifier at record offset 6. */
    public int actionId() {
      return (raw[6] & 0xFF) | ((raw[7] & 0xFF) << 8)
          | ((raw[8] & 0xFF) << 16) | ((raw[9] & 0xFF) << 24);
    }
  }
}
