"""Run actual 1.10f D2Common RNG bodies against production Java, without game windows."""
import argparse
import hashlib
import json
from pathlib import Path
import random
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent.parent
EXPECTED_DLL = "b9d1a27c6a35992932c7c47438d02e5269f878e899f6e614d507902cecd1beab"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def fixtures():
    edges = (0, 1, 665, 666, 0x7FFFFFFF, 0x80000000, 0xFFFFFFFE, 0xFFFFFFFF)
    seeds = [(a, b) for a in edges for b in edges]
    rng = random.Random(0x110F)
    seeds += [(rng.getrandbits(32), rng.getrandbits(32)) for _ in range(64)]
    cases = []
    for low, high in seeds:
        cases.append(("raw", low, high, 0, 64))
        for bound in (-1, 0, 1, 2, 3, 7, 100, 256, 1000000, 0x7FFFFFFF):
            cases.append(("limited", low, high, bound, 64))
    return cases


def validate(rows, cases, label):
    expected = sum(case[4] for case in cases)
    if len(rows) != expected:
        raise RuntimeError("Incomplete %s output: %d != %d" % (label, len(rows), expected))
    offset = 0
    for index, (op, low, high, bound, steps) in enumerate(cases):
        for step in range(steps):
            fields = rows[offset].split(",")
            offset += 1
            if len(fields) != 5 or any(not v.isdigit() for v in fields):
                raise RuntimeError("Malformed %s row %d" % (label, offset))
            number, draw, value, state_low, state_high = map(int, fields)
            if number != index or draw != step or not (0 <= state_low <= 0xFFFFFFFF
                                                       and 0 <= state_high <= 0xFFFFFFFF):
                raise RuntimeError("Invalid %s case/step/state at row %d" % (label, offset))
            if op == "raw":
                if value != (state_high << 32 | state_low):
                    raise RuntimeError("Invalid %s 64-bit return at row %d" % (label, offset))
            elif bound <= 0:
                if (value, state_low, state_high) != (0, low, high):
                    raise RuntimeError("Invalid %s non-positive-bound behavior" % label)
            elif not 0 <= value < bound:
                raise RuntimeError("Invalid %s bounded return at row %d" % (label, offset))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--home", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if sys.platform != "win32":
        parser.error("Requires Windows, VS2019 x86 compiler, and a JDK")
    home = args.home.resolve()
    dll = home / "D2Common.dll"
    if not dll.is_file() or digest(dll) != EXPECTED_DLL:
        parser.error("Unsupported D2Common.dll SHA-256; no native code was loaded")
    java, javac = shutil.which("java"), shutil.which("javac")
    if not java or not javac:
        parser.error("java and javac are required on PATH")
    run = (args.output or ROOT / "runs" / (time.strftime("%Y%m%d-%H%M%S") + "-rng")).resolve()
    run.mkdir(parents=True, exist_ok=False)
    cases = fixtures()
    inputs = "".join("%s %d %d %d %d\n" % case for case in cases)
    (run / "inputs.txt").write_text(inputs, encoding="ascii")
    report = {"schema": 1, "status": "error", "home": str(home), "cases": len(cases),
              "draws": sum(c[4] for c in cases), "dll_sha256": EXPECTED_DLL,
              "scope": "raw uint64 and limited uint32 RNG sequences, return and both state words; not live skills/combat/AI",
              "rvas": {"raw": "0x38E30", "limited": "0x3D3E0"},
              "native_launch_hash_checked": False}

    def execute(label, command, input_text=None, timeout=60):
        result = subprocess.run(command, input=input_text, text=True, encoding="utf-8", errors="replace",
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, cwd=run,
                                timeout=timeout, creationflags=subprocess.CREATE_NO_WINDOW)
        (run / (label + ".stdout")).write_text(result.stdout, encoding="utf-8")
        (run / (label + ".stderr")).write_text(result.stderr, encoding="utf-8")
        report[label + "_exit_code"] = result.returncode
        if result.returncode:
            raise RuntimeError("%s failed with %d; inspect %s" % (label, result.returncode, run))
        return result.stdout.splitlines()

    try:
        execute("build-native", ["powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
                                 str(ROOT / "native-harness/build-x86.ps1")])
        sources = [REPO / "D2MOO_JAVA/src/main/java/com/d2moo/common/drlg/D2Seed.java",
                   REPO / "D2MOO_JAVA/src/main/java/com/d2moo/common/seed/Seed.java",
                   ROOT / "native-harness/RngVectors.java"]
        classes = run / "classes"
        classes.mkdir()
        execute("build-java", [javac, "-encoding", "UTF-8", "-d", str(classes)] + list(map(str, sources)))
        report["source_sha256"] = {str(p.relative_to(REPO)): digest(p) for p in sources}
        native_source = ROOT / "native-harness/d2common_rng.cpp"
        report["source_sha256"][str(native_source.relative_to(REPO))] = digest(native_source)
        native_exe = ROOT / "native-harness/build/d2common_rng.exe"
        report["native_exe_sha256"] = digest(native_exe)
        if digest(dll) != EXPECTED_DLL:
            raise RuntimeError("Original DLL changed before launch")
        report["native_launch_hash_checked"] = True
        native = execute("native", [str(native_exe), str(home)], inputs, timeout=10)
        port = execute("java", [java, "-cp", str(classes), "RngVectors"], inputs, timeout=10)
        validate(native, cases, "native")
        validate(port, cases, "java")
        mismatches = [{"row": i, "native": a, "java": b}
                      for i, (a, b) in enumerate(zip(native, port)) if a != b]
        report.update(status="failed" if mismatches else "passed", mismatch_count=len(mismatches),
                      mismatches=mismatches[:100], matched=len(native) - len(mismatches),
                      original_dll_unchanged=digest(dll) == EXPECTED_DLL)
        if not report["original_dll_unchanged"]:
            raise RuntimeError("Original DLL identity changed during run")
    except (OSError, subprocess.TimeoutExpired, RuntimeError) as error:
        report.update(status="error", error=str(error))
    finally:
        (run / "summary.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"run": str(run), **report}, ensure_ascii=False))
    return 0 if report["status"] == "passed" else 1


if __name__ == "__main__":
    sys.exit(main())
