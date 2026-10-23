package com.riiablo.codec.excel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Deterministic identity for a set of TXT tables loaded for one ruleset.
 *
 * <p>The digest is independent of filesystem paths and insertion order. It
 * includes logical table names, declared versions and each table's raw,
 * header and semantic identities, so a session can reject mixed data packs
 * before rules start reading them.</p>
 */
public final class TxtDataPackManifest {
  public final String packId;
  public final TxtTableSource.Version version;
  public final Map<String, TxtTableSource> tables;
  public final String sha256;

  private TxtDataPackManifest(String packId, TxtTableSource.Version version,
      Map<String, TxtTableSource> tables, String sha256) {
    this.packId = packId;
    this.version = version;
    this.tables = Collections.unmodifiableMap(new LinkedHashMap<>(tables));
    this.sha256 = sha256;
  }

  public static Builder builder(String packId, TxtTableSource.Version version) {
    if (packId == null || packId.trim().isEmpty()) throw new IllegalArgumentException("packId");
    return new Builder(packId, Objects.requireNonNull(version, "version"));
  }

  public boolean matches(TxtDataPackManifest other) {
    return other != null && packId.equals(other.packId) && version == other.version
        && sha256.equals(other.sha256);
  }

  @Override public String toString() {
    return "packId=" + packId + ",version=" + version + ",tables=" + tables.keySet()
        + ",sha256=" + sha256;
  }

  public static final class Builder {
    private final String packId;
    private final TxtTableSource.Version version;
    private final Map<String, TxtTableSource> tables = new LinkedHashMap<>();
    private final Set<String> required = new TreeSet<>();

    private Builder(String packId, TxtTableSource.Version version) {
      this.packId = packId;
      this.version = version;
    }

    public Builder require(String tableName) {
      required.add(name(tableName));
      return this;
    }

    public Builder table(String tableName, TxtTableSource source) {
      String key = name(tableName);
      if (source == null) throw new NullPointerException("source for " + key);
      if (tables.put(key, source) != null) {
        throw new IllegalArgumentException("duplicate table: " + key);
      }
      if (version != TxtTableSource.Version.UNSPECIFIED
          && source.version != version) {
        throw new IllegalArgumentException("table " + key + " declares " + source.version
            + ", expected " + version);
      }
      return this;
    }

    public TxtDataPackManifest build() {
      List<String> missing = new ArrayList<>();
      for (String tableName : required) if (!tables.containsKey(tableName)) missing.add(tableName);
      if (!missing.isEmpty()) {
        throw new MissingTxtDataException("missing required TXT tables: " + missing);
      }
      return new TxtDataPackManifest(packId, version, tables, digest(packId, version, tables));
    }

    private static String name(String value) {
      if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("tableName");
      return value;
    }
  }

  private static String digest(String packId, TxtTableSource.Version version,
      Map<String, TxtTableSource> tables) {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
    update(digest, packId);
    update(digest, version.name());
    for (String tableName : new TreeSet<>(tables.keySet())) {
      TxtTableSource source = tables.get(tableName);
      update(digest, tableName);
      update(digest, source.version.name());
      update(digest, source.manifest.rawSha256);
      update(digest, source.manifest.headerSha256);
      update(digest, source.manifest.semanticSha256);
      updateInt(digest, source.manifest.rows);
      updateInt(digest, source.manifest.headerColumns);
      updateInt(digest, source.manifest.valueColumns);
    }
    return hex(digest.digest());
  }

  private static void update(MessageDigest digest, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
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
    StringBuilder result = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) result.append(String.format("%02x", value & 0xFF));
    return result.toString();
  }
}
