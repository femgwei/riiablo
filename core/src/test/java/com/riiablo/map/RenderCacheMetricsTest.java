package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RenderCacheMetricsTest {
  @Test
  void recordsOnlyBenchmarkZoomLevels() {
    RenderCacheMetrics metrics = new RenderCacheMetrics();
    metrics.record(1f, 100, 10, 8, 1_000_000L);
    metrics.record(1.5f, 200, 20, 16, 2_000_000L);

    RenderCacheMetrics.Snapshot snapshot = metrics.snapshot(1f);
    assertEquals(1, snapshot.sampleCount);
    assertEquals(100d, snapshot.averageVisibleCells);
    assertNull(metrics.snapshot(1.5f));
  }

  @Test
  void aggregatesCountsAndBuildTime() {
    RenderCacheMetrics metrics = new RenderCacheMetrics();
    metrics.record(2f, 100, 10, 8, 1_000_000L);
    metrics.record(2f, 300, 30, 12, 3_000_000L);

    RenderCacheMetrics.Snapshot snapshot = metrics.snapshot(2f);
    assertEquals(2, snapshot.sampleCount);
    assertEquals(200d, snapshot.averageVisibleCells);
    assertEquals(20d, snapshot.averageIndexedEntities);
    assertEquals(10d, snapshot.averageOccupiedCells);
    assertEquals(2d, snapshot.averageBuildMillis);
    assertEquals(3d, snapshot.maxBuildMillis);
  }

  @Test
  void resetClearsAllZoomBuckets() {
    RenderCacheMetrics metrics = new RenderCacheMetrics();
    metrics.record(1f, 100, 10, 8, 1_000_000L);
    metrics.record(5f, 500, 50, 40, 5_000_000L);
    metrics.reset();

    assertEquals(0, metrics.snapshot(1f).sampleCount);
    assertEquals(0, metrics.snapshot(5f).sampleCount);
    assertTrue(metrics.report().contains("zoom=2x samples=0"));
  }
}
