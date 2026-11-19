"""Read-only, bounded client scene samples for the hash-pinned 1.10f observer.

Loaded rooms/units are not a full-level monster census. No target functions are
called and the sample is not atomic with respect to the game's update thread.
"""
import collections
import struct


def u32(raw, offset):
    return struct.unpack_from("<I", raw, offset)[0]


def full_level_unit_census(win, process, act_ptr, level_ptr, level_raw,
                           active_room_pairs=None):
    """Read unit lists from every generated RoomEx/Room1 pair, without calls.

    The ordinary scene snapshot walks the Act's active-room list and therefore
    intentionally excludes rooms that are not currently loaded by D2Client.
    This bounded pass starts at the generated level's RoomEx list instead.  A
    RoomEx may not have a Room1 yet; those rooms are reported separately and
    are not treated as zero-monster rooms.
    """
    active_room_pairs = active_room_pairs or {}
    result = dict(scope="generated RoomEx joined to active Room1 unit lists; read-only",
                  level_pointer=level_ptr, rooms_seen=0, rooms_with_room1=0,
                  room_unit_records=0, unique_units=0, monster_count=0,
                  unit_counts={}, monsters=[], errors=[])
    room_ptr = u32(level_raw, 0x30)
    room_seen = set()
    unit_seen = set()
    counts = collections.Counter()
    while room_ptr:
        if room_ptr in room_seen or len(room_seen) >= 4096:
            result["errors"].append("invalid/unbounded generated RoomEx list")
            break
        room_seen.add(room_ptr)
        try:
            roomex = win.read(process, room_ptr, 0xEC)
            if u32(roomex, 0) != level_ptr:
                result["errors"].append("RoomEx belongs to another level")
                break
            # Room1 -> RoomEx is known at Room1+0x38.  The reverse pointer is
            # not assumed here: use the validated active-room join supplied by
            # scene_snapshot, and report the remaining generated RoomEx nodes
            # as not currently materialized in D2Client.
            room1_ptr = active_room_pairs.get(room_ptr)
            result["rooms_seen"] += 1
            if not room1_ptr:
                room_ptr = u32(roomex, 0xE8)
                continue
            room1 = win.read(process, room1_ptr, 0x80)
            if u32(room1, 0x78) != act_ptr:
                result["errors"].append("Room1 belongs to another act")
                room_ptr = u32(roomex, 0xE8)
                continue
            result["rooms_with_room1"] += 1
            unit_ptr = u32(room1, 0x2C)
            local_seen = set()
            while unit_ptr:
                if unit_ptr in local_seen or len(local_seen) >= 8192:
                    result["errors"].append("invalid/unbounded Room1 unit list")
                    break
                local_seen.add(unit_ptr)
                unit = win.read(process, unit_ptr, 0xEC)
                kind = u32(unit, 0)
                if kind > 5:
                    result["errors"].append("invalid unit type")
                    break
                if unit_ptr not in unit_seen:
                    unit_seen.add(unit_ptr)
                    counts[str(kind)] += 1
                    result["room_unit_records"] += 1
                    item = dict(pointer=unit_ptr, roomex_pointer=room_ptr,
                                room_pointer=room1_ptr, type=kind,
                                class_id=u32(unit, 4), unit_id=u32(unit, 0xC),
                                mode=u32(unit, 0x10), animation=u32(unit, 0x44))
                    if kind == 1:
                        result["monster_count"] += 1
                        result["monsters"].append(item)
                unit_ptr = u32(unit, 0xE8)
            room_ptr = u32(roomex, 0xE8)
        except (OSError, ValueError, struct.error) as error:
            result["errors"].append(str(error))
            break
    result["rooms_without_room1"] = result["rooms_seen"] - result["rooms_with_room1"]
    result["unique_units"] = len(unit_seen)
    result["unit_counts"] = dict(counts)
    return result


def layout_snapshot(win, process, modules, scene):
    """Already-generated level topology; never initializes/loads target rooms."""
    if scene.get("status") != "scene":
        raise ValueError("A validated scene is required")
    signature = bytes.fromhex("8b49208b41088b48048b50088d0c498d0c898d448a44c3")
    if win.read(process, modules["d2common.dll"]["base"] + 0x481B0, len(signature)) != signature:
        raise ValueError("Preset file getter signature mismatch")
    drlg = win.u32(process, scene["level_pointer"])
    raw_drlg = win.read(process, drlg, 0x3F0)
    act_ptr = u32(raw_drlg, 0xC)
    act = win.read(process, act_ptr, 0x20)
    if u32(act, 8) != drlg or act[0] != raw_drlg[0x10]:
        raise ValueError("Inconsistent DRLG/Act linkage")
    result = dict(schema=2, source="original_process_memory", read_only=True,
                  preset_file_source="D2DrlgPresetRoom.pMap.nPickedFile; getter RVA 0x481b0",
                  act=int(act[0]), difficulty=int(raw_drlg[0x3EC]),
                  map_seed=u32(act, 0xC), drlg_start_seed=u32(raw_drlg, 0x1C),
                  drlg_flags=u32(raw_drlg, 0x24),
                  scope="already generated level layouts; preset lists only where initialized",
                  server_game_pointer=u32(raw_drlg, 0x3E8), levels=[])
    ptr, seen = u32(raw_drlg, 0), set()
    while ptr:
        if ptr in seen or len(seen) >= 136:
            raise ValueError("Invalid/unbounded level list")
        seen.add(ptr)
        raw = win.read(process, ptr, 0x230)
        lid = u32(raw, 4)
        if u32(raw, 0) != drlg or not 1 <= lid <= 136:
            raise ValueError("Invalid level header")
        ox, oy, width, height = struct.unpack_from("<iiii", raw, 0x20)
        item = dict(pointer=ptr, level_id=lid, drlg_type=u32(raw, 0xC),
                    origin_tiles=[ox, oy], size_tiles=[width, height],
                    seed=[u32(raw, 0x14), u32(raw, 0x18)], initial_seed=u32(raw, 0x1C),
                    reported_room_count=u32(raw, 0x34), rooms=[])
        rp, room_seen = u32(raw, 0x30), set()
        while rp:
            if rp in room_seen or len(room_seen) >= 4096:
                raise ValueError("Invalid/unbounded generated room list")
            room_seen.add(rp)
            rr = win.read(process, rp, 0xEC)
            if u32(rr, 0) != ptr:
                raise ValueError("Generated room belongs to another level")
            x, y, rw, rh = struct.unpack_from("<iiii", rr, 4)
            if not all(-100000 <= v <= 100000 for v in (x, y)) or not all(0 < v < 4096 for v in (rw, rh)):
                raise ValueError("Invalid generated room geometry")
            entry = dict(pointer=rp, ordinal=len(item["rooms"]),
                         local_subtiles=[(x-ox)*5, (y-oy)*5, rw*5, rh*5],
                         room_type=u32(rr, 0x1C), flags=u32(rr, 0x14),
                         tile_grid_initialized=bool(u32(rr, 0xA8)),
                         room1_pointer=u32(rr, 0xE4), active=bool(u32(rr, 0xE4)),
                         seed=[u32(rr, 0xD4), u32(rr, 0xD8)], initial_seed=u32(rr, 0xDC),
                         preset=None, preset_map_available=False, adjacency=[], preset_units=[])
            if entry["room_type"] == 2 and u32(rr, 0x20):
                pr = win.read(process, u32(rr, 0x20), 12)
                entry["preset_room_unused_slot"] = u32(pr, 4)
                entry["preset"] = [u32(pr, 0), -1]
                if u32(pr, 8):
                    preset_map = win.read(process, u32(pr, 8), 8)
                    if u32(preset_map, 0) != u32(pr, 0):
                        raise ValueError("Preset map/room definitions differ")
                    entry["preset"] = list(struct.unpack("<ii", preset_map))
                    entry["preset_map_available"] = True
            near_count, near_array = u32(rr, 0xC4), u32(rr, 0xC0)
            if near_count > 256:
                raise ValueError("Invalid adjacency count")
            if near_count:
                neighbors = struct.unpack("<" + "I" * near_count, win.read(process, near_array, 4 * near_count))
                for neighbor in neighbors:
                    nr = win.read(process, neighbor, 0x14)
                    nl = u32(nr, 0)
                    nlevel = win.read(process, nl, 0x30)
                    nox, noy = struct.unpack_from("<ii", nlevel, 0x20)
                    nx, ny, nw, nh = struct.unpack_from("<iiii", nr, 4)
                    entry["adjacency"].append(dict(pointer=neighbor, level_id=u32(nlevel, 4),
                        local_subtiles=[(nx-nox)*5, (ny-noy)*5, nw*5, nh*5]))
            pp, preset_seen = u32(rr, 0xCC), set()
            while pp:
                if pp in preset_seen or len(preset_seen) >= 4096:
                    raise ValueError("Invalid/unbounded preset unit list")
                preset_seen.add(pp)
                pr = win.read(process, pp, 0x20)
                kind, index, mode, px, py, spawned = struct.unpack_from("<iiiiii", pr)
                if not 0 <= kind <= 5:
                    raise ValueError("Invalid preset unit kind")
                entry["preset_units"].append(dict(type=kind, class_id=index, mode=mode,
                    local_subtiles=[(x-ox)*5+px, (y-oy)*5+py], spawned=bool(spawned)))
                pp = u32(pr, 0x1C)
            item["rooms"].append(entry)
            rp = u32(rr, 0xE8)
        result["levels"].append(item)
        ptr = u32(raw, 0x22C)
    result["levels"].sort(key=lambda level: level["level_id"])
    return result


def scene_snapshot(win, process, modules, player):
    if player.get("status") != "player":
        return {"status": "no_player"}
    common = modules["d2common.dll"]["base"]
    signatures = {
        0x79C10: bytes.fromhex("8b4424048b401cc20400"),  # PATH_GetRoom
        0x4D0C0: bytes.fromhex("8b4138c3"),  # DUNGEON_GetRoomExFromRoom
        0x37A10: bytes.fromhex("8b018b4004c3"),  # DRLGROOM_GetLevelId
    }
    for rva, expected in signatures.items():
        if win.read(process, common + rva, len(expected)) != expected:
            raise ValueError("Scene getter signature mismatch at " + hex(rva))
    unit = win.read(process, player["pointer"], 0xEC)
    path_ptr, act_ptr = u32(unit, 0x2C), u32(unit, 0x1C)
    if not path_ptr or not act_ptr:
        return {"status": "incomplete_player"}
    path = win.read(process, path_ptr, 0x34)
    if u32(path, 0x30) != player["pointer"]:
        raise ValueError("Path does not point back to selected player")
    room_ptr = u32(path, 0x1C)
    if not room_ptr:
        return {"status": "no_room"}
    room = win.read(process, room_ptr, 0x80)
    roomex_ptr = u32(room, 0x38)
    roomex = win.read(process, roomex_ptr, 0xEC)
    level_ptr = u32(roomex, 0)
    level = win.read(process, level_ptr, 0x38)
    level_id = u32(level, 4)
    if not 1 <= level_id <= 136:
        raise ValueError("Invalid level id")
    x, y, width, height = struct.unpack_from("<iiii", room)
    px, py = struct.unpack_from("<H", path, 2)[0], struct.unpack_from("<H", path, 6)[0]
    if not (0 < width < 4096 and 0 < height < 4096 and x <= px < x + width and y <= py < y + height):
        raise ValueError("Player is outside sampled room bounds; retry on next sample")
    act = win.read(process, act_ptr, 0x20)
    if u32(room, 0x78) != act_ptr or act[0] != unit[0x18]:
        raise ValueError("Inconsistent act/room/player linkage")
    result = dict(status="scene", level_id=level_id, room_pointer=room_ptr,
                  roomex_pointer=roomex_ptr, level_pointer=level_ptr,
                  room_bounds=dict(x=x, y=y, width=width, height=height),
                  player_inside_room=True, path_back_reference=True,
                  map_seed=u32(act, 0xC), client_act=bool(u32(act, 0x1C)),
                  scope="client loaded rooms and units; excludes unloaded monsters and items",
                  atomic=False)
    rooms, seen, unit_seen, units = [], set(), set(), []
    active_room_pairs = {}
    ptr = u32(act, 4)
    while ptr:
        if ptr in seen or len(seen) >= 1024:
            raise ValueError("Invalid/unbounded active room list")
        seen.add(ptr)
        raw = win.read(process, ptr, 0x80)
        if u32(raw, 0x78) != act_ptr:
            raise ValueError("Room list crosses acts")
        ex = win.u32(process, ptr + 0x38)
        lp = win.u32(process, ex)
        lid = win.u32(process, lp + 4)
        rooms.append(dict(pointer=ptr, level_id=lid, bounds=list(struct.unpack_from("<iiii", raw))))
        uptr = u32(raw, 0x2C)
        local_seen = set()
        while uptr:
            if uptr in local_seen or len(unit_seen) >= 8192:
                raise ValueError("Invalid/unbounded unit list")
            local_seen.add(uptr)
            uraw = win.read(process, uptr, 0xEC)
            kind = u32(uraw, 0)
            if kind > 5:
                raise ValueError("Invalid unit type")
            if uptr not in unit_seen:
                unit_seen.add(uptr)
                item = dict(pointer=uptr, level_id=lid, type=kind,
                            class_id=u32(uraw, 4), unit_id=u32(uraw, 0xC),
                            mode=u32(uraw, 0x10), animation=u32(uraw, 0x44))
                # PLAYER_PATH is shared by players and monsters.  Preserve a
                # position only when the path's back-reference is valid; a
                # partially loaded unit remains observable without making the
                # whole scene sample fail.
                path_ptr = u32(uraw, 0x2C)
                if path_ptr:
                    try:
                        path = win.read(process, path_ptr, 0x34)
                        if u32(path, 0x30) == uptr:
                            item["position"] = {
                                "x": struct.unpack_from("<H", path, 2)[0],
                                "y": struct.unpack_from("<H", path, 6)[0],
                            }
                    except (OSError, ValueError):
                        pass
                units.append(item)
            uptr = u32(uraw, 0xE8)
        ptr = u32(raw, 0x7C)
        active_room_pairs[ex] = rooms[-1]["pointer"]
    try:
        result["full_level_census"] = full_level_unit_census(
            win, process, act_ptr, level_ptr, level, active_room_pairs)
    except (OSError, ValueError, struct.error) as error:
        result["full_level_census"] = {
            "scope": "generated RoomEx joined to active Room1 unit lists; read-only",
            "status": "error", "errors": [str(error)]
        }
    result.update(loaded_rooms=rooms, loaded_units=units,
                  loaded_unit_counts=dict(collections.Counter(str(u["type"]) for u in units)),
                  player_in_loaded_unit_list=player["pointer"] in unit_seen)
    return result
