"""Named two-skill fixture for same-process batching, not a zero-synergy oracle."""
import hashlib
import importlib.util
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parent
NAME = "CXDruBatch"
SKILLS = (225, 229)


def normalized_hotkeys(data):
    # Original save writer canonicalizes unused FFFFFFFF slots to 0000FFFF.
    # Preserve every assigned skill/button bit; only the two unused encodings match.
    return tuple(0xFFFF if value in (0xFFFF, 0xFFFFFFFF) else value
                 for value in struct.unpack_from("<16I", data, 56))


def prepare(home, output, surface=False, wind=False, storm=False, raven=False, wolf=False,
            raven_level=1, wolf_level=1):
    if type(raven_level) is not int or not 1 <= raven_level <= 20:
        raise ValueError("Raven fixture level must be in 1..20")
    if type(wolf_level) is not int or not 1 <= wolf_level <= 20:
        raise ValueError("Werewolf fixture level must be in 1..20")
    if sum((surface, wind, storm, raven, wolf)) > 1:
        raise ValueError("Select one fixed Druid batch")
    name, skills = ("CXDruRaven", (221,)) if raven else ("CXDruWolf", (223,)) if wolf else ("CXDruState", (250, 249)) if storm else ("CXDruWind", (240, 245)) if wind else ("CXDruSurface", (234, 244)) if surface else (NAME, SKILLS)
    import winreg
    with winreg.OpenKey(winreg.HKEY_CURRENT_USER, r"Software\Blizzard Entertainment\Diablo II",
                        0, winreg.KEY_READ | winreg.KEY_WOW64_32KEY) as key:
        save = Path(winreg.QueryValueEx(key, "Save Path")[0])
    spec = importlib.util.spec_from_file_location("batch_character", ROOT / "test-character.py")
    fixture = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(fixture)
    source = (save / "CXSorc.d2s").read_bytes()
    data = bytearray(fixture.create(source, name, class_id=5, skill_level=0))
    _, offset = fixture.read_stats(data)
    for skill in skills:
        data[offset + 2 + skill - 221] = raven_level if raven else wolf_level if wolf else 1
    struct.pack_into("<2I", data, 56, skills[0], skills[-1])  # F1/F2, right hand.
    struct.pack_into("<4I", data, 120, 0, skills[0], 0, skills[0])
    struct.pack_into("<I", data, 12, fixture.checksum(data))
    target = save / (name + ".d2s")
    if target.exists():
        existing = target.read_bytes()
        _, pos = fixture.read_stats(existing)
        raven_save = (raven and 1 <= existing[pos + 2] <= 20
                      and existing[pos + 3:pos + 32] == bytes(29))
        if (fixture.checksum(existing) != struct.unpack_from("<I", existing, 12)[0]
                or existing[20:36].split(b"\0")[0] != name.encode("ascii")
                or existing[40] != 5 or existing[36] & 0x0C
                or (existing[pos + 2:pos + 32] != data[offset + 2:offset + 32] and not raven_save)
                or normalized_hotkeys(existing) != normalized_hotkeys(data)
                or struct.unpack_from("<I", existing, 171)[0] != 127897359
                or existing[pos + 32:pos + 36] != b"JM\0\0"):
            raise ValueError("Existing " + name + " is not the fixed batch fixture; refusing overwrite")
        # Raven captures must start with no persisted summon from a previous
        # run.  The dedicated CXDruRaven save is regenerated from the clean
        # source fixture after validation; other batches retain their saved
        # progress for same-process continuation.
        if not raven:
            data = bytearray(existing)
        else:
            (output / (name + "-previous.d2s")).write_bytes(existing)
            replacement = target.with_suffix(".d2s.raven.tmp")
            with replacement.open("xb") as stream:
                stream.write(data)
            replacement.replace(target)
    else:
        with target.open("xb") as stream:
            stream.write(data)
    (output / (name + "-input.d2s")).write_bytes(data)
    if target.read_bytes() != data:
        raise ValueError("Installed fixture differs from reported input")
    return dict(name=name, path=str(target), sha256=hashlib.sha256(data).hexdigest(),
                source_sha256=hashlib.sha256(source).hexdigest(), class_id=5,
                hard_points={str(skill): raven_level if raven else wolf_level if wolf else 1 for skill in skills},
                other_hard_points=0, equipment="empty", map_seed=127897359,
                hotkeys={"F1": skills[0], "F2": skills[-1]},
                limitations=(["Only Raven learned; summon activity/AI requires runtime observation"] if raven else
                             ["Only Werewolf learned; private-desktop morph timing requires runtime observation"] if wolf else
                             ["Only Hurricane/Armageddon learned; their duration synergies Cyclone Armor/Eruption remain zero",
                              "Shared-process client observation, not damage parity"] if storm else
                             ["Both skills learned together: native synergy bonuses are active",
                              "Batch proves capture isolation, not parity with isolated zero-synergy fixtures"]))
