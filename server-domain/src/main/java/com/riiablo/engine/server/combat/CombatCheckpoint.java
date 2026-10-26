package com.riiablo.engine.server.combat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable, compact combat checkpoint for diagnostics and replay comparison.
 *
 * <p>This is intentionally a summary, not a save/restore format: it records
 * the authoritative hit boundary without claiming to reproduce the complete
 * M8 world snapshot.</p>
 */
public final class CombatCheckpoint {
  private final long tick;
  private final int casterId;
  private final int targetId;
  private final int skillId;
  private final int lifeBeforeFixed;
  private final int lifeAfterFixed;
  private final int missileCount;

  private CombatCheckpoint(long tick, int casterId, int targetId, int skillId,
      int lifeBeforeFixed, int lifeAfterFixed, int missileCount) {
    this.tick = tick;
    this.casterId = casterId;
    this.targetId = targetId;
    this.skillId = skillId;
    this.lifeBeforeFixed = lifeBeforeFixed;
    this.lifeAfterFixed = lifeAfterFixed;
    this.missileCount = missileCount;
  }

  public static CombatCheckpoint of(long tick, int casterId, int targetId, int skillId,
      int lifeBeforeFixed, int lifeAfterFixed, int missileCount) {
    if (tick < 0 || casterId < 0 || targetId < 0 || skillId < 0
        || lifeBeforeFixed < 0 || lifeAfterFixed < 0 || missileCount < 0) {
      throw new IllegalArgumentException("combat checkpoint fields must be non-negative");
    }
    return new CombatCheckpoint(tick, casterId, targetId, skillId,
        lifeBeforeFixed, lifeAfterFixed, missileCount);
  }

  public long tick() { return tick; }
  public int casterId() { return casterId; }
  public int targetId() { return targetId; }
  public int skillId() { return skillId; }
  public int lifeBeforeFixed() { return lifeBeforeFixed; }
  public int lifeAfterFixed() { return lifeAfterFixed; }
  public int missileCount() { return missileCount; }

  /** Canonical field order used by diagnostics and stable digesting. */
  public String canonical() {
    return tick + "|" + casterId + "|" + targetId + "|" + skillId + "|"
        + lifeBeforeFixed + "|" + lifeAfterFixed + "|" + missileCount;
  }

  /** SHA-256 digest of the canonical summary, rendered as lowercase hex. */
  public String digest() {
    try {
      byte[] bytes = MessageDigest.getInstance("SHA-256")
          .digest(canonical().getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(bytes.length * 2);
      for (byte value : bytes) hex.append(String.format("%02x", value & 0xff));
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError("JRE must provide SHA-256", e);
    }
  }

  /**
   * Checks that a post-restore combat summary really continues this hit
   * boundary.  This is a field-level replay gate; it does not replace the
   * complete ECS checkpoint or save image.
   */
  public Continuation verifyContinuation(CombatCheckpoint resumed) {
    if (resumed == null) throw new NullPointerException("resumed");
    ArrayList<String> mismatches = new ArrayList<>();
    if (resumed.tick <= tick) mismatches.add("tick");
    if (resumed.casterId != casterId) mismatches.add("casterId");
    if (resumed.targetId != targetId) mismatches.add("targetId");
    if (resumed.skillId != skillId) mismatches.add("skillId");
    if (resumed.lifeBeforeFixed != lifeAfterFixed) mismatches.add("lifeBeforeFixed");
    return new Continuation(mismatches);
  }

  public static final class Continuation {
    private final List<String> mismatches;

    private Continuation(List<String> mismatches) {
      this.mismatches = Collections.unmodifiableList(new ArrayList<>(mismatches));
    }

    public boolean accepted() { return mismatches.isEmpty(); }
    public List<String> mismatches() { return mismatches; }
  }
}
