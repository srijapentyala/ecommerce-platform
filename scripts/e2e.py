#!/usr/bin/env python3
"""End-to-end checks against the full stack: real Kafka, Redis, and Postgres, no mocks.

    ./mvnw -B package -DskipTests
    python3 scripts/e2e.py            # brings the stack up, checks it, leaves it running
    python3 scripts/e2e.py --down     # ...and tears it down afterwards

Exits non-zero if any check fails. Standard library only.
"""
from __future__ import annotations

import argparse
import concurrent.futures as futures
import json
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ORDER = "http://localhost:8081"
INVENTORY = "http://localhost:8082"
NOTIFICATION = "http://localhost:8084"
SEARCH = "http://localhost:8085"
SERVICES = {"order": 8081, "inventory": 8082, "payment": 8083, "notification": 8084, "search": 8085}
TERMINAL = {"PAID", "PAYMENT_FAILED", "REJECTED"}

failures = []


def check(name, condition, detail=""):
    print(f"  {'ok  ' if condition else 'FAIL'} {name}" + (f"  ({detail})" if detail and not condition else ""), flush=True)
    if not condition:
        failures.append(name)


def compose(*args, check_exit=True):
    return subprocess.run(["docker", "compose", "--profile", "apps", *args], cwd=ROOT, check=check_exit,
                          capture_output=True, text=True)


def http(method, url, body=None, timeout=10):
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(url, data=data, method=method, headers={"content-type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        raw = error.read()
        try:
            return error.code, json.loads(raw) if raw else None
        except json.JSONDecodeError:
            return error.code, None


def wait_for(predicate, timeout=60, interval=0.25):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            value = predicate()
            if value:
                return value
        except (urllib.error.URLError, ConnectionError, TimeoutError, OSError):
            pass
        time.sleep(interval)
    return None


def wait_healthy(names=SERVICES, timeout=180):
    for name in names:
        url = f"http://localhost:{SERVICES[name]}/health"
        if not wait_for(lambda: http("GET", url)[0] == 200, timeout=timeout, interval=1):
            raise SystemExit(f"{name} did not become healthy")


def new_product(qty):
    sku = "SKU-" + uuid.uuid4().hex[:10]
    status, _ = http("POST", f"{INVENTORY}/api/products",
                     {"sku": sku, "name": f"Blue ceramic mug {sku}", "description": "stoneware", "qty": qty})
    assert status == 201, status
    return sku


def stock(sku):
    return http("GET", f"{INVENTORY}/api/inventory/{sku}")[1]["available"]


def place(sku, qty=1, cents=1800):
    return http("POST", f"{ORDER}/api/orders", {"sku": sku, "qty": qty, "amountCents": cents, "customerId": "e2e"})


def order_status(order_id):
    return http("GET", f"{ORDER}/api/orders/{order_id}")[1]["status"]


def settled(order_id, timeout=60):
    return wait_for(lambda: (lambda s: s if s in TERMINAL else None)(order_status(order_id)), timeout=timeout)


def happy_path():
    print("Order that pays")
    sku = new_product(5)
    status, order = place(sku, qty=2)
    check("order is accepted as PENDING", status == 201 and order["status"] == "PENDING", f"{status} {order}")
    check("order settles as PAID", settled(order["id"]) == "PAID")
    check("stock went down by the order quantity", wait_for(lambda: stock(sku) == 3, 10) is True, str(stock(sku)))
    notified = wait_for(lambda: any(order["id"] in json.dumps(n) for n in http("GET", f"{NOTIFICATION}/api/notifications")[1]), 15)
    check("notification service heard about it", bool(notified))
    return sku


def search_path(sku):
    print("Catalog search")
    found = wait_for(lambda: sku in json.dumps(http("GET", f"{SEARCH}/api/search?q={urllib.parse.quote('blue ceramic mug')}")[1]), 20)
    check("a new product becomes searchable through Kafka", bool(found))


def payment_failure():
    print("Order whose payment fails")
    sku = new_product(5)
    _, order = place(sku, qty=2, cents=600_000)  # over the payment limit
    check("order settles as PAYMENT_FAILED", settled(order["id"]) == "PAYMENT_FAILED")
    check("compensation returns the reserved stock", wait_for(lambda: stock(sku) == 5, 15) is True, str(stock(sku)))


def rest_rejection():
    print("Order the stock pre-check refuses")
    sku = new_product(1)
    status, _ = place(sku, qty=2)
    check("returns 409 and creates nothing", status == 409, str(status))


def no_oversell():
    print("Concurrent orders racing for scarce stock")
    sku = new_product(5)
    with futures.ThreadPoolExecutor(40) as pool:
        results = list(pool.map(lambda _: place(sku), range(40)))
    accepted = [body["id"] for status, body in results if status == 201]
    with futures.ThreadPoolExecutor(40) as pool:
        outcomes = list(pool.map(settled, accepted))
    paid = outcomes.count("PAID")
    print(f"       {len(accepted)} of 40 passed the pre-check, {paid} paid, {outcomes.count('REJECTED')} rejected")
    check("exactly the available stock was sold", paid == 5, f"paid={paid}")
    check("every other accepted order settled as REJECTED", outcomes.count("REJECTED") == len(accepted) - 5, str(outcomes))
    check("stock ends at zero, not negative", stock(sku) == 0, str(stock(sku)))


def redis_outage():
    print("Redis outage")
    sku = new_product(5)
    _, order = place(sku)
    check("order settles before the outage", settled(order["id"]) == "PAID")
    compose("stop", "redis")
    try:
        started = time.time()
        status, body = http("GET", f"{ORDER}/api/orders/{order['id']}")
        took = time.time() - started
        check("reads fall back to Postgres", status == 200 and body["status"] == "PAID", f"{status} {body}")
        check("a dead cache does not stall the request", took < 3, f"{took:.2f}s")
    finally:
        compose("start", "redis")
    wait_for(lambda: compose("exec", "-T", "redis", "redis-cli", "ping", check_exit=False).stdout.strip() == "PONG", 30)
    _, after = place(sku)
    check("orders flow again after Redis returns", settled(after["id"]) == "PAID")


def order_durability():
    print("Order service restart")
    sku = new_product(5)
    _, order = place(sku)
    settled(order["id"])
    compose("restart", "order")
    wait_healthy(["order"])
    check("orders survive a restart", order_status(order["id"]) == "PAID")


def kafka_replay():
    print("Kafka redelivery (rewinds the inventory consumer group and replays every order)")
    sku = new_product(10)
    _, paid = place(sku, qty=3)
    settled(paid["id"])
    failed_sku = new_product(10)
    _, failed = place(failed_sku, qty=3, cents=600_000)
    settled(failed["id"])
    wait_for(lambda: stock(failed_sku) == 10, 15)
    before = (stock(sku), stock(failed_sku))

    compose("stop", "inventory")
    group = "inventory-service"
    tool = "/opt/kafka/bin/kafka-consumer-groups.sh"
    # The broker can take a moment to notice the group has gone empty.
    def reset():
        result = compose("exec", "-T", "kafka", tool, "--bootstrap-server", "kafka:19092", "--group", group,
                         "--topic", "orders", "--reset-offsets", "--to-earliest", "--execute", check_exit=False)
        return result.returncode == 0 and "Error" not in result.stdout + result.stderr
    rewound = wait_for(reset, timeout=90, interval=3)
    compose("start", "inventory")
    check("consumer group was rewound to the start of the topic", bool(rewound))
    wait_healthy(["inventory"])

    def caught_up():
        out = compose("exec", "-T", "kafka", tool, "--bootstrap-server", "kafka:19092", "--group", group, "--describe",
                      check_exit=False).stdout
        lags = [line.split()[5] for line in out.splitlines() if " orders " in line and len(line.split()) > 5]
        return bool(lags) and all(lag == "0" for lag in lags)
    check("every order was redelivered and consumed", bool(wait_for(caught_up, timeout=90, interval=2)))
    time.sleep(2)
    check("redelivered orders did not reserve stock twice", (stock(sku), stock(failed_sku)) == before,
          f"{before} -> {(stock(sku), stock(failed_sku))}")
    check("a redelivered PAID order stays PAID", order_status(paid["id"]) == "PAID")
    check("a redelivered failed order stays PAYMENT_FAILED", order_status(failed["id"]) == "PAYMENT_FAILED")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--down", action="store_true", help="tear the stack down afterwards")
    parser.add_argument("--no-up", action="store_true", help="use a stack that is already running")
    args = parser.parse_args()
    missing = [s for s in SERVICES if not list((ROOT / f"{s}-service" / "target").glob("*.jar"))]
    if missing:
        raise SystemExit(f"jars missing for {missing}; run ./mvnw -B package -DskipTests first")
    if not args.no_up:
        print("Starting the stack", flush=True)
        result = compose("up", "-d", "--build", check_exit=False)
        if result.returncode != 0:
            raise SystemExit(result.stderr[-3000:])
    wait_healthy()
    try:
        sku = happy_path()
        search_path(sku)
        payment_failure()
        rest_rejection()
        no_oversell()
        redis_outage()
        order_durability()
        kafka_replay()
    finally:
        if args.down:
            compose("down", "-v")
    print(f"\n{'FAILED: ' + ', '.join(failures) if failures else 'all end-to-end checks passed'}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
