"""Offline gates preventing unrelated rooms and ID collisions in censuses."""
import unittest

from room_census import level_unit_records


class LevelUnitRecordsTests(unittest.TestCase):
    def unit(self, kind=1, uid=7, pointer=100, level=3):
        return dict(type=kind, unit_id=uid, pointer=pointer, level_id=level)

    def records(self, *units):
        return level_unit_records(dict(loaded_units=list(units)), 3)

    def test_excludes_other_loaded_levels(self):
        selected = self.unit()
        self.assertEqual(list(self.records(selected, self.unit(level=1)).values()), [selected])

    def test_same_id_across_types_is_not_collapsed(self):
        records = self.records(self.unit(), self.unit(kind=2, pointer=200))
        self.assertEqual(len(records), 2)

    def test_distinct_pointers_with_same_guid_are_preserved(self):
        records = self.records(self.unit(), self.unit(pointer=200))
        self.assertEqual(len(records), 2)

    def test_repeated_record_is_counted_once(self):
        self.assertEqual(len(self.records(self.unit(), self.unit())), 1)

    def test_unknown_level_is_not_assumed_to_be_current(self):
        unit = self.unit()
        del unit["level_id"]
        self.assertEqual(self.records(unit), {})


if __name__ == "__main__":
    unittest.main()
