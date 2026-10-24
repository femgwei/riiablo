package com.riiablo.engine.replay;

import java.util.Objects;
import java.util.function.Function;

import com.riiablo.engine.SimulationCommandEnvelope;

/** In-memory replay recorder with an explicit canonical payload encoder. */
public final class SimulationReplayCapture implements SimulationReplayRecorder {
  private final Function<Object, String> payloadEncoder;
  private final SimulationReplayLog.Builder log;

  public SimulationReplayCapture(int gameSeed, Function<Object, String> payloadEncoder) {
    this.payloadEncoder = Objects.requireNonNull(payloadEncoder, "payloadEncoder");
    this.log = SimulationReplayLog.builder(gameSeed);
  }

  @Override public void recordCommand(SimulationCommandEnvelope command) {
    Objects.requireNonNull(command, "command");
    String payload = payloadEncoder.apply(command.payload);
    if (payload == null) throw new IllegalStateException("payloadEncoder returned null");
    log.command(command, payload);
  }

  @Override public void recordCheckpoint(SimulationCheckpoint checkpoint) {
    log.checkpoint(Objects.requireNonNull(checkpoint, "checkpoint"));
  }

  public SimulationReplayLog snapshot() { return log.build(); }
}
