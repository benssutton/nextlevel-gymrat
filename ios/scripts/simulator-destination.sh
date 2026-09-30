#!/usr/bin/env bash
# Prints an xcodebuild -destination for the newest available iPhone simulator,
# so CI does not break when runner images rename or drop specific devices.
set -euo pipefail

xcrun simctl list devices available --json | python3 -c '
import json, re, sys
devices = json.load(sys.stdin)["devices"]
best = None
for runtime, sims in devices.items():
    m = re.search(r"iOS-(\d+)-(\d+)", runtime)
    if not m:
        continue
    version = (int(m.group(1)), int(m.group(2)))
    for sim in sims:
        if sim["name"].startswith("iPhone") and (best is None or version > best[0]):
            best = (version, sim["udid"])
if best is None:
    sys.exit("No available iPhone simulator found")
print(f"platform=iOS Simulator,id={best[1]}")
'
