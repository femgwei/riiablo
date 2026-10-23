package com.riiablo.codec.excel;

/** Raised when a strict TXT data-pack is missing a required table. */
public final class MissingTxtDataException extends IllegalStateException {
  public MissingTxtDataException(String message) {
    super(message);
  }
}
