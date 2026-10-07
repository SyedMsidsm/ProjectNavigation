"""Small RAIN-only device smoke-test helper. Never reads other apps' UI or logs."""
from pathlib import Path
import argparse
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ADB = ROOT / '.tools/android-sdk/platform-tools/adb.exe'
sys.stdout.reconfigure(encoding='utf-8')

def adb(*args):
    return subprocess.check_output([str(ADB), *map(str, args)], text=True, encoding='utf-8', errors='replace').strip()

def snapshot():
    resumed = adb('shell', 'dumpsys', 'activity', 'activities')
    current = [line for line in resumed.splitlines() if 'mResumedActivity' in line or 'topResumedActivity' in line]
    if not any('in.mite.rain/' in line for line in current):
        raise RuntimeError('RAIN is not foreground; refusing to inspect another app.')
    adb('shell', 'uiautomator', 'dump', '/sdcard/rain-window.xml')
    data = adb('shell', 'cat', '/sdcard/rain-window.xml')
    adb('shell', 'rm', '/sdcard/rain-window.xml')
    root = ET.fromstring(data)
    nodes = list(root.iter('node'))
    # Camera OCR may contain incidental private text. Never print/store it from this helper.
    if any(n.get('text') == 'ON-DEVICE OCR' for n in nodes):
        allowed = {'Read a room sign', 'Close scanner', 'Match detected text', 'Waiting for readable text…', 'ON-DEVICE OCR', 'Allow camera', 'App settings'}
        visible = [n for n in nodes if n.get('text') in allowed]
    else:
        visible = [n for n in nodes if n.get('text') or n.get('content-desc')]
    return nodes, visible

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['snapshot', 'tap', 'swipe', 'type', 'back', 'screenshot'])
    parser.add_argument('value', nargs='?')
    args = parser.parse_args()
    nodes, visible = snapshot()
    if args.action == 'snapshot':
        for n in visible:
            print(f"{n.get('text') or n.get('content-desc')} | {n.get('bounds')} | enabled={n.get('enabled')}")
    elif args.action == 'tap':
        matches = [n for n in nodes if n.get('text') == args.value and n.get('enabled') == 'true']
        if len(matches) != 1:
            raise RuntimeError(f'Expected one visible enabled match, found {len(matches)}')
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', matches[0].get('bounds')))
        adb('shell', 'input', 'tap', (x1+x2)//2, (y1+y2)//2)
        print('Tapped requested RAIN control.')
    elif args.action == 'swipe':
        scroll = next(n for n in nodes if n.get('scrollable') == 'true')
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', scroll.get('bounds')))
        high, low = int(y1+(y2-y1)*.2), int(y1+(y2-y1)*.8)
        start, end = (low, high) if args.value == 'up' else (high, low)
        adb('shell', 'input', 'swipe', (x1+x2)//2, start, (x1+x2)//2, end, 450)
        print('Scrolled RAIN.')
    elif args.action == 'type':
        if not args.value or not re.fullmatch(r'[A-Za-z0-9 .-]+', args.value):
            raise RuntimeError('Only simple synthetic test labels are accepted.')
        adb('shell', 'input', 'text', args.value.replace(' ', '%s'))
    elif args.action == 'screenshot':
        if any(n.get('text') in {'ON-DEVICE OCR', 'AR route preview', 'AR walking navigation', 'AI place collection'} for n in nodes):
            raise RuntimeError('Refusing to save a live camera frame.')
        name = args.value or 'phone-ui'
        if not re.fullmatch(r'[a-z0-9-]+', name):
            raise RuntimeError('Screenshot name must be lowercase letters, digits or hyphens.')
        adb('shell', 'screencap', '-p', '/sdcard/rain-ui.png')
        adb('pull', '/sdcard/rain-ui.png', ROOT / 'output' / f'{name}.png')
        adb('shell', 'rm', '/sdcard/rain-ui.png')
        print('Saved RAIN UI screenshot.')
    else:
        adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')

if __name__ == '__main__':
    main()
