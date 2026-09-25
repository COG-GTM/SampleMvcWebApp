#!/usr/bin/env python3
"""Prints an NFR before/after table: demo/BASELINE.json vs a k6 summary (load/out/<label>.json)."""
import json, sys

base = json.load(open(sys.argv[1]))
after = json.load(open(sys.argv[2]))
b = base["nfr"]; gates = base["gates_for_migration"]

rows = [
    ("p50 ms", b["p50_ms"], after["p50_ms"], None),
    ("p95 ms", b["p95_ms"], after["p95_ms"], gates["p95_ms_max"]),
    ("p99 ms", b["p99_ms"], after["p99_ms"], gates["p99_ms_max"]),
    ("avg ms", b["avg_ms"], after["avg_ms"], None),
    ("error rate", b["error_rate"], after["error_rate"], gates["error_rate_max"]),
    ("req/s", base["load"]["rps"], after["rps"], None),
]
print(f"\n== NFR before/after  (baseline: {base['app']['target_framework']} {base['app']['runtime_family']} | after: {after['label']}) ==")
print(f"{'metric':<11}{'baseline':>12}{'after':>12}{'delta':>10}{'gate':>10}  verdict")
bad = False
for name, bv, av, gate in rows:
    if bv is None or av is None:
        continue
    delta = (av - bv) / bv * 100 if bv else 0
    verdict = ""
    if gate is not None:
        ok = av <= gate if name != "error rate" else av < gate
        verdict = "PASS" if ok else "FAIL"
        bad |= not ok
    print(f"{name:<11}{bv:>12}{av:>12}{delta:>+9.0f}%{('<' + str(gate)) if gate is not None else '':>10}  {verdict}")
print("\nper-route p95 ms (baseline -> after):")
for r, v in (after.get("per_route") or {}).items():
    bp = ((base.get("per_route") or {}).get(r) or {}).get("p95_ms")
    print(f"  {r:<10} {bp!s:>8} -> {v.get('p95_ms')}")
print()
sys.exit(1 if bad else 0)
