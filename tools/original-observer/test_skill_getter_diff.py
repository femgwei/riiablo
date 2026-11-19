"""Failure gates for the live original skill-getter comparison."""
import csv
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("skill_diff", Path(__file__).with_name("skill-getter-diff.py"))
diff = importlib.util.module_from_spec(spec)
spec.loader.exec_module(diff)


class SkillGetterDiffTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        (self.root / "summary.json").write_text('{"status":"passed"}', encoding="utf8")
        for phase in ("native-cadence", "burst"):
            folder = self.root / phase
            folder.mkdir()
            (folder / "summary.json").write_text(json.dumps(dict(status="passed", cleanup_confirmed=True,
                detached=True, frames_executed=12, first_frame=100, last_frame=112)), encoding="utf8")
            (folder / "frames.csv").write_text("step,before,after\n" + "".join(
                f"{n},{99+n},{100+n}\n" for n in range(1, 13)), encoding="utf8")
            (folder / "skill-values.csv").write_text(
                "skill,level,min_fixed,max_fixed,min_integer,max_integer,owner_class,mastery\n" + "".join(
                    f"36,{n},768,1536,3,6,0,0\n" for n in range(1, 21)), encoding="utf8")
        self.port = self.root / "port.txt"
        self.port.write_text("\n".join(f"SKILL_FORMULA skill=36 level={n} min_fixed=768 max_fixed=1536 "
            "min_integer=3 max_integer=6 synergy=0 mastery=0" for n in range(1, 21)), encoding="utf8")

    def compare(self):
        return diff.compare(self.root, self.port)

    def test_complete_evidence_passes_with_explicit_scope(self):
        result = self.compare()
        self.assertEqual(result["status"], "passed")
        self.assertEqual(result["getter_returns"], 40)
        self.assertIn("actual casting", result["not_tested"])

    def test_port_damage_difference_fails(self):
        self.port.write_text(self.port.read_text().replace("min_fixed=768", "min_fixed=769", 1))
        self.assertEqual(self.compare()["status"], "failed")

    def test_missing_and_duplicate_native_level_rejected(self):
        path = self.root / "native-cadence/skill-values.csv"
        lines = path.read_text().splitlines()
        for content in (lines[:-1], lines + [lines[-1]]):
            with self.subTest(rows=len(content)):
                path.write_text("\n".join(content))
                with self.assertRaises(ValueError):
                    self.compare()

    def test_frame_skip_rejected(self):
        path = self.root / "burst/frames.csv"
        path.write_text(path.read_text().replace("2,101,102", "2,101,103"))
        with self.assertRaisesRegex(ValueError, "sequence"):
            self.compare()

    def test_unconfirmed_debugger_cleanup_rejected(self):
        path = self.root / "burst/summary.json"
        row = json.loads(path.read_text())
        row["detached"] = False
        path.write_text(json.dumps(row))
        with self.assertRaisesRegex(ValueError, "cleanup"):
            self.compare()

    def test_synergy_mismatch_rejected(self):
        self.port.write_text(self.port.read_text().replace("synergy=0", "synergy=1", 1))
        with self.assertRaisesRegex(ValueError, "synergy"):
            self.compare()

    def test_cadence_and_burst_disagreement_rejected(self):
        path = self.root / "burst/skill-values.csv"
        path.write_text(path.read_text().replace("768,1536,3,6", "1024,1536,4,6", 1))
        with self.assertRaisesRegex(ValueError, "changed"):
            self.compare()

    def test_missing_port_level_rejected(self):
        self.port.write_text("\n".join(self.port.read_text().splitlines()[:-1]))
        with self.assertRaisesRegex(ValueError, "exactly"):
            self.compare()


if __name__ == "__main__":
    unittest.main()
