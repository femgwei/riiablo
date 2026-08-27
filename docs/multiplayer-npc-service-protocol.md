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

`OPEN`, `BUY`, and `SELL` are implemented as the first protocol slice. BUY
removes an item from the game-scoped NPC stock only after server-side gold and
inventory placement succeeds. SELL validates the owned inventory slot and
updates the authenticated player's wallet. Unsupported operations return
`OPERATION_NOT_IMPLEMENTED` instead of falling back to unsafe client-side
mutation.

Network clients open `VendorPanel` from the server stock snapshot and keep one
pending mutation at a time. Client inventory and the carried/bank gold split
are changed only after a successful result; rejection rebuilds the panel from
the authoritative snapshot. Local single-player vendors keep their existing
direct transaction path.

## Idempotency and revisions

The server must cache the last completed request ids per connection/session.
Repeating a completed request returns its original result. Vendor and gamble
stocks receive monotonically increasing revisions; stale BUY requests return
`STALE_STOCK` with a refreshed snapshot. SELL and REPAIR additionally validate
that the referenced item still belongs to the authenticated player.

## Rollout order

1. Server-owned OPEN stock snapshots.
2. BUY and SELL atomic mutations. (implemented)
3. REPAIR_ITEM and REPAIR_ALL.
4. GAMBLE inventory refresh and purchase.
5. HIRE and RESURRECT.
6. Request-result idempotency cache and reconnect/session recovery.
