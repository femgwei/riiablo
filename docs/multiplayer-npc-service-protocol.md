# Multiplayer NPC service protocol

The D2GS server is authoritative for NPC services. A client sends only intent;
it never supplies its player id, price, gold balance, generated quality, or an
authoritative item payload.

## Wire messages

`NpcServiceRequest` contains a client request id, NPC entity id, service,
operation, item reference, and the stock revision last observed by the client.
The connection-to-player mapping is the only source of the player entity id.

`NpcServiceResult` returns the request id, success/reason, authoritative gold,
stock revision, an optional resulting item, and an optional complete stock
snapshot. `NpcServiceStock` contains the server item id, serialized item data,
and server-calculated price.

## Validation order

1. Resolve an authenticated player from the connection.
2. Resolve a live NPC entity and its native NPC definition.
3. Enforce interaction distance.
4. Check that the NPC offers the requested service and operation.
5. Require the current stock revision for stock-changing operations.
6. Resolve inventory ownership, price, gold, space, durability, and mercenary
   state on the server.
7. Apply one atomic mutation and return the new authoritative snapshot.

`OPEN`, `BUY`, `SELL`, `REPAIR_ITEM`, and `REPAIR_ALL` are implemented. BUY
removes an item from the game-scoped NPC stock only after server-side gold and
inventory placement succeeds. SELL validates the owned inventory slot and
updates the authenticated player's wallet. Unsupported operations return
`OPERATION_NOT_IMPLEMENTED` instead of falling back to unsafe client-side
mutation.

Repair validates the exact owned item id/index pair and applies wallet and
durability changes atomically. `REPAIR_ALL` is all-or-nothing. Gamble pages are
private to each authenticated player, refresh on each new `OPEN`, and use the
native gambling price rather than the ordinary vendor buy price.

Network clients open `VendorPanel` from the server stock snapshot and keep one
pending mutation at a time. Client inventory and the carried/bank gold split
are changed only after a successful result; rejection rebuilds the panel from
the authoritative snapshot. Local single-player vendors keep their existing
direct transaction path.

## Idempotency and revisions

The server caches up to 128 completed request ids per connection using LRU
eviction. Repeating an id with the exact same intent returns the original raw
result without executing the operation again. Reusing an id with different
fields returns `REQUEST_ID_REUSED`, and disconnect clears that connection's
cache. Vendor and gamble
stocks receive monotonically increasing revisions; stale BUY requests return
`STALE_STOCK` with a refreshed snapshot. SELL and REPAIR additionally validate
that the referenced item still belongs to the authenticated player.

## Rollout order

1. Server-owned OPEN stock snapshots.
2. BUY and SELL atomic mutations. (implemented)
3. REPAIR_ITEM and REPAIR_ALL. (implemented)
4. GAMBLE inventory refresh and purchase. (implemented)
5. HIRE candidate selection and RESURRECT (implemented; live integration verification pending).
6. Request-result idempotency cache (implemented) and reconnect/session recovery.

## Mercenary selection

`HIRE/OPEN` returns `NpcHirelingOffer[]`: stable roster index/seed, native
Hireling Id, four-class presentation type, name, level, life, defense, cost and
HireDesc code. Opening does not charge or create a mercenary. The roster is
private to a player/NPC and expires after ten minutes.

`HIRE/HIRE` reuses `itemIndex` for the chosen roster index and `itemId` for its
seed. `stockRevision` carries the unsigned previous mercenary seed confirmed
by the player (zero when none exists). D2GS validates the exact offer and
replacement consent; it computes cost and checks carried plus bank gold.
Entity reservation/payment failure retains the old mercenary. Successful
replacement discards the old equipment after an explicit client warning.

HIRE and RESURRECT results include `NpcMercenarySnapshot` and the authoritative
wallet. Entity snapshots append `MercenaryP`, including server owner id, native
Hireling Id, class, level, name, seed, experience and flags. The client maps
the owner id and shares the local owner's mercenary attributes with the HUD.
Both client and server must be upgraded for the new selection flow.

See [implementation and verification boundaries](mercenary-hire-ui.md).
