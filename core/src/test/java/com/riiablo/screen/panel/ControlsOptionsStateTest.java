package com.riiablo.screen.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ControlsOptionsStateTest {
  @Test
  void captureConflictSaveAndClearStatesHaveStableLabels() {
    ControlsOptionsState state = new ControlsOptionsState();
    assertEquals("SELECT A BINDING TO CHANGE IT", state.label());
    state.beginCapture("Skill 1", false);
    assertEquals(ControlsOptionsState.Status.CAPTURING, state.getStatus());
    assertEquals("PRESS A KEY FOR Skill 1 (PRIMARY)", state.label());
    state.conflict("Inventory");
    assertEquals("CONFLICT: Inventory", state.label());
    state.saved();
    assertEquals("BINDING SAVED", state.label());
    state.cleared();
    assertEquals("BINDING CLEARED", state.label());
  }

  @Test
  void defaultsAndCancelReturnToUnambiguousState() {
    ControlsOptionsState state = new ControlsOptionsState();
    state.beginCapture("Automap", true);
    state.defaultsRestored();
    assertEquals(ControlsOptionsState.Status.DEFAULTS_RESTORED, state.getStatus());
    assertEquals("DEFAULTS RESTORED", state.label());
    state.cancel();
    assertEquals(ControlsOptionsState.Status.IDLE, state.getStatus());
    assertEquals("SELECT A BINDING TO CHANGE IT", state.label());
  }
}
