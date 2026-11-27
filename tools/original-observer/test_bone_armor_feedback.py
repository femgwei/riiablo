import struct
import unittest

from bone_armor_feedback import stats
from collector_specs import validate_collector


class FakeMemory:
    def __init__(self, descriptor, payload):
        self.descriptor, self.payload = descriptor, payload

    def read(self, process, address, size):
        if address == 0x1000:
            return self.descriptor
        if address == 0x2000:
            return self.payload[:size]
        raise ValueError("Unexpected read")


class BoneArmorFeedbackTest(unittest.TestCase):
    def test_signed_native_fixed_values_and_layer_zero_are_retained(self):
        raw = b"".join(struct.pack("<HHi", *s) for s in (
            (0, 132, 19632), (0, 133, 53760), (0, 355, 2), (1, 132, 123), (0, 6, -1)))
        memory = FakeMemory(struct.pack("<IHH", 0x2000, 5, 8), raw)
        self.assertEqual({"132": 19632, "133": 53760, "355": 2, "6": -1}, stats(memory, None, 0x1000))

    def test_unbounded_or_inconsistent_descriptors_are_rejected(self):
        for count, capacity in ((9, 8), (5000, 5000)):
            with self.subTest(count=count):
                with self.assertRaises(ValueError):
                    stats(FakeMemory(struct.pack("<IHH", 0x2000, count, capacity), b""), None, 0x1000)

    def test_empty_native_array_is_valid_without_dereferencing_null(self):
        self.assertEqual({}, stats(FakeMemory(struct.pack("<IHH", 0, 0, 0), b""), None, 0x1000))

    def test_preparer_contract_requires_the_reviewed_bone_armor_capture(self):
        scenario = dict(input="d2gfx_callchain", skill=68, level=20,
                        **{"class": 2, "prepare_bone_armor_attacker": True})
        self.assertTrue(str(validate_collector("native_runtime_trace", scenario)).endswith("d2gfx_export_trace.exe"))
        for changed in ({"input": "client_callchain"}, {"skill": 94}, {"level": 1},
                        {"prepare_corpse": True}, {"prepare_bone_armor_attacker": 1}):
            with self.subTest(changed=changed):
                with self.assertRaises(ValueError):
                    validate_collector("native_runtime_trace", {**scenario, **changed})


if __name__ == "__main__":
    unittest.main()
