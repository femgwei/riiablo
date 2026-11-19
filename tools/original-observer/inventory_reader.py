"""Read-only bounded 1.10f inventory inspection; caller validates module hashes."""


def inventory_snapshot(win, process, player):
    owner = player['pointer']
    inv = win.u32(process, owner + 0x60)
    if not inv:
        raise ValueError('player inventory pointer absent')
    if win.u32(process, inv) != 0x01020304 or win.u32(process, inv + 8) != owner:
        raise ValueError('inventory signature/owner mismatch')
    count = win.u32(process, inv + 0x28)
    if count > 256:
        raise ValueError('inventory count exceeds bound')
    items, seen = [], set()
    ptr = win.u32(process, inv + 0x0C)
    while ptr:
        if ptr in seen or len(seen) >= 256 or win.u32(process, ptr) != 4:
            raise ValueError('invalid inventory item chain')
        seen.add(ptr)
        data = win.u32(process, ptr + 0x14)
        if not data or win.u32(process, data + 0x5C) != inv:
            raise ValueError('item parent inventory mismatch')
        location = win.read(process, data + 0x44, 4)
        items.append(dict(pointer=ptr, class_id=win.u32(process, ptr + 4),
                          unit_id=win.u32(process, ptr + 0xC),
                          mode=win.u32(process, ptr + 0x10), body_location=location[0],
                          inventory_page=location[1], cell=location[3]))
        ptr = win.u32(process, data + 0x64)
    if len(items) != count:
        raise ValueError('inventory count changed or chain incomplete')
    return dict(status='read', pointer=inv, count=count, items=items,
                cursor_item=win.u32(process, inv + 0x20),
                scope='owned item chain only; item codes and usable quantities not yet resolved')
