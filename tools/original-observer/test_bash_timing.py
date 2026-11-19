import tempfile
import json
import unittest
from pathlib import Path

from summarize_barbarian_bash import iter_samples, read_timing


class BashTimingTest(unittest.TestCase):
    def test_attack_duration_is_mode_transition_not_capture_window(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "frames.csv").write_text(
                "frame,player_mode\n100,7\n107,7\n116,1\n180,1\n", encoding="utf8")
            (root / "damage.csv").write_text("frame,total_fixed\n107,2650\n", encoding="utf8")
            self.assertEqual({"attack_frames": 16, "damage_frame": 7}, read_timing(root, 100))

    def test_missing_transition_is_unknown(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "frames.csv").write_text("frame,player_mode\n100,7\n180,7\n", encoding="utf8")
            self.assertEqual({"attack_frames": None, "damage_frame": None}, read_timing(root, 100))

    def test_nested_broker_batch_keeps_outer_run_identity_and_rejects_unclean_trace(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sample = root / "run-a/batch-001/orchestrator/process-session/001-bash"
            sample.mkdir(parents=True)
            summary = dict(status="passed", scope="Bash entity-targeted", cleanup_confirmed=True,
                           detached=True, damage_total_fixed=100, first_frame=100, last_frame=180)
            (sample / "summary.json").write_text(json.dumps(summary), encoding="utf8")
            (sample / "identity.json").write_text('{"level":12}', encoding="utf8")
            (sample / "damage.csv").write_text(
                "frame,physical_fixed,total_fixed\n107,3172,100\n", encoding="utf8")
            samples = list(iter_samples(root))
            self.assertEqual(1, len(samples))
            self.assertEqual(("run-a", 12), (samples[0]["run"], samples[0]["level"]))
            self.assertEqual(3072, samples[0]["physical_minus_total_fixed"])
            summary["cleanup_confirmed"] = False
            (sample / "summary.json").write_text(json.dumps(summary), encoding="utf8")
            self.assertEqual([], list(iter_samples(root)))


if __name__ == "__main__":
    unittest.main()
