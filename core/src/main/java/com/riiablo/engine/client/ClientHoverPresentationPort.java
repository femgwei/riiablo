package com.riiablo.engine.client;

/** Presentation-owned hover facts and highlight operations. */
public interface ClientHoverPresentationPort {
  ClientHoverBounds bounds(int entityId);
  boolean containsLabel(int entityId, float screenX, float screenY);
  void setHighlighted(int entityId, boolean highlighted);
  boolean isUnsummonSelected();
}
