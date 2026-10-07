package com.riiablo.map;

import java.util.Locale;

/** Aggregates {@link RenderSystem} cache-build costs at selected zoom levels. */
final class RenderCacheMetrics {
  private static final float ZOOM_EPSILON = 0.0001f;
  private static final float[] TARGET_ZOOMS = {1f, 2f, 5f};

  private final Sample[] samples = new Sample[TARGET_ZOOMS.length];

  RenderCacheMetrics() {
    for (int i = 0; i < samples.length; i++) {
      samples[i] = new Sample(TARGET_ZOOMS[i]);
    }
  }

  void record(float zoom, int visibleCells, int indexedEntities, int occupiedCells,
      long buildNanos) {
    Sample sample = sampleFor(zoom);
    if (sample == null) return;
    sample.record(visibleCells, indexedEntities, occupiedCells, buildNanos);
  }

  void reset() {
    for (Sample sample : samples) sample.reset();
  }

  Snapshot snapshot(float zoom) {
    Sample sample = sampleFor(zoom);
    return sample == null ? null : sample.snapshot();
  }

  String report() {
    StringBuilder builder = new StringBuilder("Render cache metrics");
    for (Sample sample : samples) {
      Snapshot snapshot = sample.snapshot();
      builder.append(System.lineSeparator()).append(String.format(Locale.ROOT,
          "zoom=%.0fx samples=%d visible(avg=%.1f) indexed(avg=%.1f) "
              + "occupied(avg=%.1f) buildCaches(avg=%.3fms max=%.3fms)",
          snapshot.zoom, snapshot.sampleCount, snapshot.averageVisibleCells,
          snapshot.averageIndexedEntities, snapshot.averageOccupiedCells,
          snapshot.averageBuildMillis, snapshot.maxBuildMillis));
    }
    return builder.toString();
  }

  private Sample sampleFor(float zoom) {
    for (Sample sample : samples) {
      if (Math.abs(zoom - sample.zoom) <= ZOOM_EPSILON) return sample;
    }
    return null;
  }

  static final class Snapshot {
    final float zoom;
    final long sampleCount;
    final double averageVisibleCells;
    final double averageIndexedEntities;
    final double averageOccupiedCells;
    final double averageBuildMillis;
    final double maxBuildMillis;

    Snapshot(float zoom, long sampleCount, double averageVisibleCells,
        double averageIndexedEntities, double averageOccupiedCells,
        double averageBuildMillis, double maxBuildMillis) {
      this.zoom = zoom;
      this.sampleCount = sampleCount;
      this.averageVisibleCells = averageVisibleCells;
      this.averageIndexedEntities = averageIndexedEntities;
      this.averageOccupiedCells = averageOccupiedCells;
      this.averageBuildMillis = averageBuildMillis;
      this.maxBuildMillis = maxBuildMillis;
    }
  }

  private static final class Sample {
    final float zoom;
    long sampleCount;
    long visibleCells;
    long indexedEntities;
    long occupiedCells;
    long buildNanos;
    long maxBuildNanos;

    Sample(float zoom) {
      this.zoom = zoom;
    }

    void record(int visibleCells, int indexedEntities, int occupiedCells, long buildNanos) {
      sampleCount++;
      this.visibleCells += visibleCells;
      this.indexedEntities += indexedEntities;
      this.occupiedCells += occupiedCells;
      this.buildNanos += buildNanos;
      maxBuildNanos = Math.max(maxBuildNanos, buildNanos);
    }

    void reset() {
      sampleCount = 0;
      visibleCells = 0;
      indexedEntities = 0;
      occupiedCells = 0;
      buildNanos = 0;
      maxBuildNanos = 0;
    }

    Snapshot snapshot() {
      double divisor = sampleCount == 0 ? 1d : sampleCount;
      return new Snapshot(zoom, sampleCount,
          visibleCells / divisor,
          indexedEntities / divisor,
          occupiedCells / divisor,
          buildNanos / divisor / 1_000_000d,
          maxBuildNanos / 1_000_000d);
    }
  }
}
