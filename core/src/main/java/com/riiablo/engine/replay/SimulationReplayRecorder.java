package com.riiablo.engine.replay;

import com.riiablo.engine.SimulationCommandEnvelope;

/** Receives only admitted commands and checkpoints from a fixed-tick simulation. */
public interface SimulationReplayRecorder {
  SimulationReplayRecorder NONE = new SimulationReplayRecorder() {
    @Override public void recordCommand(SimulationCommandEnvelope command) {}
    @Override public void recordCheckpoint(SimulationCheckpoint checkpoint) {}
  };

  void recordCommand(SimulationCommandEnvelope command);

  void recordCheckpoint(SimulationCheckpoint checkpoint);
}
