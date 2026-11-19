"""Failure gates for original/port scene comparisons (no live game required)."""
import copy
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("layout_diff", Path(__file__).with_name("layout-diff.py"))
diff = importlib.util.module_from_spec(spec)
spec.loader.exec_module(diff)


class LayoutDiffTests(unittest.TestCase):
    def setUp(self):
        self.original = dict(schema=2, map_seed=123, difficulty=0, levels=[dict(
            level_id=2, size_tiles=[8, 8], reported_room_count=1, rooms=[dict(
                local_subtiles=[0, 0, 40, 40], preset=[4, 2], preset_map_available=True,
                tile_grid_initialized=True, adjacency=[], preset_units=[dict(
                    type=2, class_id=580, mode=0, local_subtiles=[9, 10])])])])
        self.scene = dict(context=dict(seed="123", difficulty="0", level="2", width="40", height="40"),
            rooms=[dict(id=7, local=(0, 0, 40, 40), preset=[4, 2], adjacent=[])],
            entities={"NATIVE_OBJECT": [dict(room="7", presetClass="580", **{"class": "243"},
                mode="0", pos="9,10", source="Act1/Cott1.ds1", resolver="SPECIAL_CHEST")]})

    def result(self):
        return diff.compare(self.original, self.scene)

    def test_same_stage_matches_despite_resolved_class(self):
        result = self.result()
        self.assertEqual(result["status"], "partial")
        self.assertEqual(result["checks"]["initialized_preset_objects"]["matched"], 1)
        self.assertEqual(result["checks"]["runtime_object_classes"]["status"], "not_tested")

    def test_different_seed_rejected(self):
        self.scene["context"]["seed"] = "124"
        with self.assertRaisesRegex(ValueError, "seeds"):
            self.result()

    def test_different_difficulty_rejected(self):
        self.scene["context"]["difficulty"] = "1"
        with self.assertRaisesRegex(ValueError, "difficulties"):
            self.result()

    def test_empty_original_rejected(self):
        self.original["levels"][0].update(rooms=[], reported_room_count=0)
        with self.assertRaisesRegex(ValueError, "empty"):
            self.result()

    def test_same_count_different_geometry_fails(self):
        self.scene["rooms"][0]["local"] = (1, 0, 40, 40)
        self.assertEqual(self.result()["checks"]["room_geometry"]["status"], "failed")

    def test_missing_object_fails(self):
        self.scene["entities"]["NATIVE_OBJECT"] = []
        self.assertEqual(self.result()["checks"]["initialized_preset_objects"]["status"], "failed")

    def test_wrong_object_position_or_class_fails(self):
        for field, value in (("presetClass", "243"), ("pos", "10,10"), ("mode", "1")):
            with self.subTest(field=field):
                scene = copy.deepcopy(self.scene)
                scene["entities"]["NATIVE_OBJECT"][0][field] = value
                self.assertEqual(diff.compare(self.original, scene)["status"], "failed")

    def test_population_objects_excluded(self):
        extra = dict(self.scene["entities"]["NATIVE_OBJECT"][0], source="ObjGroup/33/slot/1", presetClass="144")
        self.scene["entities"]["NATIVE_OBJECT"].append(extra)
        self.assertEqual(self.result()["checks"]["initialized_preset_objects"]["status"], "passed")

    def test_old_file_index_not_tested(self):
        self.original["schema"] = 1
        self.original["levels"][0]["rooms"][0]["preset"][1] = 0
        self.assertEqual(self.result()["checks"]["selected_file_variants"]["status"], "not_tested")

    def test_missing_pre_resolution_metadata_not_tested(self):
        del self.scene["entities"]["NATIVE_OBJECT"][0]["presetClass"]
        self.assertEqual(self.result()["checks"]["initialized_preset_objects"]["status"], "not_tested")

    def test_manifest_coordinates_and_duplicate_gate(self):
        text = ("SCENE_CONTEXT level=2 seed=123 difficulty=0 originX=100 originY=200 width=40 height=40\n"
                "ROOM id=7 x=100 y=200 width=40 height=40 preset=4:2 adjacent=[]\n")
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "scene.txt"
            path.write_text(text, encoding="utf8")
            self.assertEqual(diff.read_scene(path)["rooms"][0]["local"], (0, 0, 40, 40))
            path.write_text(text + text.splitlines()[-1] + "\n", encoding="utf8")
            with self.assertRaisesRegex(ValueError, "Duplicate"):
                diff.read_scene(path)


if __name__ == "__main__":
    unittest.main()
