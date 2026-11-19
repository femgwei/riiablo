"""Generate reviewed, deterministic SoloPlay-derived test manifests."""
import argparse
import json
from pathlib import Path

from soloplay_strategy import scenario


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--scenario", choices=("bash", "frenzy", "war_cry", "howl"), required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise SystemExit("output already exists: " + str(args.output))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(scenario(args.scenario), ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"scenario": args.scenario, "output": str(args.output)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
