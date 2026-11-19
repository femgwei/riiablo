# D2Common native smoke harness

The `d2common_*` programs below are isolated DLL oracles. The newer
`d2game_controller` is a separate, live mutating debugger experiment; its
contract and evidence are documented in `../README.md`. Do not describe that
controller as read-only or terminate it with unresolved cleanup.

This is the first no-CD-Key layer of the original-vs-riiablo test plan. It
loads the user-supplied 1.10f `D2Common.dll` in a private 32-bit process and
checks two known no-argument ordinal exports (`10016`, `10018`) plus the
`sgptDataTables` export. It does not launch `Game.exe`, inject into a process,
write memory, or modify the installation.

Build from PowerShell:

```powershell
./build-x86.ps1
./build/d2common_smoke.exe 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
./build/d2common_smoke.exe 'G:\BaiduNetdiskDownload\Diablo II 1.10F' --probe-archives
```

The DLLs are loaded from the supplied directory. The smoke calls are deliberately
small; each future skill/path/stat call needs a reviewed 1.10f ABI and a fixture.
`--probe-archives` additionally loads the original `Storm.dll`, sets its base
path, and opens `d2data.mpq`, `d2exp.mpq`, and `Patch_D2.mpq`. This is safe to run
without starting the game or supplying a CD-Key. It confirms archive access, but
does not call `DATATBLS_LoadAllTxts`: that entry point assumes more game-wide Fog
initialization than this isolated process provides and is therefore kept out of
the unattended smoke path.

The harness is intentionally read-only. It does not launch `Game.exe`, inject
into a process, write memory, or modify the installation.

## Original-vs-Java seed-state differential

`build-x86.ps1` also builds `d2common_seed.exe`, which reads `init`, `low`, or
`set` followed by two unsigned 32-bit words from standard input. It emits CSV
with the input, resulting state, individual getters and paired getter. It calls
six actual DLL exports (`10912`–`10915`, `10921`, `10922`) with the 1.10f x86
fastcall ABI, and rejects unexpected export RVAs. No table initialization is
required. The higher-level runner pins the exact original DLL SHA-256 before
launch and gives the native process a 10-second timeout with no console window.

From the repository root:

```powershell
python tools\original-observer\seed-differential.py --home 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
```

This compares 448 identical input cases to the production Java `Seed` and
`D2Seed` classes. It checks initialization, seed replacement and unsigned
state/getter parity. This seed-state gate does not claim RNG draw parity.
It also does not test skill formulas or the live game's AI/combat behavior.

## Original RNG sequence differential

`d2common_rng.exe` invokes the actual DLL code at RVAs `0x38E30` (raw 64-bit
draw) and `0x3D3E0` (bounded unsigned low-word draw). Both complete function
bodies are pinned by bytes before calling. The selected build contains callable
out-of-line copies even though D2MOO also declares these routines inline.
The raw ABI uses ECX for the seed and EDX:EAX for the return; the bounded ABI
uses ECX for the seed and EDX for the signed bound. Static disassembly confirms
that neither body calls another function or accesses a global variable.

```powershell
python tools\original-observer\rng-differential.py --home 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
```

The runner compares 1408 sequences of 64 draws (90,112 outputs) to production
Java, including both updated seed words after every call. It covers invalid
bounds that must not advance the seed, a bound of one that does advance it,
powers of two, ordinary modulo bounds, unsigned carry and boundary seed words.
Inputs, stdout/stderr, hashes and mismatches are retained in `runs/*-rng/`.
This establishes RNG-function parity only, not live AI/skill/combat parity.
# D2Client animation call-chain observer

`d2client_animation_trace.exe` is the observational client-side companion to the
D2Game probes. It is built x86, verifies the shipped 1.10f D2Client SHA-256 and
uses one-shot breakpoints at four statically verified entry points. Each hit
records the module-relative RVA, return address, thread and a bounded stack,
then restores the original instruction byte before continuing. It does not
invoke a target function or inject a DLL.
Software breakpoints temporarily write executable bytes and adjust EIP;
observational does not mean zero target-memory mutation. The recorded stack
words are raw memory, not a fully unwound call stack.

Build with `build-x86.ps1`. Run it only against the already-running loader
instance, with a fresh output directory. A successful detach is required; an
`incomplete` summary means the selected action did not reach one or more
entries during the bounded observation window.
