package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.scenes.scene2d.Actor;

class ClientHoverPresentationAdapterTest {
  @Test
  void labelBoundsAreSelectable() {
    Actor label = new Actor();
    label.setBounds(100, 200, 80, 16);

    assertTrue(ClientHoverPresentationAdapter.containsLabelPoint(label, 140, 208));
    assertTrue(ClientHoverPresentationAdapter.containsLabelPoint(label, 100, 200));
    assertFalse(ClientHoverPresentationAdapter.containsLabelPoint(label, 99, 208));
    assertFalse(ClientHoverPresentationAdapter.containsLabelPoint(label, 140, 217));
  }
}
