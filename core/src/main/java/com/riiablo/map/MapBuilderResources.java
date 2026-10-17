package com.riiablo.map;

import com.riiablo.Files;
import com.riiablo.Riiablo;
import com.riiablo.mpq.MPQFileHandleResolver;

/** Data bindings shared by the builders belonging to one map. */
abstract class MapBuilderResources {
  protected final MapResources resources;

  protected MapBuilderResources() { this(null); }
  protected MapBuilderResources(MapResources resources) { this.resources = resources; }
  protected final Files files() { return resources != null ? resources.files() : Riiablo.files; }
  protected final MPQFileHandleResolver mpqs() {
    return resources != null ? resources.mpqs() : Riiablo.mpqs;
  }
}
