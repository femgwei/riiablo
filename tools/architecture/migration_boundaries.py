"""M1 source-boundary ratchet and network inventory. No game assets required.

This is a conservative source scan, not Java semantic analysis. Compilation
and the existing module/map gates remain the authority for those boundaries.
"""
import argparse
from collections import Counter
import csv
import io
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
DEBT = ROOT / "tools/architecture/m1-presentation-debt.tsv"
LEXEMES = re.compile(r'/\*.*?\*/|//[^\n]*|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'', re.S)
PACKAGE = re.compile(r'\bpackage\s+([\w.]+)\s*;')
PRESENTATION = re.compile(
    r'\bcom\.riiablo\.(?:presentation|screen|widget|audio|graphics)\.(?:[\w.]+|\*)'
    r'|\bcom\.badlogic\.gdx\.(?:graphics|scenes|audio|backends)\.(?:[\w.]+|\*)'
    r'|\bcom\.badlogic\.gdx\.(?:Audio|Graphics)\b'
    r'|\b(?:com\.badlogic\.gdx\.)?Gdx\s*\.\s*(?:audio|graphics|gl(?:20|30)?)\b')
NETWORK = re.compile(
    r'\bcom\.riiablo\.(?:server\.(?:d2gs_test|d2gs_netty)\.(?:[\w.]+|\*)|(?:nnet|onet)\.(?:[\w.]+|\*))')
SOURCE_ROOTS = ("src/main/java", "gen/main/java")
MODULES = ("core", "shared", "game-data", "server-domain", "server-systems", "quests")


def java_sources(root):
    """Only production roots; never infer a source set from a class name."""
    modules = [root / module for module in MODULES]
    modules += sorted(path for path in (root / "server").glob("*") if path.is_dir())
    for module in modules:
        for source_root in SOURCE_ROOTS:
            for path in sorted((module / source_root).glob("**/*.java")):
                yield path, LEXEMES.sub(" ", path.read_text(encoding="utf-8"))


def presentation_edges(root):
    result = Counter()
    for path, code in java_sources(root):
        name = path.relative_to(root).as_posix()
        # core/presentation is the adapter owner. Client composition/replica
        # bridges inside engine are legacy debt, not a blanket package allowlist.
        if name.startswith("server/") or (name.startswith("core/")
                and not name.startswith("core/src/main/java/com/riiablo/engine/")):
            continue
        for match in PRESENTATION.finditer(PACKAGE.sub(" ", code)):
            result[(name, re.sub(r'\s+', '', match[0]))] += 1
    return result


def read_debt(path):
    rows = list(csv.DictReader(io.StringIO(path.read_text(encoding="utf-8-sig")), delimiter="\t"))
    result = {}
    for row in rows:
        key = (row["file"], row["target"])
        if key in result or not all(row.get(field) for field in
                ("file", "target", "count", "owner", "migration", "remove_when")):
            raise ValueError("Incomplete/duplicate M1 debt row: " + str(row))
        if int(row["count"]) <= 0:
            raise ValueError("M1 debt count must be positive: " + str(key))
        result[key] = row
    return result


def check_debt(edges, debt):
    violations = []
    for key, count in sorted(edges.items()):
        row = debt.get(key)
        if row is None or count > int(row["count"]):
            violations.append(f"{key[0]} -> {key[1]}: {count} > {row['count'] if row else 0}")
        if not key[0].startswith("core/src/main/java/com/riiablo/engine/client/"):
            violations.append(f"Headless module cannot depend on presentation: {key[0]} -> {key[1]}")
    for key, row in sorted(debt.items()):
        if edges[key] < int(row["count"]):
            violations.append(f"Shrink removed debt before accepting the baseline: {key}")
    return violations


def network_family(package):
    for prefix, family in (
            ("com.riiablo.server.d2gs_test", "d2gs_test"),
            ("com.riiablo.server.d2gs_netty", "d2gs_netty"),
            ("com.riiablo.nnet", "nnet"), ("com.riiablo.onet", "onet"),
            ("com.riiablo.net", "net"), ("com.riiablo.server.d2gs", "d2gs"),
            ("com.riiablo.server.bnls", "bnls"), ("com.riiablo.server.bncs", "bncs"),
            ("com.riiablo.server.mcp", "mcp")):
        if package == prefix or package.startswith(prefix + "."):
            return family
    return "other"


def network_inventory(root):
    rows = []
    for path, code in java_sources(root):
        if not path.relative_to(root).as_posix().startswith("server/"):
            continue
        match = PACKAGE.search(code)
        if match is None:
            raise ValueError("Missing Java package: " + str(path))
        rows.append(dict(family=network_family(match[1]), path=path.relative_to(root).as_posix(),
                         package=match[1], productionSourceSet="main"))
    if not rows:
        raise ValueError("No server production sources found")
    return sorted(rows, key=lambda row: row["path"])


def network_references(root):
    rows = []
    for path, code in java_sources(root):
        match = PACKAGE.search(code)
        own_family = network_family(match[1]) if match else "other"
        # Remove the package declaration, not the whole implementation tree:
        # a reference from d2gs_netty to d2gs_test must still be reported.
        code = PACKAGE.sub(lambda m: " " * len(m[0]), code)
        for target in NETWORK.finditer(code):
            family = network_family(target[0])
            if family == own_family:
                continue
            rows.append(dict(family=family, file=path.relative_to(root).as_posix(),
                             target=target[0], classification="production-reference"))
    return sorted(rows, key=lambda row: (row["file"], row["target"]))


def write_tsv(output, rows, fields):
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields, delimiter="\t", lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def network_violations(inventory, refs):
    violations = [f"Test harness is in a production source root: {row['path']}"
                  for row in inventory if row["family"] == "d2gs_test"]
    violations += [f"Production source depends on a test harness: {row['file']} -> {row['target']}"
                   for row in refs if row["family"] == "d2gs_test"]
    return violations


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("check", "inventory", "references"), default="check")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    output = args.output or ROOT / f"build/reports/architecture/m1-{args.mode}.json"
    if not output.is_absolute():
        output = ROOT / output
    if args.mode == "inventory":
        rows = network_inventory(ROOT)
        write_tsv(output, rows, ("family", "path", "package", "productionSourceSet"))
        count = sum(row["family"] == "d2gs_test" for row in rows)
        print(f"entries={len(rows)} d2gs_test_main_entries={count}")
        return 0
    refs = network_references(ROOT)
    counts = Counter(row["family"] for row in refs)
    if args.mode == "references":
        write_tsv(output, refs, ("family", "file", "target", "classification"))
        print(f"d2gs_test_refs={counts['d2gs_test']} d2gs_netty_refs={counts['d2gs_netty']} total={len(refs)}")
        return 0
    debt = read_debt(DEBT)
    edges = presentation_edges(ROOT)
    violations = check_debt(edges, debt)
    inventory = network_inventory(ROOT)
    violations.extend(network_violations(inventory, refs))
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(dict(
        presentationEdges=len(edges), presentationReferences=sum(edges.values()),
        debts=list(debt.values()), networkReferences=refs,
        networkInventory=inventory, violations=violations), indent=2) + "\n", encoding="utf-8")
    print(f"M1 presentation: {len(edges)} owned legacy edges, {sum(edges.values())} references")
    print(f"M1 test-harness external production references: {counts['d2gs_test']}")
    for violation in violations:
        print(violation, file=sys.stderr)
    return 1 if violations else 0


if __name__ == "__main__":
    sys.exit(main())
