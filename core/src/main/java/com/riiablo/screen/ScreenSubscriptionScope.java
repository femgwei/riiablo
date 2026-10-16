package com.riiablo.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Reusable show/hide subscriptions, closed permanently when their screen is disposed. */
final class ScreenSubscriptionScope {
  private final List<Runnable> attach = new ArrayList<>();
  private final List<Runnable> detach = new ArrayList<>();
  private int attached;
  private boolean active;
  private boolean disposed;

  void add(Runnable subscribe, Runnable unsubscribe) {
    if (active || disposed) throw new IllegalStateException("scope is active or disposed");
    Objects.requireNonNull(subscribe, "subscribe");
    Objects.requireNonNull(unsubscribe, "unsubscribe");
    attach.add(subscribe);
    detach.add(unsubscribe);
  }

  void activate() {
    if (disposed) throw new IllegalStateException("scope is disposed");
    if (active) return;
    active = true;
    try {
      while (attached < attach.size()) {
        // Include the current registration in rollback: an initial callback
        // (for example Cvar.onLoaded) can throw after the listener was added.
        int index = attached++;
        attach.get(index).run();
      }
    } catch (RuntimeException | Error failure) {
      try {
        deactivate();
      } catch (RuntimeException | Error cleanup) {
        if (failure != cleanup) failure.addSuppressed(cleanup);
      }
      throw failure;
    }
  }

  void deactivate() {
    active = false;
    Throwable failure = null;
    while (attached > 0) {
      try {
        detach.get(--attached).run();
      } catch (RuntimeException | Error cleanup) {
        if (failure == null) failure = cleanup;
        else if (failure != cleanup) failure.addSuppressed(cleanup);
      }
    }
    if (failure instanceof RuntimeException) throw (RuntimeException) failure;
    if (failure instanceof Error) throw (Error) failure;
  }

  void dispose() {
    if (disposed) return;
    disposed = true;
    try {
      deactivate();
    } finally {
      attach.clear();
      detach.clear();
    }
  }
}
