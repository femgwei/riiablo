# Native monster population regions

The Travincal log with seed `127897359` contains six monsters populated directly
in RoomEx 30 and seven in RoomEx 31. The room30 spawn locations do not establish
a room31-to-room30 movement or a change to the map layout. Room30 is not an
entirely excluded population area.

The previous Act II–V population pass rolled density and searched for pack
leaders throughout each RoomEx rectangle. Native
`MonsterRegion.cpp::D2GAME_PopulateRoom_6FC67190` instead iterates coordinate
lists, skipping `nIndex == 0`, `bNode` and empty boxes. It computes each density
budget from `pBox[1]`, whose fields are absolute tile edges. Leader selection
in `sub_6FC66260` also requires the selected point's coordinate-list index to
match. The native spawn search keeps ordinary pack members, champion peers and
unique servants in that list's bounds and index.

`NativeMonsterPopulationRegion` now copies those boxes and cell indices while
the native DRLG is still alive, projects them to world subtiles, and supplies
them to the deferred population pass. Each eligible list receives its own
budget and placement search. An empty native list set remains authoritative;
only compatibility rooms without native metadata use a whole-room region.
Arcane room metadata is captured after tile export initializes the lists.

Native exceptions are preserved: DS1 preset group markers do not use the normal
density/warp search, and attached `PartyMin`/`PartyMax` minions use a nearby
search without a coordinate-list restriction (`MonsterSpawn::sub_6FC69C00`).
Warp-distance filtering still applies to random pack leaders, not followers.

The tests cover negative world coordinates, exclusive edges, mismatched indices
inside a bounding box, invalid/node/zero-index lists, metadata lifetime, normal
and elite groups, attached parties, preset groups and Arcane initialization.
Real 1.10f Travincal resources with seeds `127897359`, `1`, and `2` verify pack
placement, nonzero population, unchanged terrain collision and one-time queuing.
For the reported seed, `(1697,-2089)` and `(1698,-2088)` belong to the same
eligible list; `(1700,-2085)` and the waypoint `(1699,-2096)` do not belong to
that list. This constrains initial population, not subsequent AI movement.

This change does not alter map layout, wall collision or pathfinding. It does
change random population budgets and random-number consumption; exact monster
counts and spawn locations can differ after rebuilding a map. Client-side
confirmation of the earlier wall-crossing report remains separate.
