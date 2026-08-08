# Lucky Draw microservices

A locally runnable Java 17/Spring Boot and React implementation of the supplied architecture. Commands enter through Nginx, transactional state lives in MySQL, events travel through Redpanda (Kafka API), and the CQRS read model is maintained only by an idempotent Redis projector.

## Run

Prerequisite: Docker Desktop with Docker Compose v2. No local Java, Gradle, Node, MySQL, Redis, or Kafka installation is used.

```bash
docker compose up --build
```

Open <http://localhost:8080>. Stop with:

```bash
docker compose down
```

Reset all deterministic demo data and broker/read-model state:

```bash
docker compose down -v
docker compose up --build
```

## Demo identities and workflow

- Customer: `customer-1` / `CUSTOMER`
- Seller: `seller-1` / `SELLER`
- Seed campaign: `demo-campaign`, active, two entries per customer, coupon reward
- Seed orders: two qualifying orders and one non-qualifying order; qualifying orders asynchronously issue tickets

Use the role switcher in the header. As customer, wait briefly for the two tickets, submit both, create another qualifying order to demonstrate the quota conflict, and reuse a consumed ticket to demonstrate its separate conflict. As seller, view `lastUpdatedAt`, end the campaign, and draw twice; the winner and snapshot hash remain unchanged. The customer then receives a locally stored notification and reward.

Automated stack smoke test (run after a clean startup):

```powershell
.\deploy\smoke-test.ps1
```

## Ports and API documentation

| Component | URL |
| --- | --- |
| Frontend + gateway | <http://localhost:8080> |
| Order OpenAPI | <http://localhost:18081/swagger-ui.html> |
| Write OpenAPI | <http://localhost:18082/swagger-ui.html> |
| Analytics OpenAPI | <http://localhost:18085/swagger-ui.html> |
| MySQL inspection | `localhost:3307`, user/password `lucky` |
| Redis inspection | `localhost:6380` |
| Kafka-compatible broker | `localhost:19092` |

Main endpoints through the gateway:

- `POST /api/orders`
- `GET /api/write/tickets`
- `GET|POST /api/write/campaigns`
- `POST /api/write/campaigns/{id}/activate|end|cancel|draw`
- `POST /api/write/campaigns/{id}/entries`
- `GET /api/analytics/campaigns/{id}/stats`
- `GET /api/analytics/campaigns/{id}/me`
- `GET /api/notifications`
- `GET /api/rewards`

Demo authentication is deliberately isolated to `X-Demo-User` and `X-Demo-Role` headers. Every seller mutation checks campaign ownership in the write service; analytics checks projected ownership. Replace the gateway/header mechanism with verified identity claims for a real deployment.

## Modules and responsibilities

- `common-events`: Spring-free event records with event ID, aggregate ID, correlation ID, and timestamp.
- `order-service`: isolated local order bounded context; commits order and `OrderCompleted` outbox row together and relays asynchronously.
- `lucky-draw-write`: campaigns, tickets, quota, entries, snapshot, draw audit, and write outbox in one MySQL bounded context.
- `lucky-draw-relay`: publishes committed write outbox rows at least once and marks them only after broker acknowledgement.
- `lucky-draw-scheduler`: takes the exclusive close lock, changes `ACTIVE -> ENDED`, freezes dense snapshot items, and hashes them.
- `analytics-service`: atomically deduplicates and projects campaign/entry/winner events into Redis; serves reads with `lastUpdatedAt`.
- `notification-service`: independently deduplicates `WinnerPicked` and stores a local notification behind `NotificationProvider`.
- `reward-service`: creates a unique claim before delivery and selects `ProductReward` or `CouponReward` through `RewardStrategy`.
- `frontend`: responsive customer and seller workspaces.
- `gateway`: same-origin routing and the replacement seam for production authentication/rate limiting.

No Spring Boot application module depends on another application module; all share only `common-events`.

## Correctness decisions

Entry submission is one local ACID transaction: `SELECT campaign FOR SHARE`, conditional ticket consumption, conditional quota increment, entry insert, and outbox insert. A quota failure rolls the consumed ticket back. Shared campaign locks permit concurrent submissions but force close to wait; later submissions see `ENDED`.

The scheduler freezes ordered write-side entry IDs once. Draw uses `SecureRandom.nextLong(bound)`, selects from that snapshot, writes its audit, conditionally changes `ENDED -> DRAWN`, and appends `WinnerPicked` in the same transaction. Redis is never used to select a winner.

Outbox delivery is at least once. Ticket creation uses unique `order_id`; Redis projection uses one Lua transaction for event deduplication and counter changes; notifications and reward claims use `(event_id, consumer_name)` plus unique campaign constraints. Read responses expose `lastUpdatedAt` because projection is eventually consistent.

Local substitutions are Redpanda for a managed Kafka cluster, header-based demo auth for an identity provider, database-backed notifications for email/SMS/push, and local fulfillment/coupon adapters for external reward systems.

## Tests and builds

Backend unit tests and jars (inside Java 17 Docker):

```bash
docker run --rm -v "$PWD:/workspace" -w /workspace gradle:8.10.2-jdk17-alpine ./gradlew test bootJar --no-daemon
```

The MySQL integration/concurrency suite is enabled when `TEST_DB_URL` is set. Against the running stack/network:

```bash
docker run --rm --network lucky-draw_default -e TEST_DB_URL="jdbc:mysql://mysql:3306/integrationtest?useSSL=false&allowPublicKeyRetrieval=true" -e TEST_DB_USER=lucky -e TEST_DB_PASSWORD=lucky -v "$PWD:/workspace" -w /workspace gradle:8.10.2-jdk17-alpine ./gradlew :lucky-draw-write:test --rerun-tasks --no-daemon
```

Frontend:

```bash
cd frontend
npm ci
npm test
npm run lint
npm run format
npm run build
```

`docker compose build` builds every production image. `deploy/smoke-test.ps1` covers the major end-to-end flow, including quota rejection, ticket reuse rejection, Redis catch-up, duplicate-event replay, reward/notification deduplication, snapshot hashing, and repeated draw idempotency.
