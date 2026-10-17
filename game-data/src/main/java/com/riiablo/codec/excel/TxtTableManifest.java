package com.riiablo.codec.excel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Compact, redistributable identity for a Diablo II TXT table.
 *
 * <p>The raw digest identifies the exact MPQ payload. The semantic digest is
 * length-prefixed and covers every header and cell retained by
 * {@link LosslessTxtTable}; it therefore detects empty, duplicate and surplus
 * columns without committing Blizzard's table contents to this repository.</p>
 */
public final class TxtTableManifest {
  public final int rows;
  public final int headerColumns;
  public final int valueColumns;
  public final String rawSha256;
  public final String headerSha256;
  public final String semanticSha256;

  private TxtTableManifest(int rows, int headerColumns, int valueColumns,
      String rawSha256, String headerSha256, String semanticSha256) {
    this.rows = rows;
    this.headerColumns = headerColumns;
    this.valueColumns = valueColumns;
    this.rawSha256 = rawSha256;
    this.headerSha256 = headerSha256;
    this.semanticSha256 = semanticSha256;
  }

  public static TxtTableManifest create(byte[] bytes) throws IOException {
    if (bytes == null) throw new NullPointerException("bytes");
    LosslessTxtTable table = LosslessTxtTable.parse(bytes);
    MessageDigest header = sha256();
    for (String value : table.headers()) update(header, value);

    MessageDigest semantic = sha256();
    updateInt(semantic, table.columnCount());
    for (String value : table.headers()) update(semantic, value);
    updateInt(semantic, table.rowCount());
    for (LosslessTxtTable.Row row : table.rows()) {
      updateInt(semantic, row.cells().size());
      for (String value : row.cells()) update(semantic, value);
    }

    return new TxtTableManifest(
        table.rowCount(),
        table.columnCount(),
        table.valueColumnCount(),
        hex(digest(bytes)),
        hex(header.digest()),
        hex(semantic.digest()));
  }

  private static byte[] digest(byte[] bytes) {
    MessageDigest digest = sha256();
    digest.update(bytes);
    return digest.digest();
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }

  private static void update(MessageDigest digest, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.ISO_8859_1);
    updateInt(digest, bytes.length);
    digest.update(bytes);
  }

  private static void updateInt(MessageDigest digest, int value) {
    digest.update((byte) (value >>> 24));
    digest.update((byte) (value >>> 16));
    digest.update((byte) (value >>> 8));
    digest.update((byte) value);
  }

  private static String hex(byte[] bytes) {
    StringBuilder builder = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) builder.append(String.format("%02x", value & 0xFF));
    return builder.toString();
  }

  @Override
  public String toString() {
    return "rows=" + rows
        + ",headerColumns=" + headerColumns
        + ",valueColumns=" + valueColumns
        + ",rawSha256=" + rawSha256
        + ",headerSha256=" + headerSha256
        + ",semanticSha256=" + semanticSha256;
  }
}
