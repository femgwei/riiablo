"""Read-only pinned 1.10f client armor stat-list snapshot; polling is non-atomic."""
import struct


def stats(win, process, array):
    header = win.read(process, array, 8)
    pointer, count, capacity = struct.unpack("<IHH", header)
    if count > capacity or capacity > 4096 or (count and not pointer):
        raise ValueError("Invalid bounded stat-array descriptor")
    raw = win.read(process, pointer, count * 8) if count else b""
    if win.read(process, array, 8) != header:
        raise ValueError("Stat-array descriptor changed during read")
    result = {}
    for layer, stat, value in struct.iter_unpack("<HHi", raw):
        if layer == 0 and stat in (6, 7, 132, 133, 355):
            result[str(stat)] = value
    return result


def snapshot(win, process, player):
    ex = win.u32(process, player + 0x5C)
    if not ex or not win.u32(process, ex + 0x10) & 0x80000000:
        raise ValueError("Player has no extended stat list")
    flags = win.u32(process, ex + 0x58)
    result = {"state_bit": bool(flags and win.u32(process, flags) & (1 << 14)),
              "aggregate": stats(win, process, ex + 0x48), "lists": []}
    seen = set()
    for offset in (0x3C, 0x40):
        node = win.u32(process, ex + offset)
        chain = set()
        while node:
            if node in chain or len(chain) >= 256:
                raise ValueError("Invalid armor stat-list chain")
            chain.add(node)
            if node not in seen and win.u32(process, node + 0x14) == 14:
                result["lists"].append({"pointer": node, "skill": win.u32(process, node + 0x1C),
                    "level": win.u32(process, node + 0x20), "stats": stats(win, process, node + 0x24)})
            seen.add(node)
            node = win.u32(process, node + 0x2C)
    from arctic_blast_passive import overlay_snapshot
    result["overlays"] = overlay_snapshot(win, process, player)
    return result
