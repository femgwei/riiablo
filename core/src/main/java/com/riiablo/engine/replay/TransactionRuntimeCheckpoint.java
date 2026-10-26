package com.riiablo.engine.replay;

import com.riiablo.engine.AuthoritativeSessionRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Portable metadata for D2GS request transactions.
 *
 * Socket, packet, handler and arbitrary command payload objects are deliberately
 * excluded.  A host may retain completed idempotency responses here, but only as
 * immutable bytes and only after assigning a stable request identity.
 */
public final class TransactionRuntimeCheckpoint {
  private static final int VERSION = 1;
  public static final int MAX_BYTES = 16 * 1024 * 1024;
  private static final int MAX_ENTRIES = 1_000_000;
  private static final int MAX_STRING = 64 * 1024;

  public static final class Entry {
    public final int connectionId;
    public final long generation;
    public final int actorId;
    public final long requestId;
    public final String kind;
    public final int targetEntityId;
    public final long observedTick;
    public final long targetTick;
    /** Canonical intent fields; never an arbitrary Java object. */
    public final String intent;
    /** Completed idempotency response, or empty when no response is retained. */
    private final byte[] response;

    private Entry(int connectionId, long generation, int actorId, long requestId,
        String kind, int targetEntityId, long observedTick, long targetTick,
        String intent, byte[] response) {
      if (connectionId < 0 || generation <= 0L || actorId < 0 || requestId <= 0L
          || kind == null || kind.trim().isEmpty() || targetEntityId < -1
          || observedTick < 0L || targetTick < 0L || targetTick < observedTick
          || intent == null || intent.length() > MAX_STRING) {
        throw new IllegalArgumentException("invalid transaction entry");
      }
      this.connectionId = connectionId;
      this.generation = generation;
      this.actorId = actorId;
      this.requestId = requestId;
      this.kind = kind;
      this.targetEntityId = targetEntityId;
      this.observedTick = observedTick;
      this.targetTick = targetTick;
      this.intent = intent;
      this.response = response == null ? new byte[0] : response.clone();
    }

    public static Entry of(int connectionId, long generation, int actorId, long requestId,
        String kind, int targetEntityId, long observedTick, long targetTick,
        String intent, byte[] response) {
      return new Entry(connectionId, generation, actorId, requestId, kind, targetEntityId,
          observedTick, targetTick, intent, response);
    }

    public byte[] response() { return response.clone(); }

    String canonical() {
      return connectionId + ":" + generation + ":" + actorId + ":" + requestId + ":"
          + kind + ":" + targetEntityId + ":" + observedTick + ":" + targetTick + ":"
          + intent + ":" + response.length + ":" + digest(response);
    }

    private static String digest(byte[] bytes) {
      try {
        byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result = new StringBuilder(hash.length * 2);
        for (byte value : hash) result.append(String.format("%02x", value & 0xff));
        return result.toString();
      } catch (java.security.NoSuchAlgorithmException impossible) {
        throw new AssertionError(impossible);
      }
    }
  }

  private final long nextIngressRequestId;
  private final List<Entry> entries;
  private final byte[] bytes;

  private TransactionRuntimeCheckpoint(long nextIngressRequestId, List<Entry> entries,
      byte[] bytes) {
    this.nextIngressRequestId = nextIngressRequestId;
    this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    this.bytes = bytes;
  }

  public static TransactionRuntimeCheckpoint capture(long nextIngressRequestId,
      List<Entry> entries) {
    if (nextIngressRequestId <= 0L) throw new IllegalArgumentException("next request id");
    if (entries == null) throw new NullPointerException("entries");
    List<Entry> copy = new ArrayList<>(entries);
    validate(nextIngressRequestId, copy);
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(bytes);
      out.writeInt(VERSION);
      out.writeLong(nextIngressRequestId);
      out.writeInt(copy.size());
      for (Entry entry : copy) write(out, entry);
      out.flush();
      return fromBytes(bytes.toByteArray());
    } catch (IOException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  public static TransactionRuntimeCheckpoint empty(long nextIngressRequestId) {
    return capture(nextIngressRequestId, Collections.emptyList());
  }

  public static TransactionRuntimeCheckpoint fromBytes(byte[] encoded) {
    if (encoded == null || encoded.length == 0 || encoded.length > MAX_BYTES) {
      throw new IllegalArgumentException("transaction runtime length");
    }
    try {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(encoded));
      if (in.readInt() != VERSION) throw new IllegalArgumentException("transaction runtime version");
      long next = in.readLong();
      int count = in.readInt();
      if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("transaction count");
      List<Entry> entries = new ArrayList<>(count);
      for (int i = 0; i < count; i++) entries.add(read(in));
      if (in.read() != -1) throw new IllegalArgumentException("trailing transaction runtime");
      validate(next, entries);
      return new TransactionRuntimeCheckpoint(next, entries, encoded.clone());
    } catch (EOFException e) {
      throw new IllegalArgumentException("truncated transaction runtime", e);
    } catch (IOException e) {
      throw new IllegalArgumentException("invalid transaction runtime", e);
    }
  }

  public long nextIngressRequestId() { return nextIngressRequestId; }
  public List<Entry> entries() { return entries; }
  public byte[] bytes() { return bytes.clone(); }

  /** Canonical witness used by the enclosing D2GS checkpoint digest. */
  public String canonical() {
    StringBuilder result = new StringBuilder("transaction-runtime-v1|")
        .append(nextIngressRequestId);
    for (Entry entry : entries) result.append('|').append(entry.canonical());
    return result.toString();
  }

  /** Validates that retained requests belong to the archived session generation. */
  public void validateSessions(AuthoritativeSessionRegistry.Snapshot sessions) {
    if (sessions == null && !entries.isEmpty()) throw new IllegalArgumentException("sessions missing");
    for (Entry entry : entries) {
      AuthoritativeSessionRegistry.Session session = null;
      for (AuthoritativeSessionRegistry.Session candidate : sessions.sessions()) {
        if (candidate.connectionId == entry.connectionId) { session = candidate; break; }
      }
      if (session == null || session.state != AuthoritativeSessionRegistry.State.ACTIVE
          || session.generation != entry.generation || session.actorId != entry.actorId) {
        throw new IllegalArgumentException("transaction session generation mismatch");
      }
    }
  }

  private static void validate(long next, List<Entry> entries) {
    if (next <= 0L || entries == null || entries.size() > MAX_ENTRIES) {
      throw new IllegalArgumentException("transaction runtime");
    }
    Set<String> keys = new HashSet<>();
    int previousConnection = -1;
    long previousRequest = 0L;
    for (Entry entry : entries) {
      if (entry == null || entry.requestId >= next || !keys.add(entry.connectionId + ":" + entry.requestId)
          || entry.connectionId < previousConnection
          || entry.connectionId == previousConnection && entry.requestId <= previousRequest) {
        throw new IllegalArgumentException("transaction request identity/order");
      }
      if (entry.connectionId != previousConnection) previousRequest = 0L;
      previousConnection = entry.connectionId;
      previousRequest = entry.requestId;
      if (entry.response.length > MAX_BYTES) throw new IllegalArgumentException("transaction response");
    }
  }

  private static void write(DataOutputStream out, Entry e) throws IOException {
    out.writeInt(e.connectionId); out.writeLong(e.generation); out.writeInt(e.actorId);
    out.writeLong(e.requestId); writeString(out, e.kind); out.writeInt(e.targetEntityId);
    out.writeLong(e.observedTick); out.writeLong(e.targetTick); writeString(out, e.intent);
    out.writeInt(e.response.length); out.write(e.response);
  }

  private static Entry read(DataInputStream in) throws IOException {
    int connection = in.readInt(); long generation = in.readLong(); int actor = in.readInt();
    long request = in.readLong(); String kind = readString(in); int target = in.readInt();
    long observed = in.readLong(); long targetTick = in.readLong(); String intent = readString(in);
    int length = in.readInt();
    if (length < 0 || length > MAX_BYTES || length > in.available()) {
      throw new IllegalArgumentException("transaction response length");
    }
    byte[] response = new byte[length]; in.readFully(response);
    return Entry.of(connection, generation, actor, request, kind, target, observed, targetTick,
        intent, response);
  }

  private static void writeString(DataOutputStream out, String value) throws IOException {
    byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    if (bytes.length > MAX_STRING) throw new IllegalArgumentException("transaction string");
    out.writeInt(bytes.length); out.write(bytes);
  }

  private static String readString(DataInputStream in) throws IOException {
    int length = in.readInt();
    if (length < 0 || length > MAX_STRING || length > in.available()) {
      throw new IllegalArgumentException("transaction string length");
    }
    byte[] bytes = new byte[length]; in.readFully(bytes);
    return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
  }
}
