"""Do not admit incomplete or inconsistent native execution as a port oracle."""
import csv
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("cast_trace", Path(__file__).with_name("cast-trace-check.py"))
trace = importlib.util.module_from_spec(spec)
spec.loader.exec_module(trace)


class CastTraceTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.summary = dict(status="passed", cleanup_confirmed=True, detached=True, first_frame=100,
            last_frame=180, target_hp_before_fixed=256000, target_hp_after_fixed=254611,
            damage_fire_fixed=1389, lowest_mana_fixed=255445)
        self.identity = dict(name="CXSorc", **{"class":1}, skill=36, level=1, map_seed=127897359,
            target_class=0, target_x=7, target_y=0, player=1000, target=2000)
        self.frames = [dict(step=i, frame=100+i, player_mode=10 if i<14 else 1,
            mana_fixed=255445 if i==7 else 256000, target_hp_fixed=256000 if i<13 else 254611,
            target_x=7, target_y=0, owned_firebolts=int(7<=i<13), first_missile_id=1 if 7<=i<13 else 0,
            missile_seed_low=564996791 if 7<=i<13 else 0, missile_seed_high=666 if 7<=i<13 else 0,
            fire_min_fixed=768 if 7<=i<13 else 0, fire_max_fixed=1536 if 7<=i<13 else 0) for i in range(81)]
        self.hits = [dict(frame=113, attacker=1000, victim=2000, result_flags=5, physical_fixed=0,
            fire_fixed=1389, total_fixed=1389, missile_seed_after_low=567464301, missile_seed_after_high=235655850)]

    def check(self):
        for name, value in (("summary", self.summary), ("identity", self.identity)):
            (self.root / (name+".json")).write_text(json.dumps(value), encoding="utf8")
        for name, value in (("frames", self.frames), ("damage", self.hits)):
            with (self.root / (name+".csv")).open("w", newline="", encoding="utf8") as stream:
                writer = csv.DictWriter(stream, fieldnames=value[0].keys())
                writer.writeheader(); writer.writerows(value)
        return trace.check(self.root)

    def test_valid_trace_has_narrow_scope(self):
        result = self.check()
        self.assertEqual(result["status"], "passed")
        self.assertIn("visual parity", result["not_tested"])

    def test_cleanup_unconfirmed_rejected(self):
        self.summary["cleanup_confirmed"] = False
        with self.assertRaisesRegex(ValueError, "cleanup"): self.check()

    def test_frame_skip_rejected(self):
        self.frames[10]["frame"] += 1
        with self.assertRaisesRegex(ValueError, "sequence"): self.check()

    def test_wrong_damage_owner_rejected(self):
        self.hits[0]["attacker"] += 1
        with self.assertRaisesRegex(ValueError, "participants"): self.check()

    def test_hp_mismatch_rejected(self):
        self.frames[-1]["target_hp_fixed"] += 1
        with self.assertRaisesRegex(ValueError, "HP delta"): self.check()

    def test_rng_carry_mismatch_rejected(self):
        self.hits[0]["missile_seed_after_high"] += 1
        with self.assertRaisesRegex(ValueError, "RNG"): self.check()

    def test_wrong_seed_despite_same_damage_rejected(self):
        self.frames[12]["missile_seed_low"] += 1
        with self.assertRaisesRegex(ValueError, "Seed"): self.check()

    def test_wrong_target_motion_rejected(self):
        self.frames[12]["target_x"] += 1
        with self.assertRaisesRegex(ValueError, "target moved"): self.check()

    def test_duplicate_damage_rejected(self):
        self.hits.append(dict(self.hits[0]))
        with self.assertRaisesRegex(ValueError, "one damage event"): self.check()


if __name__ == "__main__": unittest.main()
