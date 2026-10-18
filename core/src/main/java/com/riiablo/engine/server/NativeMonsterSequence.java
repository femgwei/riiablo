package com.riiablo.engine.server;

import com.riiablo.Riiablo;
import com.riiablo.Files;
import com.riiablo.mpq.MPQFileHandleResolver;
import java.util.ArrayList;
import java.util.List;

/** MonSeq source points and actions; the sequence clock is independent of COF speed. */
public final class NativeMonsterSequence {
  private static final class CacheState {
    final java.util.Map<String, NativeMonsterSequence> cache = new java.util.HashMap<>();
    Files files;
    MPQFileHandleResolver source;
  }
  private static final ThreadLocal<CacheState> CURRENT =
      ThreadLocal.withInitial(CacheState::new);
  public final byte mode;
  public final int[] frames;
  public final byte[] modes;
  public final byte[] actions;
  NativeMonsterSequence(byte mode, int[] frames, byte[] modes, byte[] actions) {
    this.mode = mode; this.frames = frames; this.modes = modes; this.actions = actions;
  }

  /** Non-wrapping SequenceTbls.cpp event selection, with an exclusive end sentinel. */
  public int lastActionPoint(int previous, int next) {
    if (previous < 0 || next < previous) throw new IllegalArgumentException("forward cursor required");
    int last = -1;
    int start = previous == next ? next >>> 8 : (previous >>> 8) + 1;
    for (int point = start; point <= (next >>> 8) && point < actions.length; point++)
      if (actions[point] != 0) last = point;
    return last;
  }
  public static NativeMonsterSequence load(String name) {
    return load(name, Riiablo.files, Riiablo.mpqs);
  }

  public static NativeMonsterSequence load(String name, Files files,
      MPQFileHandleResolver mpqs) {
    if (name == null || !name.startsWith("seq_")) return null;
    CacheState state = CURRENT.get();
    if (state.source != mpqs || state.files != files) {
      state.cache.clear();
      state.source = mpqs;
      state.files = files;
    }
    if (mpqs == null || files == null || files.MonMode == null) return null;
    NativeMonsterSequence cached = state.cache.get(name);
    if (cached != null) return cached;
    List<Integer> frames = new ArrayList<>();
    List<Byte> actions = new ArrayList<>();
    List<Byte> modes = new ArrayList<>();
    int mode = -1;
    String text = mpqs.resolve("data\\global\\excel\\monseq.txt").readString("windows-1252");
    for (String line : text.split("\\r?\\n")) {
      String[] columns = line.split("\\t", -1);
      if (columns.length < 5 || !name.equals(columns[0])) continue;
      int pointMode = files.MonMode.index(columns[1]);
      if (mode < 0) mode = pointMode;
      modes.add((byte) pointMode);
      frames.add(Integer.parseInt(columns[2]));
      actions.add(columns[4].isEmpty() ? (byte) 0 : Byte.parseByte(columns[4]));
    }
    if (frames.isEmpty() || mode < 0) return null;
    int[] f = new int[frames.size()]; byte[] a = new byte[f.length], m = new byte[f.length];
    for (int i = 0; i < f.length; i++) { f[i] = frames.get(i); a[i] = actions.get(i); m[i] = modes.get(i); }
    NativeMonsterSequence result = new NativeMonsterSequence((byte) mode, f, m, a);
    state.cache.put(name, result);
    return result;
  }
}
