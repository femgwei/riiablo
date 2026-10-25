package com.riiablo.engine.server.component;

import com.artemis.PooledComponent;
import com.artemis.World;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;
import java.util.Objects;

/** Host entity-lifetime identity for path adapters; not a native unit ID or RNG.
 * Allocate at the factory birth boundary, never on path queries or resurrection. */
@Transient
@PooledWeaver
public class NativePathIdentity extends PooledComponent {
  private Token token;

  public NativePathIdentity allocated(World world, int entityId) {
    if (entityId < 0) throw new IllegalArgumentException("entity ID required");
    token = new Token(Objects.requireNonNull(world), entityId);
    return this;
  }

  public Token token() { return token; }

  /** A checkpoint's stale lifetime reference must never bind to a recycled owner. */
  public static Token retiredToken(World world, int entityId) {
    return new Token(Objects.requireNonNull(world), entityId);
  }

  @Override protected void reset() { token = null; }

  /** Retain this immutable token, not the pooled component. Comparison is by identity. */
  public static final class Token {
    private final World world;
    private final int entityId;

    private Token(World world, int entityId) {
      this.world = world;
      this.entityId = entityId;
    }

    /** ECS deletion takes effect at its normal flush boundary. Death keeps identity. */
    public boolean isCurrent(World currentWorld, int currentId) {
      if (world != currentWorld || entityId != currentId
          || !world.getEntityManager().isActive(entityId)) return false;
      NativePathIdentity current = world.getMapper(NativePathIdentity.class).get(entityId);
      return current != null && current.token == this;
    }
  }
}
