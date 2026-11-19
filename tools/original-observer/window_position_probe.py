import sys, time, ctypes as C
sys.path.insert(0, str(__import__('pathlib').Path(__file__).parent))
from observer import Win32, api
from ctypes import wintypes as W

pid, x, y = int(sys.argv[1]), int(sys.argv[2]), int(sys.argv[3])
w = Win32(); u = w.u
show = api(u, 'ShowWindowAsync', W.BOOL, W.HWND, C.c_int)
pos = api(u, 'SetWindowPos', W.BOOL, W.HWND, W.HWND, C.c_int, C.c_int, C.c_int, C.c_int, W.UINT)
d = w.get_thread_desktop(api(w.k, 'GetCurrentThreadId', W.DWORD)())
window = next(v for v in w.windows(d, pid) if v['class'] == 'Diablo II')
h = window['hwnd']
show(h, 4)
time.sleep(.2)
pos(h, 0, x, y, max(1, window['width']), max(1, window['height']), 0x0010 | 0x0004)
time.sleep(.2)
print(w.windows(d, pid))
