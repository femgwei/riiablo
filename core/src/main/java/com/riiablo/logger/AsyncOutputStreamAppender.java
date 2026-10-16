package com.riiablo.logger;

import java.io.OutputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncOutputStreamAppender implements Appender, Runnable {
  private final OutputStream out;
  private final Encoder encoder = new RiiabloEncoder();

  private final Thread thread;
  private final BlockingQueue<LogEvent> queue = new ArrayBlockingQueue<>(65536);
  private final AtomicBoolean closed = new AtomicBoolean();

  public AsyncOutputStreamAppender(OutputStream out) {
    this.out = out;

    thread = new Thread(this);
    thread.setName("AsyncOutputStreamAppender-Worker");
    thread.setDaemon(true);
    thread.start();
  }

  /** Stop the worker, flush queued events, and release every pooled event. */
  public synchronized void dispose() {
    if (!closed.compareAndSet(false, true)) return;
    if (!thread.isAlive()) return;
    thread.interrupt();
    try {
      thread.join(2000L);
    } catch (InterruptedException interruption) {
      Thread.currentThread().interrupt();
    }
    LogEvent event;
    while ((event = queue.poll()) != null) event.release();
  }

  @Override
  public Encoder encoder() {
    return encoder;
  }

  @Override
  public synchronized void append(LogEvent event) {
    if (closed.get()) { event.release(); return; }
    boolean added = queue.offer(event);
    assert added : "event(" + event + ") could not be added to queue!";
  }

  @Override
  public void run() {
    LogEvent event = null;
    for (;;) {
      try {
        event = queue.take();
        encoder.encode(event, out);
      } catch (InterruptedException ignored) {
        while ((event = queue.poll()) != null) {
          try { encoder.encode(event, out); }
          finally { event.release(); }
        }
        event = null;
        return;
      } finally {
        if (event != null) {
          event.release();
          event = null;
        }
      }
    }
  }
}
