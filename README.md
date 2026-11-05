# Scalable E-Commerce Backend Platform

Java 21 and Spring Boot microservices for orders, inventory, payments, notifications, and catalog search. A synchronous REST call checks stock up front, and everything after an order is accepted runs as an event-driven saga over Kafka. Postgres is the system of record, and Redis caches reads and guards payment idempotency.

## How an order flows

```
POST /api/orders
      │  REST: is there stock?  (a fast pre-check, not the source of truth)
      ▼
   order ──"orders"──> inventory ──"inventory-reserved"──> payment ──"payments"──> order: PAID
   (PENDING)               │                                  │                 └> notification
                           │                                  │ declined
                           │ out of stock                     ├──> order: PAYMENT_FAILED
                           ▼                                  └──> inventory: put the stock back
                "inventory-rejected" ──> order: REJECTED
                                     └─> notification
```

| Service | Port | Owns | Talks to |
|---|---|---|---|
| order-service | 8081 | Orders in Postgres, read cache in Redis | REST to inventory, Kafka |
| inventory-service | 8082 | Stock and reservations in Postgres | Kafka |
| payment-service | 8083 | Idempotency keys in Redis | Kafka |
| notification-service | 8084 | Recent notices, in memory | Kafka |
| search-service | 8085 | Catalog index, in memory, Redis result cache | Kafka |

Payment is simulated: amounts from 1 cent up to $5,000 succeed, and anything else fails.

## The problems the design solves

**Overselling.** The REST pre-check is advisory, because two orders can both pass it. The real decision is one atomic statement, which the database applies one at a time across all replicas:

```sql
UPDATE stock SET qty = qty - ? WHERE sku = ? AND qty >= ?
```

**At-least-once delivery.** Kafka can redeliver any event, so every consumer is idempotent. A reservation is keyed by order id, and a repeat returns the first outcome without reserving again. It still republishes that outcome, in case the first attempt crashed between committing and publishing. An order only moves out of `PENDING`, with a conditional `UPDATE`, so a late or repeated event cannot undo a settled order.

**Failure after reservation.** A declined payment triggers a compensating step that returns the stock, exactly once.

**A broken cache.** Redis is optional for reads. A Redis timeout (200 ms) counts as a cache miss and the read falls through to Postgres.

## Quick start

Needs Java 21 and Docker. The build fails on JDKs that JaCoCo cannot instrument, such as Java 27.

```bash
export JAVA_HOME="$(brew --prefix openjdk@21)"   # or any JDK 21
./mvnw -B verify                                  # tests, plus the coverage gate
python3 scripts/e2e.py                            # starts the full stack and checks it
```

```bash
curl -s -X POST localhost:8082/api/products -H 'content-type: application/json' \
  -d '{"sku":"MUG","name":"Ceramic Mug","description":"blue stoneware","qty":5}'
curl -s -X POST localhost:8081/api/orders -H 'content-type: application/json' \
  -d '{"sku":"MUG","qty":1,"amountCents":1800,"customerId":"c1"}'
curl -s localhost:8081/api/orders/<id>
curl -s 'localhost:8085/api/search?q=blue%20mug'
curl -s localhost:8084/api/notifications
```

The full stack needs roughly 4 GB of free memory for Docker.

## How it is tested

**Unit and slice tests.** 56 tests, with the real SQL and schema files run against H2 in PostgreSQL mode, including a concurrent no-oversell test. `./mvnw verify` fails under 90% line coverage per module, and every module is currently at 98.6% or above. Spring Boot entry points and wiring classes are excluded from that figure, because they only start the process.

**End to end.** `scripts/e2e.py` runs against real Kafka, Redis, and Postgres, with no mocks:

| Scenario | What must hold |
|---|---|
| Order that pays | Settles `PAID`, stock drops, a notification arrives |
| Payment declined | Settles `PAYMENT_FAILED`, the stock comes back |
| 40 concurrent orders for 5 units | Exactly 5 are paid, the rest are rejected, stock ends at 0 |
| Redis stopped | Reads still succeed from Postgres without stalling, and orders flow again after it returns |
| Order service restarted | Orders survive |
| Consumer group rewound, whole topic replayed | Stock is unchanged, and settled orders stay settled |

In the race test, up to 33 of the 40 orders got past the REST pre-check in a run, and exactly 5 were still sold, which is the reason the pre-check cannot be the source of truth.

## Performance: what is and is not measured

**No throughput or latency figure in this repository should be treated as established.** The only machine available was an 8 GB laptop with other applications open, which could not hold Kafka, Postgres, Redis, and the JVMs in memory and started thrashing. Even a dependency-free `/health` endpoint managed only about 480 requests/sec during that.

The raw runs are in [`results/loadtest.jsonl`](results/loadtest.jsonl). Reads at a target of 1,000 requests/sec, with the cache on and off:

| Run | Cache | Achieved | p50 | p95 | Failed |
|---|---|---|---|---|---|
| Round 1 | on | 345/sec | 32.8 s | 45.1 s | 12,879 of 20,000 |
| Round 1 | off | 264/sec | 18.1 s | 54.6 s | 8,638 of 20,000 |
| Round 2 | off | 998/sec | 0.86 ms | 50.0 ms | 0 |
| Round 2 | on | 1,000/sec | 0.72 ms | 5.67 ms | 0 |

The third round and a 5,000 requests/sec attempt did not finish within their time limits, so there is no result for either. The clean pair suggests the cache helps the tail, but one pair is not a result.

To get real numbers, run this with at least 16 GB of memory or on dedicated hosts:

```bash
./mvnw -B package -DskipTests
docker compose --profile apps up -d --build
python3 scripts/loadtest.py --modes read,write,mixed --rates 1000,5000
```

[`tools/LoadGen.java`](tools/LoadGen.java) is open loop: requests go out on a fixed schedule whether or not earlier ones have finished, and latency is measured from when each was due, so a stall counts against every request behind it. `CACHE_ENABLED=false` turns the cache off for comparison.

## Deployment

Each service has a Dockerfile, and all of them run under Compose in the end-to-end test.

[`k8s/platform.yaml`](k8s/platform.yaml) defines a Deployment and Service per service, with an HPA from 2 to 10 replicas at 70% CPU. `maxUnavailable: 0`, readiness probes, a `preStop` delay, and graceful shutdown are there for zero-downtime rollouts. [`k8s/local-deps.yaml`](k8s/local-deps.yaml) adds single-node Kafka and Postgres for a local cluster. On AWS you would use EKS with MSK, RDS, and ElastiCache.

**Not yet verified:** the manifests pass schema validation (kubeconform, 22 resources), but they have **not been applied to a live cluster**, so zero-downtime rollout and autoscaling are untested. Nothing has been deployed to AWS.

## Known limits

- **No transactional outbox.** An order is committed and then published. A crash or a Kafka outage between those steps leaves it `PENDING`, with nothing to retry it. This is the next thing to fix.
- **Search and notifications keep state in memory.** Search is pinned to one replica because a second would hold a different slice of the catalog. A notification replica only lists the notices it handled itself.
- **Cache staleness.** A failed evict, or a read racing an evict, can serve a stale order until the 10-minute TTL expires.
- **Finite retries, no dead-letter topic.** Consumers use Spring Kafka's default error handling, which skips an event after 10 failed attempts. During a long Redis outage, payment events can be skipped this way, leaving those orders `PENDING`.
- **No authentication.** None of the APIs check who is calling.

## Layout

| Path | What |
|---|---|
| `*-service/` | One Spring Boot service each |
| `common/` | Event records, topics, Redis JSON cache |
| `scripts/e2e.py` | End-to-end checks |
| `scripts/loadtest.py`, `tools/LoadGen.java` | Load test and open-loop generator |
| `k8s/` | Kubernetes manifests |
| `results/` | Raw load test output |
