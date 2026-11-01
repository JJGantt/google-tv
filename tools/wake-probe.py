#!/usr/bin/env python3
"""Which ways of waking the TV from standby work (docs/POWER.md). Turns the TV off over adb, waits,
then tries one wake method and reports whether the TV came back on and how long it took.

    uv run --with pychromecast tools/wake-probe.py adb|wol|cast [--wait 60]

It lights the screen: run it only when someone in the bedroom has said it is fine.
"""
import argparse
import subprocess
import sys
import time
from pathlib import Path

TV = Path(__file__).resolve().parent.parent / "bin" / "tv"
CAST_NAME = "Bedroom TV"


def tv(*args, timeout=10):
    try:
        return subprocess.run([str(TV), *args], capture_output=True, text=True, timeout=timeout).stdout.strip()
    except subprocess.TimeoutExpired:
        return "timeout"


def wol():
    """Wake-on-LAN, sent by bin/tv, which reads the TV's MAC from its per-machine config."""
    p = subprocess.run([str(TV), "wake"], capture_output=True, text=True, timeout=10)
    if p.returncode:
        raise RuntimeError(p.stderr.strip())


def cast():
    import pychromecast
    found, browser = pychromecast.get_listed_chromecasts(friendly_names=[CAST_NAME], timeout=15)
    try:
        if not found:
            print("cast: not discoverable")
            return
        cc = found[0]
        cc.wait(timeout=15)  # needs discovery still running to resolve the host
        cc.start_app("233637DE")  # YouTube's Cast app id
    finally:
        browser.stop_discovery()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("method", choices=["adb", "wol", "cast"])
    ap.add_argument("--wait", type=int, default=60, help="seconds in standby before trying")
    a = ap.parse_args()

    print("before:", tv("power"))
    print("off:", tv("power", "off"))
    time.sleep(a.wait)
    print(f"after {a.wait}s, adb reaches it:", tv("power"))
    t0 = time.time()
    try:
        {"adb": lambda: print("adb:", tv("power", "on")), "wol": wol, "cast": cast}[a.method]()
    except Exception as e:
        print(f"{a.method}: {e!r}")
    for _ in range(30):
        if tv("power") == "on":
            print(f"ON after {time.time() - t0:.1f}s via {a.method}")
            return
        time.sleep(2)
    print(f"still off 60s after {a.method}")
    sys.exit(1)


if __name__ == "__main__":
    main()
