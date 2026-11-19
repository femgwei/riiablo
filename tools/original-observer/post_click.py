import ctypes as C, sys, time
from ctypes import wintypes as W
hwnd, x, y = map(int, sys.argv[1:4])
u = C.WinDLL('user32', use_last_error=True)
u.PostMessageW.argtypes = [W.HWND, W.UINT, W.WPARAM, W.LPARAM]
u.PostMessageW.restype = W.BOOL
l = x | (y << 16)
for msg, wp in ((0x200, 0), (0x201, 1)):
    if not u.PostMessageW(hwnd, msg, wp, l): raise C.WinError(C.get_last_error())
    time.sleep(.08)
if not u.PostMessageW(hwnd, 0x202, 0, l): raise C.WinError(C.get_last_error())
print({'hwnd': hwnd, 'x': x, 'y': y, 'posted': True})
