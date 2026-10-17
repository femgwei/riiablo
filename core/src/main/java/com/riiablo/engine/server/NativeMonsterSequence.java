package com.riiablo.engine.server;

import com.riiablo.Riiablo;
import java.util.ArrayList;
import java.util.List;

/** MonSeq source points and actions; the sequence clock is independent of COF speed. */
public final class NativeMonsterSequence {
  private static final java.util.Map<String, NativeMonsterSequence> CACHE = new java.util.HashMap<>();
  private static java.lang.Object source;
  public final byte mode;
  public final int[] frames;
  public final byte[] modes;
  public final byte[] actions;
  private NativeMonsterSequence(byte mode, int[] frames, byte[] modes, byte[] actions) {
    this.mode = mode; this.frames = frames; this.modes = modes; this.actions = actions;
  }
  public static NativeMonsterSequence load(String name) {
    if (name == null || !java.util.Arrays.asList("seq_ancientwhirlwind", "seq_ancientleap",
        "seq_genericspawner", "seq_mosquitoskill", "seq_vileres", "seq_undeadbestow",
        "seq_fethisraise", "seq_reanimatedcharge", "seq_siegebeastcharge",
        "seq_nihlathakarctic", "seq_serpentcharge", "seq_impinferno").contains(name)) return null;
    if (source != Riiablo.mpqs) { CACHE.clear(); source = Riiablo.mpqs; }
    NativeMonsterSequence cached = CACHE.get(name);
    if (cached != null) return cached;
    List<Integer> frames = new ArrayList<>();
    List<Byte> actions = new ArrayList<>();
    List<Byte> modes = new ArrayList<>();
    int mode = -1;
    String text = Riiablo.mpqs.resolve("data\\global\\excel\\monseq.txt").readString("windows-1252");
    for (String line : text.split("\\r?\\n")) {
      String[] columns = line.split("\\t", -1);
      if (columns.length < 5 || !name.equals(columns[0])) continue;
      int pointMode = Riiablo.files.MonMode.index(columns[1]);
      if (mode < 0) mode = pointMode;
      modes.add((byte) pointMode);
      frames.add(Integer.parseInt(columns[2]));
      actions.add(columns[4].isEmpty() ? (byte) 0 : Byte.parseByte(columns[4]));
    }
    if (frames.isEmpty() || mode < 0) throw new IllegalStateException("Missing MonSeq: " + name);
    int[] f = new int[frames.size()]; byte[] a = new byte[f.length], m = new byte[f.length];
    for (int i = 0; i < f.length; i++) { f[i] = frames.get(i); a[i] = actions.get(i); m[i] = modes.get(i); }
    NativeMonsterSequence result = new NativeMonsterSequence((byte) mode, f, m, a);
    CACHE.put(name, result);
    return result;
  }
}
