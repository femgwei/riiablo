"""Finite collector contracts. Never silently ignore requested scenarios."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent
COLLECTORS = {
    "native_ai_batch": ("d2game_ai_batch", {"class_id": 5}),
    "portal_scroll_probe": ("portal_scroll_probe", {}),
    "fallen_death": ("d2game_fallen_death", {"profile": "same-room-native-death"}),
    "native_cast": ("d2game_cast", {
        "class": 1, "skill": 36, "level": 1, "target_class": 0,
        "target_fire_resist": 0, "target_magic_damage_reduction": 0,
    }),
    "barbarian_formulas": ("d2game_barbarian", {
        "skills": list(range(126, 156)), "levels": list(range(1, 41)),
        "profile": "zero-barbarian-hardpoints", "owner_class": 1,
    }),
    "barbarian_bash": ("d2game_barbarian_cast", {
        "class": 4, "skill": 126, "level": 1, "target_class": 0,
        "target_fire_resist": 0, "target_magic_damage_reduction": 0,
    }),
    "barbarian_bash_target": ("d2game_barbarian_target", {
        "class": 4, "skill": 126, "level": 1, "target_class": 0,
        "target_fire_resist": 0, "target_magic_damage_reduction": 0,
    }),
    "native_runtime_trace": ("d2game_runtime_trace", {
        "scope": "update-target-damage", "seconds": 30,
    }),
    "native_net_trace": ("d2net_trace", {
        "scope": "d2net-outbound-call", "seconds": 20,
    }),
    "native_socket_trace": ("d2socket_trace", {
        "scope": "winsock-send-call", "seconds": 20,
    }),
    "native_server_population": ("d2game_server_population", {}),
    "barbarian_whirlwind": ("d2game_whirlwind", {
        "class": 4, "skill": 151, "level": 1, "target_class": 0,
        "profile": "single-hand-axe-stationary-target",
    }),
    # Same authoritative collector as barbarian_whirlwind, with a concurrent
    # read-only D2Client sampler enabled by process-session.
    "barbarian_whirlwind_animation": ("d2game_whirlwind", {
        "class": 4, "skill": 151, "level": 1, "target_class": 0,
        "profile": "single-hand-axe-stationary-target",
    }),
    "room_census_walk": ("room_census_walk", {
        "level_id": 42, "seconds": 900,
    }),
    "waypoint_open": ("waypoint-open", {
        "level_id": 40, "x": 5069, "y": 5084, "class_id": 156,
    }),
    "waypoint_select": ("waypoint-select", {
        "level_id": 40, "destination_level": 3, "act": 1,
        "tab_x": 35, "tab_y": 16, "row_x": 150, "row_y": 112,
    }),
    "layout_snapshot": ("layout-snapshot", {"level_id": 3}),
}


def validate_collector(name, scenario):
    if name not in COLLECTORS:
        raise ValueError("collector is not allow-listed: " + str(name))
    executable, expected = COLLECTORS[name]
    if name == "native_ai_batch":
        if set(scenario) - {"class_id", "distance", "profile"} or type(scenario.get("class_id", 5)) is not int or scenario.get("class_id", 5) not in (5, 170, 19, 58, 53):
            raise ValueError("native_ai_batch supports only the five batch-one monster classes")
        if "distance" in scenario and (type(scenario["distance"]) is not int or scenario["distance"] != 18 or scenario.get("class_id", 5) != 170):
            raise ValueError("explicit distance supports only skeleton archer at 18 subtiles")
        if "profile" in scenario and (scenario["profile"] not in ("shaman-hydra", "shaman-hydra-wall", "shaman-golem", "shaman-golem-wall")
                or scenario.get("class_id") != 58 or "distance" in scenario):
            raise ValueError("summon profile supports only the fixed shaman Hydra fixture")
        return ROOT / "native-harness" / "build" / "d2game_ai_batch.exe"
    if name == "portal_scroll_probe":
        if scenario != {}:
            raise ValueError("portal probe accepts only the fixed CXPortal fixture")
        return ROOT / "inventory_reader.py"
    if not isinstance(scenario, dict):
        raise ValueError("scenario must be an object")
    if name == "fallen_death":
        if set(scenario) - {"profile"} or scenario.get("profile", "same-room-native-death") not in (
                "same-room-native-death", "distance-14", "distance-15", "ring-4", "ring-5", "adjacent-room", "recovery", "recovery-reengage", "attack-direct", "attack-repeat", "attack-natural"):
            raise ValueError("unsupported controlled Fallen death profile")
        return ROOT / "native-harness" / "build" / "d2game_fallen_death.exe"
    # Empty scenario means precisely the collector's fixed documented fixture.
    if name == "native_cast" and scenario == {"class": 4, "skill": 126, "level": 1}:
        return ROOT / "native-harness" / "build" / "d2game_barbarian_cast.exe"
    if name == "barbarian_bash_target":
        if scenario in ({}, expected):
            return ROOT / "native-harness" / "build" / "d2game_barbarian_target.exe"
        allowed = {"class", "skill", "level", "target_class",
                   "target_fire_resist", "target_magic_damage_reduction"}
        if set(scenario).issubset(allowed):
            if scenario.get("class", 4) != 4 or scenario.get("skill", 126) != 126:
                raise ValueError("barbarian_bash_target is fixed to class=4 skill=126")
            level = scenario.get("level", 1)
            if isinstance(level, bool) or not isinstance(level, int) or not 1 <= level <= 40:
                raise ValueError("barbarian_bash_target level must be an integer in 1..40")
            for key, value in expected.items():
                if key in scenario and key not in {"level"} and scenario[key] != value:
                    raise ValueError("unsupported Bash scenario field: " + key)
            return ROOT / "native-harness" / "build" / "d2game_barbarian_target.exe"
    if name == "native_runtime_trace" and isinstance(scenario, dict):
        allowed = {"scope", "seconds", "input", "skill", "level", "class", "render_state", "prepare_corpse", "target_corpse", "prepare_bone_armor_attacker"}
        if not set(scenario).issubset(allowed):
            raise ValueError("unsupported runtime trace scenario field")
        if scenario.get("scope", expected["scope"]) != expected["scope"]:
            raise ValueError("runtime trace scope is fixed")
        seconds = scenario.get("seconds", expected["seconds"])
        if isinstance(seconds, bool) or not isinstance(seconds, int) or not 1 <= seconds <= 30:
            raise ValueError("runtime trace seconds must be an integer in 1..30")
        if scenario.get("input") not in (None, "bash", "whirlwind", "encounter", "client_callchain", "d2gfx_callchain"):
            raise ValueError("runtime trace input must be bash, whirlwind, encounter, client_callchain or d2gfx_callchain")
        if "prepare_bone_armor_attacker" in scenario and (
                scenario.get("input") != "d2gfx_callchain" or scenario["prepare_bone_armor_attacker"] is not True
                or scenario.get("class") != 2 or scenario.get("skill") != 68
                or scenario.get("level") != 20 or scenario.get("prepare_corpse") or scenario.get("target_corpse")):
            raise ValueError("Bone Armor attacker requires the fixed class2/skill68/level20 D2Gfx fixture")
        if scenario.get("input") == "client_callchain":
            return ROOT / "native-harness" / "build" / "d2client_animation_trace.exe"
        if scenario.get("input") == "d2gfx_callchain":
            if scenario.get("render_state") not in (None, "visible_unfocused", "minimized", "offscreen"):
                raise ValueError("unsupported D2Gfx render_state")
            return ROOT / "native-harness" / "build" / "d2gfx_export_trace.exe"
        if scenario.get("input") == "whirlwind":
            if scenario.get("skill", 151) != 151 or scenario.get("level", 1) != 1 or scenario.get("class", 4) != 4:
                raise ValueError("whirlwind input is fixed to class=4 skill=151 level=1")
        return ROOT / "native-harness" / "build" / (executable + ".exe")
    if name == "native_net_trace" and isinstance(scenario, dict):
        allowed = {"scope", "seconds", "input"}
        if not set(scenario).issubset(allowed):
            raise ValueError("unsupported D2Net trace scenario field")
        if scenario.get("scope", expected["scope"]) != expected["scope"]:
            raise ValueError("D2Net trace scope is fixed")
        seconds = scenario.get("seconds", expected["seconds"])
        if isinstance(seconds, bool) or not isinstance(seconds, int) or not 1 <= seconds <= 60:
            raise ValueError("D2Net trace seconds must be an integer in 1..60")
        if scenario.get("input") not in (None, "move", "move-burst"):
            raise ValueError("D2Net trace input must be move, move-burst or omitted")
        return ROOT / "native-harness" / "build" / "d2net_trace.exe"
    if name == "native_socket_trace" and isinstance(scenario, dict):
        allowed = {"scope", "seconds", "input"}
        if not set(scenario).issubset(allowed):
            raise ValueError("unsupported Winsock trace scenario field")
        if scenario.get("scope", expected["scope"]) != expected["scope"]:
            raise ValueError("Winsock trace scope is fixed")
        seconds = scenario.get("seconds", expected["seconds"])
        if isinstance(seconds, bool) or not isinstance(seconds, int) or not 1 <= seconds <= 60:
            raise ValueError("Winsock trace seconds must be an integer in 1..60")
        if scenario.get("input") not in (None, "move", "move-burst"):
            raise ValueError("Winsock trace input must be move or move-burst")
        return ROOT / "native-harness" / "build" / "d2socket_trace.exe"
    if name == "native_server_population" and isinstance(scenario, dict):
        if scenario:
            raise ValueError("native_server_population has no scenario fields")
        # Use a fresh filename for the helper. Endpoint protection on this
        # workstation quarantines the original debugger name after launch;
        # the binary and source remain hash-pinned by the process session.
        return ROOT / "native-harness" / "build" / "d2game_server_population2.exe"
    if name == "room_census_walk" and isinstance(scenario, dict):
        allowed = {"level_id", "seconds", "navigation_plan", "resume_census"}
        if not set(scenario).issubset(allowed):
            raise ValueError("unsupported room census scenario field")
        level_id = scenario.get("level_id", expected["level_id"])
        if isinstance(level_id, bool) or not isinstance(level_id, int) or not 1 <= level_id <= 136:
            raise ValueError("room census level_id must be an integer in 1..136")
        seconds = scenario.get("seconds", expected["seconds"])
        if isinstance(seconds, bool) or not isinstance(seconds, int) or not 60 <= seconds <= 1200:
            raise ValueError("room census seconds must be an integer in 60..1200")
        for field in ("navigation_plan", "resume_census"):
            if field not in scenario:
                continue
            name = scenario[field]
            if (not isinstance(name, str) or not name or len(name) > 80
                    or any(ch not in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_" for ch in name)
                    or not (ROOT / "plans" / (name + ".json")).is_file()):
                raise ValueError(field + " must name an existing repository plan")
        return ROOT / "room_census_walk.py"
    if name == "waypoint_open" and isinstance(scenario, dict):
        allowed = {"level_id", "x", "y", "class_id", "nav_offset_y"}
        if not set(scenario).issubset(allowed):
            raise ValueError("unsupported waypoint_open scenario field")
        level_id = scenario.get("level_id", expected["level_id"])
        if level_id not in (1, 40):
            raise ValueError("waypoint_open currently supports Rogue Encampment level 1 or Lut Gholein level 40")
        if level_id == 40:
            if scenario.get("x", expected["x"]) != expected["x"] or scenario.get("y", expected["y"]) != expected["y"]:
                raise ValueError("level 40 waypoint_open is pinned to the observed coordinate")
            if scenario.get("class_id", expected["class_id"]) != 156:
                raise ValueError("level 40 waypoint_open requires class_id 156")
        else:
            if scenario.get("class_id", 119) != 119:
                raise ValueError("level 1 waypoint_open requires class_id 119")
            for key in ("x", "y"):
                if key in scenario and (isinstance(scenario[key], bool) or not isinstance(scenario[key], int)):
                    raise ValueError("level 1 waypoint coordinates must be integers")
        nav_offset_y = scenario.get("nav_offset_y", 12)
        if isinstance(nav_offset_y, bool) or not isinstance(nav_offset_y, int) or not -64 <= nav_offset_y <= 64:
            raise ValueError("waypoint_open nav_offset_y must be an integer in -64..64")
        return ROOT / "waypoint-open.py"
    if name == "waypoint_select" and isinstance(scenario, dict):
        allowed = {"level_id", "destination_level", "act", "tab_x", "tab_y", "row_x", "row_y"}
        if not set(scenario).issubset(allowed):
            raise ValueError("unsupported waypoint_select scenario field")
        source_level = scenario.get("level_id", expected["level_id"])
        if source_level not in (1, 40):
            raise ValueError("waypoint_select currently supports source level 1 or 40")
        destination_level = scenario.get("destination_level", expected["destination_level"])
        if isinstance(destination_level, bool) or not isinstance(destination_level, int) or not 1 <= destination_level <= 136:
            raise ValueError("waypoint_select destination_level must be an integer in 1..136")
        act = scenario.get("act", expected["act"])
        if isinstance(act, bool) or not isinstance(act, int) or not 1 <= act <= 5:
            raise ValueError("waypoint_select act must be an integer in 1..5")
        for key in ("tab_x", "tab_y", "row_x", "row_y"):
            value = scenario.get(key, expected[key])
            if isinstance(value, bool) or not isinstance(value, int) or not 0 <= value <= 799:
                raise ValueError("waypoint_select %s must be a client pixel coordinate" % key)
        return ROOT / "waypoint-open.py"
    if name == "layout_snapshot" and isinstance(scenario, dict):
        allowed = {"level_id"}
        if not set(scenario).issubset(allowed):
            raise ValueError("unsupported layout_snapshot scenario field")
        level_id = scenario.get("level_id", expected["level_id"])
        if isinstance(level_id, bool) or not isinstance(level_id, int) or not 1 <= level_id <= 136:
            raise ValueError("layout_snapshot level_id must be an integer in 1..136")
        return ROOT / "waypoint-open.py"
    if scenario and scenario != expected:
        raise ValueError("unsupported scenario for " + name + "; this collector has a fixed contract")
    return ROOT / "native-harness" / "build" / (executable + ".exe")
