package com.riiablo.engine.server;

import java.util.concurrent.atomic.AtomicLong;

/** Shared event order for the ECS projection of Targets.cpp head insertion.
 * Runtime only: not a native node index, GUID, pet quota order or save field. */
public final class NativeTargetOrder {
  private static final AtomicLong next = new AtomicLong();

  private NativeTargetOrder() {}

  public static long inserted() {
    return next.incrementAndGet();
  }
}
