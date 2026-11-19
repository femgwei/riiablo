"""Compare original 1.10f seed-state exports to the production Java port, without game windows."""
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
    cases = [(op, low, high) for op in ("init", "low", "set") for low in edges for high in edges]
    rng = random.Random(110)
    cases.extend(("set", rng.getrandbits(32), rng.getrandbits(32)) for _ in range(256))
    return cases


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--home", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if sys.platform != "win32":
        parser.error("Requires Windows and VS2019 x86 compiler plus a JDK")
    home = args.home.resolve()
    dll = home / "D2Common.dll"
    # The ABI/RVAs are reviewed for this exact user-supplied build only.
    if not dll.is_file() or digest(dll) != EXPECTED_DLL:
        parser.error("Unsupported D2Common.dll SHA-256; no native code was loaded")
    java, javac = shutil.which("java"), shutil.which("javac")
    if not java or not javac:
        parser.error("java and javac are required on PATH")
    run = (args.output or ROOT / "runs" / (time.strftime("%Y%m%d-%H%M%S") + "-seed")).resolve()
    run.mkdir(parents=True, exist_ok=False)
    cases = fixtures()
    inputs = "".join("%s %d %d\n" % case for case in cases)
    (run / "inputs.txt").write_text(inputs, encoding="ascii")
    report = {"schema": 1, "status": "error", "home": str(home), "cases": len(cases),
              "scope": "seed initialization/set/get only; no RNG draws, skills, combat or AI runtime parity",
              "dll_sha256": EXPECTED_DLL, "ordinals": [10912, 10913, 10914, 10915, 10921, 10922],
              "native_launch_hash_checked": False}

    def execute(label, command, input_text=None, timeout=60, cwd=run):
        result = subprocess.run(command, input=input_text, text=True, encoding="utf-8", errors="replace",
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, cwd=cwd,
                                timeout=timeout, creationflags=subprocess.CREATE_NO_WINDOW)
        (run / (label + ".stdout")).write_text(result.stdout, encoding="utf-8")
        (run / (label + ".stderr")).write_text(result.stderr, encoding="utf-8")
        report[label + "_exit_code"] = result.returncode
        if result.returncode:
            raise RuntimeError("%s failed with %d; inspect %s" % (label, result.returncode, run))
        return result.stdout.splitlines()

    try:
        execute("build-native", ["powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
                                  str(ROOT / "native-harness" / "build-x86.ps1")])
        sources = [REPO / "D2MOO_JAVA/src/main/java/com/d2moo/common/drlg/D2Seed.java",
                   REPO / "D2MOO_JAVA/src/main/java/com/d2moo/common/seed/Seed.java",
                   ROOT / "native-harness/SeedVectors.java"]
        classes = run / "classes"
        classes.mkdir()
        execute("build-java", [javac, "-encoding", "UTF-8", "-d", str(classes)] + list(map(str, sources)))
        report["java_source_sha256"] = {str(p.relative_to(REPO)): digest(p) for p in sources}
        native_exe = ROOT / "native-harness/build/d2common_seed.exe"
        report["native_exe_sha256"] = digest(native_exe)
        # Recheck after compilation; an old or changed installation must fail closed.
        if digest(dll) != EXPECTED_DLL:
            raise RuntimeError("Original DLL changed before launch")
        report["native_launch_hash_checked"] = True
        native = execute("native", [str(native_exe), str(home)], inputs, timeout=10)
        port = execute("java", [java, "-cp", str(classes), "SeedVectors"], inputs, timeout=10)
        if len(native) != len(cases) or len(port) != len(cases):
            raise RuntimeError("Incomplete oracle output")
        for label, rows in (("native", native), ("java", port)):
            for i, row in enumerate(rows):
                fields = row.split(",")
                prefix = [str(value) for value in cases[i]]
                if (len(fields) != 9 or fields[:3] != prefix
                        or any(not v.isdigit() or not 0 <= int(v) <= 0xFFFFFFFF for v in fields[3:])):
                    raise RuntimeError("Malformed %s output at case %d" % (label, i))
        mismatches = [{"case": i, "input": list(cases[i]), "original": a, "java": b}
                      for i, (a, b) in enumerate(zip(native, port)) if a != b]
        report.update(status="passed" if not mismatches else "failed", mismatches=mismatches,
                      matched=len(cases) - len(mismatches), original_dll_unchanged=digest(dll) == EXPECTED_DLL)
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
