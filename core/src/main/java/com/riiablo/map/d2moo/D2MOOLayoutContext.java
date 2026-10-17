package com.riiablo.map.d2moo;

import com.riiablo.Files;
import com.riiablo.Riiablo;
import com.riiablo.map.MapResources;
import com.riiablo.mpq.MPQFileHandleResolver;
import java.util.Objects;

/** Resources bound to one map generation; legacy bridges use a thread-scoped view. */
public final class D2MOOLayoutContext {
  private static final ThreadLocal<D2MOOLayoutContext> CURRENT = new ThreadLocal<>();
  private final Files files;
  private final MPQFileHandleResolver mpqs;

  public D2MOOLayoutContext(Files files, MPQFileHandleResolver mpqs) {
    this.files = Objects.requireNonNull(files, "files");
    this.mpqs = mpqs;
  }

  public static D2MOOLayoutContext of(MapResources resources) {
    return resources == null ? fromGlobals() : new D2MOOLayoutContext(resources.files(), resources.mpqs());
  }

  public static D2MOOLayoutContext fromGlobals() {
    return new D2MOOLayoutContext(Riiablo.files, Riiablo.mpqs);
  }

  static Files files() {
    D2MOOLayoutContext context = CURRENT.get();
    return context == null ? Riiablo.files : context.files;
  }

  static MPQFileHandleResolver mpqs() {
    D2MOOLayoutContext context = CURRENT.get();
    return context == null ? Riiablo.mpqs : context.mpqs;
  }

  public Scope activate() {
    D2MOOLayoutContext previous = CURRENT.get();
    CURRENT.set(this);
    return () -> { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); };
  }

  public interface Scope extends AutoCloseable {
    @Override void close();
  }
}
