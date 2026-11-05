#!/usr/bin/env python3
"""Measure the order service with the Redis cache on and off, under identical load.

    python3 scripts/loadtest.py                        # the full matrix, writes results/loadtest.jsonl
    python3 scripts/loadtest.py --rates 5000 --rounds 1 --modes read

The stack must already be up (see README). The service is recreated between runs so each
one starts from the same state, and rounds alternate on and off so that slow drift on the
machine cannot favour one side.
"""
from __future__ import annotations

import argparse
import json
import statistics
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def compose(*args, env=None):
    import os
    return subprocess.run(["docker", "compose", "--profile", "apps", "--profile", "tools", *args], cwd=ROOT, check=True,
                          capture_output=True, text=True, env={**os.environ, **(env or {})})


def wait_healthy():
    deadline = time.time() + 120
    while time.time() < deadline:
        try:
            with urllib.request.urlopen("http://localhost:8081/health", timeout=2) as response:
                if response.status == 200:
                    return
        except OSError:
            pass
        time.sleep(1)
    raise SystemExit("order service did not become healthy")


def one_run(cache: bool, mode: str, rate: int, warmup: int, duration: int) -> dict:
    compose("up", "-d", "--no-deps", "--force-recreate", "order",
            env={"CACHE_ENABLED": "true" if cache else "false", "JVM_HEAP": "768m"})
    wait_healthy()
    label = "cache" if cache else "no-cache"
    out = compose("run", "--rm", "loadgen", "--mode", mode, "--rate", str(rate), "--warmup", str(warmup),
                  "--duration", str(duration), "--label", label).stdout
    return json.loads(out.strip().splitlines()[-1])


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--modes", default="read,write,mixed")
    parser.add_argument("--rates", default="1000,5000")
    parser.add_argument("--rounds", type=int, default=3)
    parser.add_argument("--warmup", type=int, default=20)
    parser.add_argument("--duration", type=int, default=30)
    parser.add_argument("--out", type=Path, default=ROOT / "results" / "loadtest.jsonl")
    args = parser.parse_args()

    args.out.parent.mkdir(exist_ok=True)
    rows = []
    with open(args.out, "a") as sink:
        for mode in args.modes.split(","):
            for rate in map(int, args.rates.split(",")):
                for round_number in range(args.rounds):
                    order = (True, False) if round_number % 2 == 0 else (False, True)
                    for cache in order:
                        row = one_run(cache, mode, rate, args.warmup, args.duration)
                        row["round"] = round_number
                        rows.append(row)
                        sink.write(json.dumps(row) + "\n")
                        sink.flush()
                        print(json.dumps(row), flush=True)

    print("\nmode   target  cache     achieved  p50     p95     p99     failed   (median of rounds)")
    keys = sorted({(r["mode"], r["target_rps"], r["label"]) for r in rows})
    for mode, rate, label in keys:
        group = [r for r in rows if (r["mode"], r["target_rps"], r["label"]) == (mode, rate, label)]
        median = lambda f: statistics.median(f(r) for r in group)
        print(f"{mode:<6} {rate:<7} {label:<9} {median(lambda r: r['achieved_rps']):<9.0f} "
              f"{median(lambda r: r['latency_ms']['p50']):<7.2f} {median(lambda r: r['latency_ms']['p95']):<7.2f} "
              f"{median(lambda r: r['latency_ms']['p99']):<7.2f} {sum(r['failed'] for r in group)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
