package com.riiablo.save;

import com.riiablo.io.ByteInput;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Immutable, validated D2S byte snapshot keyed by authoritative entity id. */
public final class D2SCheckpoint {
  public static final class Entry {
    private final int entityId;
    private final int difficulty;
    private final byte[] bytes;
    private final int checksum;
    private final String digest;

    private Entry(int entityId, int difficulty, byte[] bytes) {
      if (entityId < 0) throw new IllegalArgumentException("entityId");
      if (difficulty < 0) throw new IllegalArgumentException("difficulty");
      if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("bytes");
      D2SReader.INSTANCE.readComplete(bytes);
      this.entityId = entityId;
      this.difficulty = difficulty;
      this.bytes = Arrays.copyOf(bytes, bytes.length);
      this.checksum = D2SReader.INSTANCE.calculateChecksum(ByteInput.wrap(bytes));
      this.digest = sha256(bytes);
    }

    public int entityId() { return entityId; }
    public int difficulty() { return difficulty; }
    public byte[] bytes() { return Arrays.copyOf(bytes, bytes.length); }
    public int checksum() { return checksum; }
    public String digest() { return digest; }

    private String canonical() {
      return entityId + ":" + difficulty + ":" + checksum + ":" + digest;
    }
  }

  private final Map<Integer, Entry> entries;

  public static D2SCheckpoint of(Map<Integer, Entry> entries) {
    if (entries == null) throw new NullPointerException("entries");
    TreeMap<Integer, Entry> copy = new TreeMap<>();
    for (Map.Entry<Integer, Entry> entry : entries.entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null
          || entry.getKey().intValue() != entry.getValue().entityId()) {
        throw new IllegalArgumentException("entry identity");
      }
      copy.put(entry.getKey(), entry.getValue());
    }
    return new D2SCheckpoint(copy);
  }

  private D2SCheckpoint(Map<Integer, Entry> entries) {
    this.entries = Collections.unmodifiableMap(new TreeMap<>(entries));
  }

  public Map<Integer, Entry> entries() { return entries; }
  public Entry entry(int entityId) { return entries.get(entityId); }

  public String canonical() {
    StringBuilder result = new StringBuilder("d2s-v1");
    for (Entry entry : entries.values()) result.append("|entity:").append(entry.canonical());
    return result.toString();
  }

  private static String sha256(byte[] bytes) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
      StringBuilder result = new StringBuilder(digest.length * 2);
      for (byte value : digest) result.append(String.format("%02x", value & 0xff));
      return result.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError("JRE must provide SHA-256", e);
    }
  }

  /** Builds a validated immutable entry for checkpoint transport codecs. */
  public static Entry entry(int entityId, int difficulty, byte[] bytes) {
    return new Entry(entityId, difficulty, bytes);
  }
}
