"""Offline Diablo II process probe. Python 3.9+, standard library, Windows x64.

No SwitchDesktop, global input, injection, remote calls or memory writes.
The launched game belongs to a kill-on-close job on a private desktop.
Only explicitly listed game/loader executables from a prepared runtime are accepted;
the legacy ``Diablo II.exe`` launcher is intentionally excluded because its
CD-ROM/compatibility check can show a Windows compatibility dialog.
"""
import argparse
import ctypes as C
from ctypes import wintypes as W
import hashlib
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parent
EXECUTABLES = ("Game.exe", "D2Loader.exe", "D2v110各版本免CD全功能启动.exe")
DLLS = {
    "binkw32.dll", "bnclient.dll", "d2client.dll", "d2cmp.dll", "d2common.dll",
    "d2ddraw.dll", "d2direct3d.dll", "d2game.dll", "d2gdi.dll", "d2gfx.dll",
    "d2glide.dll", "d2lang.dll", "d2launch.dll", "d2mcpclient.dll", "d2multi.dll",
    "d2net.dll", "d2sound.dll", "d2win.dll", "fog.dll", "ijl11.dll",
    "smackw32.dll", "storm.dll",
}
PROFILE_FILES = ("Game.exe", "D2Client.dll", "D2Common.dll", "D2Game.dll", "D2Win.dll")
# Game.exe also reads these small installation-level files before D2Client is
# loaded.  They are copied into the isolated runtime; the original directory
# is never used as a write target.
SUPPORT_FILES = {
    "bncache.dat", "d2131210.txt", "d2messages.log", "default.cfg", "default.key",
    "gameport.dat", "install.log", "listfile.dat", "res_sc.lng", "waypoint.bmp",
}


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def saves(path):
    return {str(p.relative_to(path)): digest(p) for p in path.rglob("*.d2s")}


def prepare(source, destination):
    source, destination = source.resolve(), destination.resolve()
    if destination.exists():
        raise ValueError("Destination must be new; refusing to overwrite a runtime")
    if source == destination or source in destination.parents:
        raise ValueError("Runtime must be outside the original installation")
    if not all((source / f).is_file() for f in PROFILE_FILES):
        raise ValueError("Missing required EXE/DLL")
    destination.mkdir(parents=True)
    manifest = {"source": str(source), "files": [], "original_saves": saves(source)}
    for path in source.iterdir():
        name = path.name.lower()
        if path.is_file() and (name in DLLS or name in {n.lower() for n in EXECUTABLES} or
                               name in SUPPORT_FILES or path.suffix.lower() == ".mpq" or name == "d2.lng"):
            shutil.copy2(path, destination / path.name)
            manifest["files"].append({"name": path.name, "bytes": path.stat().st_size,
                                      "sha256": digest(path)})
    # A directly launched Game.exe expects the normal save search path to
    # exist. Copy it into the isolated runtime so character selection/loading
    # follows the same path while the source installation remains untouched.
    save_source = source / "save"
    if save_source.is_dir():
        shutil.copytree(save_source, destination / "save", dirs_exist_ok=True)
        manifest["copied_directories"] = ["save"]
    (destination / "observer-manifest.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps({"runtime": str(destination), "files": len(manifest["files"]),
                      "save_files": len(manifest["original_saves"])}, ensure_ascii=False))


class STARTUPINFO(C.Structure):
    _fields_ = [("cb", W.DWORD), ("lpReserved", W.LPWSTR), ("lpDesktop", W.LPWSTR),
                ("lpTitle", W.LPWSTR), ("dwX", W.DWORD), ("dwY", W.DWORD),
                ("dwXSize", W.DWORD), ("dwYSize", W.DWORD), ("dwXCountChars", W.DWORD),
                ("dwYCountChars", W.DWORD), ("dwFillAttribute", W.DWORD),
                ("dwFlags", W.DWORD), ("wShowWindow", W.WORD), ("cbReserved2", W.WORD),
                ("lpReserved2", C.POINTER(C.c_byte)), ("hStdInput", W.HANDLE),
                ("hStdOutput", W.HANDLE), ("hStdError", W.HANDLE)]


class PROCESS_INFORMATION(C.Structure):
    _fields_ = [("hProcess", W.HANDLE), ("hThread", W.HANDLE),
                ("dwProcessId", W.DWORD), ("dwThreadId", W.DWORD)]


class MODULEENTRY32(C.Structure):
    _fields_ = [("dwSize", W.DWORD), ("th32ModuleID", W.DWORD),
                ("th32ProcessID", W.DWORD), ("GlblcntUsage", W.DWORD),
                ("ProccntUsage", W.DWORD), ("modBaseAddr", C.c_void_p),
                ("modBaseSize", W.DWORD), ("hModule", W.HMODULE),
                ("szModule", W.WCHAR * 256), ("szExePath", W.WCHAR * 260)]


class JOBOBJECT_BASIC_LIMIT_INFORMATION(C.Structure):
    _fields_ = [("PerProcessUserTimeLimit", C.c_longlong),
                ("PerJobUserTimeLimit", C.c_longlong), ("LimitFlags", W.DWORD),
                ("MinimumWorkingSetSize", C.c_size_t), ("MaximumWorkingSetSize", C.c_size_t),
                ("ActiveProcessLimit", W.DWORD), ("Affinity", C.c_size_t),
                ("PriorityClass", W.DWORD), ("SchedulingClass", W.DWORD)]


class IO_COUNTERS(C.Structure):
    _fields_ = [(name, C.c_ulonglong) for name in
                ("ReadOperationCount", "WriteOperationCount", "OtherOperationCount",
                 "ReadTransferCount", "WriteTransferCount", "OtherTransferCount")]


class JOB_LIMITS(C.Structure):
    _fields_ = [("BasicLimitInformation", JOBOBJECT_BASIC_LIMIT_INFORMATION),
                ("IoInfo", IO_COUNTERS), ("ProcessMemoryLimit", C.c_size_t),
                ("JobMemoryLimit", C.c_size_t), ("PeakProcessMemoryUsed", C.c_size_t),
                ("PeakJobMemoryUsed", C.c_size_t)]


class EXCEPTION_RECORD(C.Structure):
    _fields_ = [("code", W.DWORD), ("flags", W.DWORD), ("record", C.c_void_p),
                ("address", C.c_void_p), ("parameter_count", W.DWORD),
                ("parameters", C.c_size_t * 15)]


class EXCEPTION_INFO(C.Structure):
    _fields_ = [("record", EXCEPTION_RECORD), ("first_chance", W.DWORD)]


class DEBUG_UNION(C.Union):
    _fields_ = [("exception", EXCEPTION_INFO), ("raw", C.c_byte * 160)]


class DEBUG_EVENT(C.Structure):
    _fields_ = [("code", W.DWORD), ("pid", W.DWORD), ("tid", W.DWORD),
                ("data", DEBUG_UNION)]


class BITMAPINFOHEADER(C.Structure):
    _fields_ = [("biSize", W.DWORD), ("biWidth", C.c_long), ("biHeight", C.c_long),
                ("biPlanes", W.WORD), ("biBitCount", W.WORD), ("biCompression", W.DWORD),
                ("biSizeImage", W.DWORD), ("biXPelsPerMeter", C.c_long),
                ("biYPelsPerMeter", C.c_long), ("biClrUsed", W.DWORD),
                ("biClrImportant", W.DWORD)]


class RGBQUAD(C.Structure):
    _fields_ = [("rgbBlue", W.BYTE), ("rgbGreen", W.BYTE), ("rgbRed", W.BYTE),
                ("rgbReserved", W.BYTE)]


class BITMAPINFO(C.Structure):
    _fields_ = [("bmiHeader", BITMAPINFOHEADER), ("bmiColors", RGBQUAD)]


def api(dll, name, result, *args):
    fn = getattr(dll, name)
    fn.restype, fn.argtypes = result, list(args)
    return fn


class Win32:
    def __init__(self):
        self.k = C.WinDLL("kernel32", use_last_error=True)
        self.u = C.WinDLL("user32", use_last_error=True)
        self.gdi = C.WinDLL("gdi32", use_last_error=True)
        self.close = api(self.k, "CloseHandle", W.BOOL, W.HANDLE)
        self.create_desktop = api(self.u, "CreateDesktopW", W.HANDLE, W.LPCWSTR,
                                  W.LPCWSTR, C.c_void_p, W.DWORD, W.DWORD, C.c_void_p)
        self.close_desktop = api(self.u, "CloseDesktop", W.BOOL, W.HANDLE)
        self.create_process = api(self.k, "CreateProcessW", W.BOOL, W.LPCWSTR,
                                  W.LPWSTR, C.c_void_p, C.c_void_p, W.BOOL, W.DWORD,
                                  C.c_void_p, W.LPCWSTR, C.POINTER(STARTUPINFO),
                                  C.POINTER(PROCESS_INFORMATION))
        self.resume = api(self.k, "ResumeThread", W.DWORD, W.HANDLE)
        self.wait = api(self.k, "WaitForSingleObject", W.DWORD, W.HANDLE, W.DWORD)
        self.terminate = api(self.k, "TerminateProcess", W.BOOL, W.HANDLE, W.UINT)
        self.create_job = api(self.k, "CreateJobObjectW", W.HANDLE, C.c_void_p, W.LPCWSTR)
        self.set_job = api(self.k, "SetInformationJobObject", W.BOOL, W.HANDLE,
                           C.c_int, C.c_void_p, W.DWORD)
        self.assign_job = api(self.k, "AssignProcessToJobObject", W.BOOL, W.HANDLE, W.HANDLE)
        self.snapshot = api(self.k, "CreateToolhelp32Snapshot", W.HANDLE, W.DWORD, W.DWORD)
        self.module_first = api(self.k, "Module32FirstW", W.BOOL, W.HANDLE,
                                C.POINTER(MODULEENTRY32))
        self.module_next = api(self.k, "Module32NextW", W.BOOL, W.HANDLE,
                               C.POINTER(MODULEENTRY32))
        self.rpm = api(self.k, "ReadProcessMemory", W.BOOL, W.HANDLE, C.c_void_p,
                       C.c_void_p, C.c_size_t, C.POINTER(C.c_size_t))
        self.foreground = api(self.u, "GetForegroundWindow", W.HWND)
        self.system_metric = api(self.u, "GetSystemMetrics", C.c_int, C.c_int)
        self.show_window = api(self.u, "ShowWindow", W.BOOL, W.HWND, C.c_int)
        self.get_thread_desktop = api(self.u, "GetThreadDesktop", W.HANDLE, W.DWORD)
        self.enum_callback = C.WINFUNCTYPE(W.BOOL, W.HWND, W.LPARAM)
        self.enum_windows = api(self.u, "EnumDesktopWindows", W.BOOL, W.HANDLE,
                                self.enum_callback, W.LPARAM)
        self.enum_child_windows = api(self.u, "EnumChildWindows", W.BOOL, W.HWND,
                                      self.enum_callback, W.LPARAM)
        self.window_pid = api(self.u, "GetWindowThreadProcessId", W.DWORD,
                              W.HWND, C.POINTER(W.DWORD))
        self.window_text = api(self.u, "GetWindowTextW", C.c_int, W.HWND, W.LPWSTR, C.c_int)
        self.class_name = api(self.u, "GetClassNameW", C.c_int, W.HWND, W.LPWSTR, C.c_int)
        self.get_rect = api(self.u, "GetClientRect", W.BOOL, W.HWND, C.POINTER(W.RECT))
        self.get_dc = api(self.u, "GetDC", W.HDC, W.HWND)
        self.release_dc = api(self.u, "ReleaseDC", C.c_int, W.HWND, W.HDC)
        self.print_window = api(self.u, "PrintWindow", W.BOOL, W.HWND, W.HDC, W.UINT)
        self.is_visible = api(self.u, "IsWindowVisible", W.BOOL, W.HWND)
        self.exit_code = api(self.k, "GetExitCodeProcess", W.BOOL, W.HANDLE,
                             C.POINTER(W.DWORD))
        self.post = api(self.u, "PostMessageW", W.BOOL, W.HWND, W.UINT, W.WPARAM, W.LPARAM)
        self.debug_wait = api(self.k, "WaitForDebugEvent", W.BOOL, C.POINTER(DEBUG_EVENT), W.DWORD)
        self.debug_continue = api(self.k, "ContinueDebugEvent", W.BOOL, W.DWORD, W.DWORD, W.DWORD)
        self.open_thread = api(self.k, "OpenThread", W.HANDLE, W.DWORD, W.BOOL, W.DWORD)
        self.wow_context = api(self.k, "Wow64GetThreadContext", W.BOOL, W.HANDLE, C.c_void_p)
        self.wow_set_context = api(self.k, "Wow64SetThreadContext", W.BOOL, W.HANDLE, C.c_void_p)
        self.create_compatible_dc = api(self.gdi, "CreateCompatibleDC", W.HDC, W.HDC)
        self.delete_dc = api(self.gdi, "DeleteDC", W.BOOL, W.HDC)
        self.create_compatible_bitmap = api(self.gdi, "CreateCompatibleBitmap", W.HBITMAP,
                                            W.HDC, C.c_int, C.c_int)
        self.select_object = api(self.gdi, "SelectObject", W.HGDIOBJ, W.HDC, W.HGDIOBJ)
        self.delete_object = api(self.gdi, "DeleteObject", W.BOOL, W.HGDIOBJ)
        self.get_dibits = api(self.gdi, "GetDIBits", C.c_int, W.HDC, W.HBITMAP,
                              W.UINT, W.UINT, C.c_void_p, C.POINTER(BITMAPINFO), W.UINT)

    @staticmethod
    def checked(result):
        if not result:
            raise C.WinError(C.get_last_error())
        return result

    def modules(self, pid):
        snap = self.snapshot(0x8 | 0x10, pid)
        if snap == C.c_void_p(-1).value:
            raise C.WinError(C.get_last_error())
        try:
            entry = MODULEENTRY32()
            entry.dwSize = C.sizeof(entry)
            more, result = self.module_first(snap, C.byref(entry)), {}
            while more:
                result[entry.szModule.lower()] = {"base": entry.modBaseAddr,
                    "size": entry.modBaseSize, "path": entry.szExePath}
                more = self.module_next(snap, C.byref(entry))
            return result
        finally:
            self.close(snap)

    def read(self, process, address, size):
        if not address:
            raise ValueError("Null pointer")
        buf, count = C.create_string_buffer(size), C.c_size_t()
        self.checked(self.rpm(process, address, buf, size, C.byref(count)))
        if count.value != size:
            raise ValueError("Partial memory read")
        return buf.raw

    def u32(self, process, address):
        return struct.unpack("<I", self.read(process, address, 4))[0]

    def capture_window_hash(self, hwnd):
        """Capture a client window without activation and hash its 32-bit pixels.

        ``PrintWindow`` is intentionally attempted first so a hidden/minimized
        window can be sampled without changing foreground focus.  The fallback
        BitBlt path is omitted on purpose: it would read the desktop and could
        silently produce a non-game image.  A failed capture is returned as a
        structured status and must never be promoted to the visual capability.
        """
        rect = W.RECT()
        if not self.get_rect(hwnd, C.byref(rect)):
            return {"status": "get_client_rect_failed"}
        width, height = int(rect.right), int(rect.bottom)
        if width <= 0 or height <= 0 or width > 4096 or height > 4096:
            return {"status": "invalid_client_rect", "width": width, "height": height}
        window_dc = self.get_dc(hwnd)
        if not window_dc:
            return {"status": "get_dc_failed"}
        mem_dc = bitmap = old = None
        try:
            mem_dc = self.create_compatible_dc(window_dc)
            bitmap = self.create_compatible_bitmap(window_dc, width, height)
            if not mem_dc or not bitmap:
                return {"status": "create_bitmap_failed", "width": width, "height": height}
            old = self.select_object(mem_dc, bitmap)
            if not old:
                return {"status": "select_bitmap_failed", "width": width, "height": height}
            # PW_RENDERFULLCONTENT is accepted by modern user32 and harmless
            # on the legacy D2 window class used by 1.10f.
            if not self.print_window(hwnd, mem_dc, 3):
                return {"status": "print_window_failed", "width": width, "height": height}
            info = BITMAPINFO()
            info.bmiHeader.biSize = C.sizeof(BITMAPINFOHEADER)
            info.bmiHeader.biWidth = width
            info.bmiHeader.biHeight = -height  # top-down BGRA rows
            info.bmiHeader.biPlanes = 1
            info.bmiHeader.biBitCount = 32
            info.bmiHeader.biCompression = 0  # BI_RGB
            pixels = C.create_string_buffer(width * height * 4)
            rows = self.get_dibits(mem_dc, bitmap, 0, height, pixels,
                                   C.byref(info), 0)
            if rows != height:
                return {"status": "get_dibits_failed", "rows": int(rows),
                        "width": width, "height": height}
            return {"status": "captured", "width": width, "height": height,
                    "pixel_format": "BGRA32",
                    "sha256": hashlib.sha256(pixels.raw).hexdigest()}
        finally:
            if old and mem_dc:
                self.select_object(mem_dc, old)
            if bitmap:
                self.delete_object(bitmap)
            if mem_dc:
                self.delete_dc(mem_dc)
            self.release_dc(hwnd, window_dc)

    def capture_window_pixels(self, hwnd):
        """Return a read-only top-down BGRA32 PrintWindow capture."""
        rect = W.RECT()
        if not self.get_rect(hwnd, C.byref(rect)):
            return {"status": "get_client_rect_failed"}
        width, height = int(rect.right), int(rect.bottom)
        if width <= 0 or height <= 0 or width > 4096 or height > 4096:
            return {"status": "invalid_client_rect", "width": width, "height": height}
        window_dc = self.get_dc(hwnd)
        if not window_dc:
            return {"status": "get_dc_failed"}
        mem_dc = bitmap = old = None
        try:
            mem_dc = self.create_compatible_dc(window_dc)
            bitmap = self.create_compatible_bitmap(window_dc, width, height)
            if not mem_dc or not bitmap:
                return {"status": "create_bitmap_failed", "width": width, "height": height}
            old = self.select_object(mem_dc, bitmap)
            if not old or not self.print_window(hwnd, mem_dc, 3):
                return {"status": "print_window_failed", "width": width, "height": height}
            info = BITMAPINFO()
            info.bmiHeader.biSize = C.sizeof(BITMAPINFOHEADER)
            info.bmiHeader.biWidth = width
            info.bmiHeader.biHeight = -height
            info.bmiHeader.biPlanes = 1
            info.bmiHeader.biBitCount = 32
            info.bmiHeader.biCompression = 0
            pixels = C.create_string_buffer(width * height * 4)
            rows = self.get_dibits(mem_dc, bitmap, 0, height, pixels, C.byref(info), 0)
            if rows != height:
                return {"status": "get_dibits_failed", "rows": int(rows),
                        "width": width, "height": height}
            return {"status": "captured", "width": width, "height": height,
                    "pixel_format": "BGRA32", "pixels": pixels.raw}
        finally:
            if old and mem_dc:
                self.select_object(mem_dc, old)
            if bitmap:
                self.delete_object(bitmap)
            if mem_dc:
                self.delete_dc(mem_dc)
            self.release_dc(hwnd, window_dc)

    def windows(self, desktop, pid):
        result = []

        @self.enum_callback
        def callback(hwnd, _):
            owner = W.DWORD()
            self.window_pid(hwnd, C.byref(owner))
            if owner.value == pid:
                title, cls, rect = C.create_unicode_buffer(512), C.create_unicode_buffer(128), W.RECT()
                self.window_text(hwnd, title, len(title))
                self.class_name(hwnd, cls, len(cls))
                self.get_rect(hwnd, C.byref(rect))
                item = {"hwnd": hwnd, "title": title.value, "class": cls.value,
                               "visible": bool(self.is_visible(hwnd)),
                               "width": rect.right, "height": rect.bottom}
                children = []
                @self.enum_callback
                def child_callback(child, _):
                    ctitle, cclass, crect = C.create_unicode_buffer(512), C.create_unicode_buffer(128), W.RECT()
                    self.window_text(child, ctitle, len(ctitle))
                    self.class_name(child, cclass, len(cclass))
                    self.get_rect(child, C.byref(crect))
                    children.append({"hwnd": child, "title": ctitle.value, "class": cclass.value,
                                     "visible": bool(self.is_visible(child)),
                                     "width": crect.right, "height": crect.bottom})
                    return True
                self.enum_child_windows(hwnd, child_callback, 0)
                if children:
                    item["children"] = children
                result.append(item)
            return True

        C.set_last_error(0)
        if not self.enum_windows(desktop, callback, 0) and C.get_last_error() not in (0, 18):
            raise C.WinError(C.get_last_error())
        return result


def player_snapshot(win, process, modules):
    """1.10f player global corroborated by the shipped getter at RVA 0x883D0.

    Only used after explicit profile hash validation. This is a sampled client
    snapshot, not an atomic server tick or an authoritative damage event.
    """
    client = modules.get("d2client.dll")
    if not client:
        return {"status": "client_not_loaded"}
    # The local d2hackmap header describes a different client layout.  Its
    # 0x11C1E0 global is not referenced by this build.  Validate the actual
    # getter's relocated MOV EAX,[global]; RET before dereferencing it.
    player_global = client["base"] + 0x11C200
    expected_getter = b"\xA1" + struct.pack("<I", player_global) + b"\xC3"
    if win.read(process, client["base"] + 0x883D0, 6) != expected_getter:
        raise ValueError("Player getter signature mismatch; version-specific read refused")
    ptr = win.u32(process, player_global)
    if not ptr:
        return {"status": "no_player"}
    raw = win.read(process, ptr, 0xEC)
    get = lambda offset: struct.unpack_from("<I", raw, offset)[0]
    if get(0) != 0 or get(4) > 6:
        return {"status": "invalid_candidate", "pointer": ptr}
    state = {"status": "player", "pointer": ptr, "type": get(0), "class_id": get(4),
             "unit_id": get(0xC), "mode": get(0x10), "act": get(0x18),
             "animation_frame_raw": get(0x44), "seed": [get(0x20), get(0x24)]}
    if get(0x14):
        state["name"] = win.read(process, get(0x14), 16).split(b"\0")[0].decode("ascii", "replace")
    if get(0x2C):
        path = win.read(process, get(0x2C), 0x68)
        state["position"] = {"x": struct.unpack_from("<H", path, 2)[0],
                             "y": struct.unpack_from("<H", path, 6)[0]}
    return state


def animation_candidate_snapshot(win, process, modules, player):
    """Return a bounded, read-only dump of fields adjacent to the player unit.

    This is deliberately a candidate scanner, not a layout claim.  The unit
    pointer and the D2Client hash have already been validated by
    ``player_snapshot``.  We only read the existing 0xec-byte unit record and
    classify DWORDs that could plausibly be an animation object pointer or a
    small cursor/count.  No target code is called and no memory is written.
    """
    pointer = int(player.get("pointer") or 0)
    if not pointer:
        return {"status": "no_player"}
    raw = win.read(process, pointer, 0xEC)
    words = [struct.unpack_from("<I", raw, offset)[0]
             for offset in range(0, len(raw), 4)]
    client = modules.get("d2client.dll") or {}
    client_base, client_size = int(client.get("base") or 0), int(client.get("size") or 0)
    pointers = []
    pointer_records = []
    small_values = []
    for index, value in enumerate(words):
        offset = index * 4
        if value and client_base <= value < client_base + client_size:
            pointers.append({"offset": offset, "value": value,
                             "module": "d2client.dll", "rva": value - client_base})
        if offset in (0x30, 0x50, 0x54, 0x58) and value:
            # These are the animation/COF-related pointer slots used by the
            # 1.10f UnitAny layout.  A preview is evidence only; the target
            # memory remains read-only and an unreadable pointer is retained
            # as a failed candidate rather than being followed blindly.
            item = {"offset": offset, "value": value}
            try:
                preview_size = 160 if offset == 0x50 else 64
                preview = win.read(process, value, preview_size)
                item["preview_hex"] = preview.hex()
                item["preview_u32"] = list(struct.unpack("<16I", preview[:64]))
                if offset == 0x50:
                    token = preview[:8].split(b"\0", 1)[0]
                    if token and all(0x20 <= byte < 0x7F for byte in token):
                        item["ascii_token"] = token.decode("ascii")
                        item["weapon_class_candidate"] = token[-3:].decode("ascii")
                        item["token_syntax_valid"] = (
                            len(token) == 7 and token[:2].isalpha() and
                            token[2:4].isalpha() and token[4:7].isalnum())
                    if len(preview) >= 16:
                        frames = struct.unpack_from("<I", preview, 8)[0]
                        speed = struct.unpack_from("<i", preview, 12)[0]
                        flags = list(preview[16:160])
                        item["anim_data_record"] = {
                            "frames": frames,
                            "anim_speed": speed,
                            "frame_flags": flags,
                            "nonzero_frame_flags": [index for index, flag in enumerate(flags)
                                                     if flag],
                        }
                item["readable"] = True
            except (OSError, ValueError):
                item["readable"] = False
            pointer_records.append(item)
        # Keep values useful for frame/token/count discovery without dumping
        # every unrelated high-bit field from the unit structure.
        if value <= 0x10000:
            small_values.append({"offset": offset, "value": value})
    known = {
        "sequence_mode_pointer": struct.unpack_from("<I", raw, 0x30)[0],
        "sequence_frame_count_raw": struct.unpack_from("<I", raw, 0x34)[0],
        "sequence_frame_raw": struct.unpack_from("<I", raw, 0x38)[0],
        "animation_speed_raw": struct.unpack_from("<I", raw, 0x3C)[0],
        "sequence_mode": struct.unpack_from("<I", raw, 0x40)[0],
        "animation_frame_raw": struct.unpack_from("<I", raw, 0x44)[0],
        "animation_frame_count_raw": struct.unpack_from("<I", raw, 0x48)[0],
        "animation_speed_16": struct.unpack_from("<H", raw, 0x4C)[0],
        "action_frame": raw[0x4E],
        "anim_data_pointer": struct.unpack_from("<I", raw, 0x50)[0],
        "gfx_data_pointer": struct.unpack_from("<I", raw, 0x54)[0],
        "gfx_data_ext_pointer": struct.unpack_from("<I", raw, 0x58)[0],
    }
    return {"status": "candidate_scan", "pointer": pointer,
            "unit_size": len(raw), "layout_source": "d2hackmap/d2types UnitAny v112; hash-gated runtime candidate",
            "known_fields": known, "pointer_records": pointer_records,
            "small_values": small_values, "module_pointers": pointers}


def probe(args):
    runtime = args.runtime.resolve()
    manifest_path = runtime / "observer-manifest.json"
    if not manifest_path.is_file():
        raise ValueError("Run prepare first; direct launch of the original directory is disabled")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    source = Path(manifest["source"])
    before_saves = saves(source)
    identities = {name: digest(runtime / name) for name in PROFILE_FILES}
    profile = json.loads(args.profile.read_text(encoding="utf-8")) if args.profile else None
    if profile and identities != profile["sha256"]:
        raise ValueError("Profile hash mismatch; refusing to read version-specific addresses")
    run = args.output.resolve() if args.output else ROOT / "runs" / time.strftime("%Y%m%d-%H%M%S")
    run.mkdir(parents=True, exist_ok=False)
    win, process, thread, job, desktop = Win32(), None, None, None, None
    started = time.monotonic()
    desktop_name = "riiablo_probe_" + uuid.uuid4().hex[:12]
    summary = {"schema": 1, "runtime": str(runtime), "desktop": desktop_name,
               "switched_desktop": False, "sha256": identities,
               "profile": str(args.profile) if profile else None,
               "module_observed": [], "player_observed": False}
    with (run / "trace.jsonl").open("w", encoding="utf-8") as trace:
        def emit(kind, **data):
            record = {"elapsed_ms": round((time.monotonic() - started) * 1000),
                      "type": kind, **data}
            trace.write(json.dumps(record, ensure_ascii=False) + "\n")
            trace.flush()

        try:
            summary["foreground_before"] = win.foreground()
            summary["screen_before"] = [win.system_metric(0), win.system_metric(1)]
            if not args.current_desktop:
                desktop = win.checked(win.create_desktop(desktop_name, None, None, 0, 0x01FF, None))
            job = win.checked(win.create_job(None, None))
            limits = JOB_LIMITS()
            limits.BasicLimitInformation.LimitFlags = 0x2000  # KILL_ON_JOB_CLOSE
            win.checked(win.set_job(job, 9, C.byref(limits), C.sizeof(limits)))
            si, pi = STARTUPINFO(), PROCESS_INFORMATION()
            si.cb, si.lpDesktop = C.sizeof(si), (None if args.current_desktop else desktop_name)
            # With --minimize-after-ms the process creates its normal window;
            # the probe minimizes it after creation instead of using SW_HIDE.
            si.dwFlags = 1
            si.wShowWindow = 4 if (not args.current_desktop or args.minimize_after_ms is not None) else 0
            executable = runtime / args.executable
            if args.executable not in EXECUTABLES or not executable.is_file():
                raise ValueError("Only an explicitly listed copied game/loader may be launched")
            summary["executable"] = str(executable)
            summary["executable_sha256"] = digest(executable)
            launch_args = list(args.game_arg) if args.exact_args else ["-w", "-ns"] + args.game_arg
            command = C.create_unicode_buffer(subprocess.list2cmdline([str(executable)] + launch_args))
            # CREATE_SUSPENDED is needed to attach the owned job before the
            # first instruction.  DEBUG_PROCESS is diagnostic-only: keeping it
            # enabled for ordinary probes changes the legacy game's startup
            # path and is not equivalent to launching Game.exe directly.
            # Match a normal shell launch in non-debug probes.  CREATE_SUSPENDED
            # changes initialization timing for old loaders; use it only when
            # the debugger path explicitly needs it.
            creation_flags = (0x4 if args.debug else 0) | (0x2 if args.debug else 0)
            win.checked(win.create_process(str(executable), command,
                None, None, False, creation_flags,
                None, str(runtime), C.byref(si), C.byref(pi)))
            process, thread = pi.hProcess, pi.hThread
            summary["pid"] = pi.dwProcessId
            win.checked(win.assign_job(job, process))
            if win.resume(thread) == 0xFFFFFFFF:
                raise C.WinError(C.get_last_error())
            emit("launch", pid=pi.dwProcessId, desktop=desktop_name,
                 command=[args.executable] + launch_args)
            seen, previous_windows, assert_break_set = set(), None, False
            minimize_at = (time.monotonic() + args.minimize_after_ms / 1000.0
                           if args.minimize_after_ms is not None else None)
            deadline = time.monotonic() + args.seconds
            while time.monotonic() < deadline:
                if args.debug:
                    event = DEBUG_EVENT()
                    if win.debug_wait(C.byref(event), 100):
                        status = 0x10002  # DBG_CONTINUE
                        if event.code == 1:
                            ex = event.data.exception
                            emit("exception", code=hex(ex.record.code),
                                 address=hex(ex.record.address or 0),
                                 first_chance=bool(ex.first_chance))
                            if ex.record.code == 0x80000004 and args.break_assert:
                                debug_thread = win.checked(win.open_thread(0x58, False, event.tid))
                                try:
                                    context = C.create_string_buffer(716)
                                    struct.pack_into("<I", context, 0, 0x10017)
                                    win.checked(win.wow_context(debug_thread, context))
                                    esp = struct.unpack_from("<I", context, 196)[0]
                                    caller, msg, file, line = struct.unpack("<4I", win.read(process, esp, 16))
                                    emit("original_assert", caller=hex(caller), line=line,
                                         message=win.read(process, msg, 256).split(b"\0")[0].decode("ascii", "replace"),
                                         file=win.read(process, file, 256).split(b"\0")[0].decode("ascii", "replace"))
                                    struct.pack_into("<I", context, 24, 0)
                                    win.checked(win.wow_set_context(debug_thread, context))
                                finally:
                                    win.close(debug_thread)
                            elif ex.record.code not in (0x80000003, 0x4000001F):
                                status = 0x80010001  # DBG_EXCEPTION_NOT_HANDLED
                                debug_thread = win.checked(win.open_thread(0x48, False, event.tid))
                                try:
                                    context = C.create_string_buffer(716)
                                    struct.pack_into("<I", context, 0, 0x10007)
                                    win.checked(win.wow_context(debug_thread, context))
                                    esp = struct.unpack_from("<I", context, 196)[0]
                                    emit("exception_stack", esp=hex(esp),
                                         words=list(struct.unpack("<64I", win.read(process, esp, 256))))
                                finally:
                                    win.close(debug_thread)
                        # Close the file handles handed to the debugger by CREATE_PROCESS/LOAD_DLL.
                        if event.code in (3, 6):
                            handle = C.c_void_p.from_buffer(event.data).value
                            if handle:
                                win.close(handle)
                        if args.break_assert and not assert_break_set:
                            try:
                                current = win.modules(pi.dwProcessId)
                            except OSError as error:
                                if error.winerror not in (299, 24):
                                    raise
                                current = {}
                            if "fog.dll" in current:
                                debug_thread = win.checked(win.open_thread(0x58, False, event.tid))
                                try:
                                    context = C.create_string_buffer(716)
                                    struct.pack_into("<I", context, 0, 0x10010)
                                    win.checked(win.wow_context(debug_thread, context))
                                    struct.pack_into("<I", context, 4, current["fog.dll"]["base"] + 0xED30)
                                    struct.pack_into("<I", context, 24, 1)
                                    win.checked(win.wow_set_context(debug_thread, context))
                                    assert_break_set = True
                                    emit("hardware_breakpoint", target="FOG.DisplayAssert #10023")
                                finally:
                                    win.close(debug_thread)
                        win.checked(win.debug_continue(event.pid, event.tid, status))
                    else:
                        C.set_last_error(0)
                if win.wait(process, 0) == 0:
                    code = W.DWORD()
                    win.checked(win.exit_code(process, C.byref(code)))
                    summary["natural_exit_code"] = code.value
                    emit("exit", code=code.value)
                    break
                try:
                    modules = win.modules(pi.dwProcessId)
                    for name, module in modules.items():
                        if name not in seen:
                            emit("module", name=name, **module)
                            seen.add(name)
                    window_desktop = desktop if desktop else win.get_thread_desktop(pi.dwThreadId)
                    windows = win.windows(window_desktop, pi.dwProcessId)
                    if minimize_at is not None and time.monotonic() >= minimize_at:
                        for item in windows:
                            if item["visible"] and item["width"] > 0 and item["height"] > 0:
                                win.show_window(item["hwnd"], 6)  # SW_MINIMIZE
                        emit("minimize", windows=len(windows))
                        minimize_at = None
                    if windows != previous_windows:
                        emit("windows", windows=windows)
                        previous_windows = windows
                    if profile:
                        state = player_snapshot(win, process, modules)
                        summary["player_observed"] |= state["status"] == "player"
                        emit("sample", **state)
                except (OSError, ValueError) as error:
                    emit("sample_error", error=str(error))
                if not args.debug:
                    time.sleep(args.interval)
            summary["module_observed"] = sorted(seen)
            summary["windows"] = previous_windows
            summary["survived_observation"] = "natural_exit_code" not in summary
        except Exception as error:
            summary["error"] = str(error)
            emit("error", error=str(error))
        finally:
            if process:
                if win.wait(process, 0) != 0:
                    win.terminate(process, 0)
                if args.debug:
                    if "event" in locals() and event.code:
                        win.debug_continue(event.pid, event.tid, 0x10002)
                    cleanup_deadline = time.monotonic() + 5
                    while win.wait(process, 0) != 0 and time.monotonic() < cleanup_deadline:
                        cleanup = DEBUG_EVENT()
                        if win.debug_wait(C.byref(cleanup), 50):
                            win.debug_continue(cleanup.pid, cleanup.tid, 0x10002)
                summary["process_stopped"] = win.wait(process, 5000) == 0
            if thread:
                win.close(thread)
            if process:
                win.close(process)
            if job:
                win.close(job)
            if desktop:
                summary["desktop_closed"] = bool(win.close_desktop(desktop))
            summary["foreground_after"] = win.foreground()
            summary["screen_after"] = [win.system_metric(0), win.system_metric(1)]
            summary["original_saves_unchanged"] = before_saves == saves(source)
            summary["duration_ms"] = round((time.monotonic() - started) * 1000)
            (run / "summary.json").write_text(json.dumps(summary, indent=2,
                ensure_ascii=False), encoding="utf-8")
    print(json.dumps({"run": str(run), **summary}, ensure_ascii=False))
    return 1 if "error" in summary or not summary.get("process_stopped") else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    p = commands.add_parser("prepare", help="Copy only runtime files to a NEW directory")
    p.add_argument("source", type=Path)
    p.add_argument("--runtime", type=Path, default=ROOT / "runtime")
    p = commands.add_parser("probe", help="Bounded launch on an unshown private desktop")
    p.add_argument("--runtime", type=Path, default=ROOT / "runtime")
    p.add_argument("--seconds", type=float, default=20)
    p.add_argument("--interval", type=float, default=0.2)
    p.add_argument("--output", type=Path)
    p.add_argument("--profile", type=Path)
    p.add_argument("--executable", choices=EXECUTABLES, default="Game.exe")
    p.add_argument("--current-desktop", action="store_true", help="Use the current desktop but keep the game window hidden")
    p.add_argument("--minimize-after-ms", type=int, help="Create a normal window, then minimize it after this delay")
    p.add_argument("--debug", action="store_true", help="Record native exceptions on this owned process")
    p.add_argument("--game-arg", action="append", default=[], help="Additional game argument, use --game-arg=-flag")
    p.add_argument("--exact-args", action="store_true", help="Launch with exactly --game-arg values, including no arguments")
    p.add_argument("--break-assert", action="store_true", help="Diagnostic: one x86 hardware breakpoint on FOG #10023 (1.10f only)")
    args = parser.parse_args()
    if sys.platform != "win32" or C.sizeof(C.c_void_p) != 8:
        parser.error("Requires 64-bit Python on Windows")
    if args.command == "prepare":
        prepare(args.source, args.runtime)
        return 0
    if not (0 < args.seconds <= 300 and 0.02 <= args.interval <= 10):
        parser.error("seconds: (0, 300]; interval: [0.02, 10]")
    if args.break_assert and not args.debug:
        parser.error("--break-assert requires --debug")
    return probe(args)


if __name__ == "__main__":
    sys.exit(main())
