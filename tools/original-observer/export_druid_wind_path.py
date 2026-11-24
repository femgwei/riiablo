"""Freeze observed wind movement and static DCC direction mapping, with separate evidence labels."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import pefile


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def export(directory,home,output):
    summary_path=directory/"runtime-wind-polling-summary.json"
    summary=json.loads(summary_path.read_text(encoding="utf8"))
    assert summary["background_verified"] and summary["owned_game_closed"]
    cmp_path=home/"D2CMP.dll"
    assert sha(cmp_path)=="e725036e1118d34b98d2b739525ff75a225e6964b425469e4773c93f0c9cf882"
    cmp=pefile.PE(str(cmp_path))
    directions=list(struct.unpack("<64I",cmp.get_data(0x1A3D0,256)))
    assert directions==[((i+8)&63)>>4 for i in range(64)]
    cmp.close()
    result=dict(schema=1,run_id=summary["run_id"],background_verified=True,
        fixture=summary["fixture"],cases={},
        direction_evidence="Static D2CMP lookup applied to observed path byte; no actual DCC submission trace",
        direction_table=directions,cmp_sha256=sha(cmp_path),summary_sha256=sha(summary_path))
    for capture in summary["captures"]:
        path=directory/capture["file"]
        assert sha(path)==capture["sha256"]
        data=json.loads(path.read_text(encoding="utf8"))
        name="twister" if data["skill_id"]==240 else "tornado"
        result["cases"][name]=dict(skill_id=data["skill_id"],identity=data["identity"],fixture=data["fixture"],
            source_sha256=sha(path),units=data["units"],limitations=data["limitations"])
    output.write_text(json.dumps(result,indent=2)+"\n",encoding="utf8")
    print({name:sum(len(u["states"]) for u in case["units"]) for name,case in result["cases"].items()})


if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory",type=Path);parser.add_argument("home",type=Path);parser.add_argument("output",type=Path)
    args=parser.parse_args();export(args.directory,args.home,args.output)
