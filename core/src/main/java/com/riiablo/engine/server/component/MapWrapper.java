package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;
import com.riiablo.map.Map;

@Transient
@PooledWeaver
public class MapWrapper extends Component {
  public Map      map;
  public Map.Zone zone;
  /** Native RoomEx id containing the entity, or -1 when outside exported rooms. */
  public int roomId = -1;
  /** Test/native-fixture override; normal entities remain room-tracked. */
  public boolean roomIdPinned;

  public MapWrapper set(Map map, Map.Zone zone) {
    this.map = map;
    this.zone = zone;
    this.roomId = -1;
    this.roomIdPinned = false;
    return this;
  }
}
