"""Navigation smoke check on an already installed test emulator.

python scripts/check-ui.py <adb-path> <emulator-serial> <screenshot-directory> [--narrow]
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

adb, serial, destination = sys.argv[1:4]
output = Path(destination)
output.mkdir(parents=True, exist_ok=True)


def run(*args):
    return subprocess.check_output([adb, '-s', serial, *args], text=True, encoding='utf-8')


def nodes():
    result = run('shell', 'uiautomator', 'dump', '/sdcard/ledger-ui.xml')
    assert 'dumped to:' in result, result
    return list(ET.fromstring(run('shell', 'cat', '/sdcard/ledger-ui.xml')).iter('node'))


def tap(label, attribute='text', last=False):
    matches = [node for node in nodes() if node.get(attribute) == label]
    assert matches, f'Missing control: {label}'
    node = matches[-1] if last else matches[0]
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
    assert x2 > x1 and y2 > y1, f'Empty control: {label}'
    run('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(1)


def capture(name, title, attribute='text'):
    hierarchy = nodes()
    assert any(node.get(attribute) == title for node in hierarchy), f'Missing page: {title}'
    run('pull', '/sdcard/ledger-ui.xml', str(output / f'{name}.xml'))
    run('shell', 'screencap', '-p', '/sdcard/ledger-ui.png')
    run('pull', '/sdcard/ledger-ui.png', str(output / f'{name}.png'))
    print(f'PASS {name}: {title}', flush=True)


if __name__ == "__main__":
    run('shell', 'am', 'start', '-n', 'com.localledger.app/.MainActivity')
    time.sleep(5)
    tap('备忘录')
    tap('备忘录', last=True)
    tap('笔记')
    capture('01-notes', '我的笔记')
    tap('全部 ▾')
    tap('已完成', last=True)
    assert any(node.get('text') == '已完成 ▾' for node in nodes()), 'Note filter did not change'
    tap('已完成 ▾')
    tap('全部', last=True)
    tap('待办')
    capture('09-tasks', '今天，慢慢来')
    tap('笔记')
    for label, name, title in [('生活', '02-life', '生活的节奏'),
                               ('博物馆', '03-museum', '我的博物馆'),
                               ('心愿', '04-wishes', '心愿清单')]:
        tap(label)
        capture(name, title)
        if label == '博物馆':
            if '--narrow' in sys.argv:
                # Stacked summaries put the filter below the fold at 320 dp / 1.3x.
                width, height = map(int, re.findall(r'(\d+)x(\d+)', run('shell', 'wm', 'size'))[0])
                run('shell', 'input', 'swipe', str(width // 2), str(height * 75 // 100),
                    str(width // 2), str(height * 20 // 100), '600')
                time.sleep(1)
            tap('筛选藏品', 'content-desc')
            assert any(node.get('text') == '全部位置 ▾' for node in nodes()), 'Missing location filter'
            tap('收起藏品筛选', 'content-desc')
            tap('切换网格', 'content-desc')
            capture('10-museum-grid', '切换列表', 'content-desc')
            tap('切换列表', 'content-desc')
    tap('记账')
    tap('账单')
    capture('05-ledger', '收入')
    for label, name, title in [('报表', '06-reports', '收支报表'),
                               ('工具', '07-tools', '财务工具箱')]:
        tap(label)
        capture(name, title)
    tap('设置', 'content-desc')
    capture('08-settings', '按你的习惯')
    run('shell', 'input', 'keyevent', '4')
