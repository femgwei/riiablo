import copy
import unittest

from compare_population_census import compare


class PopulationComparisonTests(unittest.TestCase):
    def setUp(self):
        self.census = dict(status="completed_all_generated_rooms", rooms_visited=1,
            navigation_identity=dict(status="passed", seed=123, difficulty=0, level_id=42, matched_rooms=1),
            units={"1:7:100": dict(type=1, unit_id=7, pointer=100, class_id=84, level_id=42),
                   "1:7:200": dict(type=1, unit_id=7, pointer=200, class_id=84, level_id=42),
                   "1:8:300": dict(type=1, unit_id=8, pointer=300, class_id=269, level_id=42)})
        self.port = dict(context=dict(seed="123", difficulty="0", level="42"),
            classes={84: dict(id="cat", align="0", npc="false", killable="true"),
                     269: dict(id="scorpion", align="1", npc="false", killable="true")},
            population=[dict(**{"class": "84"})], runtime=[dict(hcIdx="84")],
            rooms=[dict(monstersSpawned="true")])

    def test_reloaded_pointers_do_not_inflate_enemy_count_or_include_critters(self):
        report = compare(self.census, self.port)
        self.assertEqual(report["original_type1_pointer_records"], 3)
        self.assertEqual(report["original_type1_distinct_guids"], 2)
        self.assertEqual(report["original_hostile_distinct_guids"], 1)
        self.assertEqual(report["hostile_guid_difference"], 0)
        self.assertFalse(report["exact_initial_population_verified"])

    def test_wrong_seed_or_unmaterialized_port_is_rejected(self):
        for mutate in (lambda p:p["context"].update(seed="124"),
                       lambda p:p["rooms"][0].update(monstersSpawned="false")):
            port = copy.deepcopy(self.port)
            mutate(port)
            with self.assertRaises(ValueError):
                compare(self.census, port)

    def test_partial_walk_and_guid_class_conflicts_are_explicit(self):
        self.census.update(status="timeout", rooms_visited=0)
        self.census["units"]["1:7:400"] = dict(type=1, unit_id=7, pointer=400, class_id=269, level_id=42)
        report = compare(self.census, self.port)
        self.assertFalse(report["all_original_rooms_visited"])
        self.assertEqual(report["status"], "partial_census")
        self.assertEqual(report["guid_class_conflicts"], {"7": [84, 269]})


if __name__ == "__main__":
    unittest.main()
