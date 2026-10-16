package com.riiablo.logger;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class AsyncOutputStreamAppenderLifecycleTest {
  @Test
  void disposeStopsWorkerAndIsIdempotent() throws Exception {
    AsyncOutputStreamAppender appender =
        new AsyncOutputStreamAppender(new ByteArrayOutputStream());
    try {
      Thread worker = worker(appender);
      assertTrue(worker.isAlive());
      appender.dispose();
      assertFalse(worker.isAlive());
      appender.dispose();
    } finally {
      appender.dispose();
    }
  }

  private static Thread worker(AsyncOutputStreamAppender appender) throws Exception {
    Field field = AsyncOutputStreamAppender.class.getDeclaredField("thread");
    field.setAccessible(true);
    return (Thread) field.get(appender);
  }
}
