"""Minimal Win32 device-level input helper for the original client.

Unlike PostMessage, SendInput is global and therefore requires the target game
window to be foreground. This helper is intentionally opt-in and does not run
automatically from the broker.
"""
import ctypes as C
from ctypes import wintypes as W
import time

user32 = C.WinDLL("user32", use_last_error=True)
INPUT_KEYBOARD, INPUT_MOUSE = 1, 0
KEYEVENTF_KEYUP = 0x0002
class KEYBDINPUT(C.Structure):
    _fields_ = [("wVk", W.WORD), ("wScan", W.WORD), ("dwFlags", W.DWORD),
                ("time", W.DWORD), ("dwExtraInfo", W.ULONG_PTR)]
class MOUSEINPUT(C.Structure):
    _fields_ = [("dx", W.LONG), ("dy", W.LONG), ("mouseData", W.DWORD),
                ("dwFlags", W.DWORD), ("time", W.DWORD), ("dwExtraInfo", W.ULONG_PTR)]
class INPUT(C.Union):
    _fields_ = [("ki", KEYBDINPUT), ("mi", MOUSEINPUT)]
class INPUT_RECORD(C.Structure):
    _anonymous_ = ("data",)
    _fields_ = [("type", W.DWORD), ("data", INPUT)]

def key(vk, scan):
    records = (INPUT_RECORD * 2)()
    records[0].type = records[1].type = INPUT_KEYBOARD
    records[0].ki = KEYBDINPUT(vk, scan, 0, 0, 0)
    records[1].ki = KEYBDINPUT(vk, scan, KEYEVENTF_KEYUP, 0, 0)
    if user32.SendInput(2, records, C.sizeof(INPUT_RECORD)) != 2:
        raise C.WinError(C.get_last_error())
    time.sleep(.15)

if __name__ == "__main__":
    key(0x49, 0x17)  # I, inventory
