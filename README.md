# Lucky Draw microservices

Complete local demo of the supplied architecture using Java 17, Spring Boot, React, MySQL, Redis, and a Kafka-compatible Redpanda broker.

## Run

Docker Desktop with Docker Compose v2 is the only prerequisite.

```bash
docker compose up --build
```

Open <http://localhost:8080>. Stop with `docker compose down`; reset all local application data with `docker compose down -v`.

The application starts with no persisted campaigns, orders, tickets, entries, notifications, or rewards. Shop items are a frontend-only mock catalog.

## UI test flow

| Role | Username | Password |
| --- | --- | --- |
| Customer | `customer` | `customer123` |
| Seller | `seller` | `seller123` |

1. Sign in as `seller` and open **Campaigns**.
2. Create a campaign: enter a name, duration such as `30`, entry limit such as `2`, reward type, and reward reference. Click **Create draft**, then **Publish**.
3. Sign out, sign in as `customer`, and open **Shop & orders**.
4. Buy a product priced above `1,000,000`. Refresh **My tickets** until an `ISSUED` ticket appears.
5. Open **Lucky wheel**, select the active campaign, and click **Spin with ticket**. The backend selects one of eight wheel segments. Two segments contain the configured reward; a winning spin immediately shows **pending**.
6. Repeat purchases/spins if needed. Each ticket can be used once and every spin is persisted as an entry.
7. Sign out, sign in as `seller`, and open **Lucky wheel**. There is no seller wheel. Select one or more items under **Pending customer rewards** and click **Cancel selected** if those prizes must be revoked before the campaign ends.
8. A canceled prize immediately appears in the customer's **Winner notifications** with status `CANCELED`; it never appears in **Rewards**.
9. As seller, click **End campaign & release rewards**. Waiting for the configured end time performs the same action automatically. Cancellation is no longer allowed after either close path starts.
10. Sign in as `customer`, open **Lucky wheel**, and click **Refresh result**. Each remaining prize gets a delivery notification and appears in **Rewards** with its delivery reference.

## Exact diagram mapping

| Diagram component | Implementation / deployment |
| --- | --- |
| Customer App + Seller/Admin Portal | `frontend` React application |
| API Gateway: auth, rate limit, routing | `api-gateway` Spring Boot service |
| Order Service + Order Outbox Relay | `order-service` |
| Order DB + outbox table | MySQL `orders` schema |
| Campaign Service | separate `campaign-service` |
| Lucky Draw Write Service + Scheduler + Relay | `lucky-draw-service` |
| Campaign Scheduler | internal scheduled component in `lucky-draw-service` |
| Lucky Draw Outbox Relay | internal scheduled component in `lucky-draw-service` |
| Write DB: campaign/ticket/quota/entry/outbox/snapshot | MySQL `luckydraw` schema shared by Campaign and Lucky Draw services |
| Analytics Read Service + Analytics Projector | `analytics-service` |
| Read Model DB | Redis |
| Message broker | Redpanda Kafka API |
| Notification Service | `notification-service` |
| Reward Delivery strategies | `reward-service` with Product/Coupon strategies |

Ticket, quota, entry outcome, campaign closing, scheduler, and outbox relay are deliberately combined in the single `lucky-draw-service` module, deployment, and write database. Campaign Service is separate as shown in the diagram, but saves campaign configuration and its outbox event into that same write database. Analytics Read Service and Projector are one deployment around Redis.

## URLs

| Component | URL |
| --- | --- |
| Web application | <http://localhost:8080> |
| API Gateway health | <http://localhost:18080/actuator/health> |
| Order OpenAPI | <http://localhost:18081/swagger-ui.html> |
| Lucky Draw OpenAPI | <http://localhost:18082/swagger-ui.html> |
| Campaign OpenAPI | <http://localhost:18083/swagger-ui.html> |
| Analytics OpenAPI | <http://localhost:18085/swagger-ui.html> |
| MySQL / Redis / Kafka | `3307` / `6380` / `19092` |

Login is `POST /auth/login`; use the returned token as `Authorization: Bearer <token>`. Main gateway routes are `GET|POST /api/orders`, `GET /api/tickets`, `GET|POST /api/campaigns`, campaign `activate|cancel|end|entries`, seller reward `pending|cancel`, analytics `stats|me`, notifications, and rewards.

## Correctness

Order creation commits order and `OrderCompleted` outbox row together. It never calls Lucky Draw synchronously. Ticket issuing is idempotent by unique order ID.

Entry submission is one local transaction: `SELECT campaign FOR SHARE`, atomically consume ticket, reserve campaign/user quota, select the wheel segment with `SecureRandom`, insert the entry and pending outcome, append `EntrySubmitted`, commit. Redis is updated only by Kafka projection. Closing takes the exclusive campaign lock, freezes ordered snapshot items, moves `ACTIVE → ENDED → DRAWN`, and appends one `WinnerPicked` event for every pending winning spin. The scheduler uses the same close flow when time expires.

Seller reward cancellation and campaign close take the same exclusive campaign lock. Cancellation marks all selected pending entries and appends their `RewardCanceled` events in one transaction. Close emits `WinnerPicked` only for pending entries that were not canceled, so Reward Service never creates or delivers a claim for a canceled prize.

Analytics, notification, and reward consumers independently deduplicate events. `RewardCanceled` projects the customer status and notification per entry; only `WinnerPicked` creates a reward claim, persisted before local Product/Coupon delivery.

## Verification

```bash
docker run --rm -v "$PWD:/workspace" -w /workspace gradle:8.10.2-jdk17-alpine gradle test bootJar --no-daemon
cd frontend
npm ci
npm test
npm run lint
npm run format
npm run build
```

After the stack is healthy:

```powershell
.\deploy\smoke-test.ps1
```

The repeatable smoke test creates its own isolated campaign and orders.
