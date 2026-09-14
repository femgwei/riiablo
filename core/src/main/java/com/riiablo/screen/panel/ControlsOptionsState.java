package com.riiablo.screen.panel;

/** State contract shared by the controls menu and the hidden visual harness. */
public final class ControlsOptionsState {
  public enum Status { IDLE, CAPTURING, CONFLICT, SAVED, CLEARED, DEFAULTS_RESTORED }

  private Status status = Status.IDLE;
  private String binding;
  private String detail;

  public Status getStatus() {
    return status;
  }

  public String getBinding() {
    return binding;
  }

  public String getDetail() {
    return detail;
  }

  public void beginCapture(String binding, boolean secondary) {
    status = Status.CAPTURING;
    this.binding = binding;
    detail = secondary ? "SECONDARY" : "PRIMARY";
  }

  public void conflict(String owner) {
    status = Status.CONFLICT;
    detail = owner;
  }

  public void saved() {
    status = Status.SAVED;
    detail = null;
  }

  public void cleared() {
    status = Status.CLEARED;
    detail = null;
  }

  public void defaultsRestored() {
    status = Status.DEFAULTS_RESTORED;
    binding = null;
    detail = null;
  }

  public void cancel() {
    status = Status.IDLE;
    binding = null;
    detail = null;
  }

  public String label() {
    switch (status) {
      case CAPTURING:
        return "PRESS A KEY FOR " + binding + " (" + detail + ")";
      case CONFLICT:
        return "CONFLICT: " + detail;
      case SAVED:
        return "BINDING SAVED";
      case CLEARED:
        return "BINDING CLEARED";
      case DEFAULTS_RESTORED:
        return "DEFAULTS RESTORED";
      case IDLE:
      default:
        return "SELECT A BINDING TO CHANGE IT";
    }
  }
}
