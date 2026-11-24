"""Compare complete wind positions/velocities by spread lane and native age, without GPU claims."""
import argparse
import hashlib
import json
import math
from pathlib import Path


def read(path):return json.loads(path.read_text(encoding="utf8"))
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def compare(directory):
    records=[]
    for name in ["twister","tornado"]:
        original_path=directory/("druid-"+name+"-replay-input-1.10f.json")
        production_path=directory/("druid-"+name+"-production-1.10f.json")
        before_path=directory/("druid-"+name+"-production-before-client-path-1.10f.json")
        original,output,before=read(original_path),read(production_path),read(before_path)
        assert original["run_id"]==output["source_run_id"]==before["source_run_id"]
        assert original["fixture"]["sha256"]==output["fixture_sha256"]
        assert original["fixture"]["hard_points"]==output["production"]["hard_points"]
        counts={};stable=[]
        for visual in [True,False]:
            births=[b for b in output["production"]["births"] if b["missile"]==name and b["presentation_only"]==visual]
            assert len(births)==len(original["units"])
            checked=missing=position_errors=velocity_errors=duplicates=0
            for unit,birth in zip(original["units"],births):
                states={s["age"]:s for s in birth["states"]}
                for s in unit["states"]:
                    age=s["total_frames"]-s["current_frame"];checked+=1
                    actual=states.get(age)
                    if actual is None:missing+=1;continue
                    position_errors+=(s["x_fixed"],s["y_fixed"])!=(actual["x_fixed"],actual["y_fixed"])
                    velocity_errors+=(s["velocity_x"],s["velocity_y"])!=(actual["velocity_x"],actual["velocity_y"])
                    if not visual and age>0:duplicates+=actual["loaded"] or not actual["hidden_damage_carrier"]
                if visual:
                    dirs=sorted({s["direction"] for s in birth["states"] if s["loaded"]});assert len(dirs)==1
                    stable.append(dirs[0])
            counts["client_visual" if visual else "local_authority_vs_client_oracle"]=dict(samples=checked,
                missing=missing,position_mismatches=position_errors,velocity_mismatches=velocity_errors,
                combat_carrier_duplicate_draw_candidates=duplicates)
            assert not (missing or position_errors or velocity_errors or duplicates)
        old=[b for b in before["production"]["births"] if b["missile"]==name and not b["presentation_only"]]
        old_distances=[]
        for unit,birth in zip(original["units"],old):
            states={s["age"]:s for s in birth["states"]}
            for s in unit["states"]:
                age=s["total_frames"]-s["current_frame"]
                if age in states:
                    pos=states[age]["position"]
                    old_distances.append(math.hypot(pos[0]-(s["x_fixed"]/65536-.5),pos[1]-(s["y_fixed"]/65536-.5)))
        record=dict(schema=1,source_run_id=original["run_id"],skill_id=original["skill_id"],
            scope="Original client polling motion oracle and shared native path initializer; no original server or GPU trace",
            checks=counts,
            before_projection_comparison=dict(samples=len(old_distances),mismatches_over_0_002_cell=sum(d>.002 for d in old_distances),
                maximum_distance_cells=max(old_distances),note="Old JSON stores rounded host floats; not an exact raw-native precision claim"),
            original_initializers=[dict(seed_low=original["identity"]["target_x"]+i,seed_high=666) for i in range(len(original["units"]))],
            native_static_inferred_dcc_directions=[(((u["states"][0]["path_direction"]+8)&63)>>4) if name=="twister" else 0 for u in original["units"]],
            production_stable_dcc_directions=stable,direction_allocation_rng_matched=False,
            remaining_differences=["Sprite variants use the static native direction mapping and stay fixed; original global allocation RNG is not matched",
                "No actual DCC submission/direction trace or GPU pixel comparison in this polling batch",
                "One level-one cast/direction in a clear initial corridor; full wall/room removal and all-direction cases remain unverified",
                "Local damage carriers share the audited native path; actual original server collision/damage is not captured",
                "Restored carriers without a SkillDo visual retain snapshot fallback; old rolled path seeds retain legacy movement"],
            sha256=dict(original=sha(original_path),production=sha(production_path),before=sha(before_path)))
        path=directory/("druid-"+name+"-path-comparison-1.10f.json")
        path.write_text(json.dumps(record,indent=2)+"\n",encoding="utf8");records.append(record)
        print(name,counts,"before max distance",round(max(old_distances),4))
    return records


if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("directory",type=Path)
    compare(parser.parse_args().directory)
