package com.riiablo.util;

import com.badlogic.gdx.utils.Disposable;
import java.util.Objects;

/**
 * A short-lived cleanup pass: attempt every action, then propagate the first failure.
 * Does not register, own, deduplicate or retry resources; callers decide ownership and order.
 */
public final class Disposal implements AutoCloseable {
  private Throwable failure;
  private boolean closed;

  public void run(Runnable action) {
    if (closed) throw new IllegalStateException("cleanup pass is closed");
    Objects.requireNonNull(action, "action");
    try {
      action.run();
    } catch (RuntimeException | Error cleanup) {
      if (failure == null) failure = cleanup;
      else if (failure != cleanup) failure.addSuppressed(cleanup);
    }
  }

  public void dispose(Disposable resource) {
    if (resource != null) run(resource::dispose);
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    Throwable first = failure;
    failure = null;
    if (first instanceof RuntimeException) throw (RuntimeException) first;
    if (first instanceof Error) throw (Error) first;
  }
}
