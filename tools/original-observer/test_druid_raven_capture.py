"""Offline regression tests for Raven evidence acquisition (no game launched)."""
import importlib.util
from pathlib import Path
import shutil
import struct
import tempfile
import unittest
from unittest.mock import patch

import prepare_druid_batch_fixture as saves
from druid_raven_passive import scan_units, target_feedback

ROOT = Path(__file__).resolve().parent


class RavenCaptureTest(unittest.TestCase):
    def test_client_state_bit_does_not_imply_a_full_curse_stat_list(self):
        class Memory:
            def u32(self, process, address):
                return {0x105C: 0x2000, 0x2010: 0x80000000,
                        0x2058: 0x3000, 0x3000: 1 << 23}.get(address, 0)
        result = target_feedback(Memory(), None, 0x1000)
        self.assertTrue(result["dimvision"])
        self.assertFalse(result["dimvision_list"])
        self.assertEqual([], result["overlays"])

    def test_target_curse_list_preserves_skill_level_and_expiry(self):
        class Memory:
            def u32(self, process, address):
                return {0x105C: 0x2000, 0x2010: 0x80000000,
                        0x2058: 0x3000, 0x3000: 1 << 23, 0x203C: 0x4000,
                        0x4014: 23, 0x4018: 1249, 0x401C: 71, 0x4020: 8}.get(address, 0)
        result = target_feedback(Memory(), None, 0x1000)
        self.assertTrue(result["dimvision_list"])
        self.assertEqual((1249, 71, 8), (result["dimvision_expiry"],
                         result["dimvision_skill"], result["dimvision_level"]))

    def test_existing_fixture_really_installs_the_requested_level_and_preserves_backup(self):
        import winreg
        source = ROOT.parent.parent / "core/src/test/resources/native/poison-javelin-input-fixture.d2s"
        spec = importlib.util.spec_from_file_location("fixture", ROOT / "test-character.py")
        fixture = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(fixture)
        with tempfile.TemporaryDirectory() as tmp:
            save = Path(tmp)
            shutil.copy2(source, save / "CXSorc.d2s")
            previous = None
            with patch.object(winreg, "OpenKey") as opened, patch.object(winreg, "QueryValueEx", return_value=(str(save), 1)):
                for level in (1, 5, 8, 20, 1):
                    output = save / ("run-%d-%d" % (level, len(list(save.glob("run-*")))))
                    output.mkdir()
                    result = saves.prepare(save, output, raven=True, raven_level=level)
                    installed = (save / "CXDruRaven.d2s").read_bytes()
                    _, offset = fixture.read_stats(installed)
                    self.assertEqual(level, installed[offset + 2])
                    self.assertEqual(result["hard_points"]["221"], level)
                    self.assertEqual(installed, (output / "CXDruRaven-input.d2s").read_bytes())
                    self.assertEqual(fixture.checksum(installed), struct.unpack_from("<I", installed, 12)[0])
                    if previous is not None:
                        self.assertEqual(previous, (output / "CXDruRaven-previous.d2s").read_bytes())
                    previous = installed

    def test_target_in_room_list_is_observed_even_when_hash_tables_are_empty(self):
        unit, path = 0x123400, 0x234500
        raw = [0] * 59
        raw[0], raw[1], raw[3], raw[4], raw[11] = 1, 0, 17, 1, path
        raw[19] = 0x100F0  # action byte above unsigned 16-bit animation speed
        data = bytearray(0x9C)
        struct.pack_into("<2I", data, 0, 4524 << 16, 5644 << 16)
        struct.pack_into("<I", data, 0x30, unit)

        class Memory:
            def read(self, process, address, size):
                if address == unit:
                    return struct.pack("<59I", *raw)
                if address == path:
                    return data
                return bytes(size)

        result = scan_units(Memory(), None, 0x60000000,
            [dict(pointer=unit, type=1, class_id=0, unit_id=17)])
        self.assertIn((1, 17), result)
        self.assertEqual(240, result[(1, 17)]["animation_rate"])
        self.assertEqual(4524 << 16, result[(1, 17)]["x_fixed"])


if __name__ == "__main__":
    unittest.main()
