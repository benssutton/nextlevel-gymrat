"""Pyright error ratchet.

The backend was copied from a template that carries pre-existing Pyright
errors. Rather than block CI on them, this gate fails only when the error
count RISES above the committed baseline, and nudges you to lower the baseline
when it falls. Drive the baseline to 0 over time, then replace this script with
a plain `pyright` step.

Usage: pyright --outputjson | python pyright_ratchet.py <baseline-file>
"""
import json
import sys
from pathlib import Path

baseline_path = Path(sys.argv[1])
baseline = int(baseline_path.read_text().strip())
report = json.load(sys.stdin)
errors = [d for d in report["generalDiagnostics"] if d["severity"] == "error"]

for d in errors:
    line = d["range"]["start"]["line"] + 1
    print(f"{d['file']}:{line}: {d.get('rule', 'pyright')}: {d['message'].splitlines()[0]}")

count = len(errors)
print(f"Pyright errors: {count} (baseline {baseline})")
if count > baseline:
    print(f"::error::Pyright errors rose from {baseline} to {count}. Fix the new errors.")
    sys.exit(1)
if count < baseline:
    print(f"::notice::Pyright errors fell to {count}. Lower {baseline_path} to {count} to lock in the gain.")
