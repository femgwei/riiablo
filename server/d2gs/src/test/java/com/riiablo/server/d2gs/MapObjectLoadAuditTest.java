package com.riiablo.server.d2gs;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.map.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class MapObjectLoadAuditTest {
  @Test public void rejectsPendingAndMissingEntitiesInsteadOfAcceptingExportCount() {
    World world = new World(new WorldConfigurationBuilder().build());
    try {
      Map.Zone zone = new Map.Zone();
      zone.level = new com.riiablo.codec.excel.Levels.Entry();
      zone.level.Id = 2;
      Map.NativeObject pending = new Map.NativeObject(1, 0, 10, 10, false, false);
      Map.NativeObject missing = new Map.NativeObject(5, 0, 12, 10, false, false);
      missing.creationStatus = "created";
      missing.entityId = world.create();
      zone.getNativeObjects().add(pending);
      zone.getNativeObjects().add(missing);
      java.util.List<String> evidence = MapObjectLoadAudit.inspect(world, zone);
      assertTrue(evidence.get(0).contains("pending_after_level_materialization"));
      assertTrue(evidence.get(1).contains("missing_entity"));
      assertTrue(evidence.get(2).contains("failures=2 result=FAIL"));
    } finally { world.dispose(); }
  }
}
