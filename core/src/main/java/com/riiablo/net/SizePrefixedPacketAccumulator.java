package com.riiablo.net;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Accumulates a TCP byte stream and emits complete little-endian,
 * size-prefixed frames. TCP does not preserve application packet boundaries,
 * so callers must retain incomplete prefixes and payloads between reads.
 */
public final class SizePrefixedPacketAccumulator {
  public interface FrameConsumer {
    void accept(ByteBuffer frame);
  }

  public static final class InvalidFrameException extends IllegalArgumentException {
    public final int frameSize;

    InvalidFrameException(int frameSize, int maxFrameSize) {
      super("Invalid size-prefixed frame: size=" + frameSize + " max=" + maxFrameSize);
      this.frameSize = frameSize;
    }
  }

  private static final int PREFIX_SIZE = Integer.BYTES;

  private final int maxFrameSize;
  private final int maxBufferedSize;
  private ByteBuffer buffer;

  public SizePrefixedPacketAccumulator(
      int initialCapacity, int maxFrameSize, int maxBufferedSize) {
    if (initialCapacity < PREFIX_SIZE) throw new IllegalArgumentException("initialCapacity < 4");
    if (maxFrameSize <= 0) throw new IllegalArgumentException("maxFrameSize <= 0");
    if (maxBufferedSize < maxFrameSize + PREFIX_SIZE) {
      throw new IllegalArgumentException("maxBufferedSize cannot hold one maximum frame");
    }
    if (initialCapacity > maxBufferedSize) {
      throw new IllegalArgumentException("initialCapacity > maxBufferedSize");
    }
    this.maxFrameSize = maxFrameSize;
    this.maxBufferedSize = maxBufferedSize;
    buffer = ByteBuffer.allocate(initialCapacity).order(ByteOrder.LITTLE_ENDIAN);
  }

  public void append(byte[] bytes, int offset, int length) {
    if (bytes == null) throw new NullPointerException("bytes");
    if (offset < 0 || length < 0 || offset > bytes.length - length) {
      throw new IndexOutOfBoundsException(
          "offset=" + offset + " length=" + length + " bytes=" + bytes.length);
    }
    ensureWritable(length);
    buffer.put(bytes, offset, length);
  }

  public void append(ByteBuffer source) {
    if (source == null) throw new NullPointerException("source");
    ensureWritable(source.remaining());
    buffer.put(source);
  }

  /** Emits all complete frames and retains any incomplete tail. */
  public int drain(FrameConsumer consumer) {
    if (consumer == null) throw new NullPointerException("consumer");
    int frames = 0;
    buffer.flip();
    try {
      while (buffer.remaining() >= PREFIX_SIZE) {
        buffer.mark();
        int frameSize = buffer.getInt();
        if (frameSize <= 0 || frameSize > maxFrameSize) {
          buffer.clear();
          throw new InvalidFrameException(frameSize, maxFrameSize);
        }
        if (buffer.remaining() < frameSize) {
          buffer.reset();
          break;
        }

        ByteBuffer frame = buffer.slice().order(ByteOrder.LITTLE_ENDIAN);
        frame.limit(frameSize);
        buffer.position(buffer.position() + frameSize);
        frames++;
        // Consume the parent buffer first. If application-level processing of
        // this valid frame fails, the next TCP frame must still retain its own
        // prefix instead of inheriting a payload-only tail.
        consumer.accept(frame);
      }
    } finally {
      // InvalidFrameException clears the buffer before reaching here. Avoid
      // compacting a freshly-cleared buffer, which would incorrectly make its
      // entire capacity look like buffered input.
      if (buffer.limit() != buffer.capacity() || buffer.position() != 0) {
        buffer.compact();
      }
    }
    return frames;
  }

  public int pendingBytes() {
    return buffer.position();
  }

  /** Returns -1 until a complete four-byte prefix has arrived. */
  public int expectedFrameSize() {
    return buffer.position() < PREFIX_SIZE ? -1 : buffer.getInt(0);
  }

  public void clear() {
    buffer.clear();
  }

  private void ensureWritable(int length) {
    if (length == 0) return;
    long required = (long) buffer.position() + length;
    if (required > maxBufferedSize) {
      throw new IllegalStateException(
          "Network frame buffer exceeded limit: required=" + required
              + " max=" + maxBufferedSize);
    }
    if (required <= buffer.capacity()) return;

    int capacity = buffer.capacity();
    while (capacity < required) {
      capacity = Math.min(maxBufferedSize, capacity << 1);
      if (capacity < 0) capacity = maxBufferedSize;
    }
    ByteBuffer expanded = ByteBuffer.allocate(capacity).order(ByteOrder.LITTLE_ENDIAN);
    buffer.flip();
    expanded.put(buffer);
    buffer = expanded;
  }
}
