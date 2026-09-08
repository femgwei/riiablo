package com.riiablo.save;

import io.netty.util.ByteProcessor;

import com.badlogic.gdx.files.FileHandle;

import com.riiablo.attributes.StatListReader;
import com.riiablo.io.ByteInput;
import com.riiablo.io.InvalidFormat;
import com.riiablo.io.UnsafeNarrowing;
import com.riiablo.item.ItemReader;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.logger.MDC;

public enum D2SReader {
  INSTANCE;

  private static final Logger log = LogManager.getLogger(D2SReader.class);

  // set the value of it in the d2s data to be zero and iterate through all the bytes
  public int calculateChecksum(ByteInput in) {
    ChecksumCalculator checksumCalculator = new ChecksumCalculator();
    in.buffer().forEachByte(checksumCalculator);
    return checksumCalculator.checksum;
  }

  private static class ChecksumCalculator implements ByteProcessor {
    int checksum = 0;
    int index = 0;

    @Override
    public boolean process(byte value) {
      int octet = index >= 0x0C && index < 0x10 ? 0 : value & 0xFF;
      checksum = ((checksum << 1) | ((checksum >>> 31) & 1)) + octet;
      index++;
      return true;
    }
  }

  // TODO: rewrite this function without stubbing serialization
  @SuppressWarnings("deprecation")
  public D2S readD2S(FileHandle handle) {
    byte[] bytes = handle.readBytes();
    D2S d2s = readD2S(ByteInput.wrap(bytes));
    D2SWriterStub.put(d2s, bytes);
    return d2s;
  }

  public D2S readD2S(ByteInput in) {
    log.trace("Reading d2s...");
    log.trace("Validating d2s signature");
    in.readSignature(D2S.SIGNATURE);
    try {
      D2S d2s = new D2S();
      return readHeader(in, d2s);
    } catch (UnsafeNarrowing t) {
      throw new InvalidFormat(in, t);
    }
  }

  /**
   * Reads and validates a complete 1.10f save.  The legacy {@link #readD2S}
   * method intentionally stops after the fixed header because character-list
   * screens only need preview data; callers that enter the game must use this
   * method (or call {@link #readRemaining} themselves) so the body cannot be
   * silently omitted.
   *
   * <p>The native loader rejects truncated files and checksum mismatches.  Do
   * the same here before parsing variable-length item/stat sections, where a
   * malformed count otherwise tends to surface as an unrelated EOF.</p>
   */
  public D2S readComplete(byte[] bytes, StatListReader statReader, ItemReader itemReader) {
    if (bytes == null || bytes.length < D2SReader96.HEADER_SIZE) {
      throw new InvalidFormat(ByteInput.wrap(bytes == null ? new byte[0] : bytes),
          "D2S file is truncated before the fixed header");
    }
    ByteInput in = ByteInput.wrap(bytes);
    D2S d2s = readD2S(in);
    long declaredSize = Integer.toUnsignedLong(d2s.size);
    if (declaredSize != bytes.length) {
      throw new InvalidFormat(in, "D2S size mismatch: header=" + declaredSize
          + " actual=" + bytes.length);
    }
    int calculated = calculateChecksum(ByteInput.wrap(bytes));
    if (d2s.checksum != calculated) {
      throw new InvalidFormat(in, String.format(
          "D2S checksum mismatch: header=0x%08X calculated=0x%08X",
          d2s.checksum, calculated));
    }
    if (statReader == null) statReader = new StatListReader();
    if (itemReader == null) itemReader = new ItemReader();
    readRemaining(d2s, in, statReader, itemReader);
    if (in.bytesRemaining() != 0) {
      throw new InvalidFormat(in, "D2S has " + in.bytesRemaining()
          + " trailing bytes after the final section");
    }
    return d2s;
  }

  /** Convenience overload using the standard stat/item decoders. */
  public D2S readComplete(byte[] bytes) {
    return readComplete(bytes, null, null);
  }

  static D2S readHeader(ByteInput in, D2S d2s) {
    d2s.version = in.readSafe32u();
    log.debug("version: {} ({})", d2s.version, D2S.getVersionString(d2s.version));
    try {
      MDC.put("d2s.version", d2s.version);
      switch (d2s.version) {
        case D2S.VERSION_110:
          return D2SReader96.readHeader(in, d2s);
        case D2S.VERSION_100:
        case D2S.VERSION_107:
        case D2S.VERSION_108:
        case D2S.VERSION_109:
        default:
          log.error("Unsupported d2s version: " + D2S.getVersionString(d2s.version));
          return d2s;
      }
    } finally {
      MDC.remove("d2s.version");
    }
  }

  public D2S readRemaining(D2S d2s, ByteInput in, StatListReader statReader, ItemReader itemReader) {
    try {
      MDC.put("d2s.version", d2s.version);
      switch (d2s.version) {
        case D2S.VERSION_110:
          return D2SReader96.readRemaining(d2s, in, statReader, itemReader);
        case D2S.VERSION_100:
        case D2S.VERSION_107:
        case D2S.VERSION_108:
        case D2S.VERSION_109:
        default:
          log.error("Unsupported d2s version: " + D2S.getVersionString(d2s.version));
          return d2s;
      }
    } finally {
      MDC.remove("d2s.version");
    }
  }

  CharData copyTo(D2S d2s, CharData data) {
    try {
      MDC.put("d2s.version", d2s.version);
      switch (d2s.version) {
        case D2S.VERSION_110:
          return D2SReader96.copyTo(d2s, data);
        case D2S.VERSION_100:
        case D2S.VERSION_107:
        case D2S.VERSION_108:
        case D2S.VERSION_109:
        default:
          log.error("Unsupported d2s version: " + D2S.getVersionString(d2s.version));
          return data;
      }
    } finally {
      MDC.remove("d2s.version");
    }
  }
}
