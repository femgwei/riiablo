package com.riiablo.save;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.item.Item;
import com.riiablo.item.Location;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ItemLocationListenerTest {
  @Test
  void removingOneSessionListenerPreservesOtherListenersAndItemData() {
    ItemData items = new ItemData(null, null);
    AtomicInteger oldCalls = new AtomicInteger();
    AtomicInteger newCalls = new AtomicInteger();
    ItemData.LocationListener old = (data, from, to, item) -> oldCalls.incrementAndGet();
    ItemData.LocationListener replacement = (data, from, to, item) -> newCalls.incrementAndGet();
    items.addLocationListener(old);
    items.addLocationListener(replacement);
    Item item = new Item();
    items.setLocation(item, Location.CURSOR);
    assertEquals(1, oldCalls.get());
    assertEquals(1, newCalls.get());
    assertTrue(items.removeLocationListener(old));
    assertFalse(items.removeLocationListener(old));
    items.setLocation(item, Location.STORED);
    assertEquals(1, oldCalls.get());
    assertEquals(2, newCalls.get());
    assertEquals(Location.STORED, item.location);
  }

  @Test
  void balancedRemovalDoesNotClearAnotherRegistrationOfTheSameCursor() {
    ItemData items = new ItemData(null, null);
    AtomicInteger calls = new AtomicInteger();
    ItemData.LocationListener cursor = (data, from, to, item) -> calls.incrementAndGet();
    items.addLocationListener(cursor);
    items.addLocationListener(cursor);
    assertTrue(items.removeLocationListener(cursor));
    items.setLocation(new Item(), Location.CURSOR);
    assertEquals(1, calls.get());
  }
}
