import csv
import json
import tempfile
import unittest
from pathlib import Path

from check_whirlwind import check


class WhirlwindTraceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.summary = dict(status="passed", cleanup_confirmed=True, detached=True,
                            fixtures_restored=True, first_frame=100, last_frame=104,
                            damage_events=1, damage_total_fixed=256,
                            target_hp_before_fixed=256000, target_hp_after_fixed=255744,
                            mana_before_fixed=256000, lowest_mana_fixed=249600,
                            start_calls=1, do_calls=2)
        self.identity = dict(skill=151, level=1, **{"class": 4}, player=11, target=12,
                             right_item_class=0, left_item_class=328,
                             game=13, start_x=10, start_y=10, destination_x=12, destination_y=10)
        self.frames = [dict(frame=100+i, player_mode=18 if i<4 else 1,
                            x=10+i//2, y=10, target_hp_fixed=256000 if i<3 else 255744,
                            mana_fixed=249600 + 85*i,
                            param4=107 if i>=3 else 0) for i in range(5)]
        self.damage = [dict(frame=103, attacker=11, victim=12, total_fixed=256)]
        self.trace = []
        for kind, frame, rva in [("start",100,0xCBFE0), ("do",103,0xCC4C0),
                                 ("damage",103,0x91260), ("do",104,0xCC4C0)]:
            self.trace.append(dict(kind=kind, frame=frame, eip=0x6FC30000+rva,
                                   ecx=13, edx=11,
                                   stack=[0,12,0]+[0]*13 if kind=="damage" else [0,151,1]+[0]*13))

    def write(self):
        for name, value in [("summary",self.summary), ("identity",self.identity)]:
            (self.root/(name+".json")).write_text(json.dumps(value), encoding="utf8")
        for name, rows in [("frames",self.frames), ("damage",self.damage)]:
            with (self.root/(name+".csv")).open("w",encoding="utf8",newline="") as stream:
                writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
                writer.writeheader()
                writer.writerows(rows)
        (self.root/"trace.jsonl").write_text("\n".join(map(json.dumps,self.trace)),encoding="utf8")

    def test_valid_trace_reports_observed_window(self):
        self.write()
        result = check(self.root)
        self.assertEqual([3], result["damage_frames"])
        self.assertEqual([dict(frame=3,next_deadline=7)], result["attack_timer_windows"])
        self.assertEqual(6400, result["mana_immediate_cost_fixed"])
        self.assertEqual(6060, result["mana_final_net_drop_fixed"])

    def test_resource_metadata_preserves_observed_recovery_series(self):
        self.write()
        (self.root/"resources.json").write_text(json.dumps(dict(
            mana_before_fixed=256000, max_mana_fixed=256000,
            mana_recovery=0, mana_recovery_bonus=0)), encoding="utf8")
        self.assertEqual([85], check(self.root)["resources"]["observed_step_deltas_fixed"])

    def test_rejects_incorrect_minimum_mana(self):
        self.summary["lowest_mana_fixed"] += 1
        self.write()
        with self.assertRaisesRegex(ValueError,"lowest mana"):
            check(self.root)

    def test_rejects_unconfirmed_fixture_restoration(self):
        self.summary["fixtures_restored"] = False
        self.write()
        with self.assertRaisesRegex(ValueError,"cleanup/restoration"):
            check(self.root)

    def test_rejects_skipped_frame(self):
        self.frames.pop(2)
        self.write()
        with self.assertRaisesRegex(ValueError,"skipped/duplicate"):
            check(self.root)

    def test_rejects_hp_event_disagreement(self):
        self.frames[3]["target_hp_fixed"] += 1
        self.write()
        with self.assertRaisesRegex(ValueError,"per-frame HP"):
            check(self.root)

    def test_rejects_wrong_damage_target(self):
        self.damage[0]["victim"] = 99
        self.write()
        with self.assertRaisesRegex(ValueError,"target binding"):
            check(self.root)

    def test_rejects_wrong_weapon_fixture(self):
        self.identity["right_item_class"] = 1
        self.write()
        with self.assertRaisesRegex(ValueError,"hand-axe/shield"):
            check(self.root)

    def precise_frames(self):
        for i, row in enumerate(self.frames):
            row.update(x_fp16=(row["x"]<<16) + (100 if i>=3 else 0),
                       y_fp16=row["y"]<<16, path_velocity=1536)

    def test_precise_path_reports_first_fractional_displacement(self):
        self.precise_frames()
        self.write()
        self.assertEqual(2, check(self.root)["precise_path"]["first_displacement_frame"])

    def test_rejects_inconsistent_precise_coordinate(self):
        self.precise_frames()
        self.frames[3]["x_fp16"] += 65536
        self.write()
        with self.assertRaisesRegex(ValueError,"FP16/integer"):
            check(self.root)

    def test_rejects_partial_precise_capture(self):
        self.precise_frames()
        for row in self.frames:
            del row["y_fp16"]
        self.write()
        with self.assertRaisesRegex(ValueError,"incomplete precise"):
            check(self.root)


if __name__ == "__main__":
    unittest.main()
