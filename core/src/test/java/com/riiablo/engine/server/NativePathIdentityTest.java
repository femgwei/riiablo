package com.riiablo.engine.server;

import com.artemis.World;
import com.riiablo.engine.server.component.NativePathIdentity;
import com.riiablo.engine.server.component.UnitLifecycle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativePathIdentityTest {
  private static NativePathIdentity identity(World world, int id) {
    return world.getMapper(NativePathIdentity.class).create(id).allocated(world, id);
  }

  @Test void sameIdInAnotherWorldIsNotTheSameLifetime() {
    World a = new World(), b = new World();
    try {
      int ai = a.create(), bi = b.create();
      assertEquals(ai, bi);
      NativePathIdentity.Token at = identity(a, ai).token(), bt = identity(b, bi).token();
      a.process(); b.process();
      assertTrue(at.isCurrent(a, ai));
      assertFalse(at.isCurrent(b, bi));
      assertFalse(bt.isCurrent(a, ai));
      assertFalse(at.isCurrent(a, ai + 1));
    } finally { a.dispose(); b.dispose(); }
  }

  @Test void deletionAndActualIdReuseCannotReviveAnOldToken() {
    World world = new World();
    try {
      int first = world.create();
      NativePathIdentity.Token old = identity(world, first).token();
      world.process();
      world.delete(first); world.process();
      assertFalse(old.isCurrent(world, first));
      int replacement = world.create();
      assertEquals(first, replacement, "exercise real ECS ID recycling");
      NativePathIdentity pooled = world.getMapper(NativePathIdentity.class).create(replacement);
      assertNull(pooled.token(), "the pooled component must not retain the old birth");
      pooled.allocated(world, replacement);
      world.process();
      assertNotSame(old, pooled.token());
      assertFalse(old.isCurrent(world, replacement));
      assertTrue(pooled.token().isCurrent(world, replacement));
    } finally { world.dispose(); }
  }

  @Test void deathAndInPlaceResurrectionKeepTheLifetime() {
    World world = new World();
    try {
      int id = world.create();
      NativePathIdentity identity = identity(world, id);
      NativePathIdentity.Token token = identity.token();
      UnitLifecycle lifecycle = world.getMapper(UnitLifecycle.class).create(id);
      world.process();
      lifecycle.transition(UnitLifecycle.Phase.DEATH);
      assertTrue(token.isCurrent(world, id));
      lifecycle.revive();
      assertSame(token, identity.token());
      assertTrue(token.isCurrent(world, id));
    } finally { world.dispose(); }
  }

  @Test void removingIdentityCannotSilentlyFallBackToTheNumericId() {
    World world = new World();
    try {
      int id = world.create();
      NativePathIdentity.Token token = identity(world, id).token();
      world.process();
      world.getMapper(NativePathIdentity.class).remove(id);
      world.process();
      assertFalse(token.isCurrent(world, id));
      assertNull(world.getMapper(NativePathIdentity.class).create(id).token());
      assertFalse(token.isCurrent(world, id));
    } finally { world.dispose(); }
  }

  @Test void explicitNewBirthReplacesIdentityButReadingDoesNot() {
    World world = new World();
    try {
      int id = world.create();
      NativePathIdentity component = identity(world, id);
      NativePathIdentity.Token old = component.token();
      world.process();
      assertSame(old, component.token());
      component.allocated(world, id);
      assertFalse(old.isCurrent(world, id));
      assertTrue(component.token().isCurrent(world, id));
    } finally { world.dispose(); }
  }

  @Test void unknownAllocationIsNotInventedAndInvalidInputsAreRejected() {
    NativePathIdentity unknown = new NativePathIdentity();
    assertNull(unknown.token());
    assertThrows(IllegalArgumentException.class, () -> unknown.allocated(null, -1));
    assertThrows(NullPointerException.class, () -> unknown.allocated(null, 0));
    assertNull(unknown.token());
  }
}
