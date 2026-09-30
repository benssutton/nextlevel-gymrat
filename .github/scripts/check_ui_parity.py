"""Fail CI when the iOS and Android apps' UI test hooks drift apart.

Every iOS `.accessibilityIdentifier("x")` in ios/GymRat must have a matching
Android `Modifier.testTag("x")` in android/app/src/main, and vice versa. The same
identifiers drive both platforms' UI tests, so a screen added or changed on one
platform only is caught here. See docs/FEATURE_PARITY.md.

Usage: python .github/scripts/check_ui_parity.py   (from the repo root)
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
IOS_SRC = ROOT / "ios" / "GymRat"
ANDROID_SRC = ROOT / "android" / "app" / "src" / "main"

IOS_ID = re.compile(r'\.accessibilityIdentifier\(\s*"([^"]+)"\s*\)')
ANDROID_TAG = re.compile(r'\.testTag\(\s*"([^"]+)"\s*\)')


def collect(src: Path, pattern: re.Pattern[str], glob: str) -> dict[str, list[str]]:
    found: dict[str, list[str]] = {}
    for path in sorted(src.rglob(glob)):
        for match in pattern.finditer(path.read_text(encoding="utf-8")):
            found.setdefault(match.group(1), []).append(str(path.relative_to(ROOT)))
    return found


def main() -> int:
    ios = collect(IOS_SRC, IOS_ID, "*.swift")
    android = collect(ANDROID_SRC, ANDROID_TAG, "*.kt")
    print(f"iOS identifiers:  {sorted(ios)}")
    print(f"Android testTags: {sorted(android)}")

    ok = True
    for name in sorted(set(ios) - set(android)):
        print(f"::error::'{name}' exists on iOS ({', '.join(ios[name])}) but has no Android testTag")
        ok = False
    for name in sorted(set(android) - set(ios)):
        print(f"::error::'{name}' exists on Android ({', '.join(android[name])}) but has no iOS accessibilityIdentifier")
        ok = False
    if ok:
        print("UI parity OK: iOS and Android expose the same identifiers.")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
